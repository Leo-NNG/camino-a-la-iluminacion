package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.modelo.Reserva;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReservaDAOImpl implements ReservaDAO {

    /** Columnas de una reserva con el código de su sacramento; las usan todas las lecturas para que mapear() sea uno solo. */
    private static final String COLUMNAS = "r.id_reserva, r.id_usuario, r.id_parroquia, s.codigo AS sacramento, r.nombre_solicitante, "
            + "r.fecha_solicitada, r.notas, r.estado, r.motivo_rechazo, r.creado_en, r.fecha_original, r.reprogramaciones, "
            + "r.cancelada_en, r.motivo_cancelacion";

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
    public boolean insertarSiDisponible(Reserva r, int cupo) throws SQLException {
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
                        "SELECT COUNT(*) FROM reserva WHERE id_parroquia = ? AND id_sacramento = (SELECT id_sacramento FROM sacramento WHERE codigo = ?) "
                      + "AND fecha_solicitada = ? AND estado NOT IN ('rechazada','cancelada')")) {
                    chk.setInt(1, r.getIdParroquia());
                    chk.setString(2, r.getSacramento());
                    chk.setDate(3, r.getFechaSolicitada());
                    try (ResultSet rs = chk.executeQuery()) {
                        if (rs.next() && rs.getInt(1) >= cupo) { con.rollback(); return false; }
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
    public boolean cancelar(int idReserva, String motivo) throws SQLException {
        String sql = "UPDATE reserva SET estado = 'cancelada', cancelada_en = NOW(), motivo_cancelacion = ? "
                   + "WHERE id_reserva = ? AND estado IN ('pendiente','confirmada')";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, motivo);
            ps.setInt(2, idReserva);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public ResultadoReprogramacion reprogramar(int idReserva, Date nuevaFecha, int cupo, int maximo) throws SQLException {
        try (Connection con = Conexion.obtener()) {
            con.setAutoCommit(false);
            try {
                ResultadoReprogramacion r = reprogramar(con, idReserva, nuevaFecha, cupo, maximo);
                if (r == ResultadoReprogramacion.HECHA) con.commit(); else con.rollback();
                return r;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private ResultadoReprogramacion reprogramar(Connection con, int idReserva, Date nuevaFecha, int cupo, int maximo) throws SQLException {
        // 1) Qué parroquia y sacramento son (sin bloquear todavía).
        int idParroquia, idSacramento;
        try (PreparedStatement ps = con.prepareStatement("SELECT id_parroquia, id_sacramento FROM reserva WHERE id_reserva = ?")) {
            ps.setInt(1, idReserva);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return ResultadoReprogramacion.NO_VIGENTE;
                idParroquia = rs.getInt(1);
                idSacramento = rs.getInt(2);
            }
        }
        // 2) Mismo orden de bloqueo que insertarSiDisponible: primero la parroquia, luego la reserva.
        try (PreparedStatement ps = con.prepareStatement("SELECT id_parroquia FROM parroquia WHERE id_parroquia = ? FOR UPDATE")) {
            ps.setInt(1, idParroquia);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); }
        }
        // 3) Con todo bloqueado se vuelve a leer: el estado pudo cambiar mientras tanto.
        try (PreparedStatement ps = con.prepareStatement("SELECT estado, reprogramaciones FROM reserva WHERE id_reserva = ? FOR UPDATE")) {
            ps.setInt(1, idReserva);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return ResultadoReprogramacion.NO_VIGENTE;
                String estado = rs.getString(1);
                if (!"pendiente".equals(estado) && !"confirmada".equals(estado)) return ResultadoReprogramacion.NO_VIGENTE;
                if (rs.getInt(2) >= maximo) return ResultadoReprogramacion.LIMITE;
            }
        }
        // 4) Cupo de la fecha nueva, sin contar esta misma solicitud. Debe ser una lectura CON BLOQUEO (FOR UPDATE): la lectura
        //    normal de InnoDB usa la "foto" fijada por el primer SELECT de esta transacción, que es anterior a la espera del bloqueo
        //    de la parroquia, y no vería lo que otra transacción confirmó mientras esperábamos (se pasaría del cupo).
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM reserva WHERE id_parroquia = ? AND id_sacramento = ? "
                + "AND fecha_solicitada = ? AND estado NOT IN ('rechazada','cancelada') AND id_reserva <> ? FOR UPDATE")) {
            ps.setInt(1, idParroquia);
            ps.setInt(2, idSacramento);
            ps.setDate(3, nuevaFecha);
            ps.setInt(4, idReserva);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) >= cupo) return ResultadoReprogramacion.SIN_CUPO;
            }
        }
        // 5) Se mueve: la primera vez se guarda la fecha original; vuelve a quedar pendiente de validación.
        try (PreparedStatement ps = con.prepareStatement("UPDATE reserva SET fecha_original = COALESCE(fecha_original, fecha_solicitada), "
                + "fecha_solicitada = ?, estado = 'pendiente', motivo_rechazo = NULL, reprogramaciones = reprogramaciones + 1 WHERE id_reserva = ?")) {
            ps.setDate(1, nuevaFecha);
            ps.setInt(2, idReserva);
            ps.executeUpdate();
        }
        return ResultadoReprogramacion.HECHA;
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
        String sql = "SELECT " + COLUMNAS + " FROM reserva r JOIN sacramento s ON s.id_sacramento = r.id_sacramento WHERE r.id_reserva = ?";
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
        String sql = "SELECT " + COLUMNAS + " FROM reserva r JOIN sacramento s ON s.id_sacramento = r.id_sacramento WHERE r.id_usuario = ? ORDER BY r.creado_en DESC";
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
        String sql = "SELECT " + COLUMNAS + " FROM reserva r JOIN sacramento s ON s.id_sacramento = r.id_sacramento WHERE r.estado = ? ORDER BY r.fecha_solicitada";
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
        String sql = "SELECT " + COLUMNAS + " FROM reserva r JOIN sacramento s ON s.id_sacramento = r.id_sacramento WHERE r.estado = ? AND r.id_parroquia = ? ORDER BY r.fecha_solicitada";
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
        r.setFechaOriginal(rs.getDate("fecha_original"));
        r.setReprogramaciones(rs.getInt("reprogramaciones"));
        r.setCanceladaEn(rs.getTimestamp("cancelada_en"));
        r.setMotivoCancelacion(rs.getString("motivo_cancelacion"));
        return r;
    }
}
