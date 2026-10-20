package pe.caminoiluminacion.util;

import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.ReglaNegocioException;

import java.io.PrintWriter;

/** Respuestas JSON de error comunes a todos los servlets. */
public final class Respuesta {

    private Respuesta() { }

    public static void error(HttpServletResponse response, PrintWriter out, int estado, String mensaje) {
        response.setStatus(estado);
        out.print(Json.error(mensaje));
    }

    /** Una regla de negocio no se cumplió: el servicio ya decidió el código HTTP y el mensaje. */
    public static void error(HttpServletResponse response, PrintWriter out, ReglaNegocioException e) {
        error(response, out, e.getEstado(), e.getMessage());
    }

    public static void errorInterno(HttpServletResponse response, PrintWriter out, String mensajeUsuario) {
        error(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, mensajeUsuario);
    }
}
