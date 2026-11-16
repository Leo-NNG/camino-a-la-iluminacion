package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.IntencionService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.servicio.VerificacionService;
import pe.caminoiluminacion.servicio.VerificacionService.Constancia;
import pe.caminoiluminacion.util.ConstanciaPdf;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Sesion;
import pe.caminoiluminacion.util.UrlBase;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.time.Clock;

/**
 * GET /api/reservas/constancia?id=N : constancia en PDF de una solicitud CONFIRMADA del usuario de la sesión.
 * La solicitud de otra persona recibe 404; una no confirmada, 409 (lo decide VerificacionService).
 */
@WebServlet("/api/reservas/constancia")
public class ConstanciaServlet extends HttpServlet {

    private final VerificacionService verificacion;
    private final Clock reloj;

    public ConstanciaServlet() { this(Servicios.verificacion(), IntencionService.relojDePeru()); }

    ConstanciaServlet(VerificacionService verificacion, Clock reloj) {
        this.verificacion = verificacion;
        this.reloj = reloj;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!Sesion.hayUsuario(request)) { error(response, 401, "Inicia sesión para continuar."); return; }
        if (!Sesion.esFeligres(request)) { error(response, 403, "Las constancias son para feligreses."); return; }
        try {
            Constancia k = verificacion.constancia(Sesion.idUsuario(request), request.getParameter("id"));
            byte[] pdf = ConstanciaPdf.generar(k, UrlBase.verificacion(request, k.codigo()), reloj);
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("Content-Disposition", "inline; filename=\"constancia-" + k.codigo() + ".pdf\"");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Content-Security-Policy", "default-src 'none'");
            response.setStatus(HttpServletResponse.SC_OK);
            try (OutputStream out = response.getOutputStream()) {
                out.write(pdf);
            }
        } catch (ReglaNegocioException e) {
            error(response, e.getEstado(), e.getMessage());
        } catch (SQLException e) {
            Registro.error("Error al generar una constancia", e);
            error(response, 500, "No se pudo generar la constancia.");
        }
    }

    private static void error(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(Json.error(mensaje));
    }
}
