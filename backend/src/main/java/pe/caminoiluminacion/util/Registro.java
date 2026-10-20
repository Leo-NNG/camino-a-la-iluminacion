package pe.caminoiluminacion.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registro de errores del servidor. Usa java.util.logging, que Tomcat redirige a sus archivos de log
 * (catalina / localhost), y que funciona igual en las pruebas, sin necesitar un contenedor de servlets.
 * Nunca debe recibir contraseñas ni datos personales.
 */
public final class Registro {

    private static final Logger LOG = Logger.getLogger("pe.caminoiluminacion");

    private Registro() { }

    public static void error(String mensaje, Throwable causa) {
        LOG.log(Level.SEVERE, mensaje, causa);
    }
}
