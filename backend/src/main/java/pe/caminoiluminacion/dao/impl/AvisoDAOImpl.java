package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.AvisoDAO;
import pe.caminoiluminacion.modelo.Aviso;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AvisoDAOImpl implements AvisoDAO {

    @Override
    public Aviso insertar(Aviso a) throws SQLException {
        String sql = "INSERT INTO aviso (id_parroquia, titulo, descripcion, tipo, urgente, fecha_evento) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getIdParroquia());
            ps.setString(2, a.getTitulo());
            ps.setString(3, a.getDescripcion());
            ps.setString(4, a.getTipo());
            ps.setBoolean(5, a.isUrgente());
            ps.setDate(6, a.getFechaEvento());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) a.setIdAviso(keys.getInt(1));
            }
            return a;
        }
    }

    @Override
    public List<Aviso> listarPorParroquia(int idParroquia) throws SQLException {
        String sql = "SELECT * FROM aviso WHERE id_parroquia = ? ORDER BY publicado_en DESC";
        List<Aviso> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public List<Aviso> listarUrgentes(int idParroquia) throws SQLException {
        String sql = "SELECT * FROM aviso WHERE id_parroquia = ? AND urgente = TRUE ORDER BY publicado_en DESC";
        List<Aviso> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public boolean eliminar(int idAviso) throws SQLException {
        String sql = "DELETE FROM aviso WHERE id_aviso = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idAviso);
            return ps.executeUpdate() > 0;
        }
    }

    private Aviso mapear(ResultSet rs) throws SQLException {
        Aviso a = new Aviso();
        a.setIdAviso(rs.getInt("id_aviso"));
        a.setIdParroquia(rs.getInt("id_parroquia"));
        a.setTitulo(rs.getString("titulo"));
        a.setDescripcion(rs.getString("descripcion"));
        a.setTipo(rs.getString("tipo"));
        a.setUrgente(rs.getBoolean("urgente"));
        a.setFechaEvento(rs.getDate("fecha_evento"));
        a.setPublicadoEn(rs.getTimestamp("publicado_en"));
        return a;
    }
}
