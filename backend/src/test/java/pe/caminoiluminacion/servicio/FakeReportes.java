package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.ReporteDAO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** ReporteDAO en memoria para las pruebas: devuelve datos fijos y recuerda con qué argumentos lo llamaron. */
public class FakeReportes implements ReporteDAO {

    public String mesPedido = "sin llamar";
    public Integer parroquiaPedida;
    public LocalDate hoyPedido, inicioMesPedido;
    public int diasPedidos;

    public List<FilaTramites> tramites = List.of(
            new FilaTramites("Bautizo", 6, 3, 1, 1, 1),
            new FilaTramites("Matrimonio", 2, 1, 1, 0, 0),
            new FilaTramites("Confirmación", 0, 0, 0, 0, 0));
    public List<FilaPagoIngreso> pagos = new ArrayList<>(List.of(
            new FilaPagoIngreso("2026-10-03", "Ana Torres", "arancel", "yape", "000123", new BigDecimal("80.00"), "REC-AAAAA-BBBBB"),
            new FilaPagoIngreso("2026-10-04", "Luis Paz", "arancel", "plin", "456789", new BigDecimal("80.00"), null),
            new FilaPagoIngreso("2026-10-05", "Ana Torres", "donacion", "yape", "777777", new BigDecimal("15.50"), null)));
    public Resumen resumen = new Resumen(3, 2, 4, 1, 5, 9, 2, 6, new BigDecimal("175.5"));

    public List<FilaTramites> tramitesPorSacramento(int idParroquia, String mes) {
        parroquiaPedida = idParroquia;
        mesPedido = mes;
        return tramites;
    }

    public List<FilaDocumento> documentosPendientes(int idParroquia) {
        parroquiaPedida = idParroquia;
        return List.of(new FilaDocumento("Ana Torres", "dni.png", "Bautizo", "Bebé", "2026-10-05", 3));
    }

    public List<FilaAgenda> agenda(int idParroquia) {
        parroquiaPedida = idParroquia;
        return List.of(new FilaAgenda("2026-11-01", "Bautizo", "Bebé", null));
    }

    public List<FilaPagoIngreso> ingresos(int idParroquia, String mes) {
        parroquiaPedida = idParroquia;
        mesPedido = mes;
        return pagos;
    }

    public Resumen resumen(int idParroquia, LocalDate hoy, int diasProximos, LocalDate inicioMes) {
        parroquiaPedida = idParroquia;
        hoyPedido = hoy;
        diasPedidos = diasProximos;
        inicioMesPedido = inicioMes;
        return resumen;
    }
}
