package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.DocumentoDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.dao.impl.DocumentoDAOImpl;
import pe.caminoiluminacion.dao.impl.ReservaDAOImpl;
import pe.caminoiluminacion.dao.impl.UsuarioDAOImpl;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Panel del administrador: documentos pendientes de SU parroquia, para validarlos o rechazarlos (con motivo). */
@WebServlet("/api/admin/documentos")
public class DocumentosAdminServlet extends HttpServlet {

    private final DocumentoDAO documentoDAO;
    private final ReservaDAO reservaDAO;
    private final UsuarioDAO usuarioDAO;

    public DocumentosAdminServlet() {
        this(new DocumentoDAOImpl(), new ReservaDAOImpl(), new UsuarioDAOImpl());
    }

    DocumentosAdminServlet(DocumentoDAO documentoDAO, ReservaDAO reservaDAO, UsuarioDAO usuarioDAO) {
        this.documentoDAO = documentoDAO;
        this.reservaDAO = reservaDAO;
        this.usuarioDAO = usuarioDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        Integer idParroquia = autorizar(request, response, out);
        if (idParroquia == null) return;

        try {
            List<Documento> docs = documentoDAO.listarPendientesPorParroquia(idParroquia);
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < docs.size(); i++) {
                Documento d = docs.get(i);
                Optional<Reserva> r = reservaDAO.buscarPorId(d.getIdReserva());
                Optional<Usuario> u = usuarioDAO.buscarPorId(d.getIdUsuario());
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\":").append(d.getIdDocumento()).append(",")
                    .append("\"feligres\":\"").append(Json.esc(u.map(x -> x.getNombre() + " " + x.getApellido()).orElse(""))).append("\",")
                    .append("\"nombre\":\"").append(Json.esc(d.getNombreArchivo())).append("\",")
                    .append("\"sacramento\":\"").append(Json.esc(r.map(Reserva::getSacramento).orElse(""))).append("\",")
                    .append("\"solicitante\":\"").append(Json.esc(r.map(Reserva::getNombreSolicitante).orElse(""))).append("\",")
                    .append("\"fechaReserva\":\"").append(Json.fecha(r.map(Reserva::getFechaSolicitada).orElse(null))).append("\",")
                    .append("\"fechaEnvio\":\"").append(Json.fecha(d.getFechaEnvio())).append("\",")
                    .append("\"estado\":\"").append(Json.esc(d.getEstadoValidacion())).append("\"")
                    .append("}");
            }
            json.append("]");
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json);
        } catch (SQLException e) {
            getServletContext().log("Error al listar documentos pendientes", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(Json.error("No se pudieron cargar los documentos."));
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
        if (!"validado".equals(estado) && !"rechazado".equals(estado)) {
            error(response, out, 400, "Estado no válido.");
            return;
        }
        String motivo = request.getParameter("motivo");
        motivo = motivo == null ? "" : motivo.trim();
        if ("rechazado".equals(estado)) {
            if (motivo.isEmpty()) { error(response, out, 400, "Indica el motivo del rechazo: el feligrés lo verá."); return; }
            if (motivo.length() > 255) { error(response, out, 400, "El motivo no puede superar los 255 caracteres."); return; }
        }
        try {
            int id = Integer.parseInt(request.getParameter("id"));
            Optional<Documento> doc = documentoDAO.buscarPorId(id);
            Optional<Reserva> reserva = (doc.isPresent() && doc.get().getIdReserva() != null)
                    ? reservaDAO.buscarPorId(doc.get().getIdReserva()) : Optional.empty();
            // Un documento de otra parroquia se trata como inexistente.
            if (reserva.isEmpty() || !idParroquia.equals(reserva.get().getIdParroquia())) {
                error(response, out, 404, "El documento no existe.");
                return;
            }
            if (!documentoDAO.resolverPendiente(id, estado, motivo)) {
                error(response, out, 409, "Este documento ya fue resuelto.");
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (NumberFormatException e) {
            error(response, out, 400, "Identificador no válido.");
        } catch (SQLException e) {
            getServletContext().log("Error al resolver el documento", e);
            error(response, out, 500, "No se pudo actualizar el documento.");
        }
    }

    /** @return la parroquia del administrador, o null si ya respondió con el error correspondiente. */
    private static Integer autorizar(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        if (!Sesion.hayUsuario(request)) { error(response, out, 401, "Inicia sesión para continuar."); return null; }
        if (!Sesion.esAdministrador(request)) { error(response, out, 403, "Solo el personal parroquial puede ver esta información."); return null; }
        Integer idParroquia = Sesion.idParroquia(request);
        if (idParroquia == null) { error(response, out, 403, "Tu cuenta de personal no tiene una parroquia asignada."); return null; }
        return idParroquia;
    }

    private static void error(HttpServletResponse resp, PrintWriter out, int estado, String msg) {
        resp.setStatus(estado);
        out.print(Json.error(msg));
    }
}
