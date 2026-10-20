package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.AvisoDAO;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.impl.AvisoDAOImpl;
import pe.caminoiluminacion.dao.impl.ParroquiaDAOImpl;
import pe.caminoiluminacion.modelo.Aviso;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Avisos PÚBLICOS.
 * GET /api/avisos?parroquia=N : los avisos de esa parroquia (más recientes primero).
 * GET /api/avisos?urgentes=1  : los 3 avisos urgentes más recientes de todas las parroquias (alertas del sitio).
 */
@WebServlet("/api/avisos")
public class AvisosServlet extends HttpServlet {

    private static final int LIMITE_URGENTES = 3;

    private final AvisoDAO avisoDAO;
    private final ParroquiaDAO parroquiaDAO;

    public AvisosServlet() {
        this(new AvisoDAOImpl(), new ParroquiaDAOImpl());
    }

    AvisosServlet(AvisoDAO avisoDAO, ParroquiaDAO parroquiaDAO) {
        this.avisoDAO = avisoDAO;
        this.parroquiaDAO = parroquiaDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        PrintWriter out = response.getWriter();
        try {
            List<Aviso> lista;
            if ("1".equals(request.getParameter("urgentes"))) {
                lista = avisoDAO.listarUrgentesRecientes(LIMITE_URGENTES);
            } else {
                int idParroquia;
                try {
                    idParroquia = Integer.parseInt(String.valueOf(request.getParameter("parroquia")).trim());
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(Json.error("Indica la parroquia."));
                    return;
                }
                lista = avisoDAO.listarPorParroquia(idParroquia);
            }
            Map<Integer, String> nombres = new HashMap<>();
            for (Parroquia p : parroquiaDAO.listarTodas()) nombres.put(p.getIdParroquia(), p.getNombre());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(aJson(lista, nombres));
        } catch (SQLException e) {
            Registro.error("Error al listar avisos", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(Json.error("No se pudieron cargar los avisos."));
        }
    }

    static String aJson(List<Aviso> lista, Map<Integer, String> parroquias) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lista.size(); i++) {
            Aviso a = lista.get(i);
            if (i > 0) json.append(",");
            json.append("{\"id\":").append(a.getIdAviso())
                .append(",\"parroquia\":\"").append(Json.esc(parroquias.get(a.getIdParroquia()))).append("\"")
                .append(",\"titulo\":\"").append(Json.esc(a.getTitulo())).append("\"")
                .append(",\"descripcion\":\"").append(Json.esc(a.getDescripcion())).append("\"")
                .append(",\"tipo\":\"").append(Json.esc(a.getTipo())).append("\"")
                .append(",\"urgente\":").append(a.isUrgente())
                .append(",\"fechaEvento\":\"").append(Json.fecha(a.getFechaEvento())).append("\"")
                .append(",\"publicadoEn\":\"").append(Json.fecha(a.getPublicadoEn())).append("\"}");
        }
        return json.append("]").toString();
    }
}
