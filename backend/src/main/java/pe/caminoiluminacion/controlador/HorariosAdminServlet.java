package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Horario;
import pe.caminoiluminacion.servicio.ContenidoService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * El administrador gestiona los horarios de SU parroquia.
 * GET    /api/admin/horarios          : los de su parroquia.
 * POST   /api/admin/horarios          : crea (sin id) o modifica (con id) un horario: dia, tipo, hora.
 * DELETE /api/admin/horarios?id=N     : elimina uno de su parroquia.
 */
@WebServlet("/api/admin/horarios")
public class HorariosAdminServlet extends HttpServlet {

    private final ContenidoService contenido;

    public HorariosAdminServlet() {
        this(Servicios.contenido());
    }

    HorariosAdminServlet(ContenidoService contenido) {
        this.contenido = contenido;
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
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(HorariosServlet.aJson(contenido.horariosDe(idParroquia)));
        } catch (SQLException e) {
            Registro.error("Error al listar horarios del panel", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar los horarios.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            Horario h = contenido.guardarHorario(Sesion.idUsuario(request), idParroquia, request.getParameter("id"),
                    request.getParameter("dia"), request.getParameter("tipo"), request.getParameter("hora"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + h.getIdHorario() + "}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al guardar horario", e);
            Respuesta.errorInterno(response, out, "No se pudo guardar el horario.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            contenido.eliminarHorario(Sesion.idUsuario(request), idParroquia, request.getParameter("id"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al eliminar horario", e);
            Respuesta.errorInterno(response, out, "No se pudo eliminar el horario.");
        }
    }
}
