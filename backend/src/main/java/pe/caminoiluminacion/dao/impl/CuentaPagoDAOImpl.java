package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.CuentaPagoDAO;
import pe.caminoiluminacion.modelo.CuentaPago;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class CuentaPagoDAOImpl implements CuentaPagoDAO {

    @Override
    public Optional<CuentaPago> buscar(int idParroquia) throws SQLException {
        String sql = "SELECT id_parroquia, yape_numero, yape_titular, plin_numero, plin_titular FROM cuenta_pago WHERE id_parroquia = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new CuentaPago(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)))
                        : Optional.empty();
            }
        }
    }

    @Override
    public void guardar(CuentaPago c) throws SQLException {
        String sql = "INSERT INTO cuenta_pago (id_parroquia, yape_numero, yape_titular, plin_numero, plin_titular) VALUES (?, ?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE yape_numero = VALUES(yape_numero), yape_titular = VALUES(yape_titular), "
                   + "plin_numero = VALUES(plin_numero), plin_titular = VALUES(plin_titular)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, c.idParroquia());
            ps.setString(2, c.yapeNumero());
            ps.setString(3, c.yapeTitular());
            ps.setString(4, c.plinNumero());
            ps.setString(5, c.plinTitular());
            ps.executeUpdate();
        }
    }
}
