package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.modelo.Reserva;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReservaDAOImpl implements ReservaDAO {

    @Override
    public Reserva insertar(Reserva r) throws SQLException {
        String sql = "INSERT INTO reserva (id_usuario, id_parroquia, sacramento, nombre_solicitante, "
                   + "fecha_solicitada, notas, estado) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getIdUsuario());
            ps.setInt(2, r.getIdParroquia());
            ps.setString(3, r.getSacramento());
            ps.setString(4, r.getNombreSolicitante());
            ps.setDate(5, r.getFechaSolicitada());
            ps.setString(6, r.getNotas());
            ps.setString(7, r.getEstado() != null ? r.getEstado() : "pendiente");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) r.setIdReserva(keys.getInt(1));
            }
            return r;
        }
    }

    @Override
    public Optional<Reserva> buscarPorId(int idReserva) throws SQLException {
        String sql = "SELECT * FROM reserva WHERE id_reserva = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idReserva);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Reserva> listarPorUsuario(int idUsuario) throws SQLException {
        String sql = "SELECT * FROM reserva WHERE id_usuario = ? ORDER BY creado_en DESC";
        List<Reserva> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public List<Reserva> listarPorEstado(String estado) throws SQLException {
        String sql = "SELECT * FROM reserva WHERE estado = ? ORDER BY fecha_solicitada";
        List<Reserva> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, estado);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public boolean actualizarEstado(int idReserva, String nuevoEstado) throws SQLException {
        String sql = "UPDATE reserva SET estado = ? WHERE id_reserva = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idReserva);
            return ps.executeUpdate() > 0;
        }
    }

    private Reserva mapear(ResultSet rs) throws SQLException {
        Reserva r = new Reserva();
        r.setIdReserva(rs.getInt("id_reserva"));
        r.setIdUsuario(rs.getInt("id_usuario"));
        r.setIdParroquia(rs.getInt("id_parroquia"));
        r.setSacramento(rs.getString("sacramento"));
        r.setNombreSolicitante(rs.getString("nombre_solicitante"));
        r.setFechaSolicitada(rs.getDate("fecha_solicitada"));
        r.setNotas(rs.getString("notas"));
        r.setEstado(rs.getString("estado"));
        r.setCreadoEn(rs.getTimestamp("creado_en"));
        return r;
    }
}
