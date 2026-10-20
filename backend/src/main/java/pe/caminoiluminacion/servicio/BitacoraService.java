package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.BitacoraDAO;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registra acciones críticas (RNF09). Es de "mejor esfuerzo": si la bitácora falla, la acción del usuario
 * NO se cancela (se deja constancia en el log del servidor). Nunca debe recibir DNI, contraseñas ni
 * contenido de documentos.
 */
public class BitacoraService {

    private static final Logger LOG = Logger.getLogger(BitacoraService.class.getName());

    private final BitacoraDAO dao;

    public BitacoraService(BitacoraDAO dao) {
        this.dao = dao;
    }

    public void registrar(Integer idUsuario, String accion, String detalle, String ip) {
        try {
            dao.registrar(idUsuario, accion, detalle, ip);
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo registrar en la bitácora la acción " + accion, e);
        }
    }
}
