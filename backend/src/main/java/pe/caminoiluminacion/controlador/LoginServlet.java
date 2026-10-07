package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.dao.impl.UsuarioDAOImpl;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.util.Claves;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.LimiteIntentos;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Optional;

/** Inicio de sesión con DNI (8 dígitos) y contraseña. */
@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {

    /** Se verifica contra este hash cuando el DNI no existe, para que el tiempo de respuesta no delate si la cuenta existe. */
    private static final String HASH_FALSO = Claves.hashear("contrasena-que-nadie-usa");

    private final UsuarioDAO usuarioDAO;
    private final LimiteIntentos limite;

    public LoginServlet() {
        this(new UsuarioDAOImpl(), new LimiteIntentos());
    }

    LoginServlet(UsuarioDAO usuarioDAO, LimiteIntentos limite) {
        this.usuarioDAO = usuarioDAO;
        this.limite = limite;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String dni = request.getParameter("dni");
        String contrasena = request.getParameter("contrasena");
        dni = dni == null ? "" : dni.trim();

        if (!dni.matches("[0-9]{8}")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print(Json.error("El DNI debe tener 8 dígitos."));
            return;
        }

        String claveLimite = request.getRemoteAddr() + "|" + dni;
        if (limite.estaBloqueado(claveLimite)) {
            response.setStatus(429);
            out.print(Json.error("Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo."));
            return;
        }

        try {
            Optional<Usuario> usuarioOpt = usuarioDAO.buscarPorDni(dni);
            boolean valido = Claves.verificar(contrasena,
                    usuarioOpt.isPresent() ? usuarioOpt.get().getContrasenaHash() : HASH_FALSO)
                    && usuarioOpt.isPresent();

            if (valido) {
                Usuario u = usuarioOpt.get();
                limite.reiniciar(claveLimite);
                Sesion.iniciar(request, u);
                response.setStatus(HttpServletResponse.SC_OK);
                out.print("{\"exito\":true,\"nombre\":\"" + Json.esc(u.getNombre())
                        + "\",\"rol\":\"" + Json.esc(u.getRol()) + "\"}");
                return;
            }

            limite.registrarFallo(claveLimite);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(Json.error("DNI o contraseña incorrectos."));

        } catch (SQLException e) {
            getServletContext().log("Error en el inicio de sesión", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print(Json.error("No se pudo iniciar sesión. Intenta de nuevo."));
        }
    }
}
