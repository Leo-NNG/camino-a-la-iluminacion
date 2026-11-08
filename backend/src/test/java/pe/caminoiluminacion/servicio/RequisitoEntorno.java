package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.controlador.Pruebas;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.controlador.Pruebas.FakeParroquias;
import pe.caminoiluminacion.controlador.Pruebas.FakeReservas;
import pe.caminoiluminacion.controlador.Pruebas.FakeSacramentos;
import pe.caminoiluminacion.controlador.Pruebas.FakeUsuarios;
import pe.caminoiluminacion.dao.RequisitoDAO;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Requisito;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.util.AlmacenArchivos;

import java.nio.file.Path;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Armado de {@link RequisitoService} y {@link DocumentoService} con DAO falsos en memoria, compartido por las pruebas
 * del servicio y de los servlets. El SQL real (cumplimientos, avance, unicidad) se verifica aparte contra una base real.
 */
public class RequisitoEntorno {

    /** Requisitos en memoria; los cumplimientos y el avance se calculan sobre los documentos falsos. */
    public static class FakeRequisitos implements RequisitoDAO {
        final List<Requisito> lista = new ArrayList<>();
        final List<Documento> documentos;
        final FakeReservas reservas;
        int sig = 1;

        FakeRequisitos(List<Documento> documentos, FakeReservas reservas) { this.documentos = documentos; this.reservas = reservas; }

        public Requisito insertar(Requisito r) throws SQLException {
            if (lista.stream().anyMatch(x -> x.getIdParroquia() == r.getIdParroquia() && x.getIdSacramento() == r.getIdSacramento()
                    && x.getNombre().equalsIgnoreCase(r.getNombre()))) throw new SQLIntegrityConstraintViolationException("duplicado", "23000", 1062);
            r.setIdRequisito(sig++);
            r.setCodigoSacramento(codigo(r.getIdSacramento()));
            r.setNombreSacramento(codigo(r.getIdSacramento()));
            lista.add(r);
            return r;
        }
        static String codigo(int id) { return new String[]{"", "bautizo", "matrimonio", "primera_comunion", "confirmacion"}[id]; }
        public Optional<Requisito> buscarPorId(int id) { return lista.stream().filter(r -> r.getIdRequisito() == id).findFirst(); }
        public List<Requisito> listar(int p, Integer s) {
            return lista.stream().filter(r -> r.getIdParroquia() == p && (s == null || r.getIdSacramento() == s))
                    .sorted(java.util.Comparator.comparingInt(Requisito::getIdSacramento).thenComparingInt(Requisito::getOrden)).toList();
        }
        public boolean actualizar(int id, String nombre, String desc, boolean obligatorio, int orden) throws SQLException {
            Requisito r = buscarPorId(id).orElse(null);
            if (r == null) return false;
            if (lista.stream().anyMatch(x -> x != r && x.getIdParroquia() == r.getIdParroquia() && x.getIdSacramento() == r.getIdSacramento()
                    && x.getNombre().equalsIgnoreCase(nombre))) throw new SQLIntegrityConstraintViolationException("duplicado", "23000", 1062);
            r.setNombre(nombre); r.setDescripcion(desc); r.setObligatorio(obligatorio); r.setOrden(orden);
            return true;
        }
        public boolean eliminar(int id) { return lista.removeIf(r -> r.getIdRequisito() == id); }
        public int contarDocumentos(int id) { return (int) documentos.stream().filter(d -> d.getIdRequisito() != null && d.getIdRequisito() == id).count(); }
        public List<Cumplimiento> cumplimientos(int idReserva) {
            List<Cumplimiento> x = new ArrayList<>();
            for (int i = documentos.size() - 1; i >= 0; i--) { // el más reciente primero
                Documento d = documentos.get(i);
                if (d.getIdReserva() != null && d.getIdReserva() == idReserva && d.getIdRequisito() != null) {
                    x.add(new Cumplimiento(d.getIdRequisito(), d.getIdDocumento(), d.getEstadoValidacion(), d.getMotivoRechazo()));
                }
            }
            return x;
        }
        public Map<Integer, Avance> avanceDeParroquia(int p) {
            Map<Integer, Avance> m = new HashMap<>();
            for (Reserva r : reservas.lista) {
                if (r.getIdParroquia() != p || "rechazada".equals(r.getEstado())) continue;
                int sac = java.util.List.of("bautizo", "matrimonio", "primera_comunion", "confirmacion").indexOf(r.getSacramento()) + 1;
                List<Requisito> obl = listar(p, sac).stream().filter(Requisito::isObligatorio).toList();
                long val = obl.stream().filter(q -> documentos.stream().anyMatch(d -> d.getIdReserva() != null && d.getIdReserva().equals(r.getIdReserva())
                        && d.getIdRequisito() != null && d.getIdRequisito().equals(q.getIdRequisito()) && "validado".equals(d.getEstadoValidacion()))).count();
                m.put(r.getIdReserva(), new Avance(obl.size(), (int) val));
            }
            return m;
        }
    }

    /** Un DocumentoDAO en memoria (el de AvisosDeEstadoTest) que además respeta id_requisito. */
    public final AvisosDeEstadoTest.FakeDocumentos documentos = new AvisosDeEstadoTest.FakeDocumentos();
    public final FakeReservas reservas = new FakeReservas();
    public final FakeUsuarios usuarios = new FakeUsuarios();
    public final FakeBitacora bitacoraDao = new FakeBitacora();
    public final FakeSacramentos sacramentos = new FakeSacramentos();
    public final FakeRequisitos requisitos = new FakeRequisitos(documentos.lista, reservas);
    public final AlmacenArchivos almacen;
    public final RequisitoService servicio;
    public final DocumentoService documentoService;
    public final int ana;          // feligrés dueño de la reserva
    public final int otra;         // otra feligresa
    public final Reserva reserva;  // bautizo, parroquia 1, de Ana
    public final Requisito partida, dni, charla, padrino; // requisitos de bautizo en la parroquia 1: 2 obligatorios + 2 (charla obligatorio, padrino recomendado)

    public RequisitoEntorno(Path carpeta) throws SQLException {
        BitacoraService bitacora = new BitacoraService(bitacoraDao);
        almacen = new AlmacenArchivos(carpeta);
        ana = usuarios.insertar(Pruebas.usuario("70000001", "ana@x.pe", "Clave123", "feligres", null)).getIdUsuario();
        otra = usuarios.insertar(Pruebas.usuario("70000002", "otra@x.pe", "Clave456", "feligres", null)).getIdUsuario();
        servicio = new RequisitoService(requisitos, sacramentos, reservas, bitacora);
        documentoService = new DocumentoService(documentos, reservas, new FakeParroquias(), usuarios, almacen, bitacora, Avisador.ninguno(), servicio);
        reserva = reservas.insertar(new Reserva(ana, 1, "bautizo", "Bebé Uno", Date.valueOf("2026-12-20"), null));
        partida = requisitos.insertar(new Requisito(1, 1, "Partida de nacimiento", "Copia legible", true, 1));
        dni = requisitos.insertar(new Requisito(1, 1, "DNI de los padres", null, true, 2));
        charla = requisitos.insertar(new Requisito(1, 1, "Charla prebautismal", null, true, 3));
        padrino = requisitos.insertar(new Requisito(1, 1, "Confirmación del padrino", null, false, 4));
        requisitos.insertar(new Requisito(1, 2, "Partida de bautismo", null, true, 1));   // matrimonio, parroquia 1
        requisitos.insertar(new Requisito(2, 1, "Otro requisito de otra parroquia", null, true, 1)); // bautizo, parroquia 2
    }

    /** Un documento ya enviado para un requisito, en el estado indicado. */
    public Documento documento(Requisito q, String estado, String motivo) {
        Documento d = new Documento(reserva.getIdReserva(), ana, "doc" + documentos.lista.size() + ".pdf", "x.pdf");
        d.setIdRequisito(q.getIdRequisito());
        documentos.insertar(d);
        d.setEstadoValidacion(estado);
        d.setMotivoRechazo(motivo);
        return d;
    }
}
