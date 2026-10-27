package pe.caminoiluminacion.util;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class RegistroTest {

    @Test
    void logbackEsElRegistroActivoYNoElLoggerNulo() {
        assertEquals("ch.qos.logback.classic.LoggerContext", LoggerFactory.getILoggerFactory().getClass().getName(),
                "SLF4J no está enlazado a Logback (revisa la versión de slf4j-api en el pom)");
    }

    @Test
    void registrarNoLanzaExcepciones() {
        assertDoesNotThrow(() -> {
            Registro.info("prueba de info");
            Registro.aviso("prueba de aviso");
            Registro.error("prueba de error", new RuntimeException("simulado"));
        });
    }
}