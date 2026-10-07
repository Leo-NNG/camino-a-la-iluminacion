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
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.regex.Pattern;

/** Registro de feligreses. Las cuentas de personal NO se crean desde aquí. */
@WebServlet("/api/registro")
public class RegistroServlet extends HttpServlet {

    private static final Pattern NOMBRE = Pattern.compile("^[\\p{L}][\\p{L} .'-]{1,79}$");
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UsuarioDAO usuarioDAO;

    public RegistroServlet() {
        this(new UsuarioDAOImpl());
    }

    RegistroServlet(UsuarioDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String dni = valor(request, "dni");
        String nombre = valor(request, "nombre");
        String apellido = valor(request, "apellido");
        String correo = valor(request, "correo").toLowerCase();
        String telefono = valor(request, "telefono");
        String contrasena = request.getParameter("contrasena");
        String ubicacion = valor(request, "ubicacion");

        // Validación en el servidor (no basta con la del navegador).
        if (!dni.matches("[0-9]{8}")) { error(response, out, 400, "El DNI debe tener 8 dígitos."); return; }
        if (!NOMBRE.matcher(nombre).matches()) { error(response, out, 400, "Ingresa tus nombres (solo letras)."); return; }
        if (!NOMBRE.matcher(apellido).matches()) { error(response, out, 400, "Ingresa tus apellidos (solo letras)."); return; }
        if (correo.length() > 120 || !CORREO.matcher(correo).matches()) { error(response, out, 400, "Ingresa un correo válido."); return; }
        if (!telefono.isEmpty() && !telefono.matches("[0-9]{7,9}")) { error(response, out, 400, "El teléfono debe tener de 7 a 9 dígitos."); return; }
        if (contrasena == null || contrasena.length() < 6 || contrasena.length() > 100) { error(response, out, 400, "La contraseña debe tener entre 6 y 100 caracteres."); return; }
        if (ubicacion.length() > 150) ubicacion = ubicacion.substring(0, 150);

        try {
            Usuario nuevo = new Usuario(dni, nombre, apellido, correo,
                    telefono.isEmpty() ? null : telefono,
                    Claves.hashear(contrasena), Sesion.ROL_FELIGRES,
                    ubicacion.isEmpty() ? null : ubicacion);
            usuarioDAO.insertar(nuevo);

            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"mensaje\":\"Usuario registrado correctamente\"}");

        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState()) || e.getErrorCode() == 1062) {
                String m = String.valueOf(e.getMessage());
                error(response, out, 409, m.contains("uq_usuario_dni")
                        ? "Ya existe una cuenta registrada con ese DNI."
                        : "Ya existe una cuenta registrada con ese correo.");
            } else {
                getServletContext().log("Error al registrar usuario", e);
                error(response, out, 500, "No se pudo registrar la cuenta. Intenta de nuevo.");
            }
        }
    }

    private static String valor(HttpServletRequest r, String nombre) {
        String v = r.getParameter(nombre);
        return v == null ? "" : v.trim();
    }

    private static void error(HttpServletResponse resp, PrintWriter out, int estado, String msg) {
        resp.setStatus(estado);
        out.print(Json.error(msg));
    }
}
