package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Aviso;
import pe.caminoiluminacion.servicio.ContenidoService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.HashMap;

/**
 * El administrador publica y elimina avisos de SU parroquia.
 * GET    /api/admin/avisos          : los avisos de su parroquia.
 * POST   /api/admin/avisos          : publica un aviso (titulo, descripcion, tipo, fecha, urgente).
 * DELETE /api/admin/avisos?id=N     : elimina uno de su parroquia (los de otras se tratan como inexistentes).
 */
@WebServlet("/api/admin/avisos")
public class AvisosAdminServlet extends HttpServlet {

    private final ContenidoService contenido;

    public AvisosAdminServlet() {
        this(Servicios.contenido());
    }

    AvisosAdminServlet(ContenidoService contenido) {
        this.contenido = contenido;
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
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(AvisosServlet.aJson(contenido.avisosDe(idParroquia), new HashMap<>()));
        } catch (SQLException e) {
            Registro.error("Error al listar avisos del panel", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar los avisos.");
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
        String urgente = valor(request, "urgente");
        try {
            // La parroquia sale de la sesión: nunca de un parámetro del cliente.
            Aviso nuevo = contenido.publicarAviso(Sesion.idUsuario(request), idParroquia, request.getParameter("titulo"),
                    request.getParameter("descripcion"), request.getParameter("tipo"), request.getParameter("fecha"),
                    "true".equals(urgente) || "on".equals(urgente), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + nuevo.getIdAviso() + "}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al publicar aviso", e);
            Respuesta.errorInterno(response, out, "No se pudo publicar el aviso.");
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
            contenido.eliminarAviso(Sesion.idUsuario(request), idParroquia, request.getParameter("id"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al eliminar aviso", e);
            Respuesta.errorInterno(response, out, "No se pudo eliminar el aviso.");
        }
    }

    private static String valor(HttpServletRequest r, String nombre) {
        String v = r.getParameter(nombre);
        return v == null ? "" : v.trim();
    }
}
