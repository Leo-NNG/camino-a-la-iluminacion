package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.servicio.DocumentoService;
import pe.caminoiluminacion.servicio.DocumentoService.DocumentoPropio;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * Documentos del feligrés.
 * GET  /api/documentos : sus documentos con el estado de validación.
 * POST /api/documentos : sube UN archivo (campos: id_reserva, archivo) a una solicitud suya.
 * Las reglas (tipo por contenido, tamaño, permisos) están en DocumentoService.
 */
@WebServlet("/api/documentos")
@MultipartConfig(fileSizeThreshold = 1048576, maxFileSize = 5242880L, maxRequestSize = 6291456L)
public class DocumentoServlet extends HttpServlet {

    private final DocumentoService documentos;

    public DocumentoServlet() {
        this(Servicios.documentos());
    }

    DocumentoServlet(DocumentoService documentos) {
        this.documentos = documentos;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        if (!autorizarFeligres(request, response, out)) return;
        try {
            // Siempre del usuario de la sesión, nunca por parámetro.
            List<DocumentoPropio> lista = documentos.delUsuario(Sesion.idUsuario(request));
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                Documento d = lista.get(i).documento();
                Reserva r = lista.get(i).reserva();
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\":").append(d.getIdDocumento()).append(",")
                    .append("\"nombre\":\"").append(Json.esc(d.getNombreArchivo())).append("\",")
                    .append("\"sacramento\":\"").append(Json.esc(r == null ? "" : r.getSacramento())).append("\",")
                    .append("\"parroquia\":\"").append(Json.esc(lista.get(i).parroquia())).append("\",")
                    .append("\"solicitante\":\"").append(Json.esc(r == null ? "" : r.getNombreSolicitante())).append("\",")
                    .append("\"fechaReserva\":\"").append(Json.fecha(r == null ? null : r.getFechaSolicitada())).append("\",")
                    .append("\"fechaEnvio\":\"").append(Json.fecha(d.getFechaEnvio())).append("\",")
                    .append("\"estado\":\"").append(Json.esc(d.getEstadoValidacion())).append("\",")
                    .append("\"motivo\":\"").append(Json.esc(d.getMotivoRechazo())).append("\"")
                    .append("}");
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.append("]"));
        } catch (SQLException e) {
            Registro.error("Error al listar documentos", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar tus documentos.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        if (!autorizarFeligres(request, response, out)) return;

        Part parte;
        try {
            parte = request.getPart("archivo");
        } catch (IllegalStateException e) { // el contenedor corta al superar maxFileSize / maxRequestSize
            Respuesta.error(response, out, 413, "El archivo supera el máximo de 5 MB.");
            return;
        }
        if (parte == null) { Respuesta.error(response, out, 400, "Elige un archivo para subir."); return; }

        try (InputStream contenido = parte.getInputStream()) {
            Documento doc = documentos.subir(Sesion.idUsuario(request), request.getParameter("id_reserva"),
                    contenido, parte.getSubmittedFileName(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + doc.getIdDocumento() + "}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al guardar el documento", e);
            Respuesta.errorInterno(response, out, "No se pudo guardar el documento. Intenta de nuevo.");
        }
    }

    private static boolean autorizarFeligres(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return false; }
        if (!Sesion.esFeligres(request)) { Respuesta.error(response, out, 403, "Esta sección es para feligreses."); return false; }
        return true;
    }
}
