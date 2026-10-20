package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.impl.ParroquiaDAOImpl;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Información PÚBLICA de las parroquias (mapa, fichas, selector de reservas). No requiere sesión.
 * GET /api/parroquias        -> todas
 * GET /api/parroquias?id=N   -> una (404 si no existe)
 */
@WebServlet("/api/parroquias")
public class ParroquiasServlet extends HttpServlet {

    private final ParroquiaDAO parroquiaDAO;

    public ParroquiasServlet() {
        this(new ParroquiaDAOImpl());
    }

    ParroquiasServlet(ParroquiaDAO parroquiaDAO) {
        this.parroquiaDAO = parroquiaDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        PrintWriter out = response.getWriter();
        try {
            String idTxt = request.getParameter("id");
            if (idTxt != null) {
                int id;
                try {
                    id = Integer.parseInt(idTxt.trim());
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print(Json.error("Identificador no válido."));
                    return;
                }
                Optional<Parroquia> p = parroquiaDAO.buscarPorId(id);
                if (p.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.print(Json.error("La parroquia no existe."));
                    return;
                }
                response.setStatus(HttpServletResponse.SC_OK);
                out.print(aJson(p.get()));
                return;
            }
            List<Parroquia> lista = parroquiaDAO.listarTodas();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                if (i > 0) json.append(",");
                json.append(aJson(lista.get(i)));
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.append("]"));
        } catch (SQLException e) {
            Registro.error("Error al listar parroquias", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(Json.error("No se pudo cargar la información de las parroquias."));
        }
    }

    static String aJson(Parroquia p) {
        StringBuilder sac = new StringBuilder("[");
        if (p.getSacramentosDisponibles() != null) {
            boolean primero = true;
            for (String s : p.getSacramentosDisponibles().split(",")) {
                if (s.isBlank()) continue;
                if (!primero) sac.append(",");
                sac.append("\"").append(Json.esc(s.trim())).append("\"");
                primero = false;
            }
        }
        sac.append("]");
        return "{\"id\":" + p.getIdParroquia()
                + ",\"nombre\":\"" + Json.esc(p.getNombre()) + "\""
                + ",\"direccion\":\"" + Json.esc(p.getDireccion()) + "\""
                + ",\"lat\":" + numero(p.getLatitud())
                + ",\"lng\":" + numero(p.getLongitud())
                + ",\"sacramentos\":" + sac
                + ",\"fotoUrl\":\"" + Json.esc(urlSegura(p.getFotoUrl())) + "\""
                + ",\"fotoInteriorUrl\":\"" + Json.esc(urlSegura(p.getFotoInteriorUrl())) + "\""
                + ",\"horarioTramites\":\"" + Json.esc(p.getHorarioTramites()) + "\"}";
    }

    private static String numero(java.math.BigDecimal n) {
        return n == null ? "null" : n.toPlainString();
    }

    /** Solo se publican URLs http(s): nunca javascript: ni data: aunque alguien las guarde en la base. */
    private static String urlSegura(String url) {
        if (url == null) return "";
        String u = url.trim().toLowerCase();
        return (u.startsWith("https://") || u.startsWith("http://")) ? url.trim() : "";
    }
}
