package com.practice.tools.support;

import com.practice.tools.support.Shell.Run;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Un proyecto de muestra en una carpeta temporal, con su propio Maven Wrapper, para correr Maven
 * "de verdad" desde una prueba y revisar su salida. Lo usan las pruebas de build/maven.
 */
public final class SampleProject {

    private static final Path TOOLS = Path.of("").toAbsolutePath();      // Surefire corre en v9_tools/

    private final Path dir;

    private SampleProject(Path dir) {
        this.dir = dir;
    }

    /** Copia el wrapper de v9_tools (mvnw y .mvn/) a la carpeta: cada proyecto trae el suyo. */
    public static SampleProject in(Path tempDir) throws IOException {
        // La ruta REAL: en Windows la carpeta temporal llega con el nombre corto (LUISCA~1) y Maven
        // compara -pl contra rutas largas; sin esto, "-pl app" no encuentra el módulo (medido).
        Path dir = tempDir.toRealPath();
        Files.copy(TOOLS.resolve("mvnw"), dir.resolve("mvnw"));
        Files.copy(TOOLS.resolve("mvnw.cmd"), dir.resolve("mvnw.cmd"));
        Files.createDirectories(dir.resolve(".mvn/wrapper"));
        Files.copy(TOOLS.resolve(".mvn/wrapper/maven-wrapper.properties"), dir.resolve(".mvn/wrapper/maven-wrapper.properties"));
        return new SampleProject(dir);
    }

    public Path dir() {
        return dir;
    }

    public SampleProject file(String relativePath, String content) throws IOException {
        Path file = dir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return this;
    }

    /**
     * ./mvnw -B -ntp args... en la carpeta del proyecto. La ruta va explícita (.\mvnw.cmd): hay Windows
     * configurados para NO buscar programas en la carpeta actual (NoDefaultCurrentDirectoryInExePath).
     */
    public Run maven(String... args) throws IOException, InterruptedException {
        Stream<String> mvnw = Shell.WINDOWS ? Stream.of("cmd", "/c", ".\\mvnw.cmd") : Stream.of("sh", "mvnw");
        return Shell.run(dir, Stream.concat(mvnw, Stream.concat(Stream.of("-B", "-ntp"), Stream.of(args))).toList());
    }

    /** Corre la clase con el java actual y el classpath dado (relativo a la carpeta). */
    public Run java(String... args) throws IOException, InterruptedException {
        String java = ProcessHandle.current().info().command().orElseThrow();
        return Shell.run(dir, Stream.concat(Stream.of(java), Stream.of(args)).toList());
    }

    /** El separador del classpath: ; en Windows, : en Linux y macOS. */
    public static String classpath(String... entries) {
        return String.join(Shell.WINDOWS ? ";" : ":", entries);
    }
}
