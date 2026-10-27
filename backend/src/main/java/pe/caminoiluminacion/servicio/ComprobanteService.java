package pe.caminoiluminacion.servicio;

import com.google.common.base.Strings;
import org.apache.commons.codec.digest.DigestUtils;
import pe.caminoiluminacion.dao.PagoDAO;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.seguridad.Cripto;
import pe.caminoiluminacion.util.AlmacenArchivos;
import pe.caminoiluminacion.util.AlmacenArchivos.Tipo;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * Reglas comunes de CUALQUIER pago con comprobante (arancel, donación, diezmo, intención de misa): formato de la
 * operación y del celular, imagen real (por su contenido), tope de tamaño, no reutilizar una operación ni un
 * comprobante y límite de pagos pendientes por usuario. Así todos los tipos de pago comparten las mismas defensas.
 */
public class ComprobanteService {

    private static final Pattern OPERACION = Pattern.compile("^\\d{6,12}$");
    private static final Pattern CELULAR = Pattern.compile("^9\\d{8}$");
    private static final int MAX_PENDIENTES_POR_USUARIO = 5;

    /** Contenido ya validado de un comprobante. {@code celularCifrado} nunca lleva el celular en claro. */
    public record Comprobante(byte[] datos, Tipo tipo, String sha256, String metodo, String operacion, String celularCifrado) { }

    private final PagoDAO pagoDAO;
    private final AlmacenArchivos almacen;

    public ComprobanteService(PagoDAO pagoDAO, AlmacenArchivos almacen) {
        this.pagoDAO = pagoDAO;
        this.almacen = almacen;
    }

    /** Valida y lee el comprobante; lanza {@link ReglaNegocioException} (400/413/415/409/429) si algo no cumple. */
    public Comprobante leer(int idUsuario, String metodo, String operacionTxt, String celularTxt, InputStream contenido)
            throws SQLException, IOException {
        String operacion = Strings.nullToEmpty(operacionTxt).trim();
        if (!OPERACION.matcher(operacion).matches()) {
            throw new ReglaNegocioException(400, "El número de operación debe tener entre 6 y 12 dígitos.");
        }
        String celular = Strings.nullToEmpty(celularTxt).replaceAll("\\s", "");
        if (!CELULAR.matcher(celular).matches()) {
            throw new ReglaNegocioException(400, "Ingresa el celular desde el que pagaste (9 dígitos, empieza con 9).");
        }
        if (contenido == null) throw new ReglaNegocioException(400, "Sube la captura del comprobante.");
        // Se lee con tope: no se confía en el tamaño que declare el cliente.
        byte[] datos = contenido.readNBytes(AlmacenArchivos.MAX_BYTES + 1);
        if (datos.length == 0) throw new ReglaNegocioException(400, "Sube la captura del comprobante.");
        if (datos.length > AlmacenArchivos.MAX_BYTES) throw new ReglaNegocioException(413, "El comprobante supera el máximo de 5 MB.");
        Tipo tipo = AlmacenArchivos.detectar(datos);
        if (tipo != Tipo.JPEG && tipo != Tipo.PNG) throw new ReglaNegocioException(415, "El comprobante debe ser una imagen JPG o PNG.");

        String sha = DigestUtils.sha256Hex(datos);
        if (pagoDAO.existeOperacionVigente(metodo, operacion)) {
            throw new ReglaNegocioException(409, "Ese número de operación ya fue registrado en otro pago.");
        }
        if (pagoDAO.existeVoucherVigente(sha)) {
            throw new ReglaNegocioException(409, "Ese comprobante ya fue usado en otro pago.");
        }
        long pendientes = pagoDAO.listarPorUsuario(idUsuario).stream().filter(p -> Pago.PENDIENTE.equals(p.estado())).count();
        if (pendientes >= MAX_PENDIENTES_POR_USUARIO) {
            throw new ReglaNegocioException(429, "Tienes varios pagos esperando verificación. Espera a que la parroquia los revise.");
        }
        return new Comprobante(datos, tipo, sha, metodo, operacion, Cripto.cifrar(celular));
    }

    /** Guarda el archivo en el almacén privado y devuelve su nombre. */
    public String guardarArchivo(Comprobante c) throws IOException {
        return almacen.guardar(c.datos(), c.tipo());
    }

    /** Borra un archivo ya guardado (cuando el registro en la base de datos falla). */
    public void borrarArchivo(String nombre) {
        almacen.borrar(nombre);
    }
}
