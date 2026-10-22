package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.servicio.PagoService;
import pe.caminoiluminacion.servicio.PagoService.InfoPago;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * GET /api/pagos/info?id_reserva=N  : cuánto se paga por el trámite de una reserva PROPIA y a qué número.
 * GET /api/pagos/info?id_parroquia=N: a qué número se puede donar o diezmar en esa parroquia.
 */
@WebServlet("/api/pagos/info")
public class PagoInfoServlet extends HttpServlet {

    private final PagoService pagos;

    public PagoInfoServlet() { this(Servicios.pagos()); }

    PagoInfoServlet(PagoService pagos) { this.pagos = pagos; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return; }
        if (!Sesion.esFeligres(request)) { Respuesta.error(response, out, 403, "Esta sección es para feligreses."); return; }
        try {
            String reserva = request.getParameter("id_reserva");
            InfoPago info = reserva != null && !reserva.isBlank()
                    ? pagos.infoArancel(Sesion.idUsuario(request), reserva)
                    : pagos.infoLibre(request.getParameter("id_parroquia"));
            Pago vigente = info.pagoVigente();
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"monto\":" + (info.monto() == null ? "null" : info.monto().toPlainString())
                    + ",\"concepto\":\"" + Json.esc(info.concepto()) + "\""
                    + ",\"detalle\":\"" + Json.esc(info.detalle()) + "\""
                    + ",\"parroquia\":\"" + Json.esc(info.parroquia()) + "\""
                    + ",\"cuenta\":" + PagoJson.cuenta(info.cuenta())
                    + ",\"pagoVigente\":" + (vigente == null ? "null"
                        : "{\"id\":" + vigente.idPago() + ",\"estado\":\"" + Json.esc(vigente.estado()) + "\"}") + "}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al consultar los datos de pago", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar los datos de pago.");
        }
    }
}
