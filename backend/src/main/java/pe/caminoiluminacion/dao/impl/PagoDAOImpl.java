package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.PagoDAO;
import pe.caminoiluminacion.modelo.Pago;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PagoDAOImpl implements PagoDAO {

    private static final String INSERT = "INSERT INTO pago (id_usuario, id_parroquia, id_reserva, concepto, monto, metodo, "
            + "celular_origen, numero_operacion, ruta_voucher, voucher_sha256) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public Optional<Pago> insertarArancel(Pago p) throws SQLException {
        try (Connection con = Conexion.obtener()) {
            con.setAutoCommit(false);
            try {
                // Bloquea la fila de la reserva hasta el commit: dos pagos simultáneos de la misma reserva se serializan.
                try (PreparedStatement lock = con.prepareStatement("SELECT id_reserva FROM reserva WHERE id_reserva = ? FOR UPDATE")) {
                    lock.setInt(1, p.idReserva());
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new SQLException("La reserva no existe: " + p.idReserva());
                    }
                }
                try (PreparedStatement chk = con.prepareStatement(
                        "SELECT 1 FROM pago WHERE id_reserva = ? AND estado <> 'rechazado' LIMIT 1")) {
                    chk.setInt(1, p.idReserva());
                    try (ResultSet rs = chk.executeQuery()) {
                        if (rs.next()) { con.rollback(); return Optional.empty(); }
                    }
                }
                Pago guardado = insertar(con, p);
                con.commit();
                return Optional.of(guardado);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    @Override
    public Pago insertarLibre(Pago p) throws SQLException {
        try (Connection con = Conexion.obtener()) {
            return insertar(con, p);
        }
    }

    /** Inserta el pago con la conexión dada (la usa también IntencionDAOImpl dentro de su transacción). */
    static Pago insertar(Connection con, Pago p) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.idUsuario());
            ps.setInt(2, p.idParroquia());
            ps.setObject(3, p.idReserva(), Types.INTEGER);
            ps.setString(4, p.concepto());
            ps.setBigDecimal(5, p.monto());
            ps.setString(6, p.metodo());
            ps.setString(7, p.celularOrigen());
            ps.setString(8, p.numeroOperacion());
            ps.setString(9, p.rutaVoucher());
            ps.setString(10, p.voucherSha256());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? p.conId(keys.getInt(1)) : p;
            }
        }
    }

    @Override
    public Optional<Pago> buscarPorId(int idPago) throws SQLException {
        return uno("SELECT * FROM pago WHERE id_pago = ?", idPago);
    }

    @Override
    public Optional<Pago> vigenteDeReserva(int idReserva) throws SQLException {
        return uno("SELECT * FROM pago WHERE id_reserva = ? AND estado <> 'rechazado' ORDER BY id_pago DESC LIMIT 1", idReserva);
    }

    @Override
    public List<Pago> listarPorUsuario(int idUsuario) throws SQLException {
        return varios("SELECT * FROM pago WHERE id_usuario = ? ORDER BY creado_en DESC, id_pago DESC", idUsuario, null);
    }

    @Override
    public List<Pago> listarPorParroquiaYEstado(int idParroquia, String estado) throws SQLException {
        return varios("SELECT * FROM pago WHERE id_parroquia = ? AND estado = ? ORDER BY creado_en ASC, id_pago ASC", idParroquia, estado);
    }

    @Override
    public boolean existeOperacionVigente(String metodo, String numeroOperacion) throws SQLException {
        return existe("SELECT 1 FROM pago WHERE clave_operacion = ? LIMIT 1", metodo + ":" + numeroOperacion);
    }

    @Override
    public boolean existeVoucherVigente(String sha256) throws SQLException {
        return existe("SELECT 1 FROM pago WHERE clave_voucher = ? LIMIT 1", sha256);
    }

    @Override
    public boolean resolverPendiente(int idPago, String nuevoEstado, String observacion, int idRevisor) throws SQLException {
        String sql = "UPDATE pago SET estado = ?, observacion = ?, revisado_por = ?, revisado_en = CURRENT_TIMESTAMP "
                   + "WHERE id_pago = ? AND estado = 'pendiente'";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setString(2, observacion == null || observacion.isBlank() ? null : observacion);
            ps.setInt(3, idRevisor);
            ps.setInt(4, idPago);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public List<FilaIngreso> ingresosPorConcepto(int idParroquia, String mes) throws SQLException {
        String sql = "SELECT concepto, COUNT(*) AS pagos, COALESCE(SUM(monto), 0) AS total FROM pago "
                   + "WHERE id_parroquia = ? AND estado = 'aprobado' "
                   + (mes != null ? "AND DATE_FORMAT(creado_en, '%Y-%m') = ? " : "")
                   + "GROUP BY concepto ORDER BY concepto";
        List<FilaIngreso> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            if (mes != null) ps.setString(2, mes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(new FilaIngreso(rs.getString("concepto"), rs.getInt("pagos"), rs.getBigDecimal("total")));
            }
        }
        return lista;
    }

    // ------------------------------------------------------------ auxiliares

    private static boolean existe(String sql, String valor) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Optional<Pago> uno(String sql, int id) throws SQLException {
        List<Pago> l = varios(sql, id, null);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    private static List<Pago> varios(String sql, int id, String texto) throws SQLException {
        List<Pago> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            if (texto != null) ps.setString(2, texto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    private static Pago mapear(ResultSet rs) throws SQLException {
        int idReserva = rs.getInt("id_reserva");
        Integer reserva = rs.wasNull() ? null : idReserva;
        return new Pago(rs.getInt("id_pago"), rs.getInt("id_usuario"), rs.getInt("id_parroquia"), reserva,
                rs.getString("concepto"), rs.getBigDecimal("monto"), rs.getString("metodo"), rs.getString("celular_origen"),
                rs.getString("numero_operacion"), rs.getString("ruta_voucher"), rs.getString("voucher_sha256"),
                rs.getString("estado"), rs.getString("observacion"), rs.getTimestamp("creado_en"), rs.getTimestamp("revisado_en"));
    }
}
