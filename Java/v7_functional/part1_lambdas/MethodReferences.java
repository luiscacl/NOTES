package v7_functional.part1_lambdas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/*
    =====================================================================
    REFERENCIAS A MÉTODOS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de functional/: guía 3 de 6  (part1_lambdas)
    Anterior:  part1_lambdas/FunctionalInterfaces
    Siguiente: part2_streams/StreamsBasics

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es una referencia a método
        CAPA 2)  Los cuatro tipos
        CAPA 3)  Tipo 1: método static
        CAPA 4)  Tipo 2: método de un objeto concreto (bound)
        CAPA 5)  La diferencia escondida: el objeto se evalúa UNA vez
        CAPA 6)  Tipo 3: método de "cualquier objeto" del tipo (unbound)
        CAPA 7)  Tipo 4: constructores y arreglos
        CAPA 8)  La trampa de ArrayList::new con claves Integer
        CAPA 9)  Cómo elige Java el método, y cuándo es ambiguo
        CAPA 10) this::, super:: y métodos privados con nombre
        CAPA 11) ¿Lambda o referencia?
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class MethodReferences {

    public static void main(String[] args) {
        check(whatIsItExample(), "CAPA 1");
        check(staticExample(), "CAPA 3");
        check(boundExample(), "CAPA 4");
        check(evaluatedOnceExample(), "CAPA 5");
        check(unboundExample(), "CAPA 6");
        check(constructorExample(), "CAPA 7");
        check(arrayListCapacityTrapExample(), "CAPA 8");
        check(resolutionExample(), "CAPA 9");
        check(new OrderProcessor(1_000).thisExample(), "CAPA 10");
        System.out.println("MethodReferences: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UNA REFERENCIA A MÉTODO
        =================================================================

        Muchas lambdas solo llaman a un método que ya existe, pasándole
        sus parámetros tal cual:

            name -> name.toUpperCase()
            text -> Integer.parseInt(text)
            item -> System.out.println(item)

        Una REFERENCIA A MÉTODO es la forma corta de escribir eso: nombras
        el método con :: y Java arma la lambda por ti:

            String::toUpperCase
            Integer::parseInt
            System.out::println

        Se lee "usa el método toUpperCase de String". No se ejecuta nada
        al escribirla (igual que una lambda; Lambdas, CAPA 2): es una
        manera de PASAR el método.

        Es exactamente lo mismo que la lambda equivalente: necesita un tipo
        destino (Lambdas, CAPA 3), produce un objeto que implementa la
        interface funcional, y por dentro usa el mismo mecanismo
        (invokedynamic; Lambdas, CAPA 12), con la diferencia de que
        normalmente no hace falta generar un método lambda$...: el método ya
        existe. (Una excepción: String[]::new sí genera uno, porque crear
        un arreglo no es un método.)

        Por qué existe: quita ruido. En name -> name.toUpperCase() el
        "name ->" no aporta nada; String::toUpperCase dice solo lo
        importante: QUÉ operación se aplica.
    */
    static boolean whatIsItExample() {
        Function<String, String> lambda = name -> name.toUpperCase();
        Function<String, String> reference = String::toUpperCase;   // lo mismo, sin el "name ->"

        return lambda.apply("ana").equals(reference.apply("ana"));
    }

    /*
        =================================================================
        CAPA 2) LOS CUATRO TIPOS
        =================================================================

        | Tipo                                   | Ejemplo              | Lambda equivalente            |
        |----------------------------------------|----------------------|-------------------------------|
        | 1) Método static                       | Integer::parseInt    | s -> Integer.parseInt(s)      |
        | 2) Método de un objeto CONCRETO        | System.out::println  | x -> System.out.println(x)    |
        |    (bound: "atado" a ese objeto)       | validator::isValid   | x -> validator.isValid(x)     |
        | 3) Método de CUALQUIER objeto del tipo | String::length       | s -> s.length()               |
        |    (unbound: el objeto llega después)  | String::compareTo    | (a, b) -> a.compareTo(b)      |
        | 4) Constructor                         | ArrayList::new       | () -> new ArrayList<>()       |
        |                                        | String[]::new        | n -> new String[n]            |

        La clave para no confundirte: a la izquierda de :: puede haber
        - un NOMBRE DE CLASE (Integer, String): tipo 1 si el método es
          static, tipo 3 si es de instancia;
        - una EXPRESIÓN que da un objeto (System.out, validator, this):
          tipo 2;
        - y new a la derecha: tipo 4.
    */

    /*
        =================================================================
        CAPA 3) TIPO 1: MÉTODO STATIC
        =================================================================

            Clase::metodoStatic       ==       (args) -> Clase.metodoStatic(args)

        Los parámetros de la interface se pasan, en orden, al método.
        Ejemplos frecuentes:
            Integer::parseInt        texto -> número
            Math::max                (a, b) -> el mayor (IntBinaryOperator)
            String::valueOf          cualquier cosa -> texto
            Objects::nonNull         filtrar los null (Predicate)

        También con tus métodos static: Lambdas::readConfig se usó en
        Lambdas, CAPA 10.
    */
    static boolean isValidSku(String sku) {
        return sku.matches("[A-Z]{3}-\\d{3}");      // tres letras, guion, tres dígitos
    }

    static boolean staticExample() {
        Function<String, Integer> parse = Integer::parseInt;
        Predicate<String> validSku = MethodReferences::isValidSku;  // un método static propio

        List<String> skus = new ArrayList<>(List.of("ABC-123", "abc-123", "XYZ-999"));
        skus.removeIf(validSku.negate());

        return parse.apply("42") == 42 && skus.equals(List.of("ABC-123", "XYZ-999"));
    }

    /*
        =================================================================
        CAPA 4) TIPO 2: MÉTODO DE UN OBJETO CONCRETO (BOUND)
        =================================================================

            objeto::metodo       ==       (args) -> objeto.metodo(args)

        El objeto queda "atado" (bound) a la referencia: cada llamada usa
        ESE objeto. Es lo que haces cuando tienes un servicio o una
        colección y quieres pasar uno de sus métodos:

            System.out::println        imprime en la consola estándar
            allowed::contains          "¿está en mi lista de permitidos?"
            emailSender::send          "manda con este emisor"

        Detalle útil: si el método devuelve algo y la interface es void
        (Consumer), el valor se descarta. Por eso list::add sirve como
        Consumer aunque add devuelva boolean (Lambdas, CAPA 4).
    */
    static boolean boundExample() {
        List<String> allowedCountries = List.of("MX", "US", "CA");
        Predicate<String> isAllowed = allowedCountries::contains;    // atada a ESA lista

        List<String> log = new ArrayList<>();
        Consumer<String> addToLog = log::add;                        // add devuelve boolean: se descarta
        addToLog.accept("inicio");

        return isAllowed.test("MX") && !isAllowed.test("BR") && log.equals(List.of("inicio"));
    }

    /*
        =================================================================
        CAPA 5) LA DIFERENCIA ESCONDIDA: EL OBJETO SE EVALÚA UNA VEZ
        =================================================================

        objeto::metodo y () -> objeto.metodo() NO son idénticas en un
        detalle importante: CUÁNDO se calcula "objeto".

        - Referencia: la expresión de la izquierda se evalúa UNA vez, al
          CREAR la referencia. Todas las llamadas usan ese resultado.
        - Lambda: el cuerpo completo, incluida la expresión, se evalúa en
          CADA llamada.

        Consecuencias:
        1) Si la izquierda es una llamada (createConnection()::send), con
           referencia se crea UNA conexión; con lambda, una por llamada.
        2) Si la izquierda es null, la referencia lanza
           NullPointerException AL CREARSE (Java revisa el objeto en ese
           momento). La lambda se crea sin problema y falla después, al
           llamarla, quizá lejos de donde estaba el error.

        Normalmente evaluar una vez es lo que quieres. Solo tenlo presente
        cuando la izquierda tenga efectos o pueda ser null.
    */
    static int builders = 0;

    static StringBuilder newBuilder() {
        builders++;
        return new StringBuilder("x");
    }

    static boolean evaluatedOnceExample() {
        builders = 0;
        Supplier<String> reference = newBuilder()::toString;   // newBuilder() se llama AQUÍ, una vez
        reference.get();
        reference.get();
        boolean once = builders == 1;

        Supplier<String> lambda = () -> newBuilder().toString();   // newBuilder() en cada llamada
        lambda.get();
        lambda.get();
        boolean everyCall = builders == 3;

        String missing = null;
        boolean referenceFailsAtCreation;
        try {
            Supplier<Integer> length = missing::length;        // NullPointerException al crearla
            referenceFailsAtCreation = length == null;
        } catch (NullPointerException e) {
            referenceFailsAtCreation = true;
        }
        Supplier<Integer> lazyLength = () -> missing.length();  // se crea bien; fallaría al llamarla

        return once && everyCall && referenceFailsAtCreation && lazyLength != null;
    }

    /*
        =================================================================
        CAPA 6) TIPO 3: MÉTODO DE "CUALQUIER OBJETO" DEL TIPO (UNBOUND)
        =================================================================

            Clase::metodoDeInstancia   ==   (obj, resto...) -> obj.metodoDeInstancia(resto...)

        Es la más confusa al principio. String::length no dice DE QUÉ
        String: el objeto todavía no existe. Llegará como el PRIMER
        parámetro de la interface, y los demás parámetros (si hay) se
        pasan al método.

            Function<String, Integer>          String::length
                -> s -> s.length()                         (1 parámetro: el objeto)
            BiPredicate<String, String>        String::startsWith
                -> (texto, prefijo) -> texto.startsWith(prefijo)
            Comparator<String>                 String::compareToIgnoreCase
                -> (a, b) -> a.compareToIgnoreCase(b)

        Por eso un mismo String::algo puede encajar en interfaces con
        distinto número de parámetros: depende de cuántos tenga el método.

        Es el tipo que más usarás con streams y comparadores:
            Comparator.comparing(Employee::name)        // el getter de cada empleado
            users.stream().map(User::email)             // el correo de cada usuario
    */
    record Employee(String name, int salary) { }

    static boolean unboundExample() {
        Function<String, Integer> length = String::length;
        BiPredicate<String, String> startsWith = String::startsWith;   // el primero es el objeto
        Comparator<String> ignoreCase = String::compareToIgnoreCase;
        ToIntFunction<Employee> salary = Employee::salary;             // getter de un record

        List<String> names = new ArrayList<>(List.of("beto", "Ana", "carlos"));
        names.sort(ignoreCase);

        return length.apply("java") == 4
                && startsWith.test("java", "ja")
                && names.equals(List.of("Ana", "beto", "carlos"))
                && salary.applyAsInt(new Employee("Ana", 30_000)) == 30_000;
    }

    /*
        =================================================================
        CAPA 7) TIPO 4: CONSTRUCTORES Y ARREGLOS
        =================================================================

            Clase::new      ==   (args) -> new Clase(args)
            Tipo[]::new     ==   n -> new Tipo[n]

        ¿Qué constructor usa, si la clase tiene varios? El que encaje con
        los parámetros de la interface destino:
            Supplier<ArrayList<String>>                ArrayList::new  -> new ArrayList<>()
            Function<Collection<String>, ArrayList<..>> ArrayList::new  -> new ArrayList<>(coleccion)

        Usos típicos:
        - Supplier como "fábrica": Collectors.toCollection(TreeSet::new),
          un parámetro Supplier<T> para crear objetos sin saber su clase.
        - Convertir datos en objetos: names.stream().map(Customer::new).
        - Arreglos del tipo correcto: list.toArray(String[]::new)
          (v5_collections/part4_tools/CollectionsAndArraysUtilities,
          CAPA 10).
    */
    record Customer(String name) { }

    static <T> List<T> createMany(int count, Supplier<T> factory) {
        List<T> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            created.add(factory.get());                 // no sabe qué clase crea: la fábrica decide
        }
        return created;
    }

    static boolean constructorExample() {
        Supplier<List<String>> newList = ArrayList::new;
        Function<String, Customer> toCustomer = Customer::new;     // usa el constructor que recibe un String
        IntFunction<String[]> newArray = String[]::new;

        List<StringBuilder> buffers = createMany(3, StringBuilder::new);

        return newList.get().isEmpty()
                && toCustomer.apply("Ana").equals(new Customer("Ana"))
                && newArray.apply(4).length == 4
                && buffers.size() == 3 && buffers.get(0) != buffers.get(1);   // objetos distintos
    }

    /*
        =================================================================
        CAPA 8) LA TRAMPA DE ARRAYLIST::NEW CON CLAVES INTEGER
        =================================================================

        Para agrupar (v5_collections/part2_sets_and_maps/Maps, CAPA 9) se
        escribe:
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(x);

        Es tentador "acortarlo" a ArrayList::new. Lo que pasa depende del
        tipo de la clave, porque computeIfAbsent espera una
        Function<Clave, Lista>: la referencia debe usar un constructor que
        RECIBA la clave.

        - Con claves String NO compila: no existe new ArrayList(String).
              // ERROR: incompatible types: invalid constructor reference
              //   no suitable constructor found for ArrayList(String)
        - Con claves Integer SÍ COMPILA... y usa new ArrayList(int
          capacidadInicial) (v5_collections/part1_basics/Lists, CAPA 2),
          con la CLAVE como capacidad. Agrupar por año (2024) crea listas
          con 2024 lugares reservados; agrupar por un id de 7 dígitos
          reserva millones de lugares por grupo. Funciona "bien" pero
          desperdicia memoria en silencio.

        Lección: una referencia a constructor elige el constructor por los
        PARÁMETROS de la interface, no por lo que tú tenías en mente. Aquí
        la lambda k -> new ArrayList<>() es la correcta: ignora la clave a
        propósito.
    */
    static boolean arrayListCapacityTrapExample() {
        Map<Integer, List<String>> byYear = new HashMap<>();
        byYear.computeIfAbsent(2024, ArrayList::new).add("pedido-1");   // compila: new ArrayList<>(2024)

        Map<Integer, List<String>> correct = new HashMap<>();
        correct.computeIfAbsent(2024, year -> new ArrayList<>()).add("pedido-1");

        // El resultado se VE igual; la diferencia (2024 lugares reservados) no se ve desde fuera.
        return byYear.equals(correct);
    }

    /*
        =================================================================
        CAPA 9) CÓMO ELIGE JAVA EL MÉTODO, Y CUÁNDO ES AMBIGUO
        =================================================================

        Con el tipo destino, Java sabe qué parámetros llegan y qué se
        espera de vuelta. Entonces busca, entre los métodos con ese nombre
        (puede haber sobrecargas), uno que encaje, probando las dos
        lecturas posibles con Clase::metodo:
        - static, con todos los parámetros;
        - de instancia, con el primero como objeto (CAPA 6).

        Si encaja exactamente uno, listo. Si no encaja ninguno, error. Y
        si encajan DOS, es ambiguo. El caso clásico:

            Function<Integer, String> f = Integer::toString;
            // ERROR: incompatible types: invalid method reference
            //   reference to toString is ambiguous
            //   both method toString(int) in Integer and method toString() in Integer match

        Integer tiene un toString(int) static (lectura 1: le paso el
        número) y un toString() de instancia (lectura 3: el número es el
        objeto). Los dos sirven para "Integer -> String". Soluciones: una
        lambda (n -> n.toString()), o un método sin ambigüedad
        (String::valueOf).

        Las referencias también adaptan tipos como lo haría una llamada
        normal: boxing/unboxing (String::length sirve para
        Function<String, Integer> y para ToIntFunction<String>) y
        ensanchamiento (int a long).
    */
    static boolean resolutionExample() {
        Function<Integer, String> withValueOf = String::valueOf;    // sin ambigüedad
        Function<Integer, String> withLambda = n -> n.toString();   // o una lambda

        Function<String, Integer> boxed = String::length;           // devuelve int, se convierte a Integer
        ToIntFunction<String> primitive = String::length;           // la misma referencia, sin boxing

        BiFunction<Integer, Integer, Integer> max = Math::max;      // elige max(int, int) entre sus sobrecargas

        return withValueOf.apply(7).equals("7") && withLambda.apply(7).equals("7")
                && boxed.apply("abc") == 3 && primitive.applyAsInt("abc") == 3
                && max.apply(3, 9) == 9;
    }

    /*
        =================================================================
        CAPA 10) THIS::, SUPER:: Y MÉTODOS PRIVADOS CON NOMBRE
        =================================================================

        Dentro de un método de instancia:
        - this::metodo es el tipo 2 con el objeto actual: "usa MI método".
        - super::metodo llama a la versión de la clase PADRE (útil si la
          sobrescribiste y quieres pasar la original; v2_oop/part2_pillars/
          Inheritance).

        Su mejor uso: sacar una condición o transformación a un método
        PRIVADO con un nombre claro y pasarlo con this:: (o Clase:: si es
        static). Es la solución a las lambdas largas (Lambdas, CAPA 13):

            orders.removeIf(order -> order.total() > 0 && !order.cancelled()
                    && order.total() <= limit && ...);           // hay que leerla para entenderla
            orders.removeIf(this::isNotBillable);                // el nombre lo explica

        El método se prueba por separado, aparece con su nombre en los
        stack traces y se reutiliza.
    */
    record Order(String id, int total, boolean cancelled) { }

    static final class OrderProcessor {
        private final int creditLimit;

        OrderProcessor(int creditLimit) {
            this.creditLimit = creditLimit;
        }

        private boolean isBillable(Order order) {       // la regla, con nombre y en un solo lugar
            return !order.cancelled() && order.total() > 0 && order.total() <= creditLimit;
        }

        List<Order> billable(List<Order> orders) {
            List<Order> result = new ArrayList<>(orders);
            result.removeIf(Predicate.not(this::isBillable));   // usa el método de ESTE procesador
            return result;
        }

        boolean thisExample() {
            List<Order> orders = List.of(
                    new Order("A", 300, false),
                    new Order("B", 300, true),
                    new Order("C", 5_000, false),
                    new Order("D", 0, false));
            return billable(orders).equals(List.of(new Order("A", 300, false)));
        }
    }

    /*
        =================================================================
        CAPA 11) ¿LAMBDA O REFERENCIA?
        =================================================================

        Prefiere la REFERENCIA cuando la lambda solo pasa sus parámetros
        a un método, en el mismo orden:
            x -> process(x)            ->  this::process
            s -> s.trim()              ->  String::trim
            () -> new HashMap<>()      ->  HashMap::new

        Prefiere la LAMBDA cuando:
        - hay que cambiar el orden o agregar argumentos:
              s -> s.substring(0, 3)        (no hay referencia que lo diga)
              p -> p.price() * quantity
        - la referencia sería ambigua o engañosa (CAPAS 8 y 9);
        - la referencia es MENOS clara que la lambda. Por ejemplo,
          Objects::isNull frente a x -> x == null: las dos están bien;
          elige la que se lea mejor en ese lugar.

        IntelliJ y otros IDEs sugieren el cambio automáticamente; acéptalo
        cuando de verdad se lea mejor, no por reflejo.
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Escribir paréntesis: String::length() no es una referencia (no
           compila). La referencia NO llama al método, lo nombra (CAPA 1).

        2) Confundir el tipo 2 con el 3: en String::length no hay un
           String todavía; llega como primer parámetro (CAPA 6).

        3) Olvidar que objeto::metodo evalúa el objeto una vez, al crearse,
           y falla en ese momento si es null (CAPA 5).

        4) computeIfAbsent(clave, ArrayList::new) con claves Integer: usa
           la clave como capacidad (CAPA 8).

        5) Integer::toString como Function<Integer, String>: ambiguo; usa
           String::valueOf (CAPA 9).

        6) Llamar métodos sobre una referencia: String::isBlank.negate()
           no compila ("method reference not expected here"); usa
           Predicate.not (FunctionalInterfaces, CAPA 8).

        7) Forzar una referencia donde una lambda es más clara (CAPA 11).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Una referencia a método es una lambda que solo llama a un
          método: Clase::metodo, objeto::metodo, Clase::new, Tipo[]::new.
        - Cuatro tipos: static; de un objeto concreto (bound); de cualquier
          objeto del tipo, que llega como primer parámetro (unbound);
          constructor.
        - Bound: el objeto se evalúa una vez, al crearla (y NPE si es null).
        - El constructor o la sobrecarga se eligen por los parámetros de
          la interface destino: cuidado con ArrayList::new y con métodos
          ambiguos como Integer::toString.
        - this::metodoPrivado convierte una lambda larga en un nombre claro.
        - Referencia si solo reenvía parámetros; lambda si los cambia.
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
