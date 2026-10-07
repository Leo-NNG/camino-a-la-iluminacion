package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import pe.caminoiluminacion.dao.DocumentoDAO;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.impl.DocumentoDAOImpl;
import pe.caminoiluminacion.dao.impl.ParroquiaDAOImpl;
import pe.caminoiluminacion.dao.impl.ReservaDAOImpl;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.util.AlmacenArchivos;
import pe.caminoiluminacion.util.AlmacenArchivos.Tipo;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Documentos del feligrés.
 * GET  /api/documentos : sus documentos con el estado de validación.
 * POST /api/documentos : sube UN archivo (campos: id_reserva, archivo) a una solicitud suya.
 */
@WebServlet("/api/documentos")
@MultipartConfig(fileSizeThreshold = 1048576, maxFileSize = 5242880L, maxRequestSize = 6291456L)
public class DocumentoServlet extends HttpServlet {

    private static final int MAX_DOCUMENTOS_POR_USUARIO = 50;

    private final DocumentoDAO documentoDAO;
    private final ReservaDAO reservaDAO;
    private final ParroquiaDAO parroquiaDAO;
    private final AlmacenArchivos almacen;

    public DocumentoServlet() {
        this(new DocumentoDAOImpl(), new ReservaDAOImpl(), new ParroquiaDAOImpl(), AlmacenArchivos.porDefecto());
    }

    DocumentoServlet(DocumentoDAO documentoDAO, ReservaDAO reservaDAO, ParroquiaDAO parroquiaDAO, AlmacenArchivos almacen) {
        this.documentoDAO = documentoDAO;
        this.reservaDAO = reservaDAO;
        this.parroquiaDAO = parroquiaDAO;
        this.almacen = almacen;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        if (!autorizarFeligres(request, response, out)) return;

        try {
            Map<Integer, String> parroquias = new HashMap<>();
            for (Parroquia p : parroquiaDAO.listarTodas()) parroquias.put(p.getIdParroquia(), p.getNombre());

            // Siempre del usuario de la sesión, nunca por parámetro.
            List<Documento> docs = documentoDAO.listarPorUsuario(Sesion.idUsuario(request));
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < docs.size(); i++) {
                Documento d = docs.get(i);
                Optional<Reserva> r = d.getIdReserva() == null ? Optional.empty() : reservaDAO.buscarPorId(d.getIdReserva());
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\":").append(d.getIdDocumento()).append(",")
                    .append("\"nombre\":\"").append(Json.esc(d.getNombreArchivo())).append("\",")
                    .append("\"sacramento\":\"").append(Json.esc(r.map(Reserva::getSacramento).orElse(""))).append("\",")
                    .append("\"parroquia\":\"").append(Json.esc(r.map(x -> parroquias.get(x.getIdParroquia())).orElse(""))).append("\",")
                    .append("\"solicitante\":\"").append(Json.esc(r.map(Reserva::getNombreSolicitante).orElse(""))).append("\",")
                    .append("\"fechaReserva\":\"").append(Json.fecha(r.map(Reserva::getFechaSolicitada).orElse(null))).append("\",")
                    .append("\"fechaEnvio\":\"").append(Json.fecha(d.getFechaEnvio())).append("\",")
                    .append("\"estado\":\"").append(Json.esc(d.getEstadoValidacion())).append("\",")
                    .append("\"motivo\":\"").append(Json.esc(d.getMotivoRechazo())).append("\"")
                    .append("}");
            }
            json.append("]");
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json);
        } catch (SQLException e) {
            getServletContext().log("Error al listar documentos", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(Json.error("No se pudieron cargar tus documentos."));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        if (!autorizarFeligres(request, response, out)) return;

        int idReserva;
        try {
            idReserva = Integer.parseInt(String.valueOf(request.getParameter("id_reserva")).trim());
        } catch (NumberFormatException e) {
            error(response, out, 400, "Elige a qué solicitud pertenece el documento.");
            return;
        }

        String guardado = null;
        try {
            // La solicitud debe ser del propio usuario (si es de otro, se responde como si no existiera).
            Optional<Reserva> reserva = reservaDAO.buscarPorId(idReserva);
            if (reserva.isEmpty() || !reserva.get().getIdUsuario().equals(Sesion.idUsuario(request))) {
                error(response, out, 404, "La solicitud no existe.");
                return;
            }
            if ("rechazada".equals(reserva.get().getEstado())) {
                error(response, out, 409, "Esa solicitud fue rechazada. Haz una nueva solicitud para enviar documentos.");
                return;
            }

            Part parte;
            try {
                parte = request.getPart("archivo");
            } catch (IllegalStateException e) { // el contenedor corta al superar maxFileSize / maxRequestSize
                error(response, out, 413, "El archivo supera el máximo de 5 MB.");
                return;
            }
            if (parte == null) { error(response, out, 400, "Elige un archivo para subir."); return; }

            // Se lee con tope: no se confía en el tamaño que declare el cliente.
            byte[] datos;
            try (InputStream in = parte.getInputStream()) {
                datos = in.readNBytes(AlmacenArchivos.MAX_BYTES + 1);
            }
            if (datos.length == 0) { error(response, out, 400, "El archivo está vacío."); return; }
            if (datos.length > AlmacenArchivos.MAX_BYTES) { error(response, out, 413, "El archivo supera el máximo de 5 MB."); return; }

            Tipo tipo = AlmacenArchivos.detectar(datos);
            if (tipo == null) { error(response, out, 415, "Solo se aceptan archivos PDF, JPG o PNG."); return; }

            if (documentoDAO.listarPorUsuario(Sesion.idUsuario(request)).size() >= MAX_DOCUMENTOS_POR_USUARIO) {
                error(response, out, 409, "Alcanzaste el máximo de documentos permitidos.");
                return;
            }

            guardado = almacen.guardar(datos, tipo);
            Documento doc = new Documento(idReserva, Sesion.idUsuario(request),
                    nombreVisible(parte.getSubmittedFileName(), tipo), guardado);
            doc.setTipoMime(tipo.mime);
            doc.setTamanoBytes((long) datos.length);
            documentoDAO.insertar(doc);
            guardado = null; // ya quedó registrado

            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + doc.getIdDocumento() + "}");

        } catch (SQLException e) {
            getServletContext().log("Error al guardar el documento", e);
            error(response, out, 500, "No se pudo guardar el documento. Intenta de nuevo.");
        } finally {
            if (guardado != null) almacen.borrar(guardado); // no dejar archivos sin registro si falló la base de datos
        }
    }

    /** Nombre que se muestra (solo informativo): sin ruta, sin caracteres de control y con largo acotado. */
    static String nombreVisible(String enviado, Tipo tipo) {
        String n = enviado == null ? "" : enviado;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);
        n = n.replaceAll("[\\p{Cntrl}]", "").trim();
        if (n.isEmpty()) n = "documento." + tipo.extension;
        if (n.length() > 120) n = n.substring(0, 120);
        return n;
    }

    private static boolean autorizarFeligres(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        if (!Sesion.hayUsuario(request)) { error(response, out, 401, "Inicia sesión para continuar."); return false; }
        if (!Sesion.esFeligres(request)) { error(response, out, 403, "Esta sección es para feligreses."); return false; }
        return true;
    }

    private static void error(HttpServletResponse resp, PrintWriter out, int estado, String msg) {
        resp.setStatus(estado);
        out.print(Json.error(msg));
    }
}
