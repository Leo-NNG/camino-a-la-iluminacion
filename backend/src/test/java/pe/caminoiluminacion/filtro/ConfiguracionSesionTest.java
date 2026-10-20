package pe.caminoiluminacion.filtro;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.SessionCookieConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionSesionTest {

    private static ServletContextEvent evento(List<String> llamadas, boolean contenedorAntiguo) {
        SessionCookieConfig cookie = (SessionCookieConfig) Proxy.newProxyInstance(ConfiguracionSesionTest.class.getClassLoader(),
                new Class<?>[]{SessionCookieConfig.class}, (o, m, a) -> {
                    if (m.getName().equals("setAttribute") && contenedorAntiguo) throw new NoSuchMethodError("setAttribute");
                    if (m.getName().equals("setHttpOnly")) llamadas.add("httpOnly=" + a[0]);
                    if (m.getName().equals("setAttribute")) llamadas.add(a[0] + "=" + a[1]);
                    return null;
                });
        ServletContext ctx = (ServletContext) Proxy.newProxyInstance(ConfiguracionSesionTest.class.getClassLoader(),
                new Class<?>[]{ServletContext.class}, (o, m, a) -> m.getName().equals("getSessionCookieConfig") ? cookie : null);
        return new ServletContextEvent(ctx);
    }

    @Test
    void laCookieDeSesionQuedaHttpOnlyYSameSiteLax() {
        List<String> llamadas = new ArrayList<>();
        new ConfiguracionSesion().contextInitialized(evento(llamadas, false));
        assertTrue(llamadas.contains("httpOnly=true"));
        assertTrue(llamadas.contains("SameSite=Lax"));
    }

    @Test
    void enUnContenedorAntiguoSoloQuedaHttpOnlyYNoFallaElArranque() {
        List<String> llamadas = new ArrayList<>();
        assertDoesNotThrow(() -> new ConfiguracionSesion().contextInitialized(evento(llamadas, true)));
        assertTrue(llamadas.contains("httpOnly=true"));
        assertFalse(llamadas.contains("SameSite=Lax"));
    }
}
