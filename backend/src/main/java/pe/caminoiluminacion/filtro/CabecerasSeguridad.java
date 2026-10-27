package pe.caminoiluminacion.filtro;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Cabeceras de seguridad en TODAS las respuestas (páginas, estilos, scripts y API):
 *  - Content-Security-Policy: el navegador solo ejecuta scripts del propio sitio y solo carga recursos de los
 *    orígenes listados (fotos de Pexels y mosaicos del mapa de OpenStreetMap); si alguien lograra inyectar un
 *    script, no se ejecutaría. Frena también el clickjacking (frame-ancestors) y los formularios hacia otros sitios.
 *  - X-Content-Type-Options: el navegador no "adivina" el tipo de un archivo.
 *  - X-Frame-Options, Referrer-Policy, Permissions-Policy y políticas de origen cruzado.
 *  - Strict-Transport-Security solo cuando la petición llegó por HTTPS (en http://localhost no aplica).
 *  - Las respuestas de /api/ no se guardan en caché del navegador.
 * Las descargas de documentos (ArchivoDocumentoServlet) fijan su propia política, más estricta, y esa prevalece.
 */
@WebFilter("/*")
public class CabecerasSeguridad implements Filter {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob: https://images.pexels.com https://*.tile.openstreetmap.org; "
            + "font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; "
            + "form-action 'self'; object-src 'none'";

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (req instanceof HttpServletRequest request && resp instanceof HttpServletResponse response) {
            aplicar(request, response);
        }
        chain.doFilter(req, resp);
    }

    static void aplicar(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "geolocation=(self), camera=(), microphone=(), payment=(), usb=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        if (esHttps(request)) response.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/api/")) response.setHeader("Cache-Control", "no-store");
    }

    private static boolean esHttps(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
