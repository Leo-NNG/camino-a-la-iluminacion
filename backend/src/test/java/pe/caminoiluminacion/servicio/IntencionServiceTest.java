package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.caminoiluminacion.controlador.Pruebas;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.controlador.Pruebas.FakeParroquias;
import pe.caminoiluminacion.controlador.Pruebas.FakeSacramentos;
import pe.caminoiluminacion.controlador.Pruebas.FakeUsuarios;
import pe.caminoiluminacion.dao.HorarioDAO;
import pe.caminoiluminacion.dao.IntencionDAO;
import pe.caminoiluminacion.modelo.CuentaPago;
import pe.caminoiluminacion.modelo.Horario;
import pe.caminoiluminacion.modelo.Intencion;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.servicio.IntencionService.IntencionVista;
import pe.caminoiluminacion.servicio.PagoServiceTest.FakeAranceles;
import pe.caminoiluminacion.servicio.PagoServiceTest.FakeCuentas;
import pe.caminoiluminacion.servicio.PagoServiceTest.FakeNotificaciones;
import pe.caminoiluminacion.servicio.PagoServiceTest.FakePagos;
import pe.caminoiluminacion.util.AlmacenArchivos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class IntencionServiceTest {

    // ------------------------------------------------------------ DAO falsos

    static class FakeHorarios implements HorarioDAO {
        final List<Horario> lista = new ArrayList<>();
        void agregar(int id, int parroquia, String dia, String hora, String tipo) {
            Horario h = new Horario(parroquia, dia, hora, tipo);
            h.setIdHorario(id);
            lista.add(h);
        }
        public Horario insertar(Horario h) { return h; }
        public Optional<Horario> buscarPorId(int id) { return lista.stream().filter(h -> h.getIdHorario() == id).findFirst(); }
        public List<Horario> listarPorParroquia(int p) { return lista.stream().filter(h -> h.getIdParroquia() == p).toList(); }
        public boolean actualizar(Horario h) { return true; }
        public boolean eliminar(int id) { return true; }
    }

    static class FakeIntenciones implements IntencionDAO {
        final List<Intencion> lista = new ArrayList<>();
        final FakePagos pagos;
        SQLException fallar;
        int sig = 1;
        FakeIntenciones(FakePagos pagos) { this.pagos = pagos; }

        private boolean vigente(Intencion i) { return !Pago.RECHAZADO.equals(pagos.buscarPorId(i.idPago()).get().estado()); }

        public Optional<Intencion> insertarConPago(Intencion i, Pago p, int max) throws SQLException {
            long ocupados = lista.stream().filter(x -> x.idHorario().equals(i.idHorario()) && x.fechaMisa().equals(i.fechaMisa()) && vigente(x)).count();
            if (ocupados >= max) return Optional.empty();
            if (fallar != null) throw fallar;
            Pago g = pagos.insertarLibre(p);
            Intencion n = new Intencion(sig++, i.idUsuario(), i.idParroquia(), i.idHorario(), i.horaMisa(), i.fechaMisa(), i.tipo(), i.texto(), g.idPago(), null);
            lista.add(n);
            return Optional.of(n);
        }
        private Fila fila(Intencion i) {
            Pago p = pagos.buscarPorId(i.idPago()).get();
            return new Fila(i, p.estado(), p.monto(), p.observacion());
        }
        public List<Fila> listarPorUsuario(int u) { return lista.stream().filter(i -> i.idUsuario() == u).map(this::fila).toList(); }
        public List<Fila> agenda(int pq, Date desde, Date hasta, boolean soloAprobadas) {
            return lista.stream().filter(i -> i.idParroquia() == pq && !i.fechaMisa().before(desde) && !i.fechaMisa().after(hasta))
                    .map(this::fila).filter(f -> soloAprobadas ? Pago.APROBADO.equals(f.estadoPago()) : !Pago.RECHAZADO.equals(f.estadoPago())).toList();
        }
    }

    // ------------------------------------------------------------ preparación

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Lima")); // miércoles
    private static final String DOMINGO = "2026-10-25";

    @TempDir Path carpeta;
    FakePagos pagos;
    FakeCuentas cuentas;
    FakeNotificaciones notificaciones;
    FakeHorarios horarios;
    FakeIntenciones intenciones;
    FakeBitacora bitacoraDao;
    IntencionService servicio;
    PagoService pagoService;
    AtomicInteger semilla = new AtomicInteger();

    @BeforeEach
    void preparar() throws SQLException {
        pagos = new FakePagos();
        cuentas = new FakeCuentas();
        notificaciones = new FakeNotificaciones();
        horarios = new FakeHorarios();
        intenciones = new FakeIntenciones(pagos);
        bitacoraDao = new FakeBitacora();
        FakeUsuarios usuarios = new FakeUsuarios();
        usuarios.insertar(Pruebas.usuario("70000001", "ana@x.pe", "Clave123", "feligres", null));      // 1
        usuarios.insertar(Pruebas.usuario("70000002", "luis@x.pe", "Clave123", "feligres", null));     // 2
        usuarios.insertar(Pruebas.usuario("70000003", "padre@x.pe", "Clave123", "administrador", 1));  // 3
        usuarios.insertar(Pruebas.usuario("70000004", "maria@x.pe", "Clave123", "feligres", null));    // 4
        cuentas.guardar(new CuentaPago(1, "911111111", "Parroquia San José", null, null, null));
        cuentas.guardarOfrenda(1, new BigDecimal("10.00"));
        horarios.agregar(1, 1, "domingo", "7:00 a.m. y 7:00 p.m.", "misa");
        horarios.agregar(2, 1, "sabado", "4:00 p.m.", "actividad");
        horarios.agregar(3, 2, "domingo", "9:00 a.m.", "misa");
        BitacoraService bitacora = new BitacoraService(bitacoraDao);
        AlmacenArchivos almacen = new AlmacenArchivos(carpeta);
        ComprobanteService comprobantes = new ComprobanteService(pagos, almacen);
        FakeParroquias parroquias = new FakeParroquias();
        servicio = new IntencionService(intenciones, horarios, cuentas, parroquias, usuarios, comprobantes, bitacora, RELOJ);
        pagoService = new PagoService(pagos, new FakeAranceles(), cuentas, notificaciones, new Pruebas.FakeReservas(), parroquias,
                new FakeSacramentos(), usuarios, almacen, bitacora);
    }

    private InputStream png() {
        byte[] b = new byte[64];
        byte[] firma = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(firma, 0, b, 0, firma.length);
        b[20] = (byte) semilla.incrementAndGet();
        return new ByteArrayInputStream(b);
    }

    private Intencion pedir(int usuario, String horario, String fecha, String tipo, String texto) throws Exception {
        return servicio.registrar(usuario, "1", horario, fecha, tipo, texto, "yape", String.valueOf(300000 + semilla.incrementAndGet()), "987654321", png(), "1.1.1.1");
    }

    private int estado(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(ReglaNegocioException.class, e).getEstado();
    }

    private long archivos() throws IOException {
        try (Stream<Path> s = Files.list(carpeta)) { return s.count(); }
    }

    // ------------------------------------------------------------ registro

    @Test
    void registraLaIntencionConSuPagoPendienteYLaOfrendaDeLaBaseDeDatos() throws Exception {
        Intencion i = pedir(1, "1", DOMINGO, "difunto", "  Juan   Pérez Rojas ");
        assertEquals("Juan Pérez Rojas", i.texto(), "se normalizan los espacios");
        assertEquals("7:00 a.m. y 7:00 p.m.", i.horaMisa(), "se guarda copia de la hora del horario");
        assertEquals(Date.valueOf(DOMINGO), i.fechaMisa());
        Pago p = pagos.buscarPorId(i.idPago()).get();
        assertEquals(Pago.INTENCION, p.concepto());
        assertEquals(new BigDecimal("10.00"), p.monto(), "la ofrenda sale de la base de datos");
        assertEquals(Pago.PENDIENTE, p.estado());
        assertTrue(p.celularOrigen().startsWith("v1:"), "el celular va cifrado");
        assertEquals(1, archivos());
        assertTrue(bitacoraDao.hay("INTENCION_REGISTRADA"));
    }

    @Test
    void infoMuestraLaOfrendaYSoloLasMisas() throws Exception {
        IntencionService.InfoIntencion info = servicio.info("1");
        assertEquals(new BigDecimal("10.00"), info.ofrenda());
        assertEquals(1, info.misas().size(), "las actividades no son misas");
        assertEquals("domingo", info.misas().get(0).getDiaSemana());
        assertEquals(IntencionService.MAX_POR_MISA, info.maxPorMisa());
        assertNull(servicio.info("2").ofrenda(), "la parroquia 2 no configuró ofrenda");
        assertEquals(404, estado(() -> servicio.info("99")));
        assertEquals(400, estado(() -> servicio.info("x")));
    }

    @Test
    void laFechaDebeSerFuturaDelDiaDeLaMisaYDentroDe90Dias() {
        assertEquals(400, estado(() -> pedir(1, "1", "2026-10-21", "difunto", "Juan Pérez")), "hoy no");
        assertEquals(400, estado(() -> pedir(1, "1", "2026-10-18", "difunto", "Juan Pérez")), "pasada");
        assertEquals(400, estado(() -> pedir(1, "1", "2026-10-26", "difunto", "Juan Pérez")), "un lunes no es domingo");
        assertEquals(400, estado(() -> pedir(1, "1", "2027-02-14", "difunto", "Juan Pérez")), "más de 90 días");
        for (String mala : new String[]{"25/10/2026", "mañana", "", null, "2026-13-40"}) {
            assertEquals(400, estado(() -> pedir(1, "1", mala, "difunto", "Juan Pérez")), "fecha " + mala);
        }
        assertDoesNotThrow(() -> pedir(1, "1", "2027-01-17", "difunto", "Juan Pérez"), "88 días: dentro del límite");
    }

    @Test
    void validaTipoYTextoDeLaIntencion() throws Exception {
        assertEquals(400, estado(() -> pedir(1, "1", DOMINGO, "inventado", "Juan Pérez")));
        assertEquals(400, estado(() -> pedir(1, "1", DOMINGO, null, "Juan Pérez")));
        for (String malo : new String[]{"ab", "", null, "<script>alert(1)</script>", "x".repeat(201), "Juan\u0000Perez", "Juan@correo.com", "50% off", "a=b"}) {
            assertEquals(400, estado(() -> pedir(1, "1", DOMINGO, "difunto", malo)), "texto " + malo);
        }
        assertEquals(0, pagos.lista.size());
        assertEquals("María Ñandú (ñ) — cuidado!".replace(" — ", " "), pedir(1, "1", DOMINGO, "salud", "María Ñandú (ñ) — cuidado!".replace(" — ", " ")).texto());
    }

    @Test
    void laMisaDebeSerDeEsaParroquiaYSerUnaMisa() {
        assertEquals(404, estado(() -> pedir(1, "3", DOMINGO, "difunto", "Juan Pérez")), "misa de otra parroquia");
        assertEquals(404, estado(() -> pedir(1, "2", "2026-10-24", "difunto", "Juan Pérez")), "una actividad no es misa");
        assertEquals(404, estado(() -> pedir(1, "99", DOMINGO, "difunto", "Juan Pérez")));
        assertEquals(400, estado(() -> pedir(1, "abc", DOMINGO, "difunto", "Juan Pérez")));
        assertEquals(404, estado(() -> servicio.registrar(1, "99", "1", DOMINGO, "difunto", "Juan Pérez", "yape", "123456", "987654321", png(), "ip")));
    }

    @Test
    void sinOfrendaOSinCuentaNoSePuedePedir() throws Exception {
        assertEquals(409, estado(() -> servicio.registrar(1, "1", "1", DOMINGO, "difunto", "Juan Pérez", "plin", "123456", "987654321", png(), "ip")),
                "la parroquia no tiene Plin");
        assertEquals(400, estado(() -> servicio.registrar(1, "1", "1", DOMINGO, "difunto", "Juan Pérez", "paypal", "123456", "987654321", png(), "ip")));
        cuentas.guardarOfrenda(1, null);
        assertEquals(409, estado(() -> pedir(1, "1", DOMINGO, "difunto", "Juan Pérez")), "ofrenda desactivada");
        cuentas.guardarOfrenda(1, BigDecimal.ZERO);
        assertEquals(409, estado(() -> pedir(1, "1", DOMINGO, "difunto", "Juan Pérez")), "ofrenda en cero");
    }

    @Test
    void usaLasMismasDefensasDelComprobanteQueLosDemasPagos() throws Exception {
        servicio.registrar(1, "1", "1", DOMINGO, "difunto", "Juan Pérez", "yape", "777777", "987654321", png(), "ip");
        assertEquals(409, estado(() -> servicio.registrar(2, "1", "1", DOMINGO, "salud", "Luis Gómez", "yape", "777777", "987654321", png(), "ip")),
                "misma operación");
        assertEquals(415, estado(() -> servicio.registrar(2, "1", "1", DOMINGO, "salud", "Luis Gómez", "yape", "555555", "987654321",
                new ByteArrayInputStream("%PDF-1.4".getBytes()), "ip")));
        assertEquals(400, estado(() -> servicio.registrar(2, "1", "1", DOMINGO, "salud", "Luis Gómez", "yape", "abc", "987654321", png(), "ip")));
        assertEquals(1, archivos(), "los envíos rechazados no dejan archivos");
    }

    @Test
    void unTextoConComillasYPuntoYComaSeGuardaTalCualPorqueLasConsultasSonParametrizadas() throws Exception {
        assertEquals("'; DROP TABLE pago; --", pedir(1, "1", DOMINGO, "otra", "'; DROP TABLE pago; --").texto());
    }

    // ------------------------------------------------------------ cupo

    @Test
    void cadaMisaTieneUnCupoPorFechaYLosPagosRechazadosLoLiberan() throws Exception {
        int[] usuarios = {1, 2, 4};
        List<Intencion> hechas = new ArrayList<>();
        for (int k = 0; k < IntencionService.MAX_POR_MISA; k++) {
            hechas.add(pedir(usuarios[k % 3], "1", DOMINGO, "difunto", "Difunto Número " + k));
        }
        assertEquals(409, estado(() -> pedir(4, "1", DOMINGO, "difunto", "Una más")), "cupo lleno");
        assertDoesNotThrow(() -> pedir(4, "1", "2026-11-01", "difunto", "Otra fecha"), "otra fecha tiene su propio cupo");

        pagoService.resolver(3, 1, String.valueOf(hechas.get(0).idPago()), false, "No aparece", "ip");
        assertDoesNotThrow(() -> pedir(4, "1", DOMINGO, "difunto", "Ahora sí"), "el rechazo libera el cupo");
    }

    @Test
    void siElCupoEstaLlenoOLaBaseFallaNoQuedaElArchivo() throws Exception {
        for (int k = 0; k < IntencionService.MAX_POR_MISA; k++) pedir(new int[]{1, 2, 4}[k % 3], "1", DOMINGO, "difunto", "Persona " + k);
        long antes = archivos();
        assertEquals(409, estado(() -> pedir(1, "1", DOMINGO, "difunto", "Sobrante")));
        assertEquals(antes, archivos());

        intenciones.fallar = new SQLException("caída");
        assertThrows(SQLException.class, () -> pedir(1, "1", "2026-11-01", "difunto", "Con falla"));
        assertEquals(antes, archivos());
        intenciones.fallar = new SQLIntegrityConstraintViolationException("Duplicate entry 'x' for key 'uq_pago_voucher'");
        assertEquals(409, estado(() -> pedir(1, "1", "2026-11-08", "difunto", "Carrera")));
        assertEquals(antes, archivos());
    }

    // ------------------------------------------------------------ consulta y agenda

    @Test
    void elFeligresVeSusIntencionesConElEstadoDelPago() throws Exception {
        pedir(1, "1", DOMINGO, "difunto", "Juan Pérez");
        pedir(2, "1", DOMINGO, "salud", "Luis Gómez");
        List<IntencionVista> mias = servicio.deUsuario(1);
        assertEquals(1, mias.size());
        assertEquals("pendiente", mias.get(0).estadoPago());
        assertEquals("Por el eterno descanso de", mias.get(0).etiqueta());
        assertEquals("Parroquia San José", mias.get(0).parroquia());
        assertEquals(new BigDecimal("10.00"), mias.get(0).monto());
        assertTrue(servicio.deUsuario(4).isEmpty());
    }

    @Test
    void laAgendaDelPersonalSoloMuestraLasIntencionesConPagoAprobado() throws Exception {
        Intencion a = pedir(1, "1", DOMINGO, "difunto", "Juan Pérez");
        Intencion b = pedir(2, "1", DOMINGO, "accion_gracias", "el bautizo de Ana");
        pedir(4, "1", DOMINGO, "salud", "Pendiente de pago");
        assertTrue(servicio.agenda(1, null, null).isEmpty(), "ninguna está aprobada todavía");

        pagoService.resolver(3, 1, String.valueOf(a.idPago()), true, "", "ip");
        pagoService.resolver(3, 1, String.valueOf(b.idPago()), false, "No coincide", "ip");
        List<IntencionVista> agenda = servicio.agenda(1, null, null);
        assertEquals(1, agenda.size(), "solo la aprobada");
        assertEquals("Juan Pérez", agenda.get(0).intencion().texto());
        assertEquals("Ana Torres", agenda.get(0).solicitante(), "el personal ve quién la ofrece");
        assertTrue(servicio.agenda(2, null, null).isEmpty(), "otra parroquia no ve la agenda");
    }

    @Test
    void laAgendaValidaElRangoDeFechas() throws Exception {
        assertEquals(400, estado(() -> servicio.agenda(1, "ayer", null)));
        assertEquals(400, estado(() -> servicio.agenda(1, "2026-10-30", "2026-10-25")), "fin antes del inicio");
        assertEquals(400, estado(() -> servicio.agenda(1, "2026-10-01", "2026-12-31")), "más de 62 días");
        assertDoesNotThrow(() -> servicio.agenda(1, "2026-10-01", "2026-11-30"));
    }

    @Test
    void aprobarElPagoNotificaAlFeligresConElConceptoDeIntencion() throws Exception {
        Intencion i = pedir(1, "1", DOMINGO, "difunto", "Juan Pérez");
        pagoService.resolver(3, 1, String.valueOf(i.idPago()), true, "", "ip");
        assertEquals(1, notificaciones.enviadas.size());
        assertTrue(notificaciones.enviadas.get(0)[2].contains("Intención de misa"));
        assertEquals("pendiente".equals("x") ? "" : "aprobado", servicio.deUsuario(1).get(0).estadoPago());
    }
}
