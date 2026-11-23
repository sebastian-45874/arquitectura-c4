package ec.portal.sri;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class SriClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4)).build();
    private final String endpoint;
    private final String detalleEndpoint;
    private final ObjectMapper json;
    private final RutasVehiculo rutasVehiculo;

    SriClient(String endpoint, String detalleEndpoint, ObjectMapper json, RutasVehiculo rutasVehiculo) {
        this.endpoint = endpoint;
        this.detalleEndpoint = detalleEndpoint;
        this.json = json;
        this.rutasVehiculo = rutasVehiculo;
    }

    boolean existe(String ruc) throws IOException, InterruptedException {
        if (ruc == null || !ruc.matches("[0-9]{13}")) {
            throw new IllegalArgumentException("El RUC debe tener 13 digitos.");
        }

        return switch (consultar(endpoint + "?numeroRuc=" + ruc).strip()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IOException("Formato inesperado del SRI.");
        };
    }

    Contribuyente detalle(String ruc) throws IOException, InterruptedException {
        if (!existe(ruc)) {
            return null;
        }
        JsonNode registros = consultarJson(detalleEndpoint + "?ruc=" + ruc);
        if (!registros.isArray()) {
            throw new IOException("Formato inesperado para el detalle del RUC.");
        }
        for (JsonNode registro : registros) {
            if (!ruc.equals(registro.path("numeroRuc").asString(""))) {
                continue;
            }
            String tipo = registro.path("tipoContribuyente").asString("").strip();
            if (tipo.isEmpty()) {
                throw new IOException("El SRI no informó el tipo de contribuyente.");
            }
            if (!"PERSONA NATURAL".equalsIgnoreCase(tipo) && !"NATURAL".equalsIgnoreCase(tipo)) {
                throw new UnsupportedOperationException("El RUC no corresponde a una persona natural.");
            }
            String nombre = registro.path("razonSocial").asString("").strip();
            if (nombre.isEmpty()) {
                throw new IOException("El SRI no informó el nombre del contribuyente.");
            }
            return new Contribuyente(ruc, nombre, tipo,
                    registro.path("estadoContribuyenteRuc").asString(
                            registro.path("estadoContribuyente").asString("")));
        }
        throw new IOException("El SRI no devolvió el detalle del RUC solicitado.");
    }

    Matricula matricula(String placa) throws IOException, InterruptedException {
        String normalizada = placa.strip().toUpperCase(Locale.ROOT).replace("-", "");
        if (!normalizada.matches("[A-Z]{2,3}[0-9]{3,4}")) {
            throw new IllegalArgumentException("Ingresa una placa válida, por ejemplo ABC-1234.");
        }

        String existe = consultar(rutasVehiculo.existe() + "?numeroPlaca=" + normalizada).strip();
        if ("false".equals(existe)) {
            return null;
        }
        if (!"true".equals(existe)) {
            throw new IOException("Formato inesperado al validar la placa.");
        }

        JsonNode vehiculo = consultarJson(rutasVehiculo.detalle() + "?numeroPlacaCampvCpn=" + normalizada);
        String codigo = vehiculo.path("codigoVehiculo").asString("");
        if (!vehiculo.isObject() || !codigo.matches("[0-9]+")) {
            throw new IOException("El SRI no devolvió un código de vehículo válido.");
        }
        JsonNode rubros = consultarJson(rutasVehiculo.rubros() + "?codigoVehiculo=" + codigo);
        if (!rubros.isObject() && !rubros.isArray()) {
            throw new IOException("Formato inesperado para los rubros de matrícula.");
        }
        return new Matricula(normalizada, vehiculo, rubros);
    }

    private JsonNode consultarJson(String url) throws IOException, InterruptedException {
        try {
            return json.readTree(consultar(url));
        } catch (tools.jackson.core.JacksonException ex) {
            throw new IOException("El SRI no devolvió JSON válido.", ex);
        }
    }

    record RutasVehiculo(String existe, String detalle, String rubros) {}
    record Matricula(String placa, JsonNode vehiculo, JsonNode rubros) {}

    private String consultar(String url) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Respuesta HTTP inesperada del SRI.");
        }
        return response.body();
    }

    record Contribuyente(String ruc, String razonSocial, String tipoContribuyente, String estado) {}
}
