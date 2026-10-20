package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.dao.impl.SacramentoDAOImpl;
import pe.caminoiluminacion.modelo.Sacramento;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/** Catálogo PÚBLICO de sacramentos: GET /api/sacramentos. Las tarjetas del formulario de reservas salen de aquí. */
@WebServlet("/api/sacramentos")
public class SacramentosServlet extends HttpServlet {

    private final SacramentoDAO sacramentoDAO;

    public SacramentosServlet() {
        this(new SacramentoDAOImpl());
    }

    SacramentosServlet(SacramentoDAO sacramentoDAO) {
        this.sacramentoDAO = sacramentoDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        PrintWriter out = response.getWriter();
        try {
            List<Sacramento> lista = sacramentoDAO.listarTodos();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                if (i > 0) json.append(",");
                json.append("{\"id\":").append(lista.get(i).getIdSacramento())
                    .append(",\"codigo\":\"").append(Json.esc(lista.get(i).getCodigo())).append("\"")
                    .append(",\"nombre\":\"").append(Json.esc(lista.get(i).getNombre())).append("\"}");
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.append("]"));
        } catch (SQLException e) {
            Registro.error("Error al listar sacramentos", e);
            Respuesta.errorInterno(response, out, "No se pudo cargar el catálogo de sacramentos.");
        }
    }
}
