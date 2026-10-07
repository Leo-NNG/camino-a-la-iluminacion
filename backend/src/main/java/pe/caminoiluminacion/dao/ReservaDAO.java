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
     * Inserta la reserva solo si esa parroquia no tiene ya otra solicitud activa (pendiente o
     * confirmada) del mismo sacramento en la misma fecha. La comprobación y el INSERT ocurren en
     * una transacción que bloquea la fila de la parroquia, así dos solicitudes simultáneas no
     * pueden ocupar la misma fecha.
     * @return true si se registró; false si la fecha ya estaba ocupada
     */
    boolean insertarSiDisponible(Reserva reserva) throws SQLException;

    /**
     * Confirma o rechaza una solicitud que sigue PENDIENTE (una ya resuelta no se puede volver a cambiar).
     * @param motivo obligatorio al rechazar; se ignora al confirmar
     * @return false si la reserva no existe o ya no estaba pendiente
     */
    boolean resolverPendiente(int idReserva, String nuevoEstado, String motivo) throws SQLException;
}
