package pe.caminoiluminacion.servicio;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.dao.ReporteDAO;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReporteServiceTest {

    private String mesPedido = "sin llamar";
    private Integer parroquiaPedida;
    private FakeBitacora bitacoraDao;
    private ReporteService servicio;

    @BeforeEach
    void preparar() {
        bitacoraDao = new FakeBitacora();
        servicio = new ReporteService(new ReporteDAO() {
            public List<FilaTramites> tramitesPorSacramento(int idParroquia, String mes) {
                parroquiaPedida = idParroquia;
                mesPedido = mes;
                return List.of(new FilaTramites("Bautizo", 5, 3, 1, 1), new FilaTramites("Matrimonio", 2, 1, 1, 0), new FilaTramites("Confirmación", 0, 0, 0, 0));
            }
            public List<FilaDocumento> documentosPendientes(int idParroquia) {
                parroquiaPedida = idParroquia;
                return List.of(new FilaDocumento("Ana Torres", "dni.png", "Bautizo", "Bebé", "2026-10-05", 3));
            }
            public List<FilaAgenda> agenda(int idParroquia) {
                parroquiaPedida = idParroquia;
                return List.of(new FilaAgenda("2026-11-01", "Bautizo", "Bebé", null));
            }
        }, new BitacoraService(bitacoraDao));
    }

    /** Lee la primera hoja con Apache POI y devuelve el texto de cada fila con las celdas separadas por "|". */
    private static String hoja(byte[] xlsx) throws IOException {
        DataFormatter formato = new DataFormatter();
        StringBuilder sb = new StringBuilder();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheetAt(0);
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
        assertNull(mesPedido);
        servicio.tramites(1, "  ");
        assertNull(mesPedido);
        servicio.tramites(7, "2026-10");
        assertEquals("2026-10", mesPedido);
        assertEquals(7, parroquiaPedida.intValue());
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
        assertTrue(xml.contains("Total|7|4|"), "totales: solicitudes 5+2+0 y confirmadas 3+1+0");
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
}
