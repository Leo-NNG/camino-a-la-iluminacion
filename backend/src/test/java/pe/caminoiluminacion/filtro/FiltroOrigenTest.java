package pe.caminoiluminacion.filtro;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FiltroOrigenTest {

    private static HttpServletRequest peticion(String metodo, Map<String, String> encabezados) {
        return (HttpServletRequest) Proxy.newProxyInstance(FiltroOrigenTest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class}, (o, m, a) -> {
            if (m.getName().equals("getMethod")) return metodo;
            if (m.getName().equals("getHeader")) return encabezados.get(a[0]);
            return null;
        });
    }

    private static Map<String, String> h(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static final class Resultado { int estado = 200; boolean paso; StringWriter cuerpo = new StringWriter(); }

    private static Resultado ejecutar(String metodo, Map<String, String> encabezados) throws Exception {
        Resultado r = new Resultado();
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(FiltroOrigenTest.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class}, (o, m, a) -> {
            switch (m.getName()) {
                case "setStatus": r.estado = (Integer) a[0]; return null;
                case "getWriter": return new PrintWriter(r.cuerpo, true);
                default: return null;
            }
        });
        FilterChain cadena = (req, res) -> r.paso = true;
        new FiltroOrigen().doFilter(peticion(metodo, encabezados), resp, cadena);
        return r;
    }

    @Test
    void permiteUnPostDelMismoOrigen() throws Exception {
        Resultado r = ejecutar("POST", h("Origin", "http://localhost:8080", "Host", "localhost:8080", "Sec-Fetch-Site", "same-origin"));
        assertTrue(r.paso);
    }

    @Test
    void bloqueaUnPostDeOtroSitio() throws Exception {
        Resultado r = ejecutar("POST", h("Origin", "https://malo.example", "Host", "localhost:8080", "Sec-Fetch-Site", "cross-site"));
        assertFalse(r.paso);
        assertEquals(403, r.estado);
        assertTrue(r.cuerpo.toString().contains("\"exito\":false"));
    }

    @Test
    void bloqueaPorOrigenAunqueNoHayaSecFetchSite() throws Exception {
        assertFalse(ejecutar("POST", h("Origin", "https://malo.example", "Host", "localhost:8080")).paso);
        assertFalse(ejecutar("POST", h("Origin", "http://localhost:9999", "Host", "localhost:8080")).paso, "otro puerto es otro origen");
        assertFalse(ejecutar("POST", h("Origin", "http://localhost:8080.malo.example", "Host", "localhost:8080")).paso);
    }

    @Test
    void bloqueaOrigenNuloYSameSite() throws Exception {
        assertFalse(ejecutar("POST", h("Origin", "null", "Host", "localhost:8080")).paso);
        assertFalse(ejecutar("POST", h("Sec-Fetch-Site", "same-site", "Host", "localhost:8080")).paso);
    }

    @Test
    void bloqueaOrigenMalFormadoOSinHost() throws Exception {
        assertFalse(ejecutar("POST", h("Origin", "ht!tp://%%", "Host", "localhost:8080")).paso);
        assertFalse(ejecutar("POST", h("Origin", "http://localhost:8080")).paso);
    }

    @Test
    void compararElHostNoDistingueMayusculas() throws Exception {
        assertTrue(ejecutar("POST", h("Origin", "http://LOCALHOST:8080", "Host", "localhost:8080")).paso);
    }

    @Test
    void sinEncabezadosDeNavegadorSePermite() throws Exception {
        assertTrue(ejecutar("POST", h("Host", "localhost:8080")).paso, "un cliente que no es navegador no puede ser víctima de CSRF");
        assertTrue(ejecutar("POST", h("Sec-Fetch-Site", "none", "Host", "localhost:8080")).paso, "el usuario escribió la URL");
    }

    @Test
    void protegeTodosLosMetodosQueCambianDatos() throws Exception {
        for (String metodo : new String[]{"POST", "PUT", "DELETE", "PATCH", "post"}) {
            assertFalse(ejecutar(metodo, h("Origin", "https://malo.example", "Host", "localhost:8080")).paso, metodo);
        }
    }

    @Test
    void lasLecturasNoSeTocan() throws Exception {
        for (String metodo : new String[]{"GET", "HEAD", "OPTIONS"}) {
            Resultado r = ejecutar(metodo, h("Origin", "https://malo.example", "Host", "localhost:8080", "Sec-Fetch-Site", "cross-site"));
            assertTrue(r.paso, metodo);
        }
    }
}
