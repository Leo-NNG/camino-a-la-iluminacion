package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.controlador.Pruebas;
import pe.caminoiluminacion.controlador.Pruebas.FakeParroquias;
import pe.caminoiluminacion.controlador.Pruebas.FakeReservas;
import pe.caminoiluminacion.controlador.Pruebas.FakeSacramentos;
import pe.caminoiluminacion.controlador.Pruebas.FakeUsuarios;
import pe.caminoiluminacion.dao.VerificacionDAO;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.util.LimiteIntentos;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Armado de {@link VerificacionService} con DAO falsos en memoria, compartido por las pruebas del servicio y de los servlets. */
public class VerificacionEntorno {

    /** Guarda los códigos en memoria y arma las respuestas públicas con los datos de las reservas falsas. */
    public static class FakeVerificacion implements VerificacionDAO {
        final FakeReservas reservas;
        final FakeSacramentos sacramentos;
        public final Map<Integer, String> codigosReserva = new HashMap<>();
        public final Map<Integer, String> codigosPago = new HashMap<>();
        /** Pagos que "existen": id -> lo que se diría públicamente de ellos. */
        public final Map<Integer, ReciboVerificado> pagos = new HashMap<>();
        /** Cuántas llamadas a asegurarCodigo* deben chocar con un código repetido antes de funcionar. */
        public int colisiones;
        public int llamadas;

        FakeVerificacion(FakeReservas reservas, FakeSacramentos sacramentos) { this.reservas = reservas; this.sacramentos = sacramentos; }

        public String asegurarCodigoReserva(int id, String candidato) throws SQLException {
            llamadas++;
            if (colisiones > 0) { colisiones--; throw new SQLIntegrityConstraintViolationException("duplicado", "23000", 1062); }
            if (reservas.buscarPorId(id).isEmpty()) return null;
            return codigosReserva.computeIfAbsent(id, k -> candidato);
        }

        public String asegurarCodigoPago(int id, String candidato) throws SQLException {
            llamadas++;
            if (colisiones > 0) { colisiones--; throw new SQLIntegrityConstraintViolationException("duplicado", "23000", 1062); }
            if (!pagos.containsKey(id)) return null;
            return codigosPago.computeIfAbsent(id, k -> candidato);
        }

        public Optional<ConstanciaVerificada> constanciaPorCodigo(String codigo) {
            return codigosReserva.entrySet().stream().filter(e -> e.getValue().equals(codigo)).findFirst()
                    .flatMap(e -> reservas.buscarPorId(e.getKey())).map(r -> new ConstanciaVerificada(r.getEstado(), r.getFechaSolicitada(),
                            r.getNombreSolicitante(), "Parroquia San José", sacramentos.buscarPorCodigo(r.getSacramento()).get().getNombre()));
        }

        public Optional<ReciboVerificado> reciboPorCodigo(String codigo) {
            return codigosPago.entrySet().stream().filter(e -> e.getValue().equals(codigo)).findFirst().map(e -> pagos.get(e.getKey()));
        }
    }

    public static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    public final FakeUsuarios usuarios = new FakeUsuarios();
    public final FakeReservas reservas = new FakeReservas();
    public final FakeSacramentos sacramentos = new FakeSacramentos();
    public final FakeVerificacion dao = new FakeVerificacion(reservas, sacramentos);
    public final long[] ahoraMs = {0};
    public final VerificacionService servicio;
    public final int ana, otra;
    public final Reserva confirmada;  // bautizo, parroquia 1, de Ana, "Bebé Uno", confirmada

    public VerificacionEntorno() throws SQLException {
        servicio = new VerificacionService(dao, reservas, new FakeParroquias(), sacramentos, null, new LimiteIntentos(3, 60_000, 120_000, () -> ahoraMs[0]));
        ana = usuarios.insertar(Pruebas.usuario("70000001", "ana@x.pe", "Clave123", "feligres", null)).getIdUsuario();
        otra = usuarios.insertar(Pruebas.usuario("70000002", "otra@x.pe", "Clave456", "feligres", null)).getIdUsuario();
        confirmada = reserva(ana, "Bebé Uno Díaz", "confirmada");
    }

    public Reserva reserva(int idUsuario, String solicitante, String estado) {
        Reserva r = reservas.insertar(new Reserva(idUsuario, 1, "bautizo", solicitante, Date.valueOf(HOY.plusDays(40)), null));
        r.setEstado(estado);
        return r;
    }

    /** Un pago aprobado "existente" de S/ 50.00 por Yape; devuelve su id. */
    public int pagoAprobado(int id) {
        dao.pagos.put(id, new VerificacionDAO.ReciboVerificado("aprobado", "arancel", new BigDecimal("50"), "yape", Timestamp.valueOf("2026-10-21 11:02:00"), "Parroquia San José"));
        return id;
    }
}
