package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.controlador.Pruebas.FakeFechas;
import pe.caminoiluminacion.controlador.Pruebas.FakeParroquias;
import pe.caminoiluminacion.controlador.Pruebas.FakeReservas;
import pe.caminoiluminacion.controlador.Pruebas.FakeSacramentos;
import pe.caminoiluminacion.controlador.Pruebas.FakeUsuarios;
import pe.caminoiluminacion.controlador.Pruebas;
import pe.caminoiluminacion.modelo.FechaDisponible;
import pe.caminoiluminacion.modelo.Reserva;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservaServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 8);

    private FakeReservas reservas;
    private FakeBitacora bitacoraDao;
    private FakeSacramentos sacramentos;
    private FakeFechas fechas;
    private ReservaService servicio;

    @BeforeEach
    void preparar() throws SQLException {
        reservas = new FakeReservas();
        bitacoraDao = new FakeBitacora();
        sacramentos = new FakeSacramentos();
        FakeUsuarios usuarios = new FakeUsuarios();
        usuarios.insertar(Pruebas.usuario("70000001", "ana@x.pe", "Clave123", "feligres", null));
        fechas = new FakeFechas(reservas);
        servicio = new ReservaService(reservas, fechas, new FakeParroquias(), sacramentos, usuarios, new BitacoraService(bitacoraDao), () -> HOY);
    }

    private Reserva crear(String sacramento, String parroquia, String fecha) throws SQLException {
        return servicio.crear(1, sacramento, parroquia, "Bebé Uno", fecha, "", "1.1.1.1");
    }

    private int estadoDelError(String sacramento, String parroquia, String fecha) {
        return assertThrows(ReglaNegocioException.class, () -> crear(sacramento, parroquia, fecha)).getEstado();
    }

    @Test
    void creaLaReservaYRegistraEnBitacora() throws SQLException {
        Reserva r = crear("Bautizo", "1", "2026-10-20");
        assertEquals("bautizo", r.getSacramento());
        assertEquals("pendiente", r.getEstado());
        assertEquals(1, reservas.lista.size());
        assertTrue(bitacoraDao.hay("RESERVA_CREADA"));
    }

    @Test
    void aceptaElSacramentoPorCodigoONombreSinImportarTildesNiMayusculas() throws SQLException {
        assertEquals("primera_comunion", crear("Primera Comunión", "1", "2026-11-01").getSacramento());
        assertEquals("primera_comunion", crear("primera comunion", "1", "2026-11-02").getSacramento());
        assertEquals("primera_comunion", crear("PRIMERA_COMUNION", "1", "2026-11-03").getSacramento());
        assertEquals("confirmacion", crear("Confirmación", "2", "2026-11-04").getSacramento());
    }

    @Test
    void rechazaSacramentosInexistentes() {
        assertEquals(400, estadoDelError("Inventado", "1", "2026-10-20"));
        assertEquals(400, estadoDelError("", "1", "2026-10-20"));
        assertEquals(400, estadoDelError(null, "1", "2026-10-20"));
    }

    @Test
    void laFechaDebeSerFuturaYDentroDeDosAnios() throws SQLException {
        assertEquals(400, estadoDelError("Bautizo", "1", "2026-10-08"), "hoy no cuenta");
        assertEquals(400, estadoDelError("Bautizo", "1", "2026-10-07"));
        assertEquals(400, estadoDelError("Bautizo", "1", "no-es-fecha"));
        assertEquals(400, estadoDelError("Bautizo", "1", "2026-02-30"));
        assertEquals(400, estadoDelError("Bautizo", "1", "2028-10-09"), "un día más de 2 años");
        assertEquals("2026-10-09", crear("Bautizo", "1", "2026-10-09").getFechaSolicitada().toString(), "mañana sí");
        assertEquals("2028-10-08", crear("Matrimonio", "1", "2028-10-08").getFechaSolicitada().toString(), "exactamente 2 años sí");
    }

    @Test
    void laParroquiaDebeExistirYOfrecerElSacramento() {
        assertEquals(400, estadoDelError("Bautizo", "99", "2026-10-20"));
        assertEquals(400, estadoDelError("Bautizo", "abc", "2026-10-20"));
        assertEquals(400, estadoDelError("Bautizo", null, "2026-10-20"));
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> crear("Matrimonio", "2", "2026-10-20"));
        assertTrue(e.getMessage().contains("no ofrece"));
    }

    @Test
    void sigueLaOfertaActualDeLaParroquia() throws SQLException {
        assertEquals(400, estadoDelError("Matrimonio", "2", "2026-10-20"));
        sacramentos.reemplazarDeParroquia(2, List.of(1, 2, 4));
        assertEquals("matrimonio", crear("Matrimonio", "2", "2026-10-20").getSacramento());
    }

    @Test
    void dosSolicitudesDelMismoSacramentoParroquiaYFechaChocan() throws SQLException {
        crear("Bautizo", "1", "2026-10-20");
        assertEquals(409, estadoDelError("Bautizo", "1", "2026-10-20"));
        crear("Matrimonio", "1", "2026-10-20");   // otro sacramento: sí
        crear("Bautizo", "2", "2026-10-20");      // otra parroquia: sí
        crear("Bautizo", "1", "2026-10-21");      // otra fecha: sí
        assertEquals(4, reservas.lista.size());
    }

    @Test
    void unaSolicitudRechazadaLiberaLaFecha() throws SQLException {
        Reserva primera = crear("Bautizo", "1", "2026-10-20");
        servicio.resolver(9, 1, String.valueOf(primera.getIdReserva()), "rechazada", "Falta documentación", "1.1.1.1");
        assertEquals("rechazada", reservas.buscarPorId(primera.getIdReserva()).get().getEstado());
        assertNotNull(crear("Bautizo", "1", "2026-10-20"));
    }

    @Test
    void limitesDeLongitud() throws SQLException {
        assertEquals("x".repeat(150), servicio.crear(1, "Bautizo", "1", "x".repeat(150), "2026-10-20", null, "ip").getNombreSolicitante());
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.crear(1, "Bautizo", "1", "x".repeat(151), "2026-10-21", null, "ip")).getEstado());
        servicio.crear(1, "Bautizo", "1", "A", "2026-10-22", "n".repeat(500), "ip");
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.crear(1, "Bautizo", "1", "A", "2026-10-23", "n".repeat(501), "ip")).getEstado());
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.crear(1, "Bautizo", "1", "   ", "2026-10-24", null, "ip")).getEstado());
    }

    @Test
    void resolverExigeMotivoAlRechazarYSoloActuaSobrePendientesDeSuParroquia() throws SQLException {
        Reserva r = crear("Bautizo", "1", "2026-10-20");
        String id = String.valueOf(r.getIdReserva());
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.resolver(9, 1, id, "rechazada", "  ", "ip")).getEstado());
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.resolver(9, 1, id, "rechazada", "m".repeat(256), "ip")).getEstado());
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.resolver(9, 1, id, "pendiente", null, "ip")).getEstado());
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.resolver(9, 1, "abc", "confirmada", null, "ip")).getEstado());
        assertEquals(404, assertThrows(ReglaNegocioException.class, () -> servicio.resolver(9, 2, id, "confirmada", null, "ip")).getEstado(), "otra parroquia");
        assertEquals(404, assertThrows(ReglaNegocioException.class, () -> servicio.resolver(9, 1, "999", "confirmada", null, "ip")).getEstado());
        servicio.resolver(9, 1, id, "confirmada", null, "ip");
        assertEquals(409, assertThrows(ReglaNegocioException.class, () -> servicio.resolver(9, 1, id, "rechazada", "tarde", "ip")).getEstado());
        assertTrue(bitacoraDao.hay("RESERVA_CONFIRMADA"));
        assertEquals(1, bitacoraDao.cuenta("RESERVA_CONFIRMADA") + bitacoraDao.cuenta("RESERVA_RECHAZADA"));
    }

    @Test
    void lasPendientesIncluyenElContactoDeQuienLasPidio() throws SQLException {
        crear("Bautizo", "1", "2026-10-20");
        crear("Bautizo", "2", "2026-10-20");
        List<ReservaService.ReservaPendiente> lista = servicio.pendientesDeParroquia(1);
        assertEquals(1, lista.size());
        assertTrue(lista.get(0).contacto().contains("ana@x.pe"));
    }

    @Test
    void delUsuarioDevuelveSoloLasSuyasConElNombreDeLaParroquia() throws SQLException {
        crear("Bautizo", "1", "2026-10-20");
        servicio.crear(2, "Bautizo", "2", "Otro", "2026-10-21", null, "ip");
        List<ReservaService.ReservaPropia> lista = servicio.delUsuario(1);
        assertEquals(1, lista.size());
        assertEquals("Parroquia San José", lista.get(0).parroquia());
    }

    // ---- fechas publicadas por la parroquia (opción B: sin fechas publicadas, la fecha es libre)

    private void publicar(int parroquia, int sacramento, String fecha, int cupo) throws SQLException {
        fechas.insertar(new FechaDisponible(parroquia, sacramento, LocalDate.parse(fecha), null, cupo));
    }

    @Test
    void sinFechasPublicadasLaFechaSigueSiendoLibre() throws SQLException {
        assertFalse(servicio.disponibilidad("1", "Bautizo").publicadas());
        assertNotNull(crear("Bautizo", "1", "2026-12-24"));
    }

    @Test
    void conFechasPublicadasSoloSePuedeElegirEntreEllas() throws SQLException {
        publicar(1, 1, "2026-11-14", 2);
        assertEquals(400, estadoDelError("Bautizo", "1", "2026-11-15"), "otra fecha no publicada");
        assertEquals("2026-11-14", crear("Bautizo", "1", "2026-11-14").getFechaSolicitada().toString());
    }

    @Test
    void lasFechasPublicadasSoloAfectanASuParroquiaYSacramento() throws SQLException {
        publicar(1, 1, "2026-11-14", 1);
        assertNotNull(crear("Matrimonio", "1", "2026-11-15"), "otro sacramento de la misma parroquia sigue libre");
        assertNotNull(crear("Bautizo", "2", "2026-11-15"), "otra parroquia sigue libre");
    }

    @Test
    void elCupoPermiteVariasSolicitudesYSeAgota() throws SQLException {
        publicar(1, 1, "2026-11-14", 2);
        crear("Bautizo", "1", "2026-11-14");
        crear("Bautizo", "1", "2026-11-14");
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> crear("Bautizo", "1", "2026-11-14"));
        assertEquals(409, e.getEstado());
        assertTrue(e.getMessage().contains("cupo"));
    }

    @Test
    void unaSolicitudRechazadaDevuelveElCupo() throws SQLException {
        publicar(1, 1, "2026-11-14", 1);
        Reserva r = crear("Bautizo", "1", "2026-11-14");
        assertEquals(409, estadoDelError("Bautizo", "1", "2026-11-14"));
        servicio.resolver(9, 1, String.valueOf(r.getIdReserva()), "rechazada", "Falta documentación", "ip");
        assertNotNull(crear("Bautizo", "1", "2026-11-14"));
    }

    @Test
    void disponibilidadListaSoloLasFechasFuturasConCupoLibre() throws SQLException {
        publicar(1, 1, "2026-10-08", 5);   // hoy: no cuenta
        publicar(1, 1, "2026-11-14", 1);
        publicar(1, 1, "2026-11-21", 3);
        crear("Bautizo", "1", "2026-11-14");   // llena la del 14
        ReservaService.Disponibilidad d = servicio.disponibilidad("1", "bautizo");
        assertTrue(d.publicadas());
        assertEquals(1, d.fechas().size());
        assertEquals(LocalDate.parse("2026-11-21"), d.fechas().get(0).getFecha());
        assertEquals(3, d.fechas().get(0).getLibres());
    }

    @Test
    void siTodasLasFechasPublicadasEstanLlenasNoSeVuelveALibre() throws SQLException {
        publicar(1, 1, "2026-11-14", 1);
        crear("Bautizo", "1", "2026-11-14");
        ReservaService.Disponibilidad d = servicio.disponibilidad("1", "Bautizo");
        assertTrue(d.publicadas());
        assertTrue(d.fechas().isEmpty());
        assertEquals(400, estadoDelError("Bautizo", "1", "2026-11-28"));
    }

    @Test
    void disponibilidadValidaParroquiaYSacramento() {
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.disponibilidad("99", "Bautizo")).getEstado());
        assertEquals(400, assertThrows(ReglaNegocioException.class, () -> servicio.disponibilidad("1", "Inventado")).getEstado());
    }
}
