package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Pago;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Pagos Yape/Plin. Las operaciones que deben ser atómicas (insertar, resolver) viven aquí, no en el servicio. */
public interface PagoDAO {

    /** Ingresos aprobados de una parroquia agrupados por concepto. */
    record FilaIngreso(String concepto, int pagos, BigDecimal total) { }

    /**
     * Registra un pago de ARANCEL solo si la reserva no tiene ya otro pago vigente (pendiente o aprobado). La
     * comprobación y el INSERT ocurren en una transacción que bloquea la fila de la reserva.
     * @return el pago con su id, o vacío si la reserva ya tenía un pago vigente
     * @throws java.sql.SQLIntegrityConstraintViolationException si la operación o el comprobante ya se usaron
     */
    Optional<Pago> insertarArancel(Pago pago) throws SQLException;

    /** Registra una donación o un diezmo (no están ligados a una reserva). */
    Pago insertarLibre(Pago pago) throws SQLException;

    Optional<Pago> buscarPorId(int idPago) throws SQLException;

    List<Pago> listarPorUsuario(int idUsuario) throws SQLException;

    List<Pago> listarPorParroquiaYEstado(int idParroquia, String estado) throws SQLException;

    /** El pago pendiente o aprobado de una reserva, si existe. */
    Optional<Pago> vigenteDeReserva(int idReserva) throws SQLException;

    boolean existeOperacionVigente(String metodo, String numeroOperacion) throws SQLException;

    boolean existeVoucherVigente(String sha256) throws SQLException;

    /** Aprueba o rechaza un pago que sigue PENDIENTE. @return false si no existe o ya estaba resuelto */
    boolean resolverPendiente(int idPago, String nuevoEstado, String observacion, int idRevisor) throws SQLException;

    /** @param mes "AAAA-MM" o null para todos */
    List<FilaIngreso> ingresosPorConcepto(int idParroquia, String mes) throws SQLException;
}
