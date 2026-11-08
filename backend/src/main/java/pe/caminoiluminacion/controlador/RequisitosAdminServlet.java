package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Requisito;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.RequisitoService;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * El administrador define los documentos que SU parroquia pide para cada sacramento.
 * GET    /api/admin/requisitos        : todos los de su parroquia.
 * POST   /api/admin/requisitos        : crea (sacramento, nombre, descripcion, obligatorio, orden) o, con id, edita.
 * DELETE /api/admin/requisitos?id=N   : elimina uno que ningún documento haya usado.
 */
@WebServlet("/api/admin/requisitos")
public class RequisitosAdminServlet extends HttpServlet {

    private final RequisitoService requisitos;

    public RequisitosAdminServlet() {
        this(Servicios.requisitos());
    }

    RequisitosAdminServlet(RequisitoService requisitos) {
        this.requisitos = requisitos;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(aJson(requisitos.deParroquia(idParroquia)));
        } catch (SQLException e) {
            Registro.error("Error al listar los requisitos del panel", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar los requisitos.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            Requisito r = requisitos.guardar(Sesion.idUsuario(request), idParroquia, request.getParameter("id"), request.getParameter("sacramento"),
                    request.getParameter("nombre"), request.getParameter("descripcion"), request.getParameter("obligatorio"),
                    request.getParameter("orden"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + r.getIdRequisito() + "}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al guardar el requisito", e);
            Respuesta.errorInterno(response, out, "No se pudo guardar el requisito.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            requisitos.eliminar(Sesion.idUsuario(request), idParroquia, request.getParameter("id"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al eliminar el requisito", e);
            Respuesta.errorInterno(response, out, "No se pudo eliminar el requisito.");
        }
    }

    static String aJson(List<Requisito> lista) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lista.size(); i++) {
            Requisito q = lista.get(i);
            if (i > 0) json.append(",");
            json.append("{\"id\":").append(q.getIdRequisito())
                .append(",\"sacramento\":\"").append(Json.esc(q.getCodigoSacramento())).append("\"")
                .append(",\"nombreSacramento\":\"").append(Json.esc(q.getNombreSacramento())).append("\"")
                .append(",\"nombre\":\"").append(Json.esc(q.getNombre())).append("\"")
                .append(",\"descripcion\":\"").append(Json.esc(q.getDescripcion())).append("\"")
                .append(",\"obligatorio\":").append(q.isObligatorio())
                .append(",\"orden\":").append(q.getOrden()).append("}");
        }
        return json.append("]").toString();
    }
}
