package pe.caminoiluminacion.modelo;

import java.sql.Date;
import java.sql.Timestamp;

public class Aviso {

    private Integer idAviso;
    private Integer idParroquia;
    private String titulo;
    private String descripcion;
    private String tipo; // santa_cena, ayuno, vigilia, celebracion, cambio_horario, otro
    private boolean urgente;
    private Date fechaEvento;
    private Integer idAutor; // quién lo publicó
    private Timestamp publicadoEn;

    public Aviso() {
    }

    public Aviso(Integer idParroquia, String titulo, String descripcion,
                 String tipo, boolean urgente, Date fechaEvento) {
        this.idParroquia = idParroquia;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.tipo = tipo;
        this.urgente = urgente;
        this.fechaEvento = fechaEvento;
    }

    public Integer getIdAviso() { return idAviso; }
    public void setIdAviso(Integer idAviso) { this.idAviso = idAviso; }

    public Integer getIdParroquia() { return idParroquia; }
    public void setIdParroquia(Integer idParroquia) { this.idParroquia = idParroquia; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public boolean isUrgente() { return urgente; }
    public void setUrgente(boolean urgente) { this.urgente = urgente; }

    public Date getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(Date fechaEvento) { this.fechaEvento = fechaEvento; }

    public Integer getIdAutor() { return idAutor; }
    public void setIdAutor(Integer idAutor) { this.idAutor = idAutor; }

    public Timestamp getPublicadoEn() { return publicadoEn; }
    public void setPublicadoEn(Timestamp publicadoEn) { this.publicadoEn = publicadoEn; }

    @Override
    public String toString() {
        return "Aviso{id=" + idAviso + ", titulo='" + titulo + "', urgente=" + urgente + "}";
    }
}
