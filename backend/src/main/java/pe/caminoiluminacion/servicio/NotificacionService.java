package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.NotificacionDAO;
import pe.caminoiluminacion.modelo.Notificacion;

import java.sql.SQLException;
import java.util.List;

/** Avisos dentro de la plataforma (RF13): siempre del usuario de la sesión, nunca por parámetro. */
public class NotificacionService {

    private static final int LIMITE = 30;

    public record Bandeja(List<Notificacion> notificaciones, int noLeidas) { }

    private final NotificacionDAO dao;

    public NotificacionService(NotificacionDAO dao) {
        this.dao = dao;
    }

    public Bandeja deUsuario(int idUsuario) throws SQLException {
        return new Bandeja(dao.listarPorUsuario(idUsuario, LIMITE), dao.contarNoLeidas(idUsuario));
    }

    public int marcarTodasLeidas(int idUsuario) throws SQLException {
        return dao.marcarTodasLeidas(idUsuario);
    }
}
