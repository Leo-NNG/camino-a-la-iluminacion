package pe.caminoiluminacion.conexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Punto único de conexión a la base de datos.
 *
 * <p>Los datos se leen SOLO de variables de entorno; en el código no hay usuario ni contraseña:
 * <ul>
 *   <li>{@code DB_USER} y {@code DB_PASSWORD}: obligatorias. Si falta alguna, {@link #obtener()} lanza una
 *       {@link SQLException} que dice cuál falta (no se intenta entrar con valores inventados).</li>
 *   <li>{@code DB_HOST} ({@code localhost}), {@code DB_PORT} ({@code 3306}) y {@code DB_NAME}
 *       ({@code camino_iluminacion}): opcionales, con esos valores por defecto porque no son secretos.</li>
 * </ul>
 */
public class Conexion {

    /** Datos de conexión ya validados. */
    record Config(String url, String usuario, String contrasena) {
        @Override
        public String toString() { // nunca mostrar la contraseña en un log o un error
            return "Config[url=" + url + ", usuario=" + usuario + "]";
        }
    }

    private static volatile Config config;

    private Conexion() {
        // Clase utilitaria: no se instancia.
    }

    /**
     * Abre una nueva conexión. Quien la use es responsable de cerrarla
     * (idealmente con try-with-resources, como se hace en los DAO).
     *
     * @throws SQLException si faltan DB_USER o DB_PASSWORD, o si la base no responde
     */
    public static Connection obtener() throws SQLException {
        Config c = config;
        if (c == null) {
            c = leerConfig(System::getenv);
            config = c; // solo se guarda si es válida: corrigiendo las variables y reiniciando Tomcat basta
        }
        registrarDriver();
        return DriverManager.getConnection(c.url(), c.usuario(), c.contrasena());
    }

    /** Arma la configuración desde una fuente de variables (inyectable para las pruebas). */
    static Config leerConfig(Function<String, String> entorno) throws SQLException {
        String usuario = valor(entorno, "DB_USER");
        String contrasena = valor(entorno, "DB_PASSWORD");
        List<String> faltan = new ArrayList<>();
        if (usuario == null) faltan.add("DB_USER");
        if (contrasena == null) faltan.add("DB_PASSWORD");
        if (!faltan.isEmpty()) {
            throw new SQLException((faltan.size() == 1 ? "Falta configurar la variable de entorno "
                    : "Faltan configurar las variables de entorno ") + String.join(" y ", faltan)
                    + " para conectarse a la base de datos (ver backend/README.md, sección «Configurar»).");
        }
        String host = porDefecto(valor(entorno, "DB_HOST"), "localhost");
        String puerto = porDefecto(valor(entorno, "DB_PORT"), "3306");
        String base = porDefecto(valor(entorno, "DB_NAME"), "camino_iluminacion");
        if (!puerto.matches("\\d{1,5}")) {
            throw new SQLException("DB_PORT debe ser un número de puerto (por ejemplo 3306).");
        }
        String url = "jdbc:mysql://" + host + ":" + puerto + "/" + base
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&connectionCollation=utf8mb4_unicode_ci"
                + "&serverTimezone=America/Lima"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
        return new Config(url, usuario, contrasena);
    }

    /** Valor de la variable sin espacios alrededor, o null si no existe o está vacía. */
    private static String valor(Function<String, String> entorno, String clave) {
        String v = entorno.apply(clave);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static String porDefecto(String v, String siNoHay) {
        return v == null ? siNoHay : v;
    }

    /**
     * Intenta registrar el driver de MySQL o el de MariaDB, el que esté disponible en el classpath de
     * Tomcat (ambos aceptan URLs jdbc:mysql://). Si ninguno se encuentra por nombre, no falla aquí:
     * DriverManager todavía puede descubrir cualquier driver JDBC 4 que esté en el classpath, y si de
     * verdad no hay ninguno, getConnection lanza "No suitable driver" con un mensaje claro.
     */
    private static void registrarDriver() {
        for (String clase : new String[] {"com.mysql.cj.jdbc.Driver", "org.mariadb.jdbc.Driver"}) {
            try {
                Class.forName(clase);
                return;
            } catch (ClassNotFoundException e) {
                // probar el siguiente
            }
        }
    }
}
