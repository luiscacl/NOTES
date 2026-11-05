package com.practice.tools.support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Corre un programa externo (Maven, Git...) en una carpeta y devuelve su salida. Lo usan las pruebas. */
public final class Shell {

    /** Lo que el programa dejó al terminar: código de salida y todo lo que escribió. */
    public record Run(int exitCode, String output) {
    }

    public static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    private Shell() {
    }

    public static Run run(Path dir, List<String> command) throws IOException, InterruptedException {
        return run(dir, Map.of(), command);
    }

    /**
     * Con la entrada estándar VACÍA (si algo pregunta, recibe "fin de entrada" en vez de esperar para
     * siempre: pasó, medido, un cmd colgado más de 10 minutos) y un tiempo máximo.
     */
    public static Run run(Path dir, Map<String, String> env, List<String> command)
            throws IOException, InterruptedException {
        Path log = Files.createTempFile("run-", ".log");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile())
                    .redirectInput(new File(WINDOWS ? "NUL" : "/dev/null"))
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile());
            builder.environment().putAll(env);
            Process process = builder.start();
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new IllegalStateException("No terminó en 5 minutos: " + command + "\n" + Files.readString(log));
            }
            return new Run(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(log);
        }
    }
}
