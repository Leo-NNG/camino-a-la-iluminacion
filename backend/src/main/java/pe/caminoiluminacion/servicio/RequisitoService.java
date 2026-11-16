package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.RequisitoDAO;
import pe.caminoiluminacion.dao.RequisitoDAO.Avance;
import pe.caminoiluminacion.dao.RequisitoDAO.Cumplimiento;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.modelo.Requisito;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.modelo.Sacramento;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Requisitos por sacramento. El personal define qué documentos pide su parroquia; el feligrés ve esa lista como
 * checklist de su solicitud y cada documento que sube puede indicar qué requisito cumple.
 */
public class RequisitoService {

    public static final String FALTANTE = "faltante";
    public static final String PENDIENTE = "pendiente";
    public static final String VALIDADO = "validado";
    public static final String RECHAZADO = "rechazado";

    /** Un requisito con su situación en una solicitud concreta. */
    public record Item(Requisito requisito, String estado, Integer idDocumento, String motivo) { }

    /** El checklist de una solicitud: {@code completo} = todos los obligatorios tienen un documento validado. */
    public record Checklist(Reserva reserva, List<Item> items, int obligatorios, int validados, boolean completo) { }

    private static final int MAX_POR_SACRAMENTO = 15;

    private final RequisitoDAO requisitoDAO;
    private final SacramentoDAO sacramentoDAO;
    private final ReservaDAO reservaDAO;
    private final BitacoraService bitacora;

    public RequisitoService(RequisitoDAO requisitoDAO, SacramentoDAO sacramentoDAO, ReservaDAO reservaDAO, BitacoraService bitacora) {
        this.requisitoDAO = requisitoDAO;
        this.sacramentoDAO = sacramentoDAO;
        this.reservaDAO = reservaDAO;
        this.bitacora = bitacora;
    }

    // ------------------------------------------------------------------ lectura

    /** Lo que el feligrés ve antes de reservar: los requisitos de una parroquia para un sacramento. */
    public List<Requisito> publicos(String parroquiaTxt, String sacramentoCodigo) throws SQLException {
        int idParroquia = entero(parroquiaTxt, "Parroquia no válida.");
        Sacramento s = sacramentoDAO.buscarPorCodigo(sacramentoCodigo == null ? "" : sacramentoCodigo.trim())
                .orElseThrow(() -> new ReglaNegocioException(400, "Sacramento no válido."));
        return requisitoDAO.listar(idParroquia, s.getIdSacramento());
    }

    /** Todos los requisitos de la parroquia del administrador. */
    public List<Requisito> deParroquia(int idParroquia) throws SQLException {
        return requisitoDAO.listar(idParroquia, null);
    }

    /** Avance (obligatorios y validados) de las solicitudes activas de la parroquia, por id de reserva. */
    public Map<Integer, Avance> avance(int idParroquia) throws SQLException {
        return requisitoDAO.avanceDeParroquia(idParroquia);
    }

    /** El checklist de una solicitud del propio usuario (la de otra persona se trata como inexistente). */
    public Checklist checklist(int idUsuario, String reservaTxt) throws SQLException {
        Reserva reserva = reservaDAO.buscarPorId(entero(reservaTxt, "Solicitud no válida."))
                .filter(r -> r.getIdUsuario().equals(idUsuario))
                .orElseThrow(() -> new ReglaNegocioException(404, "La solicitud no existe."));
        if ("rechazada".equals(reserva.getEstado()) || "cancelada".equals(reserva.getEstado())) {
            throw new ReglaNegocioException(409, "Esa solicitud fue " + reserva.getEstado() + ".");
        }
        Sacramento s = sacramentoDAO.buscarPorCodigo(reserva.getSacramento()).orElse(null);
        List<Requisito> requisitos = s == null ? List.of() : requisitoDAO.listar(reserva.getIdParroquia(), s.getIdSacramento());
        List<Cumplimiento> cumplimientos = requisitoDAO.cumplimientos(reserva.getIdReserva());

        List<Item> items = new ArrayList<>();
        int obligatorios = 0, validados = 0;
        for (Requisito q : requisitos) {
            Item item = situacion(q, cumplimientos);
            items.add(item);
            if (q.isObligatorio()) {
                obligatorios++;
                if (VALIDADO.equals(item.estado())) validados++;
            }
        }
        return new Checklist(reserva, items, obligatorios, validados, validados >= obligatorios);
    }

    // ------------------------------------------------------------------ al subir un documento

    /**
     * Comprueba que el documento que se sube puede indicar ese requisito: debe ser de la misma parroquia y sacramento de
     * la solicitud y no tener ya un documento pendiente o validado (uno rechazado sí se puede reemplazar).
     * @return el id del requisito, o null si el texto vino vacío (documento sin requisito)
     */
    public Integer validarParaSubida(Reserva reserva, String requisitoTxt) throws SQLException {
        String t = requisitoTxt == null ? "" : requisitoTxt.trim();
        if (t.isEmpty()) return null;
        int id = entero(t, "Requisito no válido.");
        Optional<Requisito> q = requisitoDAO.buscarPorId(id);
        Optional<Sacramento> s = sacramentoDAO.buscarPorCodigo(reserva.getSacramento());
        if (q.isEmpty() || s.isEmpty() || q.get().getIdParroquia() != reserva.getIdParroquia()
                || q.get().getIdSacramento() != s.get().getIdSacramento()) {
            throw new ReglaNegocioException(400, "Ese requisito no corresponde a esta solicitud.");
        }
        for (Cumplimiento c : requisitoDAO.cumplimientos(reserva.getIdReserva())) {
            if (c.idRequisito() != id) continue;
            if (VALIDADO.equals(c.estado())) throw new ReglaNegocioException(409, "Ese requisito ya fue validado por la parroquia.");
            if (PENDIENTE.equals(c.estado())) throw new ReglaNegocioException(409, "Ese requisito ya tiene un documento pendiente de revisión.");
        }
        return id;
    }

    // ------------------------------------------------------------------ administración

    /**
     * Crea un requisito (idTxt vacío) o edita el nombre, la descripción, si es obligatorio y el orden de uno existente.
     * El sacramento de un requisito no cambia: para eso se elimina y se crea otro.
     */
    public Requisito guardar(int idAdmin, int idParroquia, String idTxt, String sacramentoTxt, String nombreTxt, String descripcionTxt,
                             String obligatorioTxt, String ordenTxt, String ip) throws SQLException {
        String nombre = nombreTxt == null ? "" : nombreTxt.trim();
        if (nombre.length() < 3 || nombre.length() > 80) throw new ReglaNegocioException(400, "El nombre debe tener entre 3 y 80 caracteres.");
        String descripcion = descripcionTxt == null ? "" : descripcionTxt.trim();
        if (descripcion.length() > 255) throw new ReglaNegocioException(400, "La descripción no puede superar los 255 caracteres.");
        boolean obligatorio = obligatorio(obligatorioTxt);
        String id = idTxt == null ? "" : idTxt.trim();

        try {
            if (!id.isEmpty()) {
                Requisito actual = propio(entero(id, "Identificador no válido."), idParroquia);
                int orden = orden(ordenTxt, actual.getOrden());
                requisitoDAO.actualizar(actual.getIdRequisito(), nombre, descripcion.isEmpty() ? null : descripcion, obligatorio, orden);
                actual.setNombre(nombre);
                actual.setDescripcion(descripcion.isEmpty() ? null : descripcion);
                actual.setObligatorio(obligatorio);
                actual.setOrden(orden);
                bitacora.registrar(idAdmin, "REQUISITO_MODIFICADO", "requisito " + actual.getIdRequisito(), ip);
                return actual;
            }

            Sacramento s = sacramentoDAO.buscarPorCodigo(sacramentoTxt == null ? "" : sacramentoTxt.trim())
                    .orElseThrow(() -> new ReglaNegocioException(400, "Elige un sacramento válido."));
            if (!sacramentoDAO.parroquiaOfrece(idParroquia, s.getIdSacramento())) {
                throw new ReglaNegocioException(400, "Tu parroquia no ofrece este sacramento. Actívalo primero en \"Gestionar horarios\".");
            }
            List<Requisito> existentes = requisitoDAO.listar(idParroquia, s.getIdSacramento());
            if (existentes.size() >= MAX_POR_SACRAMENTO) {
                throw new ReglaNegocioException(409, "Ya tienes " + MAX_POR_SACRAMENTO + " requisitos para este sacramento. Elimina o edita alguno.");
            }
            int siguiente = existentes.stream().mapToInt(Requisito::getOrden).max().orElse(0) + 1;
            Requisito nuevo = new Requisito(idParroquia, s.getIdSacramento(), nombre, descripcion.isEmpty() ? null : descripcion, obligatorio,
                    orden(ordenTxt, Math.min(siguiente, 99)));
            requisitoDAO.insertar(nuevo);
            bitacora.registrar(idAdmin, "REQUISITO_CREADO", "requisito " + nuevo.getIdRequisito(), ip);
            return nuevo;
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ReglaNegocioException(409, "Ya existe un requisito con ese nombre para este sacramento.");
        }
    }

    /** Elimina un requisito que ningún documento ha usado; si ya se usó, se edita o se marca como recomendado. */
    public void eliminar(int idAdmin, int idParroquia, String idTxt, String ip) throws SQLException {
        Requisito q = propio(entero(idTxt, "Identificador no válido."), idParroquia);
        int usados = requisitoDAO.contarDocumentos(q.getIdRequisito());
        if (usados > 0) {
            throw new ReglaNegocioException(409, "Ya hay " + usados + " documento(s) enviados para este requisito. No lo elimines: edítalo o márcalo como recomendado.");
        }
        requisitoDAO.eliminar(q.getIdRequisito());
        bitacora.registrar(idAdmin, "REQUISITO_ELIMINADO", "requisito " + q.getIdRequisito(), ip);
    }

    // ------------------------------------------------------------------ auxiliares

    /** De varios documentos para un mismo requisito gana el validado, luego el pendiente y luego el último rechazado. */
    private static Item situacion(Requisito q, List<Cumplimiento> cumplimientos) {
        Cumplimiento pendiente = null, rechazado = null;
        for (Cumplimiento c : cumplimientos) { // del más reciente al más antiguo
            if (c.idRequisito() != q.getIdRequisito()) continue;
            if (VALIDADO.equals(c.estado())) return new Item(q, VALIDADO, c.idDocumento(), null);
            if (PENDIENTE.equals(c.estado()) && pendiente == null) pendiente = c;
            if (RECHAZADO.equals(c.estado()) && rechazado == null) rechazado = c;
        }
        if (pendiente != null) return new Item(q, PENDIENTE, pendiente.idDocumento(), null);
        if (rechazado != null) return new Item(q, RECHAZADO, rechazado.idDocumento(), rechazado.motivo());
        return new Item(q, FALTANTE, null, null);
    }

    /** Un requisito de otra parroquia se trata como inexistente (no se revela que existe). */
    private Requisito propio(int id, int idParroquia) throws SQLException {
        Optional<Requisito> q = requisitoDAO.buscarPorId(id);
        if (q.isEmpty() || q.get().getIdParroquia() != idParroquia) throw new ReglaNegocioException(404, "El requisito no existe.");
        return q.get();
    }

    private static boolean obligatorio(String t) {
        String v = t == null ? "" : t.trim().toLowerCase();
        if (v.isEmpty() || v.equals("true") || v.equals("1") || v.equals("on")) return true;
        if (v.equals("false") || v.equals("0")) return false;
        throw new ReglaNegocioException(400, "Indica si el requisito es obligatorio o recomendado.");
    }

    private static int orden(String t, int porDefecto) {
        String v = t == null ? "" : t.trim();
        if (v.isEmpty()) return porDefecto;
        int o;
        try {
            o = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "El orden debe ser un número entero.");
        }
        if (o < 1 || o > 99) throw new ReglaNegocioException(400, "El orden debe estar entre 1 y 99.");
        return o;
    }

    private static int entero(String t, String mensaje) {
        try {
            return Integer.parseInt(t == null ? "" : t.trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, mensaje);
        }
    }
}
