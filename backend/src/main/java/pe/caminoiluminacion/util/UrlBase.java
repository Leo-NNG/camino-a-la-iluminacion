package pe.caminoiluminacion.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Dirección pública del sitio, para imprimir en los documentos dónde se verifican. Si existe la variable de entorno
 * {@code CAI_URL_PUBLICA} (por ejemplo https://caminoailuminacion.pe) se usa esa; si no, se arma con la petición actual.
 */
public final class UrlBase {

    private UrlBase() { }

    public static String desde(HttpServletRequest request) {
        String fija = System.getenv("CAI_URL_PUBLICA");
        if (fija != null && !fija.isBlank()) return fija.trim().replaceAll("/+$", "");
        String esquema = request.getScheme();
        int puerto = request.getServerPort();
        boolean estandar = ("http".equals(esquema) && puerto == 80) || ("https".equals(esquema) && puerto == 443);
        return esquema + "://" + request.getServerName() + (estandar ? "" : ":" + puerto) + request.getContextPath();
    }

    /** Dirección de la página de verificación, con el código ya puesto. */
    public static String verificacion(HttpServletRequest request, String codigo) {
        return desde(request) + "/paginas/verificar.html?codigo=" + codigo;
    }
}
