package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.impl.ParroquiaDAOImpl;
import pe.caminoiluminacion.dao.impl.ReservaDAOImpl;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** "Mis solicitudes": las reservas del feligrés con sesión iniciada, con su estado y el motivo si fue rechazada. */
@WebServlet("/api/mis-reservas")
public class MisReservasServlet extends HttpServlet {

    private final ReservaDAO reservaDAO;
    private final ParroquiaDAO parroquiaDAO;

    public MisReservasServlet() {
        this(new ReservaDAOImpl(), new ParroquiaDAOImpl());
    }

    MisReservasServlet(ReservaDAO reservaDAO, ParroquiaDAO parroquiaDAO) {
        this.reservaDAO = reservaDAO;
        this.parroquiaDAO = parroquiaDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();

        if (!Sesion.hayUsuario(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(Json.error("Inicia sesión para ver tus solicitudes."));
            return;
        }
        if (!Sesion.esFeligres(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print(Json.error("Esta sección es para feligreses."));
            return;
        }

        try {
            Map<Integer, String> nombres = new HashMap<>();
            for (Parroquia p : parroquiaDAO.listarTodas()) nombres.put(p.getIdParroquia(), p.getNombre());

            // Siempre se filtra por el usuario de la sesión: nunca por un parámetro del cliente.
            List<Reserva> lista = reservaDAO.listarPorUsuario(Sesion.idUsuario(request));

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                Reserva r = lista.get(i);
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\":").append(r.getIdReserva()).append(",")
                    .append("\"sacramento\":\"").append(Json.esc(r.getSacramento())).append("\",")
                    .append("\"parroquia\":\"").append(Json.esc(nombres.get(r.getIdParroquia()))).append("\",")
                    .append("\"solicitante\":\"").append(Json.esc(r.getNombreSolicitante())).append("\",")
                    .append("\"fecha\":\"").append(Json.esc(r.getFechaSolicitada())).append("\",")
                    .append("\"estado\":\"").append(Json.esc(r.getEstado())).append("\",")
                    .append("\"motivo\":\"").append(Json.esc(r.getMotivoRechazo())).append("\"")
                    .append("}");
            }
            json.append("]");
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json);

        } catch (SQLException e) {
            getServletContext().log("Error al listar mis reservas", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(Json.error("No se pudieron cargar tus solicitudes."));
        }
    }
}
