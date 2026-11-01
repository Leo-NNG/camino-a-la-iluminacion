package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Reserva;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ReservaDAO {
    Reserva insertar(Reserva reserva) throws SQLException;
    Optional<Reserva> buscarPorId(int idReserva) throws SQLException;
    List<Reserva> listarPorUsuario(int idUsuario) throws SQLException;
    List<Reserva> listarPorEstado(String estado) throws SQLException;
    List<Reserva> listarPorEstadoYParroquia(String estado, int idParroquia) throws SQLException;
    boolean actualizarEstado(int idReserva, String nuevoEstado) throws SQLException;

    /**
     * Inserta la reserva solo si esa parroquia tiene menos de {@code cupo} solicitudes activas (pendientes o
     * confirmadas) del mismo sacramento en la misma fecha. La comprobación y el INSERT ocurren en una transacción
     * que bloquea la fila de la parroquia, así dos solicitudes simultáneas no pueden pasarse del cupo.
     * @param cupo cuántas solicitudes activas admite la fecha (1 si la parroquia no publicó fechas)
     * @return true si se registró; false si la fecha ya no tiene cupo
     */
    boolean insertarSiDisponible(Reserva reserva, int cupo) throws SQLException;

    /** Igual que {@link #insertarSiDisponible(Reserva, int)} con cupo 1: una sola solicitud activa por fecha. */
    default boolean insertarSiDisponible(Reserva reserva) throws SQLException {
        return insertarSiDisponible(reserva, 1);
    }

    /**
     * Confirma o rechaza una solicitud que sigue PENDIENTE (una ya resuelta no se puede volver a cambiar).
     * @param motivo obligatorio al rechazar; se ignora al confirmar
     * @return false si la reserva no existe o ya no estaba pendiente
     */
    boolean resolverPendiente(int idReserva, String nuevoEstado, String motivo) throws SQLException;
}
