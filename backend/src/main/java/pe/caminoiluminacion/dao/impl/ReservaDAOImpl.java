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
        String sql = "INSERT INTO reserva (id_usuario, id_parroquia, id_sacramento, nombre_solicitante, "
                   + "fecha_solicitada, notas, estado) VALUES (?, ?, (SELECT id_sacramento FROM sacramento WHERE codigo = ?), ?, ?, ?, ?)";
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
    public boolean insertarSiDisponible(Reserva r) throws SQLException {
        try (Connection con = Conexion.obtener()) {
            con.setAutoCommit(false);
            try {
                // Bloquea la fila de la parroquia hasta el commit: serializa sus reservas.
                try (PreparedStatement lock = con.prepareStatement(
                        "SELECT id_parroquia FROM parroquia WHERE id_parroquia = ? FOR UPDATE")) {
                    lock.setInt(1, r.getIdParroquia());
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new SQLException("La parroquia no existe: " + r.getIdParroquia());
                    }
                }
                try (PreparedStatement chk = con.prepareStatement(
                        "SELECT 1 FROM reserva WHERE id_parroquia = ? AND id_sacramento = (SELECT id_sacramento FROM sacramento WHERE codigo = ?) "
                      + "AND fecha_solicitada = ? AND estado <> 'rechazada' LIMIT 1")) {
                    chk.setInt(1, r.getIdParroquia());
                    chk.setString(2, r.getSacramento());
                    chk.setDate(3, r.getFechaSolicitada());
                    try (ResultSet rs = chk.executeQuery()) {
                        if (rs.next()) { con.rollback(); return false; }
                    }
                }
                try (PreparedStatement ins = con.prepareStatement(
                        "INSERT INTO reserva (id_usuario, id_parroquia, id_sacramento, nombre_solicitante, "
                      + "fecha_solicitada, notas, estado) VALUES (?, ?, (SELECT id_sacramento FROM sacramento WHERE codigo = ?), ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ins.setInt(1, r.getIdUsuario());
                    ins.setInt(2, r.getIdParroquia());
                    ins.setString(3, r.getSacramento());
                    ins.setString(4, r.getNombreSolicitante());
                    ins.setDate(5, r.getFechaSolicitada());
                    ins.setString(6, r.getNotas());
                    ins.setString(7, "pendiente");
                    ins.executeUpdate();
                    try (ResultSet keys = ins.getGeneratedKeys()) {
                        if (keys.next()) r.setIdReserva(keys.getInt(1));
                    }
                }
                con.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean resolverPendiente(int idReserva, String nuevoEstado, String motivo) throws SQLException {
        String sql = "UPDATE reserva SET estado = ?, motivo_rechazo = ? WHERE id_reserva = ? AND estado = 'pendiente'";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setString(2, "rechazada".equals(nuevoEstado) ? motivo : null);
            ps.setInt(3, idReserva);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<Reserva> buscarPorId(int idReserva) throws SQLException {
        String sql = "SELECT r.id_reserva, r.id_usuario, r.id_parroquia, s.codigo AS sacramento, r.nombre_solicitante, r.fecha_solicitada, r.notas, r.estado, r.motivo_rechazo, r.creado_en FROM reserva r JOIN sacramento s ON s.id_sacramento = r.id_sacramento WHERE r.id_reserva = ?";
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
        String sql = "SELECT r.id_reserva, r.id_usuario, r.id_parroquia, s.codigo AS sacramento, r.nombre_solicitante, r.fecha_solicitada, r.notas, r.estado, r.motivo_rechazo, r.creado_en FROM reserva r JOIN sacramento s ON s.id_sacramento = r.id_sacramento WHERE r.id_usuario = ? ORDER BY r.creado_en DESC";
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
        String sql = "SELECT r.id_reserva, r.id_usuario, r.id_parroquia, s.codigo AS sacramento, r.nombre_solicitante, r.fecha_solicitada, r.notas, r.estado, r.motivo_rechazo, r.creado_en FROM reserva r JOIN sacramento s ON s.id_sacramento = r.id_sacramento WHERE r.estado = ? ORDER BY r.fecha_solicitada";
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
    public List<Reserva> listarPorEstadoYParroquia(String estado, int idParroquia) throws SQLException {
        String sql = "SELECT r.id_reserva, r.id_usuario, r.id_parroquia, s.codigo AS sacramento, r.nombre_solicitante, r.fecha_solicitada, r.notas, r.estado, r.motivo_rechazo, r.creado_en FROM reserva r JOIN sacramento s ON s.id_sacramento = r.id_sacramento WHERE r.estado = ? AND r.id_parroquia = ? ORDER BY r.fecha_solicitada";
        List<Reserva> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setInt(2, idParroquia);
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
        r.setMotivoRechazo(rs.getString("motivo_rechazo"));
        r.setCreadoEn(rs.getTimestamp("creado_en"));
        return r;
    }
}
