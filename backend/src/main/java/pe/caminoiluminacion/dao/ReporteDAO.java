package pe.caminoiluminacion.dao;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Consultas de solo lectura para los reportes del panel (siempre de UNA parroquia). */
public interface ReporteDAO {

    /** R1. Solicitudes por sacramento, con el desglose por estado. {@code solicitudes} incluye las canceladas. */
    record FilaTramites(String sacramento, int solicitudes, int confirmadas, int pendientes, int rechazadas, int canceladas) { }

    /** R2. Un documento pendiente de revisión y cuántos días lleva esperando. */
    record FilaDocumento(String feligres, String documento, String sacramento, String solicitante,
                         String fechaEnvio, int diasEspera) { }

    /** R3. Una celebración confirmada próxima. */
    record FilaAgenda(String fecha, String sacramento, String solicitante, String contacto) { }

    /** R4. Un pago APROBADO (ingreso verificado) para el reporte de ingresos. */
    record FilaPagoIngreso(String fecha, String feligres, String concepto, String metodo, String numeroOperacion,
                           BigDecimal monto, String codigoRecibo) { }

    /** Contadores del resumen (dashboard) del panel. */
    record Resumen(int solicitudesPendientes, int documentosPendientes, int pagosPorVerificar,
                   int celebracionesProximas, int intencionesProximas,
                   int solicitudesMes, int canceladasMes, int pagosMes, BigDecimal ingresosMes) { }

    /**
     * @param mes "AAAA-MM" para filtrar por el mes en que se hizo la solicitud, o null para todos.
     *            Se devuelven TODOS los sacramentos del catálogo (con 0 si no hubo solicitudes).
     */
    List<FilaTramites> tramitesPorSacramento(int idParroquia, String mes) throws SQLException;

    List<FilaDocumento> documentosPendientes(int idParroquia) throws SQLException;

    List<FilaAgenda> agenda(int idParroquia) throws SQLException;

    /** Pagos aprobados de la parroquia, por fecha de pago; {@code mes} "AAAA-MM" o null para todos. */
    List<FilaPagoIngreso> ingresos(int idParroquia, String mes) throws SQLException;

    /**
     * Contadores del panel. Las fechas llegan desde Java (hora de Perú) para no depender de la zona del servidor de BD.
     * @param hoy        día actual: celebraciones e intenciones se cuentan de hoy a hoy + {@code diasProximos} - 1
     * @param inicioMes  primer día del mes actual (solicitudes, cancelaciones e ingresos del mes)
     */
    Resumen resumen(int idParroquia, LocalDate hoy, int diasProximos, LocalDate inicioMes) throws SQLException;
}
