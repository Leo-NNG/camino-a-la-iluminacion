package pe.caminoiluminacion.util;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ExcelSimpleTest {

    private static Map<String, byte[]> descomprimir(byte[] xlsx) throws IOException {
        Map<String, byte[]> m = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) m.put(e.getName(), z.readAllBytes());
        }
        return m;
    }

    private static Document xml(byte[] datos) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(datos));
    }

    private static List<List<Object>> celdas(Document hoja) {
        List<List<Object>> filas = new ArrayList<>();
        NodeList rows = hoja.getElementsByTagName("row");
        for (int i = 0; i < rows.getLength(); i++) {
            List<Object> fila = new ArrayList<>();
            NodeList cs = ((Element) rows.item(i)).getElementsByTagName("c");
            for (int j = 0; j < cs.getLength(); j++) {
                Element c = (Element) cs.item(j);
                fila.add("inlineStr".equals(c.getAttribute("t")) ? "T:" + c.getTextContent() : "N:" + c.getTextContent());
            }
            filas.add(fila);
        }
        return filas;
    }

    @Test
    void generaUnZipConLasPartesDeUnLibroValido() throws Exception {
        byte[] xlsx = ExcelSimple.generar("Hoja", List.of("A"), List.of(List.of("x")), false);
        Map<String, byte[]> partes = descomprimir(xlsx);
        for (String parte : new String[]{"[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml"}) {
            assertTrue(partes.containsKey(parte), "falta " + parte);
            assertNotNull(xml(partes.get(parte)), parte + " debe ser XML bien formado");
        }
    }

    @Test
    void guardaEncabezadosTextosYNumeros() throws Exception {
        byte[] xlsx = ExcelSimple.generar("Trámites", List.of("Sacramento", "Solicitudes"),
                List.of(List.of("Bautizo", 5), List.of("Total", 5L)), true);
        List<List<Object>> filas = celdas(xml(descomprimir(xlsx).get("xl/worksheets/sheet1.xml")));
        assertEquals(List.of("T:Sacramento", "T:Solicitudes"), filas.get(0));
        assertEquals(List.of("T:Bautizo", "N:5"), filas.get(1));
        assertEquals(List.of("T:Total", "N:5"), filas.get(2));
    }

    @Test
    void lasFormulasSeGuardanComoTextoNuncaComoFormula() throws Exception {
        byte[] xlsx = ExcelSimple.generar("H", List.of("A"), List.of(List.of("=HYPERLINK(\"http://malo\")"), List.of("+1+1"), List.of("@SUM(A1)")), false);
        Document hoja = xml(descomprimir(xlsx).get("xl/worksheets/sheet1.xml"));
        assertEquals(0, hoja.getElementsByTagName("f").getLength(), "no debe haber ninguna etiqueta de fórmula");
        NodeList cs = hoja.getElementsByTagName("c");
        for (int i = 0; i < cs.getLength(); i++) assertEquals("inlineStr", ((Element) cs.item(i)).getAttribute("t"));
        assertEquals("T:=HYPERLINK(\"http://malo\")", celdas(hoja).get(1).get(0));
    }

    @Test
    void escapaXmlYDescartaCaracteresDeControl() throws Exception {
        byte[] xlsx = ExcelSimple.generar("H", List.of("A"), List.of(List.of("<b>&\"'</b>\u0000\u0001x")), false);
        List<List<Object>> filas = celdas(xml(descomprimir(xlsx).get("xl/worksheets/sheet1.xml")));
        assertEquals("T:<b>&\"'</b>x", filas.get(1).get(0));
    }

    @Test
    void celdasNulasSeOmiten() throws Exception {
        List<Object> fila = new ArrayList<>();
        fila.add("a");
        fila.add(null);
        fila.add("c");
        List<List<Object>> filas = celdas(xml(descomprimir(ExcelSimple.generar("H", List.of("A", "B", "C"), List.of(fila), false)).get("xl/worksheets/sheet1.xml")));
        assertEquals(List.of("T:a", "T:c"), filas.get(1));
    }

    @Test
    void ultimaFilaEnNegritaSoloSiSePide() throws Exception {
        Document con = xml(descomprimir(ExcelSimple.generar("H", List.of("A"), List.of(List.of("x"), List.of("Total")), true)).get("xl/worksheets/sheet1.xml"));
        NodeList cs = con.getElementsByTagName("c");
        assertEquals("1", ((Element) cs.item(0)).getAttribute("s"), "encabezado en negrita");
        assertEquals("", ((Element) cs.item(1)).getAttribute("s"));
        assertEquals("1", ((Element) cs.item(2)).getAttribute("s"), "total en negrita");
        Document sin = xml(descomprimir(ExcelSimple.generar("H", List.of("A"), List.of(List.of("x"), List.of("y")), false)).get("xl/worksheets/sheet1.xml"));
        assertEquals("", ((Element) sin.getElementsByTagName("c").item(2)).getAttribute("s"));
    }

    @Test
    void nombreDeHojaSeAjustaALasReglasDeExcel() {
        assertEquals("Hoja1", ExcelSimple.nombreSeguro(""));
        assertEquals("Hoja1", ExcelSimple.nombreSeguro(null));
        assertEquals("a b c d e f", ExcelSimple.nombreSeguro("a[b]c:d*e?f"));
        assertEquals(31, ExcelSimple.nombreSeguro("x".repeat(80)).length());
        assertFalse(ExcelSimple.nombreSeguro("a/b\\c").contains("/"));
    }

    @Test
    void letrasDeColumna() {
        assertEquals("A", ExcelSimple.columna(0));
        assertEquals("Z", ExcelSimple.columna(25));
        assertEquals("AA", ExcelSimple.columna(26));
        assertEquals("AB", ExcelSimple.columna(27));
    }
}
