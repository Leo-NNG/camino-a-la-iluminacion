package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Arancel;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Montos oficiales de cada sacramento por parroquia. */
public interface ArancelDAO {

    Optional<BigDecimal> montoDe(int idParroquia, int idSacramento) throws SQLException;

    /** Un renglón por cada sacramento del catálogo (monto null si la parroquia aún no lo definió). */
    List<Arancel> listarPorParroquia(int idParroquia) throws SQLException;

    /** Crea o actualiza el monto. */
    void guardar(int idParroquia, int idSacramento, BigDecimal monto) throws SQLException;
}
