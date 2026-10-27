package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Horario;
import pe.caminoiluminacion.servicio.IntencionService;
import pe.caminoiluminacion.servicio.IntencionService.InfoIntencion;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * GET /api/intenciones/info?id_parroquia=N : ofrenda, número de cobro y misas (con su día) de la parroquia, para armar
 * el formulario de una intención. {@code ofrenda} es null si la parroquia no recibe intenciones en línea.
 */
@WebServlet("/api/intenciones/info")
public class IntencionInfoServlet extends HttpServlet {

    private final IntencionService intenciones;

    public IntencionInfoServlet() { this(Servicios.intenciones()); }

    IntencionInfoServlet(IntencionService intenciones) { this.intenciones = intenciones; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return; }
        if (!Sesion.esFeligres(request)) { Respuesta.error(response, out, 403, "Esta sección es para feligreses."); return; }
        try {
            InfoIntencion info = intenciones.info(request.getParameter("id_parroquia"));
            StringBuilder json = new StringBuilder("{\"ofrenda\":").append(info.ofrenda() == null ? "null" : info.ofrenda().toPlainString())
                    .append(",\"parroquia\":\"").append(Json.esc(info.parroquia())).append("\"")
                    .append(",\"cuenta\":").append(PagoJson.cuenta(info.cuenta()))
                    .append(",\"maxPorMisa\":").append(info.maxPorMisa()).append(",\"misas\":[");
            List<Horario> misas = info.misas();
            for (int i = 0; i < misas.size(); i++) {
                Horario h = misas.get(i);
                if (i > 0) json.append(",");
                json.append("{\"id\":").append(h.getIdHorario()).append(",\"dia\":\"").append(Json.esc(h.getDiaSemana()))
                    .append("\",\"hora\":\"").append(Json.esc(h.getHora())).append("\"}");
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.append("]}"));
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al consultar los datos de intenciones", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar los datos de intenciones.");
        }
    }
}
