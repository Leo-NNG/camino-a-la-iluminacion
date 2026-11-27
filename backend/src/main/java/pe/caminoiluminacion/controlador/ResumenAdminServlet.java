package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.ReporteDAO.Resumen;
import pe.caminoiluminacion.servicio.ReporteService;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * Resumen (dashboard) del panel: contadores de la parroquia del administrador.
 * GET /api/admin/resumen
 * Responde {"pendientes":{...},"proximos":{...},"mes":{...},"diasProximos":7}.
 */
@WebServlet("/api/admin/resumen")
public class ResumenAdminServlet extends HttpServlet {

    private final ReporteService reportes;

    public ResumenAdminServlet() {
        this(Servicios.reportes());
    }

    ResumenAdminServlet(ReporteService reportes) {
        this.reportes = reportes;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            out.print(json(reportes.resumen(idParroquia)));
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (SQLException e) {
            Registro.error("Error al generar el resumen del panel", e);
            Respuesta.errorInterno(response, out, "No se pudo cargar el resumen.");
        }
    }

    static String json(Resumen r) {
        return "{\"pendientes\":{\"solicitudes\":" + r.solicitudesPendientes()
                + ",\"documentos\":" + r.documentosPendientes()
                + ",\"pagos\":" + r.pagosPorVerificar() + "}"
                + ",\"proximos\":{\"celebraciones\":" + r.celebracionesProximas()
                + ",\"intenciones\":" + r.intencionesProximas() + "}"
                + ",\"mes\":{\"solicitudes\":" + r.solicitudesMes()
                + ",\"canceladas\":" + r.canceladasMes()
                + ",\"pagos\":" + r.pagosMes()
                + ",\"ingresos\":" + ReportesAdminServlet.importe(r.ingresosMes()) + "}"
                + ",\"diasProximos\":" + ReporteService.DIAS_PROXIMOS + "}";
    }
}
