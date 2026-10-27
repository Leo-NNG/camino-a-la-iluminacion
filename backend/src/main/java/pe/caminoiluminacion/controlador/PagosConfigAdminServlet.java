package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.modelo.Arancel;
import pe.caminoiluminacion.modelo.CuentaPago;
import pe.caminoiluminacion.servicio.PagoService;
import pe.caminoiluminacion.servicio.ReglaNegocioException;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.AccesoAdmin;
import pe.caminoiluminacion.util.Json;
import pe.caminoiluminacion.util.Registro;
import pe.caminoiluminacion.util.Respuesta;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

/**
 * Panel: a dónde se paga y cuánto cuesta cada trámite, de SU parroquia.
 * GET  /api/admin/pagos/config : {cuenta:{yape,plin}, ofrendaIntencion, aranceles:[{idSacramento,sacramento,monto}]}
 * POST /api/admin/pagos/config : accion=cuenta (yape_numero, yape_titular, plin_numero, plin_titular)
 *                                accion=arancel (id_sacramento, monto)
 *                                accion=ofrenda (monto de la ofrenda de una intención de misa; vacío = no recibir en línea)
 */
@WebServlet("/api/admin/pagos/config")
public class PagosConfigAdminServlet extends HttpServlet {

    private final PagoService pagos;

    public PagosConfigAdminServlet() { this(Servicios.pagos()); }

    PagosConfigAdminServlet(PagoService pagos) { this.pagos = pagos; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        try {
            CuentaPago cuenta = pagos.cuentaDe(idParroquia);
            List<Arancel> aranceles = pagos.arancelesDe(idParroquia);
            StringBuilder json = new StringBuilder("{\"cuenta\":").append(PagoJson.cuenta(cuenta))
                    .append(",\"ofrendaIntencion\":").append(cuenta.ofrendaIntencion() == null ? "null" : cuenta.ofrendaIntencion().toPlainString())
                    .append(",\"aranceles\":[");
            for (int i = 0; i < aranceles.size(); i++) {
                Arancel a = aranceles.get(i);
                if (i > 0) json.append(",");
                json.append("{\"idSacramento\":").append(a.idSacramento()).append(",\"sacramento\":\"").append(Json.esc(a.sacramento()))
                    .append("\",\"monto\":").append(a.monto() == null ? "null" : a.monto().toPlainString()).append("}");
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print(json.append("]}"));
        } catch (SQLException e) {
            Registro.error("Error al cargar la configuración de pagos", e);
            Respuesta.errorInterno(response, out, "No se pudo cargar la configuración de pagos.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Integer idParroquia = AccesoAdmin.parroquia(request, response, out);
        if (idParroquia == null) return;
        int idAdmin = Sesion.idUsuario(request);
        String accion = request.getParameter("accion");
        try {
            if ("cuenta".equals(accion)) {
                pagos.guardarCuenta(idAdmin, idParroquia, request.getParameter("yape_numero"), request.getParameter("yape_titular"),
                        request.getParameter("plin_numero"), request.getParameter("plin_titular"), request.getRemoteAddr());
            } else if ("arancel".equals(accion)) {
                pagos.guardarArancel(idAdmin, idParroquia, request.getParameter("id_sacramento"), request.getParameter("monto"), request.getRemoteAddr());
            } else if ("ofrenda".equals(accion)) {
                pagos.guardarOfrenda(idAdmin, idParroquia, request.getParameter("monto"), request.getRemoteAddr());
            } else {
                Respuesta.error(response, out, 400, "Acción no válida.");
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            out.print("{\"exito\":true}");
        } catch (ReglaNegocioException e) {
            Respuesta.error(response, out, e);
        } catch (SQLException e) {
            Registro.error("Error al guardar la configuración de pagos", e);
            Respuesta.errorInterno(response, out, "No se pudo guardar la configuración.");
        }
    }
}
