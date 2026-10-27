package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Sacramento;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Catálogo de sacramentos y qué sacramentos ofrece cada parroquia (relación N:M). */
public interface SacramentoDAO {

    List<Sacramento> listarTodos() throws SQLException;

    Optional<Sacramento> buscarPorCodigo(String codigo) throws SQLException;

    boolean parroquiaOfrece(int idParroquia, int idSacramento) throws SQLException;

    /** Ids de los sacramentos que ofrece la parroquia. */
    List<Integer> listarIdsPorParroquia(int idParroquia) throws SQLException;

    /** Reemplaza TODO lo que ofrece la parroquia por esa lista, en una sola transacción. */
    void reemplazarDeParroquia(int idParroquia, List<Integer> idsSacramento) throws SQLException;
}
