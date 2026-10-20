package pe.caminoiluminacion.seguridad;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Al arrancar la aplicación: carga la clave de cifrado (así un problema con ella se ve de inmediato y no en el
 * primer inicio de sesión) y cifra los DNI que aún estén en claro.
 */
@WebListener
public class InicioSeguridad implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent evento) {
        Cripto.indice("0"); // fuerza la carga (o creación) de la clave
        MigracionDni.ejecutar();
    }
}
