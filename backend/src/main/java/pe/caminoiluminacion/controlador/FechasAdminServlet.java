package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.FechaDisponible;
import pe.caminoiluminacion.servicio.FechaDisponibleService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
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
 * El administrador publica las fechas en que SU parroquia celebra cada sacramento.
 * GET    /api/admin/fechas-disponibles          : las suyas, desde hoy, con cupo y solicitudes ocupadas.
 * POST   /api/admin/fechas-disponibles          : crea (sacramento, fecha, hora, cupo) o, con id, ajusta cupo y hora.
 * DELETE /api/admin/fechas-disponibles?id=N     : retira una fecha sin solicitudes activas.
 */
@WebServlet("/api/admin/fechas-disponibles")
public class FechasAdminServlet extends HttpServlet {

    private final FechaDisponibleService fechas;

    public FechasAdminServlet() {
        this(Servicios.fechas());
    }

    FechasAdminServlet(FechaDisponibleService fechas) {
        this.fechas = fechas;
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
            out.print(aJson(fechas.listar(idParroquia)));
        } catch (SQLException e) {
            Registro.error("Error al listar las fechas disponibles del panel", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar las fechas.");
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
        try {
            FechaDisponible f = fechas.guardar(Sesion.idUsuario(request), idParroquia, request.getParameter("id"),
                    request.getParameter("sacramento"), request.getParameter("fecha"), request.getParameter("hora"),
                    request.getParameter("cupo"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + f.getIdFecha() + "}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al guardar la fecha disponible", e);
            Respuesta.errorInterno(response, out, "No se pudo guardar la fecha.");
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
            fechas.eliminar(Sesion.idUsuario(request), idParroquia, request.getParameter("id"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al eliminar la fecha disponible", e);
            Respuesta.errorInterno(response, out, "No se pudo eliminar la fecha.");
        }
    }

    static String aJson(List<FechaDisponible> lista) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lista.size(); i++) {
            FechaDisponible f = lista.get(i);
            if (i > 0) json.append(",");
            json.append("{\"id\":").append(f.getIdFecha())
                .append(",\"sacramento\":\"").append(Json.esc(f.getCodigoSacramento())).append("\"")
                .append(",\"nombre\":\"").append(Json.esc(f.getNombreSacramento())).append("\"")
                .append(",\"fecha\":\"").append(f.getFecha()).append("\"")
                .append(",\"hora\":\"").append(f.getHoraInicio() == null ? "" : Json.esc(f.getHoraInicio().toString())).append("\"")
                .append(",\"cupo\":").append(f.getCupo())
                .append(",\"ocupados\":").append(f.getOcupados()).append("}");
        }
        return json.append("]").toString();
    }
}
