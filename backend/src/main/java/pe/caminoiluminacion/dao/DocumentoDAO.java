package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Documento;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface DocumentoDAO {
    Documento insertar(Documento documento) throws SQLException;
    List<Documento> listarPorUsuario(int idUsuario) throws SQLException;
    List<Documento> listarPendientes() throws SQLException;
    Optional<Documento> buscarPorId(int idDocumento) throws SQLException;
    /** Documentos pendientes cuyas reservas pertenecen a esa parroquia (lo que ve su administrador). */
    List<Documento> listarPendientesPorParroquia(int idParroquia) throws SQLException;
    /**
     * Valida o rechaza un documento que sigue PENDIENTE (uno ya resuelto no se vuelve a cambiar).
     * @param estado "validado" o "rechazado"; el motivo es obligatorio al rechazar
     * @return false si no existe o ya no estaba pendiente
     */
    boolean resolverPendiente(int idDocumento, String estado, String motivo, int idValidador) throws SQLException;
    boolean validar(int idDocumento) throws SQLException;
    boolean rechazar(int idDocumento, String motivo) throws SQLException;
}
