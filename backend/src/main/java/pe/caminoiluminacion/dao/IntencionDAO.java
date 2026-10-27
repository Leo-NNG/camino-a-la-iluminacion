package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Intencion;
import pe.caminoiluminacion.modelo.Pago;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Intenciones de misa. El pago y la intención se guardan siempre juntos, en una sola transacción. */
public interface IntencionDAO {

    /** Una intención con el estado de su pago (para mostrarla). */
    record Fila(Intencion intencion, String estadoPago, BigDecimal monto, String observacionPago) { }

    /**
     * Registra el pago y la intención en una transacción que bloquea la fila del horario: solo así dos personas no
     * pueden pasarse del cupo a la vez.
     * @return la intención con sus ids, o vacío si esa misa ya completó {@code maxPorMisa} intenciones vigentes
     *         (las de pagos rechazados no cuentan)
     * @throws java.sql.SQLIntegrityConstraintViolationException si la operación o el comprobante ya se usaron
     */
    Optional<Intencion> insertarConPago(Intencion intencion, Pago pago, int maxPorMisa) throws SQLException;

    List<Fila> listarPorUsuario(int idUsuario) throws SQLException;

    /** Intenciones de una parroquia con fecha entre {@code desde} y {@code hasta}; solo las de pago aprobado si se pide. */
    List<Fila> agenda(int idParroquia, Date desde, Date hasta, boolean soloAprobadas) throws SQLException;
}
