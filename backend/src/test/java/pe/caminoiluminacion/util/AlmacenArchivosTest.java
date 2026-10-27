package pe.caminoiluminacion.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.util.AlmacenArchivos.Tipo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AlmacenArchivosTest {

    private Path dir;
    private AlmacenArchivos almacen;

    @BeforeEach
    void preparar() throws IOException {
        dir = Files.createTempDirectory("almacen_test");
        almacen = new AlmacenArchivos(dir);
    }

    private static byte[] bytes(String t) { return t.getBytes(StandardCharsets.ISO_8859_1); }

    @Test
    void detectaElTipoPorLaFirmaDelArchivo() {
        assertEquals(Tipo.PDF, AlmacenArchivos.detectar(bytes("%PDF-1.7\n...")));
        assertEquals(Tipo.JPEG, AlmacenArchivos.detectar(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertEquals(Tipo.PNG, AlmacenArchivos.detectar(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}));
    }

    @Test
    void rechazaTodoLoQueNoSeaPdfJpgOPng() {
        for (String falso : new String[]{"<html><script>alert(1)</script>", "MZ\u0090\u0000\u0003", "<svg onload=alert(1)>", "GIF89a....", "  %PDF-1.4", "PK\u0003\u0004", "%PDF"}) {
            assertNull(AlmacenArchivos.detectar(bytes(falso)), falso);
        }
        assertNull(AlmacenArchivos.detectar(new byte[0]));
        assertNull(AlmacenArchivos.detectar(null));
    }

    @Test
    void guardaConNombreAleatorioYLoPuedeResolver() throws IOException {
        String nombre = almacen.guardar(bytes("%PDF-1.4 x"), Tipo.PDF);
        assertTrue(nombre.matches("[a-f0-9]{32}\\.pdf"));
        assertNotNull(almacen.resolver(nombre));
        assertFalse(nombre.equals(almacen.guardar(bytes("%PDF-1.4 x"), Tipo.PDF)));
    }

    @Test
    void soloResuelveNombresGeneradosPorElAlmacen() throws IOException {
        String valido = almacen.guardar(bytes("%PDF-1.4 x"), Tipo.PDF);
        String[] malos = {"../x.pdf", "../../etc/passwd", "/uploads/doc1.pdf", "/etc/passwd", valido.toUpperCase(),
                valido + "/..", "a.pdf", "", "..\\x.pdf", valido.replace(".pdf", ".html")};
        for (String malo : malos) assertNull(almacen.resolver(malo), malo);
        assertNull(almacen.resolver(null));
    }

    @Test
    void noResuelveUnNombreValidoQueNoExiste() {
        assertNull(almacen.resolver("0123456789abcdef0123456789abcdef.pdf"));
    }

    @Test
    void borrarEliminaElArchivoYTolerarNombresInvalidos() throws IOException {
        String nombre = almacen.guardar(bytes("%PDF-1.4 x"), Tipo.PDF);
        almacen.borrar(nombre);
        assertNull(almacen.resolver(nombre));
        almacen.borrar("../../algo");
        almacen.borrar(null);
    }

    @Test
    void mimeSeConvierteEnTipoSoloParaLosPermitidos() {
        assertEquals(Tipo.PNG, Tipo.porMime("image/png"));
        assertNull(Tipo.porMime("text/html"));
        assertNull(Tipo.porMime(null));
    }

    @Test
    void detectaPdfConContenidoActivo() {
        assertTrue(AlmacenArchivos.tieneContenidoActivo(bytes("%PDF-1.4\n1 0 obj << /S /JavaScript /JS (app.alert(1)) >> endobj")));
        assertTrue(AlmacenArchivos.tieneContenidoActivo(bytes("%PDF-1.4\n<< /Type /Action /S /Launch /F (cmd.exe) >>")));
        assertTrue(AlmacenArchivos.tieneContenidoActivo(bytes("%PDF-1.7\n<< /Type /EmbeddedFile >>")));
        assertFalse(AlmacenArchivos.tieneContenidoActivo(bytes("%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj")));
        assertFalse(AlmacenArchivos.tieneContenidoActivo(bytes("%PDF-1.4 ... /JSON no es una accion ...")));
        assertFalse(AlmacenArchivos.tieneContenidoActivo(null));
    }
}
