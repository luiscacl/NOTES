package com.practice.tools.quality.git;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.tools.support.Shell;
import com.practice.tools.support.Shell.Run;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Las comprobaciones de Git: repositorios de prueba en carpetas temporales, con Git de verdad.
 * Correr desde v9_tools/:  ./mvnw test -Dtest=GitTest
 */
class GitTest {

    /**
     * Usuario y fin de línea por VARIABLES DE ENTORNO, no con git config: no toca tu configuración global,
     * y en Windows cada llamada a git cuesta ~300 ms (medido: la prueba bajó de 77 s al quitar llamadas).
     * core.autocrlf=false también en el clone: si se pusiera después, el checkout ya habría convertido los
     * fines de línea con la configuración global y Git vería los archivos como modificados (medido).
     */
    private static final Map<String, String> GIT_ENV = Map.of(
            "GIT_AUTHOR_NAME", "Ana", "GIT_AUTHOR_EMAIL", "ana@example.com",
            "GIT_COMMITTER_NAME", "Ana", "GIT_COMMITTER_EMAIL", "ana@example.com",
            "GIT_CONFIG_COUNT", "1", "GIT_CONFIG_KEY_0", "core.autocrlf", "GIT_CONFIG_VALUE_0", "false");

    private record Repo(Path dir) {

        static Repo init(Path parent, String name) throws Exception {
            Path dir = parent.toRealPath().resolve(name);
            Files.createDirectories(dir);
            Repo repo = new Repo(dir);
            repo.ok("init", "-q", "-b", "main");
            return repo;
        }

        static Repo cloneOf(Path remote, Path parent, String name) throws Exception {
            Shell.run(parent.toRealPath(), GIT_ENV, List.of("git", "clone", "-q", remote.toString(), name));
            return new Repo(parent.toRealPath().resolve(name));
        }

        Run git(String... args) throws IOException, InterruptedException {
            return git(Map.of(), args);
        }

        Run git(Map<String, String> extraEnv, String... args) throws IOException, InterruptedException {
            Map<String, String> env = new HashMap<>(GIT_ENV);
            env.putAll(extraEnv);
            return Shell.run(dir, env, Stream.concat(Stream.of("git"), Stream.of(args)).toList());
        }

        /** Corre y exige que funcione; devuelve la salida sin espacios al final. */
        String ok(String... args) throws IOException, InterruptedException {
            Run run = git(args);
            assertThat(run.exitCode()).as("git %s%n%s", String.join(" ", args), run.output()).isZero();
            return run.output().strip();
        }

        Repo write(String file, String content) throws IOException {
            Files.writeString(dir.resolve(file), content, StandardCharsets.UTF_8);
            return this;
        }

        String read(String file) throws IOException {
            return Files.readString(dir.resolve(file), StandardCharsets.UTF_8);
        }

        /** Escribe, agrega y hace commit. */
        void commit(String file, String content, String message) throws Exception {
            write(file, content);
            ok("add", file);
            ok("commit", "-q", "-m", message);
        }

        String head() throws Exception {
            return ok("rev-parse", "--short", "HEAD");
        }

        long commits() throws Exception {
            return ok("log", "--oneline").lines().count();
        }
    }

    @Test
    void capa1_unCommitEsUnaFotoYUnaRamaUnPuntero(@TempDir Path tmp) throws Exception {
        Repo repo = Repo.init(tmp, "r");
        repo.commit("precios.txt", "precio=100\n", "Agregar lista de precios");
        assertThat(repo.ok("cat-file", "-p", "HEAD"))
                .startsWith("tree ").contains("author Ana <ana@example.com>").endsWith("Agregar lista de precios")
                .doesNotContain("parent");                                       // el primero no tiene padre
        assertThat(Files.size(repo.dir().resolve(".git/refs/heads/main"))).isEqualTo(41);  // 40 + salto de línea
        repo.commit("precios.txt", "precio=110\n", "Subir precio");
        assertThat(repo.ok("cat-file", "-p", "HEAD")).contains("parent ");
    }

    @Test
    void capa3_fastForwardYConflicto(@TempDir Path tmp) throws Exception {
        Repo repo = Repo.init(tmp, "r");
        repo.commit("precios.txt", "precio=100\n", "Agregar lista de precios");
        repo.ok("switch", "-q", "-c", "feature");
        repo.commit("precios.txt", "precio=100\nenvio=50\n", "Agregar costo de envío");
        repo.ok("switch", "-q", "main");
        assertThat(repo.ok("merge", "feature")).contains("Fast-forward");

        repo.ok("switch", "-q", "-c", "a");
        repo.commit("precios.txt", "precio=110\nenvio=50\n", "Subir precio a 110");
        repo.ok("switch", "-q", "main");
        repo.commit("precios.txt", "precio=95\nenvio=50\n", "Bajar precio a 95");
        Run merge = repo.git("merge", "a");
        assertThat(merge.exitCode()).isNotZero();
        assertThat(merge.output()).contains("CONFLICT (content): Merge conflict in precios.txt");
        assertThat(repo.read("precios.txt")).isEqualTo("<<<<<<< HEAD\nprecio=95\n=======\nprecio=110\n>>>>>>> a\nenvio=50\n");
        assertThat(repo.ok("status", "--short")).isEqualTo("UU precios.txt");
        repo.ok("merge", "--abort");
        assertThat(repo.ok("status", "--short")).isEmpty();
    }

    @Test
    void capa4_rebaseCreaCommitsNuevosYAutosquashJuntaLosFixup(@TempDir Path tmp) throws Exception {
        Repo repo = Repo.init(tmp, "r");
        repo.commit("base.txt", "base\n", "Base");
        repo.ok("switch", "-q", "-c", "feature");
        repo.commit("a.txt", "a\n", "Agregar a");
        repo.commit("b.txt", "b\n", "Agregar b");
        String before = repo.head();
        repo.ok("switch", "-q", "main");
        repo.commit("m.txt", "m\n", "Cambio en main");
        repo.ok("switch", "-q", "feature");
        repo.ok("rebase", "-q", "main");
        assertThat(repo.ok("rev-parse", "--short", "HEAD")).isNotEqualTo(before);   // mismo cambio, otro hash
        assertThat(repo.ok("log", "--format=%s")).isEqualTo("Agregar b\nAgregar a\nCambio en main\nBase");

        repo.commit("a.txt", "a corregido\n", "fixup! Agregar a");
        assertThat(repo.commits()).isEqualTo(5);
        Run squash = repo.git(Map.of("GIT_SEQUENCE_EDITOR", ":"), "rebase", "-q", "-i", "--autosquash", "main");
        assertThat(squash.exitCode()).as(squash.output()).isZero();
        assertThat(repo.commits()).isEqualTo(4);
        assertThat(repo.ok("show", "HEAD~1:a.txt")).isEqualTo("a corregido");     // "Agregar a" ya trae el arreglo
    }

    @Test
    void capa5_losTresResetRevertYReflog(@TempDir Path tmp) throws Exception {
        Repo repo = Repo.init(tmp, "r");
        for (int i = 1; i <= 3; i++) {
            repo.commit("f.txt", "v" + i + "\n", "Versión " + i);
        }
        repo.ok("reset", "-q", "--soft", "HEAD~1");
        assertThat(repo.git("status", "--short").output()).isEqualTo("M  f.txt\n");   // preparado
        repo.ok("reset", "-q", "--hard", "HEAD@{1}");
        repo.ok("reset", "-q", "--mixed", "HEAD~1");
        assertThat(repo.git("status", "--short").output()).isEqualTo(" M f.txt\n");   // sin preparar
        repo.ok("reset", "-q", "--hard", "HEAD@{1}");
        repo.ok("reset", "-q", "--hard", "HEAD~1");
        assertThat(repo.read("f.txt")).isEqualTo("v2\n");                              // borrado de la carpeta
        repo.ok("reset", "-q", "--hard", "HEAD@{1}");

        repo.ok("revert", "--no-edit", "HEAD");
        assertThat(repo.ok("log", "--format=%s", "-2")).isEqualTo("Revert \"Versión 3\"\nVersión 3");
        assertThat(repo.read("f.txt")).isEqualTo("v2\n");

        repo.ok("reset", "-q", "--hard", "HEAD~2");
        assertThat(repo.commits()).isEqualTo(2);
        assertThat(repo.ok("reflog", "-1")).contains("HEAD@{0}: reset: moving to HEAD~2");
        repo.ok("reset", "-q", "--hard", "HEAD@{1}");                                 // de vuelta
        assertThat(repo.commits()).isEqualTo(4);
    }

    @Test
    void capa6_stashYCherryPick(@TempDir Path tmp) throws Exception {
        Repo repo = Repo.init(tmp, "r");
        repo.commit("base.txt", "base\n", "Base");
        repo.write("base.txt", "base\na medias\n");
        repo.ok("stash", "push", "-q", "-m", "trabajo a medias");
        assertThat(repo.ok("status", "--short")).isEmpty();
        assertThat(repo.ok("stash", "list")).isEqualTo("stash@{0}: On main: trabajo a medias");
        repo.ok("stash", "pop", "-q");
        assertThat(repo.ok("status", "--short")).isEqualTo("M base.txt");
        repo.ok("checkout", "-q", "--", "base.txt");

        repo.ok("switch", "-q", "-c", "hotfix");
        repo.commit("fix.txt", "fix\n", "Corregir IVA");
        String original = repo.head();
        repo.ok("switch", "-q", "-c", "feature", "main");
        // feature ya avanzó: la copia tiene OTRO padre. (Con el mismo padre, el mismo contenido y el mismo
        // segundo, Git calcula el MISMO hash: un commit es solo esos datos. Medido: así falló esta prueba.)
        repo.commit("feature.txt", "trabajo\n", "Trabajo de la rama");
        repo.ok("cherry-pick", "hotfix");
        assertThat(repo.ok("log", "-1", "--format=%s")).isEqualTo("Corregir IVA");
        assertThat(repo.ok("rev-parse", "--short", "HEAD")).isNotEqualTo(original);   // copia: otro hash
    }

    @Test
    void capa7_bisectEncuentraElCulpableEnPocosPasos(@TempDir Path tmp) throws Exception {
        Repo repo = Repo.init(tmp, "r");
        for (int i = 1; i <= 20; i++) {
            repo.write("cfg.txt", i >= 13 ? "iva=0.15\n" : "iva=0.16\n").write("n.txt", i + "\n");
            repo.ok("add", ".");                     // cfg.txt TAMBIÉN: sin él, bisect culpó a "Cambio 2" (medido)
            repo.ok("commit", "-q", "-m", "Cambio " + i);
        }
        repo.ok("bisect", "start", "HEAD", "HEAD~19");
        Run run = repo.git("bisect", "run", "sh", "-c", "grep -q iva=0.16 cfg.txt");
        assertThat(run.output()).contains("is the first bad commit").contains("Cambio 13");
        long steps = repo.ok("bisect", "log").lines().filter(l -> l.matches("git bisect (good|bad) .*")).count();
        assertThat(steps).isLessThanOrEqualTo(6);                                       // no 20
        repo.ok("bisect", "reset");
    }

    @Test
    void capa8_pushRechazadoYForceWithLease(@TempDir Path tmp) throws Exception {
        Path remote = tmp.toRealPath().resolve("origin.git");
        Shell.run(tmp.toRealPath(), List.of("git", "init", "-q", "--bare", "-b", "main", "origin.git"));
        Repo ana = Repo.cloneOf(remote, tmp, "ana");
        ana.commit("app.txt", "v1\n", "Inicio");
        ana.ok("push", "-q", "origin", "main");
        Repo luis = Repo.cloneOf(remote, tmp, "luis");
        luis.commit("luis.txt", "luis\n", "Cambio de Luis");
        luis.ok("push", "-q", "origin", "main");

        ana.commit("ana.txt", "ana\n", "Cambio de Ana");
        Run rejected = ana.git("push", "origin", "main");
        assertThat(rejected.exitCode()).isNotZero();
        assertThat(rejected.output()).contains("[rejected]", "(fetch first)");
        ana.ok("pull", "-q", "--rebase", "origin", "main");
        assertThat(ana.ok("log", "--format=%s")).isEqualTo("Cambio de Ana\nCambio de Luis\nInicio");
        ana.ok("push", "-q", "origin", "main");

        luis.ok("pull", "-q", "--rebase", "origin", "main");
        luis.commit("x.txt", "x\n", "Luis 2");
        luis.ok("push", "-q", "origin", "main");
        ana.ok("commit", "-q", "--amend", "-m", "Cambio de Ana (corregido)");
        Run lease = ana.git("push", "--force-with-lease", "origin", "main");
        assertThat(lease.exitCode()).isNotZero();
        assertThat(lease.output()).contains("(stale info)");                        // no pisó a Luis

        ana.ok("push", "-q", "--force", "origin", "main");
        luis.ok("fetch", "-q");
        assertThat(luis.ok("log", "--format=%s", "origin/main")).doesNotContain("Luis 2");   // --force lo borró
    }
}
