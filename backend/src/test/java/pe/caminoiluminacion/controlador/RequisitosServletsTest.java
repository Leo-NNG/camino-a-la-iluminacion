package pe.caminoiluminacion.controlador;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.caminoiluminacion.controlador.Pruebas.Contenedor;
import pe.caminoiluminacion.controlador.Pruebas.Resp;
import pe.caminoiluminacion.servicio.RequisitoEntorno;
import pe.caminoiluminacion.util.Sesion;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pe.caminoiluminacion.controlador.Pruebas.cuerpo;
import static pe.caminoiluminacion.controlador.Pruebas.m;

/** Servlets de requisitos: consulta pública, checklist del feligrés, administración y avance en el panel de reservas. */
class RequisitosServletsTest {

    @TempDir Path carpeta;
    RequisitoEntorno e;
    RequisitosServlet publico;
    ChecklistServlet checklist;
    RequisitosAdminServlet admin;
    int idAdmin1, idAdmin2;

    @BeforeEach
    void preparar() throws Exception {
        e = new RequisitoEntorno(carpeta);
        publico = new RequisitosServlet(e.servicio);
        checklist = new ChecklistServlet(e.servicio);
        admin = new RequisitosAdminServlet(e.servicio);
        idAdmin1 = e.usuarios.insertar(Pruebas.usuario("70000010", "a1@x.pe", "Admin123", "administrador", 1)).getIdUsuario();
        idAdmin2 = e.usuarios.insertar(Pruebas.usuario("70000011", "a2@x.pe", "Admin123", "administrador", 2)).getIdUsuario();
    }

    private Contenedor sesion(int id) {
        Contenedor c = new Contenedor();
        Sesion.iniciar(Pruebas.req(c, m(), "/"), e.usuarios.buscarPorId(id).get());
        return c;
    }

    private Resp get(jakarta.servlet.http.HttpServlet s, Contenedor c, Map<String, String> p) throws Exception {
        Resp r = new Resp();
        if (s instanceof RequisitosServlet x) x.doGet(Pruebas.req(c, p, "/"), Pruebas.resp(r));
        else if (s instanceof ChecklistServlet x) x.doGet(Pruebas.req(c, p, "/"), Pruebas.resp(r));
        else if (s instanceof RequisitosAdminServlet x) x.doGet(Pruebas.req(c, p, "/"), Pruebas.resp(r));
        return r;
    }

    private Resp post(Contenedor c, Map<String, String> p) throws Exception {
        Resp r = new Resp();
        admin.doPost(Pruebas.req(c, p, "/"), Pruebas.resp(r));
        return r;
    }

    private Resp delete(Contenedor c, Map<String, String> p) throws Exception {
        Resp r = new Resp();
        admin.doDelete(Pruebas.req(c, p, "/"), Pruebas.resp(r));
        return r;
    }

    // ---- consulta pública

    @Test
    void laConsultaPublicaNoPideSesionYDevuelveLosRequisitosEnOrden() throws Exception {
        Resp r = get(publico, new Contenedor(), m("parroquia", "1", "sacramento", "bautizo"));
        String js = cuerpo(r);
        assertEquals(200, r.status);
        assertTrue(js.indexOf("Partida de nacimiento") < js.indexOf("DNI de los padres") && js.indexOf("DNI de los padres") < js.indexOf("Charla prebautismal"));
        assertTrue(js.contains("\"obligatorio\":true") && js.contains("\"obligatorio\":false"));
        assertFalse(js.contains("Partida de bautismo"), "no mezcla otros sacramentos");
        assertEquals(400, get(publico, new Contenedor(), m("parroquia", "x", "sacramento", "bautizo")).status);
        assertEquals(400, get(publico, new Contenedor(), m("parroquia", "1", "sacramento", "nada")).status);
    }

    // ---- checklist del feligrés

    @Test
    void elChecklistExigeSesionDeFeligresYSoloMuestraLaSolicitudPropia() throws Exception {
        Map<String, String> p = m("reserva", "1");
        assertEquals(401, get(checklist, new Contenedor(), p).status);
        assertEquals(403, get(checklist, sesion(idAdmin1), p).status, "el personal no usa el checklist del feligrés");
        assertEquals(404, get(checklist, sesion(e.otra), p).status, "la solicitud de otra persona no existe");
        assertEquals(400, get(checklist, sesion(e.ana), m("reserva", "abc")).status);
    }

    @Test
    void elChecklistInformaElEstadoDeCadaRequisito() throws Exception {
        e.documento(e.partida, "validado", null);
        e.documento(e.dni, "rechazado", "Está \"borrosa\"");
        Resp r = get(checklist, sesion(e.ana), m("reserva", "1"));
        String js = cuerpo(r);
        assertEquals(200, r.status);
        assertTrue(js.contains("\"obligatorios\":3") && js.contains("\"validados\":1") && js.contains("\"completo\":false"));
        assertTrue(js.contains("\"estado\":\"validado\"") && js.contains("\"estado\":\"rechazado\"") && js.contains("\"estado\":\"faltante\""));
        assertTrue(js.contains("Está \\\"borrosa\\\""), "el motivo va escapado: el JSON no se rompe");
        assertTrue(js.contains("\"idDocumento\":null"), "los faltantes no tienen documento");
    }

    // ---- administración

    @Test
    void laAdministracionExigeUnAdministradorDeParroquia() throws Exception {
        assertEquals(401, get(admin, new Contenedor(), m()).status);
        assertEquals(403, get(admin, sesion(e.ana), m()).status, "un feligrés no entra");
        assertEquals(401, post(new Contenedor(), m("sacramento", "bautizo", "nombre", "Algo")).status);
        assertEquals(403, delete(sesion(e.ana), m("id", "1")).status);
    }

    @Test
    void elAdminVeSoloLosRequisitosDeSuParroquia() throws Exception {
        String js = cuerpo(get(admin, sesion(idAdmin1), m()));
        assertTrue(js.contains("Partida de nacimiento") && js.contains("Partida de bautismo"));
        assertFalse(js.contains("Otro requisito de otra parroquia"));
        assertTrue(cuerpo(get(admin, sesion(idAdmin2), m())).contains("Otro requisito de otra parroquia"));
    }

    @Test
    void creaEditaYEliminaUnRequisito() throws Exception {
        Contenedor c = sesion(idAdmin1);
        Resp creado = post(c, m("sacramento", "bautizo", "nombre", "Foto de los padrinos", "descripcion", "Tamaño carnet", "obligatorio", "false"));
        assertEquals(200, creado.status);
        assertTrue(cuerpo(creado).startsWith("{\"exito\":true,\"id\":"));
        String id = cuerpo(creado).replaceAll("\\D", "");

        assertEquals(200, post(c, m("id", id, "nombre", "Foto de padrinos y padres", "obligatorio", "true", "orden", "9")).status);
        assertTrue(cuerpo(get(admin, c, m())).contains("Foto de padrinos y padres"));

        assertEquals(200, delete(c, m("id", id)).status);
        assertFalse(cuerpo(get(admin, c, m())).contains("Foto de padrinos y padres"));
    }

    @Test
    void rechazaDatosInvalidosYRepetidos() throws Exception {
        Contenedor c = sesion(idAdmin1);
        assertEquals(400, post(c, m("sacramento", "bautizo", "nombre", "ab")).status);
        assertEquals(409, post(c, m("sacramento", "bautizo", "nombre", "DNI de los padres")).status);
        assertEquals(400, post(c, m("sacramento", "confirmacion", "nombre", "Constancia de confirmación")).status, "la parroquia 1 no ofrece confirmación");
    }

    @Test
    void unAdminNoTocaLosRequisitosDeOtraParroquiaNiEliminaUnoYaUsado() throws Exception {
        int deParroquia1 = e.partida.getIdRequisito();
        assertEquals(404, post(sesion(idAdmin2), m("id", String.valueOf(deParroquia1), "nombre", "Cambio ajeno")).status);
        assertEquals(404, delete(sesion(idAdmin2), m("id", String.valueOf(deParroquia1))).status);
        e.documento(e.partida, "pendiente", null);
        Resp r = delete(sesion(idAdmin1), m("id", String.valueOf(deParroquia1)));
        assertEquals(409, r.status);
        assertTrue(cuerpo(r).contains("documento"));
    }

    // ---- avance en el panel de reservas

    @Test
    void laListaDeReservasDelAdminIncluyeElAvanceDeDocumentos() throws Exception {
        e.documento(e.partida, "validado", null);
        ListarReservasAdminServlet lista = new ListarReservasAdminServlet(Pruebas.servicioReservas(e.reservas, e.usuarios), e.servicio);
        Resp r = new Resp();
        lista.doGet(Pruebas.req(sesion(idAdmin1), m(), "/"), Pruebas.resp(r));
        assertEquals(200, r.status);
        assertTrue(cuerpo(r).contains("\"obligatorios\":3,\"validados\":1"), cuerpo(r));

        Resp sin = new Resp();   // el constructor anterior sigue funcionando y no agrega el avance
        new ListarReservasAdminServlet(Pruebas.servicioReservas(e.reservas, e.usuarios)).doGet(Pruebas.req(sesion(idAdmin1), m(), "/"), Pruebas.resp(sin));
        assertEquals(200, sin.status);
        assertFalse(cuerpo(sin).contains("obligatorios"));
    }
}
