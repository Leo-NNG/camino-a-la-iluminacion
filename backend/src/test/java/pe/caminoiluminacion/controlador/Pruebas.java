package pe.caminoiluminacion.controlador;

import jakarta.servlet.http.*;
import pe.caminoiluminacion.dao.*;
import pe.caminoiluminacion.filtro.FiltroAdmin;
import pe.caminoiluminacion.modelo.*;
import pe.caminoiluminacion.util.*;
import pe.caminoiluminacion.servicio.*;

import java.io.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

public class Pruebas {
    static int ok = 0, fail = 0;
    static final List<String> FALLAS = new ArrayList<>();
    static void check(boolean c, String n) { if (c) ok++; else { fail++; FALLAS.add(n); System.out.println("FALLA: " + n); } }

    // ---- Mocks de la API de servlets
    public static class Resp { int status = 200; StringWriter cuerpo = new StringWriter(); String redirect; boolean cadena; }
    public static class Ses { Map<String,Object> a = new HashMap<>(); boolean vivo = true; }
    public static class Contenedor { Ses actual; }   // simula la cookie de sesión

    static HttpServletRequest req(Contenedor c, Map<String,String> p, String uri) {
        return (HttpServletRequest) Proxy.newProxyInstance(Pruebas.class.getClassLoader(), new Class[]{HttpServletRequest.class}, (o, m, args) -> {
            switch (m.getName()) {
                case "getParameter": return p.get(args[0]);
                case "getRemoteAddr": return "1.2.3.4";
                case "getRequestURI": return uri;
                case "getContextPath": return "/app";
                case "setCharacterEncoding": return null;
                case "getSession": {
                    boolean crear = (Boolean) args[0];
                    if (c.actual == null || !c.actual.vivo) { if (!crear) return null; c.actual = new Ses(); }
                    final Ses s = c.actual;
                    return Proxy.newProxyInstance(Pruebas.class.getClassLoader(), new Class[]{HttpSession.class}, (o2, m2, a2) -> {
                        switch (m2.getName()) {
                            case "getAttribute": return s.a.get(a2[0]);
                            case "setAttribute": s.a.put((String) a2[0], a2[1]); return null;
                            case "invalidate": s.vivo = false; return null;
                            default: return null;
                        }
                    });
                }
            }
            return null;
        });
    }
    static HttpServletResponse resp(Resp r) {
        PrintWriter pw = new PrintWriter(r.cuerpo);
        return (HttpServletResponse) Proxy.newProxyInstance(Pruebas.class.getClassLoader(), new Class[]{HttpServletResponse.class}, (o, m, args) -> {
            switch (m.getName()) {
                case "setStatus": r.status = (Integer) args[0]; return null;
                case "getWriter": return pw;
                case "sendRedirect": r.redirect = (String) args[0]; r.status = 302; return null;
            }
            return null;
        });
    }
    static Map<String,String> m(String... kv) { Map<String,String> x = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) x.put(kv[i], kv[i+1]); return x; }
    static String cuerpo(Resp r) { return r.cuerpo.toString(); }

    // ---- DAOs falsos en memoria
    public static class FakeUsuarios implements UsuarioDAO {
        public List<Usuario> lista = new ArrayList<>(); int sig = 1;
        public Usuario insertar(Usuario u) throws SQLException {
            for (Usuario x : lista) {
                if (x.getDni().equals(u.getDni())) throw new SQLException("Duplicate entry '" + u.getDni() + "' for key 'uq_usuario_dni'", "23000", 1062);
                if (x.getCorreo().equals(u.getCorreo())) throw new SQLException("Duplicate entry for key 'uq_usuario_correo'", "23000", 1062);
            }
            u.setIdUsuario(sig++); lista.add(u); return u;
        }
        public Optional<Usuario> buscarPorId(int id) { return lista.stream().filter(u -> u.getIdUsuario() == id).findFirst(); }
        public Optional<Usuario> buscarPorCorreo(String c) { return lista.stream().filter(u -> u.getCorreo().equals(c)).findFirst(); }
        public Optional<Usuario> buscarPorDni(String d) { return lista.stream().filter(u -> u.getDni().equals(d)).findFirst(); }
        public List<Usuario> listarTodos() { return lista; }
        public boolean actualizar(Usuario u) { return true; }
        public boolean eliminar(int id) { return true; }
    }
    public static class FakeReservas implements ReservaDAO {
        public List<Reserva> lista = new ArrayList<>(); int sig = 1;
        public Reserva insertar(Reserva r) { r.setIdReserva(sig++); lista.add(r); return r; }
        public Optional<Reserva> buscarPorId(int id) { return lista.stream().filter(r -> r.getIdReserva() == id).findFirst(); }
        public List<Reserva> listarPorUsuario(int id) { List<Reserva> x = new ArrayList<>(); for (Reserva r : lista) if (r.getIdUsuario() == id) x.add(r); return x; }
        public List<Reserva> listarPorEstado(String e) { return lista; }
        public List<Reserva> listarPorEstadoYParroquia(String e, int p) {
            List<Reserva> x = new ArrayList<>(); for (Reserva r : lista) if (r.getEstado().equals(e) && r.getIdParroquia() == p) x.add(r); return x; }
        public boolean actualizarEstado(int id, String e) { buscarPorId(id).get().setEstado(e); return true; }
        public boolean insertarSiDisponible(Reserva n) {
            for (Reserva x : lista) if (x.getIdParroquia().equals(n.getIdParroquia()) && x.getSacramento().equals(n.getSacramento())
                    && x.getFechaSolicitada().equals(n.getFechaSolicitada()) && !x.getEstado().equals("rechazada")) return false;
            insertar(n); return true;
        }
        public boolean resolverPendiente(int id, String e, String motivo) {
            Reserva x = buscarPorId(id).orElse(null);
            if (x == null || !x.getEstado().equals("pendiente")) return false;
            x.setEstado(e); x.setMotivoRechazo(e.equals("rechazada") ? motivo : null); return true;
        }
    }
    public static class FakeParroquias implements ParroquiaDAO {
        static Parroquia p(int id, String nombre, String sacramentos) { Parroquia x = new Parroquia(); x.setIdParroquia(id); x.setNombre(nombre); x.setSacramentosDisponibles(sacramentos); return x; }
        public List<Parroquia> todas = List.of(p(1, "Parroquia San José", "Bautizo,Matrimonio,Primera Comunión"), p(2, "Parroquia Santa Fe", "Bautizo,Confirmación"));
        public Parroquia insertar(Parroquia x) { return x; }
        public Optional<Parroquia> buscarPorId(int i) { return todas.stream().filter(x -> x.getIdParroquia() == i).findFirst(); }
        public List<Parroquia> listarTodas() { return todas; }
        public boolean actualizar(Parroquia x) { return true; }
        public boolean eliminar(int i) { return true; }
    }


    // ---- Bitácora y sacramentos falsos, y armado de servicios con DAO falsos
    public static class FakeBitacora implements BitacoraDAO {
        public List<String[]> filas = new ArrayList<>(); public boolean fallar;
        public void registrar(Integer idUsuario, String accion, String detalle, String ip) throws SQLException {
            if (fallar) throw new SQLException("bitácora caída");
            filas.add(new String[]{String.valueOf(idUsuario), accion, detalle, ip});
        }
        public boolean hay(String accion) { return filas.stream().anyMatch(f -> f[1].equals(accion)); }
        public long cuenta(String accion) { return filas.stream().filter(f -> f[1].equals(accion)).count(); }
        public String ultima(String accion) { String[] r = null; for (String[] f : filas) if (f[1].equals(accion)) r = f; return r == null ? null : String.join("|", r); }
    }
    public static class FakeSacramentos implements SacramentoDAO {
        List<Sacramento> catalogo = List.of(new Sacramento(1, "bautizo", "Bautizo"), new Sacramento(2, "matrimonio", "Matrimonio"),
                new Sacramento(3, "primera_comunion", "Primera Comunión"), new Sacramento(4, "confirmacion", "Confirmación"));
        public Map<Integer, TreeSet<Integer>> ofrece = new HashMap<>();
        public FakeSacramentos() { ofrece.put(1, new TreeSet<>(List.of(1, 2, 3))); ofrece.put(2, new TreeSet<>(List.of(1, 4))); }
        public List<Sacramento> listarTodos() { return catalogo; }
        public Optional<Sacramento> buscarPorCodigo(String c) { return catalogo.stream().filter(s -> s.getCodigo().equals(c)).findFirst(); }
        public boolean parroquiaOfrece(int p, int s) { return ofrece.getOrDefault(p, new TreeSet<>()).contains(s); }
        public List<Integer> listarIdsPorParroquia(int p) { return new ArrayList<>(ofrece.getOrDefault(p, new TreeSet<>())); }
        public void reemplazarDeParroquia(int p, List<Integer> ids) { ofrece.put(p, new TreeSet<>(ids)); }
    }
    public static final FakeBitacora BITACORA = new FakeBitacora();
    public static final BitacoraService BIT = new BitacoraService(BITACORA);
    static UsuarioService servicioUsuarios(UsuarioDAO us, LimiteIntentos lim) { return new UsuarioService(us, lim, BIT); }
    static ReservaService servicioReservas(ReservaDAO rs, UsuarioDAO us) { return new ReservaService(rs, new FakeParroquias(), new FakeSacramentos(), us, BIT); }
    static LoginServlet loginServlet(UsuarioDAO us) { return new LoginServlet(servicioUsuarios(us, new LimiteIntentos())); }

    public static Usuario usuario(String dni, String correo, String clave, String rol, Integer parroquia) {
        Usuario u = new Usuario(dni, "Ana", "Torres", correo, null, Claves.hashear(clave), rol, null);
        u.setIdParroquia(parroquia); return u;
    }

    public static void main(String[] args) throws Exception {
        ejecutar();
        System.out.println("\nResultado: " + ok + " OK, " + fail + " fallas");
        System.exit(fail > 0 ? 1 : 0);
    }

    /** Corre todos los escenarios y devuelve los mensajes de las comprobaciones que fallaron (vacío = todo bien). */
    public static List<String> ejecutar() throws Exception {
        ok = 0; fail = 0; FALLAS.clear(); BITACORA.filas.clear(); BITACORA.fallar = false;
        FakeUsuarios us = new FakeUsuarios();
        us.insertar(usuario("70000001", "ana@x.pe", "Clave123", "feligres", null));
        us.insertar(usuario("70000000", "adm@x.pe", "Admin123", "administrador", 1));
        us.insertar(usuario("70000009", "adm2@x.pe", "Admin456", "administrador", 2));
        us.insertar(usuario("70000008", "huerfano@x.pe", "Admin789", "administrador", null));

        // ===== LOGIN
        long[] t = {0};
        LimiteIntentos lim = new LimiteIntentos(3, 60_000, 120_000, () -> t[0]);
        LoginServlet login = new LoginServlet(servicioUsuarios(us, lim));
        Contenedor c = new Contenedor(); Resp r = new Resp();
        login.doPost(req(c, m("dni", "70000001", "contrasena", "Clave123"), "/app/api/login"), resp(r));
        check(r.status == 200 && cuerpo(r).contains("\"rol\":\"feligres\""), "login feligrés correcto");
        check(Sesion.esFeligres(req(c, m(), "/")) && !Sesion.esAdministrador(req(c, m(), "/")), "sesión con rol feligrés");

        r = new Resp(); c = new Contenedor();
        login.doPost(req(c, m("dni", "70000001", "contrasena", "mala"), "/"), resp(r));
        check(r.status == 401 && c.actual == null, "contraseña mala -> 401 y sin sesión");
        r = new Resp();
        login.doPost(req(c, m("dni", "99999999", "contrasena", "Clave123"), "/"), resp(r));
        String msgNoExiste = cuerpo(r);
        check(r.status == 401, "DNI inexistente -> 401");
        r = new Resp(); login.doPost(req(c, m("dni", "70000001", "contrasena", "mala"), "/"), resp(r));
        check(cuerpo(r).equals(msgNoExiste), "mismo mensaje para DNI inexistente y contraseña mala");
        // el correo ya NO sirve para entrar
        r = new Resp(); c = new Contenedor();
        login.doPost(req(c, m("correo", "ana@x.pe", "contrasena", "Clave123"), "/"), resp(r));
        check(r.status == 400 && c.actual == null, "login con correo ya no funciona");
        for (String dniMalo : new String[]{"1234567", "123456789", "abcdefgh", "7000000a", "", " "}) {
            r = new Resp(); login.doPost(req(new Contenedor(), m("dni", dniMalo, "contrasena", "x"), "/"), resp(r));
            check(r.status == 400, "DNI inválido rechazado: '" + dniMalo + "'");
        }
        // inyección en el campo dni
        r = new Resp(); login.doPost(req(new Contenedor(), m("dni", "' OR '1'='1", "contrasena", "x"), "/"), resp(r));
        check(r.status == 400, "inyección en DNI rechazada por formato");

        // fuerza bruta: 3 fallos -> bloqueo, aun con la clave correcta
        LimiteIntentos lim2 = new LimiteIntentos(3, 60_000, 120_000, () -> t[0]);
        LoginServlet login2 = new LoginServlet(servicioUsuarios(us, lim2));
        for (int i = 0; i < 3; i++) { r = new Resp(); login2.doPost(req(new Contenedor(), m("dni", "70000001", "contrasena", "x" + i), "/"), resp(r)); }
        r = new Resp(); c = new Contenedor();
        login2.doPost(req(c, m("dni", "70000001", "contrasena", "Clave123"), "/"), resp(r));
        check(r.status == 429 && c.actual == null, "bloqueo tras 3 fallos (aunque la clave sea correcta)");
        t[0] += 121_000;
        r = new Resp(); login2.doPost(req(c, m("dni", "70000001", "contrasena", "Clave123"), "/"), resp(r));
        check(r.status == 200, "se desbloquea pasado el tiempo");
        // el bloqueo es por DNI+IP: otro DNI no se ve afectado
        r = new Resp(); login2.doPost(req(new Contenedor(), m("dni", "70000000", "contrasena", "Admin123"), "/"), resp(r));
        check(r.status == 200, "otro DNI no queda bloqueado");

        // fijación de sesión: la sesión previa se invalida
        Contenedor cf = new Contenedor(); Ses previa = new Ses(); cf.actual = previa;
        r = new Resp(); login.doPost(req(cf, m("dni", "70000001", "contrasena", "Clave123"), "/"), resp(r));
        check(!previa.vivo && cf.actual != previa && cf.actual.vivo, "se crea sesión nueva al iniciar sesión");

        // cuentas con el hash de relleno del seed viejo no pueden entrar
        FakeUsuarios us3 = new FakeUsuarios();
        Usuario viejo = new Usuario("70000005", "V", "V", "v@x.pe", null, "$2y$10$reemplazar_por_hash_real", "administrador", null);
        us3.insertar(viejo);
        r = new Resp(); new LoginServlet(servicioUsuarios(us3, new LimiteIntentos())).doPost(req(new Contenedor(), m("dni", "70000005", "contrasena", "$2y$10$reemplazar_por_hash_real"), "/"), resp(r));
        check(r.status == 401, "hash de relleno no permite entrar con el texto del hash");

        // ===== SESIÓN / LOGOUT
        Contenedor cs = new Contenedor(); r = new Resp();
        new SesionServlet(new FakeParroquias()).doGet(req(cs, m(), "/"), resp(r));
        check(cuerpo(r).equals("{\"autenticado\":false}"), "/api/sesion sin sesión");
        login.doPost(req(cs, m("dni", "70000000", "contrasena", "Admin123"), "/"), resp(new Resp()));
        r = new Resp(); new SesionServlet(new FakeParroquias()).doGet(req(cs, m(), "/"), resp(r));
        check(cuerpo(r).contains("\"autenticado\":true") && cuerpo(r).contains("administrador"), "/api/sesion con admin");
        check(cuerpo(r).contains("\"parroquia\":\"Parroquia San José\""), "/api/sesion del admin incluye el nombre de SU parroquia");
        new LogoutServlet(BIT).doPost(req(cs, m(), "/"), resp(new Resp()));
        r = new Resp(); new SesionServlet(new FakeParroquias()).doGet(req(cs, m(), "/"), resp(r));
        check(cuerpo(r).equals("{\"autenticado\":false}"), "logout invalida la sesión del servidor");
        Contenedor cFelS = new Contenedor(); login.doPost(req(cFelS, m("dni", "70000001", "contrasena", "Clave123"), "/"), resp(new Resp()));
        r = new Resp(); new SesionServlet(new FakeParroquias()).doGet(req(cFelS, m(), "/"), resp(r));
        check(cuerpo(r).contains("\"rol\":\"feligres\"") && !cuerpo(r).contains("parroquia"), "/api/sesion del feligrés no incluye parroquia");

        // ===== BITÁCORA (RNF09)
        check(BITACORA.hay("LOGIN_OK") && BITACORA.hay("LOGIN_FALLIDO") && BITACORA.hay("LOGOUT"), "la bitácora registra ingresos, intentos fallidos y salidas");
        check(BITACORA.filas.stream().noneMatch(f -> String.join(" ", f).matches(".*(7000000\\d|Clave123|Admin123|mala).*")), "la bitácora NO guarda DNI ni contraseñas");
        check(BITACORA.ultima("LOGIN_OK").endsWith("|1.2.3.4"), "la bitácora guarda la IP de origen");
        BITACORA.fallar = true;
        r = new Resp(); Contenedor cBit = new Contenedor();
        login.doPost(req(cBit, m("dni", "70000001", "contrasena", "Clave123"), "/"), resp(r));
        check(r.status == 200, "si la bitácora falla, el ingreso NO se cancela");
        BITACORA.fallar = false;

        // ===== REGISTRO
        FakeUsuarios us4 = new FakeUsuarios();
        RegistroServlet reg = new RegistroServlet(servicioUsuarios(us4, new LimiteIntentos()));
        Map<String,String> ok1 = m("dni", "12345678", "nombre", "María José", "apellido", "Fernández Núñez", "correo", "Maria@Ej.com", "telefono", "987654321", "contrasena", "secreto1", "ubicacion", "Chiclayo");
        r = new Resp(); reg.doPost(req(new Contenedor(), ok1, "/"), resp(r));
        check(r.status == 200 && us4.lista.size() == 1, "registro válido");
        Usuario g = us4.lista.get(0);
        check(g.getRol().equals("feligres") && g.getIdParroquia() == null && g.getCorreo().equals("maria@ej.com"), "registro crea feligrés sin parroquia, correo en minúsculas");
        check(g.getContrasenaHash().startsWith("pbkdf2$") && !g.getContrasenaHash().contains("secreto1"), "contraseña guardada con hash");
        Map<String,String> elevar = new HashMap<>(ok1); elevar.put("dni", "12345679"); elevar.put("correo", "otro@ej.com"); elevar.put("rol", "administrador"); elevar.put("idParroquia", "1");
        r = new Resp(); reg.doPost(req(new Contenedor(), elevar, "/"), resp(r));
        check(us4.lista.get(1).getRol().equals("feligres") && us4.lista.get(1).getIdParroquia() == null, "no se puede registrar como admin enviando rol=administrador");
        r = new Resp(); reg.doPost(req(new Contenedor(), ok1, "/"), resp(r));
        check(r.status == 409 && cuerpo(r).contains("DNI"), "DNI duplicado -> 409 mensaje de DNI");
        Map<String,String> mismoCorreo = new HashMap<>(ok1); mismoCorreo.put("dni", "22222222");
        r = new Resp(); reg.doPost(req(new Contenedor(), mismoCorreo, "/"), resp(r));
        check(r.status == 409 && cuerpo(r).contains("correo"), "correo duplicado -> 409 mensaje de correo");
        String[][] malos = {{"dni", "123"}, {"nombre", "<img src=x onerror=alert(1)>"}, {"apellido", ""}, {"correo", "no-es-correo"}, {"telefono", "12ab"}, {"contrasena", "123"}};
        for (String[] kv : malos) {
            Map<String,String> p = new HashMap<>(ok1); p.put("dni", "33333333"); p.put("correo", "z@z.com"); p.put(kv[0], kv[1]);
            r = new Resp(); reg.doPost(req(new Contenedor(), p, "/"), resp(r));
            check(r.status == 400, "registro rechaza " + kv[0] + "='" + kv[1] + "'");
        }
        check(us4.lista.size() == 2, "ningún registro inválido llegó a la BD");

        // ===== RESERVAS (feligrés)
        FakeReservas rs = new FakeReservas();
        ReservaServlet rsv = new ReservaServlet(servicioReservas(rs, us));
        Map<String,String> form = m("sacramento", "Primera Comunión", "id_parroquia", "1", "nombre_solicitante", "Pedrito", "fecha", java.time.LocalDate.now().plusDays(10).toString(), "notas", "");
        r = new Resp(); rsv.doPost(req(new Contenedor(), form, "/"), resp(r));
        check(r.status == 401 && rs.lista.isEmpty(), "reservar sin sesión -> 401");
        Contenedor cAdm = new Contenedor(); login.doPost(req(cAdm, m("dni", "70000000", "contrasena", "Admin123"), "/"), resp(new Resp()));
        r = new Resp(); rsv.doPost(req(cAdm, form, "/"), resp(r));
        check(r.status == 403 && rs.lista.isEmpty(), "admin no puede reservar -> 403");
        Contenedor cFel = new Contenedor(); login.doPost(req(cFel, m("dni", "70000001", "contrasena", "Clave123"), "/"), resp(new Resp()));
        r = new Resp(); rsv.doPost(req(cFel, form, "/"), resp(r));
        check(r.status == 200 && rs.lista.size() == 1, "feligrés reserva");
        Reserva creada = rs.lista.get(0);
        check(creada.getIdUsuario() == 1 && creada.getIdParroquia() == 1 && creada.getSacramento().equals("primera_comunion"), "reserva usa usuario de sesión, parroquia elegida y ENUM correcto");
        Map<String,String> pasada = new HashMap<>(form); pasada.put("fecha", "2020-01-01");
        r = new Resp(); rsv.doPost(req(cFel, pasada, "/"), resp(r));
        check(r.status == 400, "fecha pasada rechazada");
        Map<String,String> otraP = new HashMap<>(form); otraP.put("id_parroquia", "99");
        r = new Resp(); rsv.doPost(req(cFel, otraP, "/"), resp(r));
        check(r.status == 400, "parroquia inexistente rechazada");

        // ===== PANEL ADMIN: aislamiento por parroquia
        Reserva deP2 = new Reserva(1, 2, "bautizo", "Otro", java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(5)), null);
        rs.insertar(deP2);
        ListarReservasAdminServlet adm = new ListarReservasAdminServlet(servicioReservas(rs, us));
        r = new Resp(); adm.doGet(req(new Contenedor(), m(), "/"), resp(r));
        check(r.status == 401, "panel sin sesión -> 401");
        r = new Resp(); adm.doGet(req(cFel, m(), "/"), resp(r));
        check(r.status == 403 && !cuerpo(r).contains("Pedrito"), "feligrés no ve el panel -> 403 sin datos");
        r = new Resp(); adm.doGet(req(cAdm, m(), "/"), resp(r));
        check(r.status == 200 && cuerpo(r).contains("Pedrito") && !cuerpo(r).contains("Otro"), "admin de parroquia 1 solo ve sus reservas");
        Contenedor cHuer = new Contenedor(); login.doPost(req(cHuer, m("dni", "70000008", "contrasena", "Admin789"), "/"), resp(new Resp()));
        r = new Resp(); adm.doGet(req(cHuer, m(), "/"), resp(r));
        check(r.status == 403, "admin sin parroquia -> 403");
        // confirmar reserva ajena
        r = new Resp(); adm.doPost(req(cAdm, m("id", "" + deP2.getIdReserva(), "estado", "confirmada"), "/"), resp(r));
        check(r.status == 404 && deP2.getEstado().equals("pendiente"), "admin no puede tocar reservas de otra parroquia");
        r = new Resp(); adm.doPost(req(cFel, m("id", "" + creada.getIdReserva(), "estado", "confirmada"), "/"), resp(r));
        check(r.status == 403 && creada.getEstado().equals("pendiente"), "feligrés no puede confirmar reservas");
        r = new Resp(); adm.doPost(req(cAdm, m("id", "" + creada.getIdReserva(), "estado", "confirmada"), "/"), resp(r));
        check(r.status == 200 && creada.getEstado().equals("confirmada"), "admin confirma reserva de su parroquia");
        r = new Resp(); adm.doPost(req(cAdm, m("id", "" + creada.getIdReserva(), "estado", "pendiente; DROP"), "/"), resp(r));
        check(r.status == 400, "estado inválido rechazado");

        // ===== PASO 2: disponibilidad de fecha
        String fechaX = java.time.LocalDate.now().plusDays(40).toString();
        Map<String,String> f1 = m("sacramento", "Bautizo", "id_parroquia", "1", "nombre_solicitante", "Bebé Uno", "fecha", fechaX, "notas", "");
        r = new Resp(); rsv.doPost(req(cFel, f1, "/"), resp(r));
        check(r.status == 200, "primera reserva de la fecha entra");
        r = new Resp(); rsv.doPost(req(cFel, f1, "/"), resp(r));
        check(r.status == 409 && cuerpo(r).contains("ya no está disponible"), "misma parroquia+sacramento+fecha -> 409");
        Map<String,String> otroSac = new HashMap<>(f1); otroSac.put("sacramento", "Matrimonio");
        r = new Resp(); rsv.doPost(req(cFel, otroSac, "/"), resp(r));
        check(r.status == 200, "otro sacramento la misma fecha sí se permite");
        Map<String,String> otraParr = new HashMap<>(f1); otraParr.put("id_parroquia", "2");
        r = new Resp(); rsv.doPost(req(cFel, otraParr, "/"), resp(r));
        check(r.status == 200, "otra parroquia la misma fecha sí se permite");
        Map<String,String> otraFecha = new HashMap<>(f1); otraFecha.put("fecha", java.time.LocalDate.now().plusDays(41).toString());
        r = new Resp(); rsv.doPost(req(cFel, otraFecha, "/"), resp(r));
        check(r.status == 200, "otra fecha sí se permite");
        Map<String,String> lejana = new HashMap<>(f1); lejana.put("fecha", java.time.LocalDate.now().plusYears(3).toString());
        r = new Resp(); rsv.doPost(req(cFel, lejana, "/"), resp(r));
        check(r.status == 400, "fecha a más de 2 años rechazada");
        Map<String,String> notasLargas = new HashMap<>(f1); notasLargas.put("fecha", java.time.LocalDate.now().plusDays(50).toString()); notasLargas.put("notas", "x".repeat(501));
        r = new Resp(); rsv.doPost(req(cFel, notasLargas, "/"), resp(r));
        check(r.status == 400, "notas de más de 500 caracteres rechazadas");

        check(BITACORA.hay("RESERVA_CREADA") && BITACORA.ultima("RESERVA_CREADA").contains("reserva "), "bitácora: reserva creada");
        // ===== PASO 4: la parroquia se elige por id y debe ofrecer el sacramento
        Map<String,String> noOfrece = new HashMap<>(f1); noOfrece.put("id_parroquia", "2"); noOfrece.put("sacramento", "Matrimonio");
        r = new Resp(); rsv.doPost(req(cFel, noOfrece, "/"), resp(r));
        check(r.status == 400 && cuerpo(r).contains("no ofrece"), "parroquia que no ofrece el sacramento -> 400");
        Map<String,String> sinTilde = new HashMap<>(f1); sinTilde.put("id_parroquia", "2"); sinTilde.put("sacramento", "confirmacion"); sinTilde.put("fecha", java.time.LocalDate.now().plusDays(70).toString());
        r = new Resp(); rsv.doPost(req(cFel, sinTilde, "/"), resp(r));
        check(r.status == 200, "Confirmación (sin tilde) sí lo ofrece la parroquia 2");
        Map<String,String> idMalo = new HashMap<>(f1); idMalo.put("id_parroquia", "abc");
        r = new Resp(); rsv.doPost(req(cFel, idMalo, "/"), resp(r));
        check(r.status == 400, "id_parroquia no numérico -> 400");
        Map<String,String> porNombre = new HashMap<>(f1); porNombre.remove("id_parroquia"); porNombre.put("parroquia", "Parroquia San José");
        r = new Resp(); rsv.doPost(req(cFel, porNombre, "/"), resp(r));
        check(r.status == 400, "ya no se acepta la parroquia por nombre");

        // ===== PASO 2: rechazo con motivo, liberación de fecha y Mis solicitudes
        Reserva bebe = rs.lista.stream().filter(x -> x.getNombreSolicitante().equals("Bebé Uno")).findFirst().get();
        r = new Resp(); adm.doPost(req(cAdm, m("id", "" + bebe.getIdReserva(), "estado", "rechazada"), "/"), resp(r));
        check(r.status == 400 && bebe.getEstado().equals("pendiente"), "rechazar sin motivo -> 400");
        r = new Resp(); adm.doPost(req(cAdm, m("id", "" + bebe.getIdReserva(), "estado", "rechazada", "motivo", "   "), "/"), resp(r));
        check(r.status == 400, "motivo en blanco -> 400");
        r = new Resp(); adm.doPost(req(cAdm, m("id", "" + bebe.getIdReserva(), "estado", "rechazada", "motivo", "x".repeat(256)), "/"), resp(r));
        check(r.status == 400, "motivo de más de 255 caracteres -> 400");
        r = new Resp(); adm.doPost(req(cAdm, m("id", "" + bebe.getIdReserva(), "estado", "rechazada", "motivo", "Falta la partida de nacimiento"), "/"), resp(r));
        check(r.status == 200 && bebe.getEstado().equals("rechazada") && "Falta la partida de nacimiento".equals(bebe.getMotivoRechazo()), "rechazo con motivo guardado");
        r = new Resp(); adm.doPost(req(cAdm, m("id", "" + bebe.getIdReserva(), "estado", "confirmada"), "/"), resp(r));
        check(r.status == 409 && bebe.getEstado().equals("rechazada"), "una solicitud ya resuelta no se puede cambiar (409)");
        r = new Resp(); rsv.doPost(req(cFel, f1, "/"), resp(r));
        check(r.status == 200, "al rechazar, la fecha queda libre otra vez");
        Reserva nueva2 = rs.lista.get(rs.lista.size() - 1);
        r = new Resp(); adm.doPost(req(cAdm, m("id", "" + nueva2.getIdReserva(), "estado", "confirmada", "motivo", "ignorado"), "/"), resp(r));
        check(r.status == 200 && nueva2.getEstado().equals("confirmada") && nueva2.getMotivoRechazo() == null, "confirmar ignora el motivo");
        r = new Resp(); adm.doPost(req(cAdm, m("id", "" + nueva2.getIdReserva(), "estado", "confirmada"), "/"), resp(r));
        check(r.status == 409, "confirmar dos veces -> 409");

        r = new Resp(); adm.doGet(req(cAdm, m(), "/"), resp(r));
        check(cuerpo(r).contains("\"contacto\":\"Ana Torres") && cuerpo(r).contains("ana@x.pe"), "el admin ve el contacto de quien solicita");

        MisReservasServlet mis = new MisReservasServlet(servicioReservas(rs, us));
        r = new Resp(); mis.doGet(req(new Contenedor(), m(), "/"), resp(r));
        check(r.status == 401, "mis-reservas sin sesión -> 401");
        r = new Resp(); mis.doGet(req(cAdm, m(), "/"), resp(r));
        check(r.status == 403, "mis-reservas como admin -> 403");
        r = new Resp(); mis.doGet(req(cFel, m(), "/"), resp(r));
        String js = cuerpo(r);
        check(r.status == 200 && js.contains("Bebé Uno") && js.contains("Falta la partida de nacimiento") && js.contains("Parroquia San José") && js.contains("\"estado\":\"rechazada\""), "feligrés ve su solicitud rechazada con el motivo y la parroquia");
        // otro feligrés no ve las de ana
        us.insertar(usuario("70000007", "otro7@x.pe", "Clave777", "feligres", null));
        Contenedor cOtro = new Contenedor(); login.doPost(req(cOtro, m("dni", "70000007", "contrasena", "Clave777"), "/"), resp(new Resp()));
        r = new Resp(); mis.doGet(req(cOtro, m("idUsuario", "1", "id_usuario", "1"), "/"), resp(r));
        check(r.status == 200 && cuerpo(r).equals("[]"), "otro feligrés no ve solicitudes ajenas (ni forzando parámetros)");

        check(BITACORA.hay("RESERVA_CONFIRMADA") && BITACORA.hay("RESERVA_RECHAZADA"), "bitácora: reservas confirmadas y rechazadas");
        check(BITACORA.cuenta("RESERVA_RECHAZADA") == 1, "bitácora: solo se registra el rechazo que sí ocurrió (no los fallidos)");

        // ===== FILTRO
        FiltroAdmin f = new FiltroAdmin();
        class Cad implements jakarta.servlet.FilterChain { boolean paso; public void doFilter(jakarta.servlet.ServletRequest a, jakarta.servlet.ServletResponse b) { paso = true; } }
        Cad cad = new Cad(); r = new Resp();
        f.doFilter(req(new Contenedor(), m(), "/app/paginas/admin/documentos.html"), resp(r), cad);
        check(!cad.paso && r.redirect != null && r.redirect.startsWith("/app/paginas/login.html?next=admin/"), "página admin sin sesión -> login");
        cad = new Cad(); r = new Resp();
        f.doFilter(req(cFel, m(), "/app/paginas/admin/reportes.html"), resp(r), cad);
        check(!cad.paso && "/app/paginas/reservas.html".equals(r.redirect), "página admin como feligrés -> reservas");
        cad = new Cad(); r = new Resp();
        f.doFilter(req(cFel, m(), "/app/api/admin/reservas"), resp(r), cad);
        check(!cad.paso && r.status == 403, "API admin como feligrés -> 403");
        cad = new Cad(); r = new Resp();
        f.doFilter(req(new Contenedor(), m(), "/app/api/admin/reservas"), resp(r), cad);
        check(!cad.paso && r.status == 401, "API admin sin sesión -> 401");
        cad = new Cad(); r = new Resp();
        f.doFilter(req(cAdm, m(), "/app/paginas/admin/documentos.html"), resp(r), cad);
        check(cad.paso, "admin pasa el filtro");

        return new ArrayList<>(FALLAS);
    }
}