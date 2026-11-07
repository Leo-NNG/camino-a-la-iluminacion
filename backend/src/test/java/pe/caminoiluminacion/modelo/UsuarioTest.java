package pe.caminoiluminacion.modelo;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioTest {

    @Test
    void muestraElNombreCompletoOLaLeyendaDeCuentaEliminada() {
        Usuario u = new Usuario("12345678", "Ana", "Torres", "a@x.pe", null, "h", "feligres", null);
        assertEquals("Ana Torres", u.nombreParaMostrar());
        assertFalse(u.estaEliminado());
        u.setEliminadoEn(new Timestamp(1));
        assertTrue(u.estaEliminado());
        assertEquals("(cuenta eliminada)", u.nombreParaMostrar());
    }
}
