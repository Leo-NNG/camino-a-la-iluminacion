package pe.caminoiluminacion.controlador;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas.Contenedor;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.controlador.Pruebas.FakeUsuarios;
import pe.caminoiluminacion.controlador.Pruebas.Resp;
import pe.caminoiluminacion.dao.BitacoraDAO.Entrada;
import pe.caminoiluminacion.servicio.BitacoraService;
import pe.caminoiluminacion.util.Sesion;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pe.caminoiluminacion.controlador.Pruebas.cuerpo;
import static pe.caminoiluminacion.controlador.Pruebas.m;

/** Bloque 3-C: bitácora visible en el panel y endpoint de salud. */
class BitacoraYSaludServletTest {

    FakeBitacora dao;
    FakeUsuarios usuarios;
    BitacoraAdminServlet servlet;
    int idAdmin, idAdminSinParroquia, idFeligres;

    @BeforeEach
    void preparar() throws Exception {
        dao = new FakeBitacora();
        usuarios = new FakeUsuarios();
        servlet = new BitacoraAdminServlet(new BitacoraService(dao));
        idAdmin = usuarios.insertar(Pruebas.usuario("70000030", "a@x.pe", "Admin123", "administrador", 2)).getIdUsuario();
        idAdminSinParroquia = usuarios.insertar(Pruebas.usuario("70000031", "b@x.pe", "Admin123", "administrador", null)).getIdUsuario();
        idFeligres = usuarios.insertar(Pruebas.usuario("70000032", "c@x.pe", "Clave123", "feligres", null)).getIdUsuario();
    }

    private Contenedor sesion(int id) {
        Contenedor c = new Contenedor();
        Sesion.iniciar(Pruebas.req(c, m(), "/"), usuarios.buscarPorId(id).get());
        return c;
    }

    private Resp get(Contenedor c, Map<String, String> p) throws Exception {
        Resp r = new Resp();
        servlet.doGet(Pruebas.req(c, p, "/"), Pruebas.resp(r));
        return r;
    }

    @Test
    void laBitacoraPideAdministradorConParroquia() throws Exception {
        assertEquals(401, get(new Contenedor(), m()).status);
        assertEquals(403, get(sesion(idFeligres), m()).status);
        assertEquals(403, get(sesion(idAdminSinParroquia), m()).status);
        assertNull(dao.parroquiaPedida, "sin permiso no se consulta la base");
    }

    @Test
    void devuelveLaActividadDeLaParroquiaDeLaSesionComoJsonEscapado() throws Exception {
        dao.entradas.add(new Entrada(LocalDateTime.of(2026, 11, 27, 10, 15, 30, 123), "Ana \"Personal\"", "PAGO_APROBADO", "pago 12", "10.0.0.1"));
        dao.entradas.add(new Entrada(LocalDateTime.of(2026, 11, 26, 9, 0, 5), "Luis", "LOGIN_OK", null, null));
        Resp r = get(sesion(idAdmin), m("parroquia", "9"));
        assertEquals(200, r.status);
        assertEquals(2, dao.parroquiaPedida.intValue(), "la parroquia sale de la sesión, nunca de un parámetro");
        assertNull(dao.accionPedida);
        assertEquals(BitacoraService.LIMITE_POR_DEFECTO, dao.limitePedido.intValue());
        assertEquals("[{\"fecha\":\"2026-11-27T10:15:30\",\"usuario\":\"Ana \\\"Personal\\\"\",\"accion\":\"PAGO_APROBADO\",\"detalle\":\"pago 12\",\"ip\":\"10.0.0.1\"},"
                + "{\"fecha\":\"2026-11-26T09:00:05\",\"usuario\":\"Luis\",\"accion\":\"LOGIN_OK\",\"detalle\":\"\",\"ip\":\"\"}]", cuerpo(r));
    }

    @Test
    void filtraPorAccionYAjustaElLimite() throws Exception {
        assertEquals(200, get(sesion(idAdmin), m("accion", " pago_aprobado ", "limite", "5000")).status);
        assertEquals("PAGO_APROBADO", dao.accionPedida);
        assertEquals(BitacoraService.LIMITE_MAXIMO, dao.limitePedido.intValue());

        assertEquals(200, get(sesion(idAdmin), m("limite", "0")).status);
        assertEquals(1, dao.limitePedido.intValue());
    }

    @Test
    void rechazaFiltrosRaros() throws Exception {
        dao.parroquiaPedida = null;
        assertEquals(400, get(sesion(idAdmin), m("accion", "X' OR '1'='1")).status);
        assertEquals(400, get(sesion(idAdmin), m("limite", "muchos")).status);
        assertNull(dao.parroquiaPedida, "un filtro inválido no llega a la base");
    }

    @Test
    void siLaBaseFallaResponde500SinDetalles() throws Exception {
        dao.fallar = true;
        Resp r = get(sesion(idAdmin), m());
        assertEquals(500, r.status);
        assertFalse(cuerpo(r).contains("caída"));
    }

    // ---------- /api/salud ----------

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-27T15:00:00.456Z"), ZoneOffset.UTC);

    private Resp salud(SaludServlet.ComprobadorBd bd) throws Exception {
        Resp r = new Resp();
        new SaludServlet(bd, RELOJ).doGet(Pruebas.req(new Contenedor(), m(), "/"), Pruebas.resp(r));
        return r;
    }

    @Test
    void saludOkCuandoLaBaseResponde() throws Exception {
        Resp r = salud(() -> { });
        assertEquals(200, r.status);
        assertEquals("{\"estado\":\"ok\",\"baseDatos\":\"ok\",\"hora\":\"2026-11-27T15:00:00Z\"}", cuerpo(r));
    }

    @Test
    void salud503SinDetallesCuandoLaBaseNoResponde() throws Exception {
        Resp r = salud(() -> { throw new SQLException("Access denied for user 'root' (password: YES)"); });
        assertEquals(503, r.status);
        assertEquals("{\"estado\":\"error\",\"baseDatos\":\"error\",\"hora\":\"2026-11-27T15:00:00Z\"}", cuerpo(r));
        assertFalse(cuerpo(r).contains("root"));
    }
}
