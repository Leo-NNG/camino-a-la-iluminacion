package pe.caminoiluminacion.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LimiteIntentosTest {

    private long ahora;
    private LimiteIntentos limite;

    @BeforeEach
    void preparar() {
        ahora = 0;
        limite = new LimiteIntentos(3, 60_000, 120_000, () -> ahora);
    }

    @Test
    void bloqueaAlLlegarAlMaximoDeFallos() {
        limite.registrarFallo("a");
        limite.registrarFallo("a");
        assertFalse(limite.estaBloqueado("a"));
        limite.registrarFallo("a");
        assertTrue(limite.estaBloqueado("a"));
    }

    @Test
    void elBloqueoSeLevantaConElTiempo() {
        for (int i = 0; i < 3; i++) limite.registrarFallo("a");
        ahora = 119_000;
        assertTrue(limite.estaBloqueado("a"));
        ahora = 121_000;
        assertFalse(limite.estaBloqueado("a"));
    }

    @Test
    void cadaClaveEsIndependiente() {
        for (int i = 0; i < 3; i++) limite.registrarFallo("a");
        assertFalse(limite.estaBloqueado("b"));
    }

    @Test
    void unIngresoCorrectoReiniciaLaCuenta() {
        limite.registrarFallo("a");
        limite.registrarFallo("a");
        limite.reiniciar("a");
        limite.registrarFallo("a");
        limite.registrarFallo("a");
        assertFalse(limite.estaBloqueado("a"));
    }

    @Test
    void losFallosFueraDeLaVentanaNoSeAcumulan() {
        limite.registrarFallo("a");
        limite.registrarFallo("a");
        ahora = 61_000;
        limite.registrarFallo("a");
        assertFalse(limite.estaBloqueado("a"));
    }
}
