package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Usuario;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface UsuarioDAO {
    Usuario insertar(Usuario usuario) throws SQLException;
    Optional<Usuario> buscarPorId(int idUsuario) throws SQLException;
    Optional<Usuario> buscarPorCorreo(String correo) throws SQLException;
    Optional<Usuario> buscarPorDni(String dni) throws SQLException;
    List<Usuario> listarTodos() throws SQLException;
    boolean actualizar(Usuario usuario) throws SQLException;
    boolean eliminar(int idUsuario) throws SQLException;
}
