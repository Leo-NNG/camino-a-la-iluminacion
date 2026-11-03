package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.dao.AvisoDAO;
import pe.caminoiluminacion.dao.HorarioDAO;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.modelo.Aviso;
import pe.caminoiluminacion.modelo.Horario;
import pe.caminoiluminacion.servicio.IntencionServiceTest.FakeHorarios;

import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas al crear o modificar una celebración: hora real (24 h), detalle, duplicados y pertenencia a la parroquia. */
class ContenidoHorariosTest {

    FakeHorarios horarios;
    FakeBitacora bitacoraDao;
    ContenidoService servicio;

    @BeforeEach
    void preparar() {
        horarios = new FakeHorarios();
        bitacoraDao = new FakeBitacora();
        servicio = new ContenidoService(null, horarios, null, new BitacoraService(bitacoraDao));
    }

    private Horario crear(String dia, String tipo, String hora, String detalle) throws SQLException {
        return servicio.guardarHorario(5, 1, "", dia, tipo, hora, detalle, "ip");
    }

    private int estado(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(ReglaNegocioException.class, e).getEstado();
    }

    @Test
    void creaUnaMisaConHoraRealYSinDetalle() throws Exception {
        Horario h = crear("domingo", "misa", "07:00", "");
        assertEquals(LocalTime.of(7, 0), h.getHoraInicio());
        assertNull(h.getDetalle(), "un detalle vacío queda como null");
        assertEquals("7:00 a.m.", h.getHora());
        assertTrue(bitacoraDao.hay("HORARIO_CREADO"));
    }

    @Test
    void aceptaHorasConUnSoloDigitoYLimpiaElDetalle() throws Exception {
        Horario h = crear("sabado", "misa", "7:30", "  vigilia   de   Pascua ");
        assertEquals(LocalTime.of(7, 30), h.getHoraInicio());
        assertEquals("vigilia de Pascua", h.getDetalle());
    }

    @Test
    void rechazaHorasInvalidas() {
        for (String mala : new String[]{"", null, "25:00", "24:00", "10:60", "8am", "19.30", "7:5", "abc", "10:00:00", "-1:00"}) {
            assertEquals(400, estado(() -> crear("lunes", "misa", mala, "")), "hora " + mala);
        }
        assertTrue(horarios.lista.isEmpty());
    }

    @Test
    void validaDiaTipoYDetalle() {
        assertEquals(400, estado(() -> crear("funday", "misa", "07:00", "")));
        assertEquals(400, estado(() -> crear("lunes", "otra", "07:00", "")));
        assertEquals(400, estado(() -> crear("lunes", "misa", "07:00", "d".repeat(121))));
        assertEquals(400, estado(() -> crear("lunes", "misa", "07:00", "con\u0007control")));
    }

    @Test
    void unaActividadNecesitaSuNombre() throws Exception {
        assertEquals(400, estado(() -> crear("martes", "actividad", "17:00", "")));
        assertEquals(400, estado(() -> crear("martes", "actividad", "17:00", "ab")));
        Horario h = crear("martes", "actividad", "17:00", "Catequesis de Primera Comunión");
        assertEquals("Catequesis de Primera Comunión — 5:00 p.m.", h.getHora());
    }

    @Test
    void laMismaCelebracionNoSeRegistraDosVeces() throws Exception {
        crear("domingo", "misa", "09:00", "");
        assertEquals(409, estado(() -> crear("domingo", "misa", "09:00", "")));
        assertDoesNotThrow(() -> crear("domingo", "misa", "11:00", ""), "otra hora es otra misa");
        assertDoesNotThrow(() -> crear("domingo", "misa", "09:00", "vigilia"), "con otro detalle es otra celebración");
        assertDoesNotThrow(() -> crear("domingo", "actividad", "09:00", "Catequesis"), "otro tipo también");
        assertEquals(409, estado(() -> crear("domingo", "actividad", "09:00", "CATEQUESIS")), "sin distinguir mayúsculas");
    }

    @Test
    void modificaSuPropioHorarioSinChocarConSigoMismo() throws Exception {
        Horario h = crear("lunes", "misa", "07:00", "");
        Horario cambiado = servicio.guardarHorario(5, 1, String.valueOf(h.getIdHorario()), "lunes", "misa", "07:00", "solemne", "ip");
        assertEquals("solemne", cambiado.getDetalle());
        assertEquals(1, horarios.lista.size());
        assertTrue(bitacoraDao.hay("HORARIO_MODIFICADO"));
        Horario otro = crear("lunes", "misa", "19:00", "");
        assertEquals(409, estado(() -> servicio.guardarHorario(5, 1, String.valueOf(otro.getIdHorario()), "lunes", "misa", "07:00", "solemne", "ip")),
                "al mover una misa no puede quedar igual a otra");
    }

    @Test
    void noTocaHorariosDeOtraParroquia() throws Exception {
        horarios.agregar(50, 2, "lunes", "06:30", null, "misa");
        assertEquals(404, estado(() -> servicio.guardarHorario(5, 1, "50", "martes", "misa", "10:00", "", "ip")));
        assertEquals("6:30 a.m.", horarios.buscarPorId(50).get().getHora());
        assertEquals(400, estado(() -> servicio.guardarHorario(5, 1, "abc", "martes", "misa", "10:00", "", "ip")));
    }
}
