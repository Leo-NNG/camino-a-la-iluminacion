package pe.caminoiluminacion.seguridad;

import pe.caminoiluminacion.util.Registro;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Cifrado de datos sensibles (AES-256-GCM), índices ciegos (HMAC-SHA256) y firmas. La clave nunca vive en el repositorio. */
public final class Cripto {
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final SecretKeySpec K_CIFRADO, K_INDICE, K_FIRMA;

    static {
        try {
            byte[] maestra = clave();
            K_CIFRADO = new SecretKeySpec(derivar(maestra, "cifrado"), "AES");
            K_INDICE = new SecretKeySpec(derivar(maestra, "indice"), "HmacSHA256");
            K_FIRMA = new SecretKeySpec(derivar(maestra, "firma"), "HmacSHA256");
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private Cripto() {}

    private static byte[] derivar(byte[] maestra, String etiqueta) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(maestra, "HmacSHA256"));
        return mac.doFinal(etiqueta.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Origen de la clave, en este orden: propiedad del sistema cai.data.key (la usan las pruebas), variable de
     * entorno CAI_DATA_KEY (base64, 32 bytes; recomendada en el despliegue) o, solo en desarrollo, un archivo
     * local .data-key que se genera solo y está ignorado por git.
     */
    private static byte[] clave() throws IOException {
        String env = System.getProperty("cai.data.key");
        if (env == null || env.isBlank()) env = System.getenv("CAI_DATA_KEY");
        if (env != null && !env.isBlank()) {
            byte[] k = Base64.getDecoder().decode(env.trim());
            if (k.length < 32) throw new IllegalStateException("La clave de cifrado (CAI_DATA_KEY) debe tener 32 bytes en base64.");
            return k;
        }
        String ruta = System.getenv("CAI_DATA_KEY_FILE");
        Path f = Paths.get(ruta == null || ruta.isBlank() ? ".data-key" : ruta).toAbsolutePath();
        if (Files.exists(f)) return Base64.getDecoder().decode(Files.readString(f).trim());
        byte[] k = new byte[32];
        ALEATORIO.nextBytes(k);
        Files.writeString(f, Base64.getEncoder().encodeToString(k));
        try { Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-------")); } catch (Exception e) { /* Windows */ }
        Registro.aviso("Se generó la clave de cifrado en " + f + ". Respáldala: sin ella no se pueden leer los datos cifrados. En el despliegue usa la variable CAI_DATA_KEY.");
        return k;
    }

    public static String cifrar(String texto) {
        if (texto == null) return null;
        try {
            byte[] iv = new byte[12];
            ALEATORIO.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, K_CIFRADO, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] todo = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, todo, 0, iv.length);
            System.arraycopy(ct, 0, todo, iv.length, ct.length);
            return "v1:" + Base64.getEncoder().encodeToString(todo);
        } catch (Exception e) { throw new IllegalStateException("No se pudo cifrar.", e); }
    }

    /** Descifra valores "v1:"; lo que no esté cifrado se devuelve tal cual (datos previos a la migración). */
    public static String descifrar(String valor) {
        if (valor == null || !valor.startsWith("v1:")) return valor;
        try {
            byte[] todo = Base64.getDecoder().decode(valor.substring(3));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, K_CIFRADO, new GCMParameterSpec(128, todo, 0, 12));
            return new String(c.doFinal(todo, 12, todo.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    /** Huella determinista para buscar por DNI sin guardarlo en claro. */
    public static String indice(String texto) { return hmac(K_INDICE, texto); }

    public static String firmar(String texto) { return hmac(K_FIRMA, texto); }

    public static boolean iguales(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static String enmascarar(String texto, int visibles) {
        if (texto == null || texto.isEmpty()) return "";
        int n = Math.min(visibles, texto.length());
        return "*".repeat(texto.length() - n) + texto.substring(texto.length() - n);
    }

    private static String hmac(SecretKeySpec k, String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(k);
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
