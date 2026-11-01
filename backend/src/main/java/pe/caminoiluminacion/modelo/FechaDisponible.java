package pe.caminoiluminacion.modelo;

import java.time.LocalDate;
import java.time.LocalTime;

/** Una fecha que la parroquia publica para un sacramento, con su cupo y cuántas solicitudes activas ya la ocupan. */
public class FechaDisponible {

    private Integer idFecha;
    private Integer idParroquia;
    private Integer idSacramento;
    private String codigoSacramento;
    private String nombreSacramento;
    private LocalDate fecha;
    private LocalTime horaInicio; // opcional
    private int cupo;
    private int ocupados;         // solicitudes pendientes o confirmadas para esa parroquia, sacramento y fecha

    public FechaDisponible() {
    }

    public FechaDisponible(Integer idParroquia, Integer idSacramento, LocalDate fecha, LocalTime horaInicio, int cupo) {
        this.idParroquia = idParroquia;
        this.idSacramento = idSacramento;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.cupo = cupo;
    }

    public int getLibres() { return Math.max(0, cupo - ocupados); }

    public Integer getIdFecha() { return idFecha; }
    public void setIdFecha(Integer idFecha) { this.idFecha = idFecha; }

    public Integer getIdParroquia() { return idParroquia; }
    public void setIdParroquia(Integer idParroquia) { this.idParroquia = idParroquia; }

    public Integer getIdSacramento() { return idSacramento; }
    public void setIdSacramento(Integer idSacramento) { this.idSacramento = idSacramento; }

    public String getCodigoSacramento() { return codigoSacramento; }
    public void setCodigoSacramento(String codigoSacramento) { this.codigoSacramento = codigoSacramento; }

    public String getNombreSacramento() { return nombreSacramento; }
    public void setNombreSacramento(String nombreSacramento) { this.nombreSacramento = nombreSacramento; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public int getCupo() { return cupo; }
    public void setCupo(int cupo) { this.cupo = cupo; }

    public int getOcupados() { return ocupados; }
    public void setOcupados(int ocupados) { this.ocupados = ocupados; }

    @Override
    public String toString() {
        return "FechaDisponible{id=" + idFecha + ", parroquia=" + idParroquia + ", sacramento=" + idSacramento
                + ", fecha=" + fecha + ", cupo=" + cupo + ", ocupados=" + ocupados + "}";
    }
}
