package pe.caminoiluminacion.util;

import pe.caminoiluminacion.servicio.PagoService.Recibo;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera el recibo de un pago aprobado como un PDF de una página (A4), sin librerías externas.
 *
 * Es un escritor de PDF 1.4 mínimo: fuente estándar Helvetica (no se incrusta nada), codificación WinAnsi y un solo
 * flujo de contenido. Todo texto que viene del usuario se "sanea" antes de escribirse: los caracteres que la
 * fuente no puede dibujar pasan a "?" y los paréntesis y la barra invertida se escapan, de modo que un nombre con
 * símbolos raros no puede romper el archivo ni inyectar operadores PDF.
 */
public final class ReciboPdf {

    /** Textos ya formateados que aparecen en el recibo. */
    public record Datos(String numero, String parroquia, String direccion, String fechaPago, String fechaVerificacion,
                        String feligres, String concepto, String metodo, String operacion, String monto, String generadoEn) { }

    private static final double ANCHO = 595, ALTO = 842, MARGEN = 50, COL_VALOR = 200, ANCHO_VALOR = 345;
    private static final int MAX_LINEAS_POR_CAMPO = 3;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Anchos de Helvetica (unidades por 1000 em) para los caracteres 32..126; el resto se aproxima con 556.
    private static final int[] ANCHOS = {
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584};

    private ReciboPdf() { }

    /** Arma el recibo a partir de un pago aprobado. {@code reloj} fija la fecha de emisión (las pruebas usan uno fijo). */
    public static byte[] generar(Recibo r, Clock reloj) {
        var p = r.pago();
        return generar(new Datos(r.numero(), r.parroquia(), r.direccion(), fecha(p.creadoEn()), fecha(p.revisadoEn()),
                r.feligres(), r.detalle(), "yape".equals(p.metodo()) ? "Yape" : "Plin", p.numeroOperacion(), soles(p.monto()),
                FECHA_HORA.format(reloj.instant().atZone(LIMA))));
    }

    public static byte[] generar(Datos d) {
        StringBuilder c = new StringBuilder();

        // Franja superior
        c.append("0.13 0.19 0.33 rg\n0 770 ").append(ANCHO).append(" 72 re f\n");
        texto(c, "F2", 20, MARGEN, 803, "1 1 1", "RECIBO DE PAGO");
        texto(c, "F1", 10, MARGEN, 785, "0.85 0.89 0.95", "Camino a la Iluminación");

        double y = 735;
        texto(c, "F2", 11, MARGEN, y, "0.35 0.35 0.35", "Recibo " + d.numero());
        y -= 28;
        for (String linea : partir(d.parroquia(), 15, ANCHO - 2 * MARGEN, true)) { texto(c, "F2", 15, MARGEN, y, "0.1 0.1 0.1", linea); y -= 19; }
        for (String linea : partir(d.direccion(), 10, ANCHO - 2 * MARGEN, false)) { texto(c, "F1", 10, MARGEN, y, "0.4 0.4 0.4", linea); y -= 13; }
        y -= 8;
        linea(c, MARGEN, y, ANCHO - MARGEN);
        y -= 28;

        y = fila(c, y, "FECHA DE PAGO", d.fechaPago());
        y = fila(c, y, "FECHA DE VERIFICACIÓN", d.fechaVerificacion());
        y = fila(c, y, "FELIGRÉS", d.feligres());
        y = fila(c, y, "CONCEPTO", d.concepto());
        y = fila(c, y, "MÉTODO DE PAGO", d.metodo());
        y = fila(c, y, "N.º DE OPERACIÓN", d.operacion());

        // Caja del monto
        y -= 6;
        c.append("0.95 0.97 0.99 rg\n0.8 0.85 0.92 RG\n").append(MARGEN).append(' ').append(y - 62).append(' ')
         .append(ANCHO - 2 * MARGEN).append(" 70 re B\n");
        texto(c, "F1", 8.5, MARGEN + 16, y - 6, "0.4 0.4 0.4", "MONTO PAGADO");
        texto(c, "F2", 28, MARGEN + 16, y - 44, "0.1 0.1 0.1", d.monto());
        texto(c, "F2", 11, ANCHO - MARGEN - 150, y - 40, "0.1 0.5 0.25", "PAGO VERIFICADO");

        // Pie
        linea(c, MARGEN, 95, ANCHO - MARGEN);
        int py = 80;
        for (String l : partir("Este recibo certifica que la parroquia verificó el pago indicado. No reemplaza el registro sacramental "
                + "de la parroquia ni es un comprobante de pago tributario.", 8, ANCHO - 2 * MARGEN, false)) {
            texto(c, "F1", 8, MARGEN, py, "0.45 0.45 0.45", l);
            py -= 11;
        }
        texto(c, "F1", 8, MARGEN, py - 4, "0.45 0.45 0.45", "Emitido el " + d.generadoEn());

        return documento(c.toString(), "Recibo " + d.numero());
    }

    // ------------------------------------------------------------------ dibujo

    private static double fila(StringBuilder c, double y, String etiqueta, String valor) {
        texto(c, "F1", 8.5, MARGEN, y, "0.45 0.45 0.45", etiqueta);
        List<String> lineas = partir(valor == null || valor.isBlank() ? "-" : valor, 11, ANCHO_VALOR, false);
        for (String l : lineas) {
            texto(c, "F1", 11, COL_VALOR, y, "0.1 0.1 0.1", l);
            y -= 15;
        }
        return y - 9;
    }

    private static void texto(StringBuilder c, String fuente, double tam, double x, double y, String color, String t) {
        c.append(color).append(" rg\nBT /").append(fuente).append(' ').append(num(tam)).append(" Tf ")
         .append(num(x)).append(' ').append(num(y)).append(" Td (").append(escapar(limpiar(t))).append(") Tj ET\n");
    }

    private static void linea(StringBuilder c, double x1, double y, double x2) {
        c.append("0.8 0.8 0.8 RG\n0.7 w\n").append(num(x1)).append(' ').append(num(y)).append(" m ")
         .append(num(x2)).append(' ').append(num(y)).append(" l S\n");
    }

    // ------------------------------------------------------------------ texto

    /** Deja solo caracteres que la fuente WinAnsi dibuja bien: ASCII imprimible y Latin-1 (con tildes y ñ); el resto pasa a "?". */
    static String limpiar(String t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        t.codePoints().forEach(cp -> {
            if ((cp >= 0x20 && cp <= 0x7E) || (cp >= 0xA0 && cp <= 0xFF)) sb.append((char) cp);
            else if (Character.isWhitespace(cp) || Character.isISOControl(cp)) sb.append(' ');
            else sb.append('?');
        });
        return sb.toString();
    }

    /** Escapa lo que es especial dentro de un texto PDF entre paréntesis. */
    static String escapar(String t) {
        return t.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    /** Parte un texto en líneas que quepan en {@code maxAncho}; admite como máximo {@link #MAX_LINEAS_POR_CAMPO}. */
    static List<String> partir(String texto, double tam, double maxAncho, boolean negrita) {
        String limpio = limpiar(texto).trim().replaceAll("\\s+", " ");
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : limpio.split(" ")) {
            while (ancho(palabra, tam, negrita) > maxAncho) { // palabra más larga que la línea: se corta
                int n = Math.max(1, palabra.length() / 2);
                while (n > 1 && ancho(palabra.substring(0, n), tam, negrita) > maxAncho) n--;
                if (actual.length() > 0) { lineas.add(actual.toString()); actual.setLength(0); }
                lineas.add(palabra.substring(0, n));
                palabra = palabra.substring(n);
            }
            String prueba = actual.length() == 0 ? palabra : actual + " " + palabra;
            if (ancho(prueba, tam, negrita) <= maxAncho) {
                actual.setLength(0);
                actual.append(prueba);
            } else {
                lineas.add(actual.toString());
                actual.setLength(0);
                actual.append(palabra);
            }
        }
        if (actual.length() > 0 || lineas.isEmpty()) lineas.add(actual.toString());
        if (lineas.size() > MAX_LINEAS_POR_CAMPO) {
            lineas = new ArrayList<>(lineas.subList(0, MAX_LINEAS_POR_CAMPO));
            lineas.set(MAX_LINEAS_POR_CAMPO - 1, lineas.get(MAX_LINEAS_POR_CAMPO - 1) + "...");
        }
        return lineas;
    }

    static double ancho(String t, double tam, boolean negrita) {
        double total = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            total += (ch >= 32 && ch <= 126) ? ANCHOS[ch - 32] : 556;
        }
        return total * tam / 1000.0 * (negrita ? 1.08 : 1.0); // la negrita es algo más ancha: margen de seguridad
    }

    // ------------------------------------------------------------------ estructura del archivo

    private static byte[] documento(String contenido, String titulo) {
        byte[] flujo = contenido.getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> posiciones = new ArrayList<>();

        escribir(out, "%PDF-1.4\n");
        out.writeBytes(new byte[] {'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'}); // marca de archivo binario

        objeto(out, posiciones, "<< /Type /Catalog /Pages 2 0 R >>");
        objeto(out, posiciones, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objeto(out, posiciones, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> /Contents 4 0 R >>");
        posiciones.add(out.size());
        escribir(out, "4 0 obj\n<< /Length " + flujo.length + " >>\nstream\n");
        out.writeBytes(flujo);
        escribir(out, "\nendstream\nendobj\n");
        objeto(out, posiciones, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objeto(out, posiciones, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        objeto(out, posiciones, "<< /Title (" + escapar(limpiar(titulo).replaceAll("[^\\x20-\\x7E]", "?")) + ") /Producer (Camino a la Iluminacion) >>");

        int inicioXref = out.size();
        escribir(out, "xref\n0 " + (posiciones.size() + 1) + "\n0000000000 65535 f \n");
        for (int pos : posiciones) escribir(out, String.format("%010d 00000 n \n", pos));
        escribir(out, "trailer\n<< /Size " + (posiciones.size() + 1) + " /Root 1 0 R /Info 7 0 R >>\nstartxref\n" + inicioXref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void objeto(ByteArrayOutputStream out, List<Integer> posiciones, String cuerpo) {
        posiciones.add(out.size());
        escribir(out, (posiciones.size()) + " 0 obj\n" + cuerpo + "\nendobj\n");
    }

    private static void escribir(ByteArrayOutputStream out, String t) {
        out.writeBytes(t.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String num(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String fecha(Timestamp t) {
        return t == null ? "-" : FECHA_HORA.format(t.toInstant().atZone(LIMA));
    }

    private static String soles(BigDecimal monto) {
        return "S/ " + monto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
