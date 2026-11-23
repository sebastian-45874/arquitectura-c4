package ec.portal.sri;

import java.io.IOException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
@RestController
public class ServicioSriApplication {
    private final SriClient sri;

    public ServicioSriApplication(@Value("${sri.base-url}") String base,
                                  @Value("${sri.ruc.existe}") String ruta,
                                  @Value("${sri.ruc.detalle}") String detalle,
                                  @Value("${sri.placa.existe}") String existePlaca,
                                  @Value("${sri.vehiculo.detalle}") String detalleVehiculo,
                                  @Value("${sri.vehiculo.rubros}") String rubros,
                                  ObjectMapper json) {
        sri = new SriClient(base + ruta, base + detalle, json,
                new SriClient.RutasVehiculo(base + existePlaca, base + detalleVehiculo, base + rubros));
    }

    public static void main(String[] args) {
        SpringApplication.run(ServicioSriApplication.class, args);
    }

    @GetMapping("/api/sri/ruc/existe")
    public ResultadoRuc existe(@RequestParam("ruc") String ruc) {
        try {
            return new ResultadoRuc(sri.existe(ruc));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta interrumpida.");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El SRI no devolvio una respuesta valida.");
        }
    }

    @GetMapping("/api/sri/ruc/detalle")
    public SriClient.Contribuyente detalle(@RequestParam("ruc") String ruc) {
        try {
            var contribuyente = sri.detalle(ruc);
            if (contribuyente == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El RUC no existe.");
            }
            return contribuyente;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (UnsupportedOperationException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta interrumpida.");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El SRI no devolvió una respuesta válida.");
        }
    }

    @GetMapping("/api/sri/vehiculo/matricula")
    public SriClient.Matricula matricula(@RequestParam("placa") String placa) {
        try {
            var matricula = sri.matricula(placa);
            if (matricula == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La placa no existe.");
            }
            return matricula;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta interrumpida.");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El SRI no devolvió una respuesta válida.");
        }
    }

    public record ResultadoRuc(boolean existe) {}
}
