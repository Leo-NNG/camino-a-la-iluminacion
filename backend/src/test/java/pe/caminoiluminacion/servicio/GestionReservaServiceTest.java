package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.servicio.GestionReservaService.Cancelacion;
import pe.caminoiluminacion.servicio.GestionReservaService.Opciones;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static pe.caminoiluminacion.servicio.GestionEntorno.HOY;

/** Cancelar y reprogramar solicitudes de sacramento. */
class GestionReservaServiceTest {

    GestionEntorno e;

    @BeforeEach
    void preparar() throws SQLException {
        e = new GestionEntorno();
    }

    private int estado(org.junit.jupiter.api.function.Executable x) {
        return assertThrows(ReglaNegocioException.class, x).getEstado();
    }

    private String id(Reserva r) { return String.valueOf(r.getIdReserva()); }

    private String fecha(int dias) { return HOY.plusDays(dias).toString(); }

    // ------------------------------------------------------------------ qué se puede hacer

    @Test
    void unaSolicitudPendienteVigenteSePuedeCancelarYReprogramar() {
        assertEquals(new Opciones(true, true), e.servicio.opciones(e.r));
    }

    @Test
    void laConfirmadaExigeDosDiasDeAnticipacionPeroLaPendienteNo() {
        Reserva lejana = e.reserva(e.ana, "matrimonio", HOY.plusDays(2), "confirmada");
        Reserva cerca = e.reserva(e.ana, "matrimonio", HOY.plusDays(1), "confirmada");
        Reserva pendienteManana = e.reserva(e.ana, "matrimonio", HOY.plusDays(1), "pendiente");
        assertEquals(new Opciones(true, true), e.servicio.opciones(lejana));
        assertEquals(new Opciones(false, false), e.servicio.opciones(cerca), "confirmada a menos de 2 días: se coordina con la parroquia");
        assertEquals(new Opciones(true, true), e.servicio.opciones(pendienteManana), "una pendiente aún no está comprometida");
    }

    @Test
    void lasPasadasRechazadasYCanceladasNoSeGestionan() {
        assertEquals(new Opciones(false, false), e.servicio.opciones(e.reserva(e.ana, "bautizo", HOY.minusDays(1), "pendiente")));
        assertEquals(new Opciones(false, false), e.servicio.opciones(e.reserva(e.ana, "bautizo", HOY.plusDays(30), "rechazada")));
        assertEquals(new Opciones(false, false), e.servicio.opciones(e.reserva(e.ana, "bautizo", HOY.plusDays(30), "cancelada")));
        assertEquals(new Opciones(true, true), e.servicio.opciones(e.reserva(e.ana, "bautizo", HOY, "pendiente")), "el mismo día todavía vale si sigue pendiente");
    }

    @Test
    void alAgotarLasReprogramacionesSoloQuedaCancelar() {
        e.r.setReprogramaciones(GestionReservaService.MAX_REPROGRAMACIONES);
        assertEquals(new Opciones(true, false), e.servicio.opciones(e.r));
    }

    // ------------------------------------------------------------------ cancelar

    @Test
    void cancelaGuardaElMotivoRecortadoYQuedaEnBitacora() throws SQLException {
        Cancelacion c = e.servicio.cancelar(e.ana, id(e.r), "  Cambió la fecha del viaje  ", "ip");
        assertFalse(c.arancelPagado());
        assertEquals("cancelada", e.r.getEstado());
        assertEquals("Cambió la fecha del viaje", e.r.getMotivoCancelacion());
        assertNotNull(e.r.getCanceladaEn());
        assertTrue(e.bitacoraDao.hay("RESERVA_CANCELADA"));
    }

    @Test
    void elMotivoEsOpcional() throws SQLException {
        e.servicio.cancelar(e.ana, id(e.r), "   ", "ip");
        assertNull(e.r.getMotivoCancelacion());
        assertEquals("cancelada", e.r.getEstado());
    }

    @Test
    void cancelarLiberaElCupoDeLaFecha() throws SQLException {
        Reserva otraPersona = new Reserva(e.otra, 1, "bautizo", "Otro bebé", e.r.getFechaSolicitada(), null);
        assertFalse(e.reservas.insertarSiDisponible(otraPersona, 1), "con la solicitud de Ana la fecha está ocupada");
        e.servicio.cancelar(e.ana, id(e.r), null, "ip");
        assertTrue(e.reservas.insertarSiDisponible(otraPersona, 1), "al cancelar, otra persona puede tomarla");
    }

    @Test
    void sePuedeCancelarUnaConfirmadaConAnticipacion() throws SQLException {
        Reserva conf = e.reserva(e.ana, "matrimonio", HOY.plusDays(10), "confirmada");
        e.servicio.cancelar(e.ana, id(conf), null, "ip");
        assertEquals("cancelada", conf.getEstado());
    }

    @Test
    void soloElDuenoPuedeCancelarYLosDatosSeValidan() throws SQLException {
        assertEquals(404, estado(() -> e.servicio.cancelar(e.otra, id(e.r), null, "ip")), "la solicitud de otra persona no existe para ti");
        assertEquals(404, estado(() -> e.servicio.cancelar(e.ana, "999", null, "ip")));
        assertEquals(400, estado(() -> e.servicio.cancelar(e.ana, "abc", null, "ip")));
        assertEquals(400, estado(() -> e.servicio.cancelar(e.ana, null, null, "ip")));
        assertEquals(400, estado(() -> e.servicio.cancelar(e.ana, id(e.r), "m".repeat(256), "ip")));
        assertEquals("pendiente", e.r.getEstado(), "nada cambió");
    }

    @Test
    void noSeCancelaLoQueYaNoEstaVigente() throws SQLException {
        e.servicio.cancelar(e.ana, id(e.r), null, "ip");
        assertEquals(409, estado(() -> e.servicio.cancelar(e.ana, id(e.r), null, "ip")), "no se cancela dos veces");
        assertEquals(409, estado(() -> e.servicio.cancelar(e.ana, id(e.reserva(e.ana, "bautizo", HOY.plusDays(30), "rechazada")), null, "ip")));
        assertEquals(409, estado(() -> e.servicio.cancelar(e.ana, id(e.reserva(e.ana, "bautizo", HOY.minusDays(3), "pendiente")), null, "ip")));
        ReglaNegocioException cerca = assertThrows(ReglaNegocioException.class,
                () -> e.servicio.cancelar(e.ana, id(e.reserva(e.ana, "matrimonio", HOY.plusDays(1), "confirmada")), null, "ip"));
        assertEquals(409, cerca.getEstado());
        assertTrue(cerca.getMessage().contains("parroquia"), "se le dice que se comunique con la parroquia");
    }

    @Test
    void conUnPagoPendienteNoSeCancelaPeroConUnoAprobadoSiYSeAvisaDelArancel() throws SQLException {
        Pago pendiente = e.pago(e.r, Pago.PENDIENTE);
        assertEquals(409, estado(() -> e.servicio.cancelar(e.ana, id(e.r), null, "ip")));
        assertEquals("pendiente", e.r.getEstado());
        e.pagos.resolverPendiente(pendiente.idPago(), Pago.APROBADO, null, 9);
        Cancelacion c = e.servicio.cancelar(e.ana, id(e.r), null, "ip");
        assertTrue(c.arancelPagado(), "ya pagó: la parroquia coordina el reembolso o el uso del pago");
        assertEquals("cancelada", e.r.getEstado());
    }

    @Test
    void unPagoRechazadoNoImpideCancelar() throws SQLException {
        e.pago(e.r, Pago.RECHAZADO);
        assertFalse(e.servicio.cancelar(e.ana, id(e.r), null, "ip").arancelPagado());
    }

    // ------------------------------------------------------------------ reprogramar

    @Test
    void conFechaLibreCambiaLaFechaGuardaLaOriginalYQuedaPendiente() throws SQLException {
        Date original = e.r.getFechaSolicitada();
        Reserva r = e.servicio.reprogramar(e.ana, id(e.r), fecha(55), "ip");
        assertEquals(HOY.plusDays(55), r.getFechaSolicitada().toLocalDate());
        assertEquals(original, r.getFechaOriginal());
        assertEquals(1, r.getReprogramaciones());
        assertEquals("pendiente", r.getEstado());
        assertTrue(e.bitacoraDao.hay("RESERVA_REPROGRAMADA"));
    }

    @Test
    void unaConfirmadaVuelveAPendienteYSeBorraUnMotivoViejo() throws SQLException {
        Reserva conf = e.reserva(e.ana, "matrimonio", HOY.plusDays(10), "confirmada");
        conf.setMotivoRechazo("algo anterior");
        Reserva r = e.servicio.reprogramar(e.ana, id(conf), fecha(20), "ip");
        assertEquals("pendiente", r.getEstado(), "la parroquia debe validar la fecha nueva");
        assertNull(r.getMotivoRechazo());
    }

    @Test
    void laFechaOriginalEsSiempreLaPrimeraYHayUnMaximoDeReprogramaciones() throws SQLException {
        Date primera = e.r.getFechaSolicitada();
        e.servicio.reprogramar(e.ana, id(e.r), fecha(50), "ip");
        e.servicio.reprogramar(e.ana, id(e.r), fecha(60), "ip");
        assertEquals(primera, e.r.getFechaOriginal(), "no se pisa con la segunda");
        assertEquals(2, e.r.getReprogramaciones());
        assertEquals(409, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), fecha(70), "ip")));
        assertEquals(HOY.plusDays(60), e.r.getFechaSolicitada().toLocalDate(), "la tercera no cambió nada");
    }

    @Test
    void conFechasPublicadasSoloValeUnaDeEllasConCupo() throws SQLException {
        e.publicar(HOY.plusDays(50), 2);
        e.publicar(HOY.plusDays(60), 1);
        assertEquals(400, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), fecha(55), "ip")), "no es una fecha publicada");
        e.reserva(e.otra, "bautizo", HOY.plusDays(60), "pendiente");   // llena la de cupo 1
        assertEquals(409, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), fecha(60), "ip")), "esa fecha ya no tiene cupo");
        assertEquals(HOY.plusDays(50), e.servicio.reprogramar(e.ana, id(e.r), fecha(50), "ip").getFechaSolicitada().toLocalDate());
    }

    @Test
    void sinFechasPublicadasUnaFechaOcupadaPorOtraSolicitudDaConflicto() throws SQLException {
        e.reserva(e.otra, "bautizo", HOY.plusDays(33), "confirmada");
        assertEquals(409, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), fecha(33), "ip")));
        e.reserva(e.otra, "bautizo", HOY.plusDays(34), "rechazada");  // una rechazada no ocupa
        assertEquals(HOY.plusDays(34), e.servicio.reprogramar(e.ana, id(e.r), fecha(34), "ip").getFechaSolicitada().toLocalDate());
    }

    @Test
    void laFechaNuevaSigueLasMismasReglasQueAlCrear() throws SQLException {
        assertEquals(400, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), fecha(40), "ip")), "igual a la actual");
        assertEquals(400, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), fecha(0), "ip")), "hoy no es futuro");
        assertEquals(400, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), fecha(-5), "ip")), "pasada");
        assertEquals(400, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), HOY.plusYears(2).plusDays(1).toString(), "ip")), "más de 2 años");
        assertEquals(400, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), "mañana", "ip")));
        assertEquals(400, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), "", "ip")));
        assertEquals(400, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), null, "ip")));
        assertEquals(HOY.plusDays(40), e.r.getFechaSolicitada().toLocalDate(), "ninguna cambió la fecha");
        assertEquals(0, e.r.getReprogramaciones());
    }

    @Test
    void soloElDuenoReprogramaYNoSePuedeSiNoEstaVigente() throws SQLException {
        assertEquals(404, estado(() -> e.servicio.reprogramar(e.otra, id(e.r), fecha(50), "ip")));
        assertEquals(404, estado(() -> e.servicio.reprogramar(e.ana, "999", fecha(50), "ip")));
        assertEquals(400, estado(() -> e.servicio.reprogramar(e.ana, "x", fecha(50), "ip")));
        e.servicio.cancelar(e.ana, id(e.r), null, "ip");
        assertEquals(409, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), fecha(50), "ip")), "una cancelada no se reprograma");
        Reserva cerca = e.reserva(e.ana, "matrimonio", HOY.plusDays(1), "confirmada");
        assertEquals(409, estado(() -> e.servicio.reprogramar(e.ana, id(cerca), fecha(50), "ip")));
    }

    @Test
    void siLaParroquiaYaNoOfreceElSacramentoNoSeReprograma() {
        e.sacramentos.ofrece.get(1).remove(1);
        assertEquals(400, estado(() -> e.servicio.reprogramar(e.ana, id(e.r), fecha(50), "ip")));
    }

    @Test
    void reprogramarNoTocaOtrosDatosDeLaSolicitud() throws SQLException {
        e.r.setNotas("nota");
        Reserva r = e.servicio.reprogramar(e.ana, id(e.r), fecha(50), "ip");
        assertEquals("nota", r.getNotas());
        assertEquals(e.r.getIdReserva(), r.getIdReserva(), "es la misma solicitud: conserva documentos y pago");
    }

    @Test
    void unaReprogramadaSigueOcupandoSuFechaNuevaYLiberaLaAnterior() throws SQLException {
        Date vieja = e.r.getFechaSolicitada();
        e.servicio.reprogramar(e.ana, id(e.r), fecha(50), "ip");
        assertTrue(e.reservas.insertarSiDisponible(new Reserva(e.otra, 1, "bautizo", "Otro", vieja, null), 1), "la fecha anterior quedó libre");
        assertFalse(e.reservas.insertarSiDisponible(new Reserva(e.otra, 1, "bautizo", "Otro", Date.valueOf(HOY.plusDays(50)), null), 1), "la nueva quedó ocupada");
    }
}
