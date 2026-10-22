package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.CuentaPago;

import java.sql.SQLException;
import java.util.Optional;

/** Número Yape/Plin de cada parroquia. */
public interface CuentaPagoDAO {

    Optional<CuentaPago> buscar(int idParroquia) throws SQLException;

    /** Crea o reemplaza la cuenta de la parroquia. */
    void guardar(CuentaPago cuenta) throws SQLException;
}
