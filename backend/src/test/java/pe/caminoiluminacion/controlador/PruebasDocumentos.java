package pe.caminoiluminacion.controlador;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.*;
import pe.caminoiluminacion.dao.*;
import pe.caminoiluminacion.modelo.*;
import pe.caminoiluminacion.util.*;
import pe.caminoiluminacion.servicio.*;
import pe.caminoiluminacion.controlador.Pruebas.*;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;

public class PruebasDocumentos {
    static int ok = 0, fail = 0;
    static final List<String> FALLAS = new ArrayList<>();
    static void check(boolean c, String n) { if (c) ok++; else { fail++; FALLAS.add(n); System.out.println("FALLA: " + n); } }

    static class Resp { int status = 200; StringWriter cuerpo = new StringWriter(); ByteArrayOutputStream bytes = new ByteArrayOutputStream(); Map<String,String> h = new HashMap<>(); String tipo; }
    static class Archivo { byte[] datos; String nombre; Archivo(byte[] d, String n) { datos = d; nombre = n; } }

    static HttpServletRequest req(Contenedor c, Map<String,String> p, Archivo arch, boolean excederTamano) {
        return (HttpServletRequest) Proxy.newProxyInstance(PruebasDocumentos.class.getClassLoader(), new Class[]{HttpServletRequest.class}, (o, m, args) -> {
            switch (m.getName()) {
                case "getParameter": return p.get(args[0]);
                case "getRemoteAddr": return "1.2.3.4";
                case "setCharacterEncoding": return null;
                case "getPart":
                    if (excederTamano) throw new IllegalStateException("max file size");
                    if (arch == null) return null;
                    return Proxy.newProxyInstance(PruebasDocumentos.class.getClassLoader(), new Class[]{Part.class}, (o2, m2, a2) -> {
                        switch (m2.getName()) {
                            case "getInputStream": return new ByteArrayInputStream(arch.datos);
                            case "getSubmittedFileName": return arch.nombre;
                            case "getSize": return (long) arch.datos.length;
                        }
                        return null;
                    });
                case "getSession": {
                    boolean crear = (Boolean) args[0];
                    if (c.actual == null || !c.actual.vivo) { if (!crear) return null; c.actual = new Ses(); }
                    final Ses s = c.actual;
                    return Proxy.newProxyInstance(PruebasDocumentos.class.getClassLoader(), new Class[]{HttpSession.class}, (o2, m2, a2) -> {
                        switch (m2.getName()) {
                            case "getAttribute": return s.a.get(a2[0]);
                            case "setAttribute": s.a.put((String) a2[0], a2[1]); return null;
                            case "invalidate": s.vivo = false; return null;
                        }
                        return null;
                    });
                }
            }
            return null;
        });
    }
    static HttpServletResponse resp(Resp r) {
        PrintWriter pw = new PrintWriter(r.cuerpo);
        return (HttpServletResponse) Proxy.newProxyInstance(PruebasDocumentos.class.getClassLoader(), new Class[]{HttpServletResponse.class}, (o, m, args) -> {
            switch (m.getName()) {
                case "setStatus": r.status = (Integer) args[0]; return null;
                case "setHeader": r.h.put((String) args[0], (String) args[1]); return null;
                case "setContentType": r.tipo = (String) args[0]; return null;
                case "getWriter": return pw;
                case "getOutputStream": return new ServletOutputStream() { public void write(int b) { r.bytes.write(b); } public void write(byte[] b, int off, int len) { r.bytes.write(b, off, len); } public boolean isReady() { return true; } public void setWriteListener(jakarta.servlet.WriteListener l) { } };
            }
            return null;
        });
    }
    static String cuerpo(Resp r) { r.cuerpo.getBuffer(); return r.cuerpo.toString(); }
    static Map<String,String> m(String... kv) { Map<String,String> x = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) x.put(kv[i], kv[i+1]); return x; }

    static byte[] pdf(int extra) { byte[] b = new byte[20 + extra]; byte[] h = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII); System.arraycopy(h, 0, b, 0, h.length); return b; }
    static final byte[] PNG = {(byte)0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A,0,0,0,0};
    static final byte[] JPG = {(byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0,0x10};

    static class FakeDocs implements DocumentoDAO {
        List<Documento> lista = new ArrayList<>(); int sig = 1; boolean fallar = false; FakeReservas reservas;
        public Documento insertar(Documento d) throws SQLException { if (fallar) throw new SQLException("BD caída"); d.setIdDocumento(sig++); d.setEstadoValidacion("pendiente"); d.setFechaEnvio(new java.sql.Timestamp(System.currentTimeMillis())); lista.add(d); return d; }
        public List<Documento> listarPorUsuario(int id) { List<Documento> x = new ArrayList<>(); for (Documento d : lista) if (d.getIdUsuario() == id) x.add(d); return x; }
        public List<Documento> listarPendientes() { return lista; }
        public Optional<Documento> buscarPorId(int id) { return lista.stream().filter(d -> d.getIdDocumento() == id).findFirst(); }
        public List<Documento> listarPendientesPorParroquia(int p) {
            List<Documento> x = new ArrayList<>();
            for (Documento d : lista) if (d.getEstadoValidacion().equals("pendiente") && d.getIdReserva() != null
                    && reservas.buscarPorId(d.getIdReserva()).map(r -> r.getIdParroquia() == p).orElse(false)) x.add(d);
            return x; }
        public boolean resolverPendiente(int id, String e, String motivo, int idValidador) {
            Documento d = buscarPorId(id).orElse(null);
            if (d == null || !d.getEstadoValidacion().equals("pendiente")) return false;
            d.setIdValidador(idValidador); d.setEstadoValidacion(e); d.setMotivoRechazo(e.equals("rechazado") ? motivo : null); return true; }
        public boolean validar(int id) { return resolverPendiente(id, "validado", null, 0); }
        public boolean rechazar(int id, String m) { return resolverPendiente(id, "rechazado", m, 0); }
    }

    static long archivosEn(Path dir) throws IOException { if (!Files.exists(dir)) return 0; try (var s = Files.list(dir)) { return s.count(); } }

    public static void main(String[] args) throws Exception {
        ejecutar();
        System.out.println("\nResultado documentos: " + ok + " OK, " + fail + " fallas");
        System.exit(fail > 0 ? 1 : 0);
    }

    /** Corre todos los escenarios y devuelve los mensajes de las comprobaciones que fallaron (vacío = todo bien). */
    public static List<String> ejecutar() throws Exception {
        ok = 0; fail = 0; FALLAS.clear(); Pruebas.BITACORA.filas.clear(); Pruebas.BITACORA.fallar = false;
        Pruebas.FakeBitacora bitacoraLimpia = Pruebas.BITACORA;
        Path dir = Files.createTempDirectory("docs_test");
        AlmacenArchivos almacen = new AlmacenArchivos(dir);

        // ===== AlmacenArchivos
        check(AlmacenArchivos.detectar(pdf(0)) == AlmacenArchivos.Tipo.PDF, "detecta PDF");
        check(AlmacenArchivos.detectar(PNG) == AlmacenArchivos.Tipo.PNG, "detecta PNG");
        check(AlmacenArchivos.detectar(JPG) == AlmacenArchivos.Tipo.JPEG, "detecta JPEG");
        String[] falsos = {"<html><script>alert(1)</script>", "MZ\u0090\u0000\u0003", "<svg onload=alert(1)>", "GIF89a....", "  %PDF-1.4", "PK\u0003\u0004"};
        for (String f : falsos) check(AlmacenArchivos.detectar(f.getBytes(StandardCharsets.ISO_8859_1)) == null, "rechaza contenido no permitido: " + f.substring(0, Math.min(8, f.length())));
        check(AlmacenArchivos.detectar(new byte[0]) == null && AlmacenArchivos.detectar(null) == null, "vacío/null no son válidos");
        String guardado = almacen.guardar(pdf(0), AlmacenArchivos.Tipo.PDF);
        check(guardado.matches("[a-f0-9]{32}\\.pdf") && almacen.resolver(guardado) != null, "se guarda con nombre aleatorio y se puede resolver");
        for (String malo : new String[]{"../x.pdf", "../../etc/passwd", "/uploads/doc1.pdf", "/etc/passwd", guardado.toUpperCase(), guardado + "/..", "a.pdf", "", null, "..\\x.pdf", guardado.replace(".pdf", ".html")})
            check(almacen.resolver(malo) == null, "resolver rechaza: " + malo);
        almacen.borrar(guardado);
        check(almacen.resolver(guardado) == null, "borrar elimina el archivo");

        // ===== datos de prueba
        Pruebas.FakeUsuarios us = new Pruebas.FakeUsuarios();
        us.insertar(Pruebas.usuario("70000001", "ana@x.pe", "Clave123", "feligres", null));
        us.insertar(Pruebas.usuario("70000002", "luis@x.pe", "Clave222", "feligres", null));
        us.insertar(Pruebas.usuario("70000000", "adm1@x.pe", "Admin123", "administrador", 1));
        us.insertar(Pruebas.usuario("70000009", "adm2@x.pe", "Admin456", "administrador", 2));
        us.insertar(Pruebas.usuario("70000008", "huerf@x.pe", "Admin789", "administrador", null));
        Pruebas.FakeReservas rs = new Pruebas.FakeReservas();
        java.sql.Date fut = java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(30));
        Reserva rAna = rs.insertar(new Reserva(1, 1, "bautizo", "Bebé Ana", fut, null));       // id 1, parroquia 1, usuario 1
        Reserva rLuis = rs.insertar(new Reserva(2, 1, "matrimonio", "Luis y Eva", fut, null)); // id 2, parroquia 1, usuario 2
        Reserva rP2 = rs.insertar(new Reserva(1, 2, "bautizo", "Bebé P2", fut, null));         // id 3, parroquia 2, usuario 1
        Reserva rRech = rs.insertar(new Reserva(1, 1, "confirmacion", "Rechazada", fut, null)); rRech.setEstado("rechazada");
        FakeDocs docs = new FakeDocs(); docs.reservas = rs;
        LoginServlet login = Pruebas.loginServlet(us);
        Contenedor cAna = new Contenedor(), cLuis = new Contenedor(), cAdm1 = new Contenedor(), cAdm2 = new Contenedor(), cHuer = new Contenedor();
        login.doPost(req(cAna, m("dni", "70000001", "contrasena", "Clave123"), null, false), resp(new Resp()));
        login.doPost(req(cLuis, m("dni", "70000002", "contrasena", "Clave222"), null, false), resp(new Resp()));
        login.doPost(req(cAdm1, m("dni", "70000000", "contrasena", "Admin123"), null, false), resp(new Resp()));
        login.doPost(req(cAdm2, m("dni", "70000009", "contrasena", "Admin456"), null, false), resp(new Resp()));
        login.doPost(req(cHuer, m("dni", "70000008", "contrasena", "Admin789"), null, false), resp(new Resp()));

        DocumentoService svc = new DocumentoService(docs, rs, new Pruebas.FakeParroquias(), us, almacen, Pruebas.BIT);
        DocumentoServlet sub = new DocumentoServlet(svc);
        Resp r;

        // ===== SUBIDA
        r = new Resp(); sub.doPost(req(new Contenedor(), m("id_reserva", "1"), new Archivo(pdf(0), "a.pdf"), false), resp(r));
        check(r.status == 401 && docs.lista.isEmpty(), "subir sin sesión -> 401");
        r = new Resp(); sub.doPost(req(cAdm1, m("id_reserva", "1"), new Archivo(pdf(0), "a.pdf"), false), resp(r));
        check(r.status == 403 && docs.lista.isEmpty(), "el personal no sube documentos -> 403");
        r = new Resp(); sub.doPost(req(cAna, m(), new Archivo(pdf(0), "a.pdf"), false), resp(r));
        check(r.status == 400, "sin id_reserva -> 400");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "abc"), new Archivo(pdf(0), "a.pdf"), false), resp(r));
        check(r.status == 400, "id_reserva no numérico -> 400");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "" + rLuis.getIdReserva()), new Archivo(pdf(0), "a.pdf"), false), resp(r));
        check(r.status == 404 && docs.lista.isEmpty(), "subir a la solicitud de OTRA persona -> 404");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "999"), new Archivo(pdf(0), "a.pdf"), false), resp(r));
        check(r.status == 404, "solicitud inexistente -> 404");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "" + rRech.getIdReserva()), new Archivo(pdf(0), "a.pdf"), false), resp(r));
        check(r.status == 409 && docs.lista.isEmpty(), "solicitud rechazada -> 409");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), null, false), resp(r));
        check(r.status == 400, "sin archivo -> 400");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(new byte[0], "a.pdf"), false), resp(r));
        check(r.status == 400, "archivo vacío -> 400");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(pdf(AlmacenArchivos.MAX_BYTES), "grande.pdf"), false), resp(r));
        check(r.status == 413 && docs.lista.isEmpty(), "más de 5 MB -> 413");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(pdf(AlmacenArchivos.MAX_BYTES - 20), "justo.pdf"), false), resp(r));
        check(r.status == 200, "exactamente 5 MB sí se acepta");
        docs.lista.clear(); for (String f : dir.toFile().list()) Files.delete(dir.resolve(f));
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(pdf(0), "x.pdf"), true), resp(r));
        check(r.status == 413, "el contenedor corta por tamaño (IllegalStateException) -> 413");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo("<html><script>alert(1)</script></html>".getBytes(), "inocente.pdf"), false), resp(r));
        check(r.status == 415 && docs.lista.isEmpty() && archivosEn(dir) == 0, "HTML disfrazado de .pdf -> 415 y no se guarda nada");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1), "foto.png"), false), resp(r));
        check(r.status == 415 && archivosEn(dir) == 0, "ejecutable disfrazado de .png -> 415");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo("<svg onload=alert(1)>".getBytes(), "x.jpg"), false), resp(r));
        check(r.status == 415, "SVG con script -> 415");

        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(PNG, "DNI.png"), false), resp(r));
        check(r.status == 200 && docs.lista.size() == 1, "PNG válido se sube");
        Documento d1 = docs.lista.get(0);
        check(d1.getTipoMime().equals("image/png") && d1.getTamanoBytes() == PNG.length && d1.getIdUsuario() == 1 && d1.getIdReserva() == 1 && d1.getEstadoValidacion().equals("pendiente"), "documento registrado con tipo, tamaño, dueño y reserva");
        check(d1.getRutaArchivo().matches("[a-f0-9]{32}\\.png") && almacen.resolver(d1.getRutaArchivo()) != null, "ruta en disco aleatoria (no el nombre del usuario)");
        check(Arrays.equals(Files.readAllBytes(almacen.resolver(d1.getRutaArchivo())), PNG), "el contenido guardado es idéntico al enviado");

        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(pdf(0), "..\\..\\Windows\\system32\\evil.pdf"), false), resp(r));
        check(r.status == 200 && docs.lista.get(1).getNombreArchivo().equals("evil.pdf"), "nombre con ruta se reduce a 'evil.pdf': " + docs.lista.get(1).getNombreArchivo());
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(pdf(0), "../../etc/passwd.pdf"), false), resp(r));
        check(docs.lista.get(2).getNombreArchivo().equals("passwd.pdf") && docs.lista.get(2).getRutaArchivo().matches("[a-f0-9]{32}\\.pdf"), "path traversal en el nombre no afecta la ruta real");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(pdf(0), "a\u0000b\nc\r.pdf"), false), resp(r));
        check(!docs.lista.get(3).getNombreArchivo().matches(".*\\p{Cntrl}.*"), "se quitan caracteres de control del nombre");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(pdf(0), "n".repeat(300) + ".pdf"), false), resp(r));
        check(docs.lista.get(4).getNombreArchivo().length() <= 120, "nombre largo se acota a 120");
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(JPG, null), false), resp(r));
        check(docs.lista.get(5).getNombreArchivo().equals("documento.jpg"), "sin nombre -> documento.jpg");

        long antes = archivosEn(dir); docs.fallar = true;
        r = new Resp(); sub.doPost(req(cAna, m("id_reserva", "1"), new Archivo(pdf(0), "falla.pdf"), false), resp(r));
        check(r.status == 500 && archivosEn(dir) == antes, "si falla la BD, el archivo recién guardado se borra (sin huérfanos)");
        docs.fallar = false;

        // cuota por usuario
        FakeDocs llenos = new FakeDocs(); llenos.reservas = rs;
        for (int i = 0; i < 50; i++) { Documento d = new Documento(1, 1, "x" + i, "x"); llenos.insertar(d); }
        DocumentoServlet subLleno = new DocumentoServlet(new DocumentoService(llenos, rs, new Pruebas.FakeParroquias(), us, almacen, Pruebas.BIT));
        long a2 = archivosEn(dir);
        r = new Resp(); subLleno.doPost(req(cAna, m("id_reserva", "1"), new Archivo(pdf(0), "n.pdf"), false), resp(r));
        check(r.status == 409 && archivosEn(dir) == a2, "tope de 50 documentos por usuario -> 409 sin guardar archivo");

        // ===== LISTADO PROPIO
        sub.doPost(req(cLuis, m("id_reserva", "" + rLuis.getIdReserva()), new Archivo(pdf(0), "luis.pdf"), false), resp(new Resp()));
        r = new Resp(); sub.doGet(req(cLuis, m("id_usuario", "1"), null, false), resp(r));
        check(r.status == 200 && cuerpo(r).contains("luis.pdf") && !cuerpo(r).contains("passwd.pdf") && cuerpo(r).contains("Parroquia San José") && cuerpo(r).contains("matrimonio"), "cada feligrés lista solo SUS documentos (aunque mande id_usuario)");
        r = new Resp(); sub.doGet(req(new Contenedor(), m(), null, false), resp(r));
        check(r.status == 401, "listar sin sesión -> 401");
        r = new Resp(); sub.doGet(req(cAdm1, m(), null, false), resp(r));
        check(r.status == 403, "el admin no usa el listado del feligrés -> 403");

        // ===== DESCARGA
        ArchivoDocumentoServlet desc = new ArchivoDocumentoServlet(svc);
        int idPng = d1.getIdDocumento();
        r = new Resp(); desc.doGet(req(cAna, m("id", "" + idPng), null, false), resp(r));
        check(r.status == 200 && Arrays.equals(r.bytes.toByteArray(), PNG) && "image/png".equals(r.tipo), "dueño descarga su archivo con el tipo correcto");
        check("nosniff".equals(r.h.get("X-Content-Type-Options")) && r.h.get("Content-Security-Policy").contains("default-src 'none'") && r.h.get("Content-Security-Policy").contains("sandbox") && r.h.get("Cache-Control").contains("no-store"), "cabeceras de seguridad (nosniff, CSP sandbox para imágenes, sin caché)");
        check(r.h.get("Content-Disposition").equals("inline; filename=\"documento-" + idPng + ".png\""), "nombre de descarga fijo y seguro (no el del usuario)");
        r = new Resp(); desc.doGet(req(cAna, m("id", "" + docs.lista.get(1).getIdDocumento()), null, false), resp(r));
        check(r.status == 200 && !r.h.get("Content-Security-Policy").contains("sandbox") && "application/pdf".equals(r.tipo), "PDF: sin sandbox (para que abra el visor) pero con default-src none");
        r = new Resp(); desc.doGet(req(new Contenedor(), m("id", "" + idPng), null, false), resp(r));
        check(r.status == 401 && r.bytes.size() == 0, "descargar sin sesión -> 401");
        r = new Resp(); desc.doGet(req(cLuis, m("id", "" + idPng), null, false), resp(r));
        check(r.status == 404 && r.bytes.size() == 0, "otro feligrés no puede abrir el archivo de Ana (404)");
        r = new Resp(); desc.doGet(req(cAdm1, m("id", "" + idPng), null, false), resp(r));
        check(r.status == 200 && Arrays.equals(r.bytes.toByteArray(), PNG), "admin de la parroquia 1 abre el documento de una solicitud de su parroquia");
        r = new Resp(); desc.doGet(req(cAdm2, m("id", "" + idPng), null, false), resp(r));
        check(r.status == 404 && r.bytes.size() == 0, "admin de OTRA parroquia no puede abrirlo (404)");
        r = new Resp(); desc.doGet(req(cHuer, m("id", "" + idPng), null, false), resp(r));
        check(r.status == 404, "admin sin parroquia no puede abrirlo");
        for (String idMalo : new String[]{"abc", "", "1;DROP", "-1", "99999"}) {
            r = new Resp(); desc.doGet(req(cAna, m("id", idMalo), null, false), resp(r));
            check(r.status == 400 || r.status == 404, "id raro '" + idMalo + "' -> " + r.status);
        }
        Documento falso = new Documento(1, 1, "viejo.pdf", "/uploads/doc1.pdf"); falso.setTipoMime("application/pdf"); docs.insertar(falso);
        r = new Resp(); desc.doGet(req(cAna, m("id", "" + falso.getIdDocumento()), null, false), resp(r));
        check(r.status == 404 && r.bytes.size() == 0, "ruta antigua tipo /uploads/doc1.pdf no se sirve");
        Documento traversal = new Documento(1, 1, "x.pdf", "../../../../etc/passwd"); traversal.setTipoMime("application/pdf"); docs.insertar(traversal);
        r = new Resp(); desc.doGet(req(cAna, m("id", "" + traversal.getIdDocumento()), null, false), resp(r));
        check(r.status == 404 && r.bytes.size() == 0, "ruta con ../ en la BD no escapa del directorio");
        Documento mimeRaro = new Documento(1, 1, "x.html", d1.getRutaArchivo()); mimeRaro.setTipoMime("text/html"); docs.insertar(mimeRaro);
        r = new Resp(); desc.doGet(req(cAna, m("id", "" + mimeRaro.getIdDocumento()), null, false), resp(r));
        check(r.status == 404 && r.bytes.size() == 0, "un tipo_mime fuera de la lista (text/html) nunca se sirve");

        // ===== PANEL ADMIN
        DocumentosAdminServlet adm = new DocumentosAdminServlet(svc);
        // documento en la parroquia 2 (solicitud rP2, de Ana)
        sub.doPost(req(cAna, m("id_reserva", "" + rP2.getIdReserva()), new Archivo(pdf(0), "p2.pdf"), false), resp(new Resp()));
        Documento dP2 = docs.lista.stream().filter(d -> d.getNombreArchivo().equals("p2.pdf")).findFirst().get();
        r = new Resp(); adm.doGet(req(new Contenedor(), m(), null, false), resp(r));
        check(r.status == 401, "panel documentos sin sesión -> 401");
        r = new Resp(); adm.doGet(req(cAna, m(), null, false), resp(r));
        check(r.status == 403 && !cuerpo(r).contains("DNI"), "feligrés no entra al panel de documentos -> 403");
        r = new Resp(); adm.doGet(req(cHuer, m(), null, false), resp(r));
        check(r.status == 403, "admin sin parroquia -> 403");
        r = new Resp(); adm.doGet(req(cAdm1, m(), null, false), resp(r));
        String lista1 = cuerpo(r);
        check(r.status == 200 && lista1.contains("DNI.png") && lista1.contains("Ana Torres") && !lista1.contains("p2.pdf"), "admin 1 ve los pendientes de SU parroquia (no los de la 2)");
        r = new Resp(); adm.doGet(req(cAdm2, m(), null, false), resp(r));
        check(cuerpo(r).contains("p2.pdf") && !cuerpo(r).contains("DNI.png"), "admin 2 ve solo los de la parroquia 2");

        r = new Resp(); adm.doPost(req(cAdm1, m("id", "" + dP2.getIdDocumento(), "estado", "validado"), null, false), resp(r));
        check(r.status == 404 && dP2.getEstadoValidacion().equals("pendiente"), "admin 1 no puede validar un documento de la parroquia 2");
        r = new Resp(); adm.doPost(req(cAna, m("id", "" + idPng, "estado", "validado"), null, false), resp(r));
        check(r.status == 403 && d1.getEstadoValidacion().equals("pendiente"), "feligrés no puede validar sus propios documentos");
        r = new Resp(); adm.doPost(req(cAdm1, m("id", "" + idPng, "estado", "rechazado"), null, false), resp(r));
        check(r.status == 400 && d1.getEstadoValidacion().equals("pendiente"), "rechazar sin motivo -> 400");
        r = new Resp(); adm.doPost(req(cAdm1, m("id", "" + idPng, "estado", "rechazado", "motivo", "x".repeat(256)), null, false), resp(r));
        check(r.status == 400, "motivo de más de 255 -> 400");
        r = new Resp(); adm.doPost(req(cAdm1, m("id", "" + idPng, "estado", "aprobado"), null, false), resp(r));
        check(r.status == 400, "estado inválido -> 400");
        r = new Resp(); adm.doPost(req(cAdm1, m("id", "" + idPng, "estado", "rechazado", "motivo", "La imagen del DNI está borrosa"), null, false), resp(r));
        check(r.status == 200 && d1.getEstadoValidacion().equals("rechazado") && "La imagen del DNI está borrosa".equals(d1.getMotivoRechazo()), "rechazo con motivo guardado");
        r = new Resp(); adm.doPost(req(cAdm1, m("id", "" + idPng, "estado", "validado"), null, false), resp(r));
        check(r.status == 409 && d1.getEstadoValidacion().equals("rechazado"), "un documento resuelto no se puede cambiar -> 409");
        r = new Resp(); sub.doGet(req(cAna, m(), null, false), resp(r));
        check(cuerpo(r).contains("La imagen del DNI está borrosa") && cuerpo(r).contains("\"estado\":\"rechazado\""), "el feligrés ve el motivo del rechazo");
        Documento dOk = docs.lista.get(1);
        r = new Resp(); adm.doPost(req(cAdm1, m("id", "" + dOk.getIdDocumento(), "estado", "validado", "motivo", "ignorado"), null, false), resp(r));
        check(r.status == 200 && dOk.getEstadoValidacion().equals("validado") && dOk.getMotivoRechazo() == null, "validar ignora el motivo");
        r = new Resp(); adm.doGet(req(cAdm1, m(), null, false), resp(r));
        check(!cuerpo(r).contains("DNI.png"), "lo ya resuelto sale de la lista de pendientes");

        // ===== TRAZABILIDAD
        check(d1.getIdValidador() != null && d1.getIdValidador() == 3 && dOk.getIdValidador() == 3, "se guarda QUIÉN validó o rechazó (id_validador = el administrador)");
        check(Pruebas.BITACORA.hay("DOCUMENTO_SUBIDO") && Pruebas.BITACORA.hay("DOCUMENTO_VALIDADO") && Pruebas.BITACORA.hay("DOCUMENTO_RECHAZADO"), "bitácora: documentos subidos, validados y rechazados");
        check(Pruebas.BITACORA.filas.stream().noneMatch(f -> String.join(" ", f).toLowerCase().contains("dni.png") || String.join(" ", f).contains("passwd")), "la bitácora no guarda nombres de archivo ni contenido");

        // limpieza
        try (var s = Files.list(dir)) { for (Path p : (Iterable<Path>) s::iterator) Files.delete(p); } Files.delete(dir);
        return new ArrayList<>(FALLAS);
    }
}
