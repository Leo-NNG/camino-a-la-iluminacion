package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Requisito;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Requisitos (documentos que pide cada parroquia por sacramento) y cuáles de ellos cumple cada trámite. */
public interface RequisitoDAO {

    /** Un documento enviado para cumplir un requisito de una solicitud. */
    record Cumplimiento(int idRequisito, int idDocumento, String estado, String motivo) { }

    /** Avance de una solicitud: cuántos requisitos obligatorios hay y cuántos ya tienen un documento validado. */
    record Avance(int obligatorios, int validados) {
        public boolean completo() { return validados >= obligatorios; }
    }

    /** @throws java.sql.SQLIntegrityConstraintViolationException si la parroquia ya tiene un requisito con ese nombre para ese sacramento */
    Requisito insertar(Requisito requisito) throws SQLException;

    /** Incluye el código y el nombre del sacramento. */
    Optional<Requisito> buscarPorId(int idRequisito) throws SQLException;

    /** Requisitos de la parroquia, ordenados por sacramento y orden; {@code idSacramento} null = todos los sacramentos. */
    List<Requisito> listar(int idParroquia, Integer idSacramento) throws SQLException;

    /** @throws java.sql.SQLIntegrityConstraintViolationException si el nuevo nombre ya existe en ese sacramento de la parroquia */
    boolean actualizar(int idRequisito, String nombre, String descripcion, boolean obligatorio, int orden) throws SQLException;

    boolean eliminar(int idRequisito) throws SQLException;

    /** Cuántos documentos (de cualquier estado) se enviaron para cumplir este requisito. */
    int contarDocumentos(int idRequisito) throws SQLException;

    /** Documentos de la solicitud que indican un requisito, del más reciente al más antiguo. */
    List<Cumplimiento> cumplimientos(int idReserva) throws SQLException;

    /** Avance de las solicitudes activas (pendientes o confirmadas) de la parroquia, por id de reserva. */
    Map<Integer, Avance> avanceDeParroquia(int idParroquia) throws SQLException;
}
