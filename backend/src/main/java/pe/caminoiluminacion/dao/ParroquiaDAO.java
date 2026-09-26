package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Parroquia;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ParroquiaDAO {
    Parroquia insertar(Parroquia parroquia) throws SQLException;
    Optional<Parroquia> buscarPorId(int idParroquia) throws SQLException;
    List<Parroquia> listarTodas() throws SQLException;
    boolean actualizar(Parroquia parroquia) throws SQLException;
    boolean eliminar(int idParroquia) throws SQLException;
}
