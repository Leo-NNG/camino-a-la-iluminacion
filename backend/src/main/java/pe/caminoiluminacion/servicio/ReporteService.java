package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.ReporteDAO;
import pe.caminoiluminacion.dao.ReporteDAO.FilaAgenda;
import pe.caminoiluminacion.dao.ReporteDAO.FilaDocumento;
import pe.caminoiluminacion.dao.ReporteDAO.FilaTramites;
import pe.caminoiluminacion.util.ExcelReporte;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reportes del panel (R1 a R3 del informe), siempre acotados a la parroquia del administrador. */
public class ReporteService {

    private static final Pattern MES = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    /** Un archivo listo para descargar. */
    public record Archivo(String nombre, byte[] datos) { }

    private final ReporteDAO dao;
    private final BitacoraService bitacora;

    public ReporteService(ReporteDAO dao, BitacoraService bitacora) {
        this.dao = dao;
        this.bitacora = bitacora;
    }

    /** R1: solicitudes por sacramento. mesTxt vacío = todos los meses. */
    public List<FilaTramites> tramites(int idParroquia, String mesTxt) throws SQLException {
        return dao.tramitesPorSacramento(idParroquia, validarMes(mesTxt));
    }

    /** R2: documentos pendientes de validación, del más antiguo al más reciente. */
    public List<FilaDocumento> documentosPendientes(int idParroquia) throws SQLException {
        return dao.documentosPendientes(idParroquia);
    }

    /** R3: celebraciones confirmadas próximas. */
    public List<FilaAgenda> agenda(int idParroquia) throws SQLException {
        return dao.agenda(idParroquia);
    }

    /** Genera el Excel del reporte pedido (tipo: tramites, documentos o agenda). */
    public Archivo exportar(int idAdmin, int idParroquia, String tipo, String mesTxt, String ip) throws SQLException, IOException {
        Archivo archivo;
        if ("tramites".equals(tipo)) {
            String mes = validarMes(mesTxt);
            List<FilaTramites> filas = dao.tramitesPorSacramento(idParroquia, mes);
            List<List<Object>> datos = new ArrayList<>();
            int s = 0, c = 0, p = 0, r = 0;
            for (FilaTramites f : filas) {
                datos.add(new ArrayList<>(List.of(f.sacramento(), f.solicitudes(), f.confirmadas(), f.pendientes(), f.rechazadas())));
                s += f.solicitudes(); c += f.confirmadas(); p += f.pendientes(); r += f.rechazadas();
            }
            datos.add(new ArrayList<>(List.of("Total", s, c, p, r)));
            archivo = new Archivo("tramites-" + (mes == null ? "todos" : mes) + ".xlsx",
                    ExcelReporte.generar("Trámites por sacramento",
                            List.of("Sacramento", "Solicitudes", "Confirmadas", "Pendientes", "Rechazadas"), datos, true));
        } else if ("documentos".equals(tipo)) {
            List<List<Object>> datos = new ArrayList<>();
            for (FilaDocumento f : dao.documentosPendientes(idParroquia)) {
                datos.add(new ArrayList<>(List.of(f.feligres(), f.documento(), f.sacramento(), f.solicitante(), f.fechaEnvio(), f.diasEspera())));
            }
            archivo = new Archivo("documentos-pendientes.xlsx",
                    ExcelReporte.generar("Documentos pendientes",
                            List.of("Feligrés", "Documento", "Sacramento", "Solicitante", "Enviado el", "Días de espera"), datos, false));
        } else if ("agenda".equals(tipo)) {
            List<List<Object>> datos = new ArrayList<>();
            for (FilaAgenda f : dao.agenda(idParroquia)) {
                datos.add(new ArrayList<>(List.of(f.fecha(), f.sacramento(), f.solicitante(), f.contacto() == null ? "" : f.contacto())));
            }
            archivo = new Archivo("agenda-reservas.xlsx",
                    ExcelReporte.generar("Agenda de reservas",
                            List.of("Fecha", "Sacramento", "Solicitante", "Contacto"), datos, false));
        } else {
            throw new ReglaNegocioException(400, "Reporte no válido.");
        }
        bitacora.registrar(idAdmin, "REPORTE_EXPORTADO", tipo, ip);
        return archivo;
    }

    /** @return el mes en formato AAAA-MM, o null si no se pidió filtro. */
    static String validarMes(String mesTxt) {
        if (mesTxt == null || mesTxt.isBlank()) return null;
        String mes = mesTxt.trim();
        if (!MES.matcher(mes).matches()) throw new ReglaNegocioException(400, "El mes debe tener el formato AAAA-MM (por ejemplo 2026-10).");
        return mes;
    }
}
