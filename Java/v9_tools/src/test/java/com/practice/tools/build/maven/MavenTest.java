package com.practice.tools.build.maven;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.tools.support.SampleProject;
import com.practice.tools.support.Shell.Run;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Las comprobaciones de Maven: proyectos de muestra en carpetas temporales, construidos con Maven de verdad.
 * Correr desde v9_tools/:  ./mvnw test -Dtest=MavenTest
 */
class MavenTest {

    private static final String HEADER = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>%s</artifactId>
              <version>1.0.0</version>
              <properties>
                <maven.compiler.release>25</maven.compiler.release>
              </properties>
            """;

    private static String pom(String artifactId, String body) {
        return HEADER.formatted(artifactId) + body + "</project>\n";
    }

    private static final String JACKSON_APP = """
            package demo;
            import com.fasterxml.jackson.databind.ObjectMapper;
            import java.util.Map;
            public class App {
                public static void main(String[] args) throws Exception {
                    System.out.println(new ObjectMapper().writeValueAsString(Map.of("sku", "TEC-01")));
                }
            }
            """;

    private static final String DATABIND_AND_OLD_CORE = """
              <dependencies>
                <dependency>
                  <groupId>com.fasterxml.jackson.core</groupId>
                  <artifactId>jackson-databind</artifactId>
                  <version>2.18.2</version>
                </dependency>
                <dependency>
                  <groupId>com.fasterxml.jackson.core</groupId>
                  <artifactId>jackson-core</artifactId>
                  <version>2.14.0</version>
                </dependency>
              </dependencies>
            """;

    /** Compila y corre demo.App con el classpath que resolvió Maven. */
    private static Run runApp(SampleProject project) throws Exception {
        Run build = project.maven("-q", "compile", "dependency:build-classpath", "-Dmdep.outputFile=cp.txt");
        assertThat(build.exitCode()).as(build.output()).isZero();
        String dependencies = Files.readString(project.dir().resolve("cp.txt")).strip();
        return project.java("-cp", SampleProject.classpath("target/classes", dependencies), "demo.App");
    }

    @Test
    void capa1_unPomDeNueveLineasConstruyeUnJarQueNoEsEjecutable(@TempDir Path dir) throws Exception {
        SampleProject project = SampleProject.in(dir)
                .file("pom.xml", pom("hello", ""))
                .file("src/main/java/demo/Hello.java", """
                        package demo;
                        public class Hello {
                            public static void main(String[] args) { System.out.println("hola desde Maven"); }
                        }
                        """);
        assertThat(project.maven("package").exitCode()).isZero();
        assertThat(project.java("-jar", "target/hello-1.0.0.jar").output()).contains("no main manifest attribute");
        assertThat(project.java("-cp", "target/hello-1.0.0.jar", "demo.Hello").output()).contains("hola desde Maven");
    }

    @Test
    void capa2_unaPruebaRotaDetieneElCicloYSaltarlasNoEsLoMismo(@TempDir Path dir) throws Exception {
        SampleProject project = SampleProject.in(dir)
                .file("pom.xml", pom("lifecycle", """
                          <dependencies>
                            <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter</artifactId>
                              <version>6.0.3</version>
                              <scope>test</scope>
                            </dependency>
                          </dependencies>
                        """))
                .file("src/main/java/demo/Prices.java", """
                        package demo;
                        public class Prices { public static long withTax(long cents) { return cents * 116 / 100; } }
                        """)
                .file("src/test/java/demo/PricesTest.java", """
                        package demo;
                        import static org.junit.jupiter.api.Assertions.assertEquals;
                        import org.junit.jupiter.api.Test;
                        class PricesTest { @Test void iva() { assertEquals(11_600, Prices.withTax(10_000) + 1); } }
                        """);

        Run broken = project.maven("package");
        assertThat(broken.exitCode()).isNotZero();
        assertThat(broken.output()).contains("compiler:", "surefire:", "expected: <11600> but was: <11601>")
                .doesNotContain("--- jar:");                                // el ciclo se detuvo antes
        assertThat(project.dir().resolve("target/lifecycle-1.0.0.jar")).doesNotExist();

        Run skipTests = project.maven("clean", "package", "-DskipTests");
        assertThat(skipTests.output()).contains("Tests are skipped.");
        assertThat(project.dir().resolve("target/test-classes/demo/PricesTest.class")).exists();   // compiladas

        Run skipAll = project.maven("clean", "package", "-Dmaven.test.skip=true");
        assertThat(skipAll.output()).contains("Not compiling test sources");
        assertThat(project.dir().resolve("target/test-classes/demo/PricesTest.class")).doesNotExist();
    }

    @Test
    void capa3_ganaLaVersionMasCercanaAunqueSeaViejaYTruenaAlCorrer(@TempDir Path dir) throws Exception {
        SampleProject project = SampleProject.in(dir)
                .file("pom.xml", pom("conflict", DATABIND_AND_OLD_CORE))
                .file("src/main/java/demo/App.java", JACKSON_APP);

        assertThat(project.maven("dependency:tree", "-Dverbose").output())
                .contains("jackson-core:jar:2.18.2:compile - omitted for conflict with 2.14.0");
        assertThat(runApp(project).output())
                .contains("java.lang.NoSuchMethodError")                    // compiló; falla al correr
                .contains("BufferRecycler.releaseToPool()");
    }

    @Test
    void capa4_elEnforcerLoDetectaYElBomLoArregla(@TempDir Path enforced, @TempDir Path aligned) throws Exception {
        Run convergence = SampleProject.in(enforced)
                .file("pom.xml", pom("conflict", DATABIND_AND_OLD_CORE + """
                          <build>
                            <plugins>
                              <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-enforcer-plugin</artifactId>
                                <version>3.6.2</version>
                                <executions>
                                  <execution>
                                    <goals><goal>enforce</goal></goals>
                                    <configuration><rules><dependencyConvergence/></rules></configuration>
                                  </execution>
                                </executions>
                              </plugin>
                            </plugins>
                          </build>
                        """))
                .maven("validate");                                         // antes de compilar
        assertThat(convergence.exitCode()).isNotZero();
        assertThat(convergence.output()).contains("Dependency convergence error for com.fasterxml.jackson.core:jackson-core");

        SampleProject withBom = SampleProject.in(aligned)
                .file("pom.xml", pom("aligned", """
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>com.fasterxml.jackson</groupId>
                                <artifactId>jackson-bom</artifactId>
                                <version>2.18.2</version>
                                <type>pom</type>
                                <scope>import</scope>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>com.fasterxml.jackson.core</groupId>
                              <artifactId>jackson-databind</artifactId>
                            </dependency>
                            <dependency>
                              <groupId>com.fasterxml.jackson.core</groupId>
                              <artifactId>jackson-core</artifactId>
                            </dependency>
                          </dependencies>
                        """))
                .file("src/main/java/demo/App.java", JACKSON_APP);
        assertThat(runApp(withBom).output()).contains("{\"sku\":\"TEC-01\"}");
    }

    @Test
    void capa5_loQueEsDeScopeTestNoExisteParaSrcMain(@TempDir Path dir) throws Exception {
        Run run = SampleProject.in(dir)
                .file("pom.xml", pom("scopes", """
                          <dependencies>
                            <dependency>
                              <groupId>org.junit.jupiter</groupId>
                              <artifactId>junit-jupiter</artifactId>
                              <version>6.0.3</version>
                              <scope>test</scope>
                            </dependency>
                          </dependencies>
                        """))
                .file("src/main/java/demo/Oops.java", """
                        package demo;
                        import org.junit.jupiter.api.Assertions;
                        public class Oops { }
                        """)
                .maven("compile");
        assertThat(run.exitCode()).isNotZero();
        assertThat(run.output()).contains("package org.junit.jupiter.api does not exist");
    }

    @Test
    void capa7_elReactorOrdenaPorDependenciasYUnModuloSoloNoEncuentraAlOtro(@TempDir Path dir) throws Exception {
        String child = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>shop-parent</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                  </parent>
                  <artifactId>%s</artifactId>
                %s</project>
                """;
        SampleProject project = SampleProject.in(dir)
                .file("pom.xml", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.example</groupId>
                          <artifactId>shop-parent</artifactId>
                          <version>1.0.0-SNAPSHOT</version>
                          <packaging>pom</packaging>
                          <modules>
                            <module>app</module>
                            <module>pricing</module>
                          </modules>
                          <properties>
                            <maven.compiler.release>25</maven.compiler.release>
                          </properties>
                        </project>
                        """)
                .file("pricing/pom.xml", child.formatted("pricing", ""))
                .file("app/pom.xml", child.formatted("app", """
                          <dependencies>
                            <dependency>
                              <groupId>com.example</groupId>
                              <artifactId>pricing</artifactId>
                              <version>${project.version}</version>
                            </dependency>
                          </dependencies>
                        """))
                .file("pricing/src/main/java/demo/pricing/Pricing.java", """
                        package demo.pricing;
                        public class Pricing { public static long withTax(long cents) { return cents * 116 / 100; } }
                        """)
                .file("app/src/main/java/demo/app/App.java", """
                        package demo.app;
                        public class App { public static void main(String[] a) { System.out.println(demo.pricing.Pricing.withTax(10_000)); } }
                        """);

        Run all = project.maven("package");
        assertThat(all.exitCode()).isZero();
        String order = all.output().substring(all.output().indexOf("Reactor Build Order"));
        assertThat(order.indexOf("pricing")).isLessThan(order.indexOf("app"));   // app se declaró primero

        project.maven("clean");
        Run alone = project.maven("-pl", "app", "package");
        assertThat(alone.exitCode()).isNotZero();
        assertThat(alone.output()).contains("Could not resolve dependencies");
        assertThat(project.maven("-pl", "app", "-am", "package").exitCode()).isZero();
    }
}
