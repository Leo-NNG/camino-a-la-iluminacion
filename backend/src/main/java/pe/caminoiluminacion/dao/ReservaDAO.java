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
    boolean actualizarEstado(int idReserva, String nuevoEstado) throws SQLException;
}
