package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.FechaDisponibleDAO;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.modelo.FechaDisponible;
import pe.caminoiluminacion.modelo.Sacramento;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * El personal publica, ajusta y retira las fechas en que su parroquia celebra cada sacramento. Esas fechas (con su cupo)
 * son las que el feligrés puede elegir al reservar; ReservaService aplica la regla del lado del feligrés.
 */
public class FechaDisponibleService {

    static final int CUPO_MAXIMO = 200;

    private final FechaDisponibleDAO fechaDAO;
    private final SacramentoDAO sacramentoDAO;
    private final BitacoraService bitacora;
    private final Supplier<LocalDate> hoy;

    public FechaDisponibleService(FechaDisponibleDAO fechaDAO, SacramentoDAO sacramentoDAO, BitacoraService bitacora) {
        this(fechaDAO, sacramentoDAO, bitacora, LocalDate::now);
    }

    public FechaDisponibleService(FechaDisponibleDAO fechaDAO, SacramentoDAO sacramentoDAO, BitacoraService bitacora,
                                  Supplier<LocalDate> hoy) {
        this.fechaDAO = fechaDAO;
        this.sacramentoDAO = sacramentoDAO;
        this.bitacora = bitacora;
        this.hoy = hoy;
    }

    /** Las fechas de la parroquia desde hoy (las pasadas ya no sirven para reservar). */
    public List<FechaDisponible> listar(int idParroquia) throws SQLException {
        return fechaDAO.listarDesde(idParroquia, hoy.get());
    }

    /**
     * Publica una fecha nueva (idTxt vacío) o ajusta el cupo y la hora de una existente (con idTxt).
     * Al modificar, el sacramento y la fecha no cambian: para eso se elimina y se crea otra.
     */
    public FechaDisponible guardar(int idAdmin, int idParroquia, String idTxt, String sacramentoTxt, String fechaTxt,
                                   String horaTxt, String cupoTxt, String ip) throws SQLException {
        int cupo = cupo(cupoTxt);
        LocalTime hora = hora(horaTxt);
        String id = idTxt == null ? "" : idTxt.trim();

        if (!id.isEmpty()) {
            FechaDisponible actual = propia(entero(id), idParroquia);
            if (cupo < actual.getOcupados()) {
                throw new ReglaNegocioException(409, "Ya hay " + actual.getOcupados() + " solicitud(es) en esa fecha: el cupo no puede ser menor.");
            }
            fechaDAO.actualizar(actual.getIdFecha(), cupo, hora);
            actual.setCupo(cupo);
            actual.setHoraInicio(hora);
            bitacora.registrar(idAdmin, "FECHA_DISPONIBLE_MODIFICADA", "fecha " + actual.getIdFecha(), ip);
            return actual;
        }

        Sacramento sacramento = sacramentoDAO.buscarPorCodigo(sacramentoTxt == null ? "" : sacramentoTxt.trim())
                .orElseThrow(() -> new ReglaNegocioException(400, "Elige un sacramento válido."));
        if (!sacramentoDAO.parroquiaOfrece(idParroquia, sacramento.getIdSacramento())) {
            throw new ReglaNegocioException(400, "Tu parroquia no ofrece este sacramento. Actívalo primero en \"Gestionar horarios\".");
        }
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(fechaTxt == null ? "" : fechaTxt.trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(400, "La fecha no es válida.");
        }
        LocalDate hoyDia = hoy.get();
        if (!fecha.isAfter(hoyDia)) throw new ReglaNegocioException(400, "La fecha debe ser futura.");
        if (fecha.isAfter(hoyDia.plusYears(2))) throw new ReglaNegocioException(400, "Solo se pueden publicar fechas con hasta 2 años de anticipación.");

        FechaDisponible nueva = new FechaDisponible(idParroquia, sacramento.getIdSacramento(), fecha, hora, cupo);
        try {
            fechaDAO.insertar(nueva);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ReglaNegocioException(409, "Ya publicaste esa fecha para este sacramento. Edita su cupo en la lista.");
        }
        bitacora.registrar(idAdmin, "FECHA_DISPONIBLE_CREADA", "fecha " + nueva.getIdFecha(), ip);
        return nueva;
    }

    /** Retira una fecha. Si ya tiene solicitudes pendientes o confirmadas no se puede: primero hay que resolverlas. */
    public void eliminar(int idAdmin, int idParroquia, String idTxt, String ip) throws SQLException {
        FechaDisponible f = propia(entero(idTxt), idParroquia);
        if (f.getOcupados() > 0) {
            throw new ReglaNegocioException(409, "Esa fecha ya tiene " + f.getOcupados()
                    + " solicitud(es) pendiente(s) o confirmada(s). Resuélvelas (o recházalas) antes de retirarla.");
        }
        fechaDAO.eliminar(f.getIdFecha());
        bitacora.registrar(idAdmin, "FECHA_DISPONIBLE_ELIMINADA", "fecha " + f.getIdFecha(), ip);
    }

    // ---------------------------------------------------------------- auxiliares

    /** Una fecha de otra parroquia se trata como inexistente (no se revela que existe). */
    private FechaDisponible propia(int id, int idParroquia) throws SQLException {
        Optional<FechaDisponible> f = fechaDAO.buscarPorId(id);
        if (f.isEmpty() || f.get().getIdParroquia() != idParroquia) throw new ReglaNegocioException(404, "La fecha no existe.");
        return f.get();
    }

    private static int cupo(String texto) {
        int c;
        try {
            c = Integer.parseInt(texto == null ? "" : texto.trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "El cupo debe ser un número entero.");
        }
        if (c < 1 || c > CUPO_MAXIMO) throw new ReglaNegocioException(400, "El cupo debe estar entre 1 y " + CUPO_MAXIMO + ".");
        return c;
    }

    /** Hora opcional en formato HH:mm; vacío = sin hora. */
    private static LocalTime hora(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.isEmpty()) return null;
        try {
            return LocalTime.parse(t);
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(400, "La hora no es válida (usa el formato 16:30).");
        }
    }

    private static int entero(String t) {
        try {
            return Integer.parseInt(t == null ? "" : t.trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "Identificador no válido.");
        }
    }
}
