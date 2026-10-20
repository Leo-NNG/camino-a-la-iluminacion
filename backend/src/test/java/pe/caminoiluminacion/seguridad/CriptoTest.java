package pe.caminoiluminacion.seguridad;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CriptoTest {

    static {
        // Clave fija de PRUEBA (32 bytes). Debe fijarse antes de que se cargue la clase Cripto.
        System.setProperty("cai.data.key", Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    void cifraYDescifraSinPerderElTexto() {
        String cifrado = Cripto.cifrar("70000001");
        assertTrue(cifrado.startsWith("v1:"));
        assertFalse(cifrado.contains("70000001"));
        assertEquals("70000001", Cripto.descifrar(cifrado));
    }

    @Test
    void cadaCifradoUsaUnVectorDistinto() {
        assertNotEquals(Cripto.cifrar("70000001"), Cripto.cifrar("70000001"));
    }

    @Test
    void loQueNoEstaCifradoSeDevuelveTalCual() {
        assertEquals("70000001", Cripto.descifrar("70000001")); // datos previos a la migración
        assertNull(Cripto.descifrar(null));
        assertNull(Cripto.cifrar(null));
    }

    @Test
    void unValorManipuladoNoSeDescifra() {
        String cifrado = Cripto.cifrar("70000001");
        char ultimo = cifrado.charAt(cifrado.length() - 2);
        String alterado = cifrado.substring(0, cifrado.length() - 2) + (ultimo == 'A' ? 'B' : 'A') + cifrado.charAt(cifrado.length() - 1);
        assertEquals("", Cripto.descifrar(alterado)); // GCM detecta la alteración
    }

    @Test
    void laHuellaEsDeterministaYNoRevelaElDni() {
        String h = Cripto.indice("70000001");
        assertEquals(h, Cripto.indice("70000001"));
        assertNotEquals(h, Cripto.indice("70000002"));
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"));
        assertFalse(h.contains("70000001"));
    }

    @Test
    void laFirmaYLaHuellaUsanClavesDistintas() {
        assertNotEquals(Cripto.indice("texto"), Cripto.firmar("texto"));
    }

    @Test
    void compararYEnmascarar() {
        assertTrue(Cripto.iguales("abc", "abc"));
        assertFalse(Cripto.iguales("abc", "abd"));
        assertEquals("****0001", Cripto.enmascarar("70000001", 4));
        assertEquals("", Cripto.enmascarar("", 4));
    }
}
