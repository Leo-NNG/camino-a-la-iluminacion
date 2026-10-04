package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.impl.ReservaDAOImpl;
import pe.caminoiluminacion.modelo.Reserva;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet("/api/admin/reservas")
public class ListarReservasAdminServlet extends HttpServlet {

    private final ReservaDAO reservaDAO = new ReservaDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            // Obtenemos la lista de reservas pendientes (o puedes ajustar el método según tu DAO)
            List<Reserva> lista = reservaDAO.listarPorEstado("Pendiente");

            // Construimos un JSON completo usando los getters reales de la clase Reserva
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                Reserva r = lista.get(i);
                json.append("{")
                        .append("\"id\":").append(r.getIdReserva()).append(",")
                        .append("\"sacramento\":\"").append(r.getSacramento()).append("\",")
                        .append("\"nombreSolicitante\":\"").append(r.getNombreSolicitante()).append("\",")
                        .append("\"fecha\":\"").append(r.getFechaSolicitada()).append("\",")
                        .append("\"estado\":\"").append(r.getEstado()).append("\"")
                        .append("}");
                if (i < lista.size() - 1) json.append(",");
            }
            json.append("]");

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.toString());

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}