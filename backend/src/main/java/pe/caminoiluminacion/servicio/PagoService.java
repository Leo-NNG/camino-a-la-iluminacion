package pe.caminoiluminacion.servicio;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.codec.digest.DigestUtils;
import pe.caminoiluminacion.dao.ArancelDAO;
import pe.caminoiluminacion.dao.CuentaPagoDAO;
import pe.caminoiluminacion.dao.NotificacionDAO;
import pe.caminoiluminacion.dao.PagoDAO;
import pe.caminoiluminacion.dao.PagoDAO.FilaIngreso;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.modelo.Arancel;
import pe.caminoiluminacion.modelo.CuentaPago;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.modelo.Sacramento;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.seguridad.Cripto;
import pe.caminoiluminacion.util.AlmacenArchivos;
import pe.caminoiluminacion.util.AlmacenArchivos.Tipo;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pagos por Yape y Plin con verificación manual: arancel de una reserva, donaciones y diezmos.
 *
 * Decisiones de seguridad:
 *  - NO hay pasarela ni se reciben datos de tarjeta: el feligrés paga desde su app y sube la captura del comprobante.
 *  - El monto del arancel sale SIEMPRE de la base de datos; el navegador nunca lo decide.
 *  - Una operación y un comprobante no pueden usarse en dos pagos vigentes (se comprueba aquí y lo garantiza la BD).
 *  - El comprobante se acepta solo si el CONTENIDO es JPG o PNG (firma del archivo), con tope de tamaño.
 *  - El celular de origen se guarda cifrado; al feligrés se le muestra enmascarado.
 *  - Cada parroquia solo ve y resuelve sus pagos; un pago de otra parroquia "no existe".
 */
public class PagoService {

    static final ImmutableSet<String> METODOS = ImmutableSet.of("yape", "plin");
    static final ImmutableSet<String> LIBRES = ImmutableSet.of(Pago.DONACION, Pago.DIEZMO);
    private static final ImmutableSet<String> ESTADOS = ImmutableSet.of(Pago.PENDIENTE, Pago.APROBADO, Pago.RECHAZADO);

    private static final Pattern OPERACION = Pattern.compile("^\\d{6,12}$");
    private static final Pattern CELULAR = Pattern.compile("^9\\d{8}$");
    private static final Pattern IMPORTE = Pattern.compile("^\\d{1,6}([.,]\\d{1,2})?$");
    private static final Pattern MES = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private static final BigDecimal MONTO_MAXIMO_LIBRE = new BigDecimal("10000");
    private static final BigDecimal MONTO_MAXIMO_ARANCEL = new BigDecimal("5000");
    private static final int MAX_PENDIENTES_POR_USUARIO = 5;

    /** Lo que ve el feligrés antes de pagar: cuánto y a qué número. */
    public record InfoPago(BigDecimal monto, String concepto, String detalle, String parroquia, CuentaPago cuenta, Pago pagoVigente) { }

    /** Un pago con los datos legibles para mostrarlo. {@code celular} va enmascarado para el feligrés y completo para el personal. */
    public record PagoVista(Pago pago, String parroquia, String feligres, String celular, String detalle) { }

    public record Descarga(Pago pago, Tipo tipo, Path ruta) { }

    private final PagoDAO pagoDAO;
    private final ArancelDAO arancelDAO;
    private final CuentaPagoDAO cuentaDAO;
    private final NotificacionDAO notificacionDAO;
    private final ReservaDAO reservaDAO;
    private final ParroquiaDAO parroquiaDAO;
    private final SacramentoDAO sacramentoDAO;
    private final UsuarioDAO usuarioDAO;
    private final AlmacenArchivos almacen;
    private final BitacoraService bitacora;

    public PagoService(PagoDAO pagoDAO, ArancelDAO arancelDAO, CuentaPagoDAO cuentaDAO, NotificacionDAO notificacionDAO,
                       ReservaDAO reservaDAO, ParroquiaDAO parroquiaDAO, SacramentoDAO sacramentoDAO, UsuarioDAO usuarioDAO,
                       AlmacenArchivos almacen, BitacoraService bitacora) {
        this.pagoDAO = pagoDAO;
        this.arancelDAO = arancelDAO;
        this.cuentaDAO = cuentaDAO;
        this.notificacionDAO = notificacionDAO;
        this.reservaDAO = reservaDAO;
        this.parroquiaDAO = parroquiaDAO;
        this.sacramentoDAO = sacramentoDAO;
        this.usuarioDAO = usuarioDAO;
        this.almacen = almacen;
        this.bitacora = bitacora;
    }

    // ------------------------------------------------------------------ información para pagar

    /** Cuánto cuesta el trámite de una reserva PROPIA y a qué número se paga. */
    public InfoPago infoArancel(int idUsuario, String idReservaTxt) throws SQLException {
        Reserva reserva = reservaPropia(idUsuario, idReservaTxt);
        Sacramento sacramento = sacramentoDe(reserva);
        BigDecimal monto = arancelDAO.montoDe(reserva.getIdParroquia(), sacramento.getIdSacramento()).orElse(null);
        Parroquia parroquia = parroquiaDAO.buscarPorId(reserva.getIdParroquia()).orElse(null);
        CuentaPago cuenta = cuentaDAO.buscar(reserva.getIdParroquia()).orElse(null);
        Pago vigente = pagoDAO.vigenteDeReserva(reserva.getIdReserva()).orElse(null);
        return new InfoPago(monto, Pago.ARANCEL, sacramento.getNombre(), parroquia == null ? "" : parroquia.getNombre(), cuenta, vigente);
    }

    /** A qué número se puede donar en una parroquia. */
    public InfoPago infoLibre(String idParroquiaTxt) throws SQLException {
        Parroquia p = parroquiaExistente(idParroquiaTxt);
        return new InfoPago(null, "donacion", "", p.getNombre(), cuentaDAO.buscar(p.getIdParroquia()).orElse(null), null);
    }

    // ------------------------------------------------------------------ registrar pagos

    /** El feligrés paga el arancel de una de SUS reservas y sube el comprobante. */
    public Pago pagarReserva(int idUsuario, String idReservaTxt, String metodoTxt, String operacionTxt, String celularTxt,
                             InputStream comprobante, String ip) throws SQLException, IOException {
        String metodo = metodo(metodoTxt);
        Reserva reserva = reservaPropia(idUsuario, idReservaTxt);
        if ("rechazada".equals(reserva.getEstado())) {
            throw new ReglaNegocioException(409, "Esa solicitud fue rechazada: no corresponde pagarla.");
        }
        if (pagoDAO.vigenteDeReserva(reserva.getIdReserva()).isPresent()) {
            throw new ReglaNegocioException(409, "Esa solicitud ya tiene un pago registrado.");
        }
        Sacramento sacramento = sacramentoDe(reserva);
        BigDecimal monto = arancelDAO.montoDe(reserva.getIdParroquia(), sacramento.getIdSacramento()).orElse(null);
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioException(409, "Este trámite no tiene un arancel que pagar en línea. Consulta en la parroquia.");
        }
        exigirCuenta(reserva.getIdParroquia(), metodo);

        Comprobante c = leerComprobante(idUsuario, metodo, operacionTxt, celularTxt, comprobante);
        Pago nuevo = nuevoPago(idUsuario, reserva.getIdParroquia(), reserva.getIdReserva(), Pago.ARANCEL, monto, metodo, c);
        Pago guardado = guardar(nuevo, c, true);
        bitacora.registrar(idUsuario, "PAGO_REGISTRADO", "pago " + guardado.idPago() + " arancel reserva " + reserva.getIdReserva(), ip);
        return guardado;
    }

    /** Donación o diezmo a una parroquia, con el monto que el feligrés elige. */
    public Pago pagarLibre(int idUsuario, String idParroquiaTxt, String concepto, String montoTxt, String metodoTxt,
                           String operacionTxt, String celularTxt, InputStream comprobante, String ip)
            throws SQLException, IOException {
        String metodo = metodo(metodoTxt);
        String tipo = Strings.nullToEmpty(concepto).trim().toLowerCase();
        if (!LIBRES.contains(tipo)) throw new ReglaNegocioException(400, "Elige si es una donación o un diezmo.");
        Parroquia parroquia = parroquiaExistente(idParroquiaTxt);
        BigDecimal monto = montoLibre(montoTxt);
        exigirCuenta(parroquia.getIdParroquia(), metodo);

        Comprobante c = leerComprobante(idUsuario, metodo, operacionTxt, celularTxt, comprobante);
        Pago nuevo = nuevoPago(idUsuario, parroquia.getIdParroquia(), null, tipo, monto, metodo, c);
        Pago guardado = guardar(nuevo, c, false);
        bitacora.registrar(idUsuario, "PAGO_REGISTRADO", "pago " + guardado.idPago() + " " + tipo, ip);
        return guardado;
    }

    // ------------------------------------------------------------------ consulta

    /** Los pagos del propio usuario, con el celular enmascarado. */
    public List<PagoVista> delUsuario(int idUsuario) throws SQLException {
        List<PagoVista> lista = new ArrayList<>();
        for (Pago p : pagoDAO.listarPorUsuario(idUsuario)) {
            lista.add(new PagoVista(p, nombreParroquia(p.idParroquia()), "", Cripto.enmascarar(Cripto.descifrar(p.celularOrigen()), 3), detalle(p)));
        }
        return lista;
    }

    /** Los pagos de UNA parroquia en un estado (el personal ve el celular completo para contrastarlo con Yape/Plin). */
    public List<PagoVista> deParroquia(int idParroquia, String estado) throws SQLException {
        String e = Strings.nullToEmpty(estado).trim().toLowerCase();
        if (e.isEmpty()) e = Pago.PENDIENTE;
        if (!ESTADOS.contains(e)) throw new ReglaNegocioException(400, "Estado no válido.");
        List<PagoVista> lista = new ArrayList<>();
        for (Pago p : pagoDAO.listarPorParroquiaYEstado(idParroquia, e)) {
            Optional<Usuario> u = usuarioDAO.buscarPorId(p.idUsuario());
            lista.add(new PagoVista(p, "", u.map(x -> x.getNombre() + " " + x.getApellido()).orElse(""),
                    Cripto.descifrar(p.celularOrigen()), detalle(p)));
        }
        return lista;
    }

    /** Autoriza y localiza el comprobante: solo su dueño o el administrador de la parroquia del pago; si no, 404. */
    public Descarga paraDescarga(String rol, int idUsuario, Integer idParroquiaAdmin, String idTxt) throws SQLException {
        Pago pago = pagoDAO.buscarPorId(entero(idTxt)).orElse(null);
        boolean permitido = pago != null && (Sesion.ROL_FELIGRES.equals(rol)
                ? pago.idUsuario() == idUsuario
                : Sesion.ROL_ADMIN.equals(rol) && idParroquiaAdmin != null && pago.idParroquia() == idParroquiaAdmin);
        if (!permitido) throw new ReglaNegocioException(404, "El comprobante no existe.");
        Path ruta = almacen.resolver(pago.rutaVoucher());
        Tipo tipo = tipoPorNombre(pago.rutaVoucher());
        if (ruta == null || tipo == null) throw new ReglaNegocioException(404, "El archivo no está disponible.");
        return new Descarga(pago, tipo, ruta);
    }

    // ------------------------------------------------------------------ verificación por el personal

    /** Aprueba o rechaza un pago PENDIENTE de la parroquia del administrador y avisa al feligrés. */
    public void resolver(int idAdmin, int idParroquiaAdmin, String idTxt, boolean aprobar, String observacion, String ip)
            throws SQLException {
        String motivo = Strings.nullToEmpty(observacion).trim();
        if (!aprobar) {
            if (motivo.isEmpty()) throw new ReglaNegocioException(400, "Indica el motivo del rechazo: el feligrés lo verá.");
            if (motivo.length() > 255) throw new ReglaNegocioException(400, "El motivo no puede superar los 255 caracteres.");
        } else if (motivo.length() > 255) {
            throw new ReglaNegocioException(400, "La observación no puede superar los 255 caracteres.");
        }
        int id = entero(idTxt);
        Pago pago = pagoDAO.buscarPorId(id).orElse(null);
        // Un pago de otra parroquia se trata como inexistente (no se revela que existe).
        if (pago == null || pago.idParroquia() != idParroquiaAdmin) throw new ReglaNegocioException(404, "El pago no existe.");
        String nuevo = aprobar ? Pago.APROBADO : Pago.RECHAZADO;
        if (!pagoDAO.resolverPendiente(id, nuevo, motivo, idAdmin)) throw new ReglaNegocioException(409, "Este pago ya fue resuelto.");

        bitacora.registrar(idAdmin, aprobar ? "PAGO_APROBADO" : "PAGO_RECHAZADO", "pago " + id, ip);
        Registro.info("Pago " + id + " " + nuevo + " por el usuario " + idAdmin);
        avisar(pago.idUsuario(), aprobar ? "Pago verificado" : "Pago observado",
                aprobar ? "Tu pago de S/ " + pago.monto().toPlainString() + " (" + detalle(pago) + ") fue verificado. ¡Gracias!"
                        : "Tu pago de S/ " + pago.monto().toPlainString() + " (" + detalle(pago) + ") fue rechazado: " + motivo
                          + ". Puedes volver a enviarlo con el comprobante correcto.");
    }

    // ------------------------------------------------------------------ configuración (personal)

    public CuentaPago cuentaDe(int idParroquia) throws SQLException {
        return cuentaDAO.buscar(idParroquia).orElse(new CuentaPago(idParroquia, null, null, null, null));
    }

    public void guardarCuenta(int idAdmin, int idParroquia, String yapeNumero, String yapeTitular, String plinNumero,
                              String plinTitular, String ip) throws SQLException {
        String yn = numeroOpcional(yapeNumero, "Yape");
        String pn = numeroOpcional(plinNumero, "Plin");
        String yt = titular(yapeTitular, yn, "Yape");
        String pt = titular(plinTitular, pn, "Plin");
        cuentaDAO.guardar(new CuentaPago(idParroquia, yn, yt, pn, pt));
        bitacora.registrar(idAdmin, "CUENTA_PAGO_EDITADA", "parroquia " + idParroquia, ip);
    }

    public List<Arancel> arancelesDe(int idParroquia) throws SQLException {
        return arancelDAO.listarPorParroquia(idParroquia);
    }

    public void guardarArancel(int idAdmin, int idParroquia, String idSacramentoTxt, String montoTxt, String ip) throws SQLException {
        int idSacramento = entero(idSacramentoTxt);
        boolean existe = false;
        for (Sacramento s : sacramentoDAO.listarTodos()) if (s.getIdSacramento() == idSacramento) existe = true;
        if (!existe) throw new ReglaNegocioException(400, "Sacramento no válido.");
        BigDecimal monto = importe(montoTxt, true);
        if (monto.compareTo(MONTO_MAXIMO_ARANCEL) > 0) throw new ReglaNegocioException(400, "El arancel no puede superar S/ 5000.");
        arancelDAO.guardar(idParroquia, idSacramento, monto);
        bitacora.registrar(idAdmin, "ARANCEL_EDITADO", "parroquia " + idParroquia + " sacramento " + idSacramento, ip);
    }

    /** Ingresos aprobados por concepto, de la parroquia; {@code mes} "AAAA-MM" o vacío para todos. */
    public List<FilaIngreso> ingresos(int idParroquia, String mes) throws SQLException {
        String m = Strings.nullToEmpty(mes).trim();
        if (!m.isEmpty() && !MES.matcher(m).matches()) throw new ReglaNegocioException(400, "El mes debe tener el formato AAAA-MM.");
        return pagoDAO.ingresosPorConcepto(idParroquia, m.isEmpty() ? null : m);
    }

    // ------------------------------------------------------------------ auxiliares

    /** Contenido ya validado de un comprobante. */
    private record Comprobante(byte[] datos, Tipo tipo, String sha256, String metodo, String operacion, String celularCifrado) { }

    private Comprobante leerComprobante(int idUsuario, String metodo, String operacionTxt, String celularTxt, InputStream contenido)
            throws SQLException, IOException {
        String operacion = Strings.nullToEmpty(operacionTxt).trim();
        if (!OPERACION.matcher(operacion).matches()) {
            throw new ReglaNegocioException(400, "El número de operación debe tener entre 6 y 12 dígitos.");
        }
        String celular = Strings.nullToEmpty(celularTxt).replaceAll("\\s", "");
        if (!CELULAR.matcher(celular).matches()) {
            throw new ReglaNegocioException(400, "Ingresa el celular desde el que pagaste (9 dígitos, empieza con 9).");
        }
        if (contenido == null) throw new ReglaNegocioException(400, "Sube la captura del comprobante.");
        // Se lee con tope: no se confía en el tamaño que declare el cliente.
        byte[] datos = contenido.readNBytes(AlmacenArchivos.MAX_BYTES + 1);
        if (datos.length == 0) throw new ReglaNegocioException(400, "Sube la captura del comprobante.");
        if (datos.length > AlmacenArchivos.MAX_BYTES) throw new ReglaNegocioException(413, "El comprobante supera el máximo de 5 MB.");
        Tipo tipo = AlmacenArchivos.detectar(datos);
        if (tipo != Tipo.JPEG && tipo != Tipo.PNG) throw new ReglaNegocioException(415, "El comprobante debe ser una imagen JPG o PNG.");

        String sha = DigestUtils.sha256Hex(datos);
        if (pagoDAO.existeOperacionVigente(metodo, operacion)) {
            throw new ReglaNegocioException(409, "Ese número de operación ya fue registrado en otro pago.");
        }
        if (pagoDAO.existeVoucherVigente(sha)) {
            throw new ReglaNegocioException(409, "Ese comprobante ya fue usado en otro pago.");
        }
        long pendientes = pagoDAO.listarPorUsuario(idUsuario).stream().filter(p -> Pago.PENDIENTE.equals(p.estado())).count();
        if (pendientes >= MAX_PENDIENTES_POR_USUARIO) {
            throw new ReglaNegocioException(429, "Tienes varios pagos esperando verificación. Espera a que la parroquia los revise.");
        }
        return new Comprobante(datos, tipo, sha, metodo, operacion, Cripto.cifrar(celular));
    }

    private static Pago nuevoPago(int idUsuario, int idParroquia, Integer idReserva, String concepto, BigDecimal monto,
                                  String metodo, Comprobante c) {
        return new Pago(null, idUsuario, idParroquia, idReserva, concepto, monto, metodo, c.celularCifrado(),
                c.operacion(), null, c.sha256(), Pago.PENDIENTE, null, null);
    }

    /** Guarda el archivo y el registro; si la base de datos falla, no deja el archivo suelto. */
    private Pago guardar(Pago base, Comprobante c, boolean arancel) throws SQLException, IOException {
        String archivo = almacen.guardar(c.datos(), c.tipo());
        Pago listo = new Pago(null, base.idUsuario(), base.idParroquia(), base.idReserva(), base.concepto(), base.monto(),
                base.metodo(), base.celularOrigen(), base.numeroOperacion(), archivo, base.voucherSha256(), base.estado(), null, null);
        boolean registrado = false;
        try {
            Pago guardado;
            if (arancel) {
                Optional<Pago> r = pagoDAO.insertarArancel(listo);
                if (r.isEmpty()) throw new ReglaNegocioException(409, "Esa solicitud ya tiene un pago registrado.");
                guardado = r.get();
            } else {
                guardado = pagoDAO.insertarLibre(listo);
            }
            registrado = true;
            return guardado;
        } catch (SQLIntegrityConstraintViolationException e) {
            // La carrera entre dos envíos iguales la decide la restricción UNIQUE de la base de datos.
            String m = String.valueOf(e.getMessage());
            throw new ReglaNegocioException(409, m.contains("uq_pago_voucher")
                    ? "Ese comprobante ya fue usado en otro pago." : "Ese número de operación ya fue registrado en otro pago.");
        } finally {
            if (!registrado) almacen.borrar(archivo);
        }
    }

    private void avisar(int idUsuario, String asunto, String mensaje) {
        try {
            notificacionDAO.crear(idUsuario, asunto, mensaje);
        } catch (SQLException | RuntimeException e) {
            Registro.error("No se pudo crear la notificación del pago", e); // el pago ya quedó resuelto: no se cancela
        }
    }

    private Reserva reservaPropia(int idUsuario, String idReservaTxt) throws SQLException {
        Optional<Reserva> r = reservaDAO.buscarPorId(entero(idReservaTxt));
        // Si es de otro usuario se responde como si no existiera.
        if (r.isEmpty() || !r.get().getIdUsuario().equals(idUsuario)) throw new ReglaNegocioException(404, "La solicitud no existe.");
        return r.get();
    }

    private Sacramento sacramentoDe(Reserva reserva) throws SQLException {
        return sacramentoDAO.buscarPorCodigo(reserva.getSacramento())
                .orElseThrow(() -> new ReglaNegocioException(409, "El sacramento de la solicitud ya no está disponible."));
    }

    private Parroquia parroquiaExistente(String idTxt) throws SQLException {
        return parroquiaDAO.buscarPorId(entero(idTxt)).orElseThrow(() -> new ReglaNegocioException(404, "La parroquia no existe."));
    }

    private void exigirCuenta(int idParroquia, String metodo) throws SQLException {
        CuentaPago cuenta = cuentaDAO.buscar(idParroquia).orElse(null);
        if (cuenta == null || cuenta.numeroDe(metodo) == null) {
            throw new ReglaNegocioException(409, "Esta parroquia aún no tiene " + ("yape".equals(metodo) ? "Yape" : "Plin")
                    + " configurado. Prueba con el otro método o consulta en la parroquia.");
        }
    }

    private String nombreParroquia(int idParroquia) throws SQLException {
        return parroquiaDAO.buscarPorId(idParroquia).map(Parroquia::getNombre).orElse("");
    }

    private String detalle(Pago p) throws SQLException {
        if (Pago.ARANCEL.equals(p.concepto())) {
            Optional<Reserva> r = p.idReserva() == null ? Optional.empty() : reservaDAO.buscarPorId(p.idReserva());
            return r.map(x -> "Arancel de " + x.getSacramento().replace('_', ' ')).orElse("Arancel");
        }
        return Pago.DIEZMO.equals(p.concepto()) ? "Diezmo" : "Donación";
    }

    private static String metodo(String txt) {
        String m = Strings.nullToEmpty(txt).trim().toLowerCase();
        if (!METODOS.contains(m)) throw new ReglaNegocioException(400, "Elige Yape o Plin.");
        return m;
    }

    private static BigDecimal montoLibre(String txt) {
        BigDecimal monto = importe(txt, false);
        if (monto.compareTo(BigDecimal.ONE) < 0) throw new ReglaNegocioException(400, "El monto mínimo es S/ 1.");
        if (monto.compareTo(MONTO_MAXIMO_LIBRE) > 0) throw new ReglaNegocioException(400, "El monto máximo en línea es S/ 10000.");
        return monto;
    }

    /** Importe con hasta 2 decimales (solo dígitos: no se acepta notación científica ni signos); {@code permiteCero} para aranceles gratuitos. */
    private static BigDecimal importe(String txt, boolean permiteCero) {
        String limpio = Strings.nullToEmpty(txt).trim();
        if (!IMPORTE.matcher(limpio).matches()) throw new ReglaNegocioException(400, "El monto no es válido: usa solo números, con hasta 2 decimales.");
        BigDecimal v = new BigDecimal(limpio.replace(',', '.'));
        if (v.signum() == 0 && !permiteCero) throw new ReglaNegocioException(400, "El monto debe ser mayor que cero.");
        return v.setScale(2);
    }

    private static String numeroOpcional(String txt, String nombre) {
        String n = Strings.nullToEmpty(txt).replaceAll("\\s", "");
        if (n.isEmpty()) return null;
        if (!CELULAR.matcher(n).matches()) throw new ReglaNegocioException(400, "El número de " + nombre + " debe tener 9 dígitos y empezar con 9.");
        return n;
    }

    private static String titular(String txt, String numero, String nombre) {
        String t = Strings.nullToEmpty(txt).trim();
        if (numero == null) return null;
        if (t.isEmpty()) throw new ReglaNegocioException(400, "Indica el titular de la cuenta " + nombre + ": el feligrés lo verá para confirmar que paga a la persona correcta.");
        if (t.length() > 100) throw new ReglaNegocioException(400, "El titular de " + nombre + " no puede superar los 100 caracteres.");
        return t;
    }

    private static Tipo tipoPorNombre(String archivo) {
        for (Tipo t : Tipo.values()) if (archivo != null && archivo.endsWith("." + t.extension)) return t;
        return null;
    }

    private static int entero(String t) {
        try {
            return Integer.parseInt(Strings.nullToEmpty(t).trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "Identificador no válido.");
        }
    }
}
