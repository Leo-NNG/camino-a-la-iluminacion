package pe.caminoiluminacion.dao;

import pe.caminoiluminacion.modelo.FechaDisponible;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Fechas que cada parroquia publica para reservar sacramentos. Todas las lecturas incluyen cuántas solicitudes activas la ocupan. */
public interface FechaDisponibleDAO {

    /** @throws java.sql.SQLIntegrityConstraintViolationException si esa parroquia ya publicó ese sacramento en esa fecha */
    FechaDisponible insertar(FechaDisponible f) throws SQLException;

    Optional<FechaDisponible> buscarPorId(int idFecha) throws SQLException;

    /** Fechas de la parroquia (todos los sacramentos) desde {@code desde} inclusive, ordenadas por fecha. */
    List<FechaDisponible> listarDesde(int idParroquia, LocalDate desde) throws SQLException;

    /** Fechas de un sacramento en la parroquia desde {@code desde} inclusive, ordenadas por fecha. */
    List<FechaDisponible> listarDesde(int idParroquia, int idSacramento, LocalDate desde) throws SQLException;

    /** Cambia el cupo y la hora informativa (la parroquia, el sacramento y la fecha no se modifican). */
    boolean actualizar(int idFecha, int cupo, LocalTime horaInicio) throws SQLException;

    boolean eliminar(int idFecha) throws SQLException;
}
