package ec.portal.ant;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class RedisCache {
    private static final String RETENCION_SEGUNDOS = "86400";
    private final HttpClient http;
    private final String url;
    private final String token;
    private final ObjectMapper json;

    RedisCache(HttpClient http, String url, String token, ObjectMapper json) {
        this.http = http;
        this.url = url;
        this.token = token;
        this.json = json;
    }

    Optional<ServicioAntApplication.Registro> leer(String cedula) throws InterruptedException {
        if (!configurada()) {
            return Optional.empty();
        }
        try {
            JsonNode dato = ejecutar(List.of("GET", clave(cedula)));
            if (!dato.isString()) {
                return Optional.empty();
            }
            return Optional.ofNullable(json.readValue(dato.asString(), ServicioAntApplication.Registro.class));
        } catch (IOException | JacksonException ex) {
            return Optional.empty();
        }
    }

    void guardar(String cedula, ServicioAntApplication.Registro dato) throws InterruptedException {
        if (configurada()) {
            try {
                ejecutar(List.of("SET", clave(cedula), json.writeValueAsString(dato), "EX", RETENCION_SEGUNDOS));
            } catch (IOException | JacksonException ex) {
                System.getLogger(RedisCache.class.getName()).log(System.Logger.Level.WARNING,
                        "Redis no pudo guardar el resultado de la ANT ({0}).", ex.getClass().getSimpleName());
            }
        }
    }

    private boolean configurada() {
        return !url.isBlank() && !token.isBlank();
    }

    private String clave(String cedula) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "ant:puntos:" + HexFormat.of().formatHex(mac.doFinal(cedula.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC no disponible.", ex);
        }
    }

    private JsonNode ejecutar(List<String> comando) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(comando))).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Redis no respondió correctamente.");
        }
        JsonNode datos = json.readTree(response.body());
        if (datos.hasNonNull("error") || !datos.has("result")) {
            throw new IOException("Respuesta inesperada de Redis.");
        }
        return datos.get("result");
    }
}
