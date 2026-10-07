package pe.caminoiluminacion.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Hash de contraseñas con PBKDF2-HMAC-SHA256 y sal aleatoria (sin librerías externas).
 * Formato almacenado: pbkdf2$iteraciones$salBase64$hashBase64 (cabe en VARCHAR(255)).
 */
public final class Claves {

    private static final int ITERACIONES = 120_000;
    private static final int LONGITUD_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Claves() { }

    public static String hashear(String contrasena) {
        byte[] sal = new byte[16];
        RANDOM.nextBytes(sal);
        byte[] hash = derivar(contrasena, sal, ITERACIONES);
        return "pbkdf2$" + ITERACIONES + "$"
                + Base64.getEncoder().encodeToString(sal) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verificar(String contrasena, String almacenado) {
        if (contrasena == null || almacenado == null) return false;
        String[] p = almacenado.split("\\$");
        if (p.length != 4 || !p[0].equals("pbkdf2")) return false; // formato desconocido
        try {
            int it = Integer.parseInt(p[1]);
            byte[] sal = Base64.getDecoder().decode(p[2]);
            byte[] esperado = Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(esperado, derivar(contrasena, sal, it));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(String contrasena, byte[] sal, int iteraciones) {
        try {
            PBEKeySpec spec = new PBEKeySpec(contrasena.toCharArray(), sal, iteraciones, LONGITUD_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 no disponible", e);
        }
    }
}
