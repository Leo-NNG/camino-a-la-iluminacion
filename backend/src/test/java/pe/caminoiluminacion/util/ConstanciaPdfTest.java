package pe.caminoiluminacion.util;

import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.servicio.VerificacionService.Constancia;
import pe.caminoiluminacion.util.ReciboPdf.Datos;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ConstanciaPdfTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-10T15:30:00Z"), ZoneId.of("UTC"));
    private static final String URL = "https://iglesia.example/paginas/verificar.html?codigo=CON-7K3M9-PQ2XD";

    private static Constancia constancia(String solicitante) {
        return new Constancia("CON-7K3M9-PQ2XD", "Parroquia San José", "Av. Los Libertadores 450, Chiclayo", "Bautizo", solicitante,
                Date.valueOf("2026-12-20"), "3 de 3 requisitos obligatorios validados (expediente completo)");
    }

    private static String texto(byte[] pdf) { return new String(pdf, StandardCharsets.ISO_8859_1); }

    @Test
    void esUnPdfBienFormado() {
        String pdf = texto(ConstanciaPdf.generar(constancia("Ana Torres"), URL, RELOJ));
        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.trim().endsWith("%%EOF"));
        Matcher sx = Pattern.compile("startxref\\n(\\d+)\\n").matcher(pdf);
        assertTrue(sx.find());
        assertTrue(pdf.startsWith("xref", Integer.parseInt(sx.group(1))), "startxref apunta a la tabla xref");
        Matcher len = Pattern.compile("/Length (\\d+) >>\\nstream\\n").matcher(pdf);
        assertTrue(len.find());
        int inicio = len.end();
        assertTrue(pdf.startsWith("\nendstream", inicio + Integer.parseInt(len.group(1))), "/Length coincide con el flujo");
    }

    @Test
    void traeLosDatosYElCodigoDeVerificacion() {
        String pdf = texto(ConstanciaPdf.generar(constancia("Ana Torres"), URL, RELOJ));
        for (String esperado : new String[]{"CONSTANCIA DE RESERVA", "Constancia CON-7K3M9-PQ2XD", "Parroquia San Jos", "Bautizo", "Ana Torres",
                "Domingo 20 de diciembre de 2026", "expediente completo", "FECHA CONFIRMADA POR LA PARROQUIA",
                "verificar.html?codigo=CON-7K3M9-PQ2XD", "Emitida el 10/11/2026 10:30"}) {
            assertTrue(pdf.contains(esperado), "falta: " + esperado);
        }
        assertTrue(pdf.split("CON-7K3M9-PQ2XD", -1).length - 1 >= 3, "el código sale en el título, en la caja de verificación y en la dirección");
    }

    @Test
    void sinResumenDeDocumentosMuestraUnGuion() {
        Constancia sin = new Constancia("CON-7K3M9-PQ2XD", "Parroquia", "", "Matrimonio", "Ana", Date.valueOf("2026-12-20"), null);
        assertTrue(texto(ConstanciaPdf.generar(sin, URL, RELOJ)).contains("(-) Tj"));
    }

    @Test
    void unNombreMalintencionadoNoPuedeRomperElArchivoNiInyectarOperadores() {
        String pdf = texto(ConstanciaPdf.generar(constancia("Ana) Tj ET 1 0 0 rg 0 0 595 842 re f BT (Hackeado \\ \u202E \u0000 \uD83D\uDE00"), URL, RELOJ));
        assertFalse(pdf.contains("Ana) Tj ET 1 0 0 rg"), "los paréntesis se escapan");
        assertTrue(pdf.contains("Ana\\) Tj ET"), "queda como texto escapado");
        assertFalse(pdf.contains("\u202E") || pdf.contains("\u0000Hack"));
        assertTrue(pdf.trim().endsWith("%%EOF"));
    }

    // ---- el recibo con código de verificación

    private static Datos datosRecibo() {
        return new Datos("R-000123", "Parroquia San José", "Av. Los Libertadores 450, Chiclayo", "21/10/2026 10:15", "21/10/2026 11:02",
                "Ana Torres", "Arancel de Bautizo", "Yape", "123456789", "S/ 50.00", "21/10/2026 11:05");
    }

    @Test
    void elReciboConCodigoLoImprimeYSinCodigoQuedaIgualQueAntes() {
        String url = "https://iglesia.example/paginas/verificar.html?codigo=REC-7K3M9-PQ2XD";
        String con = texto(ReciboPdf.generar(datosRecibo(), "REC-7K3M9-PQ2XD", url));
        assertTrue(con.contains("REC-7K3M9-PQ2XD") && con.contains("verificar.html?codigo=REC-7K3M9-PQ2XD") && con.contains("C\u00D3DIGO DE VERIFICACI\u00D3N"));
        assertTrue(con.contains("PAGO VERIFICADO") && con.contains("Recibo R-000123"), "conserva lo que ya traía");
        assertArrayEquals(ReciboPdf.generar(datosRecibo()), ReciboPdf.generar(datosRecibo(), null, null), "sin código el PDF es idéntico al de antes");
        assertFalse(texto(ReciboPdf.generar(datosRecibo())).contains("REC-"));
    }
}
