package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.ConsultaDniService;
import pe.caminoiluminacion.servicio.ConsultaDniService.DatosDni;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Autocompletar nombres en el registro a partir del DNI (Factiliza, desde el servidor).
 * <ul>
 *   <li>GET  /api/consulta-dni          : {"disponible":true|false} (si hay token configurado).</li>
 *   <li>POST /api/consulta-dni  dni=…   : {"exito":true,"nombres":"…","apellidos":"…"}.
 *       Si falla responde el código de error con {"exito":false,"error":"…","manual":true}: el formulario
 *       deja escribir los nombres a mano y el registro no se bloquea.</li>
 * </ul>
 * Va por POST (y no por GET con ?dni=) para que el DNI no quede en los registros de acceso del servidor
 * y para que pase por FiltroOrigen como cualquier otra petición que viene de un formulario.
 */
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
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        preparar(response);
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().print("{\"disponible\":" + consultaDni.disponible() + "}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        preparar(response);
        PrintWriter out = response.getWriter();
        try {
            DatosDni d = consultaDni.consultar(request.getParameter("dni"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"nombres\":\"" + Json.esc(d.nombres()) + "\",\"apellidos\":\"" + Json.esc(d.apellidos()) + "\"}");
        } catch (ReglaNegocioException e) {
            response.setStatus(e.getEstado());
            out.print("{\"exito\":false,\"error\":\"" + Json.esc(e.getMessage()) + "\",\"manual\":true}");
        }
    }

    private static void preparar(HttpServletResponse response) {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
    }
}
