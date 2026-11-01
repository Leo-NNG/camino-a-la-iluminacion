package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.ReporteDAO.FilaAgenda;
import pe.caminoiluminacion.dao.ReporteDAO.FilaDocumento;
import pe.caminoiluminacion.dao.ReporteDAO.FilaTramites;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.ReporteService;
import pe.caminoiluminacion.servicio.ReporteService.Archivo;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * Reportes del panel, siempre de la parroquia del administrador.
 * GET /api/admin/reportes?tipo=tramites|documentos|agenda[&mes=AAAA-MM][&formato=xlsx]
 * Sin formato devuelve JSON (para las tablas de la pantalla); con formato=xlsx descarga el Excel.
 */
@WebServlet("/api/admin/reportes")
public class ReportesAdminServlet extends HttpServlet {

    private static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReporteService reportes;

    public ReportesAdminServlet() {
        this(Servicios.reportes());
    }

    ReportesAdminServlet(ReporteService reportes) {
        this.reportes = reportes;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        String tipo = request.getParameter("tipo");
        String mes = request.getParameter("mes");

        if ("xlsx".equals(request.getParameter("formato"))) {
            descargarExcel(request, response, tipo, mes);
            return;
        }

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            if ("tramites".equals(tipo)) out.print(jsonTramites(reportes.tramites(idParroquia, mes)));
            else if ("documentos".equals(tipo)) out.print(jsonDocumentos(reportes.documentosPendientes(idParroquia)));
            else if ("agenda".equals(tipo)) out.print(jsonAgenda(reportes.agenda(idParroquia)));
            else { Respuesta.error(response, out, 400, "Reporte no válido."); return; }
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al generar el reporte", e);
            Respuesta.errorInterno(response, out, "No se pudo generar el reporte.");
        }
    }

    /** Descarga binaria: aquí NO se usa getWriter(); los errores se escriben solo si aún no se envió el archivo. */
    private void descargarExcel(HttpServletRequest request, HttpServletResponse response, String tipo, String mes)
            throws IOException {
        // La comprobación de acceso escribe sus errores en un búfer, no en la respuesta.
        StringWriter buffer = new StringWriter();
        PrintWriter aviso = new PrintWriter(buffer);
        Integer idParroquia = AccesoAdmin.parroquia(request, response, aviso);
        aviso.flush();
        if (idParroquia == null) {
            enviarJson(response, buffer.toString());
            return;
        }
        try {
            Archivo a = reportes.exportar(Sesion.idUsuario(request), idParroquia, tipo, mes, request.getRemoteAddr());
            response.setContentType(MIME_XLSX);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + a.nombre() + "\"");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLengthLong(a.datos().length);
            response.setStatus(HttpServletResponse.SC_OK);
            try (OutputStream os = response.getOutputStream()) {
                os.write(a.datos());
            }
        } catch (ReglaNegocioException e) {
            response.setStatus(e.getEstado());
            enviarJson(response, Json.error(e.getMessage()));
        } catch (SQLException e) {
            Registro.error("Error al generar el reporte", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            enviarJson(response, Json.error("No se pudo generar el reporte."));
        }
    }

    private static void enviarJson(HttpServletResponse response, String json) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(json);
    }

    static String jsonTramites(List<FilaTramites> filas) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < filas.size(); i++) {
            FilaTramites f = filas.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"sacramento\":\"").append(Json.esc(f.sacramento())).append("\",\"solicitudes\":").append(f.solicitudes())
              .append(",\"confirmadas\":").append(f.confirmadas()).append(",\"pendientes\":").append(f.pendientes())
              .append(",\"rechazadas\":").append(f.rechazadas()).append("}");
        }
        return sb.append("]").toString();
    }

    static String jsonDocumentos(List<FilaDocumento> filas) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < filas.size(); i++) {
            FilaDocumento f = filas.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"feligres\":\"").append(Json.esc(f.feligres())).append("\",\"documento\":\"").append(Json.esc(f.documento()))
              .append("\",\"sacramento\":\"").append(Json.esc(f.sacramento())).append("\",\"solicitante\":\"").append(Json.esc(f.solicitante()))
              .append("\",\"fechaEnvio\":\"").append(Json.esc(f.fechaEnvio())).append("\",\"diasEspera\":").append(f.diasEspera()).append("}");
        }
        return sb.append("]").toString();
    }

    static String jsonAgenda(List<FilaAgenda> filas) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < filas.size(); i++) {
            FilaAgenda f = filas.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"fecha\":\"").append(Json.esc(f.fecha())).append("\",\"sacramento\":\"").append(Json.esc(f.sacramento()))
              .append("\",\"solicitante\":\"").append(Json.esc(f.solicitante())).append("\",\"contacto\":\"").append(Json.esc(f.contacto())).append("\"}");
        }
        return sb.append("]").toString();
    }
}
