package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.impl.ArancelDAOImpl;
import pe.caminoiluminacion.dao.impl.AvisoDAOImpl;
import pe.caminoiluminacion.dao.impl.BitacoraDAOImpl;
import pe.caminoiluminacion.dao.impl.CuentaPagoDAOImpl;
import pe.caminoiluminacion.dao.impl.DocumentoDAOImpl;
import pe.caminoiluminacion.dao.impl.HorarioDAOImpl;
import pe.caminoiluminacion.dao.impl.IntencionDAOImpl;
import pe.caminoiluminacion.dao.impl.NotificacionDAOImpl;
import pe.caminoiluminacion.dao.impl.PagoDAOImpl;
import pe.caminoiluminacion.dao.impl.ParroquiaDAOImpl;
import pe.caminoiluminacion.dao.impl.ReporteDAOImpl;
import pe.caminoiluminacion.dao.impl.ReservaDAOImpl;
import pe.caminoiluminacion.dao.impl.SacramentoDAOImpl;
import pe.caminoiluminacion.dao.impl.UsuarioDAOImpl;
import pe.caminoiluminacion.util.AlmacenArchivos;
import pe.caminoiluminacion.util.LimiteIntentos;

/**
 * Punto único donde se ensamblan los servicios con los DAO reales (JDBC). Los servlets lo usan en su
 * constructor sin argumentos; las pruebas arman los servicios con DAO falsos y no pasan por aquí.
 * Crear los DAO no abre ninguna conexión: la base de datos solo se toca al ejecutar una operación.
 */
public final class Servicios {

    private static final BitacoraService BITACORA = new BitacoraService(new BitacoraDAOImpl());
    private static final UsuarioDAOImpl USUARIOS = new UsuarioDAOImpl();
    private static final ReservaDAOImpl RESERVAS = new ReservaDAOImpl();
    private static final ParroquiaDAOImpl PARROQUIAS = new ParroquiaDAOImpl();
    private static final SacramentoDAOImpl SACRAMENTOS = new SacramentoDAOImpl();

    private static final UsuarioService USUARIO_SERVICE = new UsuarioService(USUARIOS, new LimiteIntentos(), BITACORA);
    private static final ReservaService RESERVA_SERVICE = new ReservaService(RESERVAS, PARROQUIAS, SACRAMENTOS, USUARIOS, BITACORA);
    private static final DocumentoService DOCUMENTO_SERVICE = new DocumentoService(
            new DocumentoDAOImpl(), RESERVAS, PARROQUIAS, USUARIOS, AlmacenArchivos.porDefecto(), BITACORA);
    private static final ContenidoService CONTENIDO_SERVICE = new ContenidoService(
            new AvisoDAOImpl(), new HorarioDAOImpl(), SACRAMENTOS, BITACORA);

    private static final ReporteService REPORTE_SERVICE = new ReporteService(new ReporteDAOImpl(), BITACORA);

    private static final NotificacionDAOImpl NOTIFICACIONES = new NotificacionDAOImpl();
    private static final PagoDAOImpl PAGOS = new PagoDAOImpl();
    private static final CuentaPagoDAOImpl CUENTAS = new CuentaPagoDAOImpl();
    private static final AlmacenArchivos ALMACEN_PAGOS = AlmacenArchivos.porDefecto();
    private static final PagoService PAGO_SERVICE = new PagoService(PAGOS, new ArancelDAOImpl(), CUENTAS, NOTIFICACIONES,
            RESERVAS, PARROQUIAS, SACRAMENTOS, USUARIOS, ALMACEN_PAGOS, BITACORA);
    private static final NotificacionService NOTIFICACION_SERVICE = new NotificacionService(NOTIFICACIONES);
    private static final IntencionService INTENCION_SERVICE = new IntencionService(new IntencionDAOImpl(), new HorarioDAOImpl(),
            CUENTAS, PARROQUIAS, USUARIOS, new ComprobanteService(PAGOS, ALMACEN_PAGOS), BITACORA, IntencionService.relojDePeru());

    private Servicios() { }

    public static BitacoraService bitacora() { return BITACORA; }
    public static UsuarioService usuarios() { return USUARIO_SERVICE; }
    public static ReservaService reservas() { return RESERVA_SERVICE; }
    public static DocumentoService documentos() { return DOCUMENTO_SERVICE; }
    public static ContenidoService contenido() { return CONTENIDO_SERVICE; }
    public static ReporteService reportes() { return REPORTE_SERVICE; }
    public static PagoService pagos() { return PAGO_SERVICE; }
    public static NotificacionService notificaciones() { return NOTIFICACION_SERVICE; }
    public static IntencionService intenciones() { return INTENCION_SERVICE; }
}
