package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.modelo.Usuario;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsuarioDAOImpl implements UsuarioDAO {

    @Override
    public Usuario insertar(Usuario u) throws SQLException {
        String sql = "INSERT INTO usuario (nombre, apellido, correo, telefono, contrasena_hash, rol, ubicacion) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getNombre());
            ps.setString(2, u.getApellido());
            ps.setString(3, u.getCorreo());
            ps.setString(4, u.getTelefono());
            ps.setString(5, u.getContrasenaHash());
            ps.setString(6, u.getRol());
            ps.setString(7, u.getUbicacion());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) u.setIdUsuario(keys.getInt(1));
            }
            return u;
        }
    }

    @Override
    public Optional<Usuario> buscarPorId(int idUsuario) throws SQLException {
        String sql = "SELECT * FROM usuario WHERE id_usuario = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) throws SQLException {
        String sql = "SELECT * FROM usuario WHERE correo = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, correo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Usuario> listarTodos() throws SQLException {
        String sql = "SELECT * FROM usuario ORDER BY id_usuario";
        List<Usuario> lista = new ArrayList<>();
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    @Override
    public boolean actualizar(Usuario u) throws SQLException {
        String sql = "UPDATE usuario SET nombre=?, apellido=?, correo=?, telefono=?, rol=?, ubicacion=? "
                   + "WHERE id_usuario=?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, u.getNombre());
            ps.setString(2, u.getApellido());
            ps.setString(3, u.getCorreo());
            ps.setString(4, u.getTelefono());
            ps.setString(5, u.getRol());
            ps.setString(6, u.getUbicacion());
            ps.setInt(7, u.getIdUsuario());
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean eliminar(int idUsuario) throws SQLException {
        String sql = "DELETE FROM usuario WHERE id_usuario = ?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            return ps.executeUpdate() > 0;
        }
    }

    private Usuario mapear(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setIdUsuario(rs.getInt("id_usuario"));
        u.setNombre(rs.getString("nombre"));
        u.setApellido(rs.getString("apellido"));
        u.setCorreo(rs.getString("correo"));
        u.setTelefono(rs.getString("telefono"));
        u.setContrasenaHash(rs.getString("contrasena_hash"));
        u.setRol(rs.getString("rol"));
        u.setUbicacion(rs.getString("ubicacion"));
        u.setCreadoEn(rs.getTimestamp("creado_en"));
        return u;
    }
}
