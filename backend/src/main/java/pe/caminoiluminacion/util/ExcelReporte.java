package pe.caminoiluminacion.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Genera libros de Excel (.xlsx) con Apache POI: una o varias hojas, encabezado resaltado y fila fija arriba.
 * SEGURIDAD: los textos se escriben siempre como celdas de TEXTO ({@code setCellValue(String)}), así que
 * un valor que empiece con "=", "+" o "@" no se ejecuta como fórmula (no hay inyección de fórmulas).
 */
public final class ExcelReporte {

    private ExcelReporte() { }

    /** Una hoja del libro. {@code ultimaFilaEnNegrita} = true si la última fila es un total. */
    public record Hoja(String nombre, List<String> encabezados, List<List<Object>> filas, boolean ultimaFilaEnNegrita) { }

    /**
     * @param ultimaFilaEnNegrita true si la última fila es un total y debe resaltarse
     * @param filas cada celda es un String o un Number (Integer, Long, BigDecimal…); null queda vacía.
     *              Un BigDecimal se trata como importe y se muestra con formato de soles (S/ 1,234.50).
     */
    public static byte[] generar(String nombreHoja, List<String> encabezados, List<List<Object>> filas,
                                 boolean ultimaFilaEnNegrita) throws IOException {
        return generar(List.of(new Hoja(nombreHoja, encabezados, filas, ultimaFilaEnNegrita)));
    }

    /** Libro con varias hojas, en el orden dado (la primera es la que se abre). */
    public static byte[] generar(List<Hoja> hojas) throws IOException {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Font negrita = libro.createFont();
            negrita.setBold(true);
            short formatoSoles = libro.createDataFormat().getFormat(FORMATO_SOLES);
            Estilos e = new Estilos();
            e.encabezado = libro.createCellStyle();
            e.encabezado.setFont(negrita);
            e.encabezado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            e.encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            e.total = libro.createCellStyle();
            e.total.setFont(negrita);
            e.soles = libro.createCellStyle();
            e.soles.setDataFormat(formatoSoles);
            e.solesTotal = libro.createCellStyle();
            e.solesTotal.setFont(negrita);
            e.solesTotal.setDataFormat(formatoSoles);

            java.util.Set<String> usados = new java.util.HashSet<>();
            for (Hoja h : hojas) {
                String base = WorkbookUtil.createSafeSheetName(vacioPorDefecto(h.nombre()));
                String nombre = base;
                // Dos hojas no pueden llamarse igual (Excel no distingue mayúsculas): se numera "Hoja 2", "Hoja 3"…
                for (int n = 2; !usados.add(nombre.toLowerCase(java.util.Locale.ROOT)); n++) {
                    String sufijo = " " + n;
                    nombre = base.substring(0, Math.min(base.length(), 31 - sufijo.length())) + sufijo;
                }
                escribirHoja(libro.createSheet(nombre), h, e);
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    /** Formato numérico de Excel para importes en soles. */
    static final String FORMATO_SOLES = "\"S/\" #,##0.00";

    private static final class Estilos { CellStyle encabezado, total, soles, solesTotal; }

    private static void escribirHoja(Sheet hoja, Hoja h, Estilos e) {
        escribirFila(hoja.createRow(0), new java.util.ArrayList<Object>(h.encabezados()), e.encabezado, e.encabezado);
        List<List<Object>> filas = h.filas();
        for (int i = 0; i < filas.size(); i++) {
            boolean total = h.ultimaFilaEnNegrita() && i == filas.size() - 1;
            escribirFila(hoja.createRow(i + 1), filas.get(i), total ? e.total : null, total ? e.solesTotal : e.soles);
        }
        hoja.createFreezePane(0, 1);
        for (int c = 0; c < h.encabezados().size(); c++) {
            hoja.setColumnWidth(c, 26 * 256);
        }
    }

    private static void escribirFila(Row fila, List<Object> valores, CellStyle estilo, CellStyle estiloImporte) {
        for (int c = 0; c < valores.size(); c++) {
            Object v = valores.get(c);
            if (v == null) continue;
            Cell celda = fila.createCell(c);
            if (v instanceof java.math.BigDecimal importe) {
                celda.setCellValue(importe.doubleValue());
                celda.setCellStyle(estiloImporte);
                continue;
            }
            if (v instanceof Number n) celda.setCellValue(n.doubleValue());
            else celda.setCellValue(limpiar(String.valueOf(v)));
            if (estilo != null) celda.setCellStyle(estilo);
        }
    }

    /** Descarta los caracteres de control que el formato XML de Excel no admite (conserva tab y saltos de línea). */
    static String limpiar(String t) {
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '\t' || ch == '\n' || ch == '\r' || (ch >= 0x20 && ch != 0x7F && ch != 0xFFFE && ch != 0xFFFF)) sb.append(ch);
        }
        // Excel admite hasta 32 767 caracteres por celda.
        return sb.length() > 32_000 ? sb.substring(0, 32_000) : sb.toString();
    }

    private static String vacioPorDefecto(String nombre) {
        return nombre == null || nombre.isBlank() ? "Hoja1" : nombre;
    }
}
