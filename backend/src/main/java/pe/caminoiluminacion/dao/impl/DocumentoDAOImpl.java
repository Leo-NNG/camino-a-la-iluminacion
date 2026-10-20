package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.DocumentoDAO;
import pe.caminoiluminacion.modelo.Documento;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DocumentoDAOImpl implements DocumentoDAO {

    @Override
    public Documento insertar(Documento d) throws SQLException {
        String sql = "INSERT INTO documento (id_reserva, id_usuario, nombre_archivo, ruta_archivo, estado_validacion, tipo_mime, tamano_bytes) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (d.getIdReserva() != null) ps.setInt(1, d.getIdReserva()); else ps.setNull(1, Types.INTEGER);
            ps.setInt(2, d.getIdUsuario());
            ps.setString(3, d.getNombreArchivo());
            ps.setString(4, d.getRutaArchivo());
            ps.setString(5, d.getEstadoValidacion() != null ? d.getEstadoValidacion() : "pendiente");
            ps.setString(6, d.getTipoMime() != null ? d.getTipoMime() : "application/pdf");
            ps.setObject(7, d.getTamanoBytes(), Types.BIGINT);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) d.setIdDocumento(keys.getInt(1));
            }
            return d;
        }
    }

    @Override
    public Optional<Documento> buscarPorId(int idDocumento) throws SQLException {
        String sql = "SELECT * FROM documento WHERE id_documento = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idDocumento);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Documento> listarPendientesPorParroquia(int idParroquia) throws SQLException {
        String sql = "SELECT d.* FROM documento d JOIN reserva r ON r.id_reserva = d.id_reserva "
                   + "WHERE d.estado_validacion = 'pendiente' AND r.id_parroquia = ? ORDER BY d.fecha_envio";
        List<Documento> lista = new ArrayList<>();
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
    public boolean resolverPendiente(int idDocumento, String estado, String motivo, int idValidador) throws SQLException {
        String sql = "UPDATE documento SET estado_validacion = ?, motivo_rechazo = ?, fecha_validacion = NOW(), id_validador = ? "
                   + "WHERE id_documento = ? AND estado_validacion = 'pendiente'";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setString(2, "rechazado".equals(estado) ? motivo : null);
            ps.setInt(3, idValidador);
            ps.setInt(4, idDocumento);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<Documento> listarPorUsuario(int idUsuario) throws SQLException {
        String sql = "SELECT * FROM documento WHERE id_usuario = ? ORDER BY fecha_envio DESC";
        List<Documento> lista = new ArrayList<>();
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
    public List<Documento> listarPendientes() throws SQLException {
        String sql = "SELECT * FROM documento WHERE estado_validacion = 'pendiente' ORDER BY fecha_envio";
        List<Documento> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    @Override
    public boolean validar(int idDocumento) throws SQLException {
        String sql = "UPDATE documento SET estado_validacion = 'validado', fecha_validacion = NOW() "
                   + "WHERE id_documento = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idDocumento);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean rechazar(int idDocumento, String motivo) throws SQLException {
        String sql = "UPDATE documento SET estado_validacion = 'rechazado', motivo_rechazo = ?, "
                   + "fecha_validacion = NOW() WHERE id_documento = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, motivo);
            ps.setInt(2, idDocumento);
            return ps.executeUpdate() > 0;
        }
    }

    private Documento mapear(ResultSet rs) throws SQLException {
        Documento d = new Documento();
        d.setIdDocumento(rs.getInt("id_documento"));
        int idReserva = rs.getInt("id_reserva");
        d.setIdReserva(rs.wasNull() ? null : idReserva);
        d.setIdUsuario(rs.getInt("id_usuario"));
        d.setNombreArchivo(rs.getString("nombre_archivo"));
        d.setRutaArchivo(rs.getString("ruta_archivo"));
        d.setEstadoValidacion(rs.getString("estado_validacion"));
        d.setMotivoRechazo(rs.getString("motivo_rechazo"));
        int validador = rs.getInt("id_validador");
        d.setIdValidador(rs.wasNull() ? null : validador);
        d.setTipoMime(rs.getString("tipo_mime"));
        long tam = rs.getLong("tamano_bytes");
        d.setTamanoBytes(rs.wasNull() ? null : tam);
        d.setFechaEnvio(rs.getTimestamp("fecha_envio"));
        d.setFechaValidacion(rs.getTimestamp("fecha_validacion"));
        return d;
    }
}
