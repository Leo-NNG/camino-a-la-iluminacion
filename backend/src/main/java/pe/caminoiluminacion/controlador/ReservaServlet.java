package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.ParroquiaDAO;
import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.impl.ParroquiaDAOImpl;
import pe.caminoiluminacion.dao.impl.ReservaDAOImpl;
import pe.caminoiluminacion.modelo.Parroquia;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

@WebServlet("/api/reservas")
public class ReservaServlet extends HttpServlet {

    /** Textos del formulario -> valores del ENUM de la tabla reserva. */
    private static final Map<String, String> SACRAMENTOS = Map.of(
            "bautizo", "bautizo",
            "matrimonio", "matrimonio",
            "primera comunión", "primera_comunion",
            "primera comunion", "primera_comunion",
            "confirmación", "confirmacion",
            "confirmacion", "confirmacion");

    private final ReservaDAO reservaDAO;
    private final ParroquiaDAO parroquiaDAO;

    public ReservaServlet() {
        this(new ReservaDAOImpl(), new ParroquiaDAOImpl());
    }

    ReservaServlet(ReservaDAO reservaDAO, ParroquiaDAO parroquiaDAO) {
        this.reservaDAO = reservaDAO;
        this.parroquiaDAO = parroquiaDAO;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();

        // La reserva pertenece al feligrés con sesión iniciada; el personal no reserva.
        if (!Sesion.hayUsuario(request)) {
            error(response, out, 401, "Inicia sesión para reservar un sacramento.");
            return;
        }
        if (!Sesion.esFeligres(request)) {
            error(response, out, 403, "Las cuentas del personal parroquial no pueden reservar sacramentos.");
            return;
        }

        String sacramentoTxt = valor(request, "sacramento");
        String sacramento = SACRAMENTOS.get(sacramentoTxt.toLowerCase());
        String nombre = valor(request, "nombre_solicitante");
        String parroquiaTxt = valor(request, "id_parroquia");
        String notas = valor(request, "notas");

        if (sacramento == null) { error(response, out, 400, "Sacramento no válido."); return; }
        if (nombre.isEmpty()) { error(response, out, 400, "Indica el nombre del solicitante."); return; }

        LocalDate fecha;
        try {
            fecha = LocalDate.parse(valor(request, "fecha"));
        } catch (DateTimeParseException e) {
            error(response, out, 400, "La fecha no es válida.");
            return;
        }
        if (!fecha.isAfter(LocalDate.now())) {
            error(response, out, 400, "La fecha debe ser futura.");
            return;
        }
        if (fecha.isAfter(LocalDate.now().plusYears(2))) {
            error(response, out, 400, "Solo se puede reservar con hasta 2 años de anticipación.");
            return;
        }
        if (nombre.length() > 150) { error(response, out, 400, "El nombre del solicitante es demasiado largo."); return; }
        if (notas.length() > 500) { error(response, out, 400, "Las notas no pueden superar los 500 caracteres."); return; }

        try {
            Parroquia parroquia = buscarParroquia(parroquiaTxt);
            if (parroquia == null) { error(response, out, 400, "Selecciona una parroquia válida."); return; }
            if (!ofrece(parroquia, sacramento)) {
                error(response, out, 400, "Esa parroquia no ofrece este sacramento. Elige otra parroquia.");
                return;
            }
            Integer idParroquia = parroquia.getIdParroquia();

            Reserva nueva = new Reserva(Sesion.idUsuario(request), idParroquia, sacramento, nombre,
                    Date.valueOf(fecha), notas.isEmpty() ? null : notas);
            if (!reservaDAO.insertarSiDisponible(nueva)) {
                error(response, out, 409, "Esa fecha ya no está disponible para ese sacramento en esa parroquia. Elige otra fecha.");
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + nueva.getIdReserva() + "}");

        } catch (SQLException e) {
            getServletContext().log("Error al guardar la reserva", e);
            error(response, out, 500, "No se pudo guardar la reserva. Intenta de nuevo.");
        }
    }

    private Parroquia buscarParroquia(String idTexto) throws SQLException {
        try {
            return parroquiaDAO.buscarPorId(Integer.parseInt(idTexto)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** ¿La parroquia ofrece ese sacramento? (compara sin tildes ni mayúsculas contra su lista de sacramentos). */
    static boolean ofrece(Parroquia p, String claveSacramento) {
        String lista = p.getSacramentosDisponibles();
        if (lista == null) return false;
        String buscado = normalizar(claveSacramento.replace('_', ' '));
        for (String item : lista.split(",")) {
            if (normalizar(item).equals(buscado)) return true;
        }
        return false;
    }

    private static String normalizar(String t) {
        return java.text.Normalizer.normalize(t.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }

    private static String valor(HttpServletRequest r, String nombre) {
        String v = r.getParameter(nombre);
        return v == null ? "" : v.trim();
    }

    private static void error(HttpServletResponse resp, PrintWriter out, int estado, String msg) {
        resp.setStatus(estado);
        out.print(Json.error(msg));
    }
}
