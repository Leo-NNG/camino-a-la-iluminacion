package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas.Contenedor;
import pe.caminoiluminacion.controlador.Pruebas.Resp;
import pe.caminoiluminacion.servicio.VerificacionEntorno;
import pe.caminoiluminacion.util.Sesion;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pe.caminoiluminacion.controlador.Pruebas.cuerpo;
import static pe.caminoiluminacion.controlador.Pruebas.m;

/** Constancia en PDF (feligrés) y verificación pública de códigos. */
class VerificacionServletsTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-10T15:30:00Z"), ZoneId.of("UTC"));

    VerificacionEntorno e;
    ConstanciaServlet constancia;
    VerificarServlet verificar;
    int idAdmin;

    @BeforeEach
    void preparar() throws Exception {
        e = new VerificacionEntorno();
        constancia = new ConstanciaServlet(e.servicio, RELOJ);
        verificar = new VerificarServlet(e.servicio);
        idAdmin = e.usuarios.insertar(Pruebas.usuario("70000010", "adm@x.pe", "Admin123", "administrador", 1)).getIdUsuario();
    }

    // ---- ayudas: petición con host y respuesta que guarda cabeceras y bytes

    private static Object invocar(Object destino, java.lang.reflect.Method metodo, Object[] args) throws Throwable {
        try {
            return metodo.invoke(destino, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private static HttpServletRequest conHost(HttpServletRequest base) {
        return (HttpServletRequest) Proxy.newProxyInstance(VerificacionServletsTest.class.getClassLoader(), new Class[]{HttpServletRequest.class}, (o, met, args) -> switch (met.getName()) {
            case "getScheme" -> "http";
            case "getServerName" -> "iglesia.test";
            case "getServerPort" -> 8080;
            default -> invocar(base, met, args);
        });
    }

    static class Salida { Map<String, String> cabeceras = new HashMap<>(); String tipo; ByteArrayOutputStream bytes = new ByteArrayOutputStream(); }

    private static HttpServletResponse conSalida(Resp r, Salida s) {
        HttpServletResponse base = Pruebas.resp(r);
        return (HttpServletResponse) Proxy.newProxyInstance(VerificacionServletsTest.class.getClassLoader(), new Class[]{HttpServletResponse.class}, (o, met, args) -> switch (met.getName()) {
            case "setHeader" -> { s.cabeceras.put((String) args[0], (String) args[1]); yield null; }
            case "setContentType" -> { s.tipo = (String) args[0]; yield null; }
            case "setContentLength" -> null;
            case "getOutputStream" -> new ServletOutputStream() {
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(WriteListener l) { }
                @Override public void write(int b) { s.bytes.write(b); }
                @Override public void write(byte[] b, int off, int len) { s.bytes.write(b, off, len); }
            };
            default -> invocar(base, met, args);
        });
    }

    private Contenedor sesion(int id) {
        Contenedor c = new Contenedor();
        Sesion.iniciar(Pruebas.req(c, m(), "/"), e.usuarios.buscarPorId(id).get());
        return c;
    }

    private Resp pedirConstancia(Contenedor c, Map<String, String> p, Salida s) throws Exception {
        Resp r = new Resp();
        constancia.doGet(conHost(Pruebas.req(c, p, "/app/api/reservas/constancia")), conSalida(r, s));
        return r;
    }

    private Resp pedirVerificacion(String codigo, String ipIgnorada) throws Exception {
        Resp r = new Resp();
        verificar.doGet(Pruebas.req(new Contenedor(), m("codigo", codigo), "/app/api/verificar"), conSalida(r, new Salida()));
        return r;
    }

    private String id() { return String.valueOf(e.confirmada.getIdReserva()); }

    // ---- constancia

    @Test
    void laConstanciaExigeSesionDeFeligres() throws Exception {
        assertEquals(401, pedirConstancia(new Contenedor(), m("id", id()), new Salida()).status);
        assertEquals(403, pedirConstancia(sesion(idAdmin), m("id", id()), new Salida()).status, "el personal no descarga constancias de feligreses");
        assertTrue(e.dao.codigosReserva.isEmpty(), "sin sesión no se crea ningún código");
    }

    @Test
    void laDeOtraPersonaYLaNoConfirmadaNoSeEntregan() throws Exception {
        assertEquals(404, pedirConstancia(sesion(e.otra), m("id", id()), new Salida()).status);
        assertEquals(400, pedirConstancia(sesion(e.ana), m("id", "abc"), new Salida()).status);
        String pendiente = String.valueOf(e.reserva(e.ana, "Otro", "pendiente").getIdReserva());
        Resp r = pedirConstancia(sesion(e.ana), m("id", pendiente), new Salida());
        assertEquals(409, r.status);
        assertTrue(cuerpo(r).contains("confirma"), cuerpo(r));
    }

    @Test
    void laConstanciaSaleComoPdfConCabecerasSegurasYElCodigoYLaDireccionDeVerificacion() throws Exception {
        Salida s = new Salida();
        Resp r = pedirConstancia(sesion(e.ana), m("id", id()), s);
        assertEquals(200, r.status);
        assertEquals("application/pdf", s.tipo);
        assertEquals("nosniff", s.cabeceras.get("X-Content-Type-Options"));
        assertEquals("private, no-store", s.cabeceras.get("Cache-Control"));
        assertEquals("default-src 'none'", s.cabeceras.get("Content-Security-Policy"));
        String codigo = e.dao.codigosReserva.get(e.confirmada.getIdReserva());
        assertTrue(s.cabeceras.get("Content-Disposition").contains("constancia-" + codigo + ".pdf"), s.cabeceras.get("Content-Disposition"));
        String pdf = new String(s.bytes.toByteArray(), StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF-1.4") && pdf.trim().endsWith("%%EOF"));
        assertTrue(pdf.contains(codigo));
        assertTrue(pdf.contains("http://iglesia.test:8080/app/paginas/verificar.html?codigo=" + codigo), "la dirección usa el host y el contexto de la petición");
    }

    @Test
    void descargarDosVecesDaElMismoCodigo() throws Exception {
        pedirConstancia(sesion(e.ana), m("id", id()), new Salida());
        String primero = e.dao.codigosReserva.get(e.confirmada.getIdReserva());
        Salida s = new Salida();
        pedirConstancia(sesion(e.ana), m("id", id()), s);
        assertEquals(primero, e.dao.codigosReserva.get(e.confirmada.getIdReserva()));
        assertTrue(new String(s.bytes.toByteArray(), StandardCharsets.ISO_8859_1).contains(primero));
    }

    // ---- verificación pública

    @Test
    void laVerificacionNoPideSesionYNoRevelaDatosPersonales() throws Exception {
        pedirConstancia(sesion(e.ana), m("id", id()), new Salida());
        String codigo = e.dao.codigosReserva.get(e.confirmada.getIdReserva());
        Resp r = pedirVerificacion(codigo, "ip");   // petición sin ninguna sesión
        String js = cuerpo(r);
        assertEquals(200, r.status);
        assertTrue(js.contains("\"tipo\":\"constancia\"") && js.contains("\"vigente\":true") && js.contains("\"solicitante\":\"B. U. D.\""), js);
        assertTrue(js.contains("\"parroquia\":\"Parroquia San José\"") && js.contains("\"detalle\":\"Bautizo\""), js);
        assertFalse(js.contains("Bebé") || js.contains("Díaz") || js.contains("70000001") || js.contains("ana@x.pe"), "ningún nombre completo ni dato de contacto");
    }

    @Test
    void laVerificacionDiceSiLaConstanciaYaNoVale() throws Exception {
        pedirConstancia(sesion(e.ana), m("id", id()), new Salida());
        String codigo = e.dao.codigosReserva.get(e.confirmada.getIdReserva());
        e.confirmada.setEstado("cancelada");
        String js = cuerpo(pedirVerificacion(codigo, "ip"));
        assertTrue(js.contains("\"vigente\":false") && js.contains("Anulada"), js);
    }

    @Test
    void unReciboSeVerificaSinNombreNiCelular() throws Exception {
        e.pagoAprobado(7);
        String codigo = e.servicio.codigoRecibo(7);
        String js = cuerpo(pedirVerificacion(codigo, "ip"));
        assertTrue(js.contains("\"tipo\":\"recibo\"") && js.contains("\"monto\":\"S/ 50.00\"") && js.contains("\"vigente\":true"), js);
        assertTrue(js.contains("\"solicitante\":\"\""), "un recibo no lleva nombre");
    }

    @Test
    void erroresDeLaVerificacion() throws Exception {
        Resp mal = pedirVerificacion("hola", "ip");
        assertEquals(400, mal.status);
        Resp noExiste = new Resp();
        verificar.doGet(Pruebas.req(new Contenedor(), m("codigo", "CON-AAAAA-BBBBB"), "/"), Pruebas.resp(noExiste));
        assertEquals(404, noExiste.status);
        assertTrue(cuerpo(noExiste).contains("No encontramos"));
    }

    @Test
    void trasVariosIntentosFallidosLaIpQuedaBloqueada() throws Exception {
        for (int i = 0; i < 3; i++) pedirVerificacion("CON-AAAAA-BBBBB", "ip");   // todas llegan con la IP 1.2.3.4 del fake
        pedirConstancia(sesion(e.ana), m("id", id()), new Salida());
        String codigo = e.dao.codigosReserva.get(e.confirmada.getIdReserva());
        Resp r = pedirVerificacion(codigo, "ip");
        assertEquals(429, r.status, "bloqueada aunque el código sea válido");
    }
}
