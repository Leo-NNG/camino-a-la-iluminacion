package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.DocumentoDAO;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.util.AlmacenArchivos;
import pe.caminoiluminacion.util.AlmacenArchivos.Tipo;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reglas de los documentos: subir (con validación del contenido), listar, descargar con permisos y validar. */
public class DocumentoService {

    private static final int MAX_DOCUMENTOS_POR_USUARIO = 50;

    public record DocumentoPropio(Documento documento, Reserva reserva, String parroquia) { }

    public record DocumentoPendiente(Documento documento, Reserva reserva, String feligres) { }

    public record Descarga(Documento documento, Tipo tipo, Path ruta) { }

    private final DocumentoDAO documentoDAO;
    private final ReservaDAO reservaDAO;
    private final ParroquiaDAO parroquiaDAO;
    private final UsuarioDAO usuarioDAO;
    private final AlmacenArchivos almacen;
    private final BitacoraService bitacora;

    public DocumentoService(DocumentoDAO documentoDAO, ReservaDAO reservaDAO, ParroquiaDAO parroquiaDAO,
                            UsuarioDAO usuarioDAO, AlmacenArchivos almacen, BitacoraService bitacora) {
        this.documentoDAO = documentoDAO;
        this.reservaDAO = reservaDAO;
        this.parroquiaDAO = parroquiaDAO;
        this.usuarioDAO = usuarioDAO;
        this.almacen = almacen;
        this.bitacora = bitacora;
    }

    /** Sube UN archivo a una solicitud del propio usuario. El tipo se decide por el contenido, no por la extensión. */
    public Documento subir(int idUsuario, String idReservaTxt, InputStream contenido, String nombreEnviado, String ip)
            throws SQLException, IOException {
        int idReserva;
        try {
            idReserva = Integer.parseInt(String.valueOf(idReservaTxt).trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "Elige a qué solicitud pertenece el documento.");
        }
        // La solicitud debe ser del propio usuario (si es de otro, se responde como si no existiera).
        Optional<Reserva> reserva = reservaDAO.buscarPorId(idReserva);
        if (reserva.isEmpty() || !reserva.get().getIdUsuario().equals(idUsuario)) throw new ReglaNegocioException(404, "La solicitud no existe.");
        if ("rechazada".equals(reserva.get().getEstado())) {
            throw new ReglaNegocioException(409, "Esa solicitud fue rechazada. Haz una nueva solicitud para enviar documentos.");
        }

        // Se lee con tope: no se confía en el tamaño que declare el cliente.
        byte[] datos = contenido.readNBytes(AlmacenArchivos.MAX_BYTES + 1);
        if (datos.length == 0) throw new ReglaNegocioException(400, "El archivo está vacío.");
        if (datos.length > AlmacenArchivos.MAX_BYTES) throw new ReglaNegocioException(413, "El archivo supera el máximo de 5 MB.");
        Tipo tipo = AlmacenArchivos.detectar(datos);
        if (tipo == null) throw new ReglaNegocioException(415, "Solo se aceptan archivos PDF, JPG o PNG.");
        if (documentoDAO.listarPorUsuario(idUsuario).size() >= MAX_DOCUMENTOS_POR_USUARIO) {
            throw new ReglaNegocioException(409, "Alcanzaste el máximo de documentos permitidos.");
        }

        String guardado = almacen.guardar(datos, tipo);
        try {
            Documento doc = new Documento(idReserva, idUsuario, nombreVisible(nombreEnviado, tipo), guardado);
            doc.setTipoMime(tipo.mime);
            doc.setTamanoBytes((long) datos.length);
            documentoDAO.insertar(doc);
            guardado = null; // ya quedó registrado
            bitacora.registrar(idUsuario, "DOCUMENTO_SUBIDO", "documento " + doc.getIdDocumento(), ip);
            return doc;
        } finally {
            if (guardado != null) almacen.borrar(guardado); // no dejar archivos sin registro si falló la base de datos
        }
    }

    /** Los documentos del propio usuario. */
    public List<DocumentoPropio> delUsuario(int idUsuario) throws SQLException {
        Map<Integer, String> parroquias = new HashMap<>();
        for (Parroquia p : parroquiaDAO.listarTodas()) parroquias.put(p.getIdParroquia(), p.getNombre());
        List<DocumentoPropio> lista = new ArrayList<>();
        for (Documento d : documentoDAO.listarPorUsuario(idUsuario)) {
            Optional<Reserva> r = d.getIdReserva() == null ? Optional.empty() : reservaDAO.buscarPorId(d.getIdReserva());
            lista.add(new DocumentoPropio(d, r.orElse(null), r.map(x -> parroquias.get(x.getIdParroquia())).orElse("")));
        }
        return lista;
    }

    /** Los documentos pendientes de revisión de UNA parroquia. */
    public List<DocumentoPendiente> pendientesDeParroquia(int idParroquia) throws SQLException {
        List<DocumentoPendiente> lista = new ArrayList<>();
        for (Documento d : documentoDAO.listarPendientesPorParroquia(idParroquia)) {
            Optional<Reserva> r = reservaDAO.buscarPorId(d.getIdReserva());
            Optional<Usuario> u = usuarioDAO.buscarPorId(d.getIdUsuario());
            lista.add(new DocumentoPendiente(d, r.orElse(null), u.map(x -> x.getNombre() + " " + x.getApellido()).orElse("")));
        }
        return lista;
    }

    /**
     * Autoriza y localiza el archivo de un documento. Solo lo abre su dueño (feligrés) o el administrador de la
     * parroquia de la solicitud. Quien no tenga permiso recibe 404 (no se revela que existe).
     */
    public Descarga paraDescarga(String rol, int idUsuario, Integer idParroquiaAdmin, String idTxt) throws SQLException {
        int id;
        try {
            id = Integer.parseInt(String.valueOf(idTxt).trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "Identificador no válido.");
        }
        Optional<Documento> docOpt = documentoDAO.buscarPorId(id);
        if (docOpt.isEmpty() || !puedeVer(rol, idUsuario, idParroquiaAdmin, docOpt.get())) {
            throw new ReglaNegocioException(404, "El documento no existe.");
        }
        Documento doc = docOpt.get();
        Tipo tipo = Tipo.porMime(doc.getTipoMime());
        Path ruta = almacen.resolver(doc.getRutaArchivo());
        if (tipo == null || ruta == null) throw new ReglaNegocioException(404, "El archivo no está disponible.");
        return new Descarga(doc, tipo, ruta);
    }

    /** Valida o rechaza un documento PENDIENTE de la parroquia del administrador. */
    public void resolver(int idAdmin, int idParroquiaAdmin, String idTxt, String estado, String motivo, String ip)
            throws SQLException {
        if (!"validado".equals(estado) && !"rechazado".equals(estado)) throw new ReglaNegocioException(400, "Estado no válido.");
        String razon = motivo == null ? "" : motivo.trim();
        if ("rechazado".equals(estado)) {
            if (razon.isEmpty()) throw new ReglaNegocioException(400, "Indica el motivo del rechazo: el feligrés lo verá.");
            if (razon.length() > 255) throw new ReglaNegocioException(400, "El motivo no puede superar los 255 caracteres.");
        }
        int id;
        try {
            id = Integer.parseInt(String.valueOf(idTxt).trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "Identificador no válido.");
        }
        Optional<Documento> doc = documentoDAO.buscarPorId(id);
        Optional<Reserva> reserva = (doc.isPresent() && doc.get().getIdReserva() != null)
                ? reservaDAO.buscarPorId(doc.get().getIdReserva()) : Optional.empty();
        // Un documento de otra parroquia se trata como inexistente.
        if (reserva.isEmpty() || reserva.get().getIdParroquia() != idParroquiaAdmin) throw new ReglaNegocioException(404, "El documento no existe.");
        if (!documentoDAO.resolverPendiente(id, estado, razon, idAdmin)) throw new ReglaNegocioException(409, "Este documento ya fue resuelto.");
        bitacora.registrar(idAdmin, "rechazado".equals(estado) ? "DOCUMENTO_RECHAZADO" : "DOCUMENTO_VALIDADO", "documento " + id, ip);
    }

    // ---------------------------------------------------------------- auxiliares

    private boolean puedeVer(String rol, int idUsuario, Integer idParroquiaAdmin, Documento doc) throws SQLException {
        if (Sesion.ROL_FELIGRES.equals(rol)) return doc.getIdUsuario().equals(idUsuario);
        if (Sesion.ROL_ADMIN.equals(rol) && idParroquiaAdmin != null && doc.getIdReserva() != null) {
            Optional<Reserva> r = reservaDAO.buscarPorId(doc.getIdReserva());
            return r.isPresent() && idParroquiaAdmin.equals(r.get().getIdParroquia());
        }
        return false;
    }

    /** Nombre que se muestra (solo informativo): sin ruta, sin caracteres de control y con largo acotado. */
    static String nombreVisible(String enviado, Tipo tipo) {
        String n = enviado == null ? "" : enviado;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);
        n = n.replaceAll("[\\p{Cntrl}]", "").trim();
        if (n.isEmpty()) n = "documento." + tipo.extension;
        if (n.length() > 120) n = n.substring(0, 120);
        return n;
    }
}
