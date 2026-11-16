package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.servicio.VerificacionService.Constancia;
import pe.caminoiluminacion.servicio.VerificacionService.Resultado;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/** Constancias de reserva, códigos de verificación y la verificación pública. */
class VerificacionServiceTest {

    VerificacionEntorno e;

    @BeforeEach
    void preparar() throws SQLException {
        e = new VerificacionEntorno();
    }

    private int estado(org.junit.jupiter.api.function.Executable x) {
        return assertThrows(ReglaNegocioException.class, x).getEstado();
    }

    private String id() { return String.valueOf(e.confirmada.getIdReserva()); }

    // ------------------------------------------------------------------ emisión de la constancia

    @Test
    void laConstanciaTraeLosDatosDeLaSolicitudYUnCodigoCON() throws SQLException {
        Constancia k = e.servicio.constancia(e.ana, id());
        assertTrue(k.codigo().matches("^CON-[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}$"), k.codigo());
        assertEquals("Parroquia San José", k.parroquia());
        assertEquals("Bautizo", k.sacramento());
        assertEquals("Bebé Uno Díaz", k.solicitante());
        assertEquals(e.confirmada.getFechaSolicitada(), k.fecha());
        assertNull(k.documentos(), "sin servicio de requisitos no se resume el checklist");
    }

    @Test
    void elCodigoSeCreaUnaVezYDespuesEsSiempreElMismo() throws SQLException {
        String primero = e.servicio.constancia(e.ana, id()).codigo();
        assertEquals(primero, e.servicio.constancia(e.ana, id()).codigo());
        assertEquals(primero, e.servicio.constancia(e.ana, id()).codigo());
        assertEquals(1, e.dao.codigosReserva.size());
    }

    @Test
    void soloElDuenoDeUnaSolicitudConfirmadaPuedeObtenerLaConstancia() throws SQLException {
        assertEquals(404, estado(() -> e.servicio.constancia(e.otra, id())), "la de otra persona no existe para ti");
        assertEquals(404, estado(() -> e.servicio.constancia(e.ana, "999")));
        assertEquals(400, estado(() -> e.servicio.constancia(e.ana, "abc")));
        assertEquals(400, estado(() -> e.servicio.constancia(e.ana, null)));
        for (String estadoReserva : new String[]{"pendiente", "rechazada", "cancelada"}) {
            String idx = String.valueOf(e.reserva(e.ana, "Otro", estadoReserva).getIdReserva());
            assertEquals(409, estado(() -> e.servicio.constancia(e.ana, idx)), "no hay constancia para una solicitud " + estadoReserva);
        }
        assertTrue(e.dao.codigosReserva.isEmpty(), "ninguno de los intentos fallidos creó un código");
    }

    @Test
    void siElCodigoChocaPorAzarSeProbaConOtroYSiNoHayManeraSeFalla() throws SQLException {
        e.dao.colisiones = 2;
        assertNotNull(e.servicio.constancia(e.ana, id()).codigo());
        assertEquals(3, e.dao.llamadas, "dos choques y un acierto");
        VerificacionEntorno otro = new VerificacionEntorno();
        otro.dao.colisiones = 100;
        assertThrows(SQLException.class, () -> otro.servicio.constancia(otro.ana, String.valueOf(otro.confirmada.getIdReserva())));
        assertEquals(5, otro.dao.llamadas, "se desiste después de 5 intentos");
    }

    // ------------------------------------------------------------------ código del recibo

    @Test
    void elCodigoDelReciboEsRECYEstable() throws SQLException {
        e.pagoAprobado(7);
        String c = e.servicio.codigoRecibo(7);
        assertTrue(c.matches("^REC-[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}$"), c);
        assertEquals(c, e.servicio.codigoRecibo(7));
        assertEquals(404, estado(() -> e.servicio.codigoRecibo(99)), "un pago inexistente no recibe código");
        e.dao.colisiones = 1;
        e.pagoAprobado(8);
        assertNotNull(e.servicio.codigoRecibo(8), "también reintenta si el código choca");
    }

    // ------------------------------------------------------------------ verificación pública

    @Test
    void unaConstanciaConfirmadaSeVerificaComoVigenteSinMostrarElNombreCompleto() throws SQLException {
        String codigo = e.servicio.constancia(e.ana, id()).codigo();
        Resultado r = e.servicio.verificar(codigo, "9.9.9.9");
        assertEquals("constancia", r.tipo());
        assertTrue(r.vigente());
        assertEquals("Parroquia San José", r.parroquia());
        assertEquals("Bautizo", r.detalle());
        assertEquals(e.confirmada.getFechaSolicitada().toString(), r.fecha());
        assertEquals("B. U. D.", r.solicitante(), "solo iniciales");
        String todo = r.toString();
        assertFalse(todo.contains("Bebé") || todo.contains("Uno") || todo.contains("Díaz") || todo.contains("70000001") || todo.contains("ana@x.pe"), todo);
    }

    @Test
    void laConstanciaDejaDeSerVigenteSiSeReprogramaOSeCancela() throws SQLException {
        String codigo = e.servicio.constancia(e.ana, id()).codigo();
        e.confirmada.setEstado("pendiente");   // reprogramada: vuelve a pendiente
        Resultado r = e.servicio.verificar(codigo, "ip");
        assertFalse(r.vigente());
        assertTrue(r.estado().contains("reprogramada"), r.estado());
        e.confirmada.setEstado("cancelada");
        r = e.servicio.verificar(codigo, "ip");
        assertFalse(r.vigente());
        assertTrue(r.estado().startsWith("Anulada"), r.estado());
        e.confirmada.setEstado("confirmada");  // y si la parroquia la vuelve a confirmar, vuelve a valer
        assertTrue(e.servicio.verificar(codigo, "ip").vigente());
    }

    @Test
    void unaCuentaEliminadaNoDejaNingunNombreEnLaVerificacion() throws SQLException {
        String codigo = e.servicio.constancia(e.ana, id()).codigo();
        e.confirmada.setNombreSolicitante("(anonimizado)");
        assertEquals("", e.servicio.verificar(codigo, "ip").solicitante());
    }

    @Test
    void unReciboSeVerificaConMontoConceptoYMetodoPeroSinDatosPersonales() throws SQLException {
        e.pagoAprobado(7);
        Resultado r = e.servicio.verificar(e.servicio.codigoRecibo(7), "ip");
        assertEquals("recibo", r.tipo());
        assertTrue(r.vigente());
        assertEquals("S/ 50.00", r.monto());
        assertEquals("Arancel de sacramento (Yape)", r.detalle());
        assertEquals("2026-10-21", r.fecha());
        assertEquals("Parroquia San José", r.parroquia());
        assertNull(r.solicitante(), "un recibo no muestra ningún nombre");
    }

    @Test
    void aceptaElCodigoComoLoEscribeLaPersona() throws SQLException {
        String codigo = e.servicio.constancia(e.ana, id()).codigo();
        String minusculas = codigo.toLowerCase().replace("-", " ");
        assertTrue(e.servicio.verificar("  " + minusculas + " ", "ip").vigente());
        assertTrue(e.servicio.verificar(codigo.replace("-", ""), "ip").vigente());
    }

    @Test
    void unCodigoInvalidoDa400UnoQueNoExisteDa404() {
        // cada llamada con una IP distinta para no mezclar con el límite de intentos (se prueba aparte)
        assertEquals(400, estado(() -> e.servicio.verificar("hola", "ip1")));
        assertEquals(400, estado(() -> e.servicio.verificar(null, "ip2")));
        assertEquals(400, estado(() -> e.servicio.verificar("' OR 1=1 --", "ip3")));
        assertEquals(404, estado(() -> e.servicio.verificar("CON-AAAAA-BBBBB", "ip4")));
        assertEquals(404, estado(() -> e.servicio.verificar("REC-AAAAA-BBBBB", "ip5")));
    }

    @Test
    void elCodigoDeUnReciboNoVerificaUnaConstanciaNiAlReves() throws SQLException {
        e.pagoAprobado(7);
        String recibo = e.servicio.codigoRecibo(7);
        String constancia = e.servicio.constancia(e.ana, id()).codigo();
        assertEquals("recibo", e.servicio.verificar(recibo, "ip").tipo());
        assertEquals("constancia", e.servicio.verificar(constancia, "ip").tipo());
        assertEquals(404, estado(() -> e.servicio.verificar("REC" + constancia.substring(3), "ipX")), "mismo cuerpo con otro prefijo no existe");
    }

    @Test
    void losIntentosFallidosSeLimitanPorIpYLosAciertosNoCuentan() throws SQLException {
        String codigo = e.servicio.constancia(e.ana, id()).codigo();
        for (int i = 0; i < 10; i++) e.servicio.verificar(codigo, "1.1.1.1");   // los aciertos no bloquean
        for (int i = 0; i < 3; i++) estado(() -> e.servicio.verificar("CON-AAAAA-BBBBB", "1.1.1.1"));
        assertEquals(429, estado(() -> e.servicio.verificar(codigo, "1.1.1.1")), "bloqueada: ni siquiera uno válido");
        assertTrue(e.servicio.verificar(codigo, "2.2.2.2").vigente(), "otra IP no se ve afectada");
        e.ahoraMs[0] = 121_000;
        assertTrue(e.servicio.verificar(codigo, "1.1.1.1").vigente(), "el bloqueo termina");
    }

    @Test
    void tambienCuentanLosCodigosMalEscritos() {
        for (int i = 0; i < 3; i++) estado(() -> e.servicio.verificar("basura", "3.3.3.3"));
        assertEquals(429, estado(() -> e.servicio.verificar("CON-AAAAA-BBBBB", "3.3.3.3")));
    }

    // ------------------------------------------------------------------ iniciales

    @Test
    void lasInicialesNuncaDejanUnNombreCompleto() {
        assertEquals("A. T. D.", VerificacionService.iniciales("Ana Torres Díaz"));
        assertEquals("A. T.", VerificacionService.iniciales("  ana   torres "));
        assertEquals("Á. Ñ.", VerificacionService.iniciales("Ángel Ñañez"));
        assertEquals("", VerificacionService.iniciales("(Jorge) 123"), "un paréntesis al inicio es el marcador de cuenta eliminada");
        assertEquals("", VerificacionService.iniciales("(anonimizado)"));
        assertEquals("", VerificacionService.iniciales(null));
        assertEquals("", VerificacionService.iniciales("  "));
        assertEquals("", VerificacionService.iniciales("123 456"));
    }
}
