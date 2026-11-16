package pe.caminoiluminacion.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CodigoVerificacionTest {

    @Test
    void generaCodigosConLaFormaPrefijoCincoCinco() {
        for (String prefijo : new String[]{CodigoVerificacion.CONSTANCIA, CodigoVerificacion.RECIBO}) {
            String c = CodigoVerificacion.nuevo(prefijo);
            assertTrue(c.matches("^" + prefijo + "-[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}$"), c);
            assertEquals(15, c.length(), "cabe en VARCHAR(20)");
            assertEquals(prefijo, CodigoVerificacion.prefijo(c));
        }
    }

    @Test
    void noUsaCaracteresQueSeConfundenAlLeer() {
        for (int i = 0; i < 2000; i++) {
            String sinPrefijo = CodigoVerificacion.nuevo("REC").substring(4);
            assertFalse(sinPrefijo.matches(".*[IO01].*"), sinPrefijo);
        }
    }

    @Test
    void losCodigosNoSeRepitenEnLaPractica() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 20_000; i++) assertTrue(vistos.add(CodigoVerificacion.nuevo("CON")), "colisión inesperada");
    }

    @Test
    void soloAceptaLosDosPrefijos() {
        assertThrows(IllegalArgumentException.class, () -> CodigoVerificacion.nuevo("XYZ"));
        assertThrows(IllegalArgumentException.class, () -> CodigoVerificacion.nuevo(null));
    }

    @Test
    void normalizaLoQueEscribeLaPersona() {
        assertEquals("CON-7K3M9-PQ2XD", CodigoVerificacion.normalizar("CON-7K3M9-PQ2XD"));
        assertEquals("CON-7K3M9-PQ2XD", CodigoVerificacion.normalizar("  con-7k3m9-pq2xd "));
        assertEquals("CON-7K3M9-PQ2XD", CodigoVerificacion.normalizar("CON7K3M9PQ2XD"));
        assertEquals("REC-7K3M9-PQ2XD", CodigoVerificacion.normalizar("rec 7k3m9 pq2xd"));
        assertEquals("REC-7K3M9-PQ2XD", CodigoVerificacion.normalizar("REC--7K3M9--PQ2XD"));
    }

    @Test
    void rechazaLoQueNoPuedeSerUnCodigo() {
        for (String malo : new String[]{null, "", "   ", "CON-7K3M9", "CON-7K3M9-PQ2XD1", "XYZ-7K3M9-PQ2XD", "CON-7K3M0-PQ2XD", "CON-7K3MI-PQ2XD",
                "CON-7K3M1-PQ2XO", "'; DROP TABLE reserva;--", "CON-7K3M9-PQ2X%", "<script>alert(1)</script>"}) {
            assertNull(CodigoVerificacion.normalizar(malo), malo);
        }
    }
}
