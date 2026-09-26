package pe.caminoiluminacion;

import pe.caminoiluminacion.dao.*;
import pe.caminoiluminacion.dao.impl.*;
import pe.caminoiluminacion.modelo.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Clase de demostración: prueba que el backend Java realmente se conecta
 * a la base de datos `camino_iluminacion` y puede leer y escribir a través
 * de los DAO, siguiendo la arquitectura en capas del Capítulo 8.1 del informe
 * (Presentación -> Lógica de negocio -> DAO -> Base de datos).
 *
 * Aquí se omite la capa de "Lógica de negocio" (Servicios) a propósito:
 * el objetivo de esta clase es solo demostrar que la capa DAO funciona
 * de punta a punta contra la base de datos real.
 */
public class PruebaConexion {

    public static void main(String[] args) {
        ParroquiaDAO parroquiaDAO = new ParroquiaDAOImpl();
        UsuarioDAO usuarioDAO = new UsuarioDAOImpl();
        ReservaDAO reservaDAO = new ReservaDAOImpl();
        DocumentoDAO documentoDAO = new DocumentoDAOImpl();
        AvisoDAO avisoDAO = new AvisoDAOImpl();
        HorarioDAO horarioDAO = new HorarioDAOImpl();

        try {
            System.out.println("== 1. Listar parroquias ==");
            List<Parroquia> parroquias = parroquiaDAO.listarTodas();
            parroquias.forEach(System.out::println);
            int idParroquiaSanJose = parroquias.stream()
                .filter(p -> p.getNombre().contains("San José"))
                .findFirst()
                .orElse(parroquias.get(0))
                .getIdParroquia();

            System.out.println("\n== 2. Horarios de la primera parroquia ==");
            horarioDAO.listarPorParroquia(idParroquiaSanJose).forEach(System.out::println);

            System.out.println("\n== 3. Avisos urgentes ==");
            avisoDAO.listarUrgentes(idParroquiaSanJose).forEach(System.out::println);

            System.out.println("\n== 4. Buscar un usuario por correo ==");
            usuarioDAO.buscarPorCorreo("maria.fernandez@example.com")
                .ifPresentOrElse(System.out::println, () -> System.out.println("No encontrado"));

            System.out.println("\n== 5. Crear una reserva nueva (prueba de escritura) ==");
            Usuario cualquiera = usuarioDAO.listarTodos().get(0);
            Reserva nueva = new Reserva(
                cualquiera.getIdUsuario(), idParroquiaSanJose, "confirmacion",
                "Prueba automática (PruebaConexion)", Date.valueOf(LocalDate.now().plusDays(30)),
                "Registro de prueba generado por PruebaConexion.java"
            );
            reservaDAO.insertar(nueva);
            System.out.println("Reserva creada: " + nueva);

            System.out.println("\n== 6. Subir un documento para esa reserva ==");
            Documento doc = new Documento(nueva.getIdReserva(), cualquiera.getIdUsuario(),
                "prueba_documento.pdf", "/uploads/prueba_documento.pdf");
            documentoDAO.insertar(doc);
            System.out.println("Documento creado: " + doc);

            System.out.println("\n== 7. Aprobar el documento (simula acción del panel admin) ==");
            documentoDAO.validar(doc.getIdDocumento());
            reservaDAO.actualizarEstado(nueva.getIdReserva(), "confirmada");
            System.out.println("Documento validado y reserva confirmada.");

            System.out.println("\n== 8. Documentos pendientes que quedan en el sistema ==");
            documentoDAO.listarPendientes().forEach(System.out::println);

            System.out.println("\n== 9. Registros de prueba creados en esta corrida ==");
            System.out.println("Reserva de prueba id=" + nueva.getIdReserva()
                + " y documento id=" + doc.getIdDocumento() + " quedaron guardados como evidencia.");

            System.out.println("\n✅ CONEXIÓN Y OPERACIONES CRUD FUNCIONANDO CORRECTAMENTE.");

        } catch (Exception e) {
            System.out.println("\n❌ ERROR: no se pudo completar la prueba.");
            e.printStackTrace();
        }
    }
}
