package pe.caminoiluminacion.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTest {

    @Test
    void escapaComillasBarrasYSaltos() {
        assertEquals("Ana \\\"la\\\" \\\\ rara\\nlinea\\ttab", Json.esc("Ana \"la\" \\ rara\nlinea\ttab"));
    }

    @Test
    void escapaCaracteresDeControl() {
        assertEquals("a\\u0001b", Json.esc("a\u0001b"));
    }

    @Test
    void nuloSeConvierteEnVacio() {
        assertEquals("", Json.esc(null));
    }

    @Test
    void noAlteraTildesNiEnies() {
        assertEquals("Señora del Carmen — 7:00 p.m.", Json.esc("Señora del Carmen — 7:00 p.m."));
    }

    @Test
    void fechaRecortaTimestampsYToleraNulos() {
        assertEquals("2026-10-09", Json.fecha("2026-10-09 07:30:00.0"));
        assertEquals("2026-10-09", Json.fecha(java.sql.Date.valueOf("2026-10-09")));
        assertEquals("", Json.fecha(null));
    }

    @Test
    void errorGeneraUnJsonEscapado() {
        assertEquals("{\"exito\":false,\"error\":\"dijo \\\"no\\\"\"}", Json.error("dijo \"no\""));
    }
}
