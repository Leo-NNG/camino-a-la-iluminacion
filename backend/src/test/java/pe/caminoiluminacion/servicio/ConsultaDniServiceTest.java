package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.servicio.ConsultaDniService.DatosDni;
import pe.caminoiluminacion.servicio.ConsultaDniService.Respuesta;
import pe.caminoiluminacion.util.LimiteIntentos;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Consulta de DNI con un cliente HTTP falso: ninguna prueba sale a internet. */
class ConsultaDniServiceTest {

    static final String OK = "{\"success\":true,\"message\":\"Exito\",\"data\":{\"numero\":\"12345678\",\"nombres\":\"MARIA JOSE\","
            + "\"apellido_paterno\":\"FERNANDEZ\",\"apellido_materno\":\"NUÑEZ\",\"nombre_completo\":\"FERNANDEZ NUÑEZ, MARIA JOSE\"}}";

    /** Cliente falso: responde lo programado y recuerda cada llamada. */
    static class FakeHttp implements ConsultaDniService.ClienteHttp {
        Respuesta respuesta = new Respuesta(200, OK);
        IOException falla;
        final List<URI> uris = new ArrayList<>();
        final List<String> tokens = new ArrayList<>();
        final List<Duration> esperas = new ArrayList<>();

        public Respuesta get(URI uri, String token, Duration espera) throws IOException {
            uris.add(uri); tokens.add(token); esperas.add(espera);
            if (falla != null) throw falla;
            return respuesta;
        }
    }

    FakeHttp http;
    String token;
    AtomicLong reloj;
    ConsultaDniService servicio;

    @BeforeEach
    void preparar() {
        http = new FakeHttp();
        token = "token-de-prueba";
        reloj = new AtomicLong(1_000_000);
        servicio = new ConsultaDniService(http, () -> token, new LimiteIntentos(3, 600_000, 600_000, reloj::get), "https://falso.test/dni/");
    }

    private int estado(Runnable r) {
        return assertThrows(ReglaNegocioException.class, r::run).getEstado();
    }

    @Test
    void devuelveNombresYApellidosYUsaElTokenYUnTiempoDeEsperaCorto() {
        DatosDni d = servicio.consultar(" 12345678 ", "1.1.1.1");
        assertEquals("MARIA JOSE", d.nombres());
        assertEquals("FERNANDEZ NUÑEZ", d.apellidos());
        assertEquals(URI.create("https://falso.test/dni/12345678"), http.uris.get(0));
        assertEquals("token-de-prueba", http.tokens.get(0));
        assertTrue(http.esperas.get(0).compareTo(Duration.ofSeconds(5)) <= 0, "timeout corto");
    }

    @Test
    void rechazaDniInvalidoSinLlamarAFactiliza() {
        for (String malo : new String[]{null, "", "1234567", "123456789", "1234567a", "../../x", "12345678?x=1"}) {
            assertEquals(400, estado(() -> servicio.consultar(malo, "ip")), String.valueOf(malo));
        }
        assertTrue(http.uris.isEmpty());
    }

    @Test
    void sinTokenNoEstaDisponibleYNoLlamaANadie() {
        for (String t : new String[]{null, "", "   "}) {
            token = t;
            assertFalse(servicio.disponible());
            assertEquals(503, estado(() -> servicio.consultar("12345678", "ip")));
        }
        assertTrue(http.uris.isEmpty());
        token = "x";
        assertTrue(servicio.disponible());
    }

    @Test
    void limitaLasConsultasPorIpYLaOtraIpSigueLibre() {
        for (int i = 0; i < 3; i++) servicio.consultar("12345678", "9.9.9.9");
        assertEquals(429, estado(() -> servicio.consultar("12345678", "9.9.9.9")));
        assertEquals(3, http.uris.size(), "bloqueado: ya no llama a Factiliza");
        assertEquals("MARIA JOSE", servicio.consultar("12345678", "8.8.8.8").nombres());
        reloj.addAndGet(600_001 + 600_001);
        assertEquals("MARIA JOSE", servicio.consultar("12345678", "9.9.9.9").nombres(), "pasado el bloqueo vuelve a funcionar");
    }

    @Test
    void lasConsultasSinDatosTambienCuentanParaElLimite() {
        http.respuesta = new Respuesta(404, "{\"success\":false}");
        for (int i = 0; i < 3; i++) assertEquals(404, estado(() -> servicio.consultar("12345678", "7.7.7.7")));
        assertEquals(429, estado(() -> servicio.consultar("87654321", "7.7.7.7")));
    }

    @Test
    void erroresDelProveedorSeConviertenEnMensajesQuePermitenSeguirAMano() {
        http.respuesta = new Respuesta(401, "{}");
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.consultar("12345678", "a"));
        assertEquals(502, e.getEstado());
        assertTrue(e.getMessage().contains("Escribe tus nombres"), e.getMessage());
        assertFalse(e.getMessage().contains("token"), "no revela detalles internos al feligrés");
        http.respuesta = new Respuesta(500, "error");
        assertEquals(502, estado(() -> servicio.consultar("12345678", "b")));
        http.respuesta = new Respuesta(200, "<html>no es json</html>");
        assertEquals(502, estado(() -> servicio.consultar("12345678", "c")));
        http.respuesta = new Respuesta(200, "{\"success\":false,\"message\":\"No encontrado\"}");
        assertEquals(404, estado(() -> servicio.consultar("12345678", "d")));
        http.respuesta = new Respuesta(200, "{\"success\":true,\"data\":{\"nombres\":\"ANA\",\"apellido_paterno\":null,\"apellido_materno\":\"\"}}");
        assertEquals(404, estado(() -> servicio.consultar("12345678", "e")));
        http.respuesta = new Respuesta(200, "[]");
        assertEquals(404, estado(() -> servicio.consultar("12345678", "f")));
    }

    @Test
    void siFactilizaNoRespondeATiempoSePuedeSeguirAMano() {
        http.falla = new HttpTimeoutException("request timed out");
        assertEquals(502, estado(() -> servicio.consultar("12345678", "ip")));
    }

    @Test
    void unApellidoFaltanteNoImpideCompletarYSeRecortanEspaciosYLargo() {
        DatosDni d = ConsultaDniService.leer("{\"success\":true,\"data\":{\"nombres\":\"  JOSE   LUIS \",\"apellido_paterno\":\"QUISPE\",\"apellido_materno\":\"\"}}");
        assertEquals("JOSE LUIS", d.nombres());
        assertEquals("QUISPE", d.apellidos());
        DatosDni largo = ConsultaDniService.leer("{\"success\":true,\"data\":{\"nombres\":\"" + "A".repeat(200) + "\",\"apellido_paterno\":\"B\"}}");
        assertEquals(80, largo.nombres().length());
    }
}
