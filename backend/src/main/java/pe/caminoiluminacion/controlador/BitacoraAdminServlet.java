package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.dao.BitacoraDAO.Entrada;
import pe.caminoiluminacion.servicio.BitacoraService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * Bitácora en el panel: últimas acciones del personal de la parroquia del administrador.
 * GET /api/admin/bitacora[?accion=CODIGO][&limite=N]
 * Responde [{"fecha":"2026-11-27T10:15:00","usuario":"…","accion":"PAGO_APROBADO","detalle":"pago 12","ip":"…"}].
 */
@WebServlet("/api/admin/bitacora")
public class BitacoraAdminServlet extends HttpServlet {

    private final BitacoraService bitacora;

    public BitacoraAdminServlet() {
        this(Servicios.bitacora());
    }

    BitacoraAdminServlet(BitacoraService bitacora) {
        this.bitacora = bitacora;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        Integer limite = null;
        String texto = request.getParameter("limite");
        if (texto != null && !texto.isBlank()) {
            try {
                limite = Integer.valueOf(texto.trim());
            } catch (NumberFormatException e) {
                Respuesta.error(response, out, 400, "El límite debe ser un número.");
                return;
            }
        }
        try {
            List<Entrada> lista = bitacora.recientesDelPersonal(idParroquia, request.getParameter("accion"), limite);
            out.print(json(lista));
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al leer la bitácora", e);
            Respuesta.errorInterno(response, out, "No se pudo cargar la bitácora.");
        }
    }

    static String json(List<Entrada> lista) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lista.size(); i++) {
            Entrada e = lista.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"fecha\":\"").append(e.fecha() == null ? "" : Json.esc(e.fecha().withNano(0)))
              .append("\",\"usuario\":\"").append(Json.esc(e.usuario()))
              .append("\",\"accion\":\"").append(Json.esc(e.accion()))
              .append("\",\"detalle\":\"").append(Json.esc(e.detalle()))
              .append("\",\"ip\":\"").append(Json.esc(e.ip())).append("\"}");
        }
        return sb.append(']').toString();
    }
}
