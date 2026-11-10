package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.caminoiluminacion.controlador.Pruebas.FakeParroquias;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.util.AlmacenArchivos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Al subir un documento se puede indicar qué requisito del checklist cumple; el servidor lo valida. */
class DocumentoRequisitoTest {

    @TempDir Path carpeta;
    RequisitoEntorno e;
    final byte[] pdf = Arrays.copyOf("%PDF-1.4\nhola".getBytes(StandardCharsets.US_ASCII), 30);

    @BeforeEach
    void preparar() throws SQLException {
        e = new RequisitoEntorno(carpeta);
    }

    private Documento subir(String requisito) throws SQLException, IOException {
        return e.documentoService.subir(e.ana, "1", requisito, new ByteArrayInputStream(pdf), "partida.pdf", "ip");
    }

    private long archivosGuardados() throws IOException {
        try (Stream<Path> s = Files.list(carpeta)) { return s.count(); }
    }

    private int estado(org.junit.jupiter.api.function.Executable x) {
        return assertThrows(ReglaNegocioException.class, x).getEstado();
    }

    @Test
    void elDocumentoQuedaVinculadoAlRequisitoYElChecklistLoMuestraPendiente() throws Exception {
        Documento d = subir(String.valueOf(e.partida.getIdRequisito()));
        assertEquals(e.partida.getIdRequisito(), d.getIdRequisito());
        var item = e.servicio.checklist(e.ana, "1").items().stream().filter(i -> i.requisito() == e.partida).findFirst().get();
        assertEquals("pendiente", item.estado());
        assertEquals(d.getIdDocumento(), item.idDocumento());
    }

    @Test
    void sinIndicarRequisitoSigueFuncionandoComoAntes() throws Exception {
        assertNull(subir(null).getIdRequisito());
        assertNull(e.documentoService.subir(e.ana, "1", new ByteArrayInputStream(pdf), "otro.pdf", "ip").getIdRequisito(), "la firma anterior sigue disponible");
        assertNull(subir("").getIdRequisito());
    }

    @Test
    void unRequisitoInvalidoSeRechazaSinGuardarElArchivo() throws Exception {
        assertEquals(400, estado(() -> subir("999")));
        assertEquals(400, estado(() -> subir("abc")));
        assertEquals(0, archivosGuardados(), "no queda ningún archivo en el disco");
        assertTrue(e.documentos.lista.isEmpty(), "ni documento registrado");
    }

    @Test
    void noSePuedeSubirOtroDocumentoParaUnRequisitoYaPendientePeroSiTrasRechazarlo() throws Exception {
        String id = String.valueOf(e.dni.getIdRequisito());
        Documento primero = subir(id);
        assertEquals(409, estado(() -> subir(id)));
        assertEquals(1, archivosGuardados(), "el intento rechazado no dejó otro archivo");
        e.documentos.rechazar(primero.getIdDocumento(), "Ilegible");
        Documento segundo = subir(id);
        assertEquals(e.dni.getIdRequisito(), segundo.getIdRequisito());
        assertEquals("pendiente", e.servicio.checklist(e.ana, "1").items().stream().filter(i -> i.requisito() == e.dni).findFirst().get().estado());
    }

    @Test
    void aUnaSolicitudCanceladaNoSeLeSubenDocumentos() throws Exception {
        e.reserva.setEstado("cancelada");
        assertEquals(409, estado(() -> subir(String.valueOf(e.partida.getIdRequisito()))));
        assertEquals(409, estado(() -> subir(null)));
        assertEquals(0, archivosGuardados());
    }

    @Test
    void elRequisitoDeOtraSolicitudNoSirve() {
        int deMatrimonio = e.requisitos.lista.stream().filter(q -> q.getIdSacramento() == 2).findFirst().get().getIdRequisito();
        assertEquals(400, estado(() -> subir(String.valueOf(deMatrimonio))));
    }

    @Test
    void sinServicioDeRequisitosNoSePuedeIndicarUnoPeroSiSubirDocumentosLibres() throws Exception {
        DocumentoService sinChecklist = new DocumentoService(e.documentos, e.reservas, new FakeParroquias(), e.usuarios,
                new AlmacenArchivos(carpeta), new BitacoraService(e.bitacoraDao));
        assertEquals(400, assertThrows(ReglaNegocioException.class,
                () -> sinChecklist.subir(e.ana, "1", "1", new ByteArrayInputStream(pdf), "x.pdf", "ip")).getEstado());
        assertNotNull(sinChecklist.subir(e.ana, "1", new ByteArrayInputStream(pdf), "x.pdf", "ip"));
    }

    @Test
    void laSolicitudDeOtraPersonaSigueSiendoInexistenteAunConRequisito() {
        assertEquals(404, estado(() -> e.documentoService.subir(e.otra, "1", String.valueOf(e.partida.getIdRequisito()),
                new ByteArrayInputStream(pdf), "x.pdf", "ip")));
    }
}
