package pe.caminoiluminacion.modelo;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class HorarioTest {

    @Test
    void formateaLaHoraEnDoceHorasConAmPm() {
        assertEquals("7:00 a.m.", Horario.formatear(LocalTime.of(7, 0)));
        assertEquals("7:30 p.m.", Horario.formatear(LocalTime.of(19, 30)));
        assertEquals("12:00 p.m.", Horario.formatear(LocalTime.NOON), "el mediodía es p.m.");
        assertEquals("12:15 a.m.", Horario.formatear(LocalTime.of(0, 15)), "la medianoche es 12 a.m.");
        assertEquals("11:59 p.m.", Horario.formatear(LocalTime.of(23, 59)));
        assertEquals("1:05 a.m.", Horario.formatear(LocalTime.of(1, 5)));
        assertEquals("", Horario.formatear(null));
    }

    @Test
    void laMisaSeLeeConSuDetalleEntreParentesis() {
        assertEquals("5:00 p.m.", new Horario(1, "sabado", LocalTime.of(17, 0), null, "misa").getHora());
        assertEquals("5:00 p.m.", new Horario(1, "sabado", LocalTime.of(17, 0), "  ", "misa").getHora());
        assertEquals("5:00 p.m. (vigilia)", new Horario(1, "sabado", LocalTime.of(17, 0), "vigilia", "misa").getHora());
    }

    @Test
    void laActividadSeLeeConSuNombreAlPrincipio() {
        assertEquals("Catequesis de Primera Comunión — 5:00 p.m.",
                new Horario(1, "martes", LocalTime.of(17, 0), "Catequesis de Primera Comunión", "actividad").getHora());
    }
}
