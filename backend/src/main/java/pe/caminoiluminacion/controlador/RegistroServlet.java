package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.servicio.UsuarioService;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Registro;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/** Registro de feligreses. Las cuentas de personal NO se crean desde aquí. */
@WebServlet("/api/registro")
public class RegistroServlet extends HttpServlet {

    private final UsuarioService usuarios;

    public RegistroServlet() {
        this(Servicios.usuarios());
    }

    RegistroServlet(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            usuarios.registrarFeligres(request.getParameter("dni"), request.getParameter("nombre"),
                    request.getParameter("apellido"), request.getParameter("correo"), request.getParameter("telefono"),
                    request.getParameter("contrasena"), request.getParameter("ubicacion"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"mensaje\":\"Usuario registrado correctamente\"}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al registrar usuario", e);
            Respuesta.errorInterno(response, out, "No se pudo registrar la cuenta. Intenta de nuevo.");
        }
    }
}
