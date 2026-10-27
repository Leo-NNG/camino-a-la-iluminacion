package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.caminoiluminacion.servicio.BitacoraService;
import pe.caminoiluminacion.servicio.Servicios;
import pe.caminoiluminacion.util.Sesion;

import java.io.IOException;

@WebServlet("/api/logout")
public class LogoutServlet extends HttpServlet {

    private final BitacoraService bitacora;

    public LogoutServlet() {
        this(Servicios.bitacora());
    }

    LogoutServlet(BitacoraService bitacora) {
        this.bitacora = bitacora;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer idUsuario = Sesion.idUsuario(request);
        Sesion.cerrar(request);
        if (idUsuario != null) bitacora.registrar(idUsuario, "LOGOUT", null, request.getRemoteAddr());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print("{\"exito\":true}");
    }
}
