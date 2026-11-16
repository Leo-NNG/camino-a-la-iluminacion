package pe.caminoiluminacion.controlador;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas.Contenedor;
import pe.caminoiluminacion.controlador.Pruebas.Resp;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.servicio.GestionEntorno;
import pe.caminoiluminacion.util.Sesion;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pe.caminoiluminacion.controlador.Pruebas.cuerpo;
import static pe.caminoiluminacion.controlador.Pruebas.m;
import static pe.caminoiluminacion.servicio.GestionEntorno.HOY;

/** Servlet de cancelar y reprogramar, y la información que se agrega a "Mis solicitudes" y al panel del personal. */
class ReservaGestionServletTest {

    GestionEntorno e;
    ReservaGestionServlet servlet;
    int idAdmin;

    @BeforeEach
    void preparar() throws Exception {
        e = new GestionEntorno();
        servlet = new ReservaGestionServlet(e.servicio);
        idAdmin = e.usuarios.insertar(Pruebas.usuario("70000010", "adm@x.pe", "Admin123", "administrador", 1)).getIdUsuario();
    }

    private Contenedor sesion(int id) {
        Contenedor c = new Contenedor();
        Sesion.iniciar(Pruebas.req(c, m(), "/"), e.usuarios.buscarPorId(id).get());
        return c;
    }

    private Resp cancelar(Contenedor c, Map<String, String> p) throws Exception {
        Resp r = new Resp();
        servlet.doPost(Pruebas.req(c, p, "/app/api/reservas/cancelar"), Pruebas.resp(r));
        return r;
    }

    private Resp reprogramar(Contenedor c, Map<String, String> p) throws Exception {
        Resp r = new Resp();
        servlet.doPost(Pruebas.req(c, p, "/app/api/reservas/reprogramar"), Pruebas.resp(r));
        return r;
    }

    private String id() { return String.valueOf(e.r.getIdReserva()); }

    @Test
    void sinSesionOSiendoPersonalNoSeGestionaNada() throws Exception {
        assertEquals(401, cancelar(new Contenedor(), m("id_reserva", id())).status);
        assertEquals(401, reprogramar(new Contenedor(), m("id_reserva", id(), "fecha", HOY.plusDays(50).toString())).status);
        assertEquals(403, cancelar(sesion(idAdmin), m("id_reserva", id())).status, "el personal no cancela solicitudes de feligreses");
        assertEquals(403, reprogramar(sesion(idAdmin), m("id_reserva", id(), "fecha", HOY.plusDays(50).toString())).status);
        assertEquals("pendiente", e.r.getEstado());
    }

    @Test
    void cancelaYResponde200() throws Exception {
        Resp r = cancelar(sesion(e.ana), m("id_reserva", id(), "motivo", "Ya no la necesito"));
        assertEquals(200, r.status);
        assertTrue(cuerpo(r).contains("\"exito\":true") && cuerpo(r).contains("\"arancelPagado\":false"));
        assertEquals("cancelada", e.r.getEstado());
        assertEquals("Ya no la necesito", e.r.getMotivoCancelacion());
    }

    @Test
    void siYaPagoElArancelElMensajeLoDiceYNoSeInventaUnReembolso() throws Exception {
        e.pago(e.r, Pago.APROBADO);
        String js = cuerpo(cancelar(sesion(e.ana), m("id_reserva", id())));
        assertTrue(js.contains("\"arancelPagado\":true") && js.contains("comunícate con la parroquia"), js);
        assertFalse(js.toLowerCase().contains("devolv"), "no se promete un reembolso automático");
    }

    @Test
    void erroresDeCancelarSonLosDelServicio() throws Exception {
        assertEquals(404, cancelar(sesion(e.otra), m("id_reserva", id())).status);
        assertEquals(400, cancelar(sesion(e.ana), m("id_reserva", "abc")).status);
        e.pago(e.r, Pago.PENDIENTE);
        Resp r = cancelar(sesion(e.ana), m("id_reserva", id()));
        assertEquals(409, r.status);
        assertTrue(cuerpo(r).contains("pago"));
        assertEquals("pendiente", e.r.getEstado());
    }

    @Test
    void reprogramaYDevuelveLaFechaNueva() throws Exception {
        Resp r = reprogramar(sesion(e.ana), m("id_reserva", id(), "fecha", HOY.plusDays(55).toString()));
        assertEquals(200, r.status);
        assertTrue(cuerpo(r).contains("\"fecha\":\"" + HOY.plusDays(55) + "\""), cuerpo(r));
        assertEquals(1, e.r.getReprogramaciones());
    }

    @Test
    void erroresDeReprogramarSonLosDelServicio() throws Exception {
        assertEquals(400, reprogramar(sesion(e.ana), m("id_reserva", id(), "fecha", "nada")).status);
        assertEquals(400, reprogramar(sesion(e.ana), m("id_reserva", id(), "fecha", HOY.minusDays(1).toString())).status);
        assertEquals(404, reprogramar(sesion(e.otra), m("id_reserva", id(), "fecha", HOY.plusDays(50).toString())).status);
        e.reserva(e.otra, "bautizo", HOY.plusDays(33), "pendiente");
        assertEquals(409, reprogramar(sesion(e.ana), m("id_reserva", id(), "fecha", HOY.plusDays(33).toString())).status);
        assertEquals(HOY.plusDays(40), e.r.getFechaSolicitada().toLocalDate());
    }

    // ---- Mis solicitudes

    @Test
    void misSolicitudesIndicaQueBotonesCorrespondenYLosDatosDeLaReprogramacion() throws Exception {
        var lista = new MisReservasServlet(e.reservaService, e.servicio);
        e.servicio.reprogramar(e.ana, id(), HOY.plusDays(50).toString(), "ip");
        var cancelada = e.reserva(e.ana, "bautizo", HOY.plusDays(70), "cancelada");
        cancelada.setMotivoCancelacion("Viaje");
        Resp r = new Resp();
        lista.doGet(Pruebas.req(sesion(e.ana), m(), "/"), Pruebas.resp(r));
        String js = cuerpo(r);
        assertEquals(200, r.status);
        assertTrue(js.contains("\"reprogramaciones\":1") && js.contains("\"fechaOriginal\":\"" + HOY.plusDays(40) + "\""), js);
        assertTrue(js.contains("\"puedeCancelar\":true,\"puedeReprogramar\":true"), "la reprogramada aún puede gestionarse");
        assertTrue(js.contains("\"puedeCancelar\":false,\"puedeReprogramar\":false"), "la cancelada ya no");
        assertTrue(js.contains("\"motivoCancelacion\":\"Viaje\"") && js.contains("\"idParroquia\":1"));
        assertTrue(js.contains("\"estado\":\"cancelada\""));
    }

    @Test
    void sinElServicioDeGestionMisSolicitudesNoAgregaLasOpciones() throws Exception {
        Resp r = new Resp();
        new MisReservasServlet(e.reservaService).doGet(Pruebas.req(sesion(e.ana), m(), "/"), Pruebas.resp(r));
        assertEquals(200, r.status);
        assertFalse(cuerpo(r).contains("puedeCancelar"));
    }

    // ---- panel del personal

    @Test
    void elPersonalVeQueUnaSolicitudFueReprogramadaYDeQueFecha() throws Exception {
        e.servicio.reprogramar(e.ana, id(), HOY.plusDays(50).toString(), "ip");
        Resp r = new Resp();
        new ListarReservasAdminServlet(e.reservaService).doGet(Pruebas.req(sesion(idAdmin), m(), "/"), Pruebas.resp(r));
        assertEquals(200, r.status);
        assertTrue(cuerpo(r).contains("\"reprogramaciones\":1") && cuerpo(r).contains("\"fechaOriginal\":\"" + HOY.plusDays(40) + "\""), cuerpo(r));
    }

    @Test
    void unaCanceladaNoApareceEnLasPendientesDelPersonal() throws Exception {
        e.servicio.cancelar(e.ana, id(), null, "ip");
        Resp r = new Resp();
        new ListarReservasAdminServlet(e.reservaService).doGet(Pruebas.req(sesion(idAdmin), m(), "/"), Pruebas.resp(r));
        assertEquals("[]", cuerpo(r));
    }
}
