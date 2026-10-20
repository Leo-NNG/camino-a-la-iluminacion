package pe.caminoiluminacion.controlador;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corre los escenarios de integración ligera: servlets reales + servicios reales + DAO falsos en memoria
 * (sin base de datos ni contenedor). Cada escenario es una secuencia de comprobaciones que comparten estado,
 * por eso se ejecutan completos y, si alguna falla, el mensaje lista cuáles.
 */
class EscenariosTest {

    private static void sinFallas(List<String> fallas) {
        assertTrue(fallas.isEmpty(), "Comprobaciones fallidas:\n - " + String.join("\n - ", fallas));
    }

    @Test
    @DisplayName("Sesión, DNI, roles, reservas, panel de reservas, bitácora y filtro del panel")
    void accesoReservasYPanel() throws Exception {
        sinFallas(Pruebas.ejecutar());
    }

    @Test
    @DisplayName("Documentos: subida segura, descarga con permisos, validación y trazabilidad")
    void documentos() throws Exception {
        sinFallas(PruebasDocumentos.ejecutar());
    }

    @Test
    @DisplayName("Parroquias, horarios, avisos y sacramentos: público y panel por parroquia")
    void contenido() throws Exception {
        sinFallas(PruebasContenido.ejecutar());
    }
}
