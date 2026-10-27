package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Notificacion;
import pe.caminoiluminacion.servicio.NotificacionService;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * Avisos dentro de la plataforma del feligrés (siempre los de la sesión).
 * GET  /api/notificaciones : {noLeidas, notificaciones:[...]}
 * POST /api/notificaciones : marca todas como leídas.
 */
@WebServlet("/api/notificaciones")
public class NotificacionesServlet extends HttpServlet {

    private final NotificacionService notificaciones;

    public NotificacionesServlet() { this(Servicios.notificaciones()); }

    NotificacionesServlet(NotificacionService notificaciones) { this.notificaciones = notificaciones; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return; }
        try {
            NotificacionService.Bandeja b = notificaciones.deUsuario(Sesion.idUsuario(request));
            StringBuilder json = new StringBuilder("{\"noLeidas\":").append(b.noLeidas()).append(",\"notificaciones\":[");
            List<Notificacion> lista = b.notificaciones();
            for (int i = 0; i < lista.size(); i++) {
                Notificacion n = lista.get(i);
                if (i > 0) json.append(",");
                json.append("{\"id\":").append(n.idNotificacion()).append(",\"asunto\":\"").append(Json.esc(n.asunto()))
                    .append("\",\"mensaje\":\"").append(Json.esc(n.mensaje())).append("\",\"leida\":").append(n.leida())
                    .append(",\"fecha\":\"").append(Json.fecha(n.creadaEn())).append("\"}");
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.append("]}"));
        } catch (SQLException e) {
            Registro.error("Error al listar notificaciones", e);
            Respuesta.errorInterno(response, out, "No se pudieron cargar tus notificaciones.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        if (!Sesion.hayUsuario(request)) { Respuesta.error(response, out, 401, "Inicia sesión para continuar."); return; }
        try {
            int n = notificaciones.marcarTodasLeidas(Sesion.idUsuario(request));
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true,\"marcadas\":" + n + "}");
        } catch (SQLException e) {
            Registro.error("Error al marcar notificaciones", e);
            Respuesta.errorInterno(response, out, "No se pudo actualizar tus notificaciones.");
        }
    }
}
