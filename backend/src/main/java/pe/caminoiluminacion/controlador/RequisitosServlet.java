package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Requisito;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.RequisitoService;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * GET /api/requisitos?parroquia=ID&sacramento=CODIGO : documentos que esa parroquia pide para ese sacramento
 * (información pública: el feligrés la ve antes de reservar).
 */
@WebServlet("/api/requisitos")
public class RequisitosServlet extends HttpServlet {

    private final RequisitoService requisitos;

    public RequisitosServlet() {
        this(Servicios.requisitos());
    }

    RequisitosServlet(RequisitoService requisitos) {
        this.requisitos = requisitos;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        try {
            List<Requisito> lista = requisitos.publicos(request.getParameter("parroquia"), request.getParameter("sacramento"));
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(RequisitosAdminServlet.aJson(lista));
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al consultar los requisitos", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar los requisitos.");
        }
    }
}
