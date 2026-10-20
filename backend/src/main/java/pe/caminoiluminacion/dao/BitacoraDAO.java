package pe.caminoiluminacion.dao;

import java.sql.SQLException;

/** Registro de acciones críticas (RNF09). */
public interface BitacoraDAO {

    /**
     * @param idUsuario quién hizo la acción (null si no se pudo identificar)
     * @param accion    código corto, ej. LOGIN_OK
     * @param detalle   texto breve SIN datos personales (nada de DNI, contraseñas ni contenido de archivos)
     * @param ip        dirección de origen
     */
    void registrar(Integer idUsuario, String accion, String detalle, String ip) throws SQLException;
}
