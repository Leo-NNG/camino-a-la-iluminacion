package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.caminoiluminacion.dao.RequisitoDAO.Avance;
import pe.caminoiluminacion.modelo.Requisito;
import pe.caminoiluminacion.servicio.RequisitoService.Checklist;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Requisitos por sacramento: consulta pública, checklist de una solicitud, validación al subir y administración. */
class RequisitoServiceTest {

    @TempDir Path carpeta;
    RequisitoEntorno e;

    @BeforeEach
    void preparar() throws SQLException {
        e = new RequisitoEntorno(carpeta);
    }

    private int estado(org.junit.jupiter.api.function.Executable x) {
        return assertThrows(ReglaNegocioException.class, x).getEstado();
    }

    private String estadoDe(Checklist c, Requisito q) {
        return c.items().stream().filter(i -> i.requisito().getIdRequisito().equals(q.getIdRequisito())).findFirst().get().estado();
    }

    // ------------------------------------------------------------------ consulta pública

    @Test
    void elFeligresVeLosRequisitosDeLaParroquiaYElSacramentoEnOrden() throws SQLException {
        List<Requisito> lista = e.servicio.publicos("1", "bautizo");
        assertEquals(List.of("Partida de nacimiento", "DNI de los padres", "Charla prebautismal", "Confirmación del padrino"),
                lista.stream().map(Requisito::getNombre).toList());
        assertEquals(1, e.servicio.publicos("1", "matrimonio").size(), "solo los del sacramento pedido");
        assertEquals(1, e.servicio.publicos("2", "bautizo").size(), "cada parroquia tiene los suyos");
        assertTrue(e.servicio.publicos("3", "bautizo").isEmpty());
    }

    @Test
    void laConsultaPublicaValidaSusParametros() {
        assertEquals(400, estado(() -> e.servicio.publicos("x", "bautizo")));
        assertEquals(400, estado(() -> e.servicio.publicos("1", "inventado")));
        assertEquals(400, estado(() -> e.servicio.publicos(null, null)));
    }

    // ------------------------------------------------------------------ checklist

    @Test
    void sinDocumentosTodoFaltaYNoEstaCompleto() throws SQLException {
        Checklist c = e.servicio.checklist(e.ana, String.valueOf(e.reserva.getIdReserva()));
        assertEquals(4, c.items().size());
        assertTrue(c.items().stream().allMatch(i -> RequisitoService.FALTANTE.equals(i.estado())));
        assertEquals(3, c.obligatorios(), "el del padrino es solo recomendado");
        assertEquals(0, c.validados());
        assertFalse(c.completo());
    }

    @Test
    void cadaRequisitoMuestraLaSituacionDeSuDocumento() throws SQLException {
        e.documento(e.partida, "validado", null);
        e.documento(e.dni, "pendiente", null);
        var rechazado = e.documento(e.charla, "rechazado", "Está borrosa");
        Checklist c = e.servicio.checklist(e.ana, String.valueOf(e.reserva.getIdReserva()));
        assertEquals("validado", estadoDe(c, e.partida));
        assertEquals("pendiente", estadoDe(c, e.dni));
        assertEquals("rechazado", estadoDe(c, e.charla));
        assertEquals("faltante", estadoDe(c, e.padrino));
        var item = c.items().stream().filter(i -> i.requisito() == e.charla).findFirst().get();
        assertEquals("Está borrosa", item.motivo());
        assertEquals(rechazado.getIdDocumento(), item.idDocumento());
        assertEquals(1, c.validados());
        assertFalse(c.completo());
    }

    @Test
    void unDocumentoNuevoReemplazaAlRechazadoYElValidadoGanaSiempre() throws SQLException {
        e.documento(e.partida, "rechazado", "Ilegible");
        e.documento(e.partida, "pendiente", null);   // lo volvió a subir
        e.documento(e.dni, "validado", null);
        e.documento(e.dni, "rechazado", "Otro, más antiguo en el orden de envío pero posterior en la lista");
        Checklist c = e.servicio.checklist(e.ana, String.valueOf(e.reserva.getIdReserva()));
        assertEquals("pendiente", estadoDe(c, e.partida), "el rechazado anterior ya no cuenta");
        assertEquals("validado", estadoDe(c, e.dni), "un documento validado no se pierde por otro rechazado");
    }

    @Test
    void estaCompletoCuandoTodosLosObligatoriosEstanValidados() throws SQLException {
        e.documento(e.partida, "validado", null);
        e.documento(e.dni, "validado", null);
        assertFalse(e.servicio.checklist(e.ana, "1").completo());
        e.documento(e.charla, "validado", null);          // el recomendado (padrino) no hace falta
        Checklist c = e.servicio.checklist(e.ana, "1");
        assertTrue(c.completo());
        assertEquals(3, c.validados());
    }

    @Test
    void sinRequisitosObligatoriosLaSolicitudYaEstaCompleta() throws SQLException {
        e.requisitos.lista.removeIf(q -> q.getIdParroquia() == 1 && q.getIdSacramento() == 1 && q.isObligatorio());
        Checklist c = e.servicio.checklist(e.ana, "1");
        assertEquals(0, c.obligatorios());
        assertTrue(c.completo());
    }

    @Test
    void soloElDuenoVeElChecklistDeSuSolicitud() throws SQLException {
        assertEquals(404, estado(() -> e.servicio.checklist(e.otra, "1")), "la solicitud de otra persona no existe para ti");
        assertEquals(404, estado(() -> e.servicio.checklist(e.ana, "99")));
        assertEquals(400, estado(() -> e.servicio.checklist(e.ana, "abc")));
        e.reserva.setEstado("rechazada");
        assertEquals(409, estado(() -> e.servicio.checklist(e.ana, "1")), "una solicitud rechazada ya no recibe documentos");
        e.reserva.setEstado("cancelada");
        assertEquals(409, estado(() -> e.servicio.checklist(e.ana, "1")), "ni una cancelada");
    }

    // ------------------------------------------------------------------ validar al subir

    @Test
    void sinRequisitoElDocumentoSigueSiendoLibre() throws SQLException {
        assertNull(e.servicio.validarParaSubida(e.reserva, null));
        assertNull(e.servicio.validarParaSubida(e.reserva, "  "));
    }

    @Test
    void aceptaUnRequisitoDeLaMismaParroquiaYSacramento() throws SQLException {
        assertEquals(e.partida.getIdRequisito(), e.servicio.validarParaSubida(e.reserva, String.valueOf(e.partida.getIdRequisito())));
    }

    @Test
    void rechazaRequisitosQueNoSonDeEstaSolicitud() {
        int deMatrimonio = e.requisitos.lista.stream().filter(q -> q.getIdSacramento() == 2).findFirst().get().getIdRequisito();
        int deOtraParroquia = e.requisitos.lista.stream().filter(q -> q.getIdParroquia() == 2).findFirst().get().getIdRequisito();
        assertEquals(400, estado(() -> e.servicio.validarParaSubida(e.reserva, String.valueOf(deMatrimonio))));
        assertEquals(400, estado(() -> e.servicio.validarParaSubida(e.reserva, String.valueOf(deOtraParroquia))));
        assertEquals(400, estado(() -> e.servicio.validarParaSubida(e.reserva, "999")));
        assertEquals(400, estado(() -> e.servicio.validarParaSubida(e.reserva, "abc")));
    }

    @Test
    void noSeAceptaOtroDocumentoSiYaHayUnoPendienteOValidadoPeroSiTrasUnRechazo() throws SQLException {
        String id = String.valueOf(e.partida.getIdRequisito());
        e.documento(e.partida, "pendiente", null);
        assertEquals(409, estado(() -> e.servicio.validarParaSubida(e.reserva, id)));
        e.documentos.lista.clear();
        e.documento(e.partida, "validado", null);
        assertEquals(409, estado(() -> e.servicio.validarParaSubida(e.reserva, id)));
        e.documentos.lista.clear();
        e.documento(e.partida, "rechazado", "Ilegible");
        assertEquals(e.partida.getIdRequisito(), e.servicio.validarParaSubida(e.reserva, id), "tras un rechazo se puede reemplazar");
    }

    // ------------------------------------------------------------------ administración

    @Test
    void creaUnRequisitoNuevoAlFinalDeLaListaYQuedaEnBitacora() throws SQLException {
        Requisito r = e.servicio.guardar(9, 1, "", "bautizo", "  Certificado médico ", " Del niño ", "false", "", "ip");
        assertEquals("Certificado médico", r.getNombre());
        assertEquals("Del niño", r.getDescripcion());
        assertFalse(r.isObligatorio());
        assertEquals(5, r.getOrden(), "va después de los 4 que ya había");
        assertEquals(5, e.servicio.deParroquia(1).stream().filter(q -> q.getIdSacramento() == 1).count());
        assertTrue(e.bitacoraDao.hay("REQUISITO_CREADO"));
    }

    @Test
    void laDescripcionEsOpcionalYElObligatorioPorDefectoEsSi() throws SQLException {
        Requisito r = e.servicio.guardar(9, 1, null, "bautizo", "Foto del niño", null, null, null, "ip");
        assertNull(r.getDescripcion());
        assertTrue(r.isObligatorio());
    }

    @Test
    void soloSeCreanRequisitosParaSacramentosQueLaParroquiaOfrece() {
        // la parroquia 1 ofrece bautizo, matrimonio y primera comunión, no confirmación
        assertEquals(400, estado(() -> e.servicio.guardar(9, 1, "", "confirmacion", "Constancia", null, "true", null, "ip")));
        assertEquals(400, estado(() -> e.servicio.guardar(9, 1, "", "inventado", "Constancia", null, "true", null, "ip")));
    }

    @Test
    void validaNombreDescripcionObligatorioYOrden() {
        assertEquals(400, estado(() -> e.servicio.guardar(9, 1, "", "bautizo", "ab", null, "true", null, "ip")));
        assertEquals(400, estado(() -> e.servicio.guardar(9, 1, "", "bautizo", null, null, "true", null, "ip")));
        assertEquals(400, estado(() -> e.servicio.guardar(9, 1, "", "bautizo", "x".repeat(81), null, "true", null, "ip")));
        assertEquals(400, estado(() -> e.servicio.guardar(9, 1, "", "bautizo", "Nombre válido", "d".repeat(256), "true", null, "ip")));
        assertEquals(400, estado(() -> e.servicio.guardar(9, 1, "", "bautizo", "Nombre válido", null, "quizas", null, "ip")));
        assertEquals(400, estado(() -> e.servicio.guardar(9, 1, "", "bautizo", "Nombre válido", null, "true", "0", "ip")));
        assertEquals(400, estado(() -> e.servicio.guardar(9, 1, "", "bautizo", "Nombre válido", null, "true", "100", "ip")));
        assertEquals(400, estado(() -> e.servicio.guardar(9, 1, "", "bautizo", "Nombre válido", null, "true", "uno", "ip")));
    }

    @Test
    void unNombreRepetidoEnElMismoSacramentoDaConflicto() {
        assertEquals(409, estado(() -> e.servicio.guardar(9, 1, "", "bautizo", "dni de los padres", null, "true", null, "ip")));
    }

    @Test
    void hayUnTopeDeRequisitosPorSacramento() throws SQLException {
        for (int i = 5; i <= 15; i++) e.servicio.guardar(9, 1, "", "bautizo", "Requisito " + i, null, "false", null, "ip");
        assertEquals(409, estado(() -> e.servicio.guardar(9, 1, "", "bautizo", "Requisito 16", null, "false", null, "ip")));
    }

    @Test
    void editaNombreDescripcionObligatorioYOrdenSinCambiarElSacramento() throws SQLException {
        Requisito r = e.servicio.guardar(9, 1, String.valueOf(e.padrino.getIdRequisito()), "matrimonio", "Constancia del padrino", "Si corresponde", "true", "2", "ip");
        assertEquals("Constancia del padrino", r.getNombre());
        assertTrue(r.isObligatorio());
        assertEquals(2, r.getOrden());
        assertEquals(1, r.getIdSacramento(), "el sacramento no cambia al editar, aunque se envíe otro");
        assertTrue(e.bitacoraDao.hay("REQUISITO_MODIFICADO"));
    }

    @Test
    void alEditarSinOrdenSeConservaElQueTenia() throws SQLException {
        Requisito r = e.servicio.guardar(9, 1, String.valueOf(e.charla.getIdRequisito()), null, "Charla prebautismal nueva", null, "true", "", "ip");
        assertEquals(3, r.getOrden());
    }

    @Test
    void unRequisitoDeOtraParroquiaNoSePuedeEditarNiEliminar() throws SQLException {
        int ajeno = e.requisitos.lista.stream().filter(q -> q.getIdParroquia() == 2).findFirst().get().getIdRequisito();
        assertEquals(404, estado(() -> e.servicio.guardar(9, 1, String.valueOf(ajeno), null, "Intento ajeno", null, "true", null, "ip")));
        assertEquals(404, estado(() -> e.servicio.eliminar(9, 1, String.valueOf(ajeno), "ip")));
        assertEquals(404, estado(() -> e.servicio.eliminar(9, 1, "999", "ip")));
        assertEquals(400, estado(() -> e.servicio.eliminar(9, 1, "abc", "ip")));
    }

    @Test
    void eliminaUnRequisitoSinUsoPeroNoUnoQueYaTieneDocumentos() throws SQLException {
        e.documento(e.partida, "pendiente", null);
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> e.servicio.eliminar(9, 1, String.valueOf(e.partida.getIdRequisito()), "ip"));
        assertEquals(409, ex.getEstado());
        assertTrue(ex.getMessage().contains("1 documento"));
        e.servicio.eliminar(9, 1, String.valueOf(e.padrino.getIdRequisito()), "ip");
        assertTrue(e.requisitos.buscarPorId(e.padrino.getIdRequisito()).isEmpty());
        assertTrue(e.bitacoraDao.hay("REQUISITO_ELIMINADO"));
    }

    // ------------------------------------------------------------------ avance para el panel

    @Test
    void elAvanceDeLasSolicitudesDeLaParroquiaCuentaSoloObligatoriosValidados() throws SQLException {
        e.documento(e.partida, "validado", null);
        e.documento(e.dni, "pendiente", null);       // pendiente no cuenta
        e.documento(e.padrino, "validado", null);    // recomendado no cuenta
        Map<Integer, Avance> avance = e.servicio.avance(1);
        assertEquals(new Avance(3, 1), avance.get(e.reserva.getIdReserva()));
        assertFalse(avance.get(e.reserva.getIdReserva()).completo());
        assertTrue(e.servicio.avance(2).isEmpty(), "otra parroquia no ve las solicitudes de esta");
    }
}
