package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.modelo.Parroquia;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ParroquiaDAOImpl implements ParroquiaDAO {

    @Override
    public Parroquia insertar(Parroquia p) throws SQLException {
        String sql = "INSERT INTO parroquia (nombre, direccion, latitud, longitud, sacramentos_disponibles) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getNombre());
            ps.setString(2, p.getDireccion());
            ps.setBigDecimal(3, p.getLatitud());
            ps.setBigDecimal(4, p.getLongitud());
            ps.setString(5, p.getSacramentosDisponibles());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) p.setIdParroquia(keys.getInt(1));
            }
            return p;
        }
    }

    @Override
    public Optional<Parroquia> buscarPorId(int idParroquia) throws SQLException {
        String sql = "SELECT * FROM parroquia WHERE id_parroquia = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Parroquia> listarTodas() throws SQLException {
        String sql = "SELECT * FROM parroquia ORDER BY nombre";
        List<Parroquia> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    @Override
    public boolean actualizar(Parroquia p) throws SQLException {
        String sql = "UPDATE parroquia SET nombre=?, direccion=?, latitud=?, longitud=?, sacramentos_disponibles=? "
                   + "WHERE id_parroquia=?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, p.getNombre());
            ps.setString(2, p.getDireccion());
            ps.setBigDecimal(3, p.getLatitud());
            ps.setBigDecimal(4, p.getLongitud());
            ps.setString(5, p.getSacramentosDisponibles());
            ps.setInt(6, p.getIdParroquia());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean eliminar(int idParroquia) throws SQLException {
        String sql = "DELETE FROM parroquia WHERE id_parroquia = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            return ps.executeUpdate() > 0;
        }
    }

    private Parroquia mapear(ResultSet rs) throws SQLException {
        Parroquia p = new Parroquia();
        p.setIdParroquia(rs.getInt("id_parroquia"));
        p.setNombre(rs.getString("nombre"));
        p.setDireccion(rs.getString("direccion"));
        p.setLatitud(rs.getBigDecimal("latitud"));
        p.setLongitud(rs.getBigDecimal("longitud"));
        p.setSacramentosDisponibles(rs.getString("sacramentos_disponibles"));
        p.setCreadoEn(rs.getTimestamp("creado_en"));
        return p;
    }
}
