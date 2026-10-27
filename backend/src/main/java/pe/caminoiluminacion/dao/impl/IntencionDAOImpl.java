package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.IntencionDAO;
import pe.caminoiluminacion.modelo.Intencion;
import pe.caminoiluminacion.modelo.Pago;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IntencionDAOImpl implements IntencionDAO {

    private static final String SELECT = "SELECT i.id_intencion, i.id_usuario, i.id_parroquia, i.id_horario, i.hora_misa, "
            + "i.fecha_misa, i.tipo, i.texto, i.id_pago, i.creada_en, p.estado AS estado_pago, p.monto, p.observacion "
            + "FROM intencion_misa i JOIN pago p ON p.id_pago = i.id_pago ";

    @Override
    public Optional<Intencion> insertarConPago(Intencion i, Pago pago, int maxPorMisa) throws SQLException {
        try (Connection con = Conexion.obtener()) {
            con.setAutoCommit(false);
            try {
                // Bloquea la fila del horario hasta el commit: serializa las intenciones de esa misa.
                try (PreparedStatement lock = con.prepareStatement("SELECT id_horario FROM horario WHERE id_horario = ? FOR UPDATE")) {
                    lock.setInt(1, i.idHorario());
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new SQLException("El horario no existe: " + i.idHorario());
                    }
                }
                try (PreparedStatement cuenta = con.prepareStatement(
                        "SELECT COUNT(*) FROM intencion_misa i JOIN pago p ON p.id_pago = i.id_pago "
                      + "WHERE i.id_horario = ? AND i.fecha_misa = ? AND p.estado <> 'rechazado'")) {
                    cuenta.setInt(1, i.idHorario());
                    cuenta.setDate(2, i.fechaMisa());
                    try (ResultSet rs = cuenta.executeQuery()) {
                        if (rs.next() && rs.getInt(1) >= maxPorMisa) { con.rollback(); return Optional.empty(); }
                    }
                }
                Pago guardado = PagoDAOImpl.insertar(con, pago);
                try (PreparedStatement ins = con.prepareStatement(
                        "INSERT INTO intencion_misa (id_usuario, id_parroquia, id_horario, hora_misa, fecha_misa, tipo, texto, id_pago) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ins.setInt(1, i.idUsuario());
                    ins.setInt(2, i.idParroquia());
                    ins.setObject(3, i.idHorario(), Types.INTEGER);
                    ins.setString(4, i.horaMisa());
                    ins.setDate(5, i.fechaMisa());
                    ins.setString(6, i.tipo());
                    ins.setString(7, i.texto());
                    ins.setInt(8, guardado.idPago());
                    ins.executeUpdate();
                    try (ResultSet keys = ins.getGeneratedKeys()) {
                        int id = keys.next() ? keys.getInt(1) : 0;
                        con.commit();
                        return Optional.of(new Intencion(id, i.idUsuario(), i.idParroquia(), i.idHorario(), i.horaMisa(),
                                i.fechaMisa(), i.tipo(), i.texto(), guardado.idPago(), null));
                    }
                }
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<Fila> listarPorUsuario(int idUsuario) throws SQLException {
        return consultar(SELECT + "WHERE i.id_usuario = ? ORDER BY i.fecha_misa DESC, i.id_intencion DESC", idUsuario, null, null);
    }

    @Override
    public List<Fila> agenda(int idParroquia, Date desde, Date hasta, boolean soloAprobadas) throws SQLException {
        return consultar(SELECT + "WHERE i.id_parroquia = ? AND i.fecha_misa BETWEEN ? AND ? "
                + (soloAprobadas ? "AND p.estado = 'aprobado' " : "AND p.estado <> 'rechazado' ")
                + "ORDER BY i.fecha_misa, i.hora_misa, i.id_intencion", idParroquia, desde, hasta);
    }

    private static List<Fila> consultar(String sql, int id, Date desde, Date hasta) throws SQLException {
        List<Fila> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            if (desde != null) { ps.setDate(2, desde); ps.setDate(3, hasta); }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idHorario = rs.getInt("id_horario");
                    Integer horario = rs.wasNull() ? null : idHorario;
                    Intencion in = new Intencion(rs.getInt("id_intencion"), rs.getInt("id_usuario"), rs.getInt("id_parroquia"), horario,
                            rs.getString("hora_misa"), rs.getDate("fecha_misa"), rs.getString("tipo"), rs.getString("texto"),
                            rs.getInt("id_pago"), rs.getTimestamp("creada_en"));
                    lista.add(new Fila(in, rs.getString("estado_pago"), rs.getBigDecimal("monto"), rs.getString("observacion")));
                }
            }
        }
        return lista;
    }
}
