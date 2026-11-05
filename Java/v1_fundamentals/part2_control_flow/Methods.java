package v1_fundamentals.part2_control_flow;

import java.util.ArrayList;
import java.util.List;

/*
    =====================================================================
    MÉTODOS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 8 de 14  (part2_control_flow)
    Anterior:  part2_control_flow/Loops
    Siguiente: part3_text_and_references/Strings

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Por qué existen los métodos
        CAPA 2)  Anatomía de un método
        CAPA 3)  return: devolver un resultado
        CAPA 4)  Java SIEMPRE pasa los argumentos por valor
        CAPA 5)  Sobrecarga: mismo nombre, distintos parámetros
        CAPA 6)  Cómo elige Java entre sobrecargas
        CAPA 7)  Varargs: un número variable de argumentos
        CAPA 8)  Recursión: un método que se llama a sí mismo
        CAPA 9)  La pila de llamadas
        CAPA 10) Cómo diseñar buenos métodos
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Methods {

    public static void main(String[] args) {
        check(reuseExample(), "CAPA 1");
        check(returnExample(), "CAPA 3");
        check(passByValueExample(), "CAPA 4");
        check(overloadingExample(), "CAPA 5");
        check(overloadResolutionExample(), "CAPA 6");
        check(varargsExample(), "CAPA 7");
        check(recursionExample(), "CAPA 8");
        check(callStackExample(), "CAPA 9");
        check(goodDesignExample(), "CAPA 10");
        System.out.println("Methods: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) POR QUÉ EXISTEN LOS MÉTODOS
        =================================================================

        Un MÉTODO es un bloque de código con NOMBRE que hace una tarea, y
        que puedes ejecutar ("llamar") las veces que quieras, desde
        donde quieras.

        Por qué son la herramienta más importante para organizar código:
        1) NO REPETIR: escribes la lógica una vez y la usas en muchos
           lugares. Si hay un error, se corrige en un solo sitio.
        2) DARLE NOMBRE a una idea: applyDiscount(price) se entiende sin
           leer cómo funciona por dentro (es abstracción a pequeña
           escala).
        3) DIVIDIR un problema grande en pasos pequeños, cada uno fácil
           de entender.
        4) PROBAR por partes: un método pequeño se puede verificar solo,
           como hace cada check(...) de estas guías.

        Si vienes de JavaScript: un método es una función, pero en Java
        SIEMPRE vive dentro de una clase. No hay funciones sueltas.
    */
    static double priceWithTax(double price) {
        return price * 1.16;
    }

    static boolean reuseExample() {
        // La misma regla, usada tres veces, escrita una sola vez.
        double keyboard = priceWithTax(100);
        double mouse = priceWithTax(50);
        double monitor = priceWithTax(1000);
        return Math.abs(keyboard + mouse + monitor - 1334) < 1e-9;
    }

    /*
        =================================================================
        CAPA 2) ANATOMÍA DE UN MÉTODO
        =================================================================

            static double  priceWithTax  (double price)  { ... }
            ^^^^^^ ^^^^^^  ^^^^^^^^^^^^   ^^^^^^^^^^^^^  ^^^^^
            modif. retorno nombre         parámetros     cuerpo

        - MODIFICADORES: public, private, static... (AccessModifiers
          y Static). Opcionales.
        - TIPO DE RETORNO: qué devuelve. void si no devuelve nada.
        - NOMBRE: un verbo en camelCase que diga qué hace:
          calculateTotal, sendEmail, isValid.
        - PARÁMETROS: variables que recibe, con su tipo, separadas por
          coma. Pueden ser cero: ().
        - CUERPO: el código entre llaves.

        PARÁMETRO vs ARGUMENTO (como lo explicaba tu antigua TypingGuide):
            static int add(int a, int b)   <- a y b son PARÁMETROS (en la declaración)
            add(2, 3)                      <- 2 y 3 son ARGUMENTOS (en la llamada)
        Al llamar, cada argumento se COPIA en su parámetro, en orden
        (CAPA 4).

        FIRMA: el nombre más la lista de TIPOS de los parámetros:
            add(int, int)
        El tipo de retorno y los nombres de los parámetros NO forman parte
        de la firma. Esto importa para la sobrecarga (CAPA 5).
    */

    /*
        =================================================================
        CAPA 3) RETURN: DEVOLVER UN RESULTADO
        =================================================================

        return hace dos cosas: TERMINA el método en ese momento, y
        entrega el valor a quien lo llamó.

        - Un método void puede usar return; (sin valor) para salir antes.
        - Un método que devuelve algo DEBE devolverlo en TODOS los caminos
          posibles. Si algún camino llega al final sin return, no compila:
              static String sign(int n) {
                  if (n > 0) return "positivo";
                  if (n < 0) return "negativo";
              }   // ERROR: missing return statement   (¿y si n es 0?)
          Es el mismo análisis de "todos los caminos" que en Variables,
          CAPA 3: Java no te deja olvidar un caso.

        Un método devuelve UN solo valor. Para devolver varios datos
        relacionados, se devuelve un objeto que los agrupe, normalmente
        un record (Records, CAPA 10).
    */
    static String sign(int n) {
        if (n > 0) {
            return "positivo";
        }
        if (n < 0) {
            return "negativo";
        }
        return "cero";          // sin esta línea no compilaría
    }

    record Stats(int min, int max, double average) { }

    static Stats stats(int[] values) {
        int min = values[0];
        int max = values[0];
        int sum = 0;
        for (int value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }
        return new Stats(min, max, (double) sum / values.length);   // tres datos en un solo valor
    }

    static boolean returnExample() {
        Stats result = stats(new int[] {4, 9, 2});
        return sign(5).equals("positivo") && sign(0).equals("cero")
                && result.min() == 2 && result.max() == 9 && result.average() == 5.0;
    }

    /*
        =================================================================
        CAPA 4) JAVA SIEMPRE PASA LOS ARGUMENTOS POR VALOR
        =================================================================

        Este es uno de los temas más malentendidos de Java, incluso por
        gente con años de experiencia.

        La regla es UNA y no tiene excepciones: al llamar un método, el
        valor de cada argumento se COPIA en el parámetro. El método
        trabaja con su copia.

        Lo que confunde es QUÉ se copia:

        1) PRIMITIVOS: se copia el valor (el número). El método no puede
           cambiar la variable de quien lo llamó. Por eso un swap(a, b)
           que intercambia dos int NO funciona en Java.

        2) OBJETOS: la variable no guarda el objeto, guarda una
           REFERENCIA (su dirección). Lo que se copia es la REFERENCIA.
           Entonces:
           - El método SÍ puede MODIFICAR el objeto (agregar a una lista):
             las dos referencias apuntan al MISMO objeto.
           - El método NO puede hacer que la variable de quien llamó
             apunte a OTRO objeto: si reasigna su parámetro, solo cambia
             su copia de la dirección.

        Analogía: te paso una COPIA de la dirección de mi casa. Con ella
        puedes ir y pintar la casa (modificar el objeto), y yo veré la
        casa pintada. Pero si tachas la dirección en tu papel y escribes
        otra, mi papel sigue diciendo mi dirección.

        Esto es "paso por valor de la referencia". Java NO tiene "paso por
        referencia" (como el & de C++ o el ref de C#). JavaScript funciona
        exactamente igual que Java en esto.
    */
    static void swap(int a, int b) {
        int temporary = a;
        a = b;           // solo cambia la copia local
        b = temporary;
    }

    static void addItem(List<String> cart, String item) {
        cart.add(item);                    // modifica el objeto compartido: quien llamó lo ve
    }

    static void replaceCart(List<String> cart) {
        cart = new ArrayList<>(List.of("otra cosa"));   // reasigna SOLO la copia de la referencia
        cart.add("tampoco se ve");
    }

    static boolean passByValueExample() {
        int x = 1;
        int y = 2;
        swap(x, y);
        boolean primitivesUnchanged = x == 1 && y == 2;

        List<String> cart = new ArrayList<>();
        addItem(cart, "teclado");
        boolean objectModified = cart.equals(List.of("teclado"));

        replaceCart(cart);
        boolean referenceNotReplaced = cart.equals(List.of("teclado"));

        return primitivesUnchanged && objectModified && referenceNotReplaced;
    }

    /*
        Consecuencia de diseño: como un método PUEDE modificar los objetos
        que recibe, eso debe ser evidente por su nombre (addItem) o estar
        documentado. Un método llamado calculateTotal(cart) que además
        vacía el carrito sorprendería a cualquiera (CAPA 10).
        (Las referencias se estudian a fondo en
        v1_fundamentals/part3_text_and_references/ReferencesAndNull.)
    */

    /*
        =================================================================
        CAPA 5) SOBRECARGA: MISMO NOMBRE, DISTINTOS PARÁMETROS
        =================================================================

        SOBRECARGA (overloading) es tener varios métodos con el MISMO
        NOMBRE pero distinta lista de parámetros (en cantidad, tipo u
        orden). Java elige cuál ejecutar según los argumentos.

        Por qué existe: para que la misma IDEA tenga un solo nombre
        aunque trabaje con distintos datos. Por eso existe un solo
        System.out.println, que acepta int, double, String, boolean...
        en lugar de printlnInt, printlnDouble...

        Como el tipo de retorno no es parte de la firma (CAPA 2), NO se
        puede sobrecargar solo cambiando el retorno:
            static int sum(int a, int b)
            static double sum(int a, int b)
            // ERROR: method sum(int,int) is already defined
        Si Java permitiera eso, ante sum(2, 3) no sabría cuál llamar.

        (No confundir con SOBRESCRITURA, que es una hija redefiniendo un
        método heredado con la MISMA firma: Inheritance, CAPA 5.)

        Los ejemplos de tu Base.java:
    */
    static int sum(int a, int b) {
        return a + b;
    }

    static int sum(int a, int b, int c) {
        return a + b + c;
    }

    static double sum(double a, double b) {
        return a + b;
    }

    static boolean overloadingExample() {
        return sum(2, 3) == 5
                && sum(2, 3, 4) == 9
                && sum(2.5, 1.5) == 4.0;
    }

    /*
        Buena práctica: las sobrecargas deben hacer LO MISMO. Si dos
        métodos hacen cosas distintas, dales nombres distintos. Y si una
        sobrecarga es solo un atajo con valores por defecto, que delegue
        en la versión completa (como los constructores en
        ClassesAndObjects, CAPA 6).
    */

    /*
        =================================================================
        CAPA 6) CÓMO ELIGE JAVA ENTRE SOBRECARGAS
        =================================================================

        La elección se hace al COMPILAR, según los tipos de los
        argumentos (Polymorphism, CAPA 6). Si varias sobrecargas podrían
        servir, Java prueba en FASES y se queda con la primera que
        encuentre:

            1) Coincidencia EXACTA, o por AMPLIACIÓN (int -> long -> double)
            2) Con BOXING (int -> Integer; v1_fundamentals/Wrappers)
            3) Con VARARGS (CAPA 7)

        Dentro de una fase, gana la MÁS ESPECÍFICA. Si no hay una más
        específica que las demás, no compila:
            static void pay(long amount, int installments)
            static void pay(int amount, long installments)
            pay(100, 3);   // ERROR: reference to pay is ambiguous

        Por qué este orden de fases: para no cambiar el significado de
        código viejo. El boxing y los varargs llegaron en Java 5; si
        hubieran tenido prioridad, programas que ya existían habrían
        empezado a llamar a otro método al recompilar.
    */
    static String kind(long value) {
        return "long (ampliación)";
    }

    static String kind(Integer value) {
        return "Integer (boxing)";
    }

    static String kind(int... values) {
        return "varargs";
    }

    static String kindWithoutLong(Integer value) {
        return "Integer (boxing)";
    }

    static String kindWithoutLong(int... values) {
        return "varargs";
    }

    static boolean overloadResolutionExample() {
        return kind(5).equals("long (ampliación)")               // fase 1 gana, aunque Integer "parezca" más cercano
                && kindWithoutLong(5).equals("Integer (boxing)")  // sin fase 1, gana la fase 2
                && kind().equals("varargs");                     // sin argumentos, solo varargs aplica
    }

    /*
        =================================================================
        CAPA 7) VARARGS: UN NÚMERO VARIABLE DE ARGUMENTOS
        =================================================================

            static int total(int... values)

        Los tres puntos significan "cero o más int". Puedes llamarlo con
        cualquier cantidad de argumentos:
            total()          total(5)          total(5, 10, 15)

        Por dentro, values es simplemente un ARREGLO (int[]). Java crea
        el arreglo por ti con los argumentos que pasaste. Por eso también
        puedes pasarle un arreglo directamente.

        Reglas:
        - Solo puede haber UN parámetro varargs, y debe ser el ÚLTIMO
          (si no, Java no sabría dónde terminan los demás).
        - Puede recibir cero argumentos: el arreglo llega vacío, no null.
          Si tu método necesita al menos uno, exígelo como parámetro
          normal antes del varargs.

        Los usas todo el tiempo: String.format("%s %d", a, b),
        List.of(a, b, c), Arrays.asList(...).

        Costo: cada llamada crea un arreglo nuevo. En código muy
        llamado, el JDK ofrece sobrecargas fijas (List.of tiene versiones
        de 0 a 10 elementos antes de la de varargs, por eso).
    */
    static int total(int... values) {
        int sum = 0;
        for (int value : values) {     // values es un int[]
            sum += value;
        }
        return sum;
    }

    // Al menos un valor obligatorio: el primero es parámetro normal, el resto varargs.
    static int maxOf(int first, int... others) {
        int max = first;
        for (int value : others) {
            max = Math.max(max, value);
        }
        return max;
    }

    static boolean varargsExample() {
        return total() == 0
                && total(5) == 5
                && total(5, 10, 15) == 30
                && total(new int[] {1, 2, 3}) == 6   // también acepta un arreglo
                && maxOf(3) == 3
                && maxOf(3, 9, 4) == 9;
    }

    /*
        =================================================================
        CAPA 8) RECURSIÓN: UN MÉTODO QUE SE LLAMA A SÍ MISMO
        =================================================================

        Un método RECURSIVO resuelve un problema llamándose a sí mismo
        con una versión MÁS PEQUEÑA del mismo problema.

        Todo método recursivo necesita dos partes:
        1) CASO BASE: el caso tan pequeño que se responde directo, sin
           volver a llamarse. Sin él, se llamaría para siempre.
        2) PASO RECURSIVO: se llama a sí mismo con un problema más chico,
           que se ACERCA al caso base.

        Ejemplo: factorial. 5! = 5 x 4 x 3 x 2 x 1.
        Observa que 5! = 5 x 4!, y 4! = 4 x 3!... hasta 1! = 1.
            factorial(n) = n x factorial(n - 1)    paso recursivo
            factorial(1) = 1                       caso base

        Por qué usar recursión si un ciclo podría hacerlo: hay problemas
        cuya estructura ES recursiva (árboles, carpetas dentro de
        carpetas, expresiones dentro de expresiones), y ahí la solución
        recursiva es mucho más natural (algorithms/RecursionAndBacktracking,
        algorithms/TreesAndGraphs).

        Dos costos que hay que conocer:
        1) Una recursión mal planteada puede repetir trabajo enorme. El
           Fibonacci "ingenuo" recalcula los mismos valores una y otra
           vez: para fibonacci(20) hace casi 22 000 llamadas. La versión
           con un ciclo hace 20 pasos.
        2) Cada llamada ocupa espacio en la pila (CAPA 9). Si la
           recursión es muy profunda (o no tiene caso base), la pila se
           llena y la JVM lanza StackOverflowError.
    */
    static long factorial(int n) {
        if (n <= 1) {
            return 1;                      // caso base
        }
        return n * factorial(n - 1);       // paso recursivo: problema más chico
    }

    static int fibonacciCalls;

    static long fibonacciNaive(int n) {
        fibonacciCalls++;
        if (n < 2) {
            return n;
        }
        return fibonacciNaive(n - 1) + fibonacciNaive(n - 2);   // recalcula los mismos valores muchas veces
    }

    static long fibonacciIterative(int n) {
        long previous = 0;
        long current = 1;
        for (int i = 0; i < n; i++) {
            long next = previous + current;
            previous = current;
            current = next;
        }
        return previous;
    }

    static int depth;

    static void recurseForever() {
        depth++;
        recurseForever();                  // sin caso base
    }

    static boolean recursionExample() {
        fibonacciCalls = 0;
        long naive = fibonacciNaive(20);

        boolean overflowDetected;
        depth = 0;
        try {
            recurseForever();
            overflowDetected = false;
        } catch (StackOverflowError e) {
            overflowDetected = depth > 1000;   // llegó a miles de llamadas antes de llenar la pila
        }

        return factorial(5) == 120
                && naive == 6765 && fibonacciIterative(20) == 6765
                && fibonacciCalls == 21_891           // ¡casi 22 000 llamadas para un solo número!
                && overflowDetected;
    }

    /*
        =================================================================
        CAPA 9) LA PILA DE LLAMADAS
        =================================================================

        ¿Cómo sabe Java a dónde regresar cuando un método termina? Con la
        PILA DE LLAMADAS (call stack).

        Cada vez que llamas un método, la JVM pone encima de la pila un
        "marco" (frame) con sus parámetros, sus variables locales y el
        punto al que debe regresar. Cuando el método termina, su marco se
        quita y la ejecución sigue donde se quedó quien lo llamó. Es una
        pila como de platos: el último que entra es el primero que sale.

            main()  llama a  level1()  llama a  level2()  llama a  level3()

            pila:   [ level3 ]   <- arriba: el que se está ejecutando
                    [ level2 ]
                    [ level1 ]
                    [ main   ]

        Por esto:
        - Las variables locales "desaparecen" al terminar el método: su
          marco se descarta (Variables, CAPA 4).
        - La recursión infinita llena la pila: StackOverflowError (CAPA 8).
        - Un STACK TRACE (lo que ves cuando el programa falla) es
          justamente una foto de esta pila: el método donde ocurrió el
          error arriba, y debajo, quién lo llamó, y quién llamó a ese...
          Se lee de arriba hacia abajo para ir del síntoma a la causa.

        Tu programa puede ver su propia pila:
    */
    static List<String> level1() {
        return level2();
    }

    static List<String> level2() {
        return level3();
    }

    static List<String> level3() {
        List<String> names = new ArrayList<>();
        StackTraceElement[] stack = new Throwable().getStackTrace();   // foto de la pila en este momento
        for (int i = 0; i < 3; i++) {
            names.add(stack[i].getMethodName());
        }
        return names;
    }

    static boolean callStackExample() {
        // El de arriba de la pila es el que se está ejecutando; debajo, quién lo llamó.
        return level1().equals(List.of("level3", "level2", "level1"));
    }

    /*
        =================================================================
        CAPA 10) CÓMO DISEÑAR BUENOS MÉTODOS
        =================================================================

        Lo que distingue un método profesional:

        1) HACE UNA SOLA COSA, y su nombre la dice. Si para nombrarlo
           necesitas "y" (validateAndSaveAndNotify), son tres métodos.

        2) ES CORTO. Si no cabe en la pantalla, probablemente hace varias
           cosas. Extrae partes a métodos con nombre.

        3) POCOS PARÁMETROS. Más de 3 o 4 es difícil de leer y fácil de
           equivocar (¿el tercer int era el año o el mes?). Agrúpalos en un
           objeto o record.

        4) SIN PARÁMETROS BOOLEANOS que cambian el comportamiento.
               formatPrice(price, true)       // ¿qué significa true?
           Mejor dos métodos con nombre: formatPriceWithSymbol(price) y
           formatPlainPrice(price).

        5) SEPARA COMANDOS DE CONSULTAS (ClassesAndObjects, CAPA 4): un
           método que devuelve algo no debería además cambiar estado.

        6) SIN EFECTOS SORPRESA: no modifiques los objetos que recibes a
           menos que el nombre lo diga (CAPA 4). Lo ideal, cuando se
           puede, son FUNCIONES PURAS: mismo resultado para los mismos
           argumentos, sin modificar nada (Static, CAPA 4).

        7) VALIDA AL ENTRAR con cláusulas de guarda (Conditionals,
           CAPA 4) y falla con un mensaje claro.

        8) NO DEVUELVAS null para "no hay resultados": devuelve una lista
           vacía. Quien llama puede recorrerla sin revisar null.
    */
    record ShippingRequest(double weightKg, String country, boolean express) { }

    // Un parámetro con nombre en lugar de tres sueltos; sin booleanos crípticos en la llamada.
    static double shippingCost(ShippingRequest request) {
        if (request.weightKg() <= 0) {
            throw new IllegalArgumentException("El peso debe ser mayor que cero");
        }
        double base = request.country().equals("MX") ? 100 : 400;
        return request.express() ? base * 2 : base;
    }

    static List<String> overdueTitles(List<String> titles, List<Integer> daysLate) {
        List<String> overdue = new ArrayList<>();          // nunca null: si no hay, queda vacía
        for (int i = 0; i < titles.size(); i++) {
            if (daysLate.get(i) > 0) {
                overdue.add(titles.get(i));
            }
        }
        return overdue;
    }

    static boolean goodDesignExample() {
        double cost = shippingCost(new ShippingRequest(2.5, "MX", true));
        List<String> none = overdueTitles(List.of("Clean Code"), List.of(0));

        return cost == 200 && none.isEmpty();   // se puede usar sin revisar null
    }

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Creer que Java pasa objetos "por referencia" y esperar que
           reasignar un parámetro cambie la variable de quien llamó
           (CAPA 4).

        2) Esperar que un método cambie un primitivo recibido (swap)
           (CAPA 4).

        3) Olvidar un return en algún camino (CAPA 3).

        4) Intentar sobrecargar cambiando solo el tipo de retorno (CAPA 5).

        5) Sobrecargas que hacen cosas distintas, o ambiguas (CAPAS 5 y 6).

        6) Varargs que no aceptan "cero argumentos" de forma sensata
           (CAPA 7).

        7) Recursión sin caso base, o que no se acerca a él (CAPA 8).

        8) Recursión ingenua que repite trabajo exponencial (CAPA 8).

        9) Métodos largos que hacen varias cosas, con muchos parámetros o
           booleanos crípticos (CAPA 10).

        10) Devolver null en lugar de una colección vacía (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Método: código con nombre, reutilizable. En Java siempre dentro
          de una clase.
        - Firma = nombre + tipos de parámetros (sin el retorno).
          Parámetros en la declaración; argumentos en la llamada.
        - return termina y devuelve; todos los caminos deben devolver. Para
          varios valores, un record.
        - Paso por valor SIEMPRE: con primitivos se copia el valor; con
          objetos se copia la referencia (puedes modificar el objeto, no
          reemplazar la variable de quien llamó).
        - Sobrecarga: mismo nombre, distintos parámetros; se elige al
          compilar: exacta/ampliación, luego boxing, luego varargs.
        - Varargs: Tipo... nombre, es un arreglo, al final, uno solo.
        - Recursión: caso base + paso que se acerca a él. Cuidado con el
          trabajo repetido y con la profundidad.
        - Pila de llamadas: un marco por llamada; el stack trace es su
          foto.
        - Buenos métodos: una cosa, cortos, pocos parámetros, sin
          booleanos crípticos, sin efectos sorpresa, sin devolver null.
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
