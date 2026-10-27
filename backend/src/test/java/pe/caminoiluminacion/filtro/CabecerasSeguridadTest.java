package pe.caminoiluminacion.filtro;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CabecerasSeguridadTest {

    private static HttpServletRequest peticion(String uri, boolean https, String forwardedProto) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
                    case "getRequestURI" -> uri;
                    case "isSecure" -> https;
                    case "getHeader" -> "X-Forwarded-Proto".equalsIgnoreCase(String.valueOf(a[0])) ? forwardedProto : null;
                    default -> null;
                });
    }

    private static HttpServletResponse respuesta(Map<String, String> cabeceras) {
        return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (p, m, a) -> {
                    if (m.getName().equals("setHeader")) cabeceras.put((String) a[0], (String) a[1]);
                    return null;
                });
    }

    private static Map<String, String> aplicar(String uri, boolean https, String forwardedProto) {
        Map<String, String> h = new HashMap<>();
        CabecerasSeguridad.aplicar(peticion(uri, https, forwardedProto), respuesta(h));
        return h;
    }

    @Test
    void laPoliticaSoloPermiteScriptsDelPropioSitio() {
        String csp = aplicar("/index.html", false, null).get("Content-Security-Policy");
        assertTrue(csp.contains("script-src 'self';"), csp);
        assertFalse(csp.matches(".*script-src[^;]*unsafe-inline.*"), "no debe permitir scripts inline");
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("object-src 'none'"));
        assertTrue(csp.contains("form-action 'self'"));
    }

    @Test
    void poneLasCabecerasBasicas() {
        Map<String, String> h = aplicar("/index.html", false, null);
        assertEquals("nosniff", h.get("X-Content-Type-Options"));
        assertEquals("DENY", h.get("X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", h.get("Referrer-Policy"));
        assertNotNull(h.get("Permissions-Policy"));
        assertEquals("same-origin", h.get("Cross-Origin-Opener-Policy"));
    }

    @Test
    void hstsSoloSePoneConHttps() {
        assertNull(aplicar("/index.html", false, null).get("Strict-Transport-Security"));
        assertNotNull(aplicar("/index.html", true, null).get("Strict-Transport-Security"));
        assertNotNull(aplicar("/index.html", false, "https").get("Strict-Transport-Security"));
        assertNull(aplicar("/index.html", false, "http").get("Strict-Transport-Security"));
    }

    @Test
    void laApiNoSeGuardaEnCache() {
        assertEquals("no-store", aplicar("/camino/api/sesion", false, null).get("Cache-Control"));
        assertNull(aplicar("/paginas/login.html", false, null).get("Cache-Control"));
    }

    @Test
    void elFiltroDejaContinuarLaPeticion() throws Exception {
        AtomicBoolean siguio = new AtomicBoolean();
        Map<String, String> h = new HashMap<>();
        new CabecerasSeguridad().doFilter(peticion("/index.html", false, null), respuesta(h), (rq, rs) -> siguio.set(true));
        assertTrue(siguio.get());
        assertNotNull(h.get("Content-Security-Policy"));
    }
}
