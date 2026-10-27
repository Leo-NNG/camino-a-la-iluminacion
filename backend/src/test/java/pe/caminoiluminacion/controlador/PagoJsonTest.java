package pe.caminoiluminacion.controlador;

import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.modelo.CuentaPago;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.seguridad.Cripto;
import pe.caminoiluminacion.servicio.PagoService.PagoVista;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PagoJsonTest {

    private static Pago pago() {
        return new Pago(7, 1, 1, 3, "arancel", new BigDecimal("50.00"), "yape", Cripto.cifrar("987654321"), "123456",
                "0123456789abcdef0123456789abcdef.png", "a".repeat(64), "pendiente", null, Timestamp.valueOf("2026-10-21 10:00:00"), null);
    }

    @Test
    void elJsonDeUnPagoNoFiltraElCelularCifradoLaRutaNiElHash() {
        String json = PagoJson.pago(new PagoVista(pago(), "Parroquia San José", "Ana Torres", "******321", "Arancel de bautizo"));
        assertFalse(json.contains("v1:"), "no debe salir el celular cifrado");
        assertFalse(json.contains("0123456789abcdef"), "no debe salir la ruta del archivo");
        assertFalse(json.contains("aaaaaaaa"), "no debe salir el hash del comprobante");
        assertTrue(json.contains("\"celular\":\"******321\""));
        assertTrue(json.contains("\"monto\":50.00"));
        assertTrue(json.contains("\"fecha\":\"2026-10-21\""));
    }

    @Test
    void escapaLosTextosQueVienenDelUsuario() {
        String json = PagoJson.pago(new PagoVista(pago(), "San \"José\"", "<img onerror=x>", "***", "d"));
        assertTrue(json.contains("San \\\"José\\\""));
    }

    @Test
    void laCuentaMuestraSoloLosMediosConfigurados() {
        assertEquals("{\"yape\":{\"numero\":\"911111111\",\"titular\":\"Parroquia\"},\"plin\":null}",
                PagoJson.cuenta(new CuentaPago(1, "911111111", "Parroquia", null, null, null)));
        assertEquals("{\"yape\":null,\"plin\":null}", PagoJson.cuenta(null));
        assertEquals("[]", PagoJson.pagos(List.of()));
    }
}
