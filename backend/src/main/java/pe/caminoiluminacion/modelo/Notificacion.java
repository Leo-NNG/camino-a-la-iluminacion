package pe.caminoiluminacion.modelo;

import java.sql.Timestamp;

/** Aviso dentro de la plataforma dirigido a un feligrés (RF13). */
public record Notificacion(int idNotificacion, int idUsuario, String asunto, String mensaje, boolean leida, Timestamp creadaEn) { }
