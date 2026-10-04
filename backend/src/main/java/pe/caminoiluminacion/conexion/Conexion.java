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

    private static final String HOST = "localhost";
    private static final String PUERTO = "3306";
    private static final String BASE_DATOS = "camino_iluminacion";
    private static final String USUARIO = "root";
    private static final String CONTRASENA = "1234";

    private static final String URL =
        "jdbc:mysql://" + HOST + ":" + PUERTO + "/" + BASE_DATOS
        + "?useUnicode=true&characterEncoding=UTF-8"
        + "&connectionCollation=utf8mb4_unicode_ci"
        + "&serverTimezone=America/Lima"
        + "&useSSL=false&allowPublicKeyRetrieval=true";

    private Conexion() {
        // Clase utilitaria: no se instancia.
    }

    /**
     * Abre una nueva conexión. Quien la use es responsable de cerrarla
     * (idealmente con try-with-resources, como se hace en los DAO).
     */
    public static Connection obtener() throws SQLException {
        try {
            // Registramos explícitamente el Driver para que Tomcat lo reconozca
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("No se encontró el driver de MySQL: " + e.getMessage(), e);
        }
        return DriverManager.getConnection(URL, USUARIO, CONTRASENA);
    }
}
