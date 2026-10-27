package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.UsuarioDAO;
import pe.caminoiluminacion.modelo.Usuario;
import pe.caminoiluminacion.util.Claves;
import pe.caminoiluminacion.util.LimiteIntentos;
import pe.caminoiluminacion.util.Sesion;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Pattern;

/** Autenticación (DNI + contraseña) y registro de feligreses. */
public class UsuarioService {

    private static final Pattern NOMBRE = Pattern.compile("^[\\p{L}][\\p{L} .'-]{1,79}$");
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Se verifica contra este hash cuando el DNI no existe, para que el tiempo de respuesta no delate si la cuenta existe. */
    private static final String HASH_FALSO = Claves.hashear("contrasena-que-nadie-usa");

    private final UsuarioDAO usuarioDAO;
    private final LimiteIntentos limite;
    private final BitacoraService bitacora;

    public UsuarioService(UsuarioDAO usuarioDAO, LimiteIntentos limite, BitacoraService bitacora) {
        this.usuarioDAO = usuarioDAO;
        this.limite = limite;
        this.bitacora = bitacora;
    }

    /** @return el usuario autenticado; si no, lanza ReglaNegocioException (400, 401 o 429). */
    public Usuario autenticar(String dni, String contrasena, String ip) throws SQLException {
        dni = dni == null ? "" : dni.trim();
        if (!dni.matches("[0-9]{8}")) throw new ReglaNegocioException(400, "El DNI debe tener 8 dígitos.");

        String claveLimite = ip + "|" + dni;
        if (limite.estaBloqueado(claveLimite)) {
            throw new ReglaNegocioException(429, "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo.");
        }

        Optional<Usuario> usuario = usuarioDAO.buscarPorDni(dni);
        boolean valido = Claves.verificar(contrasena, usuario.isPresent() ? usuario.get().getContrasenaHash() : HASH_FALSO)
                && usuario.isPresent();
        if (valido) {
            limite.reiniciar(claveLimite);
            bitacora.registrar(usuario.get().getIdUsuario(), "LOGIN_OK", null, ip);
            return usuario.get();
        }
        limite.registrarFallo(claveLimite);
        bitacora.registrar(usuario.map(Usuario::getIdUsuario).orElse(null), "LOGIN_FALLIDO", null, ip);
        throw new ReglaNegocioException(401, "DNI o contraseña incorrectos.");
    }

    /** Crea una cuenta de FELIGRÉS. El personal no se registra por aquí. */
    public Usuario registrarFeligres(String dni, String nombre, String apellido, String correo, String telefono,
                                     String contrasena, String ubicacion, String ip) throws SQLException {
        dni = limpio(dni);
        nombre = limpio(nombre);
        apellido = limpio(apellido);
        correo = limpio(correo).toLowerCase();
        telefono = limpio(telefono);
        ubicacion = limpio(ubicacion);

        if (!dni.matches("[0-9]{8}")) throw new ReglaNegocioException(400, "El DNI debe tener 8 dígitos.");
        if (!NOMBRE.matcher(nombre).matches()) throw new ReglaNegocioException(400, "Ingresa tus nombres (solo letras).");
        if (!NOMBRE.matcher(apellido).matches()) throw new ReglaNegocioException(400, "Ingresa tus apellidos (solo letras).");
        if (correo.length() > 120 || !CORREO.matcher(correo).matches()) throw new ReglaNegocioException(400, "Ingresa un correo válido.");
        if (!telefono.isEmpty() && !telefono.matches("[0-9]{7,9}")) throw new ReglaNegocioException(400, "El teléfono debe tener de 7 a 9 dígitos.");
        if (contrasena == null || contrasena.length() < 6 || contrasena.length() > 100) {
            throw new ReglaNegocioException(400, "La contraseña debe tener entre 6 y 100 caracteres.");
        }
        if (ubicacion.length() > 150) ubicacion = ubicacion.substring(0, 150);

        Usuario nuevo = new Usuario(dni, nombre, apellido, correo,
                telefono.isEmpty() ? null : telefono,
                Claves.hashear(contrasena), Sesion.ROL_FELIGRES,
                ubicacion.isEmpty() ? null : ubicacion);
        try {
            usuarioDAO.insertar(nuevo);
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState()) || e.getErrorCode() == 1062) {
                String m = String.valueOf(e.getMessage());
                throw new ReglaNegocioException(409, m.contains("uq_usuario_dni")
                        ? "Ya existe una cuenta registrada con ese DNI."
                        : "Ya existe una cuenta registrada con ese correo.");
            }
            throw e;
        }
        bitacora.registrar(nuevo.getIdUsuario(), "USUARIO_REGISTRADO", null, ip);
        return nuevo;
    }

    private static String limpio(String t) {
        return t == null ? "" : t.trim();
    }
}
