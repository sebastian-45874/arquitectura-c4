package ec.portal.ant;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import java.io.StringReader;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootApplication
@RestController
public class ServicioAntApplication {
    private static final Duration VIGENCIA = Duration.ofHours(1);
    private static final Pattern PUNTOS = Pattern.compile(
            "(?iu)(?:\\bpuntos(?:\\s+(?:de\\s+(?:la\\s+)?licencia|actuales|disponibles))?|\\bsaldo\\s+de\\s+puntos)\\s*:\\s*([0-9]{1,3})(?![0-9.,])");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String consultaUrl;
    private final RedisCache cache;

    public ServicioAntApplication(@Value("${ant.consulta-url}") String consultaUrl,
                                  @Value("${ant.redis.url}") String redisUrl,
                                  @Value("${ant.redis.token}") String redisToken,
                                  ObjectMapper json) {
        this.consultaUrl = consultaUrl;
        this.cache = new RedisCache(http, redisUrl, redisToken, json);
    }

    public static void main(String[] args) {
        SpringApplication.run(ServicioAntApplication.class, args);
    }

    @PostMapping("/api/ant/licencia/puntos")
    public Resultado puntos(@RequestBody Consulta consulta) {
        String cedula = consulta.cedula();
        if (cedula == null || !cedula.matches("[0-9]{10}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cédula debe tener 10 dígitos.");
        }

        try {
            Optional<Registro> anterior = cache.leer(cedula);
            if (anterior.filter(r -> r.consultadoEn().plus(VIGENCIA).isAfter(Instant.now())).isPresent()) {
                return resultado(anterior.orElseThrow(), "CACHE", true);
            }
            Registro actualizado;
            try {
                actualizado = consultarAnt(cedula);
            } catch (IOException ex) {
                return anterior.map(r -> resultado(r, "CACHE", false)).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "La consulta de puntos no está disponible."));
            }
            cache.guardar(cedula, actualizado);
            return resultado(actualizado, "ANT", true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta interrumpida.");
        }
    }

    private Registro consultarAnt(String cedula) throws IOException, InterruptedException {
        URI uri = URI.create(consultaUrl + "?ps_tipo_identificacion=CED&ps_identificacion=" + cedula + "&ps_placa=");
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6)).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("La ANT no respondió correctamente.");
        }
        var texto = new StringBuilder();
        new ParserDelegator().parse(new StringReader(response.body()), new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleText(char[] data, int pos) {
                texto.append(data).append(' ');
            }
        }, true);
        var coincidencia = PUNTOS.matcher(texto);
        if (!coincidencia.find()) {
            throw new IOException("La ANT no devolvió un saldo identificable.");
        }
        int puntos = Integer.parseInt(coincidencia.group(1));
        if (puntos > 30) {
            throw new IOException("Saldo de puntos fuera de rango.");
        }
        return new Registro(puntos, Instant.now());
    }

    private Resultado resultado(Registro registro, String fuente, boolean vigente) {
        return new Resultado(registro.puntos(), registro.consultadoEn(), fuente, vigente);
    }

    public record Consulta(String cedula) {}
    public record Resultado(int puntos, Instant consultadoEn, String fuente, boolean vigente) {}
    record Registro(int puntos, Instant consultadoEn) {}
}
