package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import pe.caminoiluminacion.modelo.Intencion;
import pe.caminoiluminacion.servicio.IntencionService;
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
 * Intenciones de misa del feligrés.
 * GET  /api/intenciones : sus intenciones, con el estado del pago de la ofrenda.
 * POST /api/intenciones : pide una intención y paga la ofrenda (multipart). Campos: id_parroquia, id_horario, fecha
 *                         (AAAA-MM-DD), tipo, texto, metodo, numero_operacion, celular, comprobante.
 */
@WebServlet("/api/intenciones")
@MultipartConfig(fileSizeThreshold = 1048576, maxFileSize = 5242880L, maxRequestSize = 6291456L)
public class IntencionesServlet extends HttpServlet {

    private final IntencionService intenciones;

    public IntencionesServlet() { this(Servicios.intenciones()); }

    IntencionesServlet(IntencionService intenciones) { this.intenciones = intenciones; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        if (!autorizar(request, response, out)) return;
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(IntencionJson.intenciones(intenciones.deUsuario(Sesion.idUsuario(request))));
        } catch (SQLException e) {
            Registro.error("Error al listar intenciones", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar tus intenciones.");
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

        try (InputStream contenido = parte.getInputStream()) {
            Intencion i = intenciones.registrar(Sesion.idUsuario(request), request.getParameter("id_parroquia"),
                    request.getParameter("id_horario"), request.getParameter("fecha"), request.getParameter("tipo"),
                    request.getParameter("texto"), request.getParameter("metodo"), request.getParameter("numero_operacion"),
                    request.getParameter("celular"), contenido, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + i.idIntencion() + ",\"idPago\":" + i.idPago() + "}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al registrar una intención de misa", e);
            Respuesta.errorInterno(response, out, "No se pudo registrar la intención. Intenta de nuevo.");
        }
    }

    private static boolean autorizar(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return false; }
        if (!Sesion.esFeligres(request)) { Respuesta.error(response, out, 403, "Esta sección es para feligreses."); return false; }
        return true;
    }
}
