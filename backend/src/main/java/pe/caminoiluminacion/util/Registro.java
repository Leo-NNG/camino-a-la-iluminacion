package pe.caminoiluminacion.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registro de eventos del servidor con SLF4J + Logback (configuración en logback.xml: consola y archivo
 * rotativo diario). Es la única puerta de entrada al registro: el resto del código no conoce Logback.
 * Nunca debe recibir contraseñas, DNI ni números de operación completos.
 */
public final class Registro {

    private static final Logger LOG = LoggerFactory.getLogger("pe.caminoiluminacion");

    private Registro() { }

    public static void error(String mensaje, Throwable causa) {
        LOG.error(mensaje, causa);
    }

    public static void aviso(String mensaje) {
        LOG.warn(mensaje);
    }

    public static void info(String mensaje) {
        LOG.info(mensaje);
    }
}
