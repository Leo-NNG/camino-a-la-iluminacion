package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.servicio.PagoService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * Pagos del feligrés.
 * GET  /api/pagos : sus pagos (celular enmascarado).
 * POST /api/pagos : registra un pago con comprobante (multipart). Campos: tipo = arancel | donacion | diezmo,
 *                   id_reserva (arancel) o id_parroquia + monto (donación/diezmo), metodo, numero_operacion, celular, comprobante.
 * Las reglas (monto desde la BD, anti-duplicados, tipo de archivo) están en PagoService.
 */
@WebServlet("/api/pagos")
@MultipartConfig(fileSizeThreshold = 1048576, maxFileSize = 5242880L, maxRequestSize = 6291456L)
public class PagosServlet extends HttpServlet {

    private final PagoService pagos;

    public PagosServlet() { this(Servicios.pagos()); }

    PagosServlet(PagoService pagos) { this.pagos = pagos; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        if (!autorizar(request, response, out)) return;
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(PagoJson.pagos(pagos.delUsuario(Sesion.idUsuario(request))));
        } catch (SQLException e) {
            Registro.error("Error al listar pagos", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar tus pagos.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        if (!autorizar(request, response, out)) return;

        Part parte;
        try {
            parte = request.getPart("comprobante");
        } catch (IllegalStateException e) { // el contenedor corta al superar maxFileSize / maxRequestSize
            Respuesta.error(response, out, 413, "El comprobante supera el máximo de 5 MB.");
            return;
        }
        if (parte == null) { Respuesta.error(response, out, 400, "Sube la captura del comprobante."); return; }

        int idUsuario = Sesion.idUsuario(request);
        String tipo = request.getParameter("tipo");
        try (InputStream contenido = parte.getInputStream()) {
            Pago p = "arancel".equals(tipo)
                    ? pagos.pagarReserva(idUsuario, request.getParameter("id_reserva"), request.getParameter("metodo"),
                            request.getParameter("numero_operacion"), request.getParameter("celular"), contenido, request.getRemoteAddr())
                    : pagos.pagarLibre(idUsuario, request.getParameter("id_parroquia"), tipo, request.getParameter("monto"),
                            request.getParameter("metodo"), request.getParameter("numero_operacion"), request.getParameter("celular"),
                            contenido, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + p.idPago() + "}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al registrar un pago", e);
            Respuesta.errorInterno(response, out, "No se pudo registrar el pago. Intenta de nuevo.");
        }
    }

    private static boolean autorizar(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return false; }
        if (!Sesion.esFeligres(request)) { Respuesta.error(response, out, 403, "Esta sección es para feligreses."); return false; }
        return true;
    }
}
