package pe.caminoiluminacion.controlador;

import pe.caminoiluminacion.dao.ReservaDAO;
import pe.caminoiluminacion.dao.impl.ReservaDAOImpl;
import pe.caminoiluminacion.modelo.Reserva;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Date;

// Esta anotación define la URL a la que el JavaScript enviará los datos
@WebServlet("/api/reservas")
public class ReservaServlet extends HttpServlet {

    private ReservaDAO reservaDAO = new ReservaDAOImpl();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            // 1. Recibir los datos enviados desde la página HTML/JS
            String sacramentoStr = request.getParameter("sacramento");
            String nombreSolicitante = request.getParameter("nombre_solicitante");
            String fechaStr = request.getParameter("fecha");
            String notas = request.getParameter("notas");

            // Convertir datos (para la demo usamos usuario 1 y parroquia 1 por defecto)
            //int idSacramento = Integer.parseInt(sacramentoStr);
            int idSacramento = 1;
            Date fechaSolicitada = Date.valueOf(fechaStr);

            // 2. Crear el objeto modelo
            Reserva nuevaReserva = new Reserva(
                    1, // id_usuario simulado
                    1, // id_parroquia simulada
                    sacramentoStr, // Asumiendo que tu constructor acepta el string o el ID
                    nombreSolicitante,
                    fechaSolicitada,
                    notas
            );

            // 3. Guardar en la base de datos usando tu DAO
            reservaDAO.insertar(nuevaReserva);

            // 4. Responderle al navegador que todo salió bien
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("Reserva guardada en MySQL exitosamente");

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error al guardar en la BD: " + e.getMessage());
        }
    }
}