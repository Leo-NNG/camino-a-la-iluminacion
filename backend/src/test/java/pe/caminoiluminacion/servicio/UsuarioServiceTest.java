package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.caminoiluminacion.controlador.Pruebas;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.controlador.Pruebas.FakeUsuarios;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.util.LimiteIntentos;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioServiceTest {

    private long ahora;
    private FakeUsuarios usuarios;
    private FakeBitacora bitacoraDao;
    private UsuarioService servicio;

    @BeforeEach
    void preparar() throws SQLException {
        ahora = 0;
        usuarios = new FakeUsuarios();
        usuarios.insertar(Pruebas.usuario("70000001", "ana@x.pe", "Clave123", "feligres", null));
        bitacoraDao = new FakeBitacora();
        servicio = new UsuarioService(usuarios, new LimiteIntentos(3, 60_000, 120_000, () -> ahora), new BitacoraService(bitacoraDao));
    }

    private int estado(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(ReglaNegocioException.class, e).getEstado();
    }

    @Test
    void autenticaConDniYClaveCorrectos() throws SQLException {
        Usuario u = servicio.autenticar(" 70000001 ", "Clave123", "1.1.1.1");
        assertEquals("ana@x.pe", u.getCorreo());
        assertTrue(bitacoraDao.hay("LOGIN_OK"));
    }

    @Test
    void elDniDebeTenerOchoDigitos() {
        for (String malo : new String[]{"1234567", "123456789", "abcdefgh", "7000000a", "", null, "' OR '1'='1"}) {
            assertEquals(400, estado(() -> servicio.autenticar(malo, "x", "ip")), String.valueOf(malo));
        }
    }

    @Test
    void claveIncorrectaYDniInexistenteDanElMismoError() {
        ReglaNegocioException a = assertThrows(ReglaNegocioException.class, () -> servicio.autenticar("70000001", "mala", "ip"));
        ReglaNegocioException b = assertThrows(ReglaNegocioException.class, () -> servicio.autenticar("99999999", "Clave123", "ip"));
        assertEquals(401, a.getEstado());
        assertEquals(a.getMessage(), b.getMessage());
        assertEquals(2, bitacoraDao.cuenta("LOGIN_FALLIDO"));
    }

    @Test
    void trasVariosFallosBloqueaIncluidoConLaClaveCorrectaYLuegoLibera() throws SQLException {
        for (int i = 0; i < 3; i++) estado(() -> servicio.autenticar("70000001", "mala", "ip"));
        assertEquals(429, estado(() -> servicio.autenticar("70000001", "Clave123", "ip")));
        assertEquals(401, estado(() -> servicio.autenticar("70000001", "mala", "otra-ip")) , "otra IP no está bloqueada");
        ahora = 121_000;
        assertNotNull(servicio.autenticar("70000001", "Clave123", "ip"));
    }

    @Test
    void registraSoloFeligresesYGuardaLaClaveConHash() throws SQLException {
        Usuario u = servicio.registrarFeligres("12345678", "María José", "Fernández", "Maria@Ej.com", "987654321", "secreto1", "Chiclayo", "ip");
        assertEquals("feligres", u.getRol());
        assertNull(u.getIdParroquia());
        assertEquals("maria@ej.com", u.getCorreo());
        assertTrue(u.getContrasenaHash().startsWith("pbkdf2$"));
        assertTrue(bitacoraDao.hay("USUARIO_REGISTRADO"));
    }

    @Test
    void validaCadaCampoDelRegistro() {
        String[][] malos = {{"123", "Ana", "Pérez", "a@b.co", "999", "secreto1"}, {"12345678", "<b>x</b>", "Pérez", "a@b.co", "999", "secreto1"},
                {"12345678", "Ana", "", "a@b.co", "999", "secreto1"}, {"12345678", "Ana", "Pérez", "no-es-correo", "999", "secreto1"},
                {"12345678", "Ana", "Pérez", "a@b.co", "12ab", "secreto1"}, {"12345678", "Ana", "Pérez", "a@b.co", "999", "123"}};
        for (String[] m : malos) {
            assertEquals(400, estado(() -> servicio.registrarFeligres(m[0], m[1], m[2], m[3], m[4], m[5], null, "ip")), String.join("|", m));
        }
        assertEquals(1, usuarios.lista.size(), "ninguno se guardó");
    }

    @Test
    void dniOCorreoRepetidosDanConflictoConMensajeDistinto() throws SQLException {
        servicio.registrarFeligres("12345678", "Ana", "Pérez", "a@b.co", null, "secreto1", null, "ip");
        ReglaNegocioException dni = assertThrows(ReglaNegocioException.class, () -> servicio.registrarFeligres("12345678", "Ana", "Pérez", "otro@b.co", null, "secreto1", null, "ip"));
        ReglaNegocioException correo = assertThrows(ReglaNegocioException.class, () -> servicio.registrarFeligres("22222222", "Ana", "Pérez", "a@b.co", null, "secreto1", null, "ip"));
        assertEquals(409, dni.getEstado());
        assertEquals(409, correo.getEstado());
        assertTrue(dni.getMessage().contains("DNI"));
        assertTrue(correo.getMessage().contains("correo"));
    }
}
