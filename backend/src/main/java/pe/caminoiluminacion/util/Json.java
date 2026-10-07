package pe.caminoiluminacion.util;

/** Utilidades mínimas para armar JSON sin dependencias externas. */
public final class Json {

    private Json() { }

    /** Escapa un texto para incluirlo entre comillas dentro de un JSON. */
    public static String esc(Object valor) {
        if (valor == null) return "";
        String s = valor.toString();
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Fecha (yyyy-MM-dd) de un java.sql.Date o Timestamp; vacío si es null. */
    public static String fecha(Object valor) {
        String s = valor == null ? "" : valor.toString();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    public static String error(String mensaje) {
        return "{\"exito\":false,\"error\":\"" + esc(mensaje) + "\"}";
    }
}
