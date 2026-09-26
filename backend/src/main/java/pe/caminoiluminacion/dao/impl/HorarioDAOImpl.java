package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.HorarioDAO;
import pe.caminoiluminacion.modelo.Horario;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HorarioDAOImpl implements HorarioDAO {

    @Override
    public Horario insertar(Horario h) throws SQLException {
        String sql = "INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) VALUES (?, ?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, h.getIdParroquia());
            ps.setString(2, h.getDiaSemana());
            ps.setString(3, h.getHora());
            ps.setString(4, h.getTipo());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) h.setIdHorario(keys.getInt(1));
            }
            return h;
        }
    }

    @Override
    public List<Horario> listarPorParroquia(int idParroquia) throws SQLException {
        String sql = "SELECT * FROM horario WHERE id_parroquia = ? "
                   + "ORDER BY FIELD(dia_semana,'lunes','martes','miercoles','jueves','viernes','sabado','domingo')";
        List<Horario> lista = new ArrayList<>();
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
    public boolean actualizar(Horario h) throws SQLException {
        String sql = "UPDATE horario SET dia_semana=?, hora=?, tipo=? WHERE id_horario=?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, h.getDiaSemana());
            ps.setString(2, h.getHora());
            ps.setString(3, h.getTipo());
            ps.setInt(4, h.getIdHorario());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean eliminar(int idHorario) throws SQLException {
        String sql = "DELETE FROM horario WHERE id_horario = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idHorario);
            return ps.executeUpdate() > 0;
        }
    }

    private Horario mapear(ResultSet rs) throws SQLException {
        Horario h = new Horario();
        h.setIdHorario(rs.getInt("id_horario"));
        h.setIdParroquia(rs.getInt("id_parroquia"));
        h.setDiaSemana(rs.getString("dia_semana"));
        h.setHora(rs.getString("hora"));
        h.setTipo(rs.getString("tipo"));
        return h;
    }
}
