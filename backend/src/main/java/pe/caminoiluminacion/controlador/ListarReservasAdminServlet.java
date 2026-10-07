package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.dao.impl.ReservaDAOImpl;
import pe.caminoiluminacion.dao.impl.UsuarioDAOImpl;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Panel del administrador: lista las reservas pendientes de SU parroquia y permite
 * confirmarlas o rechazarlas. Además del FiltroAdmin, cada método vuelve a comprobar
 * el rol y la parroquia (defensa en profundidad).
 */
@WebServlet("/api/admin/reservas")
public class ListarReservasAdminServlet extends HttpServlet {

    private final ReservaDAO reservaDAO;
    private final UsuarioDAO usuarioDAO;

    public ListarReservasAdminServlet() {
        this(new ReservaDAOImpl(), new UsuarioDAOImpl());
    }

    ListarReservasAdminServlet(ReservaDAO reservaDAO, UsuarioDAO usuarioDAO) {
        this.reservaDAO = reservaDAO;
        this.usuarioDAO = usuarioDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = autorizar(request, response, out);
        if (idParroquia == null) return;

        try {
            List<Reserva> lista = reservaDAO.listarPorEstadoYParroquia("pendiente", idParroquia);

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                Reserva r = lista.get(i);
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\":").append(r.getIdReserva()).append(",")
                    .append("\"sacramento\":\"").append(Json.esc(r.getSacramento())).append("\",")
                    .append("\"nombreSolicitante\":\"").append(Json.esc(r.getNombreSolicitante())).append("\",")
                    .append("\"fecha\":\"").append(Json.esc(r.getFechaSolicitada())).append("\",")
                    .append("\"estado\":\"").append(Json.esc(r.getEstado())).append("\",")
                    .append("\"notas\":\"").append(Json.esc(r.getNotas())).append("\",")
                    .append("\"contacto\":\"").append(Json.esc(contacto(r.getIdUsuario()))).append("\"")
                    .append("}");
            }
            json.append("]");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json);

        } catch (SQLException e) {
            getServletContext().log("Error al listar reservas", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(Json.error("No se pudieron cargar las reservas."));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = autorizar(request, response, out);
        if (idParroquia == null) return;

        String estado = request.getParameter("estado");
        if (!"confirmada".equals(estado) && !"rechazada".equals(estado)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(Json.error("Estado no válido."));
            return;
        }
        String motivo = request.getParameter("motivo");
        motivo = motivo == null ? "" : motivo.trim();
        if ("rechazada".equals(estado)) {
            if (motivo.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(Json.error("Indica el motivo del rechazo: el feligrés lo verá."));
                return;
            }
            if (motivo.length() > 255) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(Json.error("El motivo no puede superar los 255 caracteres."));
                return;
            }
        }
        try {
            int id = Integer.parseInt(request.getParameter("id"));
            Optional<Reserva> reserva = reservaDAO.buscarPorId(id);
            // Una reserva de otra parroquia se trata como inexistente (no se revela que existe).
            if (reserva.isEmpty() || !idParroquia.equals(reserva.get().getIdParroquia())) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(Json.error("La reserva no existe."));
                return;
            }
            if (!reservaDAO.resolverPendiente(id, estado, motivo)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                out.print(Json.error("Esta solicitud ya fue resuelta."));
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(Json.error("Identificador no válido."));
        } catch (SQLException e) {
            getServletContext().log("Error al actualizar la reserva", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(Json.error("No se pudo actualizar la reserva."));
        }
    }

    /** Datos para que el personal pueda comunicarse con quien hizo la solicitud. */
    private String contacto(Integer idUsuario) {
        try {
            Optional<Usuario> u = usuarioDAO.buscarPorId(idUsuario);
            if (u.isEmpty()) return "";
            Usuario x = u.get();
            StringBuilder sb = new StringBuilder(x.getNombre()).append(' ').append(x.getApellido());
            if (x.getTelefono() != null && !x.getTelefono().isBlank()) sb.append(" · ").append(x.getTelefono());
            if (x.getCorreo() != null) sb.append(" · ").append(x.getCorreo());
            return sb.toString();
        } catch (SQLException e) {
            return "";
        }
    }

    /** @return la parroquia del administrador, o null si ya respondió con el error correspondiente. */
    private static Integer autorizar(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        if (!Sesion.hayUsuario(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(Json.error("Inicia sesión para continuar."));
            return null;
        }
        if (!Sesion.esAdministrador(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print(Json.error("Solo el personal parroquial puede ver esta información."));
            return null;
        }
        Integer idParroquia = Sesion.idParroquia(request);
        if (idParroquia == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print(Json.error("Tu cuenta de personal no tiene una parroquia asignada."));
            return null;
        }
        return idParroquia;
    }
}
