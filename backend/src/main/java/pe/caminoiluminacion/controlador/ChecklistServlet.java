package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Requisito;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.RequisitoService;
import pe.caminoiluminacion.servicio.RequisitoService.Checklist;
import pe.caminoiluminacion.servicio.RequisitoService.Item;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * GET /api/reservas/checklist?reserva=ID : los requisitos de una solicitud DEL USUARIO DE LA SESIÓN y cuáles ya están
 * cubiertos (faltante, pendiente, validado o rechazado con su motivo).
 */
@WebServlet("/api/reservas/checklist")
public class ChecklistServlet extends HttpServlet {

    private final RequisitoService requisitos;

    public ChecklistServlet() {
        this(Servicios.requisitos());
    }

    ChecklistServlet(RequisitoService requisitos) {
        this.requisitos = requisitos;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return; }
        if (!Sesion.esFeligres(request)) { Respuesta.error(response, out, 403, "Esta sección es para feligreses."); return; }
        try {
            Checklist c = requisitos.checklist(Sesion.idUsuario(request), request.getParameter("reserva"));
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(aJson(c));
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al armar el checklist", e);
            Respuesta.errorInterno(response, out, "No se pudo cargar la lista de requisitos.");
        }
    }

    static String aJson(Checklist c) {
        StringBuilder json = new StringBuilder("{\"obligatorios\":").append(c.obligatorios())
                .append(",\"validados\":").append(c.validados())
                .append(",\"completo\":").append(c.completo()).append(",\"items\":[");
        for (int i = 0; i < c.items().size(); i++) {
            Item it = c.items().get(i);
            Requisito q = it.requisito();
            if (i > 0) json.append(",");
            json.append("{\"id\":").append(q.getIdRequisito())
                .append(",\"nombre\":\"").append(Json.esc(q.getNombre())).append("\"")
                .append(",\"descripcion\":\"").append(Json.esc(q.getDescripcion())).append("\"")
                .append(",\"obligatorio\":").append(q.isObligatorio())
                .append(",\"estado\":\"").append(Json.esc(it.estado())).append("\"")
                .append(",\"idDocumento\":").append(it.idDocumento())
                .append(",\"motivo\":\"").append(Json.esc(it.motivo())).append("\"}");
        }
        return json.append("]}").toString();
    }
}
