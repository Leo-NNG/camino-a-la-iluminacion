package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.FechaDisponibleDAO;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.modelo.FechaDisponible;
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

    private static final java.time.format.DateTimeFormatter FECHA_AVISO = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Una reserva propia con el nombre de la parroquia (para "Mis solicitudes"). */
    public record ReservaPropia(Reserva reserva, String parroquia) { }

    /** Una reserva pendiente con los datos de contacto de quien la pidió (para el panel). */
    public record ReservaPendiente(Reserva reserva, String contacto) { }

    /**
     * Qué fechas puede elegir el feligrés. {@code publicadas} = la parroquia publicó fechas para ese sacramento: solo
     * vale elegir entre {@code fechas} (las que aún tienen cupo). Si es false, la fecha es libre y la parroquia la valida.
     */
    public record Disponibilidad(boolean publicadas, List<FechaDisponible> fechas) { }

    private final ReservaDAO reservaDAO;
    private final FechaDisponibleDAO fechaDAO;
    private final ParroquiaDAO parroquiaDAO;
    private final SacramentoDAO sacramentoDAO;
    private final UsuarioDAO usuarioDAO;
    private final BitacoraService bitacora;
    private final Avisador avisador;
    private final Supplier<LocalDate> hoy;

    public ReservaService(ReservaDAO reservaDAO, FechaDisponibleDAO fechaDAO, ParroquiaDAO parroquiaDAO,
                          SacramentoDAO sacramentoDAO, UsuarioDAO usuarioDAO, BitacoraService bitacora) {
        this(reservaDAO, fechaDAO, parroquiaDAO, sacramentoDAO, usuarioDAO, bitacora, LocalDate::now);
    }

    public ReservaService(ReservaDAO reservaDAO, FechaDisponibleDAO fechaDAO, ParroquiaDAO parroquiaDAO,
                          SacramentoDAO sacramentoDAO, UsuarioDAO usuarioDAO, BitacoraService bitacora,
                          Supplier<LocalDate> hoy) {
        this(reservaDAO, fechaDAO, parroquiaDAO, sacramentoDAO, usuarioDAO, bitacora, hoy, Avisador.ninguno());
    }

    public ReservaService(ReservaDAO reservaDAO, FechaDisponibleDAO fechaDAO, ParroquiaDAO parroquiaDAO,
                          SacramentoDAO sacramentoDAO, UsuarioDAO usuarioDAO, BitacoraService bitacora,
                          Supplier<LocalDate> hoy, Avisador avisador) {
        this.avisador = avisador;
        this.reservaDAO = reservaDAO;
        this.fechaDAO = fechaDAO;
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

        // Si la parroquia publicó fechas para este sacramento, solo se puede reservar en ellas y hasta agotar su cupo.
        // Si no publicó ninguna, la fecha es libre (cupo 1) y la parroquia la valida al confirmar.
        List<FechaDisponible> publicadas = fechaDAO.listarDesde(parroquia.getIdParroquia(), sacramento.getIdSacramento(), hoyDia.plusDays(1));
        int cupo = 1;
        if (!publicadas.isEmpty()) {
            FechaDisponible elegida = publicadas.stream().filter(f -> f.getFecha().equals(fecha)).findFirst()
                    .orElseThrow(() -> new ReglaNegocioException(400,
                            "Esa fecha no está entre las que la parroquia tiene disponibles. Elige una de la lista."));
            cupo = elegida.getCupo();
        }

        Reserva nueva = new Reserva(idUsuario, parroquia.getIdParroquia(), sacramento.getCodigo(), nombre,
                Date.valueOf(fecha), observaciones.isEmpty() ? null : observaciones);
        if (!reservaDAO.insertarSiDisponible(nueva, cupo)) {
            throw new ReglaNegocioException(409, publicadas.isEmpty()
                    ? "Esa fecha ya no está disponible para ese sacramento en esa parroquia. Elige otra fecha."
                    : "Esa fecha ya no tiene cupo. Elige otra de las fechas disponibles.");
        }
        bitacora.registrar(idUsuario, "RESERVA_CREADA", "reserva " + nueva.getIdReserva(), ip);
        return nueva;
    }

    /**
     * Fechas que el feligrés puede elegir para un sacramento en una parroquia: las publicadas que aún tienen cupo,
     * o ninguna restricción si la parroquia no publicó fechas (opción de fecha libre).
     */
    public Disponibilidad disponibilidad(String idParroquiaTxt, String sacramentoTxt) throws SQLException {
        Sacramento sacramento = resolverSacramento(sacramentoTxt);
        Parroquia parroquia = buscarParroquia(idParroquiaTxt);
        if (parroquia == null) throw new ReglaNegocioException(400, "Selecciona una parroquia válida.");
        List<FechaDisponible> publicadas = fechaDAO.listarDesde(parroquia.getIdParroquia(), sacramento.getIdSacramento(), hoy.get().plusDays(1));
        List<FechaDisponible> conCupo = new ArrayList<>();
        for (FechaDisponible f : publicadas) if (f.getLibres() > 0) conCupo.add(f);
        return new Disponibilidad(!publicadas.isEmpty(), conCupo);
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
        avisarResolucion(reserva.get(), estado, razon);
    }

    /** Avisa al feligrés (RF13) que la parroquia confirmó o rechazó su solicitud; en el rechazo le muestra el motivo. */
    private void avisarResolucion(Reserva r, String estado, String motivo) throws SQLException {
        if (r.getIdUsuario() == null) return;
        String sacramento = sacramentoDAO.buscarPorCodigo(r.getSacramento()).map(Sacramento::getNombre).orElse(r.getSacramento());
        String parroquia = parroquiaDAO.buscarPorId(r.getIdParroquia()).map(Parroquia::getNombre).orElse("la parroquia");
        String fecha = r.getFechaSolicitada() == null ? "" : " para el " + r.getFechaSolicitada().toLocalDate().format(FECHA_AVISO);
        String base = "Tu solicitud de " + sacramento + " de " + r.getNombreSolicitante() + fecha;
        if ("rechazada".equals(estado)) {
            avisador.avisar(r.getIdUsuario(), "Reserva rechazada", base + " fue rechazada por " + parroquia + ": " + motivo
                    + ". Puedes solicitar otra fecha.");
        } else {
            avisador.avisar(r.getIdUsuario(), "Reserva confirmada", base + " fue confirmada por " + parroquia
                    + ". Sube tus documentos en «Mis documentos» si aún no lo hiciste.");
        }
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
        if (x.estaEliminado()) return Usuario.CUENTA_ELIMINADA;
        StringBuilder sb = new StringBuilder(x.nombreParaMostrar());
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
