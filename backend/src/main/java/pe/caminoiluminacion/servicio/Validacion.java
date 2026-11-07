package pe.caminoiluminacion.servicio;

import java.util.regex.Pattern;

/** Validaciones de datos de contacto compartidas por el registro y por "Mi cuenta". Lanzan ReglaNegocioException (400). */
final class Validacion {

    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private Validacion() { }

    /** @return el correo recortado y en minúsculas; lanza 400 si no es válido. */
    static String correo(String texto) {
        String c = limpio(texto).toLowerCase();
        if (c.length() > 120 || !CORREO.matcher(c).matches()) throw new ReglaNegocioException(400, "Ingresa un correo válido.");
        return c;
    }

    /** @return el teléfono (7 a 9 dígitos) o null si vino vacío; lanza 400 si no es válido. */
    static String telefonoOpcional(String texto) {
        String t = limpio(texto);
        if (t.isEmpty()) return null;
        if (!t.matches("[0-9]{7,9}")) throw new ReglaNegocioException(400, "El teléfono debe tener de 7 a 9 dígitos.");
        return t;
    }

    /** Contraseña de 6 a 100 caracteres. */
    static void contrasena(String texto) {
        if (texto == null || texto.length() < 6 || texto.length() > 100) {
            throw new ReglaNegocioException(400, "La contraseña debe tener entre 6 y 100 caracteres.");
        }
    }

    static String limpio(String t) {
        return t == null ? "" : t.trim();
    }
}
