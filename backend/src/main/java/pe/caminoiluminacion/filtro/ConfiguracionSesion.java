package pe.caminoiluminacion.filtro;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;

/**
 * Endurece la cookie de sesión al iniciar la aplicación:
 *  - HttpOnly: el JavaScript de la página no puede leerla (limita el robo de sesión por XSS);
 *  - SameSite=Lax: el navegador no la envía en peticiones POST iniciadas desde otros sitios.
 * (Marcarla Secure solo tiene sentido con HTTPS: actívalo en el despliegue real con setSecure(true).)
 */
@WebListener
public class ConfiguracionSesion implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent evento) {
        SessionCookieConfig cookie = evento.getServletContext().getSessionCookieConfig();
        cookie.setHttpOnly(true);
        try {
            cookie.setAttribute("SameSite", "Lax");
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            // Contenedor anterior a Servlet 6: queda solo HttpOnly (el FiltroOrigen sigue protegiendo).
        }
    }
}
