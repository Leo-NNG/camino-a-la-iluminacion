package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.servicio.VerificacionService;
import pe.caminoiluminacion.servicio.VerificacionService.Resultado;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * GET /api/verificar?codigo=CON-XXXXX-XXXXX : comprueba un código impreso en una constancia o un recibo.
 * Es PÚBLICO (no pide sesión): solo devuelve lo mínimo y limita los intentos fallidos por IP.
 */
@WebServlet("/api/verificar")
public class VerificarServlet extends HttpServlet {

    private final VerificacionService verificacion;

    public VerificarServlet() { this(Servicios.verificacion()); }

    VerificarServlet(VerificacionService verificacion) {
        this.verificacion = verificacion;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        try {
            Resultado r = verificacion.verificar(request.getParameter("codigo"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"tipo\":\"" + Json.esc(r.tipo()) + "\",\"vigente\":" + r.vigente() + ",\"estado\":\"" + Json.esc(r.estado()) + "\""
                    + ",\"parroquia\":\"" + Json.esc(r.parroquia()) + "\",\"detalle\":\"" + Json.esc(r.detalle()) + "\""
                    + ",\"fecha\":\"" + Json.esc(r.fecha()) + "\",\"monto\":\"" + Json.esc(r.monto()) + "\""
                    + ",\"solicitante\":\"" + Json.esc(r.solicitante()) + "\"}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al verificar un código", e);
            Respuesta.errorInterno(response, out, "No se pudo verificar el código. Intenta de nuevo.");
        }
    }
}
