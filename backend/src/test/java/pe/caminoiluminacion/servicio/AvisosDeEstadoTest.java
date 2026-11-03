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
import pe.caminoiluminacion.dao.DocumentoDAO;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.servicio.PagoServiceTest.FakeNotificaciones;
import pe.caminoiluminacion.util.AlmacenArchivos;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** RF13: el feligrés recibe un aviso cuando la parroquia resuelve su reserva o su documento. */
class AvisosDeEstadoTest {

    static class FakeDocumentos implements DocumentoDAO {
        final List<Documento> lista = new ArrayList<>();
        int sig = 1;
        public Documento insertar(Documento d) { d.setIdDocumento(sig++); d.setEstadoValidacion("pendiente"); lista.add(d); return d; }
        public List<Documento> listarPorUsuario(int id) { return lista.stream().filter(d -> d.getIdUsuario() == id).toList(); }
        public List<Documento> listarPendientes() { return lista; }
        public List<Documento> listarPendientesPorParroquia(int p) { return lista; }
        public Optional<Documento> buscarPorId(int id) { return lista.stream().filter(d -> d.getIdDocumento() == id).findFirst(); }
        public boolean resolverPendiente(int id, String estado, String motivo, int idValidador) {
            Documento d = buscarPorId(id).orElse(null);
            if (d == null || !"pendiente".equals(d.getEstadoValidacion())) return false;
            d.setEstadoValidacion(estado);
            d.setMotivoRechazo(motivo);
            d.setIdValidador(idValidador);
            return true;
        }
        public boolean validar(int id) { return resolverPendiente(id, "validado", null, 0); }
        public boolean rechazar(int id, String m) { return resolverPendiente(id, "rechazado", m, 0); }
    }

    @TempDir Path carpeta;
    FakeReservas reservas;
    FakeNotificaciones notificaciones;
    FakeDocumentos documentos;
    ReservaService reservaService;
    DocumentoService documentoService;
    Reserva reserva;

    @BeforeEach
    void preparar() throws SQLException {
        reservas = new FakeReservas();
        notificaciones = new FakeNotificaciones();
        documentos = new FakeDocumentos();
        FakeUsuarios usuarios = new FakeUsuarios();
        usuarios.insertar(Pruebas.usuario("70000001", "ana@x.pe", "Clave123", "feligres", null)); // 1
        BitacoraService bitacora = new BitacoraService(new FakeBitacora());
        Avisador avisador = new Avisador(notificaciones);
        reservaService = new ReservaService(reservas, new FakeParroquias(), new FakeSacramentos(), usuarios, bitacora,
                () -> LocalDate.of(2026, 10, 8), avisador);
        documentoService = new DocumentoService(documentos, reservas, new FakeParroquias(), usuarios, new AlmacenArchivos(carpeta), bitacora, avisador);
        reserva = reservaService.crear(1, "Bautizo", "1", "Bebé Uno", "2026-10-20", "", "ip");
    }

    private Documento documento() {
        Documento d = new Documento(reserva.getIdReserva(), 1, "acta.pdf", "x.pdf");
        return documentos.insertar(d);
    }

    private int estado(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(ReglaNegocioException.class, e).getEstado();
    }

    // ------------------------------------------------------------ reservas

    @Test
    void confirmarUnaReservaAvisaAlFeligres() throws Exception {
        reservaService.resolver(5, 1, String.valueOf(reserva.getIdReserva()), "confirmada", "", "ip");
        assertEquals(1, notificaciones.enviadas.size());
        String[] aviso = notificaciones.enviadas.get(0);
        assertEquals("1", aviso[0], "va dirigido a quien hizo la solicitud");
        assertEquals("Reserva confirmada", aviso[1]);
        assertTrue(aviso[2].contains("Bautizo") && aviso[2].contains("Bebé Uno") && aviso[2].contains("20/10/2026")
                && aviso[2].contains("Parroquia San José"), aviso[2]);
    }

    @Test
    void rechazarUnaReservaAvisaConElMotivo() throws Exception {
        reservaService.resolver(5, 1, String.valueOf(reserva.getIdReserva()), "rechazada", "Esa fecha tiene misa solemne", "ip");
        assertEquals("Reserva rechazada", notificaciones.enviadas.get(0)[1]);
        assertTrue(notificaciones.enviadas.get(0)[2].contains("Esa fecha tiene misa solemne"));
    }

    @Test
    void siFallaElAvisoLaReservaIgualQuedaResuelta() throws Exception {
        notificaciones.fallar = true;
        reservaService.resolver(5, 1, String.valueOf(reserva.getIdReserva()), "confirmada", "", "ip");
        assertEquals("confirmada", reservas.buscarPorId(reserva.getIdReserva()).get().getEstado());
    }

    @Test
    void unaResolucionInvalidaNoEnviaAvisos() {
        String id = String.valueOf(reserva.getIdReserva());
        assertEquals(400, estado(() -> reservaService.resolver(5, 1, id, "rechazada", "  ", "ip")), "sin motivo");
        assertEquals(404, estado(() -> reservaService.resolver(9, 2, id, "confirmada", "", "ip")), "otra parroquia");
        assertEquals(400, estado(() -> reservaService.resolver(5, 1, id, "inventado", "", "ip")));
        assertTrue(notificaciones.enviadas.isEmpty());
    }

    @Test
    void unaReservaYaResueltaNoAvisaDosVeces() throws Exception {
        String id = String.valueOf(reserva.getIdReserva());
        reservaService.resolver(5, 1, id, "confirmada", "", "ip");
        assertEquals(409, estado(() -> reservaService.resolver(5, 1, id, "rechazada", "tarde", "ip")));
        assertEquals(1, notificaciones.enviadas.size());
    }

    @Test
    void sinAvisadorElServicioSigueFuncionando() throws Exception {
        ReservaService sinAvisos = new ReservaService(reservas, new FakeParroquias(), new FakeSacramentos(), new FakeUsuarios(),
                new BitacoraService(new FakeBitacora()), () -> LocalDate.of(2026, 10, 8));
        sinAvisos.resolver(5, 1, String.valueOf(reserva.getIdReserva()), "confirmada", "", "ip");
        assertTrue(notificaciones.enviadas.isEmpty());
    }

    // ------------------------------------------------------------ documentos

    @Test
    void validarUnDocumentoAvisaAlFeligres() throws Exception {
        Documento d = documento();
        documentoService.resolver(5, 1, String.valueOf(d.getIdDocumento()), "validado", "", "ip");
        String[] aviso = notificaciones.enviadas.get(0);
        assertEquals("1", aviso[0]);
        assertEquals("Documento validado", aviso[1]);
        assertTrue(aviso[2].contains("acta.pdf"));
    }

    @Test
    void rechazarUnDocumentoAvisaConElMotivo() throws Exception {
        Documento d = documento();
        documentoService.resolver(5, 1, String.valueOf(d.getIdDocumento()), "rechazado", "La foto está borrosa", "ip");
        assertEquals("Documento rechazado", notificaciones.enviadas.get(0)[1]);
        assertTrue(notificaciones.enviadas.get(0)[2].contains("La foto está borrosa"));
    }

    @Test
    void unDocumentoDeOtraParroquiaOYaResueltoNoEnviaAvisos() throws Exception {
        Documento d = documento();
        String id = String.valueOf(d.getIdDocumento());
        assertEquals(404, estado(() -> documentoService.resolver(9, 2, id, "validado", "", "ip")));
        assertEquals(400, estado(() -> documentoService.resolver(5, 1, id, "rechazado", "", "ip")));
        assertTrue(notificaciones.enviadas.isEmpty());
        documentoService.resolver(5, 1, id, "validado", "", "ip");
        assertEquals(409, estado(() -> documentoService.resolver(5, 1, id, "rechazado", "tarde", "ip")));
        assertEquals(1, notificaciones.enviadas.size());
    }

    @Test
    void siFallaElAvisoElDocumentoIgualQuedaResuelto() throws Exception {
        Documento d = documento();
        notificaciones.fallar = true;
        documentoService.resolver(5, 1, String.valueOf(d.getIdDocumento()), "validado", "", "ip");
        assertEquals("validado", documentos.buscarPorId(d.getIdDocumento()).get().getEstadoValidacion());
    }
}
