package pe.caminoiluminacion.modelo;

/**
 * Datos de cobro de UNA parroquia: número Yape y Plin (a dónde paga el feligrés; son públicos) y la ofrenda de una
 * intención de misa (null = no se piden en línea).
 */
public record CuentaPago(int idParroquia, String yapeNumero, String yapeTitular, String plinNumero, String plinTitular,
                         java.math.BigDecimal ofrendaIntencion) {

    /** @return el número del método pedido ("yape" o "plin"), o null si no está configurado. */
    public String numeroDe(String metodo) {
        String n = "yape".equals(metodo) ? yapeNumero : "plin".equals(metodo) ? plinNumero : null;
        return n == null || n.isBlank() ? null : n;
    }

    public String titularDe(String metodo) {
        return "yape".equals(metodo) ? yapeTitular : "plin".equals(metodo) ? plinTitular : null;
    }
}
