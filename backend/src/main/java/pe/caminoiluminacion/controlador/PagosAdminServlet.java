package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.PagoService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * Panel: verificación de pagos de SU parroquia.
 * GET  /api/admin/pagos?estado=pendiente|aprobado|rechazado : lista (con el celular completo para contrastar con Yape/Plin).
 * GET  /api/admin/pagos?resumen=ingresos[&mes=AAAA-MM]      : ingresos aprobados por concepto.
 * POST /api/admin/pagos  (id, accion = aprobar | rechazar, observacion) : resuelve un pago pendiente.
 */
@WebServlet("/api/admin/pagos")
public class PagosAdminServlet extends HttpServlet {

    private final PagoService pagos;

    public PagosAdminServlet() { this(Servicios.pagos()); }

    PagosAdminServlet(PagoService pagos) { this.pagos = pagos; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            if ("ingresos".equals(request.getParameter("resumen"))) {
                List<pe.caminoiluminacion.dao.PagoDAO.FilaIngreso> filas = pagos.ingresos(idParroquia, request.getParameter("mes"));
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < filas.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append("{\"concepto\":\"").append(Json.esc(filas.get(i).concepto())).append("\",\"pagos\":")
                        .append(filas.get(i).pagos()).append(",\"total\":").append(filas.get(i).total().toPlainString()).append("}");
                }
                response.setStatus(HttpServletResponse.SC_OK);
                out.print(json.append("]"));
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(PagoJson.pagos(pagos.deParroquia(idParroquia, request.getParameter("estado"))));
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al listar pagos del panel", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar los pagos.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        String accion = request.getParameter("accion");
        if (!"aprobar".equals(accion) && !"rechazar".equals(accion)) { Respuesta.error(response, out, 400, "Acción no válida."); return; }
        try {
            pagos.resolver(Sesion.idUsuario(request), idParroquia, request.getParameter("id"), "aprobar".equals(accion),
                    request.getParameter("observacion"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al resolver un pago", e);
            Respuesta.errorInterno(response, out, "No se pudo actualizar el pago.");
        }
    }
}
