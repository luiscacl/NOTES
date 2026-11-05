package v7_functional.part1_lambdas;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

/*
    =====================================================================
    LAMBDAS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de functional/: guía 1 de 6  (part1_lambdas)
    Anterior:  v6_algorithms/Exercises
               (algorithms/ se estudia aparte)
    Siguiente: part1_lambdas/FunctionalInterfaces

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: qué es una interface funcional
    (v2_oop/part3_interfaces_and_relationships/Interfaces, CAPA 8) y qué
    es una clase anónima (NestedClasses, CAPAS 6 y 7).

    Índice:
        CAPA 1)  El problema: pasar COMPORTAMIENTO como parámetro
        CAPA 2)  Qué es una lambda
        CAPA 3)  Una lambda no tiene tipo propio: el "tipo destino"
        CAPA 4)  La sintaxis completa
        CAPA 5)  Los tipos de los parámetros
        CAPA 6)  Capturar variables: effectively final
        CAPA 7)  Por qué Java exige effectively final
        CAPA 8)  Cómo "acumular" sin romper la regla
        CAPA 9)  this y los nombres dentro de una lambda
        CAPA 10) Lambdas y excepciones checked
        CAPA 11) Lambdas como valores: devolverlas y guardarlas
        CAPA 12) Cómo funciona una lambda por dentro
        CAPA 13) Cuándo NO usar una lambda
        CAPA 14) Errores comunes
        RESUMEN FINAL
*/
public class Lambdas {

    public static void main(String[] args) {
        check(behaviorParameterExample(), "CAPA 1");
        check(whatIsALambdaExample(), "CAPA 2");
        check(targetTypeExample(), "CAPA 3");
        check(syntaxExample(), "CAPA 4");
        check(parameterTypesExample(), "CAPA 5");
        check(captureExample(), "CAPA 6");
        check(loopCaptureExample(), "CAPA 7");
        check(accumulateExample(), "CAPA 8");
        check(new Greeter("Hola").thisExample(), "CAPA 9");
        check(checkedExceptionsExample(), "CAPA 10");
        check(lambdasAsValuesExample(), "CAPA 11");
        check(insideExample(), "CAPA 12");
        System.out.println("Lambdas: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: PASAR COMPORTAMIENTO COMO PARÁMETRO
        =================================================================

        Una tienda necesita filtrar productos: los baratos, los de cierta
        categoría, los que no tienen stock... Sin nada especial, terminas
        con un método por criterio:

            List<Product> cheap(List<Product> all)        { for ... if (p.price() < 100) ... }
            List<Product> outOfStock(List<Product> all)   { for ... if (p.stock() == 0) ... }
            List<Product> ofCategory(List<Product> all, String c) { ... }

        Los tres son IGUALES salvo UNA línea: la condición. Y cada criterio
        nuevo es otro método copiado.

        La solución: escribir el recorrido UNA vez y recibir la condición
        como PARÁMETRO. Pero un parámetro de Java es un valor o un objeto,
        no "un pedazo de código". La forma orientada a objetos de pasar
        código es pasar un OBJETO que lo tenga en un método: una interface
        con un solo método (el patrón Strategy, v2_oop/part2_pillars/
        Polymorphism):

            interface ProductCondition { boolean test(Product p); }
            List<Product> filter(List<Product> all, ProductCondition condition)

        Antes de Java 8, para pasar la condición había que escribir una
        clase anónima: seis líneas para decir "price < 100". Las lambdas
        existen para decir eso en una línea.
    */
    record Product(String name, String category, int price, int stock) { }

    interface ProductCondition {
        boolean test(Product product);
    }

    static List<Product> filter(List<Product> products, ProductCondition condition) {
        List<Product> result = new ArrayList<>();
        for (Product product : products) {
            if (condition.test(product)) {          // la única parte que cambia viene de afuera
                result.add(product);
            }
        }
        return result;
    }

    static final List<Product> CATALOG = List.of(
            new Product("cable", "accesorios", 80, 30),
            new Product("teclado", "periféricos", 500, 0),
            new Product("mouse", "periféricos", 250, 12),
            new Product("funda", "accesorios", 150, 0));

    static boolean behaviorParameterExample() {
        List<Product> cheapOld = filter(CATALOG, new ProductCondition() {   // antes de Java 8
            @Override
            public boolean test(Product product) {
                return product.price() < 100;
            }
        });

        List<Product> cheap = filter(CATALOG, product -> product.price() < 100);   // lo mismo, con lambda
        List<Product> outOfStock = filter(CATALOG, product -> product.stock() == 0);

        return cheap.equals(cheapOld) && cheap.size() == 1 && outOfStock.size() == 2;
    }

    /*
        =================================================================
        CAPA 2) QUÉ ES UNA LAMBDA
        =================================================================

        Una LAMBDA es una función SIN NOMBRE escrita justo donde se usa:

            product -> product.price() < 100
            ───┬───    ─────────┬──────────
            parámetros    cuerpo (lo que devuelve)

        Se lee "dado un product, devuelve si su precio es menor a 100".

        Técnicamente, una lambda es la implementación del ÚNICO método
        abstracto de una interface funcional. Cuando escribes
            ProductCondition cheap = product -> product.price() < 100;
        Java crea un objeto que implementa ProductCondition, y su método
        test(product) ejecuta ese cuerpo. Por eso puedes guardarla en una
        variable, pasarla y llamarla como cualquier objeto:
            cheap.test(unProducto)

        Ojo: el cuerpo NO se ejecuta al escribir la lambda. Se ejecuta
        cada vez que alguien llama al método (test). Una lambda es código
        guardado para después: puede ejecutarse cero, una o mil veces.

        El nombre viene del "cálculo lambda" de las matemáticas (años 30),
        donde las funciones se escriben con la letra griega λ. La idea de
        tratar funciones como valores es la base de la programación
        FUNCIONAL, que da nombre a esta carpeta.
    */
    static boolean whatIsALambdaExample() {
        int[] calls = {0};
        ProductCondition cheap = product -> {
            calls[0]++;                             // cuenta cuántas veces se ejecuta (truco de la CAPA 8)
            return product.price() < 100;
        };

        boolean notRunYet = calls[0] == 0;          // escribirla no la ejecuta
        boolean result = cheap.test(CATALOG.get(0)) && !cheap.test(CATALOG.get(1));

        return notRunYet && result && calls[0] == 2;
    }

    /*
        =================================================================
        CAPA 3) UNA LAMBDA NO TIENE TIPO PROPIO: EL "TIPO DESTINO"
        =================================================================

        La misma lambda puede ser de tipos distintos según DÓNDE la
        escribas:

            ProductCondition a = p -> p.price() < 100;
            Predicate<Product> b = p -> p.price() < 100;

        Son dos objetos de tipos distintos con el mismo cuerpo. Java decide
        qué interface implementar mirando el TIPO DESTINO (target type):
        la variable, el parámetro o el return que recibe la lambda. De ahí
        saca también el tipo de los parámetros (p es un Product porque
        test recibe un Product).

        Consecuencia: sin un destino, Java no sabe qué crear:

            // ERROR: cannot infer type for local variable f
            //   (lambda expression needs an explicit target-type)
            var f = x -> x;

        Y aunque dos interfaces tengan la misma "forma", no son
        intercambiables: una ProductCondition no se puede asignar a una
        variable Predicate<Product>. Son tipos distintos (Java tiene
        tipado nominal: importan los nombres, no la forma).
    */
    static boolean targetTypeExample() {
        ProductCondition asCondition = p -> p.price() < 100;
        Predicate<Product> asPredicate = p -> p.price() < 100;

        Product cable = CATALOG.get(0);
        return asCondition.test(cable) && asPredicate.test(cable)
                && !(asPredicate instanceof ProductCondition);      // mismo cuerpo, tipos distintos
    }

    /*
        =================================================================
        CAPA 4) LA SINTAXIS COMPLETA
        =================================================================

        | Forma                              | Cuándo                                    |
        |------------------------------------|-------------------------------------------|
        | () -> 42                           | sin parámetros: paréntesis vacíos         |
        | x -> x * 2                         | UN parámetro sin tipo: sin paréntesis     |
        | (x, y) -> x + y                    | dos o más: con paréntesis                 |
        | (int x, int y) -> x + y            | con tipos explícitos (CAPA 5)             |
        | (_, y) -> y                        | parámetro que no usas (Java 22+)          |
        | x -> { ...; return algo; }         | cuerpo de BLOQUE: varias sentencias       |

        CUERPO DE EXPRESIÓN vs DE BLOQUE:
        - Expresión (sin llaves): el valor de la expresión ES lo que se
          devuelve. No se escribe return.
        - Bloque (con llaves): es un cuerpo de método normal. Si la
          interface devuelve algo, necesitas return en todos los caminos.

        Un detalle útil: si el método de la interface es void (Consumer,
        Runnable), un cuerpo de expresión puede ser una llamada que
        devuelve algo; el valor simplemente se descarta:
            Consumer<String> add = s -> list.add(s);   // add devuelve boolean: se ignora

        Regla de estilo: si el cuerpo necesita llaves y varias líneas,
        considera un método con nombre (CAPA 13).
    */
    static boolean syntaxExample() {
        Supplier<Integer> noParameters = () -> 42;
        Predicate<Integer> oneParameter = n -> n % 2 == 0;
        BiFunction<Integer, Integer, Integer> twoParameters = (a, b) -> a + b;
        BiFunction<String, Integer, Integer> ignoresFirst = (_, b) -> b * 10;   // "_": no lo uso

        BiFunction<Integer, Integer, Integer> blockBody = (a, b) -> {
            int bigger = Math.max(a, b);
            return bigger * 2;                      // bloque: return obligatorio
        };

        List<String> received = new ArrayList<>();
        Consumer<String> add = s -> received.add(s);   // add devuelve boolean; Consumer es void: se descarta
        add.accept("hola");

        return noParameters.get() == 42 && oneParameter.test(4)
                && twoParameters.apply(2, 3) == 5 && ignoresFirst.apply("x", 4) == 40
                && blockBody.apply(3, 7) == 14 && received.equals(List.of("hola"));
    }

    /*
        =================================================================
        CAPA 5) LOS TIPOS DE LOS PARÁMETROS
        =================================================================

        Normalmente NO escribes los tipos: Java los deduce del tipo
        destino (CAPA 3). Puedes escribirlos si ayuda a leer, o si Java no
        puede deducirlos (Sorting, CAPA 4: la lambda antes de .reversed()).

        Reglas:
        - O todos con tipo o ninguno. Mezclar no compila:
              // ERROR: invalid lambda parameter declaration
              //   (cannot mix implicitly-typed and explicitly-typed parameters)
              (Integer a, b) -> a + b
        - (var a, var b) -> ... (Java 11) es "sin tipo" escrito de otra
          forma. Existe para poder poner anotaciones en los parámetros:
          (@NonNull var a) -> ...
        - Si escribes un tipo, debe coincidir con el de la interface: no
          puedes "convertir" con la lambda.
    */
    static boolean parameterTypesExample() {
        BiFunction<Integer, Integer, Integer> inferred = (a, b) -> a + b;
        BiFunction<Integer, Integer, Integer> explicit = (Integer a, Integer b) -> a + b;
        BiFunction<Integer, Integer, Integer> withVar = (var a, var b) -> a + b;

        return inferred.apply(1, 2) == 3 && explicit.apply(1, 2) == 3 && withVar.apply(1, 2) == 3;
    }

    /*
        =================================================================
        CAPA 6) CAPTURAR VARIABLES: EFFECTIVELY FINAL
        =================================================================

        Una lambda puede usar variables de afuera. A eso se le llama
        CAPTURAR la variable, y a una función que se lleva consigo
        variables de su entorno se le llama CLOSURE (cierre).

            int limit = 100;
            ProductCondition cheap = p -> p.price() < limit;   // captura limit

        La regla: una variable LOCAL (o parámetro) solo se puede capturar
        si es FINAL o EFFECTIVELY FINAL ("final en la práctica"): no tiene
        la palabra final, pero nunca se le asigna otro valor después de
        inicializarla.

        Romper la regla en CUALQUIER lugar del método (antes o después de
        la lambda) no compila:

            int limit = 5;
            Runnable r = () -> System.out.println(limit);
            limit = 6;
            // ERROR: local variables referenced from a lambda expression
            //        must be final or effectively final

        La regla es SOLO para variables locales. Los CAMPOS de un objeto y
        los campos static sí se pueden leer y modificar desde una lambda
        (CAPA 7 explica por qué la diferencia).

        Y ojo: effectively final protege la VARIABLE, no el objeto
        (v1_fundamentals/part3_text_and_references/ReferencesAndNull). Si
        capturas una lista, no puedes reasignar la variable, pero sí
        llamar list.add(...). Por eso funcionó el Consumer de la CAPA 4.
    */
    static Predicate<Product> cheaperThan(int limit) {
        return p -> p.price() < limit;              // un parámetro nunca reasignado: effectively final
    }

    static boolean captureExample() {
        int limit = 200;                            // nunca se reasigna: se puede capturar
        ProductCondition cheap = p -> p.price() < limit;

        List<String> names = new ArrayList<>();     // la VARIABLE no cambia; el objeto sí puede cambiar
        Consumer<Product> collect = p -> names.add(p.name());
        CATALOG.forEach(collect);

        return filter(CATALOG, cheap).size() == 2
                && cheaperThan(100).test(CATALOG.get(0))
                && names.size() == 4;
    }

    /*
        =================================================================
        CAPA 7) POR QUÉ JAVA EXIGE EFFECTIVELY FINAL
        =================================================================

        Una lambda es código guardado para DESPUÉS (CAPA 2). Puede
        ejecutarse cuando el método que la creó ya terminó, o en otro
        hilo. Pero las variables locales viven en la PILA del método
        (v1_fundamentals/part3_text_and_references/ReferencesAndNull) y
        desaparecen cuando el método termina.

        Para que la lambda siga funcionando, Java le da una COPIA del
        valor de cada variable que captura, al momento de crearla. Si la
        variable original pudiera cambiar después, habría dos versiones
        (la copia de la lambda y la original) con valores distintos, y el
        código diría una cosa y haría otra. Java lo prohíbe de raíz:
        si nunca cambia, la copia y el original son siempre iguales.

        Los CAMPOS no tienen ese problema: viven en el objeto, en el heap,
        y la lambda no copia el campo sino la referencia al objeto (this).
        Siempre lee el valor actual.

        El caso clásico: crear lambdas en un for con índice.
            for (int i = 0; i < 3; i++) {
                tasks.add(() -> use(i));   // ERROR: i cambia en cada vuelta (i++)
            }
        En un for-each, en cambio, la variable es NUEVA en cada vuelta y
        nunca se reasigna, así que sí se puede capturar. Con el for
        clásico, copia el valor a una variable local dentro del ciclo.
    */
    static boolean loopCaptureExample() {
        List<Supplier<Integer>> byIndex = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int current = i;                        // copia nueva en cada vuelta: effectively final
            byIndex.add(() -> current * 10);
        }

        List<Supplier<String>> byForEach = new ArrayList<>();
        for (String name : List.of("a", "b")) {     // en for-each, name es nueva cada vuelta
            byForEach.add(() -> name.toUpperCase());
        }

        return byIndex.get(0).get() == 0 && byIndex.get(2).get() == 20
                && byForEach.get(1).get().equals("B");
    }

    /*
        =================================================================
        CAPA 8) CÓMO "ACUMULAR" SIN ROMPER LA REGLA
        =================================================================

        Intento típico al empezar:
            int total = 0;
            prices.forEach(p -> total += p);
            // ERROR: local variables referenced from a lambda expression
            //        must be final or effectively final

        Las "salidas" que se ven en internet, de peor a mejor:

        1) Arreglo de un elemento: int[] total = {0}; ... total[0] += p.
           Compila porque la variable (el arreglo) no cambia, solo su
           contenido. Funciona, pero es un truco: esconde una variable
           mutable, y con varios hilos da resultados equivocados.
        2) AtomicInteger: igual, pero seguro con hilos (v13_concurrency/).
           Sigue siendo estado mutable compartido.
        3) LO CORRECTO: no acumular desde una lambda. Usa un ciclo normal,
           o una operación que ya acumula: sum(), reduce o collect de los
           streams (part2_streams/StreamsBasics). El estilo funcional
           evita modificar cosas desde las lambdas.

        La regla de effectively final es, en el fondo, un empujón hacia
        ese estilo.
    */
    static boolean accumulateExample() {
        List<Integer> prices = List.of(80, 500, 250);

        int[] hackTotal = {0};
        prices.forEach(p -> hackTotal[0] += p);     // 1) compila, pero es un truco

        AtomicInteger atomicTotal = new AtomicInteger();
        prices.forEach(atomicTotal::addAndGet);     // 2) seguro con hilos, sigue siendo mutable

        int total = prices.stream().mapToInt(Integer::intValue).sum();   // 3) sin estado mutable

        int loopTotal = 0;
        for (int price : prices) {                  // 3) o un ciclo normal: perfectamente válido
            loopTotal += price;
        }

        return hackTotal[0] == 830 && atomicTotal.get() == 830 && total == 830 && loopTotal == 830;
    }

    /*
        =================================================================
        CAPA 9) THIS Y LOS NOMBRES DENTRO DE UNA LAMBDA
        =================================================================

        Una lambda NO crea un "ámbito" nuevo como lo hace una clase
        anónima. Su cuerpo está en el mismo ámbito que el método que la
        rodea. Dos consecuencias:

        1) this dentro de una lambda es el objeto del MÉTODO que la
           contiene, no la lambda. (En una clase anónima, this es la clase
           anónima; NestedClasses, CAPA 7.) Por eso una lambda puede usar
           los campos del objeto directamente.

        2) Los parámetros de la lambda no pueden llamarse igual que una
           variable local que ya existe:
               int x = 1;
               Function<Integer, Integer> f = x -> x + 1;
               // ERROR: variable x is already defined in method m()
           Una clase anónima sí podría "tapar" ese nombre; una lambda no.
    */
    static final class Greeter {
        private final String greeting;

        Greeter(String greeting) {
            this.greeting = greeting;
        }

        boolean thisExample() {
            Supplier<Greeter> self = () -> this;                    // this = el Greeter, no la lambda
            Supplier<String> usesField = () -> greeting + ", Ana";  // usa el campo sin más
            return self.get() == this && usesField.get().equals("Hola, Ana");
        }
    }

    /*
        =================================================================
        CAPA 10) LAMBDAS Y EXCEPCIONES CHECKED
        =================================================================

        Los métodos de las interfaces funcionales del JDK (Function,
        Consumer, Predicate...) NO declaran throws. Una lambda es su
        implementación, así que tampoco puede lanzar excepciones checked
        (v3_exceptions/part1_concepts/CheckedVsUnchecked):

            paths.forEach(p -> Files.readString(p));
            // ERROR: unreported exception IOException; must be caught or
            //        declared to be thrown

        Opciones:
        1) Atraparla dentro y envolverla en una unchecked con causa (para
           IOException existe UncheckedIOException). Es lo más común.
        2) Tu propia interface funcional que SÍ declara throws, cuando el
           código que la usa sabe qué hacer con la excepción.
        3) Un ciclo normal: si el cuerpo lanza excepciones checked y no lo
           pasas a ningún lado, a veces un for es lo más claro.

        Lo que NO se hace: atraparla y no hacer nada (v3_exceptions/
        part2_in_practice/ErrorHandlingBestPractices).
    */
    static String readConfig(String name) throws IOException {
        if (name.isBlank()) {
            throw new IOException("Nombre de archivo vacío");
        }
        return "contenido de " + name;
    }

    @FunctionalInterface
    interface ThrowingFunction<T, R> {
        R apply(T value) throws IOException;        // esta SÍ declara la checked
    }

    static <T, R> R applyOrDefault(ThrowingFunction<T, R> function, T value, R fallback) {
        try {
            return function.apply(value);
        } catch (IOException e) {
            return fallback;                        // quien la usa decide qué hacer
        }
    }

    static boolean checkedExceptionsExample() {
        List<String> contents = new ArrayList<>();
        List.of("a.txt", "b.txt").forEach(name -> {
            try {
                contents.add(readConfig(name));
            } catch (IOException e) {
                throw new UncheckedIOException(e);  // 1) envolver con la causa
            }
        });

        boolean wrapped;
        try {
            List.of(" ").forEach(name -> {
                try {
                    readConfig(name);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            wrapped = false;
        } catch (UncheckedIOException e) {
            wrapped = e.getCause().getMessage().equals("Nombre de archivo vacío");   // la causa no se pierde
        }

        String withDefault = applyOrDefault(Lambdas::readConfig, " ", "vacío");     // 2) interface propia

        return contents.size() == 2 && wrapped && withDefault.equals("vacío");
    }

    /*
        =================================================================
        CAPA 11) LAMBDAS COMO VALORES: DEVOLVERLAS Y GUARDARLAS
        =================================================================

        Como una lambda es un objeto, se puede:
        - DEVOLVER desde un método: una "fábrica de comportamiento".
          cheaperThan(100) de la CAPA 6 devuelve una condición nueva, con
          el límite capturado.
        - GUARDAR en un campo, una lista o un MAPA.

        El mapa de lambdas es un patrón muy útil: reemplaza un switch largo
        por una tabla de "nombre -> acción". Agregar una operación es
        agregar una entrada, sin tocar la lógica que las ejecuta (el
        principio abierto/cerrado, v10_design/).
    */
    static final Map<String, IntBinaryOperator> OPERATIONS = Map.of(
            "+", (a, b) -> a + b,
            "-", (a, b) -> a - b,
            "*", (a, b) -> a * b,
            "max", Math::max);                      // referencia a método: part1_lambdas/MethodReferences

    static int calculate(String operator, int a, int b) {
        IntBinaryOperator operation = OPERATIONS.get(operator);
        if (operation == null) {
            throw new IllegalArgumentException("Operación desconocida: " + operator);
        }
        return operation.applyAsInt(a, b);
    }

    static boolean lambdasAsValuesExample() {
        Predicate<Product> under300 = cheaperThan(300);     // comportamiento creado por un método
        Predicate<Product> under100 = cheaperThan(100);

        return calculate("+", 2, 3) == 5 && calculate("*", 4, 5) == 20 && calculate("max", 4, 9) == 9
                && under300.test(CATALOG.get(2)) && !under100.test(CATALOG.get(2));
    }

    /*
        =================================================================
        CAPA 12) CÓMO FUNCIONA UNA LAMBDA POR DENTRO
        =================================================================

        Una lambda NO es una clase anónima escrita más corto. El
        compilador hace esto:

        1) Convierte el CUERPO en un método privado de tu clase, con un
           nombre generado: lambda$main$0, lambda$main$1... Si capturas
           variables, se vuelven parámetros de ese método. (Lo puedes ver
           con: javap -p Lambdas.class.)
        2) En el lugar de la lambda pone una instrucción invokedynamic.
           La PRIMERA vez que se ejecuta, la JVM (LambdaMetafactory) genera
           en memoria una clase pequeña que implementa la interface y
           llama a ese método privado. Las siguientes veces reutiliza lo
           generado.

        Por qué así y no como clase anónima:
        - Una clase anónima genera un archivo .class por cada una
          (Lambdas$1.class, Lambdas$2.class...), que hay que compilar,
          empaquetar y cargar. Las lambdas no generan archivos y se preparan
          solo si se ejecutan.
        - Deja a la JVM libre de optimizar. Por ejemplo, en HotSpot una
          lambda que NO captura nada se crea UNA vez y se reutiliza el
          mismo objeto cada vez que se evalúa (no tiene nada propio que
          guardar). Una que captura variables crea un objeto nuevo cada
          vez, con su copia de los valores. (Es un detalle de
          implementación: nunca compares lambdas con ==.)

        Por eso en un stack trace verás nombres como lambda$main$0: es el
        método generado para el cuerpo de una lambda dentro de main.
    */
    static boolean insideExample() {
        List<Runnable> nonCapturing = new ArrayList<>();
        List<Supplier<Integer>> capturing = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            nonCapturing.add(() -> { });            // no captura nada
            int copy = i;
            capturing.add(() -> copy);              // captura copy: un objeto por evaluación
        }

        String generatedClass = nonCapturing.get(0).getClass().getName();

        return nonCapturing.get(0) == nonCapturing.get(1)      // reutilizado (detalle de HotSpot)
                && capturing.get(0) != capturing.get(1)
                && generatedClass.contains("$$Lambda");          // clase generada en memoria, no un .class
    }

    /*
        =================================================================
        CAPA 13) CUÁNDO NO USAR UNA LAMBDA
        =================================================================

        Las lambdas son para comportamiento CORTO y CLARO. Señales de que
        debería ser otra cosa:

        - El cuerpo tiene más de 3 o 4 líneas, o ifs anidados: muévelo a
          un método privado con un NOMBRE que explique qué hace, y pasa
          una referencia a método (part1_lambdas/MethodReferences). Un
          buen nombre documenta; una lambda larga obliga a leerla entera.
        - La misma lambda aparece en varios lugares: guárdala en una
          constante o hazla un método.
        - Necesita estado propio (campos que cambian) o varios métodos:
          es una clase.
        - Modifica variables de afuera con trucos (CAPA 8): probablemente
          debería ser un ciclo normal.
        - El stack trace de un error dentro de una lambda dice
          lambda$process$3: un método con nombre se depura mejor.

        La lambda no es "más moderna" ni "más profesional" por sí misma:
        lo profesional es lo más claro.
    */

    /*
        =================================================================
        CAPA 14) ERRORES COMUNES
        =================================================================

        1) Creer que la lambda se ejecuta al escribirla (CAPA 2).

        2) var f = x -> ...: una lambda necesita un tipo destino (CAPA 3).

        3) Mezclar parámetros con y sin tipo (CAPA 5).

        4) Modificar desde la lambda una variable local, o reasignar una
           variable capturada en cualquier parte del método (CAPAS 6 y 8).

        5) Capturar el índice de un for clásico: copia a una variable local
           dentro del ciclo (CAPA 7).

        6) Acumular con el truco del arreglo en lugar de sum/reduce o un
           ciclo (CAPA 8).

        7) Esperar que this sea la lambda, o reusar el nombre de una
           variable local como parámetro (CAPA 9).

        8) Lanzar una excepción checked dentro de una lambda del JDK, o
           "resolverlo" con un catch vacío (CAPA 10).

        9) Comparar lambdas con == o depender de que sean el mismo objeto
           (CAPA 12).

        10) Lambdas largas que deberían ser un método con nombre (CAPA 13).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Una lambda es una función sin nombre que implementa el único
          método abstracto de una interface funcional. Sirve para pasar
          COMPORTAMIENTO como parámetro.
        - No tiene tipo propio: lo toma del tipo destino (variable,
          parámetro, return).
        - Sintaxis: () -> x, x -> x, (x, y) -> x, (T x) -> x, (_, y) -> y,
          x -> { return ...; }.
        - Se ejecuta cuando se llama a su método, no al escribirla.
        - Captura variables locales solo si son effectively final, porque
          se lleva una COPIA. Los campos sí se pueden modificar.
        - No acumules desde lambdas: usa sum/reduce/collect o un ciclo.
        - this es el objeto que la rodea.
        - Sin excepciones checked: envuelve, o crea tu interface con throws.
        - Por dentro: método privado lambda$...$N + invokedynamic; sin
          archivos .class extra.
        - Si es larga, que sea un método con nombre.
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
