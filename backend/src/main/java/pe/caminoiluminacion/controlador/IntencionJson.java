package pe.caminoiluminacion.controlador;

import pe.caminoiluminacion.modelo.Intencion;
import pe.caminoiluminacion.servicio.IntencionService.IntencionVista;
import pe.caminoiluminacion.util.Json;

import java.util.List;

/** Arma el JSON de las intenciones de misa. Los textos escritos por el feligrés siempre pasan por {@link Json#esc}. */
final class IntencionJson {

    private IntencionJson() { }

    static String intencion(IntencionVista v) {
        Intencion i = v.intencion();
        return "{\"id\":" + i.idIntencion()
                + ",\"fecha\":\"" + Json.esc(String.valueOf(i.fechaMisa())) + "\""
                + ",\"hora\":\"" + Json.esc(i.horaMisa()) + "\""
                + ",\"tipo\":\"" + Json.esc(i.tipo()) + "\""
                + ",\"etiqueta\":\"" + Json.esc(v.etiqueta()) + "\""
                + ",\"texto\":\"" + Json.esc(i.texto()) + "\""
                + ",\"parroquia\":\"" + Json.esc(v.parroquia()) + "\""
                + ",\"solicitante\":\"" + Json.esc(v.solicitante()) + "\""
                + ",\"idPago\":" + i.idPago()
                + ",\"estadoPago\":\"" + Json.esc(v.estadoPago()) + "\""
                + ",\"monto\":" + v.monto().toPlainString()
                + ",\"observacion\":\"" + Json.esc(v.observacion()) + "\"}";
    }

    static String intenciones(List<IntencionVista> lista) {
        StringBuilder sb = new StringBuilder("[");
        for (int k = 0; k < lista.size(); k++) {
            if (k > 0) sb.append(",");
            sb.append(intencion(lista.get(k)));
        }
        return sb.append("]").toString();
    }
}
