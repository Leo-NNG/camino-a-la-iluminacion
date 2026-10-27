package pe.caminoiluminacion.dao;

import java.sql.SQLException;
import java.util.List;

/** Consultas de solo lectura para los reportes del panel (siempre de UNA parroquia). */
public interface ReporteDAO {

    /** R1. Solicitudes por sacramento, con el desglose por estado. */
    record FilaTramites(String sacramento, int solicitudes, int confirmadas, int pendientes, int rechazadas) { }

    /** R2. Un documento pendiente de revisión y cuántos días lleva esperando. */
    record FilaDocumento(String feligres, String documento, String sacramento, String solicitante,
                         String fechaEnvio, int diasEspera) { }

    /** R3. Una celebración confirmada próxima. */
    record FilaAgenda(String fecha, String sacramento, String solicitante, String contacto) { }

    /**
     * @param mes "AAAA-MM" para filtrar por el mes en que se hizo la solicitud, o null para todos.
     *            Se devuelven TODOS los sacramentos del catálogo (con 0 si no hubo solicitudes).
     */
    List<FilaTramites> tramitesPorSacramento(int idParroquia, String mes) throws SQLException;

    List<FilaDocumento> documentosPendientes(int idParroquia) throws SQLException;

    List<FilaAgenda> agenda(int idParroquia) throws SQLException;
}
