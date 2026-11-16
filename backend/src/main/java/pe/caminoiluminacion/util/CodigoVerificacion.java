package pe.caminoiluminacion.util;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Códigos que se imprimen en las constancias de reserva (CON) y en los recibos de pago (REC) para poder comprobar su
 * autenticidad en la página pública de verificación. Forma: {@code CON-7K3M9-PQ2XD}.
 *
 * Son aleatorios (SecureRandom), no se derivan de ningún dato de la persona ni de un contador, y el alfabeto omite los
 * caracteres que se confunden al leerlos (I, O, 0, 1). Con 10 caracteres de 32 posibles hay unos 10^15 códigos: adivinar uno
 * no es práctico, y además la verificación limita los intentos fallidos.
 */
public final class CodigoVerificacion {

    public static final String CONSTANCIA = "CON";
    public static final String RECIBO = "REC";

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom AZAR = new SecureRandom();
    private static final Pattern FORMA = Pattern.compile("^(CON|REC)-[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}$");

    private CodigoVerificacion() { }

    /** Un código nuevo con el prefijo indicado ({@link #CONSTANCIA} o {@link #RECIBO}). */
    public static String nuevo(String prefijo) {
        if (!CONSTANCIA.equals(prefijo) && !RECIBO.equals(prefijo)) throw new IllegalArgumentException("Prefijo no válido: " + prefijo);
        StringBuilder sb = new StringBuilder(prefijo).append('-');
        for (int i = 0; i < 10; i++) {
            if (i == 5) sb.append('-');
            sb.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /**
     * Lleva lo que escribió la persona a la forma canónica (mayúsculas, con guiones) o devuelve null si no puede ser un código.
     * Acepta espacios, guiones de más o de menos ({@code con 7k3m9 pq2xd}, {@code CON7K3M9PQ2XD}).
     */
    public static String normalizar(String entrada) {
        if (entrada == null) return null;
        String limpio = entrada.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (limpio.length() != 13) return null;
        String canonico = limpio.substring(0, 3) + "-" + limpio.substring(3, 8) + "-" + limpio.substring(8);
        return FORMA.matcher(canonico).matches() ? canonico : null;
    }

    /** {@link #CONSTANCIA} o {@link #RECIBO} según el prefijo de un código ya normalizado. */
    public static String prefijo(String codigoNormalizado) {
        return codigoNormalizado.substring(0, 3);
    }
}
