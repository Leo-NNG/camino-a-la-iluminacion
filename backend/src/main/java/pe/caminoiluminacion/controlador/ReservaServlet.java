package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.ReservaService;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/** POST /api/reservas: un feligrés solicita un sacramento. Las reglas están en ReservaService. */
@WebServlet("/api/reservas")
public class ReservaServlet extends HttpServlet {

    private final ReservaService reservas;

    public ReservaServlet() {
        this(Servicios.reservas());
    }

    ReservaServlet(ReservaService reservas) {
        this.reservas = reservas;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        // La reserva pertenece al feligrés con sesión iniciada; el personal no reserva.
        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para reservar un sacramento."); return; }
        if (!Sesion.esFeligres(request)) { Respuesta.error(response, out, 403, "Las cuentas del personal parroquial no pueden reservar sacramentos."); return; }

        try {
            Reserva nueva = reservas.crear(Sesion.idUsuario(request), request.getParameter("sacramento"),
                    request.getParameter("id_parroquia"), request.getParameter("nombre_solicitante"),
                    request.getParameter("fecha"), request.getParameter("notas"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + nueva.getIdReserva() + "}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al guardar la reserva", e);
            Respuesta.errorInterno(response, out, "No se pudo guardar la reserva. Intenta de nuevo.");
        }
    }
}
