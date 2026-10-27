package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.modelo.Sacramento;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SacramentoDAOImpl implements SacramentoDAO {

    @Override
    public List<Sacramento> listarTodos() throws SQLException {
        List<Sacramento> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM sacramento ORDER BY id_sacramento");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    @Override
    public Optional<Sacramento> buscarPorCodigo(String codigo) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM sacramento WHERE codigo = ?")) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public boolean parroquiaOfrece(int idParroquia, int idSacramento) throws SQLException {
        String sql = "SELECT 1 FROM parroquia_sacramento WHERE id_parroquia = ? AND id_sacramento = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            ps.setInt(2, idSacramento);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public List<Integer> listarIdsPorParroquia(int idParroquia) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT id_sacramento FROM parroquia_sacramento WHERE id_parroquia = ? ORDER BY id_sacramento";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    @Override
    public void reemplazarDeParroquia(int idParroquia, List<Integer> idsSacramento) throws SQLException {
        try (Connection con = Conexion.obtener()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement del = con.prepareStatement("DELETE FROM parroquia_sacramento WHERE id_parroquia = ?")) {
                    del.setInt(1, idParroquia);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = con.prepareStatement(
                        "INSERT INTO parroquia_sacramento (id_parroquia, id_sacramento) VALUES (?, ?)")) {
                    for (Integer id : idsSacramento) {
                        ins.setInt(1, idParroquia);
                        ins.setInt(2, id);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private Sacramento mapear(ResultSet rs) throws SQLException {
        return new Sacramento(rs.getInt("id_sacramento"), rs.getString("codigo"), rs.getString("nombre"));
    }
}
