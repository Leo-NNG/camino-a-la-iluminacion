package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.FechaDisponibleDAO;
import pe.caminoiluminacion.modelo.FechaDisponible;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FechaDisponibleDAOImpl implements FechaDisponibleDAO {

    /** Columnas de la fecha, el sacramento y cuántas solicitudes activas (ni rechazadas ni canceladas) la ocupan. */
    private static final String SELECT =
            "SELECT f.id_fecha, f.id_parroquia, f.id_sacramento, s.codigo, s.nombre, f.fecha, f.hora_inicio, f.cupo, "
          + "(SELECT COUNT(*) FROM reserva r WHERE r.id_parroquia = f.id_parroquia AND r.id_sacramento = f.id_sacramento "
          + "AND r.fecha_solicitada = f.fecha AND r.estado NOT IN ('rechazada','cancelada')) AS ocupados "
          + "FROM fecha_disponible f JOIN sacramento s ON s.id_sacramento = f.id_sacramento ";

    @Override
    public FechaDisponible insertar(FechaDisponible f) throws SQLException {
        String sql = "INSERT INTO fecha_disponible (id_parroquia, id_sacramento, fecha, hora_inicio, cupo) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, f.getIdParroquia());
            ps.setInt(2, f.getIdSacramento());
            ps.setDate(3, Date.valueOf(f.getFecha()));
            if (f.getHoraInicio() == null) ps.setNull(4, Types.TIME); else ps.setTime(4, Time.valueOf(f.getHoraInicio()));
            ps.setInt(5, f.getCupo());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) f.setIdFecha(keys.getInt(1));
            }
            return f;
        }
    }

    @Override
    public Optional<FechaDisponible> buscarPorId(int idFecha) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE f.id_fecha = ?")) {
            ps.setInt(1, idFecha);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<FechaDisponible> listarDesde(int idParroquia, LocalDate desde) throws SQLException {
        List<FechaDisponible> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE f.id_parroquia = ? AND f.fecha >= ? ORDER BY f.fecha, s.id_sacramento")) {
            ps.setInt(1, idParroquia);
            ps.setDate(2, Date.valueOf(desde));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public List<FechaDisponible> listarDesde(int idParroquia, int idSacramento, LocalDate desde) throws SQLException {
        List<FechaDisponible> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE f.id_parroquia = ? AND f.id_sacramento = ? AND f.fecha >= ? ORDER BY f.fecha")) {
            ps.setInt(1, idParroquia);
            ps.setInt(2, idSacramento);
            ps.setDate(3, Date.valueOf(desde));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    @Override
    public boolean actualizar(int idFecha, int cupo, LocalTime horaInicio) throws SQLException {
        String sql = "UPDATE fecha_disponible SET cupo = ?, hora_inicio = ? WHERE id_fecha = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, cupo);
            if (horaInicio == null) ps.setNull(2, Types.TIME); else ps.setTime(2, Time.valueOf(horaInicio));
            ps.setInt(3, idFecha);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean eliminar(int idFecha) throws SQLException {
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement("DELETE FROM fecha_disponible WHERE id_fecha = ?")) {
            ps.setInt(1, idFecha);
            return ps.executeUpdate() > 0;
        }
    }

    private static FechaDisponible mapear(ResultSet rs) throws SQLException {
        FechaDisponible f = new FechaDisponible();
        f.setIdFecha(rs.getInt("id_fecha"));
        f.setIdParroquia(rs.getInt("id_parroquia"));
        f.setIdSacramento(rs.getInt("id_sacramento"));
        f.setCodigoSacramento(rs.getString("codigo"));
        f.setNombreSacramento(rs.getString("nombre"));
        f.setFecha(rs.getDate("fecha").toLocalDate());
        Time hora = rs.getTime("hora_inicio");
        f.setHoraInicio(hora == null ? null : hora.toLocalTime());
        f.setCupo(rs.getInt("cupo"));
        f.setOcupados(rs.getInt("ocupados"));
        return f;
    }
}
