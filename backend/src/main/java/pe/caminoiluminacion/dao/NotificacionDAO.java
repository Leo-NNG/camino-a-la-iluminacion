package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Notificacion;

import java.sql.SQLException;
import java.util.List;

/** Avisos dentro de la plataforma para el feligrés. */
public interface NotificacionDAO {

    void crear(int idUsuario, String asunto, String mensaje) throws SQLException;

    /** Las más recientes primero, hasta {@code limite}. */
    List<Notificacion> listarPorUsuario(int idUsuario, int limite) throws SQLException;

    int contarNoLeidas(int idUsuario) throws SQLException;

    /** @return cuántas se marcaron */
    int marcarTodasLeidas(int idUsuario) throws SQLException;
}
