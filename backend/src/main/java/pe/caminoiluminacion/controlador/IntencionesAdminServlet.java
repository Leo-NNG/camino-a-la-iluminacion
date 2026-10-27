package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.IntencionService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * Panel: agenda de intenciones de SU parroquia (solo las de pago aprobado).
 * GET /api/admin/intenciones[?desde=AAAA-MM-DD&hasta=AAAA-MM-DD] : por defecto, los próximos 14 días.
 */
@WebServlet("/api/admin/intenciones")
public class IntencionesAdminServlet extends HttpServlet {

    private final IntencionService intenciones;

    public IntencionesAdminServlet() { this(Servicios.intenciones()); }

    IntencionesAdminServlet(IntencionService intenciones) { this.intenciones = intenciones; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(IntencionJson.intenciones(intenciones.agenda(idParroquia, request.getParameter("desde"), request.getParameter("hasta"))));
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al cargar la agenda de intenciones", e);
            Respuesta.errorInterno(response, out, "No se pudo cargar la agenda de intenciones.");
        }
    }
}
