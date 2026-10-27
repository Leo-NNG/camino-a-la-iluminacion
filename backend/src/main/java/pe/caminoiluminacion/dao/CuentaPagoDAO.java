package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.CuentaPago;

import java.sql.SQLException;
import java.util.Optional;

/** Número Yape/Plin de cada parroquia. */
public interface CuentaPagoDAO {

    Optional<CuentaPago> buscar(int idParroquia) throws SQLException;

    /** Crea o actualiza los números Yape/Plin de la parroquia. NO modifica la ofrenda de intenciones. */
    void guardar(CuentaPago cuenta) throws SQLException;

    /** Fija la ofrenda de una intención de misa (null la desactiva). */
    void guardarOfrenda(int idParroquia, java.math.BigDecimal monto) throws SQLException;
}
