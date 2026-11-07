package pe.caminoiluminacion.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import pe.caminoiluminacion.modelo.Usuario;

import java.util.concurrent.TimeUnit;

/** Acceso centralizado a los datos de la sesión del servidor (única fuente de verdad del rol). */
public final class Sesion {

    public static final String ROL_FELIGRES = "feligres";
    public static final String ROL_ADMIN = "administrador";

    private static final String ID = "idUsuario";
    private static final String ROL = "rol";
    private static final String NOMBRE = "nombre";
    private static final String PARROQUIA = "idParroquia";
    private static final int MINUTOS_INACTIVIDAD = 30;

    /**
     * Cuentas eliminadas: sus otras sesiones abiertas (otro navegador o pestaña) dejan de valer. Basta recordarlas más
     * tiempo que la inactividad máxima de una sesión, porque cada sesión revocada se invalida al detectarla.
     */
    private static final Cache<Integer, Boolean> REVOCADOS = CacheBuilder.newBuilder()
            .expireAfterWrite(MINUTOS_INACTIVIDAD + 1, TimeUnit.MINUTES).maximumSize(10_000).build();

    private Sesion() { }

    /** Deja sin efecto toda sesión de esta cuenta (se usa al eliminarla). */
    public static void revocar(int idUsuario) { REVOCADOS.put(idUsuario, Boolean.TRUE); }

    /** Crea una sesión nueva tras autenticar (invalida la anterior para evitar fijación de sesión). */
    public static void iniciar(HttpServletRequest request, Usuario u) {
        HttpSession vieja = request.getSession(false);
        if (vieja != null) vieja.invalidate();
        HttpSession s = request.getSession(true);
        s.setMaxInactiveInterval(MINUTOS_INACTIVIDAD * 60);
        s.setAttribute(ID, u.getIdUsuario());
        s.setAttribute(ROL, u.getRol());
        s.setAttribute(NOMBRE, u.getNombre());
        if (u.getIdParroquia() != null) s.setAttribute(PARROQUIA, u.getIdParroquia());
    }

    public static void cerrar(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) s.invalidate();
    }

    public static boolean hayUsuario(HttpServletRequest request) { return idUsuario(request) != null; }
    public static String rol(HttpServletRequest request) { return (String) atributo(request, ROL); }
    public static String nombre(HttpServletRequest request) { return (String) atributo(request, NOMBRE); }
    public static Integer idUsuario(HttpServletRequest request) {
        Integer id = (Integer) atributo(request, ID);
        if (id != null && REVOCADOS.getIfPresent(id) != null) {
            cerrar(request); // cuenta eliminada: esta sesión ya no vale
            return null;
        }
        return id;
    }
    public static Integer idParroquia(HttpServletRequest request) { return (Integer) atributo(request, PARROQUIA); }

    public static boolean esAdministrador(HttpServletRequest request) {
        return hayUsuario(request) && ROL_ADMIN.equals(rol(request));
    }

    public static boolean esFeligres(HttpServletRequest request) {
        return hayUsuario(request) && ROL_FELIGRES.equals(rol(request));
    }

    private static Object atributo(HttpServletRequest request, String nombre) {
        HttpSession s = request.getSession(false);
        return s == null ? null : s.getAttribute(nombre);
    }
}
