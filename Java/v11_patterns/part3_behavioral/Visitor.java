package v11_patterns.part3_behavioral;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/*
    =====================================================================
    VISITOR: AGREGAR OPERACIONES A UNA JERARQUÍA SIN TOCARLA
    =====================================================================

    Ruta de patterns/: guía 23 de 28  (part3_behavioral)
    Anterior:  part3_behavioral/TemplateMethod
    Siguiente: part4_modern/NullObject  (última de part3_behavioral/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: BAJA para escribirlo en su forma clásica: desde Java 21,
    sealed + switch con patrones resuelve el mismo problema con mucho
    menos código. Esta guía muestra esa forma, y dónde SÍ vas a encontrar
    visitantes vigentes (CAPA 4).

    Índice:
        CAPA 1)  El problema: muchas operaciones sobre los mismos tipos
        CAPA 2)  La solución actual: sealed + records + una función por operación
        CAPA 3)  El Visitor clásico, para reconocerlo
        CAPA 4)  Donde sí lo vas a usar: recorrer carpetas con FileVisitor
        CAPA 5)  Errores comunes
        RESUMEN FINAL
*/
public class Visitor {

    public static void main(String[] args) throws IOException {
        check(operationsExample(), "CAPA 2");
        check(fileVisitorExample(), "CAPA 4");
        System.out.println("Visitor: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: MUCHAS OPERACIONES SOBRE LOS MISMOS TIPOS
        =================================================================

        Un carrito tiene tres tipos de artículos, cada uno con datos
        distintos:
            Physical  un producto físico: precio y peso
            Digital   una descarga: precio, sin peso
            Service   un servicio (instalación): precio por hora y horas

        Y sobre ellos se necesitan MUCHAS operaciones, que se van agregando
        con el tiempo: calcular impuestos (distintos por tipo), calcular el
        peso para el envío, exportar a la contabilidad, decidir si requiere
        dirección...

        Con polimorfismo clásico, cada operación es un método en la
        interface, y CADA operación nueva obliga a tocar las tres clases (el
        lado difícil del "problema de la expresión"; v2_oop/
        part4_advanced_types/SealedAndPatternMatching, CAPA 10). Además, las
        clases de datos se llenan de lógica de impuestos, envío y
        contabilidad que no les corresponde.

        Cuando los TIPOS son fijos y lo que crece son las OPERACIONES, se
        quiere lo contrario: que cada operación nueva viva en su propio
        lugar, sin tocar los tipos.
    */
    sealed interface CartItem permits Physical, Digital, Service { }

    record Physical(String name, BigDecimal price, int grams) implements CartItem { }

    record Digital(String name, BigDecimal price) implements CartItem { }

    record Service(String name, BigDecimal hourlyRate, int hours) implements CartItem { }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN ACTUAL: SEALED + RECORDS + UNA FUNCIÓN POR OPERACIÓN
        =================================================================

        VISITOR: "Representa una operación que se realiza sobre los
        elementos de una estructura de objetos. Permite definir una
        operación nueva sin cambiar las clases de los elementos."

        En Java 21+ esto se escribe así:
        - los tipos son records en una jerarquía sealed (sin lógica de
          operaciones adentro);
        - CADA OPERACIÓN es una función con un switch sobre el tipo, en su
          propio lugar (su propio archivo, en un proyecto real);
        - el switch es EXHAUSTIVO (sin default): si mañana se agrega un
          cuarto tipo (Subscription), el compilador marca cada operación
          que no lo maneja.

        Agregar "exportar a contabilidad" es una función nueva; los records
        no se tocan. Eso es exactamente lo que el patrón Visitor prometía.
    */
    static BigDecimal tax(CartItem item) {                  // OPERACIÓN 1: impuestos por tipo
        return switch (item) {
            case Physical p -> p.price().multiply(new BigDecimal("0.16"));
            case Digital d -> d.price().multiply(new BigDecimal("0.16"));
            case Service s -> s.hourlyRate().multiply(BigDecimal.valueOf(s.hours())).multiply(new BigDecimal("0.08"));
        };
    }

    static int shippingGrams(CartItem item) {               // OPERACIÓN 2: peso para el envío
        return switch (item) {
            case Physical p -> p.grams();
            case Digital _ -> 0;                          // "_": el patrón no necesita el valor
            case Service _ -> 0;
        };
    }

    static String accountingLine(CartItem item) {           // OPERACIÓN 3: agregada después, sin tocar nada
        return switch (item) {
            case Physical p -> "VENTA-BIENES|" + p.name() + "|" + p.price();
            case Digital d -> "VENTA-DIGITAL|" + d.name() + "|" + d.price();
            case Service s -> "SERVICIOS|" + s.name() + "|" + s.hourlyRate().multiply(BigDecimal.valueOf(s.hours()));
        };
    }

    static boolean operationsExample() {
        List<CartItem> cart = List.of(
                new Physical("teclado", new BigDecimal("500"), 900),
                new Digital("curso Java", new BigDecimal("300")),
                new Service("instalación", new BigDecimal("200"), 2));

        BigDecimal totalTax = cart.stream().map(Visitor::tax).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        int grams = cart.stream().mapToInt(Visitor::shippingGrams).sum();
        List<String> accounting = cart.stream().map(Visitor::accountingLine).toList();

        return totalTax.compareTo(new BigDecimal("160.00")) == 0      // 80 + 48 + 32
                && grams == 900
                && accounting.get(2).equals("SERVICIOS|instalación|400");
    }

    /*
        =================================================================
        CAPA 3) EL VISITOR CLÁSICO, PARA RECONOCERLO
        =================================================================

        Antes de Java 21 no había switch exhaustivo sobre tipos, y un
        switch con instanceof no avisaba si faltaba un tipo. El libro GoF
        resolvió el problema con un truco llamado "doble despacho": una
        interface Visitor con un método visit por tipo (visit(Physical),
        visit(Digital)...) y un método accept(visitor) en cada tipo, que
        solo hace visitor.visit(this). Cada operación era una clase que
        implementaba Visitor.

        Lo vas a ver en librerías que procesan árboles (compiladores,
        analizadores de código, herramientas de bases de datos) y en
        código anterior a Java 21. Si lo encuentras: cada clase "XxxVisitor"
        es una operación, y cada método visit es una rama del switch de la
        CAPA 2. En código nuevo propio, usa la CAPA 2: hace lo mismo con
        menos piezas y el compilador revisa que no falte ningún caso.
    */

    /*
        =================================================================
        CAPA 4) DONDE SÍ LO VAS A USAR: RECORRER CARPETAS CON FILEVISITOR
        =================================================================

        El JDK tiene un visitante vigente y útil: Files.walkFileTree(raíz,
        visitante). El sistema de archivos es un árbol (part2_structural/
        Composite, CAPA 5) y el JDK lo RECORRE por ti; tú le das un objeto
        (normalmente heredando de SimpleFileVisitor) con un método por
        "tipo de momento" del recorrido:

            preVisitDirectory    al entrar a una carpeta
            visitFile            por cada archivo
            visitFileFailed      si un archivo no se pudo leer (permisos)
            postVisitDirectory   al salir de una carpeta (ya visitó todo adentro)

        Cada método devuelve qué hacer después, y eso es lo que lo hace más
        poderoso que Files.walk (v8_apis/part2_io_and_network/FilesAndIO,
        CAPA 13):
            CONTINUE         seguir
            SKIP_SUBTREE     no entrar a esta carpeta (ignorar node_modules,
                             .git, carpetas de compilación)
            SKIP_SIBLINGS    saltarse lo que queda en esta carpeta
            TERMINATE        detener todo (ya encontré lo que buscaba)

        Y visitFileFailed permite seguir aunque un archivo no se pueda leer,
        en vez de que todo el recorrido falle con una excepción.

        El ejemplo arma una carpeta temporal con código y una carpeta
        "build" (como la que generan las herramientas de compilación), y
        cuenta los archivos .java saltándose "build" completa.
    */
    static final class JavaSourceCounter extends SimpleFileVisitor<Path> {
        final List<String> found = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
            if (dir.getFileName().toString().equals("build")) {
                skipped.add(dir.getFileName().toString());
                return FileVisitResult.SKIP_SUBTREE;        // no entrar: ahorra recorrer miles de archivos generados
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (file.getFileName().toString().endsWith(".java")) {
                found.add(file.getFileName().toString());
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException error) {
            return FileVisitResult.CONTINUE;                // un archivo ilegible no detiene todo el recorrido
        }
    }

    static boolean fileVisitorExample() throws IOException {
        Path root = Files.createTempDirectory("visitor-");
        try {
            Files.createDirectories(root.resolve("src").resolve("app"));
            Files.createDirectories(root.resolve("build").resolve("generated"));
            Files.writeString(root.resolve("src").resolve("app").resolve("Main.java"), "class Main {}");
            Files.writeString(root.resolve("src").resolve("app").resolve("notas.txt"), "x");
            Files.writeString(root.resolve("build").resolve("generated").resolve("Temp.java"), "class Temp {}");

            JavaSourceCounter counter = new JavaSourceCounter();
            Files.walkFileTree(root, counter);             // el JDK recorre; el visitante decide

            return counter.found.equals(List.of("Main.java"))     // Temp.java quedó fuera: estaba en build/
                    && counter.skipped.equals(List.of("build"));
        } finally {
            try (Stream<Path> all = Files.walk(root)) {
                for (Path path : all.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);                     // limpiar (FilesAndIO, CAPA 13)
                }
            }
        }
    }

    /*
        =================================================================
        CAPA 5) ERRORES COMUNES
        =================================================================

        1) Meter en las clases de datos la lógica de cada operación nueva
           (impuestos, envío, contabilidad) (CAPA 1).

        2) Poner default en el switch de una operación: un tipo nuevo cae
           ahí en silencio en vez de que el compilador avise (CAPA 2).

        3) Escribir el Visitor clásico con accept/visit en código nuevo
           propio, cuando sealed + switch hace lo mismo (CAPA 3).

        4) Recorrer carpetas grandes con Files.walk filtrando después, en
           lugar de saltarse subárboles enteros con walkFileTree (CAPA 4).

        5) Usar esta forma cuando lo que crece son los TIPOS y no las
           operaciones: ahí conviene el polimorfismo clásico
           (SealedAndPatternMatching, CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Visitor: agregar operaciones a una jerarquía de tipos sin
          modificar los tipos.
        - Hoy: sealed + records para los tipos, y una función con switch
          exhaustivo por operación; el compilador avisa si falta un tipo.
        - El Visitor clásico (accept + visit, doble despacho) solo para
          reconocerlo en librerías y código viejo.
        - Vigente en el JDK: Files.walkFileTree con SimpleFileVisitor, que
          deja saltarse carpetas (SKIP_SUBTREE), detener el recorrido y
          manejar archivos ilegibles.
        - Úsalo cuando los tipos son fijos y crecen las operaciones.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
