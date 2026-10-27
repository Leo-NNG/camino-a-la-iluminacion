package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.IntencionService;
import pe.caminoiluminacion.servicio.PagoService;
import pe.caminoiluminacion.servicio.PagoService.Recibo;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.ReciboPdf;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.time.Clock;

/**
 * GET /api/pagos/recibo?id=N : recibo en PDF de un pago APROBADO. Lo descarga su dueño o el personal de la parroquia
 * del pago; cualquier otra persona recibe 404 y un pago aún no verificado recibe 409 (la decisión es de PagoService).
 */
@WebServlet("/api/pagos/recibo")
public class ReciboServlet extends HttpServlet {

    private final PagoService pagos;
    private final Clock reloj;

    public ReciboServlet() { this(Servicios.pagos(), IntencionService.relojDePeru()); }

    ReciboServlet(PagoService pagos, Clock reloj) {
        this.pagos = pagos;
        this.reloj = reloj;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!Sesion.hayUsuario(request)) { error(response, 401, "Inicia sesión para continuar."); return; }
        try {
            Recibo r = pagos.paraRecibo(Sesion.rol(request), Sesion.idUsuario(request), Sesion.idParroquia(request), request.getParameter("id"));
            byte[] pdf = ReciboPdf.generar(r, reloj);
            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            response.setHeader("Content-Disposition", "inline; filename=\"recibo-" + r.numero() + ".pdf\"");
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
            Registro.error("Error al generar un recibo", e);
            error(response, 500, "No se pudo generar el recibo.");
        }
    }

    private static void error(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(Json.error(mensaje));
    }
}
