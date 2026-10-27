package pe.caminoiluminacion.modelo;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Un pago por Yape o Plin: arancel de una reserva, donación o diezmo. El comprobante (voucher) se guarda en el
 * almacén privado y el personal de la parroquia lo verifica. {@code celularOrigen} viaja CIFRADO.
 */
public record Pago(Integer idPago, int idUsuario, int idParroquia, Integer idReserva, String concepto,
                   BigDecimal monto, String metodo, String celularOrigen, String numeroOperacion,
                   String rutaVoucher, String voucherSha256, String estado, String observacion,
                   Timestamp creadoEn, Timestamp revisadoEn) {

    public static final String ARANCEL = "arancel";
    public static final String DONACION = "donacion";
    public static final String DIEZMO = "diezmo";
    public static final String INTENCION = "intencion";

    public static final String PENDIENTE = "pendiente";
    public static final String APROBADO = "aprobado";
    public static final String RECHAZADO = "rechazado";

    public Pago conId(int id) {
        return new Pago(id, idUsuario, idParroquia, idReserva, concepto, monto, metodo, celularOrigen,
                numeroOperacion, rutaVoucher, voucherSha256, estado, observacion, creadoEn, revisadoEn);
    }
}
