package pe.caminoiluminacion.modelo;

import java.sql.Timestamp;

public class Documento {

    private Integer idDocumento;
    private Integer idReserva; // puede ser null: ver Capítulo 8.3 del informe
    private Integer idUsuario;
    private String nombreArchivo;
    private String rutaArchivo;
    private String estadoValidacion; // pendiente, validado, rechazado
    private String motivoRechazo;
    private Timestamp fechaEnvio;
    private Timestamp fechaValidacion;

    public Documento() {
    }

    public Documento(Integer idReserva, Integer idUsuario, String nombreArchivo, String rutaArchivo) {
        this.idReserva = idReserva;
        this.idUsuario = idUsuario;
        this.nombreArchivo = nombreArchivo;
        this.rutaArchivo = rutaArchivo;
        this.estadoValidacion = "pendiente";
    }

    public Integer getIdDocumento() { return idDocumento; }
    public void setIdDocumento(Integer idDocumento) { this.idDocumento = idDocumento; }

    public Integer getIdReserva() { return idReserva; }
    public void setIdReserva(Integer idReserva) { this.idReserva = idReserva; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public String getEstadoValidacion() { return estadoValidacion; }
    public void setEstadoValidacion(String estadoValidacion) { this.estadoValidacion = estadoValidacion; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }

    public Timestamp getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(Timestamp fechaEnvio) { this.fechaEnvio = fechaEnvio; }

    public Timestamp getFechaValidacion() { return fechaValidacion; }
    public void setFechaValidacion(Timestamp fechaValidacion) { this.fechaValidacion = fechaValidacion; }

    @Override
    public String toString() {
        return "Documento{id=" + idDocumento + ", archivo='" + nombreArchivo
            + "', estado='" + estadoValidacion + "'}";
    }
}
