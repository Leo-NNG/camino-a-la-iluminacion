package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.AvisoDAO;
import pe.caminoiluminacion.dao.impl.AvisoDAOImpl;
import pe.caminoiluminacion.modelo.Aviso;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Json;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * El administrador publica y elimina avisos de SU parroquia.
 * GET /api/admin/avisos            : los avisos de su parroquia.
 * POST /api/admin/avisos           : publica un aviso (titulo, descripcion, tipo, fecha, urgente).
 * DELETE /api/admin/avisos?id=N    : elimina uno de su parroquia (los de otras se tratan como inexistentes).
 */
@WebServlet("/api/admin/avisos")
public class AvisosAdminServlet extends HttpServlet {

    private static final Set<String> TIPOS = Set.of("santa_cena", "ayuno", "vigilia", "celebracion", "cambio_horario", "otro");

    private final AvisoDAO avisoDAO;

    public AvisosAdminServlet() {
        this(new AvisoDAOImpl());
    }

    AvisosAdminServlet(AvisoDAO avisoDAO) {
        this.avisoDAO = avisoDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            List<Aviso> lista = avisoDAO.listarPorParroquia(idParroquia);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(AvisosServlet.aJson(lista, new HashMap<>()));
        } catch (SQLException e) {
            getServletContext().log("Error al listar avisos del panel", e);
            AccesoAdmin.responder(response, out, 500, "No se pudieron cargar los avisos.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;

        String titulo = valor(request, "titulo");
        String descripcion = valor(request, "descripcion");
        String tipo = valor(request, "tipo");
        String fechaTxt = valor(request, "fecha");
        boolean urgente = "true".equals(valor(request, "urgente")) || "on".equals(valor(request, "urgente"));

        if (titulo.isEmpty() || titulo.length() > 150) { AccesoAdmin.responder(response, out, 400, "El título es obligatorio (hasta 150 caracteres)."); return; }
        if (descripcion.isEmpty() || descripcion.length() > 2000) { AccesoAdmin.responder(response, out, 400, "La descripción es obligatoria (hasta 2000 caracteres)."); return; }
        if (!TIPOS.contains(tipo)) { AccesoAdmin.responder(response, out, 400, "Elige un tipo de aviso válido."); return; }
        Date fecha = null;
        if (!fechaTxt.isEmpty()) {
            try {
                fecha = Date.valueOf(LocalDate.parse(fechaTxt));
            } catch (DateTimeParseException e) {
                AccesoAdmin.responder(response, out, 400, "La fecha del evento no es válida.");
                return;
            }
        }
        try {
            // La parroquia sale de la sesión: nunca de un parámetro del cliente.
            Aviso nuevo = new Aviso(idParroquia, titulo, descripcion, tipo, urgente, fecha);
            avisoDAO.insertar(nuevo);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + nuevo.getIdAviso() + "}");
        } catch (SQLException e) {
            getServletContext().log("Error al publicar aviso", e);
            AccesoAdmin.responder(response, out, 500, "No se pudo publicar el aviso.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            int id = Integer.parseInt(String.valueOf(request.getParameter("id")).trim());
            Optional<Aviso> aviso = avisoDAO.buscarPorId(id);
            if (aviso.isEmpty() || !idParroquia.equals(aviso.get().getIdParroquia())) {
                AccesoAdmin.responder(response, out, 404, "El aviso no existe.");
                return;
            }
            avisoDAO.eliminar(id);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (NumberFormatException e) {
            AccesoAdmin.responder(response, out, 400, "Identificador no válido.");
        } catch (SQLException e) {
            getServletContext().log("Error al eliminar aviso", e);
            AccesoAdmin.responder(response, out, 500, "No se pudo eliminar el aviso.");
        }
    }

    private static String valor(HttpServletRequest r, String nombre) {
        String v = r.getParameter(nombre);
        return v == null ? "" : v.trim();
    }
}
