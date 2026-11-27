package pe.caminoiluminacion.servicio;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReporteServiceTest {

    private FakeReportes dao;
    private FakeBitacora bitacoraDao;
    private ReporteService servicio;

    /** 31 de octubre de 2026, 23:30 en Lima (en UTC ya es 1 de noviembre: el resumen debe usar la fecha de Perú). */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-01T04:30:00Z"), ZoneId.of("America/Lima"));

    @BeforeEach
    void preparar() {
        bitacoraDao = new FakeBitacora();
        dao = new FakeReportes();
        servicio = new ReporteService(dao, new BitacoraService(bitacoraDao), RELOJ);
    }

    /** Lee la primera hoja con Apache POI y devuelve el texto de cada fila con las celdas separadas por "|". */
    private static String hoja(byte[] xlsx) throws IOException {
        return hoja(xlsx, 0);
    }

    private static String hoja(byte[] xlsx, int indice) throws IOException {
        DataFormatter formato = new DataFormatter(java.util.Locale.US);
        StringBuilder sb = new StringBuilder();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheetAt(indice);
            for (Row fila : hoja) {
                for (int c = 0; c < fila.getLastCellNum(); c++) {
                    if (c > 0) sb.append('|');
                    if (fila.getCell(c) != null) sb.append(formato.formatCellValue(fila.getCell(c)));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    void elMesVacioSignificaTodosYElMesValidoSePasaAlDao() throws SQLException {
        servicio.tramites(1, null);
        assertNull(dao.mesPedido);
        servicio.tramites(1, "  ");
        assertNull(dao.mesPedido);
        servicio.tramites(7, "2026-10");
        assertEquals("2026-10", dao.mesPedido);
        assertEquals(7, dao.parroquiaPedida.intValue());
    }

    @Test
    void rechazaMesesConFormatoInvalido() {
        for (String malo : new String[]{"2026-13", "2026-00", "26-10", "2026/10", "octubre", "2026-1", "2026-10-01", "' OR 1=1 --"}) {
            assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.tramites(1, malo)).getEstado(), malo);
        }
    }

    @Test
    void exportaTramitesConFilaDeTotales() throws Exception {
        ReporteService.Archivo a = servicio.exportar(5, 1, "tramites", "2026-10", "ip");
        assertEquals("tramites-2026-10.xlsx", a.nombre());
        String xml = hoja(a.datos());
        assertTrue(xml.contains("Sacramento") && xml.contains("Bautizo") && xml.contains("Total"));
        assertTrue(xml.contains("Total|8|4|"), "totales: solicitudes 6+2+0 y confirmadas 3+1+0");
        assertTrue(bitacoraDao.hay("REPORTE_EXPORTADO"));
    }

    @Test
    void exportaDocumentosYAgendaSinDatosNulos() throws Exception {
        assertEquals("documentos-pendientes.xlsx", servicio.exportar(5, 1, "documentos", null, "ip").nombre());
        ReporteService.Archivo agenda = servicio.exportar(5, 1, "agenda", null, "ip");
        assertEquals("agenda-reservas.xlsx", agenda.nombre());
        assertTrue(hoja(agenda.datos()).contains("2026-11-01"));
        assertEquals("tramites-todos.xlsx", servicio.exportar(5, 1, "tramites", "", "ip").nombre());
    }

    @Test
    void tipoDeReporteInvalidoSeRechazaYNoDejaBitacora() {
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.exportar(5, 1, "../../etc/passwd", null, "ip")).getEstado());
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.exportar(5, 1, null, null, "ip")).getEstado());
        assertFalse(bitacoraDao.hay("REPORTE_EXPORTADO"));
    }

    @Test
    void elNombreDelArchivoNuncaIncluyeTextoDelUsuario() {
        assertThrows(ReglaNegocioException.class, () -> servicio.exportar(5, 1, "tramites", "\"; filename=\"x", "ip"));
    }

    // ---- Bloque 2-D: columna de canceladas, reporte de ingresos y resumen del panel

    @Test
    void elReporteDeTramitesIncluyeLaColumnaDeCanceladas() throws Exception {
        String xml = hoja(servicio.exportar(5, 1, "tramites", null, "ip").datos());
        assertTrue(xml.startsWith("Sacramento|Solicitudes|Confirmadas|Pendientes|Rechazadas|Canceladas\n"), xml);
        assertTrue(xml.contains("Bautizo|6|3|1|1|1\n"));
        assertTrue(xml.contains("Total|8|4|2|1|1\n"), "la fila de totales suma también las canceladas");
    }

    @Test
    void losIngresosSeAgrupanPorConceptoYMetodoConLosCuatroConceptosSiempre() throws SQLException {
        List<ReporteService.IngresoConcepto> filas = servicio.ingresos(4, "2026-10");
        assertEquals("2026-10", dao.mesPedido);
        assertEquals(4, dao.parroquiaPedida.intValue());
        assertEquals(List.of("arancel", "intencion", "donacion", "diezmo"), filas.stream().map(ReporteService.IngresoConcepto::concepto).toList());
        ReporteService.IngresoConcepto arancel = filas.get(0);
        assertEquals(2, arancel.pagos());
        assertEquals(0, new BigDecimal("80").compareTo(arancel.yape()));
        assertEquals(0, new BigDecimal("80").compareTo(arancel.plin()));
        assertEquals(0, new BigDecimal("160").compareTo(arancel.total()));
        assertEquals(0, filas.get(1).pagos(), "intención sin pagos queda en 0");
        assertEquals(0, BigDecimal.ZERO.compareTo(filas.get(3).total()));
        assertEquals(0, new BigDecimal("15.50").compareTo(filas.get(2).yape()));
    }

    @Test
    void losIngresosValidanElMesYVacioSignificaTodos() throws SQLException {
        servicio.ingresos(1, "");
        assertNull(dao.mesPedido);
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.ingresos(1, "2026-13")).getEstado());
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.exportar(5, 1, "ingresos", "x';--", "ip")).getEstado());
        assertFalse(bitacoraDao.hay("REPORTE_EXPORTADO"));
    }

    @Test
    void exportaIngresosConHojaDeResumenYHojaDeDetalle() throws Exception {
        ReporteService.Archivo a = servicio.exportar(5, 1, "ingresos", "2026-10", "ip");
        assertEquals("ingresos-2026-10.xlsx", a.nombre());
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(a.datos()))) {
            assertEquals(2, libro.getNumberOfSheets());
            assertEquals("Resumen de ingresos", libro.getSheetName(0));
            assertEquals("Detalle de pagos", libro.getSheetName(1));
        }
        String resumen = hoja(a.datos(), 0);
        assertTrue(resumen.startsWith("Concepto|Pagos|Yape|Plin|Total\n"), resumen);
        assertTrue(resumen.contains("Arancel|2|S/ 80.00|S/ 80.00|S/ 160.00\n"), resumen);
        assertTrue(resumen.contains("Intención de misa|0|S/ 0.00|S/ 0.00|S/ 0.00\n"), resumen);
        assertTrue(resumen.contains("Total|3|S/ 95.50|S/ 80.00|S/ 175.50\n"), resumen);

        String detalle = hoja(a.datos(), 1);
        assertTrue(detalle.startsWith("Fecha de pago|Feligrés|Concepto|Método|N.º de operación|Monto|Código de recibo\n"), detalle);
        assertTrue(detalle.contains("2026-10-03|Ana Torres|Arancel|Yape|000123|S/ 80.00|REC-AAAAA-BBBBB\n"), "la operación conserva los ceros: " + detalle);
        assertTrue(detalle.contains("2026-10-04|Luis Paz|Arancel|Plin|456789|S/ 80.00|\n"), "sin recibo queda vacío: " + detalle);
        assertTrue(bitacoraDao.hay("REPORTE_EXPORTADO"));
        assertEquals("ingresos-todos.xlsx", servicio.exportar(5, 1, "ingresos", null, "ip").nombre());
    }

    @Test
    void exportarIngresosSinPagosDejaElResumenEnCeroYElDetalleSoloConEncabezado() throws Exception {
        dao.pagos.clear();
        ReporteService.Archivo a = servicio.exportar(5, 1, "ingresos", null, "ip");
        assertTrue(hoja(a.datos(), 0).contains("Total|0|S/ 0.00|S/ 0.00|S/ 0.00\n"));
        assertEquals(1, hoja(a.datos(), 1).split("\n").length);
    }

    @Test
    void elResumenUsaLaFechaDePeruSieteDiasYElInicioDelMes() throws SQLException {
        assertSame(dao.resumen, servicio.resumen(9));
        assertEquals(9, dao.parroquiaPedida.intValue());
        assertEquals(LocalDate.of(2026, 10, 31), dao.hoyPedido, "en Lima todavía es 31 de octubre");
        assertEquals(7, dao.diasPedidos);
        assertEquals(LocalDate.of(2026, 10, 1), dao.inicioMesPedido);
    }
}
