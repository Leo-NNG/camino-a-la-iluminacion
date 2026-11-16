package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.servicio.GestionReservaService;
import pe.caminoiluminacion.servicio.GestionReservaService.Cancelacion;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * El feligrés gestiona SUS solicitudes de sacramento (siempre la sesión; nunca un id de usuario del cliente):
 * POST /api/reservas/cancelar    (id_reserva, motivo opcional)
 * POST /api/reservas/reprogramar (id_reserva, fecha)
 * Pasan por FiltroOrigen (CSRF).
 */
@WebServlet({"/api/reservas/cancelar", "/api/reservas/reprogramar"})
public class ReservaGestionServlet extends HttpServlet {

    private final GestionReservaService gestion;

    public ReservaGestionServlet() {
        this(Servicios.gestionReservas());
    }

    ReservaGestionServlet(GestionReservaService gestion) {
        this.gestion = gestion;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return; }
        if (!Sesion.esFeligres(request)) { Respuesta.error(response, out, 403, "Esta sección es para feligreses."); return; }
        int idUsuario = Sesion.idUsuario(request);
        String ip = request.getRemoteAddr();
        boolean cancelar = request.getRequestURI().endsWith("/cancelar");
        try {
            if (cancelar) {
                Cancelacion c = gestion.cancelar(idUsuario, request.getParameter("id_reserva"), request.getParameter("motivo"), ip);
                response.setStatus(HttpServletResponse.SC_OK);
                out.print("{\"exito\":true,\"arancelPagado\":" + c.arancelPagado() + ",\"mensaje\":\"" + Json.esc(c.arancelPagado()
                        ? "Solicitud cancelada. Como ya pagaste el arancel, comunícate con la parroquia para coordinar el reembolso o su uso."
                        : "Solicitud cancelada.") + "\"}");
            } else {
                Reserva r = gestion.reprogramar(idUsuario, request.getParameter("id_reserva"), request.getParameter("fecha"), ip);
                response.setStatus(HttpServletResponse.SC_OK);
                out.print("{\"exito\":true,\"fecha\":\"" + Json.esc(r.getFechaSolicitada()) + "\",\"mensaje\":\"Fecha cambiada. La parroquia volverá a validarla.\"}");
            }
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al gestionar la solicitud", e);
            Respuesta.errorInterno(response, out, "No se pudo completar la operación. Intenta de nuevo.");
        }
    }
}
