package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.FechaDisponibleDAO;
import pe.caminoiluminacion.dao.PagoDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.ReservaDAO.ResultadoReprogramacion;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.modelo.FechaDisponible;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.modelo.Sacramento;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cancelar y reprogramar una solicitud de sacramento. Solo lo hace el feligrés dueño de la solicitud (la de otra persona
 * se trata como inexistente). Reglas:
 * <ul>
 *   <li>Solo se puede cancelar o reprogramar una solicitud pendiente o confirmada cuya fecha no haya pasado.</li>
 *   <li>Una solicitud confirmada exige {@link #DIAS_ANTICIPACION_CONFIRMADA} días de anticipación: más cerca, se coordina con la parroquia.</li>
 *   <li>Se puede reprogramar hasta {@link #MAX_REPROGRAMACIONES} veces; la fecha nueva sigue las mismas reglas que al crear.</li>
 *   <li>Al reprogramar, la solicitud vuelve a quedar pendiente: la parroquia valida la fecha nueva. Los documentos y el pago se conservan.</li>
 *   <li>No se cancela mientras haya un pago del arancel pendiente de verificación.</li>
 * </ul>
 */
public class GestionReservaService {

    public static final int DIAS_ANTICIPACION_CONFIRMADA = 2;
    public static final int MAX_REPROGRAMACIONES = 2;

    /** Qué puede hacer el feligrés con una solicitud (la interfaz muestra solo los botones que valen). */
    public record Opciones(boolean puedeCancelar, boolean puedeReprogramar) { }

    /** Resultado de cancelar: si el arancel ya estaba pagado, la parroquia debe coordinar el reembolso o el uso del pago. */
    public record Cancelacion(boolean arancelPagado) { }

    private final ReservaDAO reservaDAO;
    private final FechaDisponibleDAO fechaDAO;
    private final SacramentoDAO sacramentoDAO;
    private final PagoDAO pagoDAO;
    private final BitacoraService bitacora;
    private final Supplier<LocalDate> hoy;

    public GestionReservaService(ReservaDAO reservaDAO, FechaDisponibleDAO fechaDAO, SacramentoDAO sacramentoDAO, PagoDAO pagoDAO,
                                 BitacoraService bitacora, Supplier<LocalDate> hoy) {
        this.reservaDAO = reservaDAO;
        this.fechaDAO = fechaDAO;
        this.sacramentoDAO = sacramentoDAO;
        this.pagoDAO = pagoDAO;
        this.bitacora = bitacora;
        this.hoy = hoy;
    }

    /** Qué se puede hacer con una solicitud ahora mismo (sin consultar la base de datos). */
    public Opciones opciones(Reserva r) {
        boolean cancelable = motivoBloqueo(r) == null;
        return new Opciones(cancelable, cancelable && r.getReprogramaciones() < MAX_REPROGRAMACIONES);
    }

    public Cancelacion cancelar(int idUsuario, String idReservaTxt, String motivoTxt, String ip) throws SQLException {
        Reserva r = propia(idUsuario, idReservaTxt);
        String bloqueo = motivoBloqueo(r);
        if (bloqueo != null) throw new ReglaNegocioException(409, bloqueo);
        String motivo = motivoTxt == null ? "" : motivoTxt.trim();
        if (motivo.length() > 255) throw new ReglaNegocioException(400, "El motivo no puede superar los 255 caracteres.");

        Optional<Pago> pago = pagoDAO.vigenteDeReserva(r.getIdReserva());
        if (pago.isPresent() && Pago.PENDIENTE.equals(pago.get().estado())) {
            throw new ReglaNegocioException(409, "Tienes un pago de esta solicitud pendiente de verificación. Espera a que la parroquia lo resuelva para cancelar.");
        }
        if (!reservaDAO.cancelar(r.getIdReserva(), motivo.isEmpty() ? null : motivo)) {
            throw new ReglaNegocioException(409, "Esa solicitud ya no se puede cancelar: cambió de estado.");
        }
        bitacora.registrar(idUsuario, "RESERVA_CANCELADA", "reserva " + r.getIdReserva(), ip);
        return new Cancelacion(pago.isPresent() && Pago.APROBADO.equals(pago.get().estado()));
    }

    /** @return la solicitud ya reprogramada (pendiente, con la fecha nueva) */
    public Reserva reprogramar(int idUsuario, String idReservaTxt, String fechaTxt, String ip) throws SQLException {
        Reserva r = propia(idUsuario, idReservaTxt);
        String bloqueo = motivoBloqueo(r);
        if (bloqueo != null) throw new ReglaNegocioException(409, bloqueo);
        if (r.getReprogramaciones() >= MAX_REPROGRAMACIONES) {
            throw new ReglaNegocioException(409, "Ya reprogramaste esta solicitud " + MAX_REPROGRAMACIONES + " veces. Comunícate con la parroquia o haz una solicitud nueva.");
        }

        LocalDate fecha;
        try {
            fecha = LocalDate.parse(fechaTxt == null ? "" : fechaTxt.trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(400, "La fecha no es válida.");
        }
        LocalDate hoyDia = hoy.get();
        if (!fecha.isAfter(hoyDia)) throw new ReglaNegocioException(400, "La fecha debe ser futura.");
        if (fecha.isAfter(hoyDia.plusYears(2))) throw new ReglaNegocioException(400, "Solo se puede reservar con hasta 2 años de anticipación.");
        if (fecha.equals(r.getFechaSolicitada().toLocalDate())) throw new ReglaNegocioException(400, "Elige una fecha distinta de la actual.");

        Sacramento sacramento = sacramentoDAO.buscarPorCodigo(r.getSacramento())
                .orElseThrow(() -> new ReglaNegocioException(400, "Sacramento no válido."));
        if (!sacramentoDAO.parroquiaOfrece(r.getIdParroquia(), sacramento.getIdSacramento())) {
            throw new ReglaNegocioException(400, "Esa parroquia ya no ofrece este sacramento.");
        }
        // Igual que al crear: si la parroquia publicó fechas para este sacramento solo vale una de ellas (con su cupo);
        // si no publicó ninguna, la fecha es libre (cupo 1) y la parroquia la valida.
        List<FechaDisponible> publicadas = fechaDAO.listarDesde(r.getIdParroquia(), sacramento.getIdSacramento(), hoyDia.plusDays(1));
        int cupo = 1;
        if (!publicadas.isEmpty()) {
            FechaDisponible elegida = publicadas.stream().filter(f -> f.getFecha().equals(fecha)).findFirst()
                    .orElseThrow(() -> new ReglaNegocioException(400, "Esa fecha no está entre las que la parroquia tiene disponibles. Elige una de la lista."));
            cupo = elegida.getCupo();
        }

        ResultadoReprogramacion res = reservaDAO.reprogramar(r.getIdReserva(), Date.valueOf(fecha), cupo, MAX_REPROGRAMACIONES);
        switch (res) {
            case SIN_CUPO -> throw new ReglaNegocioException(409, publicadas.isEmpty()
                    ? "Esa fecha ya no está disponible para ese sacramento en esa parroquia. Elige otra fecha."
                    : "Esa fecha ya no tiene cupo. Elige otra de las fechas disponibles.");
            case NO_VIGENTE -> throw new ReglaNegocioException(409, "Esa solicitud ya no se puede reprogramar: cambió de estado.");
            case LIMITE -> throw new ReglaNegocioException(409, "Ya reprogramaste esta solicitud " + MAX_REPROGRAMACIONES + " veces.");
            default -> { }
        }
        bitacora.registrar(idUsuario, "RESERVA_REPROGRAMADA", "reserva " + r.getIdReserva() + " a " + fecha, ip);
        return reservaDAO.buscarPorId(r.getIdReserva()).orElse(r);
    }

    // ------------------------------------------------------------------ interno

    /** La solicitud debe ser del usuario de la sesión; la de otra persona se trata como inexistente. */
    private Reserva propia(int idUsuario, String idReservaTxt) throws SQLException {
        int id;
        try {
            id = Integer.parseInt(idReservaTxt == null ? "" : idReservaTxt.trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "Solicitud no válida.");
        }
        return reservaDAO.buscarPorId(id).filter(r -> r.getIdUsuario() != null && r.getIdUsuario() == idUsuario)
                .orElseThrow(() -> new ReglaNegocioException(404, "La solicitud no existe."));
    }

    /** null si se puede gestionar; si no, la razón que se le muestra al feligrés. */
    private String motivoBloqueo(Reserva r) {
        String estado = r.getEstado();
        if ("cancelada".equals(estado)) return "Esa solicitud ya fue cancelada.";
        if ("rechazada".equals(estado)) return "Esa solicitud fue rechazada: haz una nueva solicitud.";
        LocalDate fecha = r.getFechaSolicitada().toLocalDate();
        LocalDate hoyDia = hoy.get();
        if (fecha.isBefore(hoyDia)) return "La fecha de esa solicitud ya pasó.";
        if ("confirmada".equals(estado) && fecha.isBefore(hoyDia.plusDays(DIAS_ANTICIPACION_CONFIRMADA))) {
            return "Tu solicitud confirmada es en menos de " + DIAS_ANTICIPACION_CONFIRMADA + " días: comunícate directamente con la parroquia.";
        }
        return null;
    }
}
