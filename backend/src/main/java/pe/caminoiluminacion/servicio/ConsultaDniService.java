
package pe.caminoiluminacion.servicio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ConsultaDniService {

    private static final String URL =
            "https://api.factiliza.com/v1/dni/info/";

    private final String token;
    private final HttpClient cliente;

    public ConsultaDniService(String token) {
        this.token = token;

        this.cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public DatosDni consultar(String dni) {
        if (dni == null || !dni.matches("[0-9]{8}")) {
            throw new ReglaNegocioException(
                    400, "El DNI debe tener 8 dígitos.");
        }

        if (token == null || token.isBlank()) {
            throw new ReglaNegocioException(
                    503, "El servicio de consulta DNI no está configurado.");
        }

        try {
            HttpRequest solicitud = HttpRequest.newBuilder()
                    .uri(URI.create(URL + dni))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> respuesta = cliente.send(
                    solicitud,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (respuesta.statusCode() == 404) {
                throw new ReglaNegocioException(
                        404, "No se encontraron datos para ese DNI.");
            }

            if (respuesta.statusCode() == 401
                    || respuesta.statusCode() == 403) {
                throw new ReglaNegocioException(
                        502, "No se pudo autenticar con el proveedor.");
            }

            if (respuesta.statusCode() != 200) {
                throw new ReglaNegocioException(
                        502, "El proveedor no pudo completar la consulta.");
            }

            JsonObject json = JsonParser.parseString(
                    respuesta.body()).getAsJsonObject();

            if (!json.has("success")
                    || !json.get("success").getAsBoolean()
                    || !json.has("data")
                    || !json.get("data").isJsonObject()) {
                throw new ReglaNegocioException(
                        404, "No se encontraron datos para ese DNI.");
            }

            JsonObject datos = json.getAsJsonObject("data");

            String nombres = obtener(datos, "nombres");
            String paterno = obtener(datos, "apellido_paterno");
            String materno = obtener(datos, "apellido_materno");

            if (nombres.isBlank()
                    || paterno.isBlank()
                    || materno.isBlank()) {
                throw new ReglaNegocioException(
                        404, "La consulta no devolvió todos los nombres.");
            }

            return new DatosDni(
                    nombres,
                    paterno + " " + materno
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReglaNegocioException(
                    502, "La consulta fue interrumpida.");
        } catch (IOException | IllegalStateException e) {
            throw new ReglaNegocioException(
                    502, "No se pudo conectar con el servicio de DNI.");
        }
    }

    private static String obtener(JsonObject datos, String campo) {
        if (!datos.has(campo) || datos.get(campo).isJsonNull()) {
            return "";
        }
        return datos.get(campo).getAsString().trim();
    }

    public record DatosDni(String nombres, String apellidos) {
    }
}
