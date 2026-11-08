package pe.caminoiluminacion.modelo;

/** Un documento que una parroquia pide para un sacramento (tabla requisito_sacramento). */
public class Requisito {

    private Integer idRequisito;
    private int idParroquia;
    private int idSacramento;
    private String codigoSacramento;  // solo en lecturas (join con sacramento)
    private String nombreSacramento;  // solo en lecturas
    private String nombre;
    private String descripcion;
    private boolean obligatorio;
    private int orden;

    public Requisito() { }

    public Requisito(int idParroquia, int idSacramento, String nombre, String descripcion, boolean obligatorio, int orden) {
        this.idParroquia = idParroquia;
        this.idSacramento = idSacramento;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.obligatorio = obligatorio;
        this.orden = orden;
    }

    public Integer getIdRequisito() { return idRequisito; }
    public void setIdRequisito(Integer idRequisito) { this.idRequisito = idRequisito; }

    public int getIdParroquia() { return idParroquia; }
    public void setIdParroquia(int idParroquia) { this.idParroquia = idParroquia; }

    public int getIdSacramento() { return idSacramento; }
    public void setIdSacramento(int idSacramento) { this.idSacramento = idSacramento; }

    public String getCodigoSacramento() { return codigoSacramento; }
    public void setCodigoSacramento(String codigoSacramento) { this.codigoSacramento = codigoSacramento; }

    public String getNombreSacramento() { return nombreSacramento; }
    public void setNombreSacramento(String nombreSacramento) { this.nombreSacramento = nombreSacramento; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public boolean isObligatorio() { return obligatorio; }
    public void setObligatorio(boolean obligatorio) { this.obligatorio = obligatorio; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

    @Override
    public String toString() {
        return "Requisito{id=" + idRequisito + ", parroquia=" + idParroquia + ", sacramento=" + idSacramento + ", nombre='" + nombre + "'}";
    }
}
