package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.ReporteDAO;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReporteDAOImpl implements ReporteDAO {

    @Override
    public List<FilaTramites> tramitesPorSacramento(int idParroquia, String mes) throws SQLException {
        String sql = "SELECT s.nombre AS sacramento, COUNT(r.id_reserva) AS solicitudes, "
                   + "COALESCE(SUM(r.estado = 'confirmada'), 0) AS confirmadas, "
                   + "COALESCE(SUM(r.estado = 'pendiente'), 0) AS pendientes, "
                   + "COALESCE(SUM(r.estado = 'rechazada'), 0) AS rechazadas, "
                   + "COALESCE(SUM(r.estado = 'cancelada'), 0) AS canceladas "
                   + "FROM sacramento s "
                   + "LEFT JOIN reserva r ON r.id_sacramento = s.id_sacramento AND r.id_parroquia = ? "
                   + (mes != null ? "AND DATE_FORMAT(r.creado_en, '%Y-%m') = ? " : "")
                   + "GROUP BY s.id_sacramento, s.nombre ORDER BY s.id_sacramento";
        List<FilaTramites> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            if (mes != null) ps.setString(2, mes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new FilaTramites(rs.getString("sacramento"), rs.getInt("solicitudes"),
                            rs.getInt("confirmadas"), rs.getInt("pendientes"), rs.getInt("rechazadas"), rs.getInt("canceladas")));
                }
            }
        }
        return lista;
    }

    @Override
    public List<FilaDocumento> documentosPendientes(int idParroquia) throws SQLException {
        String sql = "SELECT CONCAT(u.nombre, ' ', u.apellido) AS feligres, d.nombre_archivo AS documento, "
                   + "s.nombre AS sacramento, r.nombre_solicitante AS solicitante, "
                   + "DATE(d.fecha_envio) AS fecha_envio, DATEDIFF(CURDATE(), DATE(d.fecha_envio)) AS dias_espera "
                   + "FROM documento d "
                   + "JOIN reserva r ON r.id_reserva = d.id_reserva "
                   + "JOIN usuario u ON u.id_usuario = d.id_usuario "
                   + "JOIN sacramento s ON s.id_sacramento = r.id_sacramento "
                   + "WHERE d.estado_validacion = 'pendiente' AND r.id_parroquia = ? AND r.estado <> 'cancelada' "
                   + "ORDER BY d.fecha_envio ASC";
        List<FilaDocumento> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new FilaDocumento(rs.getString("feligres"), rs.getString("documento"),
                            rs.getString("sacramento"), rs.getString("solicitante"),
                            String.valueOf(rs.getDate("fecha_envio")), rs.getInt("dias_espera")));
                }
            }
        }
        return lista;
    }

    @Override
    public List<FilaAgenda> agenda(int idParroquia) throws SQLException {
        String sql = "SELECT r.fecha_solicitada AS fecha, s.nombre AS sacramento, r.nombre_solicitante AS solicitante, "
                   + "CONCAT_WS(' · ', CONCAT(u.nombre, ' ', u.apellido), u.telefono, u.correo) AS contacto "
                   + "FROM reserva r "
                   + "JOIN sacramento s ON s.id_sacramento = r.id_sacramento "
                   + "JOIN usuario u ON u.id_usuario = r.id_usuario "
                   + "WHERE r.estado = 'confirmada' AND r.id_parroquia = ? AND r.fecha_solicitada >= CURDATE() "
                   + "ORDER BY r.fecha_solicitada, s.id_sacramento";
        List<FilaAgenda> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new FilaAgenda(String.valueOf(rs.getDate("fecha")), rs.getString("sacramento"),
                            rs.getString("solicitante"), rs.getString("contacto")));
                }
            }
        }
        return lista;
    }

    @Override
    public List<FilaPagoIngreso> ingresos(int idParroquia, String mes) throws SQLException {
        String sql = "SELECT DATE(p.creado_en) AS fecha, CONCAT(u.nombre, ' ', u.apellido) AS feligres, p.concepto, p.metodo, "
                   + "p.numero_operacion, p.monto, p.codigo_verificacion "
                   + "FROM pago p JOIN usuario u ON u.id_usuario = p.id_usuario "
                   + "WHERE p.id_parroquia = ? AND p.estado = 'aprobado' "
                   + (mes != null ? "AND DATE_FORMAT(p.creado_en, '%Y-%m') = ? " : "")
                   + "ORDER BY p.creado_en, p.id_pago";
        List<FilaPagoIngreso> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            if (mes != null) ps.setString(2, mes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new FilaPagoIngreso(String.valueOf(rs.getDate("fecha")), rs.getString("feligres"),
                            rs.getString("concepto"), rs.getString("metodo"), rs.getString("numero_operacion"),
                            rs.getBigDecimal("monto"), rs.getString("codigo_verificacion")));
                }
            }
        }
        return lista;
    }

    @Override
    public Resumen resumen(int idParroquia, LocalDate hoy, int diasProximos, LocalDate inicioMes) throws SQLException {
        // Una sola ida a la base: cada contador es una subconsulta que usa los índices por parroquia y estado.
        String sql = "SELECT "
                   + "(SELECT COUNT(*) FROM reserva WHERE id_parroquia = ? AND estado = 'pendiente') AS solicitudes_pendientes, "
                   + "(SELECT COUNT(*) FROM documento d JOIN reserva r ON r.id_reserva = d.id_reserva "
                   + "   WHERE r.id_parroquia = ? AND d.estado_validacion = 'pendiente' AND r.estado <> 'cancelada') AS documentos_pendientes, "
                   + "(SELECT COUNT(*) FROM pago WHERE id_parroquia = ? AND estado = 'pendiente') AS pagos_por_verificar, "
                   + "(SELECT COUNT(*) FROM reserva WHERE id_parroquia = ? AND estado = 'confirmada' "
                   + "   AND fecha_solicitada >= ? AND fecha_solicitada < ?) AS celebraciones_proximas, "
                   + "(SELECT COUNT(*) FROM intencion_misa i JOIN pago p ON p.id_pago = i.id_pago "
                   + "   WHERE i.id_parroquia = ? AND p.estado = 'aprobado' AND i.fecha_misa >= ? AND i.fecha_misa < ?) AS intenciones_proximas, "
                   + "(SELECT COUNT(*) FROM reserva WHERE id_parroquia = ? AND creado_en >= ? AND creado_en < ?) AS solicitudes_mes, "
                   + "(SELECT COUNT(*) FROM reserva WHERE id_parroquia = ? AND estado = 'cancelada' "
                   + "   AND cancelada_en >= ? AND cancelada_en < ?) AS canceladas_mes, "
                   + "(SELECT COUNT(*) FROM pago WHERE id_parroquia = ? AND estado = 'aprobado' AND creado_en >= ? AND creado_en < ?) AS pagos_mes, "
                   + "(SELECT COALESCE(SUM(monto), 0) FROM pago WHERE id_parroquia = ? AND estado = 'aprobado' "
                   + "   AND creado_en >= ? AND creado_en < ?) AS ingresos_mes";
        java.sql.Date desde = java.sql.Date.valueOf(hoy);
        java.sql.Date hasta = java.sql.Date.valueOf(hoy.plusDays(diasProximos));
        java.sql.Date mesDesde = java.sql.Date.valueOf(inicioMes);
        java.sql.Date mesHasta = java.sql.Date.valueOf(inicioMes.plusMonths(1));
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, idParroquia);
            ps.setInt(i++, idParroquia);
            ps.setInt(i++, idParroquia);
            ps.setInt(i++, idParroquia); ps.setDate(i++, desde); ps.setDate(i++, hasta);
            ps.setInt(i++, idParroquia); ps.setDate(i++, desde); ps.setDate(i++, hasta);
            for (int k = 0; k < 4; k++) { ps.setInt(i++, idParroquia); ps.setDate(i++, mesDesde); ps.setDate(i++, mesHasta); }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                BigDecimal ingresos = rs.getBigDecimal("ingresos_mes");
                return new Resumen(rs.getInt("solicitudes_pendientes"), rs.getInt("documentos_pendientes"),
                        rs.getInt("pagos_por_verificar"), rs.getInt("celebraciones_proximas"), rs.getInt("intenciones_proximas"),
                        rs.getInt("solicitudes_mes"), rs.getInt("canceladas_mes"), rs.getInt("pagos_mes"),
                        ingresos == null ? BigDecimal.ZERO : ingresos);
            }
        }
    }
}
