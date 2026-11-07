package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.CuentaService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * "Mi cuenta". Siempre actúa sobre la persona de la sesión (nunca recibe un id de usuario del navegador).
 *   GET  /api/cuenta             datos de la cuenta (DNI enmascarado)
 *   GET  /api/cuenta/datos       descarga una copia de los datos en JSON
 *   POST /api/cuenta/contacto    correo y teléfono
 *   POST /api/cuenta/contrasena  cambia la contraseña (pide la actual)
 *   POST /api/cuenta/politica    acepta la política de privacidad vigente
 *   POST /api/cuenta/eliminar    elimina (anonimiza) la cuenta (pide la contraseña)
 * Los POST pasan por FiltroOrigen (CSRF).
 */
@WebServlet({"/api/cuenta", "/api/cuenta/*"})
public class CuentaServlet extends HttpServlet {

    private final CuentaService cuenta;

    public CuentaServlet() {
        this(Servicios.cuenta());
    }

    CuentaServlet(CuentaService cuenta) {
        this.cuenta = cuenta;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String ruta = ruta(request);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer id = Sesion.idUsuario(request);
        if (id == null) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return; }
        try {
            switch (ruta) {
                case "" -> out.print(CuentaJson.perfil(cuenta.perfil(id)));
                case "/datos" -> {
                    String json = CuentaJson.datos(cuenta.exportarDatos(id, request.getRemoteAddr()));
                    response.setHeader("Content-Disposition", "attachment; filename=\"mis-datos-camino-a-la-iluminacion.json\"");
                    out.print(json);
                }
                default -> Respuesta.error(response, out, 404, "No existe.");
            }
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error en Mi cuenta (lectura)", e);
            Respuesta.errorInterno(response, out, "No se pudo completar la operación. Intenta de nuevo.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer id = Sesion.idUsuario(request);
        if (id == null) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return; }
        String ip = request.getRemoteAddr();
        try {
            String mensaje;
            switch (ruta(request)) {
                case "/contacto" -> {
                    cuenta.actualizarContacto(id, request.getParameter("correo"), request.getParameter("telefono"), ip);
                    mensaje = "Datos de contacto actualizados.";
                }
                case "/contrasena" -> {
                    cuenta.cambiarContrasena(id, request.getParameter("actual"), request.getParameter("nueva"), ip);
                    mensaje = "Contraseña actualizada.";
                }
                case "/politica" -> {
                    cuenta.aceptarPolitica(id, ip);
                    mensaje = "Gracias: registramos tu aceptación de la Política de privacidad.";
                }
                case "/eliminar" -> {
                    cuenta.eliminarCuenta(id, request.getParameter("contrasena"), ip);
                    Sesion.revocar(id);  // también vale para sus otras sesiones abiertas
                    Sesion.cerrar(request);
                    mensaje = "Tu cuenta fue eliminada.";
                }
                default -> { Respuesta.error(response, out, 404, "No existe."); return; }
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"mensaje\":\"" + pe.caminoiluminacion.util.Json.esc(mensaje) + "\"}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error en Mi cuenta", e);
            Respuesta.errorInterno(response, out, "No se pudo completar la operación. Intenta de nuevo.");
        }
    }

    private static String ruta(HttpServletRequest request) {
        String p = request.getPathInfo();
        return p == null ? "" : (p.endsWith("/") ? p.substring(0, p.length() - 1) : p);
    }
}
