package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.NotificacionDAO;
import pe.caminoiluminacion.util.Registro;

import java.sql.SQLException;

/**
 * Envía avisos dentro de la plataforma al feligrés (RF13). Es de "mejor esfuerzo": si el aviso falla, la acción que lo
 * originó (verificar un pago, confirmar una reserva, validar un documento) NO se cancela; solo se deja constancia en el
 * registro del servidor. Nunca debe recibir contraseñas, DNI ni números de operación.
 */
public class Avisador {

    private final NotificacionDAO dao;

    public Avisador(NotificacionDAO dao) {
        this.dao = dao;
    }

    /** Un avisador que no envía nada: para servicios que se construyen sin notificaciones (por ejemplo, en pruebas). */
    public static Avisador ninguno() {
        return new Avisador(null);
    }

    public void avisar(int idUsuario, String asunto, String mensaje) {
        if (dao == null) return;
        try {
            dao.crear(idUsuario, asunto, mensaje);
        } catch (SQLException | RuntimeException e) {
            Registro.error("No se pudo crear el aviso «" + asunto + "»", e);
        }
    }
}
