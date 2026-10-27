package pe.caminoiluminacion.servicio;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import pe.caminoiluminacion.dao.CuentaPagoDAO;
import pe.caminoiluminacion.dao.HorarioDAO;
import pe.caminoiluminacion.dao.IntencionDAO;
import pe.caminoiluminacion.dao.IntencionDAO.Fila;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.modelo.CuentaPago;
import pe.caminoiluminacion.modelo.Horario;
import pe.caminoiluminacion.modelo.Intencion;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.servicio.ComprobanteService.Comprobante;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Intenciones de misa con ofrenda. El feligrés elige una misa del horario de la parroquia, una fecha, escribe la
 * intención y paga la ofrenda por Yape/Plin (con el mismo comprobante y las mismas defensas que cualquier pago).
 * El personal ve en su agenda solo las intenciones cuyo pago ya aprobó.
 *
 * Reglas: la ofrenda sale de la base de datos (no del navegador); la fecha debe ser FUTURA (desde mañana, para dar
 * tiempo a verificar el pago), caer en el día de la semana de esa misa y estar dentro de los próximos 90 días; cada
 * misa admite un máximo de intenciones por fecha; el texto solo admite letras, números y signos comunes.
 */
public class IntencionService {

    public static final int MAX_POR_MISA = 8;
    static final int DIAS_MAXIMOS_ANTICIPACION = 90;
    private static final int DIAS_MAXIMOS_AGENDA = 62;

    static final ImmutableSet<String> TIPOS = ImmutableSet.of(Intencion.DIFUNTO, Intencion.ACCION_GRACIAS, Intencion.SALUD, Intencion.OTRA);
    private static final ImmutableMap<String, DayOfWeek> DIAS = ImmutableMap.<String, DayOfWeek>builder()
            .put("lunes", DayOfWeek.MONDAY).put("martes", DayOfWeek.TUESDAY).put("miercoles", DayOfWeek.WEDNESDAY)
            .put("jueves", DayOfWeek.THURSDAY).put("viernes", DayOfWeek.FRIDAY).put("sabado", DayOfWeek.SATURDAY)
            .put("domingo", DayOfWeek.SUNDAY).build();
    private static final Pattern TEXTO = Pattern.compile("^[\\p{L}\\p{N} .,;:'’\\-()¡!¿?/&]{3,200}$");

    /** Lo que ve el feligrés antes de pedir una intención. */
    public record InfoIntencion(BigDecimal ofrenda, String parroquia, CuentaPago cuenta, List<Horario> misas, int maxPorMisa) { }

    /** Una intención con los datos para mostrarla. {@code solicitante} solo se llena en la agenda del personal. */
    public record IntencionVista(Intencion intencion, String etiqueta, String estadoPago, BigDecimal monto, String observacion,
                                 String parroquia, String solicitante) { }

    private final IntencionDAO intencionDAO;
    private final HorarioDAO horarioDAO;
    private final CuentaPagoDAO cuentaDAO;
    private final ParroquiaDAO parroquiaDAO;
    private final UsuarioDAO usuarioDAO;
    private final ComprobanteService comprobantes;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public IntencionService(IntencionDAO intencionDAO, HorarioDAO horarioDAO, CuentaPagoDAO cuentaDAO, ParroquiaDAO parroquiaDAO,
                            UsuarioDAO usuarioDAO, ComprobanteService comprobantes, BitacoraService bitacora, Clock reloj) {
        this.intencionDAO = intencionDAO;
        this.horarioDAO = horarioDAO;
        this.cuentaDAO = cuentaDAO;
        this.parroquiaDAO = parroquiaDAO;
        this.usuarioDAO = usuarioDAO;
        this.comprobantes = comprobantes;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /** Reloj de Perú para el uso real; las pruebas inyectan uno fijo. */
    public static Clock relojDePeru() {
        return Clock.system(ZoneId.of("America/Lima"));
    }

    // ------------------------------------------------------------------ información

    /** Ofrenda, número de cobro y misas disponibles de una parroquia. La ofrenda es null si no recibe intenciones en línea. */
    public InfoIntencion info(String idParroquiaTxt) throws SQLException {
        Parroquia parroquia = parroquiaExistente(idParroquiaTxt);
        CuentaPago cuenta = cuentaDAO.buscar(parroquia.getIdParroquia()).orElse(null);
        List<Horario> misas = new ArrayList<>();
        for (Horario h : horarioDAO.listarPorParroquia(parroquia.getIdParroquia())) {
            if ("misa".equals(h.getTipo())) misas.add(h);
        }
        return new InfoIntencion(cuenta == null ? null : cuenta.ofrendaIntencion(), parroquia.getNombre(), cuenta, misas, MAX_POR_MISA);
    }

    // ------------------------------------------------------------------ registrar

    /** El feligrés pide una intención y paga la ofrenda. El pago queda PENDIENTE hasta que el personal lo verifique. */
    public Intencion registrar(int idUsuario, String idParroquiaTxt, String idHorarioTxt, String fechaTxt, String tipoTxt,
                               String textoTxt, String metodoTxt, String operacionTxt, String celularTxt,
                               InputStream comprobante, String ip) throws SQLException, IOException {
        String metodo = PagoService.metodo(metodoTxt);
        Parroquia parroquia = parroquiaExistente(idParroquiaTxt);
        int idParroquia = parroquia.getIdParroquia();
        CuentaPago cuenta = cuentaDAO.buscar(idParroquia).orElse(null);
        BigDecimal ofrenda = cuenta == null ? null : cuenta.ofrendaIntencion();
        if (ofrenda == null || ofrenda.signum() <= 0) {
            throw new ReglaNegocioException(409, "Esta parroquia aún no recibe intenciones de misa en línea. Consulta en la parroquia.");
        }
        if (cuenta.numeroDe(metodo) == null) {
            throw new ReglaNegocioException(409, "Esta parroquia aún no tiene " + ("yape".equals(metodo) ? "Yape" : "Plin")
                    + " configurado. Prueba con el otro método o consulta en la parroquia.");
        }
        Horario misa = horarioDAO.buscarPorId(entero(idHorarioTxt))
                .filter(h -> h.getIdParroquia() == idParroquia && "misa".equals(h.getTipo()))
                .orElseThrow(() -> new ReglaNegocioException(404, "La misa elegida no existe en esa parroquia."));
        LocalDate fecha = fechaDeMisa(fechaTxt, misa);
        String tipo = Strings.nullToEmpty(tipoTxt).trim().toLowerCase();
        if (!TIPOS.contains(tipo)) throw new ReglaNegocioException(400, "Elige el tipo de intención.");
        String texto = textoDeIntencion(textoTxt);

        Comprobante c = comprobantes.leer(idUsuario, metodo, operacionTxt, celularTxt, comprobante);
        String archivo = comprobantes.guardarArchivo(c);
        Pago pago = new Pago(null, idUsuario, idParroquia, null, Pago.INTENCION, ofrenda, metodo, c.celularCifrado(),
                c.operacion(), archivo, c.sha256(), Pago.PENDIENTE, null, null, null);
        Intencion nueva = new Intencion(null, idUsuario, idParroquia, misa.getIdHorario(), misa.getHora(),
                java.sql.Date.valueOf(fecha), tipo, texto, 0, null);
        boolean registrada = false;
        try {
            Optional<Intencion> r = intencionDAO.insertarConPago(nueva, pago, MAX_POR_MISA);
            if (r.isEmpty()) {
                throw new ReglaNegocioException(409, "Esa misa ya completó sus intenciones para esa fecha. Elige otra fecha u otro horario.");
            }
            registrada = true;
            bitacora.registrar(idUsuario, "INTENCION_REGISTRADA", "intencion " + r.get().idIntencion() + " pago " + r.get().idPago(), ip);
            return r.get();
        } catch (SQLIntegrityConstraintViolationException e) {
            String m = String.valueOf(e.getMessage());
            throw new ReglaNegocioException(409, m.contains("uq_pago_voucher")
                    ? "Ese comprobante ya fue usado en otro pago." : "Ese número de operación ya fue registrado en otro pago.");
        } finally {
            if (!registrada) comprobantes.borrarArchivo(archivo); // sin registro en la BD no queda el archivo suelto
        }
    }

    // ------------------------------------------------------------------ consulta

    /** Las intenciones del propio usuario, con el estado de su pago. */
    public List<IntencionVista> deUsuario(int idUsuario) throws SQLException {
        List<IntencionVista> lista = new ArrayList<>();
        for (Fila f : intencionDAO.listarPorUsuario(idUsuario)) {
            lista.add(vista(f, nombreParroquia(f.intencion().idParroquia()), ""));
        }
        return lista;
    }

    /**
     * Agenda del personal: las intenciones APROBADAS de SU parroquia entre dos fechas (por defecto, los próximos 14 días).
     * @param desdeTxt "AAAA-MM-DD" o vacío para hoy
     */
    public List<IntencionVista> agenda(int idParroquia, String desdeTxt, String hastaTxt) throws SQLException {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate desde = Strings.nullToEmpty(desdeTxt).isBlank() ? hoy : fecha(desdeTxt);
        LocalDate hasta = Strings.nullToEmpty(hastaTxt).isBlank() ? desde.plusDays(14) : fecha(hastaTxt);
        if (hasta.isBefore(desde)) throw new ReglaNegocioException(400, "La fecha final no puede ser anterior a la inicial.");
        if (ChronoUnit.DAYS.between(desde, hasta) > DIAS_MAXIMOS_AGENDA) {
            throw new ReglaNegocioException(400, "El rango máximo de la agenda es de " + DIAS_MAXIMOS_AGENDA + " días.");
        }
        List<IntencionVista> lista = new ArrayList<>();
        for (Fila f : intencionDAO.agenda(idParroquia, java.sql.Date.valueOf(desde), java.sql.Date.valueOf(hasta), true)) {
            String solicitante = usuarioDAO.buscarPorId(f.intencion().idUsuario()).map(u -> u.getNombre() + " " + u.getApellido()).orElse("");
            lista.add(vista(f, "", solicitante));
        }
        return lista;
    }

    // ------------------------------------------------------------------ auxiliares

    private static IntencionVista vista(Fila f, String parroquia, String solicitante) {
        return new IntencionVista(f.intencion(), Intencion.etiqueta(f.intencion().tipo()), f.estadoPago(), f.monto(),
                f.observacionPago(), parroquia, solicitante);
    }

    private LocalDate fechaDeMisa(String fechaTxt, Horario misa) {
        LocalDate fecha = fecha(fechaTxt);
        LocalDate hoy = LocalDate.now(reloj);
        if (!fecha.isAfter(hoy)) {
            throw new ReglaNegocioException(400, "La fecha debe ser desde mañana: la parroquia necesita tiempo para verificar el pago.");
        }
        if (ChronoUnit.DAYS.between(hoy, fecha) > DIAS_MAXIMOS_ANTICIPACION) {
            throw new ReglaNegocioException(400, "Solo se piden intenciones con hasta " + DIAS_MAXIMOS_ANTICIPACION + " días de anticipación.");
        }
        DayOfWeek dia = DIAS.get(misa.getDiaSemana());
        if (dia == null || fecha.getDayOfWeek() != dia) {
            throw new ReglaNegocioException(400, "Esa fecha no cae en el día de la misa elegida (" + misa.getDiaSemana() + ").");
        }
        return fecha;
    }

    private static LocalDate fecha(String txt) {
        try {
            return LocalDate.parse(Strings.nullToEmpty(txt).trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(400, "La fecha debe tener el formato AAAA-MM-DD.");
        }
    }

    private static String textoDeIntencion(String txt) {
        String t = Strings.nullToEmpty(txt).trim().replaceAll("\\s+", " ");
        if (!TEXTO.matcher(t).matches()) {
            throw new ReglaNegocioException(400, "Escribe la intención (entre 3 y 200 caracteres) solo con letras, números y signos comunes.");
        }
        return t;
    }

    private Parroquia parroquiaExistente(String idTxt) throws SQLException {
        return parroquiaDAO.buscarPorId(entero(idTxt)).orElseThrow(() -> new ReglaNegocioException(404, "La parroquia no existe."));
    }

    private String nombreParroquia(int idParroquia) throws SQLException {
        return parroquiaDAO.buscarPorId(idParroquia).map(Parroquia::getNombre).orElse("");
    }

    private static int entero(String t) {
        try {
            return Integer.parseInt(Strings.nullToEmpty(t).trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "Identificador no válido.");
        }
    }
}
