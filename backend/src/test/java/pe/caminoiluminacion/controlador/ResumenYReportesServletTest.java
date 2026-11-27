package pe.caminoiluminacion.controlador;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas.Contenedor;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.controlador.Pruebas.FakeUsuarios;
import pe.caminoiluminacion.controlador.Pruebas.Resp;
import pe.caminoiluminacion.dao.ReporteDAO.Resumen;
import pe.caminoiluminacion.servicio.BitacoraService;
import pe.caminoiluminacion.servicio.FakeReportes;
import pe.caminoiluminacion.servicio.ReporteService;
import pe.caminoiluminacion.util.Sesion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pe.caminoiluminacion.controlador.Pruebas.cuerpo;
import static pe.caminoiluminacion.controlador.Pruebas.m;

/** Bloque 2-D: resumen (dashboard) del panel y reporte de ingresos en JSON. */
class ResumenYReportesServletTest {

    FakeReportes dao;
    FakeUsuarios usuarios;
    ResumenAdminServlet resumen;
    ReportesAdminServlet reportes;
    int idAdmin, idAdminSinParroquia, idFeligres;

    @BeforeEach
    void preparar() throws Exception {
        dao = new FakeReportes();
        usuarios = new FakeUsuarios();
        ReporteService servicio = new ReporteService(dao, new BitacoraService(new FakeBitacora()),
                Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneId.of("America/Lima")));
        resumen = new ResumenAdminServlet(servicio);
        reportes = new ReportesAdminServlet(servicio);
        idAdmin = usuarios.insertar(Pruebas.usuario("70000020", "a@x.pe", "Admin123", "administrador", 3)).getIdUsuario();
        idAdminSinParroquia = usuarios.insertar(Pruebas.usuario("70000021", "b@x.pe", "Admin123", "administrador", null)).getIdUsuario();
        idFeligres = usuarios.insertar(Pruebas.usuario("70000022", "c@x.pe", "Clave123", "feligres", null)).getIdUsuario();
    }

    private Contenedor sesion(int id) {
        Contenedor c = new Contenedor();
        Sesion.iniciar(Pruebas.req(c, m(), "/"), usuarios.buscarPorId(id).get());
        return c;
    }

    private Resp getResumen(Contenedor c) throws Exception {
        Resp r = new Resp();
        resumen.doGet(Pruebas.req(c, m(), "/"), Pruebas.resp(r));
        return r;
    }

    private Resp getReporte(Contenedor c, Map<String, String> p) throws Exception {
        Resp r = new Resp();
        reportes.doGet(Pruebas.req(c, p, "/"), Pruebas.resp(r));
        return r;
    }

    @Test
    void elResumenPideSesionDeAdministradorConParroquia() throws Exception {
        assertEquals(401, getResumen(new Contenedor()).status);
        assertEquals(403, getResumen(sesion(idFeligres)).status);
        assertEquals(403, getResumen(sesion(idAdminSinParroquia)).status);
        assertNull(dao.parroquiaPedida, "sin permiso no se consulta la base");
    }

    @Test
    void elResumenDevuelveLosContadoresDeLaParroquiaDeLaSesion() throws Exception {
        Resp r = getResumen(sesion(idAdmin));
        assertEquals(200, r.status);
        assertEquals(3, dao.parroquiaPedida.intValue(), "solo la parroquia del administrador, nunca un parámetro");
        assertEquals("{\"pendientes\":{\"solicitudes\":3,\"documentos\":2,\"pagos\":4},"
                + "\"proximos\":{\"celebraciones\":1,\"intenciones\":5},"
                + "\"mes\":{\"solicitudes\":9,\"canceladas\":2,\"pagos\":6,\"ingresos\":175.50},\"diasProximos\":7}", cuerpo(r));
    }

    @Test
    void losIngresosDelResumenSiempreLlevanDosDecimales() {
        String js = ResumenAdminServlet.json(new Resumen(0, 0, 0, 0, 0, 0, 0, 0, new BigDecimal("1E+3")));
        assertTrue(js.contains("\"ingresos\":1000.00"), js);
    }

    @Test
    void elReporteDeTramitesEnJsonIncluyeCanceladas() throws Exception {
        Resp r = getReporte(sesion(idAdmin), m("tipo", "tramites"));
        assertEquals(200, r.status);
        assertTrue(cuerpo(r).contains("{\"sacramento\":\"Bautizo\",\"solicitudes\":6,\"confirmadas\":3,\"pendientes\":1,\"rechazadas\":1,\"canceladas\":1}"), cuerpo(r));
    }

    @Test
    void elReporteDeIngresosEnJsonTraeLosCuatroConceptos() throws Exception {
        Resp r = getReporte(sesion(idAdmin), m("tipo", "ingresos", "mes", "2026-10"));
        assertEquals(200, r.status);
        assertEquals("2026-10", dao.mesPedido);
        String js = cuerpo(r);
        assertTrue(js.startsWith("[{\"concepto\":\"arancel\",\"pagos\":2,\"yape\":80.00,\"plin\":80.00,\"total\":160.00}"), js);
        assertTrue(js.contains("{\"concepto\":\"diezmo\",\"pagos\":0,\"yape\":0.00,\"plin\":0.00,\"total\":0.00}"), js);
        assertEquals(400, getReporte(sesion(idAdmin), m("tipo", "ingresos", "mes", "2026/10")).status);
        assertEquals(403, getReporte(sesion(idFeligres), m("tipo", "ingresos")).status);
    }
}
