package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.BitacoraDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

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

    private static String recortar(String t, int max) {
        return t == null ? null : (t.length() > max ? t.substring(0, max) : t);
    }
}
