package pe.caminoiluminacion.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;

/** Comprobación común de los servlets del panel: sesión iniciada, rol administrador y parroquia asignada. */
public final class AccesoAdmin {

    private AccesoAdmin() { }

    /** @return la parroquia del administrador, o null si ya respondió con el error correspondiente (401/403). */
    public static Integer parroquia(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        if (!Sesion.hayUsuario(request)) {
            responder(response, out, 401, "Inicia sesión para continuar.");
            return null;
        }
        if (!Sesion.esAdministrador(request)) {
            responder(response, out, 403, "Solo el personal parroquial puede hacer esto.");
            return null;
        }
        Integer idParroquia = Sesion.idParroquia(request);
        if (idParroquia == null) {
            responder(response, out, 403, "Tu cuenta de personal no tiene una parroquia asignada.");
            return null;
        }
        return idParroquia;
    }

    public static void responder(HttpServletResponse response, PrintWriter out, int estado, String mensaje) {
        response.setStatus(estado);
        out.print(Json.error(mensaje));
    }
}
