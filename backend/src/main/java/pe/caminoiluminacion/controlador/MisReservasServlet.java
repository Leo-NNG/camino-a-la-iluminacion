package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.servicio.ReservaService;
import pe.caminoiluminacion.servicio.ReservaService.ReservaPropia;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/** "Mis solicitudes": las reservas del feligrés con sesión iniciada, con su estado y el motivo si fue rechazada. */
@WebServlet("/api/mis-reservas")
public class MisReservasServlet extends HttpServlet {

    private final ReservaService reservas;

    public MisReservasServlet() {
        this(Servicios.reservas());
    }

    MisReservasServlet(ReservaService reservas) {
        this.reservas = reservas;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();

        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para ver tus solicitudes."); return; }
        if (!Sesion.esFeligres(request)) { Respuesta.error(response, out, 403, "Esta sección es para feligreses."); return; }

        try {
            // Siempre se filtra por el usuario de la sesión: nunca por un parámetro del cliente.
            List<ReservaPropia> lista = reservas.delUsuario(Sesion.idUsuario(request));
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                Reserva r = lista.get(i).reserva();
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\":").append(r.getIdReserva()).append(",")
                    .append("\"sacramento\":\"").append(Json.esc(r.getSacramento())).append("\",")
                    .append("\"parroquia\":\"").append(Json.esc(lista.get(i).parroquia())).append("\",")
                    .append("\"solicitante\":\"").append(Json.esc(r.getNombreSolicitante())).append("\",")
                    .append("\"fecha\":\"").append(Json.esc(r.getFechaSolicitada())).append("\",")
                    .append("\"estado\":\"").append(Json.esc(r.getEstado())).append("\",")
                    .append("\"motivo\":\"").append(Json.esc(r.getMotivoRechazo())).append("\"")
                    .append("}");
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.append("]"));
        } catch (SQLException e) {
            Registro.error("Error al listar mis reservas", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar tus solicitudes.");
        }
    }
}
