package pe.caminoiluminacion.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.util.AlmacenArchivos;
import pe.caminoiluminacion.util.Claves;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/** "Mi cuenta" (Ley 29733): datos, contraseña, política, copia de datos y eliminación de la cuenta. */
class CuentaServiceTest {

    @TempDir Path carpeta;
    CuentaEntorno e;
    int ana;       // feligrés con datos
    int admin;     // personal de la parroquia 1
    int otro;      // otro feligrés (sus datos no deben aparecer en la copia de Ana)

    @BeforeEach
    void preparar() throws SQLException {
        e = new CuentaEntorno(carpeta);
        ana = e.agregar("70000001", "ana@x.pe", "Clave123", "feligres", null);
        admin = e.agregar("70000000", "adm@x.pe", "Admin123", "administrador", 1);
        otro = e.agregar("70000002", "otro@x.pe", "Clave456", "feligres", null);
    }

    private int estado(org.junit.jupiter.api.function.Executable x) {
        return assertThrows(ReglaNegocioException.class, x).getEstado();
    }

    // ------------------------------------------------------------------ ver

    @Test
    void elPerfilEnmascaraElDniYLaCuentaNuevaNoHaAceptadoLaPolitica() throws SQLException {
        CuentaService.Perfil p = e.servicio.perfil(ana);
        assertEquals("*****001", p.dniEnmascarado());
        assertEquals("ana@x.pe", p.correo());
        assertFalse(p.politicaAceptada(), "sin versión guardada = aún no la acepta");
        assertEquals(PoliticaPrivacidad.VERSION, p.politicaVigente());
    }

    @Test
    void aceptarLaPoliticaGuardaLaVersionVigenteYQuedaEnBitacora() throws SQLException {
        e.servicio.aceptarPolitica(ana, "1.1.1.1");
        assertTrue(e.servicio.perfil(ana).politicaAceptada());
        assertEquals(PoliticaPrivacidad.VERSION, e.usuarios.buscarPorId(ana).get().getPoliticaVersion());
        assertTrue(e.bitacoraDao.hay("POLITICA_ACEPTADA"));
    }

    @Test
    void unaVersionAnteriorDeLaPoliticaNoCuentaComoAceptada() throws SQLException {
        e.usuarios.buscarPorId(ana).get().setPoliticaVersion("2020-01");
        assertFalse(e.servicio.perfil(ana).politicaAceptada());
    }

    @Test
    void unaCuentaInexistenteOEliminadaNoTienePerfil() throws SQLException {
        assertEquals(404, estado(() -> e.servicio.perfil(999)));
        e.usuarios.buscarPorId(ana).get().setEliminadoEn(new java.sql.Timestamp(1));
        assertEquals(404, estado(() -> e.servicio.perfil(ana)));
        assertEquals(404, estado(() -> e.servicio.aceptarPolitica(ana, "ip")), "tampoco se puede operar sobre ella");
    }

    // ------------------------------------------------------------------ contacto

    @Test
    void actualizaCorreoYTelefonoNormalizados() throws SQLException {
        e.servicio.actualizarContacto(ana, "  Nuevo@X.Pe ", " 987654321 ", "ip");
        Usuario u = e.usuarios.buscarPorId(ana).get();
        assertEquals("nuevo@x.pe", u.getCorreo());
        assertEquals("987654321", u.getTelefono());
        assertTrue(e.bitacoraDao.hay("CUENTA_CONTACTO_ACTUALIZADO"));
        e.servicio.actualizarContacto(ana, "nuevo@x.pe", "", "ip");
        assertNull(e.usuarios.buscarPorId(ana).get().getTelefono(), "teléfono vacío = sin teléfono");
    }

    @Test
    void rechazaCorreoOTelefonoInvalidosSinCambiarNada() throws SQLException {
        assertEquals(400, estado(() -> e.servicio.actualizarContacto(ana, "no-es-correo", "999888777", "ip")));
        assertEquals(400, estado(() -> e.servicio.actualizarContacto(ana, "ok@x.pe", "12ab", "ip")));
        assertEquals(400, estado(() -> e.servicio.actualizarContacto(ana, null, null, "ip")));
        assertEquals("ana@x.pe", e.usuarios.buscarPorId(ana).get().getCorreo());
    }

    @Test
    void unCorreoYaRegistradoDaConflicto() {
        e.cuentas.correoRepetido = true;
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> e.servicio.actualizarContacto(ana, "otro@x.pe", null, "ip"));
        assertEquals(409, ex.getEstado());
        assertTrue(ex.getMessage().contains("correo"));
    }

    // ------------------------------------------------------------------ contraseña

    @Test
    void cambiaLaContrasenaSiLaActualEsCorrecta() throws SQLException {
        e.servicio.cambiarContrasena(ana, "Clave123", "NuevaClave9", "ip");
        String hash = e.usuarios.buscarPorId(ana).get().getContrasenaHash();
        assertTrue(Claves.verificar("NuevaClave9", hash));
        assertFalse(Claves.verificar("Clave123", hash));
        assertTrue(e.bitacoraDao.hay("CUENTA_CLAVE_CAMBIADA"));
    }

    @Test
    void conLaContrasenaActualIncorrectaNoCambiaNadaYQuedaEnBitacora() throws SQLException {
        String antes = e.usuarios.buscarPorId(ana).get().getContrasenaHash();
        assertEquals(403, estado(() -> e.servicio.cambiarContrasena(ana, "mala", "NuevaClave9", "ip")));
        assertEquals(antes, e.usuarios.buscarPorId(ana).get().getContrasenaHash());
        assertTrue(e.bitacoraDao.hay("CUENTA_CLAVE_FALLIDA"));
    }

    @Test
    void laNuevaContrasenaDebeSerValidaYDistinta() throws SQLException {
        assertEquals(400, estado(() -> e.servicio.cambiarContrasena(ana, "Clave123", "123", "ip")));
        assertEquals(400, estado(() -> e.servicio.cambiarContrasena(ana, "Clave123", null, "ip")));
        assertEquals(400, estado(() -> e.servicio.cambiarContrasena(ana, "Clave123", "Clave123", "ip")));
        assertTrue(Claves.verificar("Clave123", e.usuarios.buscarPorId(ana).get().getContrasenaHash()));
    }

    @Test
    void variosFallosBloqueanAunConLaContrasenaCorrectaYLuegoLiberan() throws SQLException {
        for (int i = 0; i < 3; i++) estado(() -> e.servicio.cambiarContrasena(ana, "mala", "NuevaClave9", "ip"));
        assertEquals(429, estado(() -> e.servicio.cambiarContrasena(ana, "Clave123", "NuevaClave9", "ip")));
        assertEquals(429, estado(() -> e.servicio.eliminarCuenta(ana, "Clave123", "ip")), "el bloqueo cubre también la eliminación");
        e.ahoraMs[0] = 121_000;
        e.servicio.cambiarContrasena(ana, "Clave123", "NuevaClave9", "ip");
    }

    @Test
    void laBitacoraNoGuardaContrasenas() throws SQLException {
        estado(() -> e.servicio.cambiarContrasena(ana, "SecretoMalo1", "NuevaClave9", "ip"));
        e.servicio.cambiarContrasena(ana, "Clave123", "NuevaClave9", "ip");
        assertTrue(e.bitacoraDao.filas.stream().noneMatch(f -> String.join(" ", f).matches(".*(SecretoMalo1|Clave123|NuevaClave9).*")));
    }

    // ------------------------------------------------------------------ copia de datos

    @Test
    void laCopiaDeDatosTraeSoloLosDatosDeLaPersona() throws SQLException {
        e.agregarReserva(ana, "Bebé Uno", "nota de ana");
        e.agregarReserva(otro, "Ajeno", "nota ajena");
        e.agregarDocumento(ana, "partida.pdf", "a".repeat(32) + ".pdf");
        e.agregarDocumento(otro, "ajeno.pdf", "b".repeat(32) + ".pdf");
        e.agregarPago(ana, "987654321", "c".repeat(32) + ".jpg", "sha-ana");
        e.agregarPago(otro, "999000111", "d".repeat(32) + ".jpg", "sha-otro");
        e.agregarAviso(ana, "Tu pago fue verificado");
        e.agregarAviso(otro, "Aviso ajeno");

        CuentaService.DatosPersonales d = e.servicio.exportarDatos(ana, "ip");
        assertEquals(1, d.reservas().size());
        assertEquals("Bebé Uno", d.reservas().get(0).getNombreSolicitante());
        assertEquals(1, d.documentos().size());
        assertEquals(1, d.pagos().size());
        assertEquals(1, d.notificaciones().size());
        assertEquals("*****001", d.perfil().dniEnmascarado());
        assertEquals("Parroquia San José", d.parroquias().get(1));
        assertTrue(e.bitacoraDao.hay("DATOS_DESCARGADOS"));
    }

    // ------------------------------------------------------------------ eliminar la cuenta

    @Test
    void conLaContrasenaIncorrectaNoSeEliminaNada() throws SQLException {
        assertEquals(403, estado(() -> e.servicio.eliminarCuenta(ana, "mala", "ip")));
        assertEquals(0, e.cuentas.llamadasAnonimizar, "ni siquiera se intentó");
        assertFalse(e.bitacoraDao.hay("CUENTA_ELIMINADA"));
    }

    @Test
    void elPersonalNoPuedeEliminarSuCuentaDesdeAqui() throws SQLException {
        assertEquals(403, estado(() -> e.servicio.eliminarCuenta(admin, "Admin123", "ip")));
        assertEquals(0, e.cuentas.llamadasAnonimizar);
    }

    @Test
    void conBloqueosSeDevuelve409ConLosMotivosYNoSeBorraNingunArchivo() throws Exception {
        String nombre = e.almacen.guardar("%PDF-1.4 x".getBytes(), AlmacenArchivos.Tipo.PDF);
        e.cuentas.archivos.add(nombre);
        e.cuentas.bloqueos.add("tienes pagos pendientes de verificación");
        e.cuentas.bloqueos.add("tienes intenciones de misa vigentes");
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> e.servicio.eliminarCuenta(ana, "Clave123", "ip"));
        assertEquals(409, ex.getEstado());
        assertTrue(ex.getMessage().contains("pagos pendientes") && ex.getMessage().contains("intenciones de misa"));
        assertNotNull(e.almacen.resolver(nombre), "el archivo sigue en el disco");
        assertFalse(e.usuarios.buscarPorId(ana).get().estaEliminado());
        assertFalse(e.bitacoraDao.hay("CUENTA_ELIMINADA"));
    }

    @Test
    void eliminarAnonimizaBorraLosArchivosYRegistraSinIp() throws Exception {
        String doc = e.almacen.guardar("%PDF-1.4 x".getBytes(), AlmacenArchivos.Tipo.PDF);
        String voucher = e.almacen.guardar(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}, AlmacenArchivos.Tipo.JPEG);
        e.cuentas.archivos.add(doc);
        e.cuentas.archivos.add(voucher);

        e.servicio.eliminarCuenta(ana, "Clave123", "9.9.9.9");

        Usuario u = e.usuarios.buscarPorId(ana).get();
        assertTrue(u.estaEliminado());
        assertEquals("", u.getDni());
        assertEquals(Usuario.CUENTA_ELIMINADA, u.nombreParaMostrar());
        assertNull(e.almacen.resolver(doc), "el documento se borró del disco");
        assertNull(e.almacen.resolver(voucher), "el comprobante se borró del disco");
        assertEquals(CuentaEntorno.HOY, e.cuentas.ultimaFecha.toLocalDate(), "'hoy' sale del reloj inyectado (hora de Perú)");
        String fila = e.bitacoraDao.ultima("CUENTA_ELIMINADA");
        assertNotNull(fila);
        assertFalse(fila.contains("9.9.9.9"), "la bitácora de la eliminación no guarda la IP");
        assertEquals(404, estado(() -> e.servicio.eliminarCuenta(ana, "Clave123", "ip")), "no se puede eliminar dos veces");
        assertFalse(Claves.verificar("Clave123", u.getContrasenaHash()), "nadie puede volver a entrar a esa cuenta");
    }

    @Test
    void sinCuentaNoHayNadaQueEliminar() {
        assertEquals(404, estado(() -> e.servicio.eliminarCuenta(999, "x", "ip")));
    }
}
