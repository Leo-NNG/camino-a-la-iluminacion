package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.HorarioDAO;
import pe.caminoiluminacion.dao.impl.HorarioDAOImpl;
import pe.caminoiluminacion.modelo.Horario;
import pe.caminoiluminacion.util.AccesoAdmin;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * El administrador gestiona los horarios de SU parroquia.
 * GET    /api/admin/horarios          : los de su parroquia.
 * POST   /api/admin/horarios          : crea (sin id) o modifica (con id) un horario: dia, tipo, hora.
 * DELETE /api/admin/horarios?id=N     : elimina uno de su parroquia.
 */
@WebServlet("/api/admin/horarios")
public class HorariosAdminServlet extends HttpServlet {

    private static final Set<String> DIAS = Set.of("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo");
    private static final Set<String> TIPOS = Set.of("misa", "actividad");

    private final HorarioDAO horarioDAO;

    public HorariosAdminServlet() {
        this(new HorarioDAOImpl());
    }

    HorariosAdminServlet(HorarioDAO horarioDAO) {
        this.horarioDAO = horarioDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            List<Horario> lista = horarioDAO.listarPorParroquia(idParroquia);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(HorariosServlet.aJson(lista));
        } catch (SQLException e) {
            getServletContext().log("Error al listar horarios del panel", e);
            AccesoAdmin.responder(response, out, 500, "No se pudieron cargar los horarios.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;

        String dia = valor(request, "dia");
        String tipo = valor(request, "tipo");
        String hora = valor(request, "hora");
        String idTxt = valor(request, "id");

        if (!DIAS.contains(dia)) { AccesoAdmin.responder(response, out, 400, "Elige un día válido."); return; }
        if (!TIPOS.contains(tipo)) { AccesoAdmin.responder(response, out, 400, "Elige si es una misa o una actividad."); return; }
        if (hora.isEmpty() || hora.length() > 120) { AccesoAdmin.responder(response, out, 400, "Indica la hora o el detalle (hasta 120 caracteres)."); return; }

        try {
            if (idTxt.isEmpty()) {
                Horario nuevo = new Horario(idParroquia, dia, hora, tipo); // la parroquia sale de la sesión
                horarioDAO.insertar(nuevo);
                response.setStatus(HttpServletResponse.SC_OK);
                out.print("{\"exito\":true,\"id\":" + nuevo.getIdHorario() + "}");
                return;
            }
            Optional<Horario> existente = horarioDAO.buscarPorId(Integer.parseInt(idTxt));
            if (existente.isEmpty() || !idParroquia.equals(existente.get().getIdParroquia())) {
                AccesoAdmin.responder(response, out, 404, "El horario no existe.");
                return;
            }
            Horario h = existente.get();
            h.setDiaSemana(dia);
            h.setTipo(tipo);
            h.setHora(hora);
            horarioDAO.actualizar(h);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"id\":" + h.getIdHorario() + "}");
        } catch (NumberFormatException e) {
            AccesoAdmin.responder(response, out, 400, "Identificador no válido.");
        } catch (SQLException e) {
            getServletContext().log("Error al guardar horario", e);
            AccesoAdmin.responder(response, out, 500, "No se pudo guardar el horario.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            int id = Integer.parseInt(String.valueOf(request.getParameter("id")).trim());
            Optional<Horario> h = horarioDAO.buscarPorId(id);
            if (h.isEmpty() || !idParroquia.equals(h.get().getIdParroquia())) {
                AccesoAdmin.responder(response, out, 404, "El horario no existe.");
                return;
            }
            horarioDAO.eliminar(id);
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (NumberFormatException e) {
            AccesoAdmin.responder(response, out, 400, "Identificador no válido.");
        } catch (SQLException e) {
            getServletContext().log("Error al eliminar horario", e);
            AccesoAdmin.responder(response, out, 500, "No se pudo eliminar el horario.");
        }
    }

    private static String valor(HttpServletRequest r, String nombre) {
        String v = r.getParameter(nombre);
        return v == null ? "" : v.trim();
    }
}
