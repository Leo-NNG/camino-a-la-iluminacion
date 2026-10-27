package pe.caminoiluminacion.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClavesTest {

    @Test
    void elHashTieneFormatoYNoContieneLaClave() {
        String hash = Claves.hashear("secreto123");
        assertTrue(hash.startsWith("pbkdf2$"));
        assertEquals(4, hash.split("\\$").length);
        assertFalse(hash.contains("secreto123"));
    }

    @Test
    void cadaHashUsaUnaSalDistinta() {
        assertFalse(Claves.hashear("igual").equals(Claves.hashear("igual")));
    }

    @Test
    void verificaLaClaveCorrectaYRechazaLasDemas() {
        String hash = Claves.hashear("secreto123");
        assertTrue(Claves.verificar("secreto123", hash));
        assertFalse(Claves.verificar("secreto124", hash));
        assertFalse(Claves.verificar("", hash));
    }

    @Test
    void formatosDesconocidosOInvalidosNuncaValidan() {
        assertFalse(Claves.verificar("x", "$2y$10$reemplazar_por_hash_real"));
        assertFalse(Claves.verificar("x", "pbkdf2$no-es-numero$@@@$@@@"));
        assertFalse(Claves.verificar("x", ""));
        assertFalse(Claves.verificar(null, Claves.hashear("x")));
        assertFalse(Claves.verificar("x", null));
    }
}
