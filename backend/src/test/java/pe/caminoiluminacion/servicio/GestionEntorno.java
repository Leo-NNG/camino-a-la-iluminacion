package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.controlador.Pruebas;
import pe.caminoiluminacion.controlador.Pruebas.FakeBitacora;
import pe.caminoiluminacion.controlador.Pruebas.FakeFechas;
import pe.caminoiluminacion.controlador.Pruebas.FakeReservas;
import pe.caminoiluminacion.controlador.Pruebas.FakeSacramentos;
import pe.caminoiluminacion.controlador.Pruebas.FakeUsuarios;
import pe.caminoiluminacion.modelo.FechaDisponible;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.modelo.Reserva;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Armado de {@link GestionReservaService} con DAO falsos en memoria, compartido por las pruebas del servicio y de los
 * servlets. El SQL real (cupo atómico, bloqueos) se verifica aparte contra una base real.
 */
public class GestionEntorno {

    public static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    public final FakeUsuarios usuarios = new FakeUsuarios();
    public final FakeReservas reservas = new FakeReservas();
    public final FakeFechas fechas = new FakeFechas(reservas);
    public final FakeSacramentos sacramentos = new FakeSacramentos();
    public final FakeBitacora bitacoraDao = new FakeBitacora();
    final PagoServiceTest.FakePagos pagos = new PagoServiceTest.FakePagos();
    public final GestionReservaService servicio;
    public final ReservaService reservaService;
    public final int ana;      // feligresa dueña de las solicitudes de las pruebas
    public final int otra;     // otra feligresa
    public final Reserva r;    // bautizo, parroquia 1, de Ana: dentro de 40 días, pendiente

    public GestionEntorno() throws SQLException {
        servicio = new GestionReservaService(reservas, fechas, sacramentos, pagos, new BitacoraService(bitacoraDao), () -> HOY);
        reservaService = new ReservaService(reservas, fechas, new Pruebas.FakeParroquias(), sacramentos, usuarios, new BitacoraService(bitacoraDao), () -> HOY);
        ana = usuarios.insertar(Pruebas.usuario("70000001", "ana@x.pe", "Clave123", "feligres", null)).getIdUsuario();
        otra = usuarios.insertar(Pruebas.usuario("70000002", "otra@x.pe", "Clave456", "feligres", null)).getIdUsuario();
        r = reserva(ana, "bautizo", HOY.plusDays(40), "pendiente");
    }

    /** Una solicitud de la parroquia 1 en la fecha y el estado indicados. */
    public Reserva reserva(int idUsuario, String sacramento, LocalDate fecha, String estado) {
        Reserva x = reservas.insertar(new Reserva(idUsuario, 1, sacramento, "Solicitante " + reservas.lista.size(), Date.valueOf(fecha), null));
        x.setEstado(estado);
        return x;
    }

    /** Publica una fecha (parroquia 1, bautizo = sacramento 1) con ese cupo. */
    public void publicar(LocalDate fecha, int cupo) throws SQLException {
        fechas.insertar(new FechaDisponible(1, 1, fecha, null, cupo));
    }

    /** Un pago del arancel de la solicitud, en el estado indicado. */
    public Pago pago(Reserva reserva, String estado) throws SQLException {
        return pagos.insertarLibre(new Pago(null, reserva.getIdUsuario(), 1, reserva.getIdReserva(), Pago.ARANCEL, new BigDecimal("50.00"), "yape",
                "v1:x", "OP" + pagos.lista.size(), "r.jpg", "sha" + pagos.lista.size(), estado, null, new Timestamp(0), null));
    }
}
