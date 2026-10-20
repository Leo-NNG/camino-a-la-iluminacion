package pe.caminoiluminacion.dao.impl;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.seguridad.Cripto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UsuarioDAOImpl implements UsuarioDAO {

    @Override
    public Usuario insertar(Usuario u) throws SQLException {
        String sql = "INSERT INTO usuario (dni, dni_indice, nombre, apellido, correo, telefono, contrasena_hash, rol, ubicacion, id_parroquia) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, Cripto.cifrar(u.getDni()));   // el DNI se guarda cifrado (AES-256-GCM)
            ps.setString(2, Cripto.indice(u.getDni()));   // huella HMAC: permite buscar por DNI sin guardarlo en claro
            ps.setString(3, u.getNombre());
            ps.setString(4, u.getApellido());
            ps.setString(5, u.getCorreo());
            ps.setString(6, u.getTelefono());
            ps.setString(7, u.getContrasenaHash());
            ps.setString(8, u.getRol());
            ps.setString(9, u.getUbicacion());
            ps.setObject(10, u.getIdParroquia(), Types.INTEGER);
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
    public Optional<Usuario> buscarPorDni(String dni) throws SQLException {
        // Se busca por la huella (dni_indice). La segunda condición cubre filas aún no migradas (dni en claro).
        String sql = "SELECT * FROM usuario WHERE dni_indice = ? OR (dni_indice IS NULL AND dni = ?)";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, Cripto.indice(dni));
            ps.setString(2, dni);
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
        String sql = "UPDATE usuario SET dni=?, dni_indice=?, nombre=?, apellido=?, correo=?, telefono=?, rol=?, ubicacion=?, id_parroquia=? "
                   + "WHERE id_usuario=?";
        try (Connection con = Conexion.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, Cripto.cifrar(u.getDni()));
            ps.setString(2, Cripto.indice(u.getDni()));
            ps.setString(3, u.getNombre());
            ps.setString(4, u.getApellido());
            ps.setString(5, u.getCorreo());
            ps.setString(6, u.getTelefono());
            ps.setString(7, u.getRol());
            ps.setString(8, u.getUbicacion());
            ps.setObject(9, u.getIdParroquia(), Types.INTEGER);
            ps.setInt(10, u.getIdUsuario());
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
        u.setDni(Cripto.descifrar(rs.getString("dni")));
        int idParroquia = rs.getInt("id_parroquia");
        u.setIdParroquia(rs.wasNull() ? null : idParroquia);
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
