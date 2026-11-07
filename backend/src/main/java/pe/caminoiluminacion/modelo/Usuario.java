package pe.caminoiluminacion.modelo;

import java.sql.Timestamp;

/**
 * Representa tanto a feligreses como a personal parroquial (campo `rol`).
 * Corresponde a la tabla `usuario` y a la herencia Usuario -> Feligrés /
 * Administrador del diagrama de clases UML (Capítulo 8.4 del informe).
 */
public class Usuario {

    private Integer idUsuario;
    private String dni; // 8 dígitos; es la credencial de acceso
    private Integer idParroquia; // solo personal (administrador): parroquia que gestiona
    private String nombre;
    private String apellido;
    private String correo;
    private String telefono;
    private String contrasenaHash;
    private String rol; // "feligres" o "administrador"
    private String ubicacion;
    private Timestamp creadoEn;
    private String politicaVersion; // versión de la política de privacidad que aceptó (null = aún no)
    private Timestamp politicaAceptadaEn;
    private Timestamp eliminadoEn; // no null = cuenta eliminada (fila anonimizada)

    /** Lo que se muestra en lugar del nombre cuando la cuenta fue eliminada. */
    public static final String CUENTA_ELIMINADA = "(cuenta eliminada)";

    public Usuario() {
    }

    public Usuario(String dni, String nombre, String apellido, String correo, String telefono,
                   String contrasenaHash, String rol, String ubicacion) {
        this.dni = dni;
        this.nombre = nombre;
        this.apellido = apellido;
        this.correo = correo;
        this.telefono = telefono;
        this.contrasenaHash = contrasenaHash;
        this.rol = rol;
        this.ubicacion = ubicacion;
    }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    public Integer getIdParroquia() { return idParroquia; }
    public void setIdParroquia(Integer idParroquia) { this.idParroquia = idParroquia; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getContrasenaHash() { return contrasenaHash; }
    public void setContrasenaHash(String contrasenaHash) { this.contrasenaHash = contrasenaHash; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }

    public Timestamp getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Timestamp creadoEn) { this.creadoEn = creadoEn; }

    public String getPoliticaVersion() { return politicaVersion; }
    public void setPoliticaVersion(String politicaVersion) { this.politicaVersion = politicaVersion; }

    public Timestamp getPoliticaAceptadaEn() { return politicaAceptadaEn; }
    public void setPoliticaAceptadaEn(Timestamp politicaAceptadaEn) { this.politicaAceptadaEn = politicaAceptadaEn; }

    public Timestamp getEliminadoEn() { return eliminadoEn; }
    public void setEliminadoEn(Timestamp eliminadoEn) { this.eliminadoEn = eliminadoEn; }

    public boolean estaEliminado() { return eliminadoEn != null; }

    /** "Nombre Apellido", o "(cuenta eliminada)" si la persona eliminó su cuenta (sus datos ya no existen). */
    public String nombreParaMostrar() {
        return estaEliminado() ? CUENTA_ELIMINADA : nombre + " " + apellido;
    }

    @Override
    public String toString() {
        return "Usuario{id=" + idUsuario + ", nombre='" + nombre + " " + apellido
            + "', correo='" + correo + "', rol='" + rol + "'}";
    }
}
