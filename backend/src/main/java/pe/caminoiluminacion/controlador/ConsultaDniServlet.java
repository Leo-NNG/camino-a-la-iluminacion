
package pe.caminoiluminacion.controlador;

import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.ConsultaDniService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;

import java.io.IOException;

@WebServlet("/api/consulta-dni")
public class ConsultaDniServlet extends HttpServlet {

    private final ConsultaDniService consultaDni;

    public ConsultaDniServlet() {
        this(Servicios.consultaDni());
    }

    ConsultaDniServlet(ConsultaDniService consultaDni) {
        this.consultaDni = consultaDni;
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        try {
            String dni = request.getParameter("dni");

            ConsultaDniService.DatosDni datos =
                    consultaDni.consultar(dni);

            JsonObject resultado = new JsonObject();
            resultado.addProperty("exito", true);
            resultado.addProperty("nombres", datos.nombres());
            resultado.addProperty("apellidos", datos.apellidos());

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().print(resultado);

        } catch (ReglaNegocioException e) {
            JsonObject error = new JsonObject();
            error.addProperty("exito", false);
            error.addProperty("mensaje", e.getMessage());

            response.setStatus(e.getEstado());
            response.getWriter().print(error);
        }
    }
}
