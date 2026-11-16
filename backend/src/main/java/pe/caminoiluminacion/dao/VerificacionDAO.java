package pe.caminoiluminacion.dao;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * Códigos de verificación de constancias (reserva) y recibos (pago) y la consulta pública por código. Aparte de los
 * DAO de reserva y pago a propósito: los datos que devuelve son solo los que la página pública puede mostrar.
 */
public interface VerificacionDAO {

    /** Lo que se puede decir públicamente de una constancia. {@code solicitante} es el nombre completo: el servicio lo reduce a iniciales. */
    record ConstanciaVerificada(String estado, Date fecha, String solicitante, String parroquia, String sacramento) { }

    /** Lo que se puede decir públicamente de un recibo (sin nombres, celular ni número de operación). */
    record ReciboVerificado(String estado, String concepto, BigDecimal monto, String metodo, Timestamp verificadoEn, String parroquia) { }

    /**
     * Deja {@code candidato} como código de la reserva SOLO si todavía no tiene uno, y devuelve el código que quedó
     * (el candidato, o el que ya tenía, aunque otra petición lo haya puesto a la vez).
     * @throws java.sql.SQLIntegrityConstraintViolationException si el candidato ya lo usa otro documento (hay que probar con otro)
     */
    String asegurarCodigoReserva(int idReserva, String candidato) throws SQLException;

    /** Igual que {@link #asegurarCodigoReserva} para un pago. */
    String asegurarCodigoPago(int idPago, String candidato) throws SQLException;

    /** @param codigo ya normalizado (CON-XXXXX-XXXXX) */
    Optional<ConstanciaVerificada> constanciaPorCodigo(String codigo) throws SQLException;

    /** @param codigo ya normalizado (REC-XXXXX-XXXXX) */
    Optional<ReciboVerificado> reciboPorCodigo(String codigo) throws SQLException;
}
