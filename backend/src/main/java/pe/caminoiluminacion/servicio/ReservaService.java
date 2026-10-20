package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.modelo.Sacramento;
import pe.caminoiluminacion.modelo.Usuario;

import java.sql.Date;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Reglas de las reservas de sacramentos: crear, ver las propias y confirmar o rechazar las de una parroquia. */
public class ReservaService {

    /** Una reserva propia con el nombre de la parroquia (para "Mis solicitudes"). */
    public record ReservaPropia(Reserva reserva, String parroquia) { }

    /** Una reserva pendiente con los datos de contacto de quien la pidió (para el panel). */
    public record ReservaPendiente(Reserva reserva, String contacto) { }

    private final ReservaDAO reservaDAO;
    private final ParroquiaDAO parroquiaDAO;
    private final SacramentoDAO sacramentoDAO;
    private final UsuarioDAO usuarioDAO;
    private final BitacoraService bitacora;
    private final Supplier<LocalDate> hoy;

    public ReservaService(ReservaDAO reservaDAO, ParroquiaDAO parroquiaDAO, SacramentoDAO sacramentoDAO,
                          UsuarioDAO usuarioDAO, BitacoraService bitacora) {
        this(reservaDAO, parroquiaDAO, sacramentoDAO, usuarioDAO, bitacora, LocalDate::now);
    }

    public ReservaService(ReservaDAO reservaDAO, ParroquiaDAO parroquiaDAO, SacramentoDAO sacramentoDAO,
                          UsuarioDAO usuarioDAO, BitacoraService bitacora, Supplier<LocalDate> hoy) {
        this.reservaDAO = reservaDAO;
        this.parroquiaDAO = parroquiaDAO;
        this.sacramentoDAO = sacramentoDAO;
        this.usuarioDAO = usuarioDAO;
        this.bitacora = bitacora;
        this.hoy = hoy;
    }

    /** Registra una solicitud de reserva de un feligrés. Lanza ReglaNegocioException si algo no cumple. */
    public Reserva crear(int idUsuario, String sacramentoTxt, String idParroquiaTxt, String nombreSolicitante,
                         String fechaTxt, String notas, String ip) throws SQLException {
        Sacramento sacramento = resolverSacramento(sacramentoTxt);
        String nombre = nombreSolicitante == null ? "" : nombreSolicitante.trim();
        String observaciones = notas == null ? "" : notas.trim();
        if (nombre.isEmpty()) throw new ReglaNegocioException(400, "Indica el nombre del solicitante.");

        LocalDate fecha;
        try {
            fecha = LocalDate.parse(fechaTxt == null ? "" : fechaTxt.trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(400, "La fecha no es válida.");
        }
        LocalDate hoyDia = hoy.get();
        if (!fecha.isAfter(hoyDia)) throw new ReglaNegocioException(400, "La fecha debe ser futura.");
        if (fecha.isAfter(hoyDia.plusYears(2))) throw new ReglaNegocioException(400, "Solo se puede reservar con hasta 2 años de anticipación.");
        if (nombre.length() > 150) throw new ReglaNegocioException(400, "El nombre del solicitante es demasiado largo.");
        if (observaciones.length() > 500) throw new ReglaNegocioException(400, "Las notas no pueden superar los 500 caracteres.");

        Parroquia parroquia = buscarParroquia(idParroquiaTxt);
        if (parroquia == null) throw new ReglaNegocioException(400, "Selecciona una parroquia válida.");
        if (!sacramentoDAO.parroquiaOfrece(parroquia.getIdParroquia(), sacramento.getIdSacramento())) {
            throw new ReglaNegocioException(400, "Esa parroquia no ofrece este sacramento. Elige otra parroquia.");
        }

        Reserva nueva = new Reserva(idUsuario, parroquia.getIdParroquia(), sacramento.getCodigo(), nombre,
                Date.valueOf(fecha), observaciones.isEmpty() ? null : observaciones);
        if (!reservaDAO.insertarSiDisponible(nueva)) {
            throw new ReglaNegocioException(409, "Esa fecha ya no está disponible para ese sacramento en esa parroquia. Elige otra fecha.");
        }
        bitacora.registrar(idUsuario, "RESERVA_CREADA", "reserva " + nueva.getIdReserva(), ip);
        return nueva;
    }

    /** Las reservas del propio usuario (nunca se filtra por un dato que mande el cliente). */
    public List<ReservaPropia> delUsuario(int idUsuario) throws SQLException {
        Map<Integer, String> nombres = new HashMap<>();
        for (Parroquia p : parroquiaDAO.listarTodas()) nombres.put(p.getIdParroquia(), p.getNombre());
        List<ReservaPropia> lista = new ArrayList<>();
        for (Reserva r : reservaDAO.listarPorUsuario(idUsuario)) lista.add(new ReservaPropia(r, nombres.get(r.getIdParroquia())));
        return lista;
    }

    /** Las solicitudes pendientes de UNA parroquia, con el contacto de quien las pidió. */
    public List<ReservaPendiente> pendientesDeParroquia(int idParroquia) throws SQLException {
        List<ReservaPendiente> lista = new ArrayList<>();
        for (Reserva r : reservaDAO.listarPorEstadoYParroquia("pendiente", idParroquia)) {
            lista.add(new ReservaPendiente(r, contacto(r.getIdUsuario())));
        }
        return lista;
    }

    /** Confirma o rechaza una solicitud PENDIENTE de la parroquia del administrador. */
    public void resolver(int idAdmin, int idParroquiaAdmin, String idReservaTxt, String estado, String motivo, String ip)
            throws SQLException {
        if (!"confirmada".equals(estado) && !"rechazada".equals(estado)) throw new ReglaNegocioException(400, "Estado no válido.");
        String razon = motivo == null ? "" : motivo.trim();
        if ("rechazada".equals(estado)) {
            if (razon.isEmpty()) throw new ReglaNegocioException(400, "Indica el motivo del rechazo: el feligrés lo verá.");
            if (razon.length() > 255) throw new ReglaNegocioException(400, "El motivo no puede superar los 255 caracteres.");
        }
        int id = entero(idReservaTxt);
        Optional<Reserva> reserva = reservaDAO.buscarPorId(id);
        // Una reserva de otra parroquia se trata como inexistente (no se revela que existe).
        if (reserva.isEmpty() || reserva.get().getIdParroquia() != idParroquiaAdmin) {
            throw new ReglaNegocioException(404, "La reserva no existe.");
        }
        if (!reservaDAO.resolverPendiente(id, estado, razon)) throw new ReglaNegocioException(409, "Esta solicitud ya fue resuelta.");
        bitacora.registrar(idAdmin, "rechazada".equals(estado) ? "RESERVA_RECHAZADA" : "RESERVA_CONFIRMADA", "reserva " + id, ip);
    }

    // ---------------------------------------------------------------- auxiliares

    /** Acepta el código (primera_comunion) o el nombre (Primera Comunión), sin importar tildes ni mayúsculas. */
    private Sacramento resolverSacramento(String texto) throws SQLException {
        String buscado = normalizar(texto == null ? "" : texto.replace('_', ' '));
        if (!buscado.isEmpty()) {
            for (Sacramento s : sacramentoDAO.listarTodos()) {
                if (normalizar(s.getNombre()).equals(buscado) || normalizar(s.getCodigo().replace('_', ' ')).equals(buscado)) return s;
            }
        }
        throw new ReglaNegocioException(400, "Sacramento no válido.");
    }

    private Parroquia buscarParroquia(String idTexto) throws SQLException {
        try {
            return parroquiaDAO.buscarPorId(Integer.parseInt(idTexto == null ? "" : idTexto.trim())).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String contacto(Integer idUsuario) throws SQLException {
        Optional<Usuario> u = idUsuario == null ? Optional.empty() : usuarioDAO.buscarPorId(idUsuario);
        if (u.isEmpty()) return "";
        Usuario x = u.get();
        StringBuilder sb = new StringBuilder(x.getNombre()).append(' ').append(x.getApellido());
        if (x.getTelefono() != null && !x.getTelefono().isBlank()) sb.append(" · ").append(x.getTelefono());
        if (x.getCorreo() != null) sb.append(" · ").append(x.getCorreo());
        return sb.toString();
    }

    private static int entero(String t) {
        try {
            return Integer.parseInt(t == null ? "" : t.trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "Identificador no válido.");
        }
    }

    private static String normalizar(String t) {
        return Normalizer.normalize(t.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }
}
