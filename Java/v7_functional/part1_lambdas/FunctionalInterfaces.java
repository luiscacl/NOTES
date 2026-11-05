package v7_functional.part1_lambdas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    =====================================================================
    INTERFACES FUNCIONALES DEL JDK — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de functional/: guía 2 de 6  (part1_lambdas)
    Anterior:  part1_lambdas/Lambdas
    Siguiente: part1_lambdas/MethodReferences

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: qué es una interface funcional y
    @FunctionalInterface (v2_oop/part3_interfaces_and_relationships/
    Interfaces, CAPA 8) y las lambdas (part1_lambdas/Lambdas).

    Índice:
        CAPA 1)  Por qué existe java.util.function
        CAPA 2)  Las cuatro básicas: Function, Predicate, Consumer, Supplier
        CAPA 3)  Supplier y la evaluación perezosa
        CAPA 4)  Dos parámetros: BiFunction, BiPredicate, BiConsumer
        CAPA 5)  Operadores: UnaryOperator y BinaryOperator
        CAPA 6)  Las variantes primitivas y cómo descifrar sus nombres
        CAPA 7)  Componer funciones: andThen, compose, identity
        CAPA 8)  Componer condiciones: and, or, negate, not
        CAPA 9)  Tu propia interface funcional (con composición)
        CAPA 10) Sobrecarga con interfaces funcionales: ambigüedad
        CAPA 11) Cómo elegir
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class FunctionalInterfaces {

    public static void main(String[] args) {
        check(basicFourExample(), "CAPA 2");
        check(lazySupplierExample(), "CAPA 3");
        check(twoParametersExample(), "CAPA 4");
        check(operatorsExample(), "CAPA 5");
        check(primitivesExample(), "CAPA 6");
        check(functionCompositionExample(), "CAPA 7");
        check(predicateCompositionExample(), "CAPA 8");
        check(customInterfaceExample(), "CAPA 9");
        check(overloadExample(), "CAPA 10");
        System.out.println("FunctionalInterfaces: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) POR QUÉ EXISTE JAVA.UTIL.FUNCTION
        =================================================================

        Una lambda necesita una interface funcional como destino
        (Lambdas, CAPA 3). Si cada librería inventara las suyas
        (ProductCondition, UserFilter, OrderCheck...), todas con la misma
        forma pero sin poder intercambiarse, nada encajaría con nada.

        Por eso Java 8 trajo el paquete java.util.function: 43 interfaces
        funcionales estándar que funcionan como un VOCABULARIO COMÚN.
        Todo el JDK las usa, así que tus lambdas encajan directo en:
            list.removeIf(Predicate)          map.computeIfAbsent(k, Function)
            list.forEach(Consumer)            optional.orElseGet(Supplier)
            map.forEach(BiConsumer)           stream.map(Function).filter(Predicate)

        43 parecen muchas, pero son pocas IDEAS combinadas con un sistema
        de nombres. Aprendes 4 básicas (CAPA 2), cómo se extienden a dos
        parámetros (CAPA 4) y la regla de los nombres primitivos (CAPA 6),
        y puedes leer cualquiera sin buscarla.
    */

    /*
        =================================================================
        CAPA 2) LAS CUATRO BÁSICAS: FUNCTION, PREDICATE, CONSUMER, SUPPLIER
        =================================================================

        Se distinguen por lo que RECIBEN y lo que DEVUELVEN:

        | Interface       | Método             | Recibe | Devuelve | Idea          | Dónde la usa el JDK            |
        |-----------------|--------------------|--------|----------|---------------|--------------------------------|
        | Function<T, R>  | R apply(T t)       | un T   | un R     | TRANSFORMAR   | stream.map, computeIfAbsent    |
        | Predicate<T>    | boolean test(T t)  | un T   | boolean  | DECIDIR       | removeIf, stream.filter        |
        | Consumer<T>     | void accept(T t)   | un T   | nada     | HACER algo    | forEach                        |
        | Supplier<T>     | T get()            | nada   | un T     | PRODUCIR      | orElseGet, Stream.generate     |

        Una forma de recordarlas:
        - Function: entra algo, sale OTRA cosa (un usuario -> su correo).
        - Predicate: entra algo, sale sí/no (¿este pedido está pagado?).
        - Consumer: entra algo y se "consume": se guarda, se envía, se
          imprime. No devuelve nada; existe por sus efectos.
        - Supplier: no entra nada, sale algo: una fábrica (dame una lista
          nueva, dame la hora actual, dame el valor por defecto).
    */
    record User(String name, String email, boolean active) { }

    static boolean basicFourExample() {
        Function<User, String> toEmail = user -> user.email();
        Predicate<User> isActive = user -> user.active();
        List<String> sent = new ArrayList<>();
        Consumer<String> sendWelcome = email -> sent.add("bienvenida -> " + email);
        Supplier<List<String>> newList = () -> new ArrayList<>();

        List<User> users = new ArrayList<>(List.of(
                new User("Ana", "ana@correo.com", true),
                new User("Luis", "luis@correo.com", false)));

        for (User user : users) {
            if (isActive.test(user)) {
                sendWelcome.accept(toEmail.apply(user));
            }
        }
        users.removeIf(isActive.negate());                  // el JDK recibe un Predicate (CAPA 8: negate)

        List<String> fresh = newList.get();
        return sent.equals(List.of("bienvenida -> ana@correo.com"))
                && users.size() == 1 && fresh.isEmpty() && fresh != newList.get();   // una lista NUEVA cada vez
    }

    /*
        =================================================================
        CAPA 3) SUPPLIER Y LA EVALUACIÓN PEREZOSA
        =================================================================

        Supplier parece la más inútil ("no recibe nada"), pero resuelve un
        problema importante: calcular algo SOLO SI hace falta.

        Pasar un VALOR obliga a calcularlo antes de llamar al método,
        aunque el método no lo use. Pasar un SUPPLIER le da al método la
        "receta", y él decide si la ejecuta. A esto se le llama evaluación
        PEREZOSA (lazy).

        Ejemplos reales del JDK:
        - optional.orElse(valor) calcula el valor SIEMPRE;
          optional.orElseGet(supplier) solo si está vacío
          (part2_streams/OptionalUsage).
        - logger.fine(String) arma el mensaje siempre; logger.fine(Supplier)
          solo si ese nivel de log está activo. Con mensajes caros de
          construir (un toString enorme) y logs de depuración apagados en
          producción, la diferencia es real.
        - Objects.requireNonNull(x, Supplier<String>): el mensaje solo se
          arma si x es null.
    */
    private static final Logger LOG = Logger.getLogger(FunctionalInterfaces.class.getName());

    static boolean lazySupplierExample() {
        LOG.setLevel(Level.INFO);                           // FINE (depuración) está apagado
        int[] built = {0};
        Supplier<String> expensiveMessage = () -> {
            built[0]++;                                     // cuenta cuántas veces se construye
            return "estado completo del pedido...";
        };

        LOG.fine(expensiveMessage);                         // nivel apagado: el Supplier nunca se ejecuta
        boolean lazy = built[0] == 0;

        String eager = choose(true, "barato", expensiveMessage.get());   // con valor: se calcula aunque no se use
        return lazy && eager.equals("barato") && built[0] == 1;
    }

    static String choose(boolean useFirst, String first, String second) {
        return useFirst ? first : second;
    }

    /*
        =================================================================
        CAPA 4) DOS PARÁMETROS: BIFUNCTION, BIPREDICATE, BICONSUMER
        =================================================================

        El prefijo "Bi" significa "recibe DOS argumentos":

            BiFunction<T, U, R>   R apply(T t, U u)
            BiPredicate<T, U>     boolean test(T t, U u)
            BiConsumer<T, U>      void accept(T t, U u)

        Donde las usa el JDK: map.forEach(BiConsumer<clave, valor>),
        map.replaceAll(BiFunction), map.merge(k, v, BiFunction)
        (v5_collections/part2_sets_and_maps/Maps, CAPAS 7, 8 y 10).

        Lo que NO existe:
        - BiSupplier: un Supplier no recibe nada; "dos" de nada no tiene
          sentido. (Si quieres producir dos cosas, devuelve un record.)
        - TriFunction o funciones de 3+ parámetros. Si las necesitas:
          agrupa los datos en un record (lo más limpio), o crea tu propia
          interface (CAPA 9).
    */
    static boolean twoParametersExample() {
        BiFunction<Integer, Integer, Integer> discountedPrice = (price, percent) -> price - price * percent / 100;
        BiPredicate<String, Integer> longerThan = (text, length) -> text.length() > length;

        Map<String, Integer> stock = new HashMap<>(Map.of("teclado", 5, "mouse", 0));
        List<String> report = new ArrayList<>();
        BiConsumer<String, Integer> addLine = (product, units) -> {
            if (units == 0) {
                report.add(product + " agotado");
            }
        };
        stock.forEach(addLine);                             // el JDK pasa clave y valor
        stock.replaceAll((product, units) -> units + 10);   // BiFunction: nuevo valor desde clave y valor

        return discountedPrice.apply(500, 10) == 450
                && longerThan.test("hola", 3)
                && report.equals(List.of("mouse agotado"))
                && stock.equals(Map.of("teclado", 15, "mouse", 10));
    }

    /*
        =================================================================
        CAPA 5) OPERADORES: UNARYOPERATOR Y BINARYOPERATOR
        =================================================================

        Cuando la entrada y la salida son del MISMO tipo, hay nombres más
        cortos:

            UnaryOperator<T>    extends Function<T, T>          T apply(T t)
            BinaryOperator<T>   extends BiFunction<T, T, T>     T apply(T a, T b)

        No son interfaces "nuevas": son las de antes con los tipos
        repetidos. Existen porque el nombre comunica más ("transforma un
        texto en otro texto") y es más corto de escribir.

        Dónde aparecen:
        - list.replaceAll(UnaryOperator): cambia cada elemento por otro
          del mismo tipo.
        - stream.reduce(BinaryOperator): combina dos elementos en uno, una
          y otra vez, hasta quedar uno (part2_streams/StreamsBasics).
        - BinaryOperator.minBy(comparador) / maxBy: "de dos, quédate con
          el menor/mayor".
    */
    static boolean operatorsExample() {
        UnaryOperator<String> shout = text -> text.toUpperCase() + "!";
        BinaryOperator<Integer> sum = (a, b) -> a + b;
        BinaryOperator<String> shorter = BinaryOperator.minBy(Comparator.comparingInt(String::length));

        List<String> words = new ArrayList<>(List.of("hola", "java"));
        words.replaceAll(shout);

        int total = List.of(1, 2, 3, 4).stream().reduce(0, sum);   // ((((0+1)+2)+3)+4)

        Function<String, String> sameAsFunction = shout;    // un UnaryOperator ES una Function
        return words.equals(List.of("HOLA!", "JAVA!")) && total == 10
                && shorter.apply("sol", "luna").equals("sol")
                && sameAsFunction.apply("a").equals("A!");
    }

    /*
        =================================================================
        CAPA 6) LAS VARIANTES PRIMITIVAS Y CÓMO DESCIFRAR SUS NOMBRES
        =================================================================

        Los genéricos solo aceptan objetos (v4_generics/GenericsBasics,
        CAPA 10): Function<Integer, Integer> convierte cada int en Integer
        al entrar y al salir (boxing). En un ciclo de millones de números
        eso son millones de objetos (v1_fundamentals/part3_text_and_
        references/Wrappers, CAPA 7). Por eso existen versiones que
        trabajan directo con int, long y double. Son la mayoría de las 43.

        La regla de los nombres (con "Int"; igual para Long y Double):

        | Nombre               | Lee como                         | Método                     |
        |----------------------|----------------------------------|----------------------------|
        | IntPredicate         | RECIBE un int                    | boolean test(int)          |
        | IntFunction<R>       | RECIBE un int, devuelve un R     | R apply(int)               |
        | IntConsumer          | RECIBE un int                    | void accept(int)           |
        | IntSupplier          | DEVUELVE un int                  | int getAsInt()             |
        | ToIntFunction<T>     | recibe un T, DEVUELVE un int     | int applyAsInt(T)          |
        | IntUnaryOperator     | int -> int                       | int applyAsInt(int)        |
        | IntBinaryOperator    | (int, int) -> int                | int applyAsInt(int, int)   |
        | IntToLongFunction    | int -> long                      | long applyAsLong(int)      |
        | ObjIntConsumer<T>    | recibe un T y un int             | void accept(T, int)        |

        Resumen de la regla: el tipo AL PRINCIPIO es lo que RECIBE; "To..."
        es lo que DEVUELVE; los métodos que devuelven primitivo se llaman
        applyAsInt, getAsInt, etc. (no apply ni get).

        Detalles:
        - Solo hay versiones para int, long y double. Para byte, short,
          char y float se usa la de int o double.
        - La única de boolean es BooleanSupplier (para "recibe boolean"
          está Predicate).
        - Ya las usaste: comparingInt recibe una ToIntFunction
          (v5_collections/part4_tools/Sorting, CAPA 3), y el mapa de
          operaciones de Lambdas, CAPA 11, usa IntBinaryOperator.
    */
    record Order(String id, int total) { }

    static boolean primitivesExample() {
        IntPredicate isEven = n -> n % 2 == 0;
        IntUnaryOperator square = n -> n * n;
        IntBinaryOperator max = Math::max;
        ToIntFunction<Order> totalOf = Order::total;

        List<Order> orders = List.of(new Order("A", 300), new Order("B", 150));
        int sum = 0;
        for (Order order : orders) {
            sum += totalOf.applyAsInt(order);               // devuelve int: sin Integer de por medio
        }

        return isEven.test(4) && square.applyAsInt(7) == 49 && max.applyAsInt(3, 9) == 9 && sum == 450;
    }

    /*
        =================================================================
        CAPA 7) COMPONER FUNCIONES: ANDTHEN, COMPOSE, IDENTITY
        =================================================================

        Las interfaces del JDK traen métodos default (Interfaces, CAPA 5)
        para COMBINAR funciones pequeñas en una más grande, sin escribir
        una lambda nueva:

            f.andThen(g)     primero f, luego g con el resultado:   g(f(x))
            f.compose(g)     primero g, luego f con el resultado:   f(g(x))
            Function.identity()   devuelve lo mismo que recibe: x -> x

        La diferencia entre andThen y compose es SOLO el orden. Con
        plus1 = x + 1 y times2 = x * 2, aplicadas a 5:
            plus1.andThen(times2)   ->  (5 + 1) * 2 = 12
            plus1.compose(times2)   ->  (5 * 2) + 1 = 11
        andThen se lee en el orden en que ocurre; por eso es la más usada.

        Para qué: armar transformaciones por pasos con nombre, como una
        tubería. Cada paso es simple y se prueba solo; la composición dice
        el orden. Ejemplo real: convertir un título en un "slug" para una
        URL ("  Hola Mundo  " -> "hola-mundo").

        identity() sirve cuando una API pide una Function y no quieres
        transformar nada (por ejemplo, Collectors.toMap con la clave como
        el elemento mismo: part2_streams/AdvancedStreams).

        Consumer también tiene andThen: "haz esto y luego aquello" con el
        mismo valor. Supplier no tiene composición.
    */
    static final Function<String, String> TRIM = String::strip;
    static final Function<String, String> LOWER = String::toLowerCase;
    static final Function<String, String> DASHES = text -> text.replaceAll("\\s+", "-");
    static final Function<String, String> SLUG = TRIM.andThen(LOWER).andThen(DASHES);

    static boolean functionCompositionExample() {
        Function<Integer, Integer> plus1 = x -> x + 1;
        Function<Integer, Integer> times2 = x -> x * 2;

        List<String> audit = new ArrayList<>();
        Consumer<String> save = text -> audit.add("guardado " + text);
        Consumer<String> notify = text -> audit.add("notificado " + text);
        save.andThen(notify).accept("pedido-7");

        return plus1.andThen(times2).apply(5) == 12
                && plus1.compose(times2).apply(5) == 11
                && SLUG.apply("  Hola   Mundo Java ").equals("hola-mundo-java")
                && Function.<String>identity().apply("x").equals("x")
                && audit.equals(List.of("guardado pedido-7", "notificado pedido-7"));
    }

    /*
        =================================================================
        CAPA 8) COMPONER CONDICIONES: AND, OR, NEGATE, NOT
        =================================================================

        Predicate trae lógica booleana como métodos:

            p.and(q)              p Y q        (si p es false, q no se evalúa)
            p.or(q)               p O q        (si p es true, q no se evalúa)
            p.negate()            NO p
            Predicate.not(p)      NO p, como método static (Java 11)
            Predicate.isEqual(x)  "es igual a x" (seguro con null)

        Igual que && y || (v1_fundamentals/part1_first_steps/Operators),
        and y or son de CORTOCIRCUITO.

        Predicate.not existe porque negate() no se puede llamar sobre una
        referencia a método directamente: String::isBlank.negate() no
        compila ("method reference not expected here": una referencia a
        método no tiene tipo hasta que hay un destino; Lambdas, CAPA 3).
        Con not se lee bien, sobre todo en
        filtros que CONSERVAN lo que cumple:
            lines.stream().filter(Predicate.not(String::isBlank))   // solo las líneas con texto

        Para qué: armar filtros a partir de reglas con nombre, como en un
        buscador con casillas ("en stock", "con descuento", "envío
        gratis") que el usuario combina.
    */
    record Item(String name, int price, int stock, boolean freeShipping) { }

    static final Predicate<Item> IN_STOCK = item -> item.stock() > 0;
    static final Predicate<Item> CHEAP = item -> item.price() < 200;
    static final Predicate<Item> FREE_SHIPPING = Item::freeShipping;

    static boolean predicateCompositionExample() {
        List<Item> items = List.of(
                new Item("cable", 80, 10, false),
                new Item("mouse", 250, 3, true),
                new Item("funda", 150, 0, true));

        Predicate<Item> buyableDeal = IN_STOCK.and(CHEAP.or(FREE_SHIPPING));   // en stock Y (barato O envío gratis)

        List<String> deals = new ArrayList<>();
        for (Item item : items) {
            if (buyableDeal.test(item)) {
                deals.add(item.name());
            }
        }

        List<String> lines = List.of("uno", "  ", "dos", "");
        List<String> withText = lines.stream().filter(Predicate.not(String::isBlank)).toList();   // conserva las que tienen texto
        List<String> filled = new ArrayList<>(List.of("a", " ", "b"));
        filled.removeIf(String::isBlank);                             // removeIf QUITA las que cumplen: sin not

        return deals.equals(List.of("cable", "mouse"))
                && IN_STOCK.negate().test(items.get(2))
                && withText.equals(List.of("uno", "dos")) && filled.equals(List.of("a", "b"))
                && Predicate.isEqual(null).test(null);
    }

    /*
        =================================================================
        CAPA 9) TU PROPIA INTERFACE FUNCIONAL (CON COMPOSICIÓN)
        =================================================================

        Usa las del JDK siempre que encajen. Crea la tuya cuando:
        1) el NOMBRE aporta significado de negocio: Validator<User> dice
           más que Function<User, List<String>> (Interfaces, CAPA 8);
        2) necesitas lanzar excepciones checked (Lambdas, CAPA 10);
        3) necesitas 3 o más parámetros y un record no tiene sentido;
        4) quieres métodos default propios para combinarlas.

        Ejemplo: un validador que devuelve TODOS los errores (no solo el
        primero, como en un formulario; v3_exceptions/part2_in_practice/
        ErrorHandlingBestPractices). Con un método default and() se
        combinan validadores igual que los Predicate, y con un método
        static rule() se crean desde una condición y un mensaje. Es
        exactamente cómo están hechos Predicate y Comparator por dentro.
    */
    @FunctionalInterface
    interface Validator<T> {
        List<String> validate(T value);             // el único abstracto: lo que implementa la lambda

        default Validator<T> and(Validator<T> other) {
            return value -> {
                List<String> errors = new ArrayList<>(this.validate(value));
                errors.addAll(other.validate(value));
                return errors;
            };
        }

        static <T> Validator<T> rule(Predicate<T> isValid, String message) {
            return value -> isValid.test(value) ? List.of() : List.of(message);
        }
    }

    record SignUp(String email, String password, int age) { }

    static final Validator<SignUp> SIGN_UP_RULES =
            Validator.<SignUp>rule(form -> form.email().contains("@"), "correo inválido")
                    .and(Validator.rule(form -> form.password().length() >= 8, "contraseña muy corta"))
                    .and(Validator.rule(form -> form.age() >= 18, "debes ser mayor de edad"));

    static boolean customInterfaceExample() {
        List<String> errors = SIGN_UP_RULES.validate(new SignUp("ana.correo.com", "123", 20));
        List<String> none = SIGN_UP_RULES.validate(new SignUp("ana@correo.com", "segura123", 20));

        return errors.equals(List.of("correo inválido", "contraseña muy corta")) && none.isEmpty();
    }

    /*
        =================================================================
        CAPA 10) SOBRECARGA CON INTERFACES FUNCIONALES: AMBIGÜEDAD
        =================================================================

        Si un método está SOBRECARGADO (v1_fundamentals/part2_control_flow/
        Methods) con dos interfaces funcionales distintas en la misma
        posición, una lambda puede encajar en las dos:

            static void run(Consumer<String> action) { ... }
            static void run(Function<String, String> action) { ... }

            run(s -> s.toUpperCase());
            // ERROR: reference to run is ambiguous
            //   both method run(Consumer<String>) in Amb and method
            //   run(Function<String,String>) in Amb match

        s.toUpperCase() devuelve un String (encaja en Function) y también
        es una llamada cuyo valor se puede descartar (encaja en Consumer;
        Lambdas, CAPA 4). Java no puede elegir.

        Se puede desempatar con un cuerpo de bloque (con return es
        Function, sin return es Consumer) o con un cast, pero la lección
        es de DISEÑO: no sobrecargues un método con distintas interfaces
        funcionales en el mismo parámetro. Dale nombres distintos:
        runAndIgnore / runAndMap. Así lo hace el JDK: Stream tiene map,
        mapToInt, mapToLong... en lugar de cuatro map sobrecargados.
    */
    static String transformName(String name, Function<String, String> transformation) {
        return transformation.apply(name);
    }

    static void inspectName(String name, Consumer<String> inspection) {   // nombre distinto: sin ambigüedad
        inspection.accept(name);
    }

    static boolean overloadExample() {
        List<String> seen = new ArrayList<>();
        inspectName("ana", s -> seen.add(s));
        return transformName("ana", s -> s.toUpperCase()).equals("ANA") && seen.equals(List.of("ana"));
    }

    /*
        =================================================================
        CAPA 11) CÓMO ELEGIR
        =================================================================

        | Necesitas algo que...                         | Usa                                  |
        |-----------------------------------------------|--------------------------------------|
        | transforma un T en un R                       | Function<T, R>                       |
        | transforma un T en otro T                     | UnaryOperator<T>                     |
        | decide sí/no sobre un T                       | Predicate<T>                         |
        | hace algo con un T sin devolver               | Consumer<T>                          |
        | produce un T sin recibir nada (o perezoso)    | Supplier<T>                          |
        | combina dos T en uno                          | BinaryOperator<T>                    |
        | lo mismo con dos parámetros distintos         | BiFunction / BiPredicate / BiConsumer |
        | trabaja con int, long o double                | la variante primitiva (IntPredicate, ToIntFunction...) |
        | ejecutar algo sin datos                       | Runnable                             |
        | comparar dos T                                | Comparator<T>                        |
        | tiene significado de negocio, o lanza checked | tu propia @FunctionalInterface       |
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Crear interfaces propias con la misma forma que una del JDK y
           sin un nombre que aporte nada (CAPA 1).

        2) Pasar un valor caro donde un Supplier lo evitaría
           (orElse vs orElseGet, logs) (CAPA 3).

        3) Buscar una TriFunction: agrupa en un record (CAPA 4).

        4) Function<Integer, Integer> en código con muchos números en
           lugar de IntUnaryOperator: boxing innecesario (CAPA 6).

        5) Confundir el orden de andThen y compose (CAPA 7).

        6) String::isBlank.negate(): no compila; usa Predicate.not
           (CAPA 8).

        7) Sobrecargar un método con dos interfaces funcionales en el mismo
           parámetro (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - java.util.function: 43 interfaces estándar para que las lambdas
          encajen en todo el JDK.
        - Las cuatro básicas: Function (transforma), Predicate (decide),
          Consumer (hace), Supplier (produce).
        - Supplier = evaluación perezosa: solo se calcula si hace falta.
        - "Bi" = dos parámetros. Operator = entrada y salida del mismo tipo.
        - Primitivas: el tipo al inicio es lo que recibe, "To..." lo que
          devuelve; métodos applyAsInt, getAsInt...
        - Componer: andThen (en orden), compose (al revés), identity;
          and, or, negate, Predicate.not.
        - Tu interface: cuando el nombre importa o hay checked; agrégale
          default para componer.
        - No sobrecargues con interfaces funcionales distintas.
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
