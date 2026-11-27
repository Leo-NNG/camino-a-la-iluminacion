package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.conexion.Conexion;
import pe.caminoiluminacion.util.Registro;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Endpoint de salud para monitoreo: GET /api/salud (público, sin sesión).
 * 200 {"estado":"ok","baseDatos":"ok","hora":"…"} si la aplicación responde y la base de datos acepta conexiones;
 * 503 {"estado":"error","baseDatos":"error","hora":"…"} si la base no responde. Nunca muestra detalles internos.
 */
@WebServlet("/api/salud")
public class SaludServlet extends HttpServlet {

    /** Comprobación de la base de datos, inyectable para las pruebas. */
    @FunctionalInterface
    interface ComprobadorBd {
        void comprobar() throws SQLException;
    }

    private final ComprobadorBd bd;
    private final Clock reloj;

    public SaludServlet() {
        this(SaludServlet::comprobarConexion, Clock.systemUTC());
    }

    SaludServlet(ComprobadorBd bd, Clock reloj) {
        this.bd = bd;
        this.reloj = reloj;
    }

    private static void comprobarConexion() throws SQLException {
        try (Connection con = Conexion.obtener()) {
            if (!con.isValid(2)) throw new SQLException("La conexión no es válida");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        boolean ok;
        try {
            bd.comprobar();
            ok = true;
        } catch (SQLException | RuntimeException e) {
            Registro.aviso("Salud: la base de datos no responde (" + e.getClass().getSimpleName() + ")");
            ok = false;
        }
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setStatus(ok ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        String estado = ok ? "ok" : "error";
        response.getWriter().print("{\"estado\":\"" + estado + "\",\"baseDatos\":\"" + estado
                + "\",\"hora\":\"" + Instant.now(reloj).truncatedTo(ChronoUnit.SECONDS) + "\"}");
    }
}
