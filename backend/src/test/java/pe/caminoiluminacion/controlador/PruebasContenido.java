package pe.caminoiluminacion.controlador;

import pe.caminoiluminacion.dao.*;
import pe.caminoiluminacion.modelo.*;
import pe.caminoiluminacion.util.*;
import pe.caminoiluminacion.servicio.*;
import pe.caminoiluminacion.controlador.Pruebas.*;
import pe.caminoiluminacion.controlador.PruebasDocumentos.Resp;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

public class PruebasContenido {
    static int ok = 0, fail = 0;
    static final List<String> FALLAS = new ArrayList<>();
    static void check(boolean c, String n) { if (c) ok++; else { fail++; FALLAS.add(n); System.out.println("FALLA: " + n); } }
    static String cuerpo(Resp r) { return r.cuerpo.toString(); }
    static Map<String,String> m(String... kv) { return PruebasDocumentos.m(kv); }

    static class FakeAvisos implements AvisoDAO {
        List<Aviso> lista = new ArrayList<>(); int sig = 1; boolean fallar;
        public Aviso insertar(Aviso a) throws SQLException { if (fallar) throw new SQLException("x"); a.setIdAviso(sig++); a.setPublicadoEn(new java.sql.Timestamp(System.currentTimeMillis())); lista.add(a); return a; }
        public Optional<Aviso> buscarPorId(int id) { return lista.stream().filter(a -> a.getIdAviso() == id).findFirst(); }
        public List<Aviso> listarPorParroquia(int p) { List<Aviso> x = new ArrayList<>(); for (Aviso a : lista) if (a.getIdParroquia() == p) x.add(a); Collections.reverse(x); return x; }
        public List<Aviso> listarUrgentes(int p) { return listarPorParroquia(p); }
        public List<Aviso> listarUrgentesRecientes(int lim) { List<Aviso> x = new ArrayList<>(); for (int i = lista.size() - 1; i >= 0 && x.size() < lim; i--) if (lista.get(i).isUrgente()) x.add(lista.get(i)); return x; }
        public boolean eliminar(int id) { return lista.removeIf(a -> a.getIdAviso() == id); }
    }
    static class FakeHorarios implements HorarioDAO {
        List<Horario> lista = new ArrayList<>(); int sig = 1;
        public Horario insertar(Horario h) { h.setIdHorario(sig++); lista.add(h); return h; }
        public Optional<Horario> buscarPorId(int id) { return lista.stream().filter(h -> h.getIdHorario() == id).findFirst(); }
        public List<Horario> listarPorParroquia(int p) { List<Horario> x = new ArrayList<>(); for (Horario h : lista) if (h.getIdParroquia() == p) x.add(h); return x; }
        public boolean actualizar(Horario h) { return true; }
        public boolean eliminar(int id) { return lista.removeIf(h -> h.getIdHorario() == id); }
    }

    public static void main(String[] args) throws Exception {
        ejecutar();
        System.out.println("\nResultado contenido: " + ok + " OK, " + fail + " fallas");
        System.exit(fail > 0 ? 1 : 0);
    }

    /** Corre todos los escenarios y devuelve los mensajes de las comprobaciones que fallaron (vacío = todo bien). */
    public static List<String> ejecutar() throws Exception {
        ok = 0; fail = 0; FALLAS.clear(); Pruebas.BITACORA.filas.clear(); Pruebas.BITACORA.fallar = false;
        Pruebas.FakeBitacora bitacoraLimpia = Pruebas.BITACORA;
        Pruebas.FakeUsuarios us = new Pruebas.FakeUsuarios();
        us.insertar(Pruebas.usuario("70000001", "ana@x.pe", "Clave123", "feligres", null));
        us.insertar(Pruebas.usuario("70000000", "adm1@x.pe", "Admin123", "administrador", 1));
        us.insertar(Pruebas.usuario("70000009", "adm2@x.pe", "Admin456", "administrador", 2));
        us.insertar(Pruebas.usuario("70000008", "huerf@x.pe", "Admin789", "administrador", null));
        LoginServlet login = Pruebas.loginServlet(us);
        Contenedor cFel = new Contenedor(), cA1 = new Contenedor(), cA2 = new Contenedor(), cHu = new Contenedor(), anon = new Contenedor();
        for (Object[] x : new Object[][]{{cFel, "70000001", "Clave123"}, {cA1, "70000000", "Admin123"}, {cA2, "70000009", "Admin456"}, {cHu, "70000008", "Admin789"}})
            login.doPost(PruebasDocumentos.req((Contenedor) x[0], m("dni", (String) x[1], "contrasena", (String) x[2]), null, false), PruebasDocumentos.resp(new Resp()));
        Resp r;

        // ===== PARROQUIAS (público)
        Pruebas.FakeParroquias fp = new Pruebas.FakeParroquias();
        Parroquia p1 = fp.todas.get(0);
        p1.setDireccion("Av. Los Álamos 245, Chiclayo"); p1.setLatitud(new BigDecimal("-6.770100")); p1.setLongitud(new BigDecimal("-79.838000"));
        p1.setFotoUrl("https://images.pexels.com/a.jpg"); p1.setFotoInteriorUrl("javascript:alert(1)"); p1.setHorarioTramites("Lunes a viernes, 9:00 a.m. a 1:00 p.m.");
        ParroquiasServlet ps = new ParroquiasServlet(fp);
        r = new Resp(); ps.doGet(PruebasDocumentos.req(anon, m(), null, false), PruebasDocumentos.resp(r));
        String t = cuerpo(r);
        check(r.status == 200 && t.startsWith("[") && t.contains("\"nombre\":\"Parroquia San José\"") && t.contains("\"nombre\":\"Parroquia Santa Fe\""), "lista pública de parroquias (sin sesión)");
        check(t.contains("\"sacramentos\":[\"Bautizo\",\"Matrimonio\",\"Primera Comunión\"]") && t.contains("\"lat\":-6.770100") && t.contains("\"lng\":-79.838000"), "sacramentos como arreglo y coordenadas numéricas");
        check(t.contains("\"lat\":null"), "coordenadas ausentes -> null (no rompe el JSON)");
        check(t.contains("\"fotoUrl\":\"https://images.pexels.com/a.jpg\"") && t.contains("\"fotoInteriorUrl\":\"\"") && !t.contains("javascript:"), "solo se publican URLs http(s) (javascript: se descarta)");
        check(t.contains("Lunes a viernes, 9:00 a.m. a 1:00 p.m."), "horario de trámites incluido");
        r = new Resp(); ps.doGet(PruebasDocumentos.req(anon, m("id", "1"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && cuerpo(r).startsWith("{\"id\":1,") , "una parroquia por id");
        r = new Resp(); ps.doGet(PruebasDocumentos.req(anon, m("id", "99"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 404, "id inexistente -> 404");
        r = new Resp(); ps.doGet(PruebasDocumentos.req(anon, m("id", "abc"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 400, "id no numérico -> 400");

        // ===== HORARIOS (público) y AVISOS (público)
        FakeHorarios fh = new FakeHorarios(); FakeAvisos fa = new FakeAvisos();
        fh.insertar(new Horario(1, "lunes", "7:00 a.m. y 7:00 p.m.", "misa")); fh.insertar(new Horario(2, "sabado", "5:00 p.m. (vigilia)", "misa"));
        HorariosServlet hs = new HorariosServlet(fh);
        r = new Resp(); hs.doGet(PruebasDocumentos.req(anon, m("parroquia", "1"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && cuerpo(r).contains("7:00 a.m. y 7:00 p.m.") && !cuerpo(r).contains("vigilia"), "horarios públicos solo de la parroquia pedida");
        r = new Resp(); hs.doGet(PruebasDocumentos.req(anon, m(), null, false), PruebasDocumentos.resp(r));
        check(r.status == 400, "horarios sin parroquia -> 400");
        fa.insertar(new Aviso(1, "Aviso uno", "d", "otro", false, null)); fa.insertar(new Aviso(1, "Urgente A", "d", "cambio_horario", true, null));
        fa.insertar(new Aviso(2, "Urgente B", "d", "ayuno", true, java.sql.Date.valueOf("2026-10-23"))); fa.insertar(new Aviso(2, "Urgente C", "d", "vigilia", true, null)); fa.insertar(new Aviso(1, "Urgente D", "d", "otro", true, null));
        AvisosServlet avs = new AvisosServlet(fa, fp);
        r = new Resp(); avs.doGet(PruebasDocumentos.req(anon, m("parroquia", "2"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && cuerpo(r).contains("Urgente B") && cuerpo(r).contains("\"fechaEvento\":\"2026-10-23\"") && !cuerpo(r).contains("Aviso uno") && cuerpo(r).contains("Parroquia Santa Fe"), "avisos públicos de una parroquia, con fecha y nombre de parroquia");
        r = new Resp(); avs.doGet(PruebasDocumentos.req(anon, m("urgentes", "1"), null, false), PruebasDocumentos.resp(r));
        int n = cuerpo(r).split("\"titulo\"", -1).length - 1;
        check(r.status == 200 && n == 3 && !cuerpo(r).contains("Aviso uno") && cuerpo(r).contains("\"urgente\":true"), "urgentes: solo urgentes y máximo 3 (entre todas las parroquias): " + n);
        r = new Resp(); avs.doGet(PruebasDocumentos.req(anon, m(), null, false), PruebasDocumentos.resp(r));
        check(r.status == 400, "avisos sin parroquia -> 400");

        // ===== AVISOS (admin)
        FakeAvisos fa2 = new FakeAvisos();
        Pruebas.FakeSacramentos fsac = new Pruebas.FakeSacramentos();
        AvisosAdminServlet aa = new AvisosAdminServlet(new ContenidoService(fa2, new FakeHorarios(), fsac, Pruebas.BIT));
        Map<String,String> av = m("titulo", "Misa de gallo", "descripcion", "A las 12 de la noche", "tipo", "celebracion", "fecha", "2026-12-24", "urgente", "true");
        r = new Resp(); aa.doPost(PruebasDocumentos.req(anon, av, null, false), PruebasDocumentos.resp(r));
        check(r.status == 401 && fa2.lista.isEmpty(), "publicar aviso sin sesión -> 401");
        r = new Resp(); aa.doPost(PruebasDocumentos.req(cFel, av, null, false), PruebasDocumentos.resp(r));
        check(r.status == 403 && fa2.lista.isEmpty(), "feligrés no publica avisos -> 403");
        r = new Resp(); aa.doPost(PruebasDocumentos.req(cHu, av, null, false), PruebasDocumentos.resp(r));
        check(r.status == 403 && fa2.lista.isEmpty(), "admin sin parroquia no publica -> 403");
        Map<String,String> conOtraParroquia = new HashMap<>(av); conOtraParroquia.put("id_parroquia", "2"); conOtraParroquia.put("idParroquia", "2");
        r = new Resp(); aa.doPost(PruebasDocumentos.req(cA1, conOtraParroquia, null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && fa2.lista.size() == 1 && fa2.lista.get(0).getIdParroquia() == 1 && fa2.lista.get(0).isUrgente() && fa2.lista.get(0).getFechaEvento().toString().equals("2026-12-24"), "publica en SU parroquia aunque mande id_parroquia=2");
        for (String[] kv : new String[][]{{"titulo", ""}, {"titulo", "x".repeat(151)}, {"descripcion", ""}, {"descripcion", "x".repeat(2001)}, {"tipo", "hackeo"}, {"tipo", ""}, {"fecha", "31/12/2026"}, {"fecha", "2026-02-30"}}) {
            Map<String,String> mal = new HashMap<>(av); mal.put(kv[0], kv[1]);
            r = new Resp(); aa.doPost(PruebasDocumentos.req(cA1, mal, null, false), PruebasDocumentos.resp(r));
            check(r.status == 400, "aviso inválido rechazado: " + kv[0] + "='" + (kv[1].length() > 10 ? kv[1].substring(0, 10) + "…" : kv[1]) + "'");
        }
        check(fa2.lista.size() == 1, "ningún aviso inválido se guardó");
        Map<String,String> sinFecha = new HashMap<>(av); sinFecha.remove("fecha"); sinFecha.put("urgente", "false"); sinFecha.put("titulo", "Con \"comillas\" y <b>html</b>");
        r = new Resp(); aa.doPost(PruebasDocumentos.req(cA1, sinFecha, null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && fa2.lista.get(1).getFechaEvento() == null && !fa2.lista.get(1).isUrgente(), "fecha opcional; urgente=false");
        fa2.insertar(new Aviso(2, "De la parroquia 2", "d", "otro", false, null));
        r = new Resp(); aa.doGet(PruebasDocumentos.req(cA1, m(), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && cuerpo(r).contains("Misa de gallo") && !cuerpo(r).contains("De la parroquia 2") && cuerpo(r).contains("Con \\\"comillas\\\" y <b>html</b>"), "el admin lista solo los de su parroquia; comillas escapadas en el JSON");
        r = new Resp(); aa.doGet(PruebasDocumentos.req(cFel, m(), null, false), PruebasDocumentos.resp(r));
        check(r.status == 403, "feligrés no lista el panel de avisos");
        int idAjeno = fa2.lista.get(2).getIdAviso(), idPropio = fa2.lista.get(0).getIdAviso();
        r = new Resp(); aa.doDelete(PruebasDocumentos.req(cA1, m("id", "" + idAjeno), null, false), PruebasDocumentos.resp(r));
        check(r.status == 404 && fa2.buscarPorId(idAjeno).isPresent(), "no se puede eliminar el aviso de otra parroquia");
        r = new Resp(); aa.doDelete(PruebasDocumentos.req(cFel, m("id", "" + idPropio), null, false), PruebasDocumentos.resp(r));
        check(r.status == 403 && fa2.buscarPorId(idPropio).isPresent(), "feligrés no elimina avisos");
        r = new Resp(); aa.doDelete(PruebasDocumentos.req(anon, m("id", "" + idPropio), null, false), PruebasDocumentos.resp(r));
        check(r.status == 401, "eliminar sin sesión -> 401");
        r = new Resp(); aa.doDelete(PruebasDocumentos.req(cA1, m("id", "x"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 400, "id no numérico -> 400");
        r = new Resp(); aa.doDelete(PruebasDocumentos.req(cA1, m("id", "" + idPropio), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && fa2.buscarPorId(idPropio).isEmpty(), "elimina su propio aviso");
        fa2.fallar = true;
        r = new Resp(); aa.doPost(PruebasDocumentos.req(cA1, av, null, false), PruebasDocumentos.resp(r));
        check(r.status == 500 && !cuerpo(r).contains("SQL"), "error de BD -> 500 sin detalles internos");

        // ===== HORARIOS (admin)
        FakeHorarios fh2 = new FakeHorarios();
        HorariosAdminServlet ha = new HorariosAdminServlet(new ContenidoService(new FakeAvisos(), fh2, fsac, Pruebas.BIT));
        Map<String,String> hv = m("dia", "domingo", "tipo", "misa", "hora", "8:00 a.m. y 6:00 p.m.");
        r = new Resp(); ha.doPost(PruebasDocumentos.req(anon, hv, null, false), PruebasDocumentos.resp(r));
        check(r.status == 401 && fh2.lista.isEmpty(), "crear horario sin sesión -> 401");
        r = new Resp(); ha.doPost(PruebasDocumentos.req(cFel, hv, null, false), PruebasDocumentos.resp(r));
        check(r.status == 403 && fh2.lista.isEmpty(), "feligrés no crea horarios");
        r = new Resp(); ha.doPost(PruebasDocumentos.req(cHu, hv, null, false), PruebasDocumentos.resp(r));
        check(r.status == 403, "admin sin parroquia no crea horarios");
        Map<String,String> hAjeno = new HashMap<>(hv); hAjeno.put("id_parroquia", "2");
        r = new Resp(); ha.doPost(PruebasDocumentos.req(cA1, hAjeno, null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && fh2.lista.size() == 1 && fh2.lista.get(0).getIdParroquia() == 1, "crea en SU parroquia aunque mande id_parroquia=2");
        for (String[] kv : new String[][]{{"dia", "funday"}, {"dia", ""}, {"tipo", "otra"}, {"hora", ""}, {"hora", "h".repeat(121)}}) {
            Map<String,String> mal = new HashMap<>(hv); mal.put(kv[0], kv[1]);
            r = new Resp(); ha.doPost(PruebasDocumentos.req(cA1, mal, null, false), PruebasDocumentos.resp(r));
            check(r.status == 400, "horario inválido rechazado: " + kv[0] + "='" + (kv[1].length() > 10 ? "…" : kv[1]) + "'");
        }
        check(fh2.lista.size() == 1, "ningún horario inválido se guardó");
        int hid = fh2.lista.get(0).getIdHorario();
        r = new Resp(); ha.doPost(PruebasDocumentos.req(cA1, m("id", "" + hid, "dia", "sabado", "tipo", "actividad", "hora", "Catequesis — 4:00 p.m."), null, false), PruebasDocumentos.resp(r));
        Horario h0 = fh2.buscarPorId(hid).get();
        check(r.status == 200 && h0.getDiaSemana().equals("sabado") && h0.getTipo().equals("actividad") && h0.getHora().startsWith("Catequesis") && h0.getIdParroquia() == 1, "modifica su horario (sin cambiar de parroquia)");
        Horario ajeno = fh2.insertar(new Horario(2, "lunes", "6:30 a.m.", "misa"));
        r = new Resp(); ha.doPost(PruebasDocumentos.req(cA1, m("id", "" + ajeno.getIdHorario(), "dia", "martes", "tipo", "misa", "hora", "hackeado"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 404 && ajeno.getHora().equals("6:30 a.m."), "no modifica horarios de otra parroquia");
        r = new Resp(); ha.doPost(PruebasDocumentos.req(cA1, m("id", "abc", "dia", "martes", "tipo", "misa", "hora", "x"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 400, "id de horario no numérico -> 400");
        r = new Resp(); ha.doDelete(PruebasDocumentos.req(cA1, m("id", "" + ajeno.getIdHorario()), null, false), PruebasDocumentos.resp(r));
        check(r.status == 404 && fh2.buscarPorId(ajeno.getIdHorario()).isPresent(), "no elimina horarios de otra parroquia");
        r = new Resp(); ha.doDelete(PruebasDocumentos.req(cFel, m("id", "" + hid), null, false), PruebasDocumentos.resp(r));
        check(r.status == 403 && fh2.buscarPorId(hid).isPresent(), "feligrés no elimina horarios");
        r = new Resp(); ha.doGet(PruebasDocumentos.req(cA1, m(), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && cuerpo(r).contains("Catequesis") && !cuerpo(r).contains("6:30"), "el admin lista solo los horarios de su parroquia");
        r = new Resp(); ha.doDelete(PruebasDocumentos.req(cA1, m("id", "" + hid), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && fh2.buscarPorId(hid).isEmpty(), "elimina su propio horario");
        r = new Resp(); ha.doGet(PruebasDocumentos.req(cA2, m(), null, false), PruebasDocumentos.resp(r));
        check(cuerpo(r).contains("6:30"), "el admin de la parroquia 2 sí ve el suyo");

        // ===== TRAZABILIDAD de avisos/horarios
        check(fa2.lista.get(0).getIdAutor() != null && fa2.lista.get(0).getIdAutor() == 2, "el aviso guarda QUIÉN lo publicó (id_autor)");
        check(Pruebas.BITACORA.hay("AVISO_PUBLICADO") && Pruebas.BITACORA.hay("AVISO_ELIMINADO") && Pruebas.BITACORA.hay("HORARIO_CREADO") && Pruebas.BITACORA.hay("HORARIO_MODIFICADO") && Pruebas.BITACORA.hay("HORARIO_ELIMINADO"), "bitácora: publicaciones y cambios de horarios");

        // ===== SACRAMENTOS: catálogo público y administración de lo que ofrece la parroquia
        SacramentosServlet cat = new SacramentosServlet(fsac);
        r = new Resp(); cat.doGet(PruebasDocumentos.req(anon, m(), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && cuerpo(r).contains("\"codigo\":\"primera_comunion\"") && cuerpo(r).contains("\"nombre\":\"Confirmación\"") && cuerpo(r).startsWith("["), "catálogo público de sacramentos (sin sesión)");
        SacramentosAdminServlet sa = new SacramentosAdminServlet(new ContenidoService(new FakeAvisos(), new FakeHorarios(), fsac, Pruebas.BIT));
        r = new Resp(); sa.doGet(PruebasDocumentos.req(anon, m(), null, false), PruebasDocumentos.resp(r));
        check(r.status == 401, "sacramentos del panel sin sesión -> 401");
        r = new Resp(); sa.doGet(PruebasDocumentos.req(cFel, m(), null, false), PruebasDocumentos.resp(r));
        check(r.status == 403, "feligrés no administra sacramentos");
        r = new Resp(); sa.doGet(PruebasDocumentos.req(cA1, m(), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && cuerpo(r).contains("\"codigo\":\"bautizo\",\"nombre\":\"Bautizo\",\"ofrece\":true") && cuerpo(r).contains("\"codigo\":\"confirmacion\",\"nombre\":\"Confirmación\",\"ofrece\":false"), "el admin ve el catálogo marcado con lo que ofrece SU parroquia");
        r = new Resp(); sa.doPost(PruebasDocumentos.req(cA1, m("codigos", "bautizo, confirmacion"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && fsac.listarIdsPorParroquia(1).equals(List.of(1, 4)) && fsac.listarIdsPorParroquia(2).equals(List.of(1, 4)), "guarda los sacramentos de SU parroquia (la 2 no cambia)");
        r = new Resp(); sa.doPost(PruebasDocumentos.req(cA1, m("codigos", "bautizo,inventado"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 400 && fsac.listarIdsPorParroquia(1).equals(List.of(1, 4)), "un código inexistente se rechaza y no cambia nada");
        r = new Resp(); sa.doPost(PruebasDocumentos.req(cA1, m("codigos", "", "idParroquia", "2"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 200 && fsac.listarIdsPorParroquia(1).isEmpty() && fsac.listarIdsPorParroquia(2).equals(List.of(1, 4)), "lista vacía = no ofrece ninguno; ignora una parroquia mandada por el cliente");
        r = new Resp(); sa.doPost(PruebasDocumentos.req(cFel, m("codigos", "bautizo"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 403, "feligrés no puede cambiar los sacramentos");
        r = new Resp(); sa.doPost(PruebasDocumentos.req(cHu, m("codigos", "bautizo"), null, false), PruebasDocumentos.resp(r));
        check(r.status == 403, "admin sin parroquia no puede cambiar sacramentos");
        check(Pruebas.BITACORA.hay("SACRAMENTOS_ACTUALIZADOS"), "bitácora: sacramentos actualizados");

        return new ArrayList<>(FALLAS);
    }
}
