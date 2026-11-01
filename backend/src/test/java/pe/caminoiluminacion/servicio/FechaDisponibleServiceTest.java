package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.controlador.Pruebas.FakeFechas;
import pe.caminoiluminacion.controlador.Pruebas.FakeReservas;
import pe.caminoiluminacion.controlador.Pruebas.FakeSacramentos;
import pe.caminoiluminacion.modelo.FechaDisponible;
import pe.caminoiluminacion.modelo.Reserva;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FechaDisponibleServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 8);

    private FakeReservas reservas;
    private FakeFechas fechas;
    private FakeBitacora bitacoraDao;
    private FechaDisponibleService servicio;

    @BeforeEach
    void preparar() {
        reservas = new FakeReservas();
        fechas = new FakeFechas(reservas);
        bitacoraDao = new FakeBitacora();
        servicio = new FechaDisponibleService(fechas, new FakeSacramentos(), new BitacoraService(bitacoraDao), () -> HOY);
    }

    private FechaDisponible crear(String sacramento, String fecha, String hora, String cupo) throws SQLException {
        return servicio.guardar(9, 1, "", sacramento, fecha, hora, cupo, "ip");
    }

    private int estado(org.junit.jupiter.api.function.Executable accion) {
        return assertThrows(ReglaNegocioException.class, accion).getEstado();
    }

    private void reservar(String sacramento, String fecha, String estadoReserva) {
        Reserva r = new Reserva(1, 1, sacramento, "Ana", Date.valueOf(fecha), null);
        r.setEstado(estadoReserva);
        reservas.insertar(r);
    }

    @Test
    void publicaUnaFechaConCupoYHoraYRegistraEnBitacora() throws SQLException {
        FechaDisponible f = crear("bautizo", "2026-11-14", "16:30", "5");
        assertEquals(LocalDate.parse("2026-11-14"), f.getFecha());
        assertEquals(LocalTime.of(16, 30), f.getHoraInicio());
        assertEquals(5, f.getCupo());
        assertTrue(bitacoraDao.hay("FECHA_DISPONIBLE_CREADA"));
        assertEquals(1, servicio.listar(1).size());
    }

    @Test
    void laHoraEsOpcional() throws SQLException {
        assertNull(crear("bautizo", "2026-11-14", "", "1").getHoraInicio());
        assertNull(crear("matrimonio", "2026-11-14", null, "1").getHoraInicio());
    }

    @Test
    void validaSacramentoFechaHoraYCupo() {
        assertEquals(400, estado(() -> crear("inventado", "2026-11-14", "", "1")));
        assertEquals(400, estado(() -> crear("confirmacion", "2026-11-14", "", "1")), "San José no ofrece Confirmación");
        assertEquals(400, estado(() -> crear("bautizo", "2026-10-08", "", "1")), "hoy no cuenta");
        assertEquals(400, estado(() -> crear("bautizo", "2026-10-01", "", "1")));
        assertEquals(400, estado(() -> crear("bautizo", "no-es-fecha", "", "1")));
        assertEquals(400, estado(() -> crear("bautizo", "2028-10-09", "", "1")), "más de 2 años");
        assertEquals(400, estado(() -> crear("bautizo", "2026-11-14", "25:99", "1")));
        assertEquals(400, estado(() -> crear("bautizo", "2026-11-14", "", "0")));
        assertEquals(400, estado(() -> crear("bautizo", "2026-11-14", "", "201")));
        assertEquals(400, estado(() -> crear("bautizo", "2026-11-14", "", "abc")));
        assertEquals(400, estado(() -> crear("bautizo", "2026-11-14", "", null)));
        assertTrue(fechas.lista.isEmpty());
    }

    @Test
    void noSePuedePublicarDosVecesLaMismaFechaDelMismoSacramento() throws SQLException {
        crear("bautizo", "2026-11-14", "", "2");
        assertEquals(409, estado(() -> crear("bautizo", "2026-11-14", "", "3")));
        crear("matrimonio", "2026-11-14", "", "1");   // otro sacramento: sí
        assertEquals(2, fechas.lista.size());
    }

    @Test
    void modificarAjustaCupoYHoraPeroNoLaFechaNiElSacramento() throws SQLException {
        FechaDisponible f = crear("bautizo", "2026-11-14", "", "2");
        servicio.guardar(9, 1, String.valueOf(f.getIdFecha()), "matrimonio", "2027-01-01", "10:00", "4", "ip");
        FechaDisponible ahora = fechas.buscarPorId(f.getIdFecha()).get();
        assertEquals(4, ahora.getCupo());
        assertEquals(LocalTime.of(10, 0), ahora.getHoraInicio());
        assertEquals(LocalDate.parse("2026-11-14"), ahora.getFecha());
        assertEquals("bautizo", ahora.getCodigoSacramento());
        assertTrue(bitacoraDao.hay("FECHA_DISPONIBLE_MODIFICADA"));
    }

    @Test
    void elCupoNoPuedeBajarPorDebajoDeLasSolicitudesActivas() throws SQLException {
        FechaDisponible f = crear("bautizo", "2026-11-14", "", "3");
        reservar("bautizo", "2026-11-14", "pendiente");
        reservar("bautizo", "2026-11-14", "confirmada");
        reservar("bautizo", "2026-11-14", "rechazada");   // no cuenta
        String id = String.valueOf(f.getIdFecha());
        assertEquals(409, estado(() -> servicio.guardar(9, 1, id, "", "", "", "1", "ip")));
        servicio.guardar(9, 1, id, "", "", "", "2", "ip");
        assertEquals(2, fechas.buscarPorId(f.getIdFecha()).get().getCupo());
    }

    @Test
    void eliminaSoloFechasSinSolicitudesActivas() throws SQLException {
        FechaDisponible libre = crear("bautizo", "2026-11-14", "", "2");
        FechaDisponible ocupada = crear("bautizo", "2026-11-21", "", "2");
        reservar("bautizo", "2026-11-21", "pendiente");
        assertEquals(409, estado(() -> servicio.eliminar(9, 1, String.valueOf(ocupada.getIdFecha()), "ip")));
        servicio.eliminar(9, 1, String.valueOf(libre.getIdFecha()), "ip");
        assertEquals(1, fechas.lista.size());
        assertTrue(bitacoraDao.hay("FECHA_DISPONIBLE_ELIMINADA"));
    }

    @Test
    void unaSolicitudRechazadaPermiteRetirarLaFecha() throws SQLException {
        FechaDisponible f = crear("bautizo", "2026-11-14", "", "1");
        reservar("bautizo", "2026-11-14", "rechazada");
        servicio.eliminar(9, 1, String.valueOf(f.getIdFecha()), "ip");
        assertTrue(fechas.lista.isEmpty());
    }

    @Test
    void unaFechaDeOtraParroquiaSeTrataComoInexistente() throws SQLException {
        FechaDisponible f = crear("bautizo", "2026-11-14", "", "1");
        String id = String.valueOf(f.getIdFecha());
        assertEquals(404, estado(() -> servicio.eliminar(9, 2, id, "ip")));
        assertEquals(404, estado(() -> servicio.guardar(9, 2, id, "", "", "", "2", "ip")));
        assertEquals(404, estado(() -> servicio.eliminar(9, 1, "999", "ip")));
        assertEquals(400, estado(() -> servicio.eliminar(9, 1, "abc", "ip")));
        assertEquals(1, fechas.lista.size());
    }

    @Test
    void listarMuestraSoloDesdeHoyYDeSuParroquia() throws SQLException {
        fechas.insertar(new FechaDisponible(1, 1, LocalDate.parse("2026-10-01"), null, 1));   // pasada
        fechas.insertar(new FechaDisponible(2, 1, LocalDate.parse("2026-11-14"), null, 1));   // otra parroquia
        crear("bautizo", "2026-11-14", "", "1");
        List<FechaDisponible> lista = servicio.listar(1);
        assertEquals(1, lista.size());
        assertEquals(1, lista.get(0).getIdParroquia().intValue());
    }
}
