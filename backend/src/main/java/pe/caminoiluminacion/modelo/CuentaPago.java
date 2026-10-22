package pe.caminoiluminacion.modelo;

/** Número Yape y Plin de UNA parroquia (a dónde paga el feligrés). Los números son públicos: se muestran para pagar. */
public record CuentaPago(int idParroquia, String yapeNumero, String yapeTitular, String plinNumero, String plinTitular) {

    /** @return el número del método pedido ("yape" o "plin"), o null si no está configurado. */
    public String numeroDe(String metodo) {
        String n = "yape".equals(metodo) ? yapeNumero : "plin".equals(metodo) ? plinNumero : null;
        return n == null || n.isBlank() ? null : n;
    }

    public String titularDe(String metodo) {
        return "yape".equals(metodo) ? yapeTitular : "plin".equals(metodo) ? plinTitular : null;
    }
}
