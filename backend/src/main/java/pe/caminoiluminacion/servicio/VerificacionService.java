package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.dao.VerificacionDAO;
import pe.caminoiluminacion.dao.VerificacionDAO.ConstanciaVerificada;
import pe.caminoiluminacion.dao.VerificacionDAO.ReciboVerificado;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.modelo.Sacramento;
import pe.caminoiluminacion.util.CodigoVerificacion;
import pe.caminoiluminacion.util.LimiteIntentos;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Constancias de reserva y códigos de verificación. Cualquier persona puede comprobar un documento con su código en la
 * página pública, así que lo que se devuelve es solo lo mínimo: nunca nombres completos (solo iniciales), documento de
 * identidad, celular ni número de operación. La verificación dice si el documento SIGUE vigente (una constancia deja de
 * valer si la solicitud se reprograma o se cancela) y limita los intentos fallidos por dirección IP.
 */
public class VerificacionService {

    /** Datos de la constancia en PDF. {@code documentos} es una línea de resumen del checklist, o null si la parroquia no definió requisitos. */
    public record Constancia(String codigo, String parroquia, String direccion, String sacramento, String solicitante, Date fecha, String documentos) { }

    /** Respuesta pública de la verificación de un código. */
    public record Resultado(String tipo, boolean vigente, String estado, String parroquia, String detalle, String fecha, String monto, String solicitante) { }

    private static final int INTENTOS_DE_CODIGO = 5;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final VerificacionDAO verificacionDAO;
    private final ReservaDAO reservaDAO;
    private final ParroquiaDAO parroquiaDAO;
    private final SacramentoDAO sacramentoDAO;
    private final RequisitoService requisitos; // null = la constancia no resume los documentos
    private final LimiteIntentos limite;

    public VerificacionService(VerificacionDAO verificacionDAO, ReservaDAO reservaDAO, ParroquiaDAO parroquiaDAO, SacramentoDAO sacramentoDAO,
                               RequisitoService requisitos, LimiteIntentos limite) {
        this.verificacionDAO = verificacionDAO;
        this.reservaDAO = reservaDAO;
        this.parroquiaDAO = parroquiaDAO;
        this.sacramentoDAO = sacramentoDAO;
        this.requisitos = requisitos;
        this.limite = limite;
    }

    // ------------------------------------------------------------------ emisión

    /** Código de verificación del recibo de un pago (se crea la primera vez y después es siempre el mismo). */
    public String codigoRecibo(int idPago) throws SQLException {
        for (int i = 0; i < INTENTOS_DE_CODIGO; i++) {
            try {
                String codigo = verificacionDAO.asegurarCodigoPago(idPago, CodigoVerificacion.nuevo(CodigoVerificacion.RECIBO));
                if (codigo != null) return codigo;
                throw new ReglaNegocioException(404, "El pago no existe.");
            } catch (SQLIntegrityConstraintViolationException choque) {
                // ya existía otro documento con ese código al azar (casi imposible): se prueba con otro
            }
        }
        throw new SQLException("No se pudo generar un código de verificación único");
    }

    /**
     * Datos de la constancia de una solicitud DEL USUARIO: la de otra persona se trata como inexistente y solo las
     * confirmadas tienen constancia. El código se crea la primera vez y después es siempre el mismo.
     */
    public Constancia constancia(int idUsuario, String reservaTxt) throws SQLException {
        int id;
        try {
            id = Integer.parseInt(reservaTxt == null ? "" : reservaTxt.trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "Solicitud no válida.");
        }
        Reserva r = reservaDAO.buscarPorId(id).filter(x -> x.getIdUsuario() != null && x.getIdUsuario() == idUsuario)
                .orElseThrow(() -> new ReglaNegocioException(404, "La solicitud no existe."));
        if (!"confirmada".equals(r.getEstado())) {
            throw new ReglaNegocioException(409, "La constancia está disponible cuando la parroquia confirma la fecha de tu solicitud.");
        }
        String codigo = null;
        for (int i = 0; i < INTENTOS_DE_CODIGO && codigo == null; i++) {
            try {
                codigo = verificacionDAO.asegurarCodigoReserva(id, CodigoVerificacion.nuevo(CodigoVerificacion.CONSTANCIA));
            } catch (SQLIntegrityConstraintViolationException choque) {
                // código repetido por azar: se prueba con otro
            }
        }
        if (codigo == null) throw new SQLException("No se pudo generar un código de verificación único");

        Parroquia p = parroquiaDAO.buscarPorId(r.getIdParroquia()).orElse(null);
        String sacramento = sacramentoDAO.buscarPorCodigo(r.getSacramento()).map(Sacramento::getNombre).orElse(r.getSacramento());
        return new Constancia(codigo, p == null ? "" : p.getNombre(), p == null || p.getDireccion() == null ? "" : p.getDireccion(),
                sacramento, r.getNombreSolicitante(), r.getFechaSolicitada(), resumenDocumentos(idUsuario, id));
    }

    private String resumenDocumentos(int idUsuario, int idReserva) {
        if (requisitos == null) return null;
        try {
            var c = requisitos.checklist(idUsuario, String.valueOf(idReserva));
            if (c.obligatorios() == 0) return null;
            return c.validados() + " de " + c.obligatorios() + " requisitos obligatorios validados" + (c.completo() ? " (expediente completo)" : "");
        } catch (ReglaNegocioException | SQLException e) {
            return null; // el resumen es informativo: si no se puede calcular, la constancia sale igual
        }
    }

    // ------------------------------------------------------------------ verificación pública

    /** @param ip dirección de quien consulta: los intentos que no encuentran nada se limitan por IP */
    public Resultado verificar(String codigoTxt, String ip) throws SQLException {
        String clave = "verificar|" + ip;
        if (limite.estaBloqueado(clave)) {
            throw new ReglaNegocioException(429, "Demasiados intentos con códigos que no existen. Espera unos minutos e inténtalo de nuevo.");
        }
        String codigo = CodigoVerificacion.normalizar(codigoTxt);
        if (codigo == null) {
            limite.registrarFallo(clave);
            throw new ReglaNegocioException(400, "Escribe el código tal como aparece en el documento. Ejemplo: CON-7K3M9-PQ2XD.");
        }
        Optional<Resultado> r = CodigoVerificacion.CONSTANCIA.equals(CodigoVerificacion.prefijo(codigo))
                ? verificacionDAO.constanciaPorCodigo(codigo).map(this::deConstancia)
                : verificacionDAO.reciboPorCodigo(codigo).map(this::deRecibo);
        if (r.isEmpty()) {
            limite.registrarFallo(clave);
            throw new ReglaNegocioException(404, "No encontramos ningún documento con ese código. Revisa que esté bien escrito.");
        }
        return r.get();
    }

    private Resultado deConstancia(ConstanciaVerificada c) {
        boolean vigente = "confirmada".equals(c.estado());
        String estado = switch (c.estado()) {
            case "confirmada" -> "Constancia vigente: la parroquia confirmó esta fecha.";
            case "pendiente" -> "No vigente: la solicitud fue reprogramada y espera una nueva confirmación de la parroquia.";
            case "cancelada" -> "Anulada: la solicitud fue cancelada.";
            default -> "No vigente: la solicitud ya no está confirmada.";
        };
        return new Resultado("constancia", vigente, estado, c.parroquia(), c.sacramento(), c.fecha() == null ? null : c.fecha().toString(), null, iniciales(c.solicitante()));
    }

    private Resultado deRecibo(ReciboVerificado r) {
        boolean vigente = "aprobado".equals(r.estado());
        String estado = vigente ? "Recibo válido: la parroquia verificó este pago." : "No válido: el pago no figura como verificado.";
        String fecha = r.verificadoEn() == null ? null : r.verificadoEn().toInstant().atZone(LIMA).toLocalDate().toString();
        String metodo = "yape".equals(r.metodo()) ? "Yape" : "plin".equals(r.metodo()) ? "Plin" : "";
        return new Resultado("recibo", vigente, estado, r.parroquia(), concepto(r.concepto()) + (metodo.isEmpty() ? "" : " (" + metodo + ")"),
                fecha, soles(r.monto()), null);
    }

    private static String concepto(String c) {
        return switch (c == null ? "" : c) {
            case "arancel" -> "Arancel de sacramento";
            case "donacion" -> "Donación";
            case "diezmo" -> "Diezmo";
            case "intencion" -> "Ofrenda por intención de misa";
            default -> "Pago";
        };
    }

    private static String soles(BigDecimal monto) {
        return monto == null ? null : "S/ " + monto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * "Ana Torres Díaz" -> "A. T. D.". Nunca se muestra un nombre completo. El marcador que deja una cuenta eliminada, "(anonimizado)",
     * y los nombres sin letras dan "" (un paréntesis al inicio no es un nombre).
     */
    static String iniciales(String nombre) {
        if (nombre == null || nombre.trim().startsWith("(")) return "";
        StringBuilder sb = new StringBuilder();
        for (String palabra : nombre.trim().split("\\s+")) {
            int i = 0;
            while (i < palabra.length() && !Character.isLetter(palabra.charAt(i))) i++;
            if (i < palabra.length()) sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(palabra.charAt(i))).append('.');
        }
        return sb.toString();
    }
}
