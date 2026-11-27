package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.BitacoraDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class BitacoraDAOImpl implements BitacoraDAO {

    @Override
    public void registrar(Integer idUsuario, String accion, String detalle, String ip) throws SQLException {
        String sql = "INSERT INTO bitacora (id_usuario, accion, detalle, ip) VALUES (?, ?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, idUsuario, Types.INTEGER);
            ps.setString(2, recortar(accion, 40));
            ps.setString(3, recortar(detalle, 255));
            ps.setString(4, recortar(ip, 45));
            ps.executeUpdate();
        }
    }

    @Override
    public List<Entrada> recientesDelPersonal(int idParroquia, String accion, int limite) throws SQLException {
        String sql = "SELECT b.creado_en, u.nombre, u.apellido, b.accion, b.detalle, b.ip "
                + "FROM bitacora b JOIN usuario u ON u.id_usuario = b.id_usuario "
                + "WHERE u.rol = 'administrador' AND u.id_parroquia = ? "
                + (accion == null ? "" : "AND b.accion = ? ")
                + "ORDER BY b.creado_en DESC, b.id_bitacora DESC LIMIT ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, idParroquia);
            if (accion != null) ps.setString(i++, accion);
            ps.setInt(i, limite);
            List<Entrada> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp t = rs.getTimestamp("creado_en");
                    lista.add(new Entrada(t == null ? null : t.toLocalDateTime(),
                            (rs.getString("nombre") + " " + rs.getString("apellido")).trim(),
                            rs.getString("accion"), rs.getString("detalle"), rs.getString("ip")));
                }
            }
            return lista;
        }
    }

    private static String recortar(String t, int max) {
        return t == null ? null : (t.length() > max ? t.substring(0, max) : t);
    }
}
