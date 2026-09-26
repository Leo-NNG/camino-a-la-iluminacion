package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Aviso;
import java.sql.SQLException;
import java.util.List;

public interface AvisoDAO {
    Aviso insertar(Aviso aviso) throws SQLException;
    List<Aviso> listarPorParroquia(int idParroquia) throws SQLException;
    List<Aviso> listarUrgentes(int idParroquia) throws SQLException;
    boolean eliminar(int idAviso) throws SQLException;
}
