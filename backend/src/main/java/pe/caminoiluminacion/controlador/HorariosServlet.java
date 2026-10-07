package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.HorarioDAO;
import pe.caminoiluminacion.dao.impl.HorarioDAOImpl;
import pe.caminoiluminacion.modelo.Horario;
import pe.caminoiluminacion.util.Json;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/** Horarios PÚBLICOS de una parroquia: GET /api/horarios?parroquia=N (misas y actividades). */
@WebServlet("/api/horarios")
public class HorariosServlet extends HttpServlet {

    private final HorarioDAO horarioDAO;

    public HorariosServlet() {
        this(new HorarioDAOImpl());
    }

    HorariosServlet(HorarioDAO horarioDAO) {
        this.horarioDAO = horarioDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        PrintWriter out = response.getWriter();
        int idParroquia;
        try {
            idParroquia = Integer.parseInt(String.valueOf(request.getParameter("parroquia")).trim());
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(Json.error("Indica la parroquia."));
            return;
        }
        try {
            List<Horario> lista = horarioDAO.listarPorParroquia(idParroquia);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(aJson(lista));
        } catch (SQLException e) {
            getServletContext().log("Error al listar horarios", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(Json.error("No se pudieron cargar los horarios."));
        }
    }

    static String aJson(List<Horario> lista) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lista.size(); i++) {
            Horario h = lista.get(i);
            if (i > 0) json.append(",");
            json.append("{\"id\":").append(h.getIdHorario())
                .append(",\"dia\":\"").append(Json.esc(h.getDiaSemana())).append("\"")
                .append(",\"hora\":\"").append(Json.esc(h.getHora())).append("\"")
                .append(",\"tipo\":\"").append(Json.esc(h.getTipo())).append("\"}");
        }
        return json.append("]").toString();
    }
}
