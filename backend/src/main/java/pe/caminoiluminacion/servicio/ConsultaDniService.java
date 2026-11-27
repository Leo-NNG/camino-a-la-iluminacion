package pe.caminoiluminacion.servicio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import pe.caminoiluminacion.util.LimiteIntentos;
import pe.caminoiluminacion.util.Registro;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Consulta de nombres por DNI con la API de Factiliza, para autocompletar el registro.
 *
 * <ul>
 *   <li>El token se lee SOLO de la variable de entorno {@code FACTILIZA_TOKEN}; nunca llega al navegador.</li>
 *   <li>La consulta sale desde el servidor con un tiempo de espera corto ({@link #TIEMPO_ESPERA}).</li>
 *   <li>Cada IP tiene un límite de consultas (cada consulta cuenta, encuentre o no datos).</li>
 *   <li>Es opcional: sin token o si Factiliza falla, el feligrés escribe sus nombres a mano y el registro sigue igual.</li>
 *   <li>El DNI nunca se escribe en el registro de eventos.</li>
 * </ul>
 * El cliente HTTP se inyecta para poder probarlo con un falso, sin salir a internet.
 */
public class ConsultaDniService {

    /** Endpoint de Factiliza; el DNI se agrega al final. */
    public static final String URL_FACTILIZA = "https://api.factiliza.com/v1/dni/info/";
    public static final Duration TIEMPO_ESPERA = Duration.ofSeconds(5);

    private static final Pattern DNI = Pattern.compile("^\\d{8}$");

    /** Respuesta HTTP mínima: código y cuerpo. */
    public record Respuesta(int estado, String cuerpo) { }

    /** Cliente HTTP inyectable: en producción usa java.net.http; en las pruebas, un falso. */
    @FunctionalInterface
    public interface ClienteHttp {
        Respuesta get(URI uri, String token, Duration tiempoEspera) throws IOException, InterruptedException;
    }

    /** Nombres tal como los devuelve el RENIEC vía Factiliza (normalmente en mayúsculas). */
    public record DatosDni(String nombres, String apellidos) { }

    private final ClienteHttp cliente;
    private final Supplier<String> token;
    private final LimiteIntentos limite;
    private final String urlBase;

    public ConsultaDniService(ClienteHttp cliente, Supplier<String> token, LimiteIntentos limite) {
        this(cliente, token, limite, URL_FACTILIZA);
    }

    ConsultaDniService(ClienteHttp cliente, Supplier<String> token, LimiteIntentos limite, String urlBase) {
        this.cliente = cliente;
        this.token = token;
        this.limite = limite;
        this.urlBase = urlBase;
    }

    /** Cliente real con java.net.http (sin librerías extra). */
    public static ClienteHttp clienteJdk() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(TIEMPO_ESPERA)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return (uri, tokenActual, espera) -> {
            HttpRequest solicitud = HttpRequest.newBuilder(uri)
                    .timeout(espera)
                    .header("Authorization", "Bearer " + tokenActual)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> r = http.send(solicitud, HttpResponse.BodyHandlers.ofString());
            return new Respuesta(r.statusCode(), r.body());
        };
    }

    /** Token desde la variable de entorno FACTILIZA_TOKEN (null si no está configurada). */
    public static Supplier<String> tokenDeEntorno() {
        return () -> System.getenv("FACTILIZA_TOKEN");
    }

    /** true si hay token configurado: el registro ofrece autocompletar; si no, los nombres se escriben a mano. */
    public boolean disponible() {
        String t = token.get();
        return t != null && !t.isBlank();
    }

    /**
     * @throws ReglaNegocioException 400 DNI inválido, 429 demasiadas consultas desde esa IP, 503 sin token,
     *         404 sin datos para ese DNI, 502 Factiliza no respondió bien. En todos los casos el registro sigue
     *         siendo posible escribiendo los nombres a mano.
     */
    public DatosDni consultar(String dniTxt, String ip) {
        String dni = dniTxt == null ? "" : dniTxt.trim();
        if (!DNI.matcher(dni).matches()) throw new ReglaNegocioException(400, "El DNI debe tener exactamente 8 dígitos.");
        if (!disponible()) {
            throw new ReglaNegocioException(503, "La consulta automática del DNI no está disponible. Escribe tus nombres y apellidos.");
        }
        String clave = "dni|" + ip;
        if (limite.estaBloqueado(clave)) {
            throw new ReglaNegocioException(429, "Hiciste muchas consultas seguidas. Escribe tus nombres y apellidos o inténtalo en unos minutos.");
        }
        limite.registrarFallo(clave); // cada consulta cuenta para el límite, encuentre o no datos

        Respuesta r;
        try {
            r = cliente.get(URI.create(urlBase + dni), token.get().trim(), TIEMPO_ESPERA);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw sinServicio();
        } catch (IOException | RuntimeException e) {
            Registro.aviso("La consulta de DNI a Factiliza falló: " + e.getClass().getSimpleName());
            throw sinServicio();
        }

        if (r == null) throw sinServicio();
        if (r.estado() == 404) throw sinDatos();
        if (r.estado() == 401 || r.estado() == 403) {
            Registro.aviso("Factiliza rechazó el token (HTTP " + r.estado() + "): revisa FACTILIZA_TOKEN.");
            throw sinServicio();
        }
        if (r.estado() != 200) {
            Registro.aviso("Factiliza respondió HTTP " + r.estado() + " a una consulta de DNI.");
            throw sinServicio();
        }
        return leer(r.cuerpo());
    }

    /** Espera {"success":true,"data":{"nombres":"…","apellido_paterno":"…","apellido_materno":"…"}}. */
    static DatosDni leer(String cuerpo) {
        JsonObject json;
        try {
            JsonElement e = JsonParser.parseString(cuerpo == null ? "" : cuerpo);
            if (!e.isJsonObject()) throw sinDatos();
            json = e.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw sinServicio();
        }
        if (!json.has("success") || !json.get("success").isJsonPrimitive() || !json.get("success").getAsBoolean()
                || !json.has("data") || !json.get("data").isJsonObject()) {
            throw sinDatos();
        }
        JsonObject datos = json.getAsJsonObject("data");
        String nombres = texto(datos, "nombres");
        String apellidos = (texto(datos, "apellido_paterno") + " " + texto(datos, "apellido_materno")).trim();
        if (nombres.isEmpty() || apellidos.isEmpty()) throw sinDatos();
        return new DatosDni(nombres, apellidos);
    }

    private static String texto(JsonObject datos, String campo) {
        JsonElement v = datos.get(campo);
        if (v == null || !v.isJsonPrimitive()) return "";
        String t = v.getAsString().trim().replaceAll("\\s+", " ");
        return t.length() > 80 ? t.substring(0, 80) : t; // mismo máximo que el formulario
    }

    private static ReglaNegocioException sinDatos() {
        return new ReglaNegocioException(404, "No encontramos datos para ese DNI. Escribe tus nombres y apellidos.");
    }

    private static ReglaNegocioException sinServicio() {
        return new ReglaNegocioException(502, "No pudimos consultar el DNI en este momento. Escribe tus nombres y apellidos.");
    }
}
