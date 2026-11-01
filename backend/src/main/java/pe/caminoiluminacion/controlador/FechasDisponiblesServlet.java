package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.FechaDisponible;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.ReservaService;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * GET /api/fechas-disponibles?parroquia=ID&sacramento=CODIGO : fechas que el feligrés puede elegir al reservar.
 * Responde {"publicadas":true|false,"fechas":[{"fecha":"2026-11-14","hora":"16:00","libres":3}]}.
 * publicadas=false significa que la parroquia no publicó fechas para ese sacramento: la fecha es libre.
 */
@WebServlet("/api/fechas-disponibles")
public class FechasDisponiblesServlet extends HttpServlet {

    private final ReservaService reservas;

    public FechasDisponiblesServlet() {
        this(Servicios.reservas());
    }

    FechasDisponiblesServlet(ReservaService reservas) {
        this.reservas = reservas;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        try {
            ReservaService.Disponibilidad d = reservas.disponibilidad(request.getParameter("parroquia"), request.getParameter("sacramento"));
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(aJson(d));
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al consultar las fechas disponibles", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar las fechas disponibles.");
        }
    }

    static String aJson(ReservaService.Disponibilidad d) {
        StringBuilder json = new StringBuilder("{\"publicadas\":").append(d.publicadas()).append(",\"fechas\":[");
        List<FechaDisponible> fechas = d.fechas();
        for (int i = 0; i < fechas.size(); i++) {
            FechaDisponible f = fechas.get(i);
            if (i > 0) json.append(",");
            json.append("{\"fecha\":\"").append(f.getFecha()).append("\"")
                .append(",\"hora\":\"").append(f.getHoraInicio() == null ? "" : Json.esc(f.getHoraInicio().toString())).append("\"")
                .append(",\"libres\":").append(f.getLibres()).append("}");
        }
        return json.append("]}").toString();
    }
}
