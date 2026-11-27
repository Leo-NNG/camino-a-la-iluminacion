package pe.caminoiluminacion.dao;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Registro de acciones críticas (RNF09). */
public interface BitacoraDAO {

    /** Una línea de la bitácora tal como se muestra en el panel (sin DNI ni correo). */
    record Entrada(LocalDateTime fecha, String usuario, String accion, String detalle, String ip) { }

    /**
     * @param idUsuario quién hizo la acción (null si no se pudo identificar)
     * @param accion    código corto, ej. LOGIN_OK
     * @param detalle   texto breve SIN datos personales (nada de DNI, contraseñas ni contenido de archivos)
     * @param ip        dirección de origen
     */
    void registrar(Integer idUsuario, String accion, String detalle, String ip) throws SQLException;

    /**
     * Últimas acciones del PERSONAL (administradores) de una parroquia, de la más reciente a la más antigua.
     * No incluye a los feligreses ni al personal de otras parroquias.
     *
     * @param accion código exacto para filtrar, o null para todas
     */
    List<Entrada> recientesDelPersonal(int idParroquia, String accion, int limite) throws SQLException;
}
