package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.CuentaDAO;
import pe.caminoiluminacion.dao.CuentaDAO.Anonimizacion;
import pe.caminoiluminacion.dao.DocumentoDAO;
import pe.caminoiluminacion.dao.IntencionDAO;
import pe.caminoiluminacion.dao.NotificacionDAO;
import pe.caminoiluminacion.dao.PagoDAO;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Notificacion;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.seguridad.Cripto;
import pe.caminoiluminacion.util.AlmacenArchivos;
import pe.caminoiluminacion.util.Claves;
import pe.caminoiluminacion.util.LimiteIntentos;
import pe.caminoiluminacion.util.Sesion;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Mi cuenta" (Ley n.° 29733): ver y corregir los propios datos, cambiar la contraseña, aceptar la política de
 * privacidad, descargar una copia de los datos y eliminar la cuenta (anonimizándola, no borrando la fila).
 * Todo opera sobre la persona de la SESIÓN: ninguna operación recibe el id de otro usuario desde el navegador.
 */
public class CuentaService {

    /** Lo que ve la persona en "Mi cuenta". El DNI va enmascarado y el hash de la contraseña nunca sale de aquí. */
    public record Perfil(String nombre, String apellido, String dniEnmascarado, String correo, String telefono, String rol,
                         boolean politicaAceptada, String politicaVersionAceptada, String politicaVigente, Timestamp creadoEn) { }

    /** Copia de los datos de la persona (derecho de acceso). Solo metadatos: nunca rutas internas, hashes ni archivos. */
    public record DatosPersonales(Perfil perfil, List<Reserva> reservas, Map<Integer, String> parroquias, List<Documento> documentos,
                                  List<Pago> pagos, List<IntencionDAO.Fila> intenciones, List<Notificacion> notificaciones,
                                  Instant generadoEn) { }

    private static final int MAX_NOTIFICACIONES = 1000;

    private final UsuarioDAO usuarioDAO;
    private final CuentaDAO cuentaDAO;
    private final ReservaDAO reservaDAO;
    private final DocumentoDAO documentoDAO;
    private final PagoDAO pagoDAO;
    private final IntencionDAO intencionDAO;
    private final NotificacionDAO notificacionDAO;
    private final ParroquiaDAO parroquiaDAO;
    private final AlmacenArchivos almacen;
    private final LimiteIntentos limite;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public CuentaService(UsuarioDAO usuarioDAO, CuentaDAO cuentaDAO, ReservaDAO reservaDAO, DocumentoDAO documentoDAO,
                         PagoDAO pagoDAO, IntencionDAO intencionDAO, NotificacionDAO notificacionDAO, ParroquiaDAO parroquiaDAO,
                         AlmacenArchivos almacen, LimiteIntentos limite, BitacoraService bitacora, Clock reloj) {
        this.usuarioDAO = usuarioDAO;
        this.cuentaDAO = cuentaDAO;
        this.reservaDAO = reservaDAO;
        this.documentoDAO = documentoDAO;
        this.pagoDAO = pagoDAO;
        this.intencionDAO = intencionDAO;
        this.notificacionDAO = notificacionDAO;
        this.parroquiaDAO = parroquiaDAO;
        this.almacen = almacen;
        this.limite = limite;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    public Perfil perfil(int idUsuario) throws SQLException {
        return perfilDe(activo(idUsuario));
    }

    public void actualizarContacto(int idUsuario, String correo, String telefono, String ip) throws SQLException {
        activo(idUsuario);
        String c = Validacion.correo(correo);
        String t = Validacion.telefonoOpcional(telefono);
        try {
            cuentaDAO.actualizarContacto(idUsuario, c, t);
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState()) || e.getErrorCode() == 1062) {
                throw new ReglaNegocioException(409, "Ya existe una cuenta registrada con ese correo.");
            }
            throw e;
        }
        bitacora.registrar(idUsuario, "CUENTA_CONTACTO_ACTUALIZADO", null, ip);
    }

    public void cambiarContrasena(int idUsuario, String actual, String nueva, String ip) throws SQLException {
        Usuario u = activo(idUsuario);
        exigirContrasena(u, actual, ip);
        Validacion.contrasena(nueva);
        if (nueva.equals(actual)) throw new ReglaNegocioException(400, "La nueva contraseña debe ser distinta de la actual.");
        cuentaDAO.actualizarContrasena(idUsuario, Claves.hashear(nueva));
        bitacora.registrar(idUsuario, "CUENTA_CLAVE_CAMBIADA", null, ip);
    }

    public void aceptarPolitica(int idUsuario, String ip) throws SQLException {
        activo(idUsuario);
        cuentaDAO.registrarAceptacion(idUsuario, PoliticaPrivacidad.VERSION);
        bitacora.registrar(idUsuario, "POLITICA_ACEPTADA", "política " + PoliticaPrivacidad.VERSION, ip);
    }

    public DatosPersonales exportarDatos(int idUsuario, String ip) throws SQLException {
        Usuario u = activo(idUsuario);
        Map<Integer, String> parroquias = new HashMap<>();
        for (Parroquia p : parroquiaDAO.listarTodas()) parroquias.put(p.getIdParroquia(), p.getNombre());
        DatosPersonales datos = new DatosPersonales(perfilDe(u), reservaDAO.listarPorUsuario(idUsuario), parroquias,
                documentoDAO.listarPorUsuario(idUsuario), pagoDAO.listarPorUsuario(idUsuario),
                intencionDAO.listarPorUsuario(idUsuario), notificacionDAO.listarPorUsuario(idUsuario, MAX_NOTIFICACIONES),
                Instant.now(reloj));
        bitacora.registrar(idUsuario, "DATOS_DESCARGADOS", null, ip);
        return datos;
    }

    /**
     * Elimina (anonimiza) la cuenta de un feligrés. Pide la contraseña, no se permite al personal y se bloquea (409)
     * mientras haya pagos pendientes, reservas pendientes o confirmadas futuras o intenciones de misa vigentes.
     * Los archivos de la persona se borran del disco solo DESPUÉS de que la base de datos confirmó el cambio.
     */
    public void eliminarCuenta(int idUsuario, String contrasena, String ip) throws SQLException {
        Usuario u = activo(idUsuario);
        if (!Sesion.ROL_FELIGRES.equals(u.getRol())) {
            throw new ReglaNegocioException(403, "Las cuentas del personal parroquial no se eliminan desde aquí. Pídelo a quien administra el sistema.");
        }
        exigirContrasena(u, contrasena, ip);
        Anonimizacion r = cuentaDAO.anonimizar(idUsuario, Date.valueOf(LocalDate.now(reloj)));
        if (!r.hecha()) {
            throw new ReglaNegocioException(409, "Todavía no puedes eliminar tu cuenta: " + String.join("; ", r.bloqueos()) + ".");
        }
        for (String archivo : r.archivos()) almacen.borrar(archivo);
        bitacora.registrar(idUsuario, "CUENTA_ELIMINADA", null, null); // sin IP: es un dato personal que se acaba de borrar
    }

    // ------------------------------------------------------------------ interno

    private Usuario activo(int idUsuario) throws SQLException {
        return usuarioDAO.buscarPorId(idUsuario).filter(u -> !u.estaEliminado())
                .orElseThrow(() -> new ReglaNegocioException(404, "La cuenta no existe."));
    }

    /** Pedir la contraseña otra vez frena a quien use una sesión ajena; los fallos se limitan igual que en el login. */
    private void exigirContrasena(Usuario u, String contrasena, String ip) {
        String clave = "cuenta|" + u.getIdUsuario();
        if (limite.estaBloqueado(clave)) {
            throw new ReglaNegocioException(429, "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo.");
        }
        if (!Claves.verificar(contrasena, u.getContrasenaHash())) {
            limite.registrarFallo(clave);
            bitacora.registrar(u.getIdUsuario(), "CUENTA_CLAVE_FALLIDA", null, ip);
            throw new ReglaNegocioException(403, "La contraseña actual no es correcta.");
        }
        limite.reiniciar(clave);
    }

    private static Perfil perfilDe(Usuario u) {
        boolean aceptada = PoliticaPrivacidad.VERSION.equals(u.getPoliticaVersion());
        return new Perfil(u.getNombre(), u.getApellido(), Cripto.enmascarar(u.getDni(), 3), u.getCorreo(), u.getTelefono(), u.getRol(),
                aceptada, u.getPoliticaVersion(), PoliticaPrivacidad.VERSION, u.getCreadoEn());
    }
}
