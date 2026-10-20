package pe.caminoiluminacion.seguridad;

import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.util.Registro;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cifra los DNI que todavía están en claro (cuentas creadas antes de esta mejora o insertadas por el script SQL
 * de demostración) y calcula su huella de búsqueda. Es idempotente: solo toca las filas con dni_indice NULL,
 * así que se puede ejecutar en cada arranque sin riesgo.
 * Requiere haber aplicado database/migracion_06_dni_cifrado.sql (columna dni_indice).
 */
public final class MigracionDni {

    private MigracionDni() { }

    /** @return cuántas cuentas se migraron (0 si no había nada pendiente o si falló, ver el log). */
    public static int ejecutar() {
        try (Connection con = Conexion.obtener()) {
            return ejecutar(con);
        } catch (SQLException e) {
            Registro.error("No se pudo cifrar los DNI pendientes. ¿Aplicaste database/migracion_06_dni_cifrado.sql?", e);
            return 0;
        }
    }

    static int ejecutar(Connection con) throws SQLException {
        Map<Integer, String> pendientes = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT id_usuario, dni FROM usuario WHERE dni_indice IS NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) pendientes.put(rs.getInt(1), rs.getString(2));
        }
        int migradas = 0;
        String sql = "UPDATE usuario SET dni = ?, dni_indice = ? WHERE id_usuario = ? AND dni_indice IS NULL";
        try (PreparedStatement up = con.prepareStatement(sql)) {
            for (Map.Entry<Integer, String> e : pendientes.entrySet()) {
                String plano = Cripto.descifrar(e.getValue());
                if (plano == null || !plano.matches("[0-9]{8}")) {
                    Registro.aviso("Usuario " + e.getKey() + ": el DNI no se pudo leer (¿cambió la clave de cifrado?). Se omite.");
                    continue;
                }
                up.setString(1, Cripto.cifrar(plano));
                up.setString(2, Cripto.indice(plano));
                up.setInt(3, e.getKey());
                migradas += up.executeUpdate();
            }
        }
        if (migradas > 0) Registro.aviso("DNI cifrados: " + migradas + " cuenta(s).");
        return migradas;
    }
}
