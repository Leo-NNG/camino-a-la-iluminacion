package pe.caminoiluminacion.controlador;

import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas.Contenedor;
import pe.caminoiluminacion.controlador.Pruebas.Resp;
import pe.caminoiluminacion.servicio.ConsultaDniService;
import pe.caminoiluminacion.servicio.ConsultaDniService.Respuesta;
import pe.caminoiluminacion.util.LimiteIntentos;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static pe.caminoiluminacion.controlador.Pruebas.cuerpo;
import static pe.caminoiluminacion.controlador.Pruebas.m;

/** Servlet de consulta de DNI: JSON de éxito, fallback manual y disponibilidad (cliente HTTP falso). */
class ConsultaDniServletTest {

    final AtomicReference<String> token = new AtomicReference<>("t");
    final AtomicReference<Respuesta> respuesta = new AtomicReference<>(new Respuesta(200,
            "{\"success\":true,\"data\":{\"nombres\":\"ROSA \\\"LA\\\" <b>\",\"apellido_paterno\":\"PAZ\",\"apellido_materno\":\"SOTO\"}}"));
    final ConsultaDniServlet servlet = new ConsultaDniServlet(new ConsultaDniService(
            (uri, t, espera) -> respuesta.get(), token::get, new LimiteIntentos(10, 600_000, 600_000, System::currentTimeMillis)));

    private Resp post(Map<String, String> p) throws Exception {
        Resp r = new Resp();
        servlet.doPost(Pruebas.req(new Contenedor(), p, "/"), Pruebas.resp(r));
        return r;
    }

    private Resp get() throws Exception {
        Resp r = new Resp();
        servlet.doGet(Pruebas.req(new Contenedor(), m(), "/"), Pruebas.resp(r));
        return r;
    }

    @Test
    void informaSiLaConsultaEstaDisponible() throws Exception {
        assertEquals("{\"disponible\":true}", cuerpo(get()));
        token.set(null);
        assertEquals("{\"disponible\":false}", cuerpo(get()));
    }

    @Test
    void devuelveLosNombresEscapadosSinPedirSesion() throws Exception {
        Resp r = post(m("dni", "12345678"));
        assertEquals(200, r.status);
        assertEquals("{\"exito\":true,\"nombres\":\"ROSA \\\"LA\\\" <b>\",\"apellidos\":\"PAZ SOTO\"}", cuerpo(r));
    }

    @Test
    void losErroresSiempreIndicanQueSePuedeSeguirAMano() throws Exception {
        Resp invalido = post(m("dni", "123"));
        assertEquals(400, invalido.status);
        assertTrue(cuerpo(invalido).contains("\"manual\":true"));

        respuesta.set(new Respuesta(503, ""));
        Resp caido = post(m("dni", "12345678"));
        assertEquals(502, caido.status);
        assertTrue(cuerpo(caido).startsWith("{\"exito\":false,\"error\":\"No pudimos consultar"), cuerpo(caido));
        assertTrue(cuerpo(caido).endsWith("\"manual\":true}"));

        token.set("");
        Resp sinToken = post(m("dni", "12345678"));
        assertEquals(503, sinToken.status);
        assertTrue(cuerpo(sinToken).contains("\"manual\":true"));
    }
}
