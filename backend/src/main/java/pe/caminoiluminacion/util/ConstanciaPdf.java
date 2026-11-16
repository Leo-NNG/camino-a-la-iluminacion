package pe.caminoiluminacion.util;

import pe.caminoiluminacion.servicio.VerificacionService.Constancia;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static pe.caminoiluminacion.util.ReciboPdf.ANCHO;
import static pe.caminoiluminacion.util.ReciboPdf.LIMA;
import static pe.caminoiluminacion.util.ReciboPdf.MARGEN;
import static pe.caminoiluminacion.util.ReciboPdf.documento;
import static pe.caminoiluminacion.util.ReciboPdf.fila;
import static pe.caminoiluminacion.util.ReciboPdf.linea;
import static pe.caminoiluminacion.util.ReciboPdf.partir;
import static pe.caminoiluminacion.util.ReciboPdf.texto;

/**
 * Constancia de reserva de un sacramento en PDF de una página (A4), con el mismo escritor mínimo del recibo (sin librerías
 * externas, Helvetica estándar). Todo texto que viene de la persona pasa por el mismo saneamiento que el recibo.
 */
public final class ConstanciaPdf {

    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ConstanciaPdf() { }

    /** @param urlVerificacion dirección de la página pública donde se comprueba el código */
    public static byte[] generar(Constancia k, String urlVerificacion, Clock reloj) {
        StringBuilder c = new StringBuilder();

        c.append("0.13 0.19 0.33 rg\n0 770 ").append(ANCHO).append(" 72 re f\n");
        texto(c, "F2", 20, MARGEN, 803, "1 1 1", "CONSTANCIA DE RESERVA");
        texto(c, "F1", 10, MARGEN, 785, "0.85 0.89 0.95", "Camino a la Iluminaci\u00F3n");

        double y = 735;
        texto(c, "F2", 11, MARGEN, y, "0.35 0.35 0.35", "Constancia " + k.codigo());
        y -= 28;
        for (String l : partir(k.parroquia(), 15, ANCHO - 2 * MARGEN, true)) { texto(c, "F2", 15, MARGEN, y, "0.1 0.1 0.1", l); y -= 19; }
        for (String l : partir(k.direccion(), 10, ANCHO - 2 * MARGEN, false)) { texto(c, "F1", 10, MARGEN, y, "0.4 0.4 0.4", l); y -= 13; }
        y -= 8;
        linea(c, MARGEN, y, ANCHO - MARGEN);
        y -= 28;

        String fecha = k.fecha() == null ? "-" : k.fecha().toLocalDate().format(FECHA_LARGA);
        y = fila(c, y, "SACRAMENTO", k.sacramento());
        y = fila(c, y, "SOLICITANTE", k.solicitante());
        y = fila(c, y, "FECHA CONFIRMADA", fecha.isEmpty() ? "-" : Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1));
        y = fila(c, y, "DOCUMENTOS", k.documentos() == null ? "-" : k.documentos());

        // Caja de estado
        y -= 6;
        c.append("0.95 0.99 0.96 rg\n0.75 0.88 0.8 RG\n").append(MARGEN).append(' ').append(y - 40).append(' ')
         .append(ANCHO - 2 * MARGEN).append(" 48 re B\n");
        texto(c, "F2", 14, MARGEN + 16, y - 22, "0.1 0.5 0.25", "FECHA CONFIRMADA POR LA PARROQUIA");

        // C\u00F3digo de verificaci\u00F3n
        double yc = y - 40 - 30;
        texto(c, "F1", 8.5, MARGEN, yc, "0.45 0.45 0.45", "C\u00D3DIGO DE VERIFICACI\u00D3N");
        texto(c, "F2", 15, MARGEN, yc - 20, "0.1 0.1 0.1", k.codigo());
        double yu = yc - 38;
        texto(c, "F1", 9, MARGEN, yu, "0.4 0.4 0.4", "Comprueba que esta constancia es aut\u00E9ntica y sigue vigente en:");
        for (String l : partir(urlVerificacion == null ? "" : urlVerificacion, 9, ANCHO - 2 * MARGEN, false)) { yu -= 12; texto(c, "F1", 9, MARGEN, yu, "0.13 0.19 0.55", l); }

        // Pie
        linea(c, MARGEN, 95, ANCHO - MARGEN);
        int py = 80;
        List<String> aviso = partir("Esta constancia indica que la parroquia confirm\u00F3 la fecha de la solicitud. Deja de ser v\u00E1lida si la solicitud se "
                + "reprograma o se cancela: verifica su vigencia con el c\u00F3digo. No reemplaza el registro sacramental de la parroquia.", 8, ANCHO - 2 * MARGEN, false);
        for (String l : aviso) { texto(c, "F1", 8, MARGEN, py, "0.45 0.45 0.45", l); py -= 11; }
        texto(c, "F1", 8, MARGEN, py - 4, "0.45 0.45 0.45", "Emitida el " + FECHA_HORA.format(reloj.instant().atZone(LIMA)));

        return documento(c.toString(), "Constancia " + k.codigo());
    }
}
