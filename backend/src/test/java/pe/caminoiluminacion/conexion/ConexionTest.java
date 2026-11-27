package pe.caminoiluminacion.conexion;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConexionTest {

    private static Conexion.Config leer(Map<String, String> vars) throws SQLException {
        return Conexion.leerConfig(vars::get);
    }

    @Test
    void sinUsuarioNiContrasenaNoInventaValores() {
        SQLException e = assertThrows(SQLException.class, () -> leer(Map.of()));
        assertTrue(e.getMessage().contains("DB_USER y DB_PASSWORD"), e.getMessage());
        assertFalse(e.getMessage().contains("1234"));
    }

    @Test
    void faltaSoloLaContrasenaOSoloElUsuario() {
        SQLException sinClave = assertThrows(SQLException.class, () -> leer(Map.of("DB_USER", "app")));
        assertTrue(sinClave.getMessage().contains("DB_PASSWORD"));
        assertFalse(sinClave.getMessage().contains("DB_USER"));

        SQLException sinUsuario = assertThrows(SQLException.class, () -> leer(Map.of("DB_PASSWORD", "x")));
        assertTrue(sinUsuario.getMessage().contains("DB_USER"));
        assertFalse(sinUsuario.getMessage().contains("DB_PASSWORD"));
    }

    @Test
    void valoresEnBlancoCuentanComoFaltantes() {
        assertThrows(SQLException.class, () -> leer(Map.of("DB_USER", "  ", "DB_PASSWORD", "x")));
        assertThrows(SQLException.class, () -> leer(Map.of("DB_USER", "app", "DB_PASSWORD", "")));
    }

    @Test
    void usaLosValoresNoSecretosPorDefecto() throws SQLException {
        Conexion.Config c = leer(Map.of("DB_USER", "app", "DB_PASSWORD", "secreta"));
        assertEquals("app", c.usuario());
        assertEquals("secreta", c.contrasena());
        assertTrue(c.url().startsWith("jdbc:mysql://localhost:3306/camino_iluminacion?"), c.url());
        assertTrue(c.url().contains("serverTimezone=America/Lima"));
    }

    @Test
    void respetaHostPuertoYBase() throws SQLException {
        Map<String, String> vars = new HashMap<>();
        vars.put("DB_USER", " app ");
        vars.put("DB_PASSWORD", "secreta");
        vars.put("DB_HOST", "db");
        vars.put("DB_PORT", "3307");
        vars.put("DB_NAME", "camino_iluminacion_dev");
        Conexion.Config c = leer(vars);
        assertEquals("app", c.usuario());
        assertTrue(c.url().startsWith("jdbc:mysql://db:3307/camino_iluminacion_dev?"), c.url());
    }

    @Test
    void puertoInvalidoSeRechaza() {
        assertThrows(SQLException.class,
                () -> leer(Map.of("DB_USER", "app", "DB_PASSWORD", "x", "DB_PORT", "3306/otra")));
    }

    @Test
    void toStringNoMuestraLaContrasena() throws SQLException {
        Conexion.Config c = leer(Map.of("DB_USER", "app", "DB_PASSWORD", "secreta"));
        assertFalse(c.toString().contains("secreta"));
    }
}
