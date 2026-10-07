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
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;

/**
 * Barrera de acceso del panel de administración: solo deja pasar a quien tenga sesión
 * de administrador, tanto en la API (/api/admin/*) como en las páginas (/paginas/admin/*).
 * Las páginas también tienen una guarda en el navegador, pero esa es solo comodidad:
 * esta es la que realmente protege cuando el sitio se sirve desde el mismo servidor.
 */
@WebFilter({"/api/admin/*", "/paginas/admin/*"})
public class FiltroAdmin implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        if (Sesion.esAdministrador(request)) {
            chain.doFilter(req, resp);
            return;
        }

        String ctx = request.getContextPath();
        boolean esApi = request.getRequestURI().startsWith(ctx + "/api/");
        if (esApi) {
            boolean sinSesion = !Sesion.hayUsuario(request);
            response.setStatus(sinSesion ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().print(Json.error(sinSesion
                    ? "Inicia sesión para continuar."
                    : "Solo el personal parroquial puede acceder a esta función."));
        } else if (Sesion.hayUsuario(request)) {
            response.sendRedirect(ctx + "/paginas/reservas.html"); // feligrés: a su zona
        } else {
            response.sendRedirect(ctx + "/paginas/login.html?next=admin/documentos.html");
        }
    }
}
