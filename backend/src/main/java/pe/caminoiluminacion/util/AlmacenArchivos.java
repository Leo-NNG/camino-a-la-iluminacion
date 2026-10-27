package pe.caminoiluminacion.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Guarda los documentos de los feligreses FUERA de la carpeta pública de la aplicación.
 * - El tipo se decide por el CONTENIDO (firma del archivo), nunca por la extensión ni por lo que declare el navegador.
 * - El nombre en disco lo genera el servidor (aleatorio): el nombre que envía el usuario no se usa como ruta.
 * - Solo se abren archivos cuyo nombre tenga ese formato y que estén dentro del directorio base.
 * Directorio: variable de entorno DOCS_DIR, o ~/camino_iluminacion_docs por defecto.
 */
public final class AlmacenArchivos {

    public static final int MAX_BYTES = 5 * 1024 * 1024; // 5 MB (RF05)

    public enum Tipo {
        PDF("application/pdf", "pdf"),
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png");

        public final String mime;
        public final String extension;

        Tipo(String mime, String extension) {
            this.mime = mime;
            this.extension = extension;
        }

        public static Tipo porMime(String mime) {
            for (Tipo t : values()) if (t.mime.equals(mime)) return t;
            return null;
        }
    }

    private static final Pattern NOMBRE_ALMACENADO = Pattern.compile("^[a-f0-9]{32}\\.(pdf|jpg|png)$");

    private final Path base;

    public AlmacenArchivos(Path base) {
        this.base = base.toAbsolutePath().normalize();
    }

    public static AlmacenArchivos porDefecto() {
        String dir = System.getenv("DOCS_DIR");
        Path base = (dir == null || dir.isBlank())
                ? Paths.get(System.getProperty("user.home"), "camino_iluminacion_docs")
                : Paths.get(dir);
        return new AlmacenArchivos(base);
    }

    /** @return el tipo según la firma del archivo, o null si no es PDF, JPEG ni PNG. */
    public static Tipo detectar(byte[] b) {
        if (b == null) return null;
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return Tipo.PDF;
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Tipo.JPEG;
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return Tipo.PNG;
        return null;
    }

    /**
     * Un PDF puede llevar JavaScript, acciones de lanzamiento o archivos incrustados; en un trámite parroquial
     * no hacen falta y son un riesgo para quien lo abra. Se rechazan los PDF que los declaren.
     * (Los marcadores se buscan en el texto crudo del archivo: es una barrera básica, no un antivirus.)
     */
    public static boolean tieneContenidoActivo(byte[] b) {
        if (b == null) return false;
        String t = new String(b, java.nio.charset.StandardCharsets.ISO_8859_1);
        // No se busca "/JS" a secas: son 3 bytes y aparecerían por casualidad dentro de los datos comprimidos de
        // un PDF grande (falso positivo). Toda acción de JavaScript declara además "/JavaScript".
        return t.contains("/JavaScript") || t.contains("/Launch") || t.contains("/EmbeddedFile");
    }

    /** @return el nombre generado (lo que se guarda en la base de datos como ruta_archivo). */
    public String guardar(byte[] datos, Tipo tipo) throws IOException {
        Files.createDirectories(base);
        String nombre = UUID.randomUUID().toString().replace("-", "") + "." + tipo.extension;
        Files.write(base.resolve(nombre), datos, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return nombre;
    }

    /** @return la ruta del archivo si el nombre es válido, está dentro del directorio base y existe; si no, null. */
    public Path resolver(String nombre) {
        if (nombre == null || !NOMBRE_ALMACENADO.matcher(nombre).matches()) return null;
        Path p = base.resolve(nombre).normalize();
        return (p.startsWith(base) && Files.isRegularFile(p)) ? p : null;
    }

    public void borrar(String nombre) {
        Path p = resolver(nombre);
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            // Si no se puede borrar, queda un archivo huérfano pero sin ninguna referencia: no afecta a nadie.
        }
    }
}
