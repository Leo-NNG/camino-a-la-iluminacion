package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.PagoService;
import pe.caminoiluminacion.servicio.PagoService.Descarga;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.sql.SQLException;

/**
 * GET /api/pagos/voucher?id=N : muestra el comprobante. Solo su dueño o el administrador de la parroquia del pago;
 * quien no tenga permiso recibe 404 (la decisión es de PagoService).
 */
@WebServlet("/api/pagos/voucher")
public class VoucherServlet extends HttpServlet {

    private final PagoService pagos;

    public VoucherServlet() { this(Servicios.pagos()); }

    VoucherServlet(PagoService pagos) { this.pagos = pagos; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!Sesion.hayUsuario(request)) { error(response, 401, "Inicia sesión para continuar."); return; }
        try {
            Descarga d = pagos.paraDescarga(Sesion.rol(request), Sesion.idUsuario(request), Sesion.idParroquia(request), request.getParameter("id"));
            response.setContentType(d.tipo().mime);
            response.setContentLengthLong(Files.size(d.ruta()));
            response.setHeader("Content-Disposition", "inline; filename=\"comprobante-" + d.pago().idPago() + "." + d.tipo().extension + "\"");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
            response.setStatus(HttpServletResponse.SC_OK);
            try (OutputStream out = response.getOutputStream()) {
                Files.copy(d.ruta(), out);
            }
        } catch (ReglaNegocioException e) {
            error(response, e.getEstado(), e.getMessage());
        } catch (SQLException e) {
            Registro.error("Error al abrir un comprobante", e);
            error(response, 500, "No se pudo abrir el comprobante.");
        }
    }

    private static void error(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(Json.error(mensaje));
    }
}
