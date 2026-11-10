package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.CuentaDAO;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CuentaDAOImpl implements CuentaDAO {

    static final String MOTIVO_PAGOS = "tienes pagos pendientes de verificación";
    static final String MOTIVO_RESERVAS = "tienes solicitudes de sacramento pendientes o confirmadas con fecha próxima";
    static final String MOTIVO_INTENCIONES = "tienes intenciones de misa vigentes";
    static final String MOTIVO_NO_EXISTE = "la cuenta no existe o ya fue eliminada";
    static final String MOTIVO_PERSONAL = "las cuentas del personal no se eliminan desde aquí";

    @Override
    public boolean actualizarContacto(int idUsuario, String correo, String telefono) throws SQLException {
        String sql = "UPDATE usuario SET correo = ?, telefono = ? WHERE id_usuario = ? AND eliminado_en IS NULL";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, correo);
            ps.setString(2, telefono);
            ps.setInt(3, idUsuario);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean actualizarContrasena(int idUsuario, String contrasenaHash) throws SQLException {
        String sql = "UPDATE usuario SET contrasena_hash = ? WHERE id_usuario = ? AND eliminado_en IS NULL";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, contrasenaHash);
            ps.setInt(2, idUsuario);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean registrarAceptacion(int idUsuario, String version) throws SQLException {
        String sql = "UPDATE usuario SET politica_version = ?, politica_aceptada_en = NOW() WHERE id_usuario = ? AND eliminado_en IS NULL";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, version);
            ps.setInt(2, idUsuario);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Anonimizacion anonimizar(int idUsuario, Date hoy) throws SQLException {
        try (Connection con = Conexion.obtener()) {
            con.setAutoCommit(false);
            try {
                Anonimizacion r = anonimizar(con, idUsuario, hoy);
                if (r.hecha()) con.commit(); else con.rollback();
                return r;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private Anonimizacion anonimizar(Connection con, int id, Date hoy) throws SQLException {
        // Se bloquea la fila del usuario: mientras dura la transacción nadie más puede cambiarla ni eliminarla a la vez.
        String rol;
        try (PreparedStatement ps = con.prepareStatement("SELECT rol, eliminado_en FROM usuario WHERE id_usuario = ? FOR UPDATE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getTimestamp("eliminado_en") != null) return new Anonimizacion(List.of(MOTIVO_NO_EXISTE), List.of());
                rol = rs.getString("rol");
            }
        }
        if (!"feligres".equals(rol)) return new Anonimizacion(List.of(MOTIVO_PERSONAL), List.of());

        List<String> bloqueos = new ArrayList<>();
        if (cuenta(con, "SELECT COUNT(*) FROM pago WHERE id_usuario = ? AND estado = 'pendiente'", id, null) > 0) bloqueos.add(MOTIVO_PAGOS);
        if (cuenta(con, "SELECT COUNT(*) FROM reserva WHERE id_usuario = ? AND estado IN ('pendiente','confirmada') AND fecha_solicitada >= ?", id, hoy) > 0) {
            bloqueos.add(MOTIVO_RESERVAS);
        }
        if (cuenta(con, "SELECT COUNT(*) FROM intencion_misa i JOIN pago p ON p.id_pago = i.id_pago "
                + "WHERE i.id_usuario = ? AND i.fecha_misa >= ? AND p.estado <> 'rechazado'", id, hoy) > 0) bloqueos.add(MOTIVO_INTENCIONES);
        if (!bloqueos.isEmpty()) return new Anonimizacion(bloqueos, List.of());

        // Archivos que habrá que borrar del disco: documentos y comprobantes de pago de esta persona.
        List<String> archivos = new ArrayList<>();
        archivos.addAll(texto(con, "SELECT ruta_archivo FROM documento WHERE id_usuario = ?", id));
        archivos.addAll(texto(con, "SELECT ruta_voucher FROM pago WHERE id_usuario = ?", id));

        ejecutar(con, "DELETE FROM documento WHERE id_usuario = ?", id);
        ejecutar(con, "DELETE FROM notificacion WHERE id_usuario = ?", id);
        // El pago se conserva (es un registro contable de la parroquia) pero sin el celular de origen.
        ejecutar(con, "UPDATE pago SET celular_origen = NULL WHERE id_usuario = ?", id);
        ejecutar(con, "UPDATE reserva SET nombre_solicitante = '(anonimizado)', notas = NULL, motivo_cancelacion = NULL WHERE id_usuario = ?", id);
        ejecutar(con, "UPDATE intencion_misa SET texto = '(eliminada)' WHERE id_usuario = ?", id);
        ejecutar(con, "UPDATE bitacora SET ip = NULL WHERE id_usuario = ?", id);
        // correo es NOT NULL y UNIQUE: se deja un marcador único e inválido. contrasena_hash '!' no es un hash válido:
        // nadie puede iniciar sesión. dni_indice NULL libera ese DNI por si la persona vuelve a registrarse.
        ejecutar(con, "UPDATE usuario SET nombre = '', apellido = '', telefono = NULL, ubicacion = NULL, dni = '', dni_indice = NULL, "
                + "correo = CONCAT('eliminado-', id_usuario, '@eliminado.invalid'), contrasena_hash = '!', eliminado_en = NOW() "
                + "WHERE id_usuario = ?", id);
        return new Anonimizacion(List.of(), archivos);
    }

    private static long cuenta(Connection con, String sql, int id, Date hoy) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            if (hoy != null) ps.setDate(2, hoy);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static List<String> texto(Connection con, String sql, int id) throws SQLException {
        List<String> r = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) if (rs.getString(1) != null) r.add(rs.getString(1));
            }
        }
        return r;
    }

    private static void ejecutar(Connection con, String sql, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
