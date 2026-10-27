package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.servicio.DocumentoService;
import pe.caminoiluminacion.servicio.DocumentoService.DocumentoPendiente;
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
import java.util.List;

/** Panel del administrador: documentos pendientes de SU parroquia, para validarlos o rechazarlos (con motivo). */
@WebServlet("/api/admin/documentos")
public class DocumentosAdminServlet extends HttpServlet {

    private final DocumentoService documentos;

    public DocumentosAdminServlet() {
        this(Servicios.documentos());
    }

    DocumentosAdminServlet(DocumentoService documentos) {
        this.documentos = documentos;
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
            List<DocumentoPendiente> lista = documentos.pendientesDeParroquia(idParroquia);
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                Documento d = lista.get(i).documento();
                Reserva r = lista.get(i).reserva();
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\":").append(d.getIdDocumento()).append(",")
                    .append("\"feligres\":\"").append(Json.esc(lista.get(i).feligres())).append("\",")
                    .append("\"nombre\":\"").append(Json.esc(d.getNombreArchivo())).append("\",")
                    .append("\"sacramento\":\"").append(Json.esc(r == null ? "" : r.getSacramento())).append("\",")
                    .append("\"solicitante\":\"").append(Json.esc(r == null ? "" : r.getNombreSolicitante())).append("\",")
                    .append("\"fechaReserva\":\"").append(Json.fecha(r == null ? null : r.getFechaSolicitada())).append("\",")
                    .append("\"fechaEnvio\":\"").append(Json.fecha(d.getFechaEnvio())).append("\",")
                    .append("\"estado\":\"").append(Json.esc(d.getEstadoValidacion())).append("\"")
                    .append("}");
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.append("]"));
        } catch (SQLException e) {
            Registro.error("Error al listar documentos pendientes", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar los documentos.");
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
        try {
            documentos.resolver(Sesion.idUsuario(request), idParroquia, request.getParameter("id"),
                    request.getParameter("estado"), request.getParameter("motivo"), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al resolver el documento", e);
            Respuesta.errorInterno(response, out, "No se pudo actualizar el documento.");
        }
    }
}
