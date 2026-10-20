package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.ReporteDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReporteDAOImpl implements ReporteDAO {

    @Override
    public List<FilaTramites> tramitesPorSacramento(int idParroquia, String mes) throws SQLException {
        String sql = "SELECT s.nombre AS sacramento, COUNT(r.id_reserva) AS solicitudes, "
                   + "COALESCE(SUM(r.estado = 'confirmada'), 0) AS confirmadas, "
                   + "COALESCE(SUM(r.estado = 'pendiente'), 0) AS pendientes, "
                   + "COALESCE(SUM(r.estado = 'rechazada'), 0) AS rechazadas "
                   + "FROM sacramento s "
                   + "LEFT JOIN reserva r ON r.id_sacramento = s.id_sacramento AND r.id_parroquia = ? "
                   + (mes != null ? "AND DATE_FORMAT(r.creado_en, '%Y-%m') = ? " : "")
                   + "GROUP BY s.id_sacramento, s.nombre ORDER BY s.id_sacramento";
        List<FilaTramites> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            if (mes != null) ps.setString(2, mes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new FilaTramites(rs.getString("sacramento"), rs.getInt("solicitudes"),
                            rs.getInt("confirmadas"), rs.getInt("pendientes"), rs.getInt("rechazadas")));
                }
            }
        }
        return lista;
    }

    @Override
    public List<FilaDocumento> documentosPendientes(int idParroquia) throws SQLException {
        String sql = "SELECT CONCAT(u.nombre, ' ', u.apellido) AS feligres, d.nombre_archivo AS documento, "
                   + "s.nombre AS sacramento, r.nombre_solicitante AS solicitante, "
                   + "DATE(d.fecha_envio) AS fecha_envio, DATEDIFF(CURDATE(), DATE(d.fecha_envio)) AS dias_espera "
                   + "FROM documento d "
                   + "JOIN reserva r ON r.id_reserva = d.id_reserva "
                   + "JOIN usuario u ON u.id_usuario = d.id_usuario "
                   + "JOIN sacramento s ON s.id_sacramento = r.id_sacramento "
                   + "WHERE d.estado_validacion = 'pendiente' AND r.id_parroquia = ? "
                   + "ORDER BY d.fecha_envio ASC";
        List<FilaDocumento> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new FilaDocumento(rs.getString("feligres"), rs.getString("documento"),
                            rs.getString("sacramento"), rs.getString("solicitante"),
                            String.valueOf(rs.getDate("fecha_envio")), rs.getInt("dias_espera")));
                }
            }
        }
        return lista;
    }

    @Override
    public List<FilaAgenda> agenda(int idParroquia) throws SQLException {
        String sql = "SELECT r.fecha_solicitada AS fecha, s.nombre AS sacramento, r.nombre_solicitante AS solicitante, "
                   + "CONCAT_WS(' · ', CONCAT(u.nombre, ' ', u.apellido), u.telefono, u.correo) AS contacto "
                   + "FROM reserva r "
                   + "JOIN sacramento s ON s.id_sacramento = r.id_sacramento "
                   + "JOIN usuario u ON u.id_usuario = r.id_usuario "
                   + "WHERE r.estado = 'confirmada' AND r.id_parroquia = ? AND r.fecha_solicitada >= CURDATE() "
                   + "ORDER BY r.fecha_solicitada, s.id_sacramento";
        List<FilaAgenda> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new FilaAgenda(String.valueOf(rs.getDate("fecha")), rs.getString("sacramento"),
                            rs.getString("solicitante"), rs.getString("contacto")));
                }
            }
        }
        return lista;
    }
}
