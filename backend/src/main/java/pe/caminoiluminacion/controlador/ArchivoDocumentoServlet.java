package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.DocumentoDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.impl.DocumentoDAOImpl;
import pe.caminoiluminacion.dao.impl.ReservaDAOImpl;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.util.AlmacenArchivos;
import pe.caminoiluminacion.util.AlmacenArchivos.Tipo;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

/**
 * GET /api/documentos/archivo?id=N : descarga el archivo. Solo lo puede abrir su dueño o el administrador
 * de la parroquia a la que pertenece la solicitud. Quien no tenga permiso recibe 404 (no se revela que existe).
 */
@WebServlet("/api/documentos/archivo")
public class ArchivoDocumentoServlet extends HttpServlet {

    private final DocumentoDAO documentoDAO;
    private final ReservaDAO reservaDAO;
    private final AlmacenArchivos almacen;

    public ArchivoDocumentoServlet() {
        this(new DocumentoDAOImpl(), new ReservaDAOImpl(), AlmacenArchivos.porDefecto());
    }

    ArchivoDocumentoServlet(DocumentoDAO documentoDAO, ReservaDAO reservaDAO, AlmacenArchivos almacen) {
        this.documentoDAO = documentoDAO;
        this.reservaDAO = reservaDAO;
        this.almacen = almacen;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!Sesion.hayUsuario(request)) { texto(response, 401, "Inicia sesión para continuar."); return; }

        try {
            int id;
            try {
                id = Integer.parseInt(String.valueOf(request.getParameter("id")).trim());
            } catch (NumberFormatException e) {
                texto(response, 400, "Identificador no válido.");
                return;
            }

            Optional<Documento> docOpt = documentoDAO.buscarPorId(id);
            if (docOpt.isEmpty() || !puedeVer(request, docOpt.get())) { texto(response, 404, "El documento no existe."); return; }
            Documento doc = docOpt.get();

            Tipo tipo = Tipo.porMime(doc.getTipoMime());
            Path ruta = almacen.resolver(doc.getRutaArchivo());
            if (tipo == null || ruta == null) { texto(response, 404, "El archivo no está disponible."); return; }

            response.setContentType(tipo.mime);
            response.setContentLengthLong(Files.size(ruta));
            response.setHeader("Content-Disposition", "inline; filename=\"documento-" + doc.getIdDocumento() + "." + tipo.extension + "\"");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Content-Security-Policy",
                    tipo == Tipo.PDF ? "default-src 'none'" : "default-src 'none'; sandbox");
            response.setStatus(HttpServletResponse.SC_OK);
            try (OutputStream out = response.getOutputStream()) {
                Files.copy(ruta, out);
            }
        } catch (SQLException e) {
            getServletContext().log("Error al descargar un documento", e);
            texto(response, 500, "No se pudo abrir el documento.");
        }
    }

    private boolean puedeVer(HttpServletRequest request, Documento doc) throws SQLException {
        if (Sesion.esFeligres(request)) return doc.getIdUsuario().equals(Sesion.idUsuario(request));
        if (Sesion.esAdministrador(request) && Sesion.idParroquia(request) != null && doc.getIdReserva() != null) {
            Optional<Reserva> r = reservaDAO.buscarPorId(doc.getIdReserva());
            return r.isPresent() && Sesion.idParroquia(request).equals(r.get().getIdParroquia());
        }
        return false;
    }

    private static void texto(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(Json.error(mensaje));
    }
}
