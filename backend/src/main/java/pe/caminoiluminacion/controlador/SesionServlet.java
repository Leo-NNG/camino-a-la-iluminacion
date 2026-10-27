package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.impl.ParroquiaDAOImpl;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.sql.SQLException;

/** Le dice al navegador quién está autenticado según el servidor (el front no debe confiar en localStorage). */
@WebServlet("/api/sesion")
public class SesionServlet extends HttpServlet {

    private final ParroquiaDAO parroquiaDAO;

    public SesionServlet() {
        this(new ParroquiaDAOImpl());
    }

    SesionServlet(ParroquiaDAO parroquiaDAO) {
        this.parroquiaDAO = parroquiaDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (!Sesion.hayUsuario(request)) {
            response.getWriter().print("{\"autenticado\":false}");
            return;
        }
        StringBuilder json = new StringBuilder("{\"autenticado\":true,\"nombre\":\"")
                .append(Json.esc(Sesion.nombre(request))).append("\",\"rol\":\"")
                .append(Json.esc(Sesion.rol(request))).append("\"");
        if (Sesion.esAdministrador(request) && Sesion.idParroquia(request) != null) {
            try {
                parroquiaDAO.buscarPorId(Sesion.idParroquia(request)).ifPresent(p ->
                        json.append(",\"parroquia\":\"").append(Json.esc(p.getNombre())).append("\""));
            } catch (SQLException e) {
                Registro.error("No se pudo leer la parroquia del administrador", e);
            }
        }
        response.getWriter().print(json.append("}"));
    }
}
