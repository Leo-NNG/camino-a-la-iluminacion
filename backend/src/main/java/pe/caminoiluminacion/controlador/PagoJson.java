package pe.caminoiluminacion.controlador;

import pe.caminoiluminacion.modelo.CuentaPago;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.servicio.PagoService.PagoVista;
import pe.caminoiluminacion.util.Json;

import java.util.List;

/** Arma el JSON de los pagos. NUNCA incluye el celular cifrado, la ruta del archivo ni el hash del comprobante. */
final class PagoJson {

    private PagoJson() { }

    static String pago(PagoVista v) {
        Pago p = v.pago();
        return "{\"id\":" + p.idPago()
                + ",\"concepto\":\"" + Json.esc(p.concepto()) + "\""
                + ",\"detalle\":\"" + Json.esc(v.detalle()) + "\""
                + ",\"monto\":" + p.monto().toPlainString()
                + ",\"metodo\":\"" + Json.esc(p.metodo()) + "\""
                + ",\"operacion\":\"" + Json.esc(p.numeroOperacion()) + "\""
                + ",\"celular\":\"" + Json.esc(v.celular()) + "\""
                + ",\"parroquia\":\"" + Json.esc(v.parroquia()) + "\""
                + ",\"feligres\":\"" + Json.esc(v.feligres()) + "\""
                + ",\"idReserva\":" + (p.idReserva() == null ? "null" : p.idReserva())
                + ",\"estado\":\"" + Json.esc(p.estado()) + "\""
                + ",\"observacion\":\"" + Json.esc(p.observacion()) + "\""
                + ",\"fecha\":\"" + Json.fecha(p.creadoEn()) + "\"}";
    }

    static String pagos(List<PagoVista> lista) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lista.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(pago(lista.get(i)));
        }
        return sb.append("]").toString();
    }

    /** Los números de cobro son públicos (el feligrés los necesita para pagar). */
    static String cuenta(CuentaPago c) {
        if (c == null) return "{\"yape\":null,\"plin\":null}";
        return "{\"yape\":" + medio(c.yapeNumero(), c.yapeTitular()) + ",\"plin\":" + medio(c.plinNumero(), c.plinTitular()) + "}";
    }

    private static String medio(String numero, String titular) {
        if (numero == null || numero.isBlank()) return "null";
        return "{\"numero\":\"" + Json.esc(numero) + "\",\"titular\":\"" + Json.esc(titular) + "\"}";
    }
}
