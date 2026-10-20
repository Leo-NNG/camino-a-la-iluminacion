package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.servicio.UsuarioService;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/** Inicio de sesión con DNI (8 dígitos) y contraseña. La lógica está en UsuarioService. */
@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {

    private final UsuarioService usuarios;

    public LoginServlet() {
        this(Servicios.usuarios());
    }

    LoginServlet(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            Usuario u = usuarios.autenticar(request.getParameter("dni"), request.getParameter("contrasena"), request.getRemoteAddr());
            Sesion.iniciar(request, u);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"nombre\":\"" + Json.esc(u.getNombre()) + "\",\"rol\":\"" + Json.esc(u.getRol()) + "\"}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error en el inicio de sesión", e);
            Respuesta.errorInterno(response, out, "No se pudo iniciar sesión. Intenta de nuevo.");
        }
    }
}
