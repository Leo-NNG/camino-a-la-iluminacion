package pe.caminoiluminacion.modelo;

import java.math.BigDecimal;

/** Monto oficial de un sacramento en una parroquia. */
public record Arancel(int idParroquia, int idSacramento, String sacramento, BigDecimal monto) { }
