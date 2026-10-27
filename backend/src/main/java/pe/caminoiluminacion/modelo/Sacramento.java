package pe.caminoiluminacion.modelo;

/** Un sacramento del catálogo (tabla sacramento). */
public class Sacramento {

    private Integer idSacramento;
    private String codigo; // identificador estable, ej. primera_comunion
    private String nombre; // lo que ve el usuario, ej. Primera Comunión

    public Sacramento() { }

    public Sacramento(Integer idSacramento, String codigo, String nombre) {
        this.idSacramento = idSacramento;
        this.codigo = codigo;
        this.nombre = nombre;
    }

    public Integer getIdSacramento() { return idSacramento; }
    public void setIdSacramento(Integer idSacramento) { this.idSacramento = idSacramento; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    @Override
    public String toString() { return "Sacramento{" + idSacramento + ", " + codigo + "}"; }
}
