package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.Aviso;
import java.sql.SQLException;
import java.util.List;

public interface AvisoDAO {
    Aviso insertar(Aviso aviso) throws SQLException;
    java.util.Optional<Aviso> buscarPorId(int idAviso) throws SQLException;
    List<Aviso> listarPorParroquia(int idParroquia) throws SQLException;
    /** Los avisos urgentes más recientes de TODAS las parroquias (para las alertas del sitio). */
    List<Aviso> listarUrgentesRecientes(int limite) throws SQLException;
    List<Aviso> listarUrgentes(int idParroquia) throws SQLException;
    boolean eliminar(int idAviso) throws SQLException;
}
