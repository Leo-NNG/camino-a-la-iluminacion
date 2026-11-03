package pe.caminoiluminacion.modelo;

import java.time.LocalTime;

/**
 * Una celebración semanal de una parroquia: una misa o una actividad (catequesis, grupo de oración…) un día a una hora.
 * Cada hora es una fila distinta, con {@code horaInicio} real: así se puede ordenar, validar y calcular la próxima misa.
 * {@code detalle} aclara la celebración ("vigilia") o nombra la actividad; en las misas es opcional.
 */
public class Horario {

    public static final String MISA = "misa";
    public static final String ACTIVIDAD = "actividad";

    private Integer idHorario;
    private Integer idParroquia;
    private String diaSemana;      // lunes, martes, ... domingo
    private LocalTime horaInicio;
    private String detalle;        // null si no hay nada que aclarar
    private String tipo;           // "misa" o "actividad"

    public Horario() {
    }

    public Horario(Integer idParroquia, String diaSemana, LocalTime horaInicio, String detalle, String tipo) {
        this.idParroquia = idParroquia;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.detalle = detalle;
        this.tipo = tipo;
    }

    public Integer getIdHorario() { return idHorario; }
    public void setIdHorario(Integer idHorario) { this.idHorario = idHorario; }

    public Integer getIdParroquia() { return idParroquia; }
    public void setIdParroquia(Integer idParroquia) { this.idParroquia = idParroquia; }

    public String getDiaSemana() { return diaSemana; }
    public void setDiaSemana(String diaSemana) { this.diaSemana = diaSemana; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    /**
     * Cómo se lee en pantalla: "7:00 a.m."; una misa con detalle, "5:00 p.m. (vigilia)"; una actividad,
     * "Catequesis de Primera Comunión — 5:00 p.m.". No se guarda: se calcula a partir de la hora real.
     */
    public String getHora() {
        String hora = formatear(horaInicio);
        if (detalle == null || detalle.isBlank()) return hora;
        return ACTIVIDAD.equals(tipo) ? detalle + " — " + hora : hora + " (" + detalle + ")";
    }

    /** 19:30 -> "7:30 p.m.", 00:15 -> "12:15 a.m.", 12:00 -> "12:00 p.m.". */
    public static String formatear(LocalTime t) {
        if (t == null) return "";
        int h = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        return String.format("%d:%02d %s", h, t.getMinute(), t.getHour() < 12 ? "a.m." : "p.m.");
    }

    @Override
    public String toString() {
        return "Horario{id=" + idHorario + ", parroquia=" + idParroquia + ", " + diaSemana + " " + getHora() + ", " + tipo + "}";
    }
}
