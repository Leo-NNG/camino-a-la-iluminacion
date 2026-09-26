package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.DocumentoDAO;
import pe.caminoiluminacion.modelo.Documento;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DocumentoDAOImpl implements DocumentoDAO {

    @Override
    public Documento insertar(Documento d) throws SQLException {
        String sql = "INSERT INTO documento (id_reserva, id_usuario, nombre_archivo, ruta_archivo, estado_validacion) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (d.getIdReserva() != null) ps.setInt(1, d.getIdReserva()); else ps.setNull(1, Types.INTEGER);
            ps.setInt(2, d.getIdUsuario());
            ps.setString(3, d.getNombreArchivo());
            ps.setString(4, d.getRutaArchivo());
            ps.setString(5, d.getEstadoValidacion() != null ? d.getEstadoValidacion() : "pendiente");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) d.setIdDocumento(keys.getInt(1));
            }
            return d;
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
        d.setFechaEnvio(rs.getTimestamp("fecha_envio"));
        d.setFechaValidacion(rs.getTimestamp("fecha_validacion"));
        return d;
    }
}
