package pe.caminoiluminacion.modelo;

import java.sql.Date;
import java.sql.Timestamp;

public class Reserva {

    private Integer idReserva;
    private Integer idUsuario;
    private Integer idParroquia;
    private String sacramento; // bautizo, matrimonio, primera_comunion, confirmacion
    private String nombreSolicitante;
    private Date fechaSolicitada;
    private String notas;
    private String estado; // pendiente, confirmada, rechazada, cancelada (la cancela el propio feligres)
    private String motivoRechazo; // solo cuando estado = rechazada
    private Timestamp creadoEn;
    private Date fechaOriginal;      // primera fecha pedida (solo si se reprogramo)
    private int reprogramaciones;    // cuantas veces se reprogramo
    private Timestamp canceladaEn;
    private String motivoCancelacion; // opcional, lo escribe el feligres

    public Reserva() {
    }

    public Reserva(Integer idUsuario, Integer idParroquia, String sacramento,
                    String nombreSolicitante, Date fechaSolicitada, String notas) {
        this.idUsuario = idUsuario;
        this.idParroquia = idParroquia;
        this.sacramento = sacramento;
        this.nombreSolicitante = nombreSolicitante;
        this.fechaSolicitada = fechaSolicitada;
        this.notas = notas;
        this.estado = "pendiente";
    }

    public Integer getIdReserva() { return idReserva; }
    public void setIdReserva(Integer idReserva) { this.idReserva = idReserva; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public Integer getIdParroquia() { return idParroquia; }
    public void setIdParroquia(Integer idParroquia) { this.idParroquia = idParroquia; }

    public String getSacramento() { return sacramento; }
    public void setSacramento(String sacramento) { this.sacramento = sacramento; }

    public String getNombreSolicitante() { return nombreSolicitante; }
    public void setNombreSolicitante(String nombreSolicitante) { this.nombreSolicitante = nombreSolicitante; }

    public Date getFechaSolicitada() { return fechaSolicitada; }
    public void setFechaSolicitada(Date fechaSolicitada) { this.fechaSolicitada = fechaSolicitada; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }

    public Timestamp getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Timestamp creadoEn) { this.creadoEn = creadoEn; }

    @Override
    public String toString() {
        return "Reserva{id=" + idReserva + ", sacramento='" + sacramento
            + "', solicitante='" + nombreSolicitante + "', estado='" + estado + "'}";
    }

    public Date getFechaOriginal() { return fechaOriginal; }
    public void setFechaOriginal(Date fechaOriginal) { this.fechaOriginal = fechaOriginal; }

    public int getReprogramaciones() { return reprogramaciones; }
    public void setReprogramaciones(int reprogramaciones) { this.reprogramaciones = reprogramaciones; }

    public Timestamp getCanceladaEn() { return canceladaEn; }
    public void setCanceladaEn(Timestamp canceladaEn) { this.canceladaEn = canceladaEn; }

    public String getMotivoCancelacion() { return motivoCancelacion; }
    public void setMotivoCancelacion(String motivoCancelacion) { this.motivoCancelacion = motivoCancelacion; }
}
