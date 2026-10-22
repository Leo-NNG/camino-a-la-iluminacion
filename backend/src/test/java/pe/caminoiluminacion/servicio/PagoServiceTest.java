package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.caminoiluminacion.controlador.Pruebas;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.controlador.Pruebas.FakeParroquias;
import pe.caminoiluminacion.controlador.Pruebas.FakeReservas;
import pe.caminoiluminacion.controlador.Pruebas.FakeSacramentos;
import pe.caminoiluminacion.controlador.Pruebas.FakeUsuarios;
import pe.caminoiluminacion.dao.ArancelDAO;
import pe.caminoiluminacion.dao.CuentaPagoDAO;
import pe.caminoiluminacion.dao.NotificacionDAO;
import pe.caminoiluminacion.dao.PagoDAO;
import pe.caminoiluminacion.modelo.Arancel;
import pe.caminoiluminacion.modelo.CuentaPago;
import pe.caminoiluminacion.modelo.Notificacion;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.servicio.PagoService.PagoVista;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PagoServiceTest {

    // ------------------------------------------------------------ DAO falsos en memoria

    static class FakePagos implements PagoDAO {
        final List<Pago> lista = new ArrayList<>();
        int sig = 1;
        SQLException fallar;

        private Pago guardar(Pago p) throws SQLException {
            if (fallar != null) throw fallar;
            Pago g = p.conId(sig++);
            lista.add(g);
            return g;
        }
        private void reemplazar(Pago nuevo) { lista.replaceAll(x -> x.idPago().equals(nuevo.idPago()) ? nuevo : x); }
        private static boolean vigente(Pago p) { return !Pago.RECHAZADO.equals(p.estado()); }

        public Optional<Pago> insertarArancel(Pago p) throws SQLException {
            if (lista.stream().anyMatch(x -> p.idReserva().equals(x.idReserva()) && vigente(x))) return Optional.empty();
            return Optional.of(guardar(p));
        }
        public Pago insertarLibre(Pago p) throws SQLException { return guardar(p); }
        public Optional<Pago> buscarPorId(int id) { return lista.stream().filter(p -> p.idPago() == id).findFirst(); }
        public List<Pago> listarPorUsuario(int id) { return lista.stream().filter(p -> p.idUsuario() == id).toList(); }
        public List<Pago> listarPorParroquiaYEstado(int pq, String e) { return lista.stream().filter(p -> p.idParroquia() == pq && p.estado().equals(e)).toList(); }
        public Optional<Pago> vigenteDeReserva(int r) { return lista.stream().filter(p -> Integer.valueOf(r).equals(p.idReserva()) && vigente(p)).findFirst(); }
        public boolean existeOperacionVigente(String m, String n) { return lista.stream().anyMatch(p -> vigente(p) && p.metodo().equals(m) && p.numeroOperacion().equals(n)); }
        public boolean existeVoucherVigente(String sha) { return lista.stream().anyMatch(p -> vigente(p) && p.voucherSha256().equals(sha)); }
        public boolean resolverPendiente(int id, String estado, String obs, int revisor) {
            Pago p = buscarPorId(id).orElse(null);
            if (p == null || !Pago.PENDIENTE.equals(p.estado())) return false;
            reemplazar(new Pago(p.idPago(), p.idUsuario(), p.idParroquia(), p.idReserva(), p.concepto(), p.monto(), p.metodo(),
                    p.celularOrigen(), p.numeroOperacion(), p.rutaVoucher(), p.voucherSha256(), estado, obs, p.creadoEn()));
            return true;
        }
        public List<FilaIngreso> ingresosPorConcepto(int pq, String mes) {
            return lista.stream().filter(p -> p.idParroquia() == pq && Pago.APROBADO.equals(p.estado()))
                    .collect(java.util.stream.Collectors.groupingBy(Pago::concepto)).entrySet().stream()
                    .map(e -> new FilaIngreso(e.getKey(), e.getValue().size(), e.getValue().stream().map(Pago::monto).reduce(BigDecimal.ZERO, BigDecimal::add))).toList();
        }
    }

    static class FakeAranceles implements ArancelDAO {
        final Map<String, BigDecimal> montos = new java.util.HashMap<>();
        public Optional<BigDecimal> montoDe(int p, int s) { return Optional.ofNullable(montos.get(p + ":" + s)); }
        public List<Arancel> listarPorParroquia(int p) { return List.of(); }
        public void guardar(int p, int s, BigDecimal m) { montos.put(p + ":" + s, m); }
    }

    static class FakeCuentas implements CuentaPagoDAO {
        final Map<Integer, CuentaPago> cuentas = new java.util.HashMap<>();
        public Optional<CuentaPago> buscar(int p) { return Optional.ofNullable(cuentas.get(p)); }
        public void guardar(CuentaPago c) { cuentas.put(c.idParroquia(), c); }
    }

    static class FakeNotificaciones implements NotificacionDAO {
        final List<String[]> enviadas = new ArrayList<>();
        boolean fallar;
        public void crear(int u, String a, String m) throws SQLException {
            if (fallar) throw new SQLException("notificaciones caídas");
            enviadas.add(new String[]{String.valueOf(u), a, m});
        }
        public List<Notificacion> listarPorUsuario(int u, int l) { return List.of(); }
        public int contarNoLeidas(int u) { return 0; }
        public int marcarTodasLeidas(int u) { return 0; }
    }

    // ------------------------------------------------------------ preparación

    @TempDir Path carpeta;
    FakePagos pagos;
    FakeAranceles aranceles;
    FakeCuentas cuentas;
    FakeNotificaciones notificaciones;
    FakeReservas reservas;
    FakeBitacora bitacoraDao;
    PagoService servicio;
    AtomicInteger semilla = new AtomicInteger();

    @BeforeEach
    void preparar() throws SQLException {
        pagos = new FakePagos();
        aranceles = new FakeAranceles();
        cuentas = new FakeCuentas();
        notificaciones = new FakeNotificaciones();
        reservas = new FakeReservas();
        bitacoraDao = new FakeBitacora();
        FakeUsuarios usuarios = new FakeUsuarios();
        usuarios.insertar(Pruebas.usuario("70000001", "ana@x.pe", "Clave123", "feligres", null));   // id 1
        usuarios.insertar(Pruebas.usuario("70000002", "luis@x.pe", "Clave123", "feligres", null));  // id 2
        usuarios.insertar(Pruebas.usuario("70000003", "padre@x.pe", "Clave123", "administrador", 1)); // id 3
        aranceles.guardar(1, 1, new BigDecimal("50.00"));   // bautizo en San José
        aranceles.guardar(1, 2, new BigDecimal("250.00"));  // matrimonio
        aranceles.guardar(1, 3, BigDecimal.ZERO);           // primera comunión: sin arancel en línea
        cuentas.guardar(new CuentaPago(1, "911111111", "Parroquia San José", null, null)); // solo Yape
        servicio = new PagoService(pagos, aranceles, cuentas, notificaciones, reservas, new FakeParroquias(),
                new FakeSacramentos(), usuarios, new AlmacenArchivos(carpeta), new BitacoraService(bitacoraDao));
    }

    private Reserva reserva(int idUsuario, int idParroquia, String sacramento, String estado) {
        Reserva r = new Reserva(idUsuario, idParroquia, sacramento, "Bebé Uno", Date.valueOf("2026-12-01"), null);
        r.setEstado(estado);
        reservas.insertar(r);
        return r;
    }

    /** Una imagen PNG distinta en cada llamada (firma real + relleno), para que cada comprobante tenga su propio hash. */
    private InputStream png() {
        byte[] b = new byte[64];
        byte[] firma = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(firma, 0, b, 0, firma.length);
        b[20] = (byte) semilla.incrementAndGet();
        return new ByteArrayInputStream(b);
    }

    private Pago pagarBautizo(int idUsuario, Reserva r, String operacion) throws Exception {
        return servicio.pagarReserva(idUsuario, String.valueOf(r.getIdReserva()), "yape", operacion, "987654321", png(), "1.1.1.1");
    }

    private int estado(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(ReglaNegocioException.class, e).getEstado();
    }

    private long archivosGuardados() throws IOException {
        try (Stream<Path> s = Files.list(carpeta)) { return s.count(); }
    }

    // ------------------------------------------------------------ pago de arancel

    @Test
    void registraElPagoConElMontoDeLaBaseDeDatosYElCelularCifrado() throws Exception {
        Reserva r = reserva(1, 1, "bautizo", "pendiente");
        Pago p = pagarBautizo(1, r, "123456");
        assertEquals(new BigDecimal("50.00"), p.monto(), "el monto sale del arancel, no del cliente");
        assertEquals(Pago.PENDIENTE, p.estado());
        assertEquals(Pago.ARANCEL, p.concepto());
        assertTrue(p.celularOrigen().startsWith("v1:"), "el celular se guarda cifrado");
        assertFalse(p.celularOrigen().contains("987654321"));
        assertEquals(64, p.voucherSha256().length());
        assertEquals(1, archivosGuardados());
        assertTrue(bitacoraDao.hay("PAGO_REGISTRADO"));
        assertFalse(bitacoraDao.ultima("PAGO_REGISTRADO").contains("123456"), "la bitácora no lleva el número de operación");
    }

    @Test
    void validaMetodoOperacionCelularYComprobante() {
        Reserva r = reserva(1, 1, "bautizo", "pendiente");
        String id = String.valueOf(r.getIdReserva());
        assertEquals(400, estado(() -> servicio.pagarReserva(1, id, "paypal", "123456", "987654321", png(), "ip")));
        assertEquals(400, estado(() -> servicio.pagarReserva(1, id, null, "123456", "987654321", png(), "ip")));
        for (String malo : new String[]{"123", "abcdef", "1234567890123", "12 34 56", "", null}) {
            assertEquals(400, estado(() -> servicio.pagarReserva(1, id, "yape", malo, "987654321", png(), "ip")), "operación " + malo);
        }
        for (String malo : new String[]{"87654321", "187654321", "98765432a", "", null}) {
            assertEquals(400, estado(() -> servicio.pagarReserva(1, id, "yape", "123456", malo, png(), "ip")), "celular " + malo);
        }
        assertEquals(400, estado(() -> servicio.pagarReserva(1, id, "yape", "123456", "987654321", null, "ip")));
        assertEquals(400, estado(() -> servicio.pagarReserva(1, id, "yape", "123456", "987654321", new ByteArrayInputStream(new byte[0]), "ip")));
        assertTrue(pagos.lista.isEmpty());
    }

    @Test
    void elComprobanteDebeSerUnaImagenPorSuContenidoYNoMasDe5MB() throws IOException {
        Reserva r = reserva(1, 1, "bautizo", "pendiente");
        String id = String.valueOf(r.getIdReserva());
        InputStream pdf = new ByteArrayInputStream("%PDF-1.4 contenido".getBytes());
        assertEquals(415, estado(() -> servicio.pagarReserva(1, id, "yape", "123456", "987654321", pdf, "ip")));
        InputStream disfrazado = new ByteArrayInputStream("<script>alert(1)</script>".getBytes()); // aunque se llame .png
        assertEquals(415, estado(() -> servicio.pagarReserva(1, id, "yape", "123456", "987654321", disfrazado, "ip")));
        byte[] grande = new byte[AlmacenArchivos.MAX_BYTES + 10];
        grande[0] = (byte) 0x89; grande[1] = 'P'; grande[2] = 'N'; grande[3] = 'G'; grande[4] = 0x0D; grande[5] = 0x0A; grande[6] = 0x1A; grande[7] = 0x0A;
        assertEquals(413, estado(() -> servicio.pagarReserva(1, id, "yape", "123456", "987654321", new ByteArrayInputStream(grande), "ip")));
        assertEquals(0, archivosGuardados(), "un comprobante rechazado no deja archivos");
    }

    @Test
    void soloSeCobraLaReservaPropiaYVigente() throws Exception {
        Reserva ajena = reserva(2, 1, "bautizo", "pendiente");
        assertEquals(404, estado(() -> pagarBautizo(1, ajena, "123456")), "la reserva de otro usuario no existe para mí");
        Reserva rechazada = reserva(1, 1, "bautizo", "rechazada");
        assertEquals(409, estado(() -> pagarBautizo(1, rechazada, "123456")));
        assertEquals(404, estado(() -> servicio.pagarReserva(1, "999", "yape", "123456", "987654321", png(), "ip")));
        assertEquals(400, estado(() -> servicio.pagarReserva(1, "abc", "yape", "123456", "987654321", png(), "ip")));
    }

    @Test
    void unaReservaNoSePagaDosVeces() throws Exception {
        Reserva r = reserva(1, 1, "bautizo", "pendiente");
        pagarBautizo(1, r, "123456");
        assertEquals(409, estado(() -> pagarBautizo(1, r, "654321")));
        assertEquals(1, pagos.lista.size());
    }

    @Test
    void unaOperacionYUnComprobanteNoSeReutilizan() throws Exception {
        Reserva a = reserva(1, 1, "bautizo", "pendiente");
        Reserva b = reserva(1, 1, "matrimonio", "pendiente");
        pagarBautizo(1, a, "123456");
        assertEquals(409, estado(() -> pagarBautizo(1, b, "123456")), "misma operación");

        byte[] mismo = new byte[64];
        mismo[0] = (byte) 0x89; mismo[1] = 'P'; mismo[2] = 'N'; mismo[3] = 'G'; mismo[4] = 0x0D; mismo[5] = 0x0A; mismo[6] = 0x1A; mismo[7] = 0x0A;
        Reserva c = reserva(1, 1, "bautizo", "pendiente");
        servicio.pagarReserva(1, String.valueOf(c.getIdReserva()), "yape", "222222", "987654321", new ByteArrayInputStream(mismo), "ip");
        Reserva d = reserva(1, 1, "bautizo", "pendiente");
        assertEquals(409, estado(() -> servicio.pagarReserva(1, String.valueOf(d.getIdReserva()), "yape", "333333", "987654321", new ByteArrayInputStream(mismo), "ip")), "mismo comprobante");
    }

    @Test
    void trasUnRechazoElFeligresPuedeReintentarConLosMismosDatos() throws Exception {
        Reserva r = reserva(1, 1, "bautizo", "pendiente");
        Pago p = pagarBautizo(1, r, "123456");
        servicio.resolver(3, 1, String.valueOf(p.idPago()), false, "El monto no coincide", "ip");
        Pago nuevo = pagarBautizo(1, r, "123456");
        assertEquals(Pago.PENDIENTE, nuevo.estado());
        assertEquals(2, pagos.lista.size());
    }

    @Test
    void sinCuentaConfiguradaOSinArancelNoSePuedePagar() throws Exception {
        Reserva r = reserva(1, 1, "bautizo", "pendiente");
        assertEquals(409, estado(() -> servicio.pagarReserva(1, String.valueOf(r.getIdReserva()), "plin", "123456", "987654321", png(), "ip")),
                "la parroquia no tiene Plin");
        Reserva gratis = reserva(1, 1, "primera_comunion", "pendiente");
        assertEquals(409, estado(() -> servicio.pagarReserva(1, String.valueOf(gratis.getIdReserva()), "yape", "123456", "987654321", png(), "ip")),
                "arancel en cero");
        Reserva sinArancel = reserva(1, 2, "bautizo", "pendiente");
        assertEquals(409, estado(() -> servicio.pagarReserva(1, String.valueOf(sinArancel.getIdReserva()), "yape", "123456", "987654321", png(), "ip")),
                "la parroquia 2 no definió aranceles");
    }

    @Test
    void conVariosPagosPendientesSeFrenaElEnvio() throws Exception {
        for (int i = 0; i < 5; i++) {
            pagarBautizo(1, reserva(1, 1, "bautizo", "pendiente"), "10000" + i);
        }
        Reserva otra = reserva(1, 1, "bautizo", "pendiente");
        assertEquals(429, estado(() -> pagarBautizo(1, otra, "999999")));
    }

    @Test
    void siLaBaseDeDatosFallaNoQuedaElArchivoSuelto() throws Exception {
        Reserva r = reserva(1, 1, "bautizo", "pendiente");
        pagos.fallar = new SQLException("caída");
        assertThrows(SQLException.class, () -> pagarBautizo(1, r, "123456"));
        assertEquals(0, archivosGuardados());
    }

    @Test
    void laRestriccionUniqueDeLaBaseDeDatosDecideLasCarreras() throws Exception {
        Reserva r = reserva(1, 1, "bautizo", "pendiente");
        pagos.fallar = new SQLIntegrityConstraintViolationException("Duplicate entry 'h' for key 'uq_pago_voucher'");
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> pagarBautizo(1, r, "123456"));
        assertEquals(409, e.getEstado());
        assertTrue(e.getMessage().contains("comprobante"));
        assertEquals(0, archivosGuardados());
    }

    // ------------------------------------------------------------ donaciones y diezmos

    private Pago donar(String concepto, String monto) throws Exception {
        return servicio.pagarLibre(1, "1", concepto, monto, "yape", String.valueOf(400000 + semilla.incrementAndGet()), "987654321", png(), "ip");
    }

    @Test
    void registraDonacionesYDiezmosConElMontoElegido() throws Exception {
        Pago d = donar("donacion", "25,50");
        assertEquals(new BigDecimal("25.50"), d.monto());
        assertNull(d.idReserva());
        assertEquals(Pago.DIEZMO, donar("DIEZMO", "100").concepto());
        assertEquals(2, pagos.lista.size());
    }

    @Test
    void validaElMontoYElConceptoDeLasDonaciones() {
        for (String malo : new String[]{"0", "0.5", "-5", "abc", "", null, "10.555", "10001", "1e3"}) {
            assertEquals(400, estado(() -> donar("donacion", malo)), "monto " + malo);
        }
        assertEquals(400, estado(() -> donar("arancel", "10")), "el arancel no se paga como donación");
        assertEquals(400, estado(() -> donar(null, "10")));
        assertEquals(404, estado(() -> servicio.pagarLibre(1, "99", "donacion", "10", "yape", "123456", "987654321", png(), "ip")));
    }

    // ------------------------------------------------------------ verificación del personal

    @Test
    void elPersonalApruebaYElFeligresRecibeUnAviso() throws Exception {
        Pago p = pagarBautizo(1, reserva(1, 1, "bautizo", "pendiente"), "123456");
        servicio.resolver(3, 1, String.valueOf(p.idPago()), true, "", "9.9.9.9");
        assertEquals(Pago.APROBADO, pagos.buscarPorId(p.idPago()).get().estado());
        assertTrue(bitacoraDao.hay("PAGO_APROBADO"));
        assertEquals(1, notificaciones.enviadas.size());
        assertEquals("1", notificaciones.enviadas.get(0)[0]);
        assertTrue(notificaciones.enviadas.get(0)[2].contains("50.00"));
    }

    @Test
    void elRechazoExigeMotivoYSeLeAvisaAlFeligres() throws Exception {
        Pago p = pagarBautizo(1, reserva(1, 1, "bautizo", "pendiente"), "123456");
        String id = String.valueOf(p.idPago());
        assertEquals(400, estado(() -> servicio.resolver(3, 1, id, false, "  ", "ip")));
        assertEquals(400, estado(() -> servicio.resolver(3, 1, id, false, "x".repeat(256), "ip")));
        servicio.resolver(3, 1, id, false, "No aparece en Yape", "ip");
        assertEquals(Pago.RECHAZADO, pagos.buscarPorId(p.idPago()).get().estado());
        assertTrue(notificaciones.enviadas.get(0)[2].contains("No aparece en Yape"));
    }

    @Test
    void unPagoSoloSeResuelveUnaVezYSoloEnSuParroquia() throws Exception {
        Pago p = pagarBautizo(1, reserva(1, 1, "bautizo", "pendiente"), "123456");
        String id = String.valueOf(p.idPago());
        assertEquals(404, estado(() -> servicio.resolver(9, 2, id, true, "", "ip")), "otra parroquia: el pago no existe");
        assertEquals(404, estado(() -> servicio.resolver(3, 1, "999", true, "", "ip")));
        servicio.resolver(3, 1, id, true, "", "ip");
        assertEquals(409, estado(() -> servicio.resolver(3, 1, id, false, "tarde", "ip")));
        assertEquals(Pago.APROBADO, pagos.buscarPorId(p.idPago()).get().estado());
    }

    @Test
    void siFallaElAvisoElPagoIgualQuedaResuelto() throws Exception {
        Pago p = pagarBautizo(1, reserva(1, 1, "bautizo", "pendiente"), "123456");
        notificaciones.fallar = true;
        servicio.resolver(3, 1, String.valueOf(p.idPago()), true, "", "ip");
        assertEquals(Pago.APROBADO, pagos.buscarPorId(p.idPago()).get().estado());
    }

    // ------------------------------------------------------------ consultas y comprobante

    @Test
    void alFeligresSeLeEnmascaraElCelularYAlPersonalSeLeMuestraCompleto() throws Exception {
        pagarBautizo(1, reserva(1, 1, "bautizo", "pendiente"), "123456");
        PagoVista propio = servicio.delUsuario(1).get(0);
        assertEquals("******321", propio.celular());
        assertEquals("Parroquia San José", propio.parroquia());
        assertTrue(propio.detalle().startsWith("Arancel de"));
        PagoVista delPanel = servicio.deParroquia(1, "pendiente").get(0);
        assertEquals("987654321", delPanel.celular());
        assertEquals("Ana Torres", delPanel.feligres());
        assertEquals(400, estado(() -> servicio.deParroquia(1, "inventado")));
        assertTrue(servicio.deParroquia(2, "pendiente").isEmpty(), "la otra parroquia no ve estos pagos");
        assertTrue(servicio.delUsuario(2).isEmpty(), "otro usuario no ve estos pagos");
    }

    @Test
    void elComprobanteLoAbreSoloSuDuenoOElPersonalDeLaParroquia() throws Exception {
        Pago p = pagarBautizo(1, reserva(1, 1, "bautizo", "pendiente"), "123456");
        String id = String.valueOf(p.idPago());
        assertNotNull(servicio.paraDescarga("feligres", 1, null, id));
        assertNotNull(servicio.paraDescarga("administrador", 3, 1, id));
        assertEquals(404, estado(() -> servicio.paraDescarga("feligres", 2, null, id)), "otro feligrés");
        assertEquals(404, estado(() -> servicio.paraDescarga("administrador", 9, 2, id)), "personal de otra parroquia");
        assertEquals(404, estado(() -> servicio.paraDescarga("administrador", 9, null, id)));
        assertEquals(404, estado(() -> servicio.paraDescarga("feligres", 1, null, "999")));
        assertEquals(400, estado(() -> servicio.paraDescarga("feligres", 1, null, "../etc/passwd")));
        assertEquals("image/png", servicio.paraDescarga("feligres", 1, null, id).tipo().mime);
    }

    // ------------------------------------------------------------ configuración

    @Test
    void guardaLaCuentaYapePlinDeLaParroquiaValidandoLosDatos() throws Exception {
        servicio.guardarCuenta(3, 1, "922 333 444", "Parroquia San José", "933444555", "P. Juan", "ip");
        CuentaPago c = servicio.cuentaDe(1);
        assertEquals("922333444", c.yapeNumero());
        assertEquals("933444555", c.numeroDe("plin"));
        assertTrue(bitacoraDao.hay("CUENTA_PAGO_EDITADA"));
        assertEquals(400, estado(() -> servicio.guardarCuenta(3, 1, "12345", "X", null, null, "ip")), "número inválido");
        assertEquals(400, estado(() -> servicio.guardarCuenta(3, 1, "922333444", "  ", null, null, "ip")), "falta el titular");
        assertEquals(400, estado(() -> servicio.guardarCuenta(3, 1, "922333444", "x".repeat(101), null, null, "ip")));
        servicio.guardarCuenta(3, 1, "", "", "", "", "ip");
        assertNull(servicio.cuentaDe(1).numeroDe("yape"), "dejar vacío desactiva el método");
        assertNull(servicio.cuentaDe(2).numeroDe("yape"), "parroquia sin cuenta");
    }

    @Test
    void guardaAranceles() throws Exception {
        servicio.guardarArancel(3, 1, "1", "65.50", "ip");
        assertEquals(new BigDecimal("65.50"), aranceles.montoDe(1, 1).get());
        assertTrue(bitacoraDao.hay("ARANCEL_EDITADO"));
        assertEquals(400, estado(() -> servicio.guardarArancel(3, 1, "1", "-1", "ip")));
        assertEquals(400, estado(() -> servicio.guardarArancel(3, 1, "1", "5000.01", "ip")));
        assertEquals(400, estado(() -> servicio.guardarArancel(3, 1, "1", "abc", "ip")));
        assertEquals(400, estado(() -> servicio.guardarArancel(3, 1, "77", "10", "ip")), "sacramento inexistente");
        servicio.guardarArancel(3, 1, "1", "0", "ip"); // gratuito
    }

    @Test
    void sumaLosIngresosAprobadosYValidaElMes() throws Exception {
        Pago a = pagarBautizo(1, reserva(1, 1, "bautizo", "pendiente"), "123456");
        servicio.resolver(3, 1, String.valueOf(a.idPago()), true, "", "ip");
        Pago d = donar("donacion", "20");
        servicio.resolver(3, 1, String.valueOf(d.idPago()), true, "", "ip");
        donar("donacion", "99"); // pendiente: no cuenta
        List<PagoDAO.FilaIngreso> ingresos = servicio.ingresos(1, "");
        assertEquals(2, ingresos.size());
        assertEquals(new BigDecimal("70.00"), ingresos.stream().map(PagoDAO.FilaIngreso::total).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(400, estado(() -> servicio.ingresos(1, "2026-13")));
        assertEquals(400, estado(() -> servicio.ingresos(1, "' OR 1=1 --")));
    }

    @Test
    void infoPagoMuestraElMontoOficialYElNumeroDeCobro() throws Exception {
        Reserva r = reserva(1, 1, "matrimonio", "pendiente");
        PagoService.InfoPago info = servicio.infoArancel(1, String.valueOf(r.getIdReserva()));
        assertEquals(new BigDecimal("250.00"), info.monto());
        assertEquals("911111111", info.cuenta().numeroDe("yape"));
        assertNull(info.pagoVigente());
        assertEquals(404, estado(() -> servicio.infoArancel(2, String.valueOf(r.getIdReserva()))), "reserva ajena");
        assertEquals("Parroquia San José", servicio.infoLibre("1").parroquia());
        assertEquals(404, estado(() -> servicio.infoLibre("99")));
    }
}
