package pe.caminoiluminacion.controlador;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.caminoiluminacion.controlador.Pruebas.Contenedor;
import pe.caminoiluminacion.controlador.Pruebas.Resp;
import pe.caminoiluminacion.servicio.CuentaEntorno;
import pe.caminoiluminacion.util.Sesion;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pe.caminoiluminacion.controlador.Pruebas.cuerpo;
import static pe.caminoiluminacion.controlador.Pruebas.m;

/** El servlet de "Mi cuenta": sesión obligatoria, rutas, descarga de datos y cierre de todas las sesiones al eliminar. */
class CuentaServletTest {

    private static final java.util.concurrent.atomic.AtomicInteger SIGUIENTE = new java.util.concurrent.atomic.AtomicInteger();

    @TempDir Path carpeta;
    CuentaEntorno e;
    CuentaServlet servlet;
    int ana;
    int admin;

    @BeforeEach
    void preparar() throws Exception {
        e = new CuentaEntorno(carpeta);
        // La revocación de sesiones es estado estático (por id de usuario): ids altos y propios de esta clase evitan
        // que revocar a "ana" afecte a otras pruebas que usan los ids 1, 2...
        e.usuarios.sig = 900_000 + 10 * SIGUIENTE.getAndIncrement(); // distinto en cada prueba
        ana = e.agregar("70000001", "ana@x.pe", "Clave123", "feligres", null);
        admin = e.agregar("70000000", "adm@x.pe", "Admin123", "administrador", 1);
        servlet = new CuentaServlet(e.servicio);
    }

    // ---- ayudas: la petición con ruta (pathInfo) y la respuesta que recuerda las cabeceras

    private static Object invocar(Object destino, java.lang.reflect.Method metodo, Object[] args) throws Throwable {
        try {
            return metodo.invoke(destino, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private static HttpServletRequest peticion(Contenedor c, String ruta, Map<String, String> p) {
        HttpServletRequest base = Pruebas.req(c, p, "/app/api/cuenta" + ruta);
        return (HttpServletRequest) Proxy.newProxyInstance(CuentaServletTest.class.getClassLoader(), new Class[]{HttpServletRequest.class},
                (o, met, args) -> met.getName().equals("getPathInfo") ? (ruta.isEmpty() ? null : ruta) : invocar(base, met, args));
    }

    private static HttpServletResponse respuesta(Resp r, Map<String, String> cabeceras) {
        HttpServletResponse base = Pruebas.resp(r);
        return (HttpServletResponse) Proxy.newProxyInstance(CuentaServletTest.class.getClassLoader(), new Class[]{HttpServletResponse.class},
                (o, met, args) -> {
                    if (met.getName().equals("setHeader")) { cabeceras.put((String) args[0], (String) args[1]); return null; }
                    return invocar(base, met, args);
                });
    }

    private Resp get(Contenedor c, String ruta, Map<String, String> cab) throws Exception {
        Resp r = new Resp();
        servlet.doGet(peticion(c, ruta, m()), respuesta(r, cab));
        return r;
    }

    private Resp post(Contenedor c, String ruta, Map<String, String> p) throws Exception {
        Resp r = new Resp();
        servlet.doPost(peticion(c, ruta, p), respuesta(r, new HashMap<>()));
        return r;
    }

    private Contenedor sesionDe(int id) {
        Contenedor c = new Contenedor();
        Sesion.iniciar(Pruebas.req(c, m(), "/"), e.usuarios.buscarPorId(id).get());
        return c;
    }

    private boolean hayUsuario(Contenedor c) { return Sesion.hayUsuario(Pruebas.req(c, m(), "/")); }

    // ---- pruebas

    @Test
    void sinSesionTodoDa401() throws Exception {
        assertEquals(401, get(new Contenedor(), "", new HashMap<>()).status);
        assertEquals(401, get(new Contenedor(), "/datos", new HashMap<>()).status);
        for (String ruta : new String[]{"/contacto", "/contrasena", "/politica", "/eliminar"}) {
            assertEquals(401, post(new Contenedor(), ruta, m()).status, ruta);
        }
    }

    @Test
    void elPerfilNoSacaElDniCompletoNiElHashDeLaContrasena() throws Exception {
        Resp r = get(sesionDe(ana), "", new HashMap<>());
        String js = cuerpo(r);
        assertEquals(200, r.status);
        assertTrue(js.contains("\"correo\":\"ana@x.pe\"") && js.contains("\"dni\":\"*****001\""));
        assertFalse(js.contains("70000001") || js.contains("pbkdf2") || js.contains("hash"));
        assertTrue(js.contains("\"politicaAceptada\":false"));
    }

    @Test
    void laDescargaDeDatosEsUnAdjuntoSinRutasNiHashesYConElCelularEnmascarado() throws Exception {
        e.agregarReserva(ana, "Bebé Uno", "con \"comillas\" y\nsalto");
        e.agregarDocumento(ana, "partida.pdf", "a".repeat(32) + ".pdf");
        e.agregarPago(ana, "987654321", "c".repeat(32) + ".jpg", "sha-secreto-ana");
        e.agregarAviso(ana, "Tu pago fue verificado");
        Map<String, String> cab = new HashMap<>();
        Resp r = get(sesionDe(ana), "/datos", cab);
        String js = cuerpo(r);
        assertEquals(200, r.status);
        assertTrue(cab.get("Content-Disposition").startsWith("attachment"), "se descarga, no se muestra");
        assertEquals("no-store", cab.get("Cache-Control"));
        assertTrue(js.contains("Bebé Uno") && js.contains("partida.pdf") && js.contains("Tu pago fue verificado"));
        assertTrue(js.contains("\"celularOrigen\":\"******321\""), "el celular va enmascarado");
        assertFalse(js.contains("987654321") || js.contains("a".repeat(32)) || js.contains("c".repeat(32)) || js.contains("sha-secreto-ana"));
        assertFalse(js.contains("pbkdf2") || js.contains("70000001"));
        assertTrue(js.contains("con \\\"comillas\\\" y\\nsalto"), "el texto del usuario va escapado: el JSON no se rompe");
    }

    @Test
    void laCopiaDeDatosIncluyeLosDatosDeCancelacionYReprogramacionDeSusSolicitudes() throws Exception {
        var cancelada = e.agregarReserva(ana, "Bebé Uno", null);
        cancelada.setEstado("cancelada");
        cancelada.setMotivoCancelacion("Cambió el viaje");
        cancelada.setFechaOriginal(java.sql.Date.valueOf("2026-12-01"));
        cancelada.setReprogramaciones(2);
        String js = cuerpo(get(sesionDe(ana), "/datos", new HashMap<>()));
        assertTrue(js.contains("\"estado\":\"cancelada\"") && js.contains("\"motivoCancelacion\":\"Cambió el viaje\""), js);
        assertTrue(js.contains("\"reprogramaciones\":2") && js.contains("\"fechaOriginal\":\"2026-12-01\""), js);
    }

    @Test
    void guardaElContactoYRechazaDatosInvalidos() throws Exception {
        Contenedor c = sesionDe(ana);
        assertEquals(200, post(c, "/contacto", m("correo", "nuevo@x.pe", "telefono", "987654321")).status);
        assertEquals("nuevo@x.pe", e.usuarios.buscarPorId(ana).get().getCorreo());
        assertEquals(400, post(c, "/contacto", m("correo", "mal", "telefono", "")).status);
        assertEquals("nuevo@x.pe", e.usuarios.buscarPorId(ana).get().getCorreo());
    }

    @Test
    void cambiaLaContrasenaYAceptaLaPolitica() throws Exception {
        Contenedor c = sesionDe(ana);
        assertEquals(403, post(c, "/contrasena", m("actual", "mala", "nueva", "OtraClave9")).status);
        assertEquals(200, post(c, "/contrasena", m("actual", "Clave123", "nueva", "OtraClave9")).status);
        assertEquals(200, post(c, "/politica", m()).status);
        assertTrue(cuerpo(get(c, "", new HashMap<>())).contains("\"politicaAceptada\":true"));
    }

    @Test
    void rutasDesconocidasDan404() throws Exception {
        Contenedor c = sesionDe(ana);
        assertEquals(404, get(c, "/otra", new HashMap<>()).status);
        assertEquals(404, post(c, "/otra", m()).status);
        assertEquals(404, get(c, "/eliminar", new HashMap<>()).status, "eliminar solo existe por POST");
    }

    @Test
    void eliminarConContrasenaMalaNoCierraLaSesionNiToca() throws Exception {
        Contenedor c = sesionDe(ana);
        assertEquals(403, post(c, "/eliminar", m("contrasena", "mala")).status);
        assertTrue(hayUsuario(c));
        assertEquals(0, e.cuentas.llamadasAnonimizar);
    }

    @Test
    void conBloqueosDa409YLaSesionSigueViva() throws Exception {
        e.cuentas.bloqueos.add("tienes pagos pendientes de verificación");
        Contenedor c = sesionDe(ana);
        Resp r = post(c, "/eliminar", m("contrasena", "Clave123"));
        assertEquals(409, r.status);
        assertTrue(cuerpo(r).contains("pagos pendientes"));
        assertTrue(hayUsuario(c));
    }

    @Test
    void alEliminarSeCierranTodasLasSesionesDeEsaCuenta() throws Exception {
        Contenedor aqui = sesionDe(ana);
        Contenedor otroNavegador = sesionDe(ana);
        assertTrue(hayUsuario(otroNavegador));

        Resp r = post(aqui, "/eliminar", m("contrasena", "Clave123"));
        assertEquals(200, r.status);
        assertFalse(hayUsuario(aqui), "la sesión de quien la eliminó se cierra");
        assertFalse(hayUsuario(otroNavegador), "y también la de otro navegador abierto con la misma cuenta");
        assertEquals(401, get(otroNavegador, "", new HashMap<>()).status);
        assertEquals(401, post(otroNavegador, "/contacto", m("correo", "x@x.pe")).status, "esa sesión ya no puede escribir datos");
    }

    @Test
    void elPersonalNoPuedeEliminarSuCuentaYSuSesionSigue() throws Exception {
        Contenedor c = sesionDe(admin);
        assertEquals(403, post(c, "/eliminar", m("contrasena", "Admin123")).status);
        assertTrue(hayUsuario(c));
        assertEquals(200, get(c, "", new HashMap<>()).status, "el personal sí ve y edita sus datos");
    }
}
