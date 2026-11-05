package com.practice.spring.production.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Las comprobaciones de PackagingAndDocker: el jar YA empaquetado y la imagen construida con el Dockerfile.
 * Es "IT": Failsafe la corre después de "package". Desde v15_spring/:
 *     ./mvnw -pl part6_production verify -Dit.test=PackagedImageIT
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.MethodName.class)     // capa5 APAGA el contenedor: va al final
class PackagedImageIT {

    private static final Path JAR = findJar();

    @Container
    private static final GenericContainer<?> APP = new GenericContainer<>(
            new ImageFromDockerfile("tienda-it", true)                  // se borra al terminar
                    .withFileFromPath("Dockerfile", Path.of("Dockerfile"))
                    .withFileFromPath("target/app.jar", JAR))
            .withEnv("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,info,env")   // CAPA 2
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/actuator/health/readiness").forStatusCode(200));

    private static Path findJar() {
        try (Stream<Path> files = Files.list(Path.of("target"))) {
            return files.filter(p -> p.toString().endsWith(".jar")).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Falta el jar: correr con mvn verify"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpResponse<String> get(String path) throws Exception {
        URI uri = URI.create("http://" + APP.getHost() + ":" + APP.getMappedPort(8080) + path);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void capa1_elJarEjecutableTraeTodoAdentro() throws IOException {
        try (JarFile jar = new JarFile(JAR.toFile())) {
            Attributes manifest = jar.getManifest().getMainAttributes();
            assertThat(manifest.getValue("Main-Class")).isEqualTo("org.springframework.boot.loader.launch.JarLauncher");
            assertThat(manifest.getValue("Start-Class")).isEqualTo(
                    "com.practice.spring.production.packaging.PackagingAndDocker$PackagedShopApplication");
            long libraries = jar.stream()
                    .filter(e -> e.getName().startsWith("BOOT-INF/lib/") && e.getName().endsWith(".jar"))
                    .count();
            assertThat(libraries).isGreaterThan(40);                        // medido: 51
            assertThat(jar.getEntry("META-INF/build-info.properties")).isNotNull();
        }
    }

    @Test
    void capa2_laConfiguracionEntraPorVariablesDeEntorno() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(200);      // no estaba en el jar
        assertThat(get("/actuator/info").body()).contains("\"version\":\"1.0.0-SNAPSHOT\"");
    }

    @Test
    void capa3_elJarSeParteEnCuatroCapas() throws Exception {
        String java = ProcessHandle.current().info().command().orElseThrow();
        Process process = new ProcessBuilder(java, "-Djarmode=tools", "-jar", JAR.toString(), "list-layers")
                .redirectErrorStream(true).start();
        List<String> layers = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .lines().toList();
        assertThat(process.waitFor()).isZero();
        assertThat(layers).containsExactly("dependencies", "spring-boot-loader", "snapshot-dependencies", "application");
    }

    @Test
    void capa5_correSinRootYSeApagaEnOrden() throws Exception {
        assertThat(get("/api/status").body()).contains("\"status\":\"OK\"", "\"java\":\"25");
        assertThat(APP.execInContainer("id", "-u").getStdout().strip()).isEqualTo("1001");

        // como "docker stop": SIGTERM, y SIGKILL si no termina en 30 s
        APP.getDockerClient().stopContainerCmd(APP.getContainerId()).withTimeout(30).exec();
        Long exitCode = APP.getDockerClient().inspectContainerCmd(APP.getContainerId())
                .exec().getState().getExitCodeLong();
        assertThat(exitCode).isEqualTo(143L);                              // 128 + SIGTERM(15); 137 sería SIGKILL
        assertThat(APP.getLogs()).contains("Graceful shutdown complete");
    }
}
