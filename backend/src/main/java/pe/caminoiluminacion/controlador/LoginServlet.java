package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.dao.impl.UsuarioDAOImpl;
import pe.caminoiluminacion.modelo.Usuario;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;

@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {

    private final UsuarioDAO usuarioDAO = new UsuarioDAOImpl();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String correo = request.getParameter("correo");
        String contrasena = request.getParameter("contrasena");

        try {
            Optional<Usuario> usuarioOpt = usuarioDAO.buscarPorCorreo(correo);

            if (usuarioOpt.isPresent()) {
                Usuario u = usuarioOpt.get();
                // Validamos la contraseña (para la demo comparamos directo o con tu campo)
                if (u.getContrasenaHash() != null && u.getContrasenaHash().equals(contrasena)) {

                    // Opcional: Guardamos sesión en el servidor
                    HttpSession session = request.getSession();
                    session.setAttribute("usuarioLogueado", u);

                    response.setStatus(HttpServletResponse.SC_OK);
                    out.print("{\"exito\": true, \"nombre\": \"" + u.getNombre() + "\", \"rol\": \"" + u.getRol() + "\"}");
                    return;
                }
            }

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"exito\": false, \"error\": \"Correo o contraseña incorrectos\"}");

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"exito\": false, \"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
