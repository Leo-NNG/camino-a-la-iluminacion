package pe.caminoiluminacion.util;

import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.servicio.PagoService.Recibo;
import pe.caminoiluminacion.util.ReciboPdf.Datos;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ReciboPdfTest {

    private static Datos datos(String feligres) {
        return new Datos("R-000123", "Parroquia San José", "Av. Los Libertadores 450, Chiclayo", "21/10/2026 10:15",
                "21/10/2026 11:02", feligres, "Arancel de Bautizo", "Yape", "123456789", "S/ 50.00", "21/10/2026 11:05");
    }

    private static String texto(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    @Test
    void esUnPdfBienFormadoConLaTablaDeReferenciasCorrecta() {
        String pdf = texto(ReciboPdf.generar(datos("Ana Torres")));
        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.trim().endsWith("%%EOF"));
        // startxref apunta a la palabra "xref"
        Matcher sx = Pattern.compile("startxref\\n(\\d+)\\n").matcher(pdf);
        assertTrue(sx.find());
        assertTrue(pdf.startsWith("xref", Integer.parseInt(sx.group(1))), "startxref debe apuntar a la tabla xref");
        // cada entrada de la tabla apunta al "N 0 obj" correspondiente
        Matcher entradas = Pattern.compile("(\\d{10}) 00000 n ").matcher(pdf);
        int n = 0;
        while (entradas.find()) {
            n++;
            assertTrue(pdf.startsWith(n + " 0 obj", Integer.parseInt(entradas.group(1))), "objeto " + n + " fuera de posición");
        }
        assertEquals(7, n);
        // la longitud declarada del flujo coincide con el flujo real
        Matcher len = Pattern.compile("/Length (\\d+) >>\\nstream\\n").matcher(pdf);
        assertTrue(len.find());
        int ini = len.end();
        assertEquals(Integer.parseInt(len.group(1)), pdf.indexOf("\nendstream", ini) - ini);
    }

    @Test
    void incluyeLosDatosDelRecibo() {
        String pdf = texto(ReciboPdf.generar(datos("Ana Torres")));
        for (String esperado : new String[]{"RECIBO DE PAGO", "Recibo R-000123", "Parroquia San Jos\u00e9", "(Ana Torres)", "(Arancel de Bautizo)",
                "(Yape)", "(123456789)", "(S/ 50.00)", "PAGO VERIFICADO", "Emitido el 21/10/2026 11:05"}) {
            assertTrue(pdf.contains(esperado), "falta: " + esperado);
        }
    }

    @Test
    void unTextoMaliciosoNoPuedeRomperElArchivoNiInyectarOperadores() {
        String normal = texto(ReciboPdf.generar(datos("Ana Torres")));
        String pdf = texto(ReciboPdf.generar(datos("Ana) Tj ET 1 0 0 rg (INYECCION \\ (x")));
        // los paréntesis y la barra quedan escapados: el texto no se cierra antes de tiempo
        assertTrue(pdf.contains("Ana\\) Tj ET 1 0 0 rg \\(INYECCION \\\\ \\(x"));
        // y no aparece ningún bloque de texto ni color extra respecto de un recibo normal
        assertEquals(count(normal, "BT "), count(pdf, "BT "));
        assertEquals(count(normal, " rg\n"), count(pdf, " rg\n"));
    }

    @Test
    void loQueLaFuenteNoDibujaPasaAInterrogante() {
        assertEquals("Ana ? ??", ReciboPdf.limpiar("Ana \u2603 \uD83D\uDE00\u4e2d").replace("???", "??"));
        assertEquals("a  b", ReciboPdf.limpiar("a\n\tb"));
        assertEquals("Mar\u00eda \u00d1and\u00fa", ReciboPdf.limpiar("Mar\u00eda \u00d1and\u00fa"));
        assertEquals("", ReciboPdf.limpiar(null));
    }

    @Test
    void escapaParentesisYBarraInvertida() {
        assertEquals("a\\(b\\)c\\\\d", ReciboPdf.escapar("a(b)c\\d"));
    }

    @Test
    void partirLasLineasLargasSinPasarseDelAncho() {
        String largo = "Parroquia de la Santísima Trinidad y San José del Valle Norte de la Arquidiócesis";
        List<String> lineas = ReciboPdf.partir(largo, 11, 200, false);
        assertTrue(lineas.size() >= 2);
        for (String l : lineas) assertTrue(ReciboPdf.ancho(l, 11, false) <= 200 + 1, "línea demasiado ancha: " + l);
        assertEquals(1, ReciboPdf.partir("Corto", 11, 200, false).size());
        assertEquals(1, ReciboPdf.partir("", 11, 200, false).size(), "un texto vacío da una línea vacía, no falla");
    }

    @Test
    void unaPalabraGigantesSeCortaYElTextoSeLimitaATresLineas() {
        List<String> lineas = ReciboPdf.partir("x".repeat(500), 11, 200, false);
        assertEquals(3, lineas.size());
        assertTrue(lineas.get(2).endsWith("..."));
        for (String l : lineas) assertTrue(ReciboPdf.ancho(l, 11, false) <= 200 + 40);
    }

    @Test
    void armaElReciboDesdeUnPagoAprobado() {
        Pago p = new Pago(7, 1, 1, 3, "arancel", new BigDecimal("50"), "plin", "v1:xx", "987654321", "a.png", "h", "aprobado", null,
                Timestamp.from(Instant.parse("2026-10-21T15:15:00Z")), Timestamp.from(Instant.parse("2026-10-21T16:02:00Z")));
        Clock reloj = Clock.fixed(Instant.parse("2026-10-21T16:05:00Z"), ZoneId.of("America/Lima"));
        String pdf = texto(ReciboPdf.generar(new Recibo(p, "R-000007", "Parroquia San Jos\u00e9", "Av. 1", "Ana Torres", "Arancel de Bautizo"), reloj));
        assertTrue(pdf.contains("(21/10/2026 10:15)"), "hora de Lima (UTC-5)");
        assertTrue(pdf.contains("(21/10/2026 11:02)"));
        assertTrue(pdf.contains("(Plin)"));
        assertTrue(pdf.contains("(S/ 50.00)"), "el monto siempre con dos decimales");
        assertTrue(pdf.contains("Emitido el 21/10/2026 11:05"));
        assertFalse(pdf.contains("v1:xx"), "el celular cifrado nunca aparece");
    }

    private static int count(String texto, String parte) {
        int n = 0, i = 0;
        while ((i = texto.indexOf(parte, i)) >= 0) { n++; i += parte.length(); }
        return n;
    }
}
