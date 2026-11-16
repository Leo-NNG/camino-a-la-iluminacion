package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.VerificacionDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class VerificacionDAOImpl implements VerificacionDAO {

    @Override
    public String asegurarCodigoReserva(int idReserva, String candidato) throws SQLException {
        return asegurar("reserva", "id_reserva", idReserva, candidato);
    }

    @Override
    public String asegurarCodigoPago(int idPago, String candidato) throws SQLException {
        return asegurar("pago", "id_pago", idPago, candidato);
    }

    /** Nombres de tabla y columna salen de constantes de esta clase, nunca de datos del usuario. */
    private static String asegurar(String tabla, String columnaId, int id, String candidato) throws SQLException {
        try (Connection con = Conexion.obtener()) {
            // Solo escribe si todavía no hay código: si dos peticiones llegan a la vez, gana una y la otra lee el de la ganadora.
            try (PreparedStatement ps = con.prepareStatement("UPDATE " + tabla + " SET codigo_verificacion = ? WHERE " + columnaId + " = ? AND codigo_verificacion IS NULL")) {
                ps.setString(1, candidato);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT codigo_verificacion FROM " + tabla + " WHERE " + columnaId + " = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        }
    }

    @Override
    public Optional<ConstanciaVerificada> constanciaPorCodigo(String codigo) throws SQLException {
        String sql = "SELECT r.estado, r.fecha_solicitada, r.nombre_solicitante, p.nombre AS parroquia, s.nombre AS sacramento "
                + "FROM reserva r JOIN parroquia p ON p.id_parroquia = r.id_parroquia JOIN sacramento s ON s.id_sacramento = r.id_sacramento "
                + "WHERE r.codigo_verificacion = ?";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new ConstanciaVerificada(rs.getString("estado"), rs.getDate("fecha_solicitada"),
                        rs.getString("nombre_solicitante"), rs.getString("parroquia"), rs.getString("sacramento"))) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<ReciboVerificado> reciboPorCodigo(String codigo) throws SQLException {
        String sql = "SELECT g.estado, g.concepto, g.monto, g.metodo, g.revisado_en, p.nombre AS parroquia "
                + "FROM pago g JOIN parroquia p ON p.id_parroquia = g.id_parroquia WHERE g.codigo_verificacion = ?";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new ReciboVerificado(rs.getString("estado"), rs.getString("concepto"), rs.getBigDecimal("monto"),
                        rs.getString("metodo"), rs.getTimestamp("revisado_en"), rs.getString("parroquia"))) : Optional.empty();
            }
        }
    }
}
