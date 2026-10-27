package pe.caminoiluminacion.servicio;

/**
 * Una regla de negocio no se cumple (dato inválido, sin permiso, fecha ocupada…).
 * Lleva el código HTTP que corresponde y un mensaje pensado para mostrarse al usuario.
 * Los errores de infraestructura (base de datos caída) NO usan esta clase: siguen siendo SQLException.
 */
public class ReglaNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int estado;

    public ReglaNegocioException(int estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public int getEstado() {
        return estado;
    }
}
