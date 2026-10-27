package pe.caminoiluminacion.servicio;

import pe.caminoiluminacion.dao.AvisoDAO;
import pe.caminoiluminacion.dao.HorarioDAO;
import pe.caminoiluminacion.dao.SacramentoDAO;
import pe.caminoiluminacion.modelo.Aviso;
import pe.caminoiluminacion.modelo.Horario;
import pe.caminoiluminacion.modelo.Sacramento;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Contenido que gestiona el administrador de SU parroquia: avisos, horarios y sacramentos que ofrece. */
public class ContenidoService {

    private static final Set<String> TIPOS_AVISO = Set.of("santa_cena", "ayuno", "vigilia", "celebracion", "cambio_horario", "otro");
    private static final Set<String> DIAS = Set.of("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo");
    private static final Set<String> TIPOS_HORARIO = Set.of("misa", "actividad");

    /** Un sacramento del catálogo con la marca de si la parroquia lo ofrece. */
    public record SacramentoMarcado(Sacramento sacramento, boolean ofrece) { }

    private final AvisoDAO avisoDAO;
    private final HorarioDAO horarioDAO;
    private final SacramentoDAO sacramentoDAO;
    private final BitacoraService bitacora;

    public ContenidoService(AvisoDAO avisoDAO, HorarioDAO horarioDAO, SacramentoDAO sacramentoDAO, BitacoraService bitacora) {
        this.avisoDAO = avisoDAO;
        this.horarioDAO = horarioDAO;
        this.sacramentoDAO = sacramentoDAO;
        this.bitacora = bitacora;
    }

    // ---------------------------------------------------------------- avisos

    public List<Aviso> avisosDe(int idParroquia) throws SQLException {
        return avisoDAO.listarPorParroquia(idParroquia);
    }

    /** Publica un aviso en la parroquia DEL ADMINISTRADOR (nunca en la que diga el cliente). */
    public Aviso publicarAviso(int idAutor, int idParroquia, String titulo, String descripcion, String tipo,
                               String fechaTxt, boolean urgente, String ip) throws SQLException {
        String t = recortar(titulo), d = recortar(descripcion), tp = recortar(tipo), f = recortar(fechaTxt);
        if (t.isEmpty() || t.length() > 150) throw new ReglaNegocioException(400, "El título es obligatorio (hasta 150 caracteres).");
        if (d.isEmpty() || d.length() > 2000) throw new ReglaNegocioException(400, "La descripción es obligatoria (hasta 2000 caracteres).");
        if (!TIPOS_AVISO.contains(tp)) throw new ReglaNegocioException(400, "Elige un tipo de aviso válido.");
        Date fecha = null;
        if (!f.isEmpty()) {
            try {
                fecha = Date.valueOf(LocalDate.parse(f));
            } catch (DateTimeParseException e) {
                throw new ReglaNegocioException(400, "La fecha del evento no es válida.");
            }
        }
        Aviso nuevo = new Aviso(idParroquia, t, d, tp, urgente, fecha);
        nuevo.setIdAutor(idAutor);
        avisoDAO.insertar(nuevo);
        bitacora.registrar(idAutor, "AVISO_PUBLICADO", "aviso " + nuevo.getIdAviso(), ip);
        return nuevo;
    }

    public void eliminarAviso(int idAdmin, int idParroquia, String idTxt, String ip) throws SQLException {
        int id = entero(idTxt);
        Optional<Aviso> aviso = avisoDAO.buscarPorId(id);
        if (aviso.isEmpty() || aviso.get().getIdParroquia() != idParroquia) throw new ReglaNegocioException(404, "El aviso no existe.");
        avisoDAO.eliminar(id);
        bitacora.registrar(idAdmin, "AVISO_ELIMINADO", "aviso " + id, ip);
    }

    // ---------------------------------------------------------------- horarios

    public List<Horario> horariosDe(int idParroquia) throws SQLException {
        return horarioDAO.listarPorParroquia(idParroquia);
    }

    /** Crea (idTxt vacío) o modifica un horario de la parroquia del administrador. */
    public Horario guardarHorario(int idAdmin, int idParroquia, String idTxt, String dia, String tipo, String hora, String ip)
            throws SQLException {
        String d = recortar(dia), tp = recortar(tipo), h = recortar(hora), id = recortar(idTxt);
        if (!DIAS.contains(d)) throw new ReglaNegocioException(400, "Elige un día válido.");
        if (!TIPOS_HORARIO.contains(tp)) throw new ReglaNegocioException(400, "Elige si es una misa o una actividad.");
        if (h.isEmpty() || h.length() > 120) throw new ReglaNegocioException(400, "Indica la hora o el detalle (hasta 120 caracteres).");

        if (id.isEmpty()) {
            Horario nuevo = new Horario(idParroquia, d, h, tp);
            horarioDAO.insertar(nuevo);
            bitacora.registrar(idAdmin, "HORARIO_CREADO", "horario " + nuevo.getIdHorario(), ip);
            return nuevo;
        }
        Optional<Horario> existente = horarioDAO.buscarPorId(entero(id));
        if (existente.isEmpty() || existente.get().getIdParroquia() != idParroquia) throw new ReglaNegocioException(404, "El horario no existe.");
        Horario actual = existente.get();
        actual.setDiaSemana(d);
        actual.setTipo(tp);
        actual.setHora(h);
        horarioDAO.actualizar(actual);
        bitacora.registrar(idAdmin, "HORARIO_MODIFICADO", "horario " + actual.getIdHorario(), ip);
        return actual;
    }

    public void eliminarHorario(int idAdmin, int idParroquia, String idTxt, String ip) throws SQLException {
        int id = entero(idTxt);
        Optional<Horario> h = horarioDAO.buscarPorId(id);
        if (h.isEmpty() || h.get().getIdParroquia() != idParroquia) throw new ReglaNegocioException(404, "El horario no existe.");
        horarioDAO.eliminar(id);
        bitacora.registrar(idAdmin, "HORARIO_ELIMINADO", "horario " + id, ip);
    }

    // ---------------------------------------------------------------- sacramentos que ofrece la parroquia

    public List<SacramentoMarcado> sacramentosDe(int idParroquia) throws SQLException {
        List<Integer> ofrecidos = sacramentoDAO.listarIdsPorParroquia(idParroquia);
        List<SacramentoMarcado> lista = new ArrayList<>();
        for (Sacramento s : sacramentoDAO.listarTodos()) lista.add(new SacramentoMarcado(s, ofrecidos.contains(s.getIdSacramento())));
        return lista;
    }

    /** Reemplaza lo que ofrece la parroquia del administrador. Acepta una lista de códigos (puede estar vacía). */
    public void guardarSacramentos(int idAdmin, int idParroquia, List<String> codigos, String ip) throws SQLException {
        Set<Integer> ids = new LinkedHashSet<>();
        for (String codigo : codigos) {
            Optional<Sacramento> s = sacramentoDAO.buscarPorCodigo(recortar(codigo));
            if (s.isEmpty()) throw new ReglaNegocioException(400, "Sacramento no válido.");
            ids.add(s.get().getIdSacramento());
        }
        sacramentoDAO.reemplazarDeParroquia(idParroquia, new ArrayList<>(ids));
        bitacora.registrar(idAdmin, "SACRAMENTOS_ACTUALIZADOS", ids.size() + " sacramentos", ip);
    }

    // ---------------------------------------------------------------- auxiliares

    private static String recortar(String t) {
        return t == null ? "" : t.trim();
    }

    private static int entero(String t) {
        try {
            return Integer.parseInt(t == null ? "" : t.trim());
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(400, "Identificador no válido.");
        }
    }
}
