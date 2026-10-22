package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.ArancelDAO;
import pe.caminoiluminacion.modelo.Arancel;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ArancelDAOImpl implements ArancelDAO {

    @Override
    public Optional<BigDecimal> montoDe(int idParroquia, int idSacramento) throws SQLException {
        String sql = "SELECT monto FROM arancel WHERE id_parroquia = ? AND id_sacramento = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            ps.setInt(2, idSacramento);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getBigDecimal(1)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Arancel> listarPorParroquia(int idParroquia) throws SQLException {
        String sql = "SELECT s.id_sacramento, s.nombre, a.monto FROM sacramento s "
                   + "LEFT JOIN arancel a ON a.id_sacramento = s.id_sacramento AND a.id_parroquia = ? "
                   + "ORDER BY s.id_sacramento";
        List<Arancel> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(new Arancel(idParroquia, rs.getInt("id_sacramento"), rs.getString("nombre"), rs.getBigDecimal("monto")));
            }
        }
        return lista;
    }

    @Override
    public void guardar(int idParroquia, int idSacramento, BigDecimal monto) throws SQLException {
        String sql = "INSERT INTO arancel (id_parroquia, id_sacramento, monto) VALUES (?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE monto = VALUES(monto)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            ps.setInt(2, idSacramento);
            ps.setBigDecimal(3, monto);
            ps.executeUpdate();
        }
    }
}
