package pe.caminoiluminacion.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.caminoiluminacion.dao.BitacoraDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Registra acciones críticas (RNF09). Es de "mejor esfuerzo": si la bitácora falla, la acción del usuario
 * NO se cancela (se deja constancia en el log del servidor). Nunca debe recibir DNI, contraseñas ni
 * contenido de documentos.
 */
public class BitacoraService {

    private static final Logger LOG = LoggerFactory.getLogger(BitacoraService.class);

    /** Máximo de líneas que muestra el panel por consulta. */
    public static final int LIMITE_MAXIMO = 200;
    public static final int LIMITE_POR_DEFECTO = 100;
    private static final Pattern CODIGO_ACCION = Pattern.compile("^[A-Z_]{3,40}$");

    private final BitacoraDAO dao;

    public BitacoraService(BitacoraDAO dao) {
        this.dao = dao;
    }

    public void registrar(Integer idUsuario, String accion, String detalle, String ip) {
        try {
            dao.registrar(idUsuario, accion, detalle, ip);
        } catch (SQLException | RuntimeException e) {
            LOG.warn("No se pudo registrar en la bitácora la acción {}", accion, e);
        }
    }

    /**
     * Actividad reciente del personal de la parroquia del administrador (panel «Bitácora»).
     *
     * @param accion código exacto (p. ej. PAGO_APROBADO); vacío o null = todas
     * @param limite cuántas líneas (se ajusta a 1..{@value #LIMITE_MAXIMO}; null = {@value #LIMITE_POR_DEFECTO})
     * @throws ReglaNegocioException 400 si el código de acción no tiene el formato esperado
     */
    public List<BitacoraDAO.Entrada> recientesDelPersonal(int idParroquia, String accion, Integer limite) throws SQLException {
        String filtro = accion == null || accion.isBlank() ? null : accion.trim().toUpperCase();
        if (filtro != null && !CODIGO_ACCION.matcher(filtro).matches()) {
            throw new ReglaNegocioException(400, "Acción no válida.");
        }
        int n = limite == null ? LIMITE_POR_DEFECTO : Math.max(1, Math.min(LIMITE_MAXIMO, limite));
        return dao.recientesDelPersonal(idParroquia, filtro, n);
    }
}
