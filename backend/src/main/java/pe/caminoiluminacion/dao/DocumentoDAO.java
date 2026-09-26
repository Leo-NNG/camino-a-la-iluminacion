package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Documento;
import java.sql.SQLException;
import java.util.List;

public interface DocumentoDAO {
    Documento insertar(Documento documento) throws SQLException;
    List<Documento> listarPorUsuario(int idUsuario) throws SQLException;
    List<Documento> listarPendientes() throws SQLException;
    boolean validar(int idDocumento) throws SQLException;
    boolean rechazar(int idDocumento, String motivo) throws SQLException;
}
