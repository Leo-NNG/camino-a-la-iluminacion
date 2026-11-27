package pe.caminoiluminacion.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelReporteTest {

    private static Sheet leer(byte[] xlsx) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx)).getSheetAt(0);
    }

    @Test
    void guardaEncabezadosTextosYNumeros() throws IOException {
        byte[] xlsx = ExcelReporte.generar("Trámites", List.of("Sacramento", "Solicitudes"),
                List.of(List.of("Bautizo", 5), List.of("Total", 5L)), true);
        Sheet h = leer(xlsx);
        assertEquals("Trámites", h.getSheetName());
        assertEquals("Sacramento", h.getRow(0).getCell(0).getStringCellValue());
        assertEquals("Bautizo", h.getRow(1).getCell(0).getStringCellValue());
        assertEquals(CellType.NUMERIC, h.getRow(1).getCell(1).getCellType());
        assertEquals(5.0, h.getRow(1).getCell(1).getNumericCellValue());
        assertEquals("Total", h.getRow(2).getCell(0).getStringCellValue());
    }

    @Test
    void lasFormulasSeGuardanComoTextoNuncaComoFormula() throws IOException {
        byte[] xlsx = ExcelReporte.generar("H", List.of("A"),
                List.of(List.of("=HYPERLINK(\"http://malo\")"), List.of("+1+1"), List.of("@SUM(A1)")), false);
        Sheet h = leer(xlsx);
        for (int i = 1; i <= 3; i++) {
            Cell c = h.getRow(i).getCell(0);
            assertEquals(CellType.STRING, c.getCellType(), "fila " + i + " debe ser texto, no fórmula");
        }
        assertEquals("=HYPERLINK(\"http://malo\")", h.getRow(1).getCell(0).getStringCellValue());
    }

    @Test
    void descartaCaracteresDeControl() throws IOException {
        byte[] xlsx = ExcelReporte.generar("H", List.of("A"), List.of(List.of("<b>&\"'</b>\u0000\u0001x")), false);
        assertEquals("<b>&\"'</b>x", leer(xlsx).getRow(1).getCell(0).getStringCellValue());
    }

    @Test
    void celdasNulasQuedanVacias() throws IOException {
        List<Object> fila = new ArrayList<>();
        fila.add("a");
        fila.add(null);
        fila.add("c");
        Row r = leer(ExcelReporte.generar("H", List.of("A", "B", "C"), List.of(fila), false)).getRow(1);
        assertEquals("a", r.getCell(0).getStringCellValue());
        assertNull(r.getCell(1));
        assertEquals("c", r.getCell(2).getStringCellValue());
    }

    @Test
    void ultimaFilaEnNegritaSoloSiSePide() throws IOException {
        Sheet con = leer(ExcelReporte.generar("H", List.of("A"), List.of(List.of("x"), List.of("Total")), true));
        assertTrue(con.getWorkbook().getFontAt(con.getRow(0).getCell(0).getCellStyle().getFontIndex()).getBold(), "encabezado en negrita");
        assertFalse(con.getWorkbook().getFontAt(con.getRow(1).getCell(0).getCellStyle().getFontIndex()).getBold());
        assertTrue(con.getWorkbook().getFontAt(con.getRow(2).getCell(0).getCellStyle().getFontIndex()).getBold(), "total en negrita");
        Sheet sin = leer(ExcelReporte.generar("H", List.of("A"), List.of(List.of("x"), List.of("y")), false));
        assertFalse(sin.getWorkbook().getFontAt(sin.getRow(2).getCell(0).getCellStyle().getFontIndex()).getBold());
    }

    @Test
    void nombreDeHojaSeAjustaALasReglasDeExcel() throws IOException {
        assertEquals("Hoja1", leer(ExcelReporte.generar(null, List.of("A"), List.of(), false)).getSheetName());
        assertEquals("Hoja1", leer(ExcelReporte.generar("  ", List.of("A"), List.of(), false)).getSheetName());
        String largo = leer(ExcelReporte.generar("x".repeat(80), List.of("A"), List.of(), false)).getSheetName();
        assertEquals(31, largo.length());
        assertFalse(leer(ExcelReporte.generar("a/b\\c", List.of("A"), List.of(), false)).getSheetName().contains("/"));
    }

    @Test
    void aceptaImportesDecimales() throws IOException {
        Sheet h = leer(ExcelReporte.generar("H", List.of("Monto"), List.of(List.of(new BigDecimal("50.50"))), false));
        assertEquals(50.5, h.getRow(1).getCell(0).getNumericCellValue(), 0.0001);
    }

    @Test
    void generaVariasHojasEnOrdenYSinNombresRepetidos() throws IOException {
        byte[] xlsx = ExcelReporte.generar(List.of(
                new ExcelReporte.Hoja("Resumen", List.of("A"), List.of(List.of("uno")), false),
                new ExcelReporte.Hoja("resumen", List.of("B"), List.of(List.of("dos")), false),
                new ExcelReporte.Hoja("x".repeat(40), List.of("C"), List.of(), false),
                new ExcelReporte.Hoja("x".repeat(40), List.of("D"), List.of(), false)));
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertEquals(4, libro.getNumberOfSheets());
            assertEquals("Resumen", libro.getSheetName(0));
            assertEquals("resumen 2", libro.getSheetName(1));
            assertEquals("x".repeat(31), libro.getSheetName(2));
            assertEquals("x".repeat(29) + " 2", libro.getSheetName(3));
            assertEquals("dos", libro.getSheetAt(1).getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void losImportesLlevanFormatoDeSolesYElTotalVaEnNegrita() throws IOException {
        Sheet h = leer(ExcelReporte.generar("H", List.of("Concepto", "Monto", "Pagos"),
                List.of(List.of("Arancel", new BigDecimal("1234.5"), 2), List.of("Total", new BigDecimal("1234.5"), 2)), true));
        assertEquals(ExcelReporte.FORMATO_SOLES, h.getRow(1).getCell(1).getCellStyle().getDataFormatString());
        assertEquals("S/ 1,234.50", new org.apache.poi.ss.usermodel.DataFormatter(java.util.Locale.US).formatCellValue(h.getRow(1).getCell(1)));
        assertEquals("General", h.getRow(1).getCell(2).getCellStyle().getDataFormatString(), "un entero no es un importe");
        assertTrue(h.getWorkbook().getFontAt(h.getRow(2).getCell(1).getCellStyle().getFontIndex()).getBold(), "importe del total en negrita");
        assertEquals(ExcelReporte.FORMATO_SOLES, h.getRow(2).getCell(1).getCellStyle().getDataFormatString());
    }
}
