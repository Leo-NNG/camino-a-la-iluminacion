package pe.caminoiluminacion.conexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Punto único de conexión a la base de datos.
 *
 * En un proyecto real, estos datos (host, usuario, contraseña) no deberían
 * ir escritos directamente en el código, sino leerse desde variables de
 * entorno o un archivo de configuración fuera del control de versiones.
 * Aquí se dejan explícitos para que sea fácil de entender y ajustar
 * mientras el proyecto está en la etapa académica.
 */
public class Conexion {

    // Se leen de variables de entorno (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD).
    // Si no existen, se usan los valores locales de desarrollo.
    private static final String HOST = env("DB_HOST", "localhost");
    private static final String PUERTO = env("DB_PORT", "3306");
    private static final String BASE_DATOS = env("DB_NAME", "camino_iluminacion");
    private static final String USUARIO = env("DB_USER", "root");
    private static final String CONTRASENA = env("DB_PASSWORD", "1234");

    private static final String URL =
        "jdbc:mysql://" + HOST + ":" + PUERTO + "/" + BASE_DATOS
        + "?useUnicode=true&characterEncoding=UTF-8"
        + "&connectionCollation=utf8mb4_unicode_ci"
        + "&serverTimezone=America/Lima"
        + "&useSSL=false&allowPublicKeyRetrieval=true";

    private static String env(String clave, String porDefecto) {
        String v = System.getenv(clave);
        return (v == null || v.isBlank()) ? porDefecto : v;
    }

    private Conexion() {
        // Clase utilitaria: no se instancia.
    }

    /**
     * Abre una nueva conexión. Quien la use es responsable de cerrarla
     * (idealmente con try-with-resources, como se hace en los DAO).
     */
    public static Connection obtener() throws SQLException {
        try {
            // Registro explícito del driver que realmente trae el pom (MariaDB, que
            // también acepta URLs jdbc:mysql://). Así Tomcat lo encuentra en WEB-INF/lib.
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("No se encontró el driver JDBC (mariadb-java-client): " + e.getMessage(), e);
        }
        return DriverManager.getConnection(URL, USUARIO, CONTRASENA);
    }
}
