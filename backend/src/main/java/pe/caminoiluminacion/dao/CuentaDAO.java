package pe.caminoiluminacion.dao;

import java.sql.Date;
import java.sql.SQLException;
import java.util.List;

/**
 * Operaciones de "Mi cuenta" (privacidad, Ley n.° 29733). Aparte de UsuarioDAO a propósito: son pocas, tienen reglas
 * propias y la anonimización debe ser UNA transacción.
 */
public interface CuentaDAO {

    /**
     * Resultado de pedir la eliminación. Si {@code bloqueos} no está vacío NO se tocó nada. Si está vacío, la cuenta
     * quedó anonimizada y {@code archivos} son los nombres (en el almacén privado) de los documentos y comprobantes
     * cuyas filas ya se borraron o se vaciaron: quien llama debe borrarlos del disco.
     */
    record Anonimizacion(List<String> bloqueos, List<String> archivos) {
        public boolean hecha() { return bloqueos.isEmpty(); }
    }

    /** Cambia correo y teléfono (null = sin teléfono). @throws SQLException con SQLState 23000 si el correo ya existe. */
    boolean actualizarContacto(int idUsuario, String correo, String telefono) throws SQLException;

    boolean actualizarContrasena(int idUsuario, String contrasenaHash) throws SQLException;

    /** Guarda la versión de la política aceptada y la fecha actual. */
    boolean registrarAceptacion(int idUsuario, String version) throws SQLException;

    /**
     * Elimina la cuenta de un FELIGRÉS anonimizándola, todo en una transacción que bloquea su fila:
     * si tiene pagos pendientes, reservas pendientes o confirmadas con fecha desde {@code hoy}, o intenciones de misa
     * vigentes desde {@code hoy}, no se hace nada y se devuelven los motivos.
     */
    Anonimizacion anonimizar(int idUsuario, Date hoy) throws SQLException;
}
