package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.controlador.Pruebas;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.controlador.Pruebas.FakeParroquias;
import pe.caminoiluminacion.controlador.Pruebas.FakeReservas;
import pe.caminoiluminacion.controlador.Pruebas.FakeUsuarios;
import pe.caminoiluminacion.dao.CuentaDAO;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Notificacion;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.seguridad.Cripto;
import pe.caminoiluminacion.util.AlmacenArchivos;
import pe.caminoiluminacion.util.LimiteIntentos;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Armado de {@link CuentaService} con DAO falsos en memoria, compartido por las pruebas del servicio y del servlet.
 * La parte SQL de la anonimización (bloqueos y borrados) NO se prueba aquí: eso se verifica contra una base real.
 */
public class CuentaEntorno {

    /** Un CuentaDAO en memoria que modifica a los usuarios del FakeUsuarios, como haría la base de datos. */
    public static class FakeCuentaDAO implements CuentaDAO {
        final FakeUsuarios usuarios;
        public List<String> bloqueos = new ArrayList<>();
        public List<String> archivos = new ArrayList<>();
        public int llamadasAnonimizar;
        public Date ultimaFecha;
        public boolean correoRepetido;

        FakeCuentaDAO(FakeUsuarios usuarios) { this.usuarios = usuarios; }

        private Usuario u(int id) { return usuarios.buscarPorId(id).orElseThrow(); }

        public boolean actualizarContacto(int id, String correo, String telefono) throws SQLException {
            if (correoRepetido) throw new SQLException("Duplicate entry for key 'uq_usuario_correo'", "23000", 1062);
            u(id).setCorreo(correo); u(id).setTelefono(telefono); return true;
        }
        public boolean actualizarContrasena(int id, String hash) { u(id).setContrasenaHash(hash); return true; }
        public boolean registrarAceptacion(int id, String version) {
            u(id).setPoliticaVersion(version); u(id).setPoliticaAceptadaEn(new Timestamp(0)); return true;
        }
        public Anonimizacion anonimizar(int id, Date hoy) {
            llamadasAnonimizar++; ultimaFecha = hoy;
            if (!bloqueos.isEmpty()) return new Anonimizacion(List.copyOf(bloqueos), List.of());
            Usuario x = u(id);
            x.setNombre(""); x.setApellido(""); x.setTelefono(null); x.setDni(""); x.setCorreo("eliminado-" + id + "@eliminado.invalid");
            x.setContrasenaHash("!"); x.setEliminadoEn(new Timestamp(1));
            return new Anonimizacion(List.of(), List.copyOf(archivos));
        }
    }

    public static final LocalDate HOY = LocalDate.of(2026, 11, 5);

    public final FakeUsuarios usuarios = new FakeUsuarios();
    public final FakeReservas reservas = new FakeReservas();
    public final FakeCuentaDAO cuentas = new FakeCuentaDAO(usuarios);
    public final FakeBitacora bitacoraDao = new FakeBitacora();
    public final AlmacenArchivos almacen;
    public final List<Notificacion> avisos = new ArrayList<>();
    final AvisosDeEstadoTest.FakeDocumentos documentos = new AvisosDeEstadoTest.FakeDocumentos();
    final PagoServiceTest.FakePagos pagos = new PagoServiceTest.FakePagos();
    final IntencionServiceTest.FakeIntenciones intenciones = new IntencionServiceTest.FakeIntenciones(pagos);
    public final long[] ahoraMs = {0};
    public final CuentaService servicio;

    public CuentaEntorno(Path carpeta) {
        // Misma clave que CriptoTest (32 bytes en cero): si otra prueba ya cargó Cripto, se usa la que haya.
        System.setProperty("cai.data.key", Base64.getEncoder().encodeToString(new byte[32]));
        almacen = new AlmacenArchivos(carpeta);
        Clock reloj = Clock.fixed(HOY.atTime(10, 0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima"));
        servicio = new CuentaService(usuarios, cuentas, reservas, documentos, pagos, intenciones,
                new PagoServiceTest.FakeNotificaciones() {
                    @Override public List<Notificacion> listarPorUsuario(int u, int l) {
                        return avisos.stream().filter(n -> n.idUsuario() == u).toList();
                    }
                },
                new FakeParroquias(), almacen, new LimiteIntentos(3, 60_000, 120_000, () -> ahoraMs[0]), new BitacoraService(bitacoraDao), reloj);
    }

    /** Crea un usuario y devuelve su id. */
    public int agregar(String dni, String correo, String clave, String rol, Integer parroquia) throws SQLException {
        return usuarios.insertar(Pruebas.usuario(dni, correo, clave, rol, parroquia)).getIdUsuario();
    }

    public Reserva agregarReserva(int idUsuario, String solicitante, String notas) {
        return reservas.insertar(new Reserva(idUsuario, 1, "bautizo", solicitante, Date.valueOf(HOY.plusDays(30)), notas));
    }

    public Documento agregarDocumento(int idUsuario, String nombre, String ruta) throws SQLException {
        return documentos.insertar(new Documento(null, idUsuario, nombre, ruta));
    }

    /** Un pago con el celular cifrado como lo guarda la aplicación. */
    public Pago agregarPago(int idUsuario, String celular, String ruta, String sha) throws SQLException {
        return pagos.insertarLibre(new Pago(null, idUsuario, 1, null, Pago.DONACION, new BigDecimal("20.00"), "yape", Cripto.cifrar(celular),
                "OP123456", ruta, sha, Pago.APROBADO, null, new Timestamp(0), null));
    }

    public void agregarAviso(int idUsuario, String asunto) {
        avisos.add(new Notificacion(avisos.size() + 1, idUsuario, asunto, "mensaje", false, new Timestamp(0)));
    }

}
