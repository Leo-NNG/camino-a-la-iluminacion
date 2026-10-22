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
 * Genera libros de Excel (.xlsx) con Apache POI: una hoja, encabezado resaltado y fila fija arriba.
 * SEGURIDAD: los textos se escriben siempre como celdas de TEXTO ({@code setCellValue(String)}), así que
 * un valor que empiece con "=", "+" o "@" no se ejecuta como fórmula (no hay inyección de fórmulas).
 */
public final class ExcelReporte {

    private ExcelReporte() { }

    /**
     * @param ultimaFilaEnNegrita true si la última fila es un total y debe resaltarse
     * @param filas cada celda es un String o un Number (Integer, Long, BigDecimal…); null queda vacía
     */
    public static byte[] generar(String nombreHoja, List<String> encabezados, List<List<Object>> filas,
                                 boolean ultimaFilaEnNegrita) throws IOException {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(WorkbookUtil.createSafeSheetName(vacioPorDefecto(nombreHoja)));

            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFont(negrita);
            estiloEncabezado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle estiloTotal = libro.createCellStyle();
            estiloTotal.setFont(negrita);

            escribirFila(hoja.createRow(0), new java.util.ArrayList<Object>(encabezados), estiloEncabezado);
            for (int i = 0; i < filas.size(); i++) {
                boolean total = ultimaFilaEnNegrita && i == filas.size() - 1;
                escribirFila(hoja.createRow(i + 1), filas.get(i), total ? estiloTotal : null);
            }
            hoja.createFreezePane(0, 1);
            for (int c = 0; c < encabezados.size(); c++) {
                hoja.setColumnWidth(c, 26 * 256);
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static void escribirFila(Row fila, List<Object> valores, CellStyle estilo) {
        for (int c = 0; c < valores.size(); c++) {
            Object v = valores.get(c);
            if (v == null) continue;
            Cell celda = fila.createCell(c);
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
