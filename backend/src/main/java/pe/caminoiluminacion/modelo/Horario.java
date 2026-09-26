package pe.caminoiluminacion.modelo;

public class Horario {

    private Integer idHorario;
    private Integer idParroquia;
    private String diaSemana; // lunes, martes, ... domingo
    private String hora;      // ej: "7:00 a.m. y 7:00 p.m."
    private String tipo;      // "misa" o "actividad"

    public Horario() {
    }

    public Horario(Integer idParroquia, String diaSemana, String hora, String tipo) {
        this.idParroquia = idParroquia;
        this.diaSemana = diaSemana;
        this.hora = hora;
        this.tipo = tipo;
    }

    public Integer getIdHorario() { return idHorario; }
    public void setIdHorario(Integer idHorario) { this.idHorario = idHorario; }

    public Integer getIdParroquia() { return idParroquia; }
    public void setIdParroquia(Integer idParroquia) { this.idParroquia = idParroquia; }

    public String getDiaSemana() { return diaSemana; }
    public void setDiaSemana(String diaSemana) { this.diaSemana = diaSemana; }

    public String getHora() { return hora; }
    public void setHora(String hora) { this.hora = hora; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    @Override
    public String toString() {
        return "Horario{id=" + idHorario + ", dia='" + diaSemana + "', hora='" + hora + "', tipo='" + tipo + "'}";
    }
}
