package pe.caminoiluminacion.modelo;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * Intención de misa: el feligrés pide que una misa de una fecha se ofrezca por alguien o algo y deja una ofrenda
 * (un {@link Pago}, enlazado por {@code idPago}). {@code horaMisa} es copia del texto del horario ("7:00 a.m. y 7:00 p.m.").
 */
public record Intencion(Integer idIntencion, int idUsuario, int idParroquia, Integer idHorario, String horaMisa,
                        Date fechaMisa, String tipo, String texto, int idPago, Timestamp creadaEn) {

    public static final String DIFUNTO = "difunto";
    public static final String ACCION_GRACIAS = "accion_gracias";
    public static final String SALUD = "salud";
    public static final String OTRA = "otra";

    /** Cómo se lee la intención en la misa: "Por el eterno descanso de" + texto. */
    public static String etiqueta(String tipo) {
        return switch (tipo == null ? "" : tipo) {
            case DIFUNTO -> "Por el eterno descanso de";
            case ACCION_GRACIAS -> "En acción de gracias por";
            case SALUD -> "Por la salud de";
            default -> "Por la intención de";
        };
    }
}
