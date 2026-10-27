package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.ReservaService;
import pe.caminoiluminacion.servicio.ReservaService.ReservaPendiente;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * Panel del administrador: lista las reservas pendientes de SU parroquia y permite confirmarlas o rechazarlas.
 * Además del FiltroAdmin, cada método vuelve a comprobar el rol y la parroquia (defensa en profundidad).
 */
@WebServlet("/api/admin/reservas")
public class ListarReservasAdminServlet extends HttpServlet {

    private final ReservaService reservas;

    public ListarReservasAdminServlet() {
        this(Servicios.reservas());
    }

    ListarReservasAdminServlet(ReservaService reservas) {
        this.reservas = reservas;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            List<ReservaPendiente> lista = reservas.pendientesDeParroquia(idParroquia);
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                Reserva r = lista.get(i).reserva();
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\":").append(r.getIdReserva()).append(",")
                    .append("\"sacramento\":\"").append(Json.esc(r.getSacramento())).append("\",")
                    .append("\"nombreSolicitante\":\"").append(Json.esc(r.getNombreSolicitante())).append("\",")
                    .append("\"fecha\":\"").append(Json.esc(r.getFechaSolicitada())).append("\",")
                    .append("\"estado\":\"").append(Json.esc(r.getEstado())).append("\",")
                    .append("\"notas\":\"").append(Json.esc(r.getNotas())).append("\",")
                    .append("\"contacto\":\"").append(Json.esc(lista.get(i).contacto())).append("\"")
                    .append("}");
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.append("]"));
        } catch (SQLException e) {
            Registro.error("Error al listar reservas", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar las reservas.");
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
            reservas.resolver(Sesion.idUsuario(request), idParroquia, request.getParameter("id"),
                    request.getParameter("estado"), request.getParameter("motivo"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al actualizar la reserva", e);
            Respuesta.errorInterno(response, out, "No se pudo actualizar la reserva.");
        }
    }
}
