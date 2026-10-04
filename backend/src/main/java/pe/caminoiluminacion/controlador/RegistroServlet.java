package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.dao.impl.UsuarioDAOImpl;
import pe.caminoiluminacion.modelo.Usuario;

import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/api/registro")
public class RegistroServlet extends HttpServlet {

    private final UsuarioDAO usuarioDAO = new UsuarioDAOImpl();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            // 1. Recibir los datos enviados desde el formulario HTML de registro
            String nombre = request.getParameter("nombre");
            String apellido = request.getParameter("apellido");
            String correo = request.getParameter("correo");
            String telefono = request.getParameter("telefono");
            String contrasena = request.getParameter("contrasena"); // O contrasenaHash
            String ubicacion = request.getParameter("ubicacion");

            // 2. Crear el objeto Usuario (asignando rol de feligrés por defecto)
            Usuario nuevoUsuario = new Usuario(
                    nombre,
                    apellido,
                    correo,
                    telefono,
                    contrasena, // En un sistema real aquí aplicarías hashing, para la demo pasamos el texto
                    "feligres",
                    ubicacion
            );

            // 3. Guardar en la base de datos usando el DAO que ya tienes
            usuarioDAO.insertar(nuevoUsuario);

            // 4. Responder con éxito en formato JSON
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\": true, \"mensaje\": \"Usuario registrado correctamente\"}");

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"exito\": false, \"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
