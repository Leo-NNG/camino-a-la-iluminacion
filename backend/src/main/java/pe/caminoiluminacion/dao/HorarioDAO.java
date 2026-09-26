package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Horario;
import java.sql.SQLException;
import java.util.List;

public interface HorarioDAO {
    Horario insertar(Horario horario) throws SQLException;
    List<Horario> listarPorParroquia(int idParroquia) throws SQLException;
    boolean actualizar(Horario horario) throws SQLException;
    boolean eliminar(int idHorario) throws SQLException;
}
