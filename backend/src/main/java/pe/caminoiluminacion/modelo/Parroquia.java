package pe.caminoiluminacion.modelo;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class Parroquia {

    private Integer idParroquia;
    private String nombre;
    private String direccion;
    private BigDecimal latitud;
    private BigDecimal longitud;
    private String sacramentosDisponibles; // lista separada por comas
    private String fotoUrl;
    private String fotoInteriorUrl;
    private String horarioTramites;
    private Timestamp creadoEn;

    public Parroquia() {
    }

    public Parroquia(String nombre, String direccion, BigDecimal latitud,
                      BigDecimal longitud, String sacramentosDisponibles) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.latitud = latitud;
        this.longitud = longitud;
        this.sacramentosDisponibles = sacramentosDisponibles;
    }

    public Integer getIdParroquia() { return idParroquia; }
    public void setIdParroquia(Integer idParroquia) { this.idParroquia = idParroquia; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public BigDecimal getLatitud() { return latitud; }
    public void setLatitud(BigDecimal latitud) { this.latitud = latitud; }

    public BigDecimal getLongitud() { return longitud; }
    public void setLongitud(BigDecimal longitud) { this.longitud = longitud; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public String getFotoInteriorUrl() { return fotoInteriorUrl; }
    public void setFotoInteriorUrl(String fotoInteriorUrl) { this.fotoInteriorUrl = fotoInteriorUrl; }

    public String getHorarioTramites() { return horarioTramites; }
    public void setHorarioTramites(String horarioTramites) { this.horarioTramites = horarioTramites; }

    public String getSacramentosDisponibles() { return sacramentosDisponibles; }
    public void setSacramentosDisponibles(String s) { this.sacramentosDisponibles = s; }

    public Timestamp getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Timestamp creadoEn) { this.creadoEn = creadoEn; }

    @Override
    public String toString() {
        return "Parroquia{id=" + idParroquia + ", nombre='" + nombre + "', direccion='" + direccion + "'}";
    }
}
