package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.NotificacionDAO;
import pe.caminoiluminacion.modelo.Notificacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NotificacionDAOImpl implements NotificacionDAO {

    @Override
    public void crear(int idUsuario, String asunto, String mensaje) throws SQLException {
        String sql = "INSERT INTO notificacion (id_usuario, asunto, mensaje) VALUES (?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setString(2, recortar(asunto, 120));
            ps.setString(3, recortar(mensaje, 500));
            ps.executeUpdate();
        }
    }

    @Override
    public List<Notificacion> listarPorUsuario(int idUsuario, int limite) throws SQLException {
        String sql = "SELECT id_notificacion, id_usuario, asunto, mensaje, leida, creada_en FROM notificacion "
                   + "WHERE id_usuario = ? ORDER BY creada_en DESC, id_notificacion DESC LIMIT ?";
        List<Notificacion> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Notificacion(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getTimestamp(6)));
                }
            }
        }
        return lista;
    }

    @Override
    public int contarNoLeidas(int idUsuario) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM notificacion WHERE id_usuario = ? AND leida = FALSE")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public int marcarTodasLeidas(int idUsuario) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE notificacion SET leida = TRUE WHERE id_usuario = ? AND leida = FALSE")) {
            ps.setInt(1, idUsuario);
            return ps.executeUpdate();
        }
    }

    private static String recortar(String t, int max) {
        return t == null ? "" : (t.length() > max ? t.substring(0, max) : t);
    }
}
