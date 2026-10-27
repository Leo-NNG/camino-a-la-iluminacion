package pe.caminoiluminacion.filtro;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.util.Json;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Protección contra CSRF (que otro sitio haga actuar al navegador de un usuario con sesión iniciada).
 * Para toda petición que CAMBIA datos (POST, DELETE…) bajo /api/ exige que venga del propio sitio:
 *  - Sec-Fetch-Site, si el navegador lo envía, debe ser same-origin (o none);
 *  - Origin, si viene, debe coincidir con el Host al que se llamó.
 * Los navegadores modernos envían Origin y Sec-Fetch-Site en todas las peticiones que cambian datos y no
 * permiten que una página ajena los falsifique. Las lecturas (GET) no se tocan.
 * Si el sitio está detrás de un proxy inverso, éste debe conservar el encabezado Host original.
 */
@WebFilter("/api/*")
public class FiltroOrigen implements Filter {

    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        if (METODOS_SEGUROS.contains(String.valueOf(request.getMethod()).toUpperCase()) || origenPermitido(request)) {
            chain.doFilter(req, resp);
            return;
        }
        HttpServletResponse response = (HttpServletResponse) resp;
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(Json.error("Solicitud rechazada por seguridad: no proviene de este sitio."));
    }

    static boolean origenPermitido(HttpServletRequest request) {
        String sitio = request.getHeader("Sec-Fetch-Site");
        if (sitio != null && !sitio.equals("same-origin") && !sitio.equals("none")) return false;

        String origen = request.getHeader("Origin");
        if (origen == null) return true; // cliente que no es un navegador (o navegador antiguo): no hay CSRF que frenar
        String host = request.getHeader("Host");
        if (host == null || origen.equals("null")) return false;
        try {
            String autoridad = URI.create(origen).getAuthority();
            return autoridad != null && autoridad.equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
