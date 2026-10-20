package pe.caminoiluminacion.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Genera un libro de Excel (.xlsx, formato OOXML) de una sola hoja, sin librerías externas.
 * Los textos se guardan como cadenas literales ("inlineStr"), así que Excel NUNCA los interpreta como
 * fórmulas (un texto que empiece con "=" no se ejecuta): no hay riesgo de inyección de fórmulas.
 */
public final class ExcelSimple {

    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private ExcelSimple() { }

    /**
     * @param ultimaFilaEnNegrita true si la última fila es un total y debe resaltarse
     * @param filas cada celda es un String o un Number (Integer, Long…); null queda vacía
     */
    public static byte[] generar(String nombreHoja, List<String> encabezados, List<List<Object>> filas,
                                 boolean ultimaFilaEnNegrita) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            entrada(zip, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
              + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
              + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
              + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
              + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
              + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
              + "</Types>");
            entrada(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
              + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
              + "</Relationships>");
            entrada(zip, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<workbook xmlns=\"" + NS + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
              + "<sheets><sheet name=\"" + xml(nombreSeguro(nombreHoja)) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entrada(zip, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
              + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
              + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
              + "</Relationships>");
            entrada(zip, "xl/styles.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
              + "<styleSheet xmlns=\"" + NS + "\">"
              + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
              + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
              + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
              + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
              + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
              + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
              + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");

            StringBuilder hoja = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"").append(NS).append("\">")
                .append("<cols><col min=\"1\" max=\"").append(Math.max(1, encabezados.size())).append("\" width=\"26\" customWidth=\"1\"/></cols>")
                .append("<sheetData>");
            fila(hoja, 1, new java.util.ArrayList<Object>(encabezados), true);
            for (int i = 0; i < filas.size(); i++) {
                fila(hoja, i + 2, filas.get(i), ultimaFilaEnNegrita && i == filas.size() - 1);
            }
            hoja.append("</sheetData></worksheet>");
            entrada(zip, "xl/worksheets/sheet1.xml", hoja.toString());
        }
        return salida.toByteArray();
    }

    private static void fila(StringBuilder sb, int numero, List<Object> celdas, boolean negrita) {
        sb.append("<row r=\"").append(numero).append("\">");
        for (int c = 0; c < celdas.size(); c++) {
            Object v = celdas.get(c);
            if (v == null) continue;
            String ref = columna(c) + numero;
            String estilo = negrita ? " s=\"1\"" : "";
            if (v instanceof Number) {
                sb.append("<c r=\"").append(ref).append("\"").append(estilo).append("><v>").append(v).append("</v></c>");
            } else {
                sb.append("<c r=\"").append(ref).append("\"").append(estilo).append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                  .append(xml(String.valueOf(v))).append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    /** 0 -> A, 25 -> Z, 26 -> AA… */
    static String columna(int indice) {
        StringBuilder sb = new StringBuilder();
        int n = indice;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    /** Escapa XML y descarta los caracteres de control que el formato no admite. */
    static String xml(String t) {
        StringBuilder sb = new StringBuilder(t.length() + 8);
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '&') sb.append("&amp;");
            else if (ch == '<') sb.append("&lt;");
            else if (ch == '>') sb.append("&gt;");
            else if (ch == '"') sb.append("&quot;");
            else if (ch == '\'') sb.append("&apos;");
            else if (ch == '\t' || ch == '\n' || ch == '\r' || (ch >= 0x20 && ch != 0x7F && ch != 0xFFFE && ch != 0xFFFF)) sb.append(ch);
        }
        return sb.toString();
    }

    /** Excel limita el nombre de hoja a 31 caracteres y prohíbe []:*?/\ */
    static String nombreSeguro(String nombre) {
        String n = (nombre == null ? "" : nombre).replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (n.isEmpty()) n = "Hoja1";
        return n.length() > 31 ? n.substring(0, 31) : n;
    }

    private static void entrada(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
