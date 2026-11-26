package ec.portal.ant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "portal.frontend.iniciar", havingValue = "true")
final class InicioFrontend implements DisposableBean {
    private static final String CLI = "node_modules/@angular/cli/bin/ng.js";
    private Process angular;

    @EventListener(ApplicationReadyEvent.class)
    void iniciar() throws IOException, InterruptedException {
        Path frontend = Path.of("..", "frontend").toAbsolutePath().normalize();
        if (!Files.isRegularFile(frontend.resolve(CLI))) {
            boolean windows = System.getProperty("os.name").startsWith("Windows");
            List<String> npm = windows ? List.of("cmd.exe", "/d", "/c", "npm.cmd", "ci")
                    : List.of("npm", "ci");
            if (ejecutar(frontend, npm).waitFor() != 0) {
                throw new IOException("No se pudieron instalar las dependencias de Angular con npm ci.");
            }
        }

        Path rutaLocal = frontend.resolve(".node-local");
        String node = "node";
        if (Files.isRegularFile(rutaLocal)) {
            String configurado = Files.readString(rutaLocal).strip();
            if (!configurado.isEmpty()) {
                Path ejecutable = frontend.resolve(configurado).normalize();
                if (Files.isRegularFile(ejecutable)) {
                    node = ejecutable.toString();
                }
            }
        }
        angular = ejecutar(frontend, List.of(node, CLI, "serve", "--host", "127.0.0.1",
                "--port", "4200", "--open"));
    }

    private Process ejecutar(Path directorio, List<String> comando) throws IOException {
        return new ProcessBuilder(comando).directory(directorio.toFile()).inheritIO().start();
    }

    @Override
    public void destroy() {
        if (angular != null) {
            angular.descendants().forEach(ProcessHandle::destroy);
            angular.destroy();
        }
    }
}
