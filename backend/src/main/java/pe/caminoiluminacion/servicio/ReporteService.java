package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.ReporteDAO;
import pe.caminoiluminacion.dao.ReporteDAO.FilaAgenda;
import pe.caminoiluminacion.dao.ReporteDAO.FilaDocumento;
import pe.caminoiluminacion.dao.ReporteDAO.FilaPagoIngreso;
import pe.caminoiluminacion.dao.ReporteDAO.FilaTramites;
import pe.caminoiluminacion.dao.ReporteDAO.Resumen;
import pe.caminoiluminacion.util.ExcelReporte;
import pe.caminoiluminacion.util.ExcelReporte.Hoja;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Reportes del panel (R1 a R4 del informe) y su resumen, siempre acotados a la parroquia del administrador. */
public class ReporteService {

    private static final Pattern MES = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    /** Un archivo listo para descargar. */
    public record Archivo(String nombre, byte[] datos) { }

    /** R4: ingresos aprobados de un concepto, separados por método de pago. */
    public record IngresoConcepto(String concepto, int pagos, BigDecimal yape, BigDecimal plin, BigDecimal total) { }

    /** Días que cuenta el resumen como "próximos" (hoy y los 6 siguientes). */
    public static final int DIAS_PROXIMOS = 7;

    /** Conceptos de pago en el orden en que se muestran, con su nombre para el reporte. */
    private static final Map<String, String> CONCEPTOS = new LinkedHashMap<>();
    static {
        CONCEPTOS.put("arancel", "Arancel");
        CONCEPTOS.put("intencion", "Intención de misa");
        CONCEPTOS.put("donacion", "Donación");
        CONCEPTOS.put("diezmo", "Diezmo");
    }
    private static final Map<String, String> METODOS = Map.of("yape", "Yape", "plin", "Plin");

    private final ReporteDAO dao;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public ReporteService(ReporteDAO dao, BitacoraService bitacora) {
        this(dao, bitacora, IntencionService.relojDePeru());
    }

    /** @param reloj da el "hoy" del resumen (hora de Perú en producción; fijo en las pruebas) */
    public ReporteService(ReporteDAO dao, BitacoraService bitacora, Clock reloj) {
        this.dao = dao;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /** Resumen (dashboard) del panel: pendientes de atender, próximos 7 días y movimiento del mes en curso. */
    public Resumen resumen(int idParroquia) throws SQLException {
        LocalDate hoy = LocalDate.now(reloj);
        return dao.resumen(idParroquia, hoy, DIAS_PROXIMOS, hoy.withDayOfMonth(1));
    }

    /** R4: ingresos aprobados por concepto (siempre los cuatro conceptos, con 0 si no hubo pagos). mesTxt vacío = todos. */
    public List<IngresoConcepto> ingresos(int idParroquia, String mesTxt) throws SQLException {
        return agruparIngresos(dao.ingresos(idParroquia, validarMes(mesTxt)));
    }

    static List<IngresoConcepto> agruparIngresos(List<FilaPagoIngreso> pagos) {
        List<IngresoConcepto> lista = new ArrayList<>();
        for (String concepto : CONCEPTOS.keySet()) {
            int n = 0;
            BigDecimal yape = BigDecimal.ZERO, plin = BigDecimal.ZERO;
            for (FilaPagoIngreso p : pagos) {
                if (!concepto.equals(p.concepto())) continue;
                n++;
                if ("yape".equals(p.metodo())) yape = yape.add(p.monto());
                else plin = plin.add(p.monto());
            }
            lista.add(new IngresoConcepto(concepto, n, yape, plin, yape.add(plin)));
        }
        return lista;
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

    /** Genera el Excel del reporte pedido (tipo: tramites, documentos, agenda o ingresos). */
    public Archivo exportar(int idAdmin, int idParroquia, String tipo, String mesTxt, String ip) throws SQLException, IOException {
        Archivo archivo;
        if ("tramites".equals(tipo)) {
            String mes = validarMes(mesTxt);
            List<FilaTramites> filas = dao.tramitesPorSacramento(idParroquia, mes);
            List<List<Object>> datos = new ArrayList<>();
            int s = 0, c = 0, p = 0, r = 0, x = 0;
            for (FilaTramites f : filas) {
                datos.add(new ArrayList<>(List.of(f.sacramento(), f.solicitudes(), f.confirmadas(), f.pendientes(), f.rechazadas(), f.canceladas())));
                s += f.solicitudes(); c += f.confirmadas(); p += f.pendientes(); r += f.rechazadas(); x += f.canceladas();
            }
            datos.add(new ArrayList<>(List.of("Total", s, c, p, r, x)));
            archivo = new Archivo("tramites-" + (mes == null ? "todos" : mes) + ".xlsx",
                    ExcelReporte.generar("Trámites por sacramento",
                            List.of("Sacramento", "Solicitudes", "Confirmadas", "Pendientes", "Rechazadas", "Canceladas"), datos, true));
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
        } else if ("ingresos".equals(tipo)) {
            String mes = validarMes(mesTxt);
            archivo = new Archivo("ingresos-" + (mes == null ? "todos" : mes) + ".xlsx", excelIngresos(dao.ingresos(idParroquia, mes)));
        } else {
            throw new ReglaNegocioException(400, "Reporte no válido.");
        }
        bitacora.registrar(idAdmin, "REPORTE_EXPORTADO", tipo, ip);
        return archivo;
    }

    /** Libro de ingresos: hoja 1 con el resumen por concepto y método, hoja 2 con cada pago aprobado. */
    private static byte[] excelIngresos(List<FilaPagoIngreso> pagos) throws IOException {
        List<List<Object>> resumen = new ArrayList<>();
        int n = 0;
        BigDecimal yape = BigDecimal.ZERO, plin = BigDecimal.ZERO;
        for (IngresoConcepto f : agruparIngresos(pagos)) {
            resumen.add(new ArrayList<>(List.of(CONCEPTOS.get(f.concepto()), f.pagos(), f.yape(), f.plin(), f.total())));
            n += f.pagos(); yape = yape.add(f.yape()); plin = plin.add(f.plin());
        }
        resumen.add(new ArrayList<>(List.of("Total", n, yape, plin, yape.add(plin))));

        List<List<Object>> detalle = new ArrayList<>();
        for (FilaPagoIngreso p : pagos) {
            List<Object> fila = new ArrayList<>();
            fila.add(p.fecha());
            fila.add(p.feligres());
            fila.add(CONCEPTOS.getOrDefault(p.concepto(), p.concepto()));
            fila.add(METODOS.getOrDefault(p.metodo(), p.metodo()));
            fila.add(p.numeroOperacion()); // texto: conserva los ceros a la izquierda
            fila.add(p.monto());
            fila.add(p.codigoRecibo() == null ? "" : p.codigoRecibo());
            detalle.add(fila);
        }
        return ExcelReporte.generar(List.of(
                new Hoja("Resumen de ingresos", List.of("Concepto", "Pagos", "Yape", "Plin", "Total"), resumen, true),
                new Hoja("Detalle de pagos", List.of("Fecha de pago", "Feligrés", "Concepto", "Método", "N.º de operación", "Monto", "Código de recibo"),
                        detalle, false)));
    }

    /** @return el mes en formato AAAA-MM, o null si no se pidió filtro. */
    static String validarMes(String mesTxt) {
        if (mesTxt == null || mesTxt.isBlank()) return null;
        String mes = mesTxt.trim();
        if (!MES.matcher(mes).matches()) throw new ReglaNegocioException(400, "El mes debe tener el formato AAAA-MM (por ejemplo 2026-10).");
        return mes;
    }
}
