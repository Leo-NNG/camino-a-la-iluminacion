package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.DocumentoService;
import pe.caminoiluminacion.servicio.DocumentoService.Descarga;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AlmacenArchivos.Tipo;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.sql.SQLException;

/**
 * GET /api/documentos/archivo?id=N : descarga el archivo. Solo lo puede abrir su dueño o el administrador
 * de la parroquia a la que pertenece la solicitud; quien no tenga permiso recibe 404 (la decisión es de DocumentoService).
 */
@WebServlet("/api/documentos/archivo")
public class ArchivoDocumentoServlet extends HttpServlet {

    private final DocumentoService documentos;

    public ArchivoDocumentoServlet() {
        this(Servicios.documentos());
    }

    ArchivoDocumentoServlet(DocumentoService documentos) {
        this.documentos = documentos;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!Sesion.hayUsuario(request)) { texto(response, 401, "Inicia sesión para continuar."); return; }
        try {
            Descarga d = documentos.paraDescarga(Sesion.rol(request), Sesion.idUsuario(request),
                    Sesion.idParroquia(request), request.getParameter("id"));
            Tipo tipo = d.tipo();
            response.setContentType(tipo.mime);
            response.setContentLengthLong(Files.size(d.ruta()));
            response.setHeader("Content-Disposition", "inline; filename=\"documento-" + d.documento().getIdDocumento() + "." + tipo.extension + "\"");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Content-Security-Policy", tipo == Tipo.PDF ? "default-src 'none'" : "default-src 'none'; sandbox");
            response.setStatus(HttpServletResponse.SC_OK);
            try (OutputStream out = response.getOutputStream()) {
                Files.copy(d.ruta(), out);
            }
        } catch (ReglaNegocioException e) {
            texto(response, e.getEstado(), e.getMessage());
        } catch (SQLException e) {
            Registro.error("Error al descargar un documento", e);
            texto(response, 500, "No se pudo abrir el documento.");
        }
    }

    private static void texto(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(Json.error(mensaje));
    }
}
