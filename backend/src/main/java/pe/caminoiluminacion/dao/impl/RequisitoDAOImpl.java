package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.RequisitoDAO;
import pe.caminoiluminacion.modelo.Requisito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RequisitoDAOImpl implements RequisitoDAO {

    private static final String SELECT = "SELECT q.*, s.codigo AS codigo_sacramento, s.nombre AS nombre_sacramento "
            + "FROM requisito_sacramento q JOIN sacramento s ON s.id_sacramento = q.id_sacramento ";

    @Override
    public Requisito insertar(Requisito r) throws SQLException {
        String sql = "INSERT INTO requisito_sacramento (id_parroquia, id_sacramento, nombre, descripcion, obligatorio, orden) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getIdParroquia());
            ps.setInt(2, r.getIdSacramento());
            ps.setString(3, r.getNombre());
            ps.setString(4, r.getDescripcion());
            ps.setBoolean(5, r.isObligatorio());
            ps.setInt(6, r.getOrden());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) r.setIdRequisito(keys.getInt(1));
            }
        }
        return r;
    }

    @Override
    public Optional<Requisito> buscarPorId(int idRequisito) throws SQLException {
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(SELECT + "WHERE q.id_requisito = ?")) {
            ps.setInt(1, idRequisito);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Requisito> listar(int idParroquia, Integer idSacramento) throws SQLException {
        String sql = SELECT + "WHERE q.id_parroquia = ? AND (? IS NULL OR q.id_sacramento = ?) ORDER BY q.id_sacramento, q.orden, q.id_requisito";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            ps.setObject(2, idSacramento, Types.INTEGER);
            ps.setObject(3, idSacramento, Types.INTEGER);
            List<Requisito> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
            return lista;
        }
    }

    @Override
    public boolean actualizar(int idRequisito, String nombre, String descripcion, boolean obligatorio, int orden) throws SQLException {
        String sql = "UPDATE requisito_sacramento SET nombre = ?, descripcion = ?, obligatorio = ?, orden = ? WHERE id_requisito = ?";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, descripcion);
            ps.setBoolean(3, obligatorio);
            ps.setInt(4, orden);
            ps.setInt(5, idRequisito);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean eliminar(int idRequisito) throws SQLException {
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement("DELETE FROM requisito_sacramento WHERE id_requisito = ?")) {
            ps.setInt(1, idRequisito);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public int contarDocumentos(int idRequisito) throws SQLException {
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM documento WHERE id_requisito = ?")) {
            ps.setInt(1, idRequisito);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Override
    public List<Cumplimiento> cumplimientos(int idReserva) throws SQLException {
        String sql = "SELECT id_requisito, id_documento, estado_validacion, motivo_rechazo FROM documento "
                + "WHERE id_reserva = ? AND id_requisito IS NOT NULL ORDER BY fecha_envio DESC, id_documento DESC";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idReserva);
            List<Cumplimiento> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(new Cumplimiento(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4)));
            }
            return lista;
        }
    }

    @Override
    public Map<Integer, Avance> avanceDeParroquia(int idParroquia) throws SQLException {
        String sql = "SELECT r.id_reserva, "
                + "(SELECT COUNT(*) FROM requisito_sacramento q WHERE q.id_parroquia = r.id_parroquia AND q.id_sacramento = r.id_sacramento AND q.obligatorio = 1) AS obligatorios, "
                + "(SELECT COUNT(DISTINCT d.id_requisito) FROM documento d JOIN requisito_sacramento q ON q.id_requisito = d.id_requisito "
                + "  WHERE d.id_reserva = r.id_reserva AND d.estado_validacion = 'validado' AND q.obligatorio = 1) AS validados "
                + "FROM reserva r WHERE r.id_parroquia = ? AND r.estado IN ('pendiente','confirmada')";
        try (Connection con = Conexion.obtener(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idParroquia);
            Map<Integer, Avance> mapa = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) mapa.put(rs.getInt(1), new Avance(rs.getInt(2), rs.getInt(3)));
            }
            return mapa;
        }
    }

    private static Requisito mapear(ResultSet rs) throws SQLException {
        Requisito r = new Requisito(rs.getInt("id_parroquia"), rs.getInt("id_sacramento"), rs.getString("nombre"),
                rs.getString("descripcion"), rs.getBoolean("obligatorio"), rs.getInt("orden"));
        r.setIdRequisito(rs.getInt("id_requisito"));
        r.setCodigoSacramento(rs.getString("codigo_sacramento"));
        r.setNombreSacramento(rs.getString("nombre_sacramento"));
        return r;
    }
}
