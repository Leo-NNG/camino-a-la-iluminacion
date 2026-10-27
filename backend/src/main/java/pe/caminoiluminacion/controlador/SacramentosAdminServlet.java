package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.ContenidoService;
import pe.caminoiluminacion.servicio.ContenidoService.SacramentoMarcado;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * El administrador elige qué sacramentos ofrece SU parroquia.
 * GET  /api/admin/sacramentos                : el catálogo con la marca "ofrece" de su parroquia.
 * POST /api/admin/sacramentos?codigos=a,b,c  : reemplaza lo que ofrece (lista vacía = ninguno).
 */
@WebServlet("/api/admin/sacramentos")
public class SacramentosAdminServlet extends HttpServlet {

    private final ContenidoService contenido;

    public SacramentosAdminServlet() {
        this(Servicios.contenido());
    }

    SacramentosAdminServlet(ContenidoService contenido) {
        this.contenido = contenido;
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
            List<SacramentoMarcado> lista = contenido.sacramentosDe(idParroquia);
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                if (i > 0) json.append(",");
                json.append("{\"id\":").append(lista.get(i).sacramento().getIdSacramento())
                    .append(",\"codigo\":\"").append(Json.esc(lista.get(i).sacramento().getCodigo())).append("\"")
                    .append(",\"nombre\":\"").append(Json.esc(lista.get(i).sacramento().getNombre())).append("\"")
                    .append(",\"ofrece\":").append(lista.get(i).ofrece()).append("}");
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.append("]"));
        } catch (SQLException e) {
            Registro.error("Error al listar los sacramentos de la parroquia", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar los sacramentos.");
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
        String crudo = request.getParameter("codigos");
        List<String> codigos = new ArrayList<>();
        if (crudo != null) for (String c : crudo.split(",")) if (!c.isBlank()) codigos.add(c.trim());
        try {
            contenido.guardarSacramentos(Sesion.idUsuario(request), idParroquia, codigos, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al guardar los sacramentos de la parroquia", e);
            Respuesta.errorInterno(response, out, "No se pudieron guardar los sacramentos.");
        }
    }
}
