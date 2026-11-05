package v1_fundamentals.part1_first_steps;

import java.util.ArrayList;
import java.util.List;

/*
    =====================================================================
    VARIABLES — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 3 de 14  (part1_first_steps)
    Anterior:  part1_first_steps/PrimitiveTypes
    Siguiente: part1_first_steps/Operators

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es una variable
        CAPA 2)  Asignar: = no es "igual a"
        CAPA 3)  Una variable local debe tener valor antes de usarse
        CAPA 4)  Los cuatro lugares donde vive una variable
        CAPA 5)  Alcance (scope): dónde existe cada variable
        CAPA 6)  final: variables que no se reasignan
        CAPA 7)  var: inferencia de tipos
        CAPA 8)  Las trampas de var
        CAPA 9)  Cómo nombrar variables
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class Variables {

    public static void main(String[] args) {
        check(declarationExample(), "CAPA 1");
        check(assignmentCopiesValueExample(), "CAPA 2");
        check(definiteAssignmentExample(), "CAPA 3");
        check(scopeExample(), "CAPA 5");
        check(finalExample(), "CAPA 6");
        check(varExample(), "CAPA 7");
        check(varTrapsExample(), "CAPA 8");
        check(namingExample(), "CAPA 9");
        System.out.println("Variables: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UNA VARIABLE
        =================================================================

        Una VARIABLE es un NOMBRE para un lugar en memoria donde se
        guarda un valor. Piensa en una caja con una etiqueta: la etiqueta
        es el nombre, lo de adentro es el valor, y en Java la caja tiene
        además una FORMA fija (el tipo) que decide qué cabe en ella.

        Tres operaciones distintas que conviene no confundir:

            int stock;          DECLARAR: crear la caja (nombre + tipo)
            stock = 10;         ASIGNAR: meter un valor
            int stock = 10;     DECLARAR E INICIALIZAR en una sola línea
                                (la forma más común)

        Por qué conviene declarar e inicializar juntos: la variable nunca
        existe "vacía", y quien lee ve de inmediato su valor inicial.
        Declara cada variable lo más cerca posible de donde se usa
        (no todas arriba del método, como en código antiguo de C).

        Se pueden declarar varias en una línea (int x = 1, y = 2;), pero
        se lee peor y dificulta comentar cada una. Una por línea.
    */
    static boolean declarationExample() {
        int stock;          // declarada, todavía sin valor
        stock = 10;         // asignada
        int reserved = 3;   // declarada e inicializada a la vez

        int available = stock - reserved;
        return available == 7;
    }

    /*
        =================================================================
        CAPA 2) ASIGNAR: = NO ES "IGUAL A"
        =================================================================

        En matemáticas, x = 5 es una afirmación ("x vale 5"). En Java,
        = es una ORDEN: "calcula lo que está a la DERECHA y guárdalo en
        la variable de la IZQUIERDA". Por eso esto es válido:

            count = count + 1;   // "toma count, súmale 1, guárdalo en count"

        Para COMPARAR se usa == (v1_fundamentals/Operators).

        Detalle fundamental: al asignar una variable PRIMITIVA a otra, se
        COPIA EL VALOR. Después de la copia, las dos variables son
        independientes: cambiar una no afecta a la otra.

        (Con objetos se copia la REFERENCIA, no el objeto, y ahí las dos
        variables sí comparten. Es el tema de v1_fundamentals/ReferencesAndNull.)
    */
    static boolean assignmentCopiesValueExample() {
        int original = 10;
        int copy = original;   // copia el VALOR 10
        original = 20;         // cambia solo original

        int count = 5;
        count = count + 1;     // lee 5, calcula 6, guarda 6

        return copy == 10 && original == 20 && count == 6;
    }

    /*
        =================================================================
        CAPA 3) UNA VARIABLE LOCAL DEBE TENER VALOR ANTES DE USARSE
        =================================================================

        Si intentas LEER una variable local que todavía no tiene valor,
        no compila:

            int total;
            System.out.println(total);
            // ERROR: variable total might not have been initialized

        Por qué Java lo exige (y C no): en C, una variable sin inicializar
        tiene "basura", lo que hubiera en esa memoria antes, y el programa
        usa ese valor al azar. Es una fuente famosa de errores imposibles
        de reproducir. Java prefiere obligarte a decidir el valor.

        El compilador hace un análisis llamado "asignación definitiva":
        revisa TODOS los caminos posibles del código. Si en todos se
        asigna la variable antes de leerla, compila. Esto permite
        declarar primero y asignar en cada rama de un if:
    */
    static String shippingLabel(boolean express) {
        String label;                // sin valor todavía
        if (express) {
            label = "Envío exprés";
        } else {
            label = "Envío estándar";
        }
        return label;                // OK: los dos caminos la asignaron
        // Si quitaras el else, no compilaría: cuando express es false, label no tendría valor.
    }

    static boolean definiteAssignmentExample() {
        return shippingLabel(true).equals("Envío exprés") && shippingLabel(false).equals("Envío estándar");
    }

    /*
        Recuerda (ClassesAndObjects, CAPA 3): esta regla es solo para
        variables LOCALES. Los campos de una clase sí reciben un valor por
        defecto (0, false, null), porque el compilador no puede seguir
        desde qué método se van a leer.
    */

    /*
        =================================================================
        CAPA 4) LOS CUATRO LUGARES DONDE VIVE UNA VARIABLE
        =================================================================

        Según DÓNDE se declara, una variable vive más o menos tiempo y se
        comporta distinto:

        | Tipo                 | Dónde se declara           | Vive...                          | Valor por defecto |
        |----------------------|----------------------------|----------------------------------|-------------------|
        | Local                | dentro de un método/bloque | hasta que termina su bloque      | No (hay que asignarla) |
        | Parámetro            | entre los () de un método  | mientras se ejecuta el método    | Lo que pase quien llama |
        | Campo de instancia   | en la clase, sin static    | mientras viva su objeto          | Sí (0, false, null) |
        | Campo static         | en la clase, con static    | mientras la clase esté cargada   | Sí (0, false, null) |

        Las locales y los parámetros viven en la PILA (stack) de la JVM:
        cada llamada a un método crea su espacio, y al terminar se
        descarta todo de golpe. Por eso son muy rápidas, y por eso una
        variable local "desaparece" al salir del método.
        Los campos viven dentro de su objeto, en el MONTÓN (heap)
        (v12_jvm/part1_memory/StackAndHeapMemory).

        Los campos se estudian en ClassesAndObjects y Static. Esta guía se
        enfoca en las variables locales.
    */

    /*
        =================================================================
        CAPA 5) ALCANCE (SCOPE): DÓNDE EXISTE CADA VARIABLE
        =================================================================

        El ALCANCE de una variable local va desde la línea donde se
        declara hasta la llave } que cierra el bloque donde se declaró.
        Fuera de ese bloque, la variable no existe: usarla no compila.

        Por qué existe esta regla: cuanto menor es el alcance de una
        variable, menos código puede tocarla por error y menos tienes que
        tener en la cabeza al leer. Por eso se declara cada variable en el
        bloque más pequeño posible.

        Casos importantes:
        - La variable de un for (int i) solo existe dentro del for. Por
          eso puedes tener varios for seguidos que usan i.
        - Una variable declarada dentro de un if o de un ciclo
          desaparece al salir de él.
    */
    static boolean scopeExample() {
        int total = 0;                       // existe hasta el final del método

        for (int i = 1; i <= 3; i++) {       // i existe solo dentro de este for
            int square = i * i;              // square existe solo en cada vuelta del for
            total += square;
        }
        // Aquí ni i ni square existen.

        for (int i = 10; i <= 11; i++) {     // otra i, nueva: la anterior ya no existe
            total += i;
        }

        {                                    // un bloque suelto también crea alcance (casi nunca se usa)
            int temporary = 100;
            total += temporary;
        }

        return total == 1 + 4 + 9 + 10 + 11 + 100;
    }

    /*
        Diferencia con JavaScript:
        - En JavaScript, var tiene alcance de FUNCIÓN y se "eleva"
          (hoisting): puedes usarla antes de declararla y vale undefined.
          let y const sí tienen alcance de bloque.
        - Java funciona como let/const de JavaScript, pero MÁS estricto:
          no puedes declarar otra variable local con el mismo nombre
          dentro de un bloque interno.

            int price = 10;
            if (true) {
                int price = 20;   // ERROR: variable price is already defined in method ...
            }

        Por qué Java prohíbe ese "sombreado" de locales: si estuviera
        permitido, dentro del if price sería otra variable y la de afuera
        seguiría valiendo 10, una confusión clásica. Lo que SÍ se permite
        es que un parámetro o variable local tenga el mismo nombre que un
        CAMPO; para eso existe this.campo (ClassesAndObjects, CAPA 5).
    */

    /*
        =================================================================
        CAPA 6) FINAL: VARIABLES QUE NO SE REASIGNAN
        =================================================================

        final en una variable significa: se le asigna valor UNA vez y
        después no se puede reasignar.

            final int maxAttempts = 3;
            maxAttempts = 4;   // ERROR: cannot assign a value to final variable maxAttempts

        Una variable final puede declararse sin valor y asignarse después
        (una sola vez en cada camino). Se llama "final en blanco", y el
        compilador usa el mismo análisis de la CAPA 3 para verificarlo.

        Por qué usar final:
        - Comunica la INTENCIÓN: "este valor no va a cambiar". Quien lee
          ya no tiene que buscar si se modifica más abajo.
        - Evita reasignaciones accidentales.
        - Es obligatorio en la práctica para variables usadas dentro de
          lambdas y clases anónimas, que exigen variables "efectivamente
          finales" (NestedClasses, CAPA 5).

        RECUERDA: final fija la VARIABLE, no el objeto. Una lista final se
        puede seguir llenando (Encapsulation, CAPA 7).
    */
    static boolean finalExample() {
        final int maxAttempts = 3;

        final String status;                 // final en blanco
        int failedAttempts = 4;
        if (failedAttempts >= maxAttempts) {
            status = "bloqueado";
        } else {
            status = "activo";
        }
        // status = "otro";   // ERROR: variable status might already have been assigned

        final List<String> log = new ArrayList<>();
        log.add("intento fallido");          // permitido: la VARIABLE no cambia, la lista sí

        return status.equals("bloqueado") && log.size() == 1;
    }

    /*
        Constantes: una variable final que además es static y tiene un
        valor fijo es una CONSTANTE (static final int MAX_ATTEMPTS = 3;),
        con nombre en MAYÚSCULAS. Su lugar es la clase, no un método
        (Static, CAPA 3).
    */

    /*
        =================================================================
        CAPA 7) VAR: INFERENCIA DE TIPOS
        =================================================================

        Desde Java 10, en lugar de escribir el tipo de una variable local
        puedes escribir var, y el compilador lo DEDUCE a partir del valor
        con que la inicializas:

            var name = "Luis";                     // el compilador deduce String
            var age = 30;                          // deduce int
            var names = new ArrayList<String>();   // deduce ArrayList<String>

        LO MÁS IMPORTANTE (como decía tu antigua TypingGuide): var NO vuelve
        dinámico a Java. El tipo se decide al COMPILAR, igual que si lo
        hubieras escrito, y no cambia nunca:

            var age = 30;
            age = "treinta";   // ERROR: incompatible types: String cannot be converted to int

        Es solo una forma de no ESCRIBIR el tipo cuando ya es obvio. No
        es el var de JavaScript.

        Por qué se agregó: con tipos largos, escribir el tipo dos veces
        era ruido:
            Map<String, List<Integer>> scoresByStudent = new HashMap<String, List<Integer>>();
            var scoresByStudent = new HashMap<String, List<Integer>>();

        Dónde SÍ se puede usar:
        - variables locales CON valor inicial
        - la variable de un for y de un for-each
        - recursos de un try-with-resources
        - parámetros de lambdas

        Dónde NO (no compila):
            var x;               // sin valor: no hay de dónde deducir
            var x = null;        // null no dice de qué tipo es
            var nums = {1, 2};   // un inicializador de arreglo solo, tampoco
            campos, parámetros de métodos y tipos de retorno
        Por qué los campos y parámetros no: son parte de la "cara" de una
        clase o método; su tipo debe estar escrito para quien los usa.
    */
    static boolean varExample() {
        var name = "Luis";
        var age = 30;
        var names = new ArrayList<String>();
        names.add(name);

        int total = 0;
        for (var grade : List.of(8, 9, 10)) {   // var en for-each: deduce Integer
            total += grade;
        }

        // El tipo deducido es real y fijo: el compilador sabe que names es ArrayList<String>.
        return name.length() == 4 && age + 1 == 31 && names.get(0).equals("Luis") && total == 27;
    }

    /*
        =================================================================
        CAPA 8) LAS TRAMPAS DE VAR
        =================================================================

        TRAMPA 1: var deduce el tipo CONCRETO, no la interface.
            var names = new ArrayList<String>();   // es ArrayList<String>, no List<String>
        Normalmente no importa dentro de un método, pero si después
        quieres asignarle otro tipo de lista, no podrás. Con el tipo
        escrito eliges la vista (Polymorphism, CAPA 5):
            List<String> names = new ArrayList<>();

        TRAMPA 2: con números, el tipo deducido puede no ser el que
        imaginas. var total = 0 deduce int, aunque más abajo sumes
        decimales. Y el operador += convierte en silencio al tipo de la
        variable (v1_fundamentals/Operators), así que los decimales se
        pierden SIN ningún error.

        Lo que sí puede avisarte es el compilador si activas sus
        advertencias extra con -Xlint (javac -Xlint:all Archivo.java):
            warning: [lossy-conversions] implicit cast from double to int
                     in compound assignment is possibly lossy
        Es una buena razón para compilar siempre con -Xlint: muchas
        trampas que compilan "bien" las detecta. (Este método suprime la
        advertencia con @SuppressWarnings solo porque el error es a
        propósito.)
    */
    @SuppressWarnings("lossy-conversions")
    static boolean varTrapsExample() {
        var total = 0;        // int, porque 0 es un literal int
        total += 2.5;         // compila: += convierte 2.5 + 0 a int... y queda 2
        total += 2.5;         // 2 + 2.5 = 4.5 -> 4

        double rightTotal = 0;   // con el tipo escrito, no hay sorpresa
        rightTotal += 2.5;
        rightTotal += 2.5;

        return total == 4 && rightTotal == 5.0;
    }

    /*
        TRAMPA 3: cuando el valor de la derecha NO deja ver el tipo, var
        esconde información:
            var result = calculator.process(order);   // ¿qué es result?
        Quien lee tiene que ir a buscar qué devuelve process.

        REGLA PRÁCTICA para var:
        - Úsalo cuando el tipo es OBVIO por la derecha:
              var customer = new Customer("Ana");
              var names = List.of("Ana", "Luis");
        - Úsalo para quitar ruido de tipos genéricos largos.
        - NO lo uses cuando el tipo no se ve, con literales numéricos
          ambiguos, o cuando quieres declarar con una interface.
        - var es una palabra "contextual": por compatibilidad, todavía
          puedes tener una variable llamada var. No lo hagas.
    */

    /*
        =================================================================
        CAPA 9) CÓMO NOMBRAR VARIABLES
        =================================================================

        REGLAS DEL LENGUAJE (si no se cumplen, no compila):
        - Pueden tener letras, dígitos, _ y $.
        - NO pueden empezar con dígito: 2items no es válido.
        - No pueden ser palabras reservadas: class, int, new...
        - Distinguen mayúsculas: total y Total son distintas.
        - Se permiten letras de cualquier idioma (año, niño), aunque en
          la práctica los equipos usan solo ASCII para evitar problemas
          con teclados y codificaciones.

        CONVENCIONES (no son obligatorias, pero TODO el mundo Java las
        sigue, y romperlas hace que tu código se vea extraño):

        | Qué                 | Estilo            | Ejemplo                 |
        |---------------------|-------------------|-------------------------|
        | variables, métodos  | camelCase         | totalPrice, getName()   |
        | clases, interfaces  | PascalCase        | OrderService            |
        | constantes          | MAYÚSCULAS_GUION  | MAX_ATTEMPTS            |
        | paquetes            | minúsculas        | v1_fundamentals.part1_...  |
        | booleanos           | pregunta sí/no    | isActive, hasStock      |

        BUENOS NOMBRES (lo que distingue a un profesional):
        - Di QUÉ guarda, no su tipo: customers, no list; elapsedMillis,
          no number.
        - Incluye la UNIDAD cuando importa: timeoutSeconds, weightKg,
          priceMxn. Un "timeout = 30" sin unidad ha causado errores reales.
        - Evita abreviaturas crípticas: customerAddress, no custAddr.
        - El largo del nombre, proporcional a su alcance: i está bien en
          un for de tres líneas; un campo que se usa en toda la clase
          merece un nombre completo.
        - Nada de _ ni $ al inicio: el $ lo usa el compilador para
          nombres que genera (NestedClasses, CAPA 6).
    */
    static boolean namingExample() {
        int timeoutSeconds = 30;           // unidad en el nombre
        double weightKg = 2.5;
        boolean hasStock = true;           // se lee como pregunta
        int año = 2026;                    // válido (letras de cualquier idioma), pero mejor: year

        return timeoutSeconds * 1000 == 30_000 && weightKg > 0 && hasStock && año == 2026;
    }

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Confundir = (asignar) con == (comparar) (CAPA 2).

        2) Leer una variable local sin haberle dado valor en todos los
           caminos (CAPA 3).

        3) Declarar todas las variables al inicio del método en lugar de
           donde se usan (CAPA 1).

        4) Usar una variable fuera del bloque donde se declaró (CAPA 5).

        5) Declarar una local con el mismo nombre que otra de un bloque
           exterior (CAPA 5).

        6) Creer que final hace inmutable al objeto (CAPA 6).

        7) Creer que var hace dinámico a Java (CAPA 7).

        8) var con literales numéricos que deducen int cuando querías
           double (CAPA 8).

        9) var donde el tipo no se ve a la derecha (CAPA 8).

        10) Nombres sin significado ni unidad: x, data, temp, timeout
            (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Variable = nombre + tipo + valor. Declarar, asignar, o ambos.
        - = asigna (derecha -> izquierda). Con primitivos, copia el valor.
        - Las locales deben tener valor en todos los caminos antes de
          leerse; los campos tienen valor por defecto.
        - Viven en cuatro lugares: local, parámetro, campo, campo static.
        - Alcance: desde la declaración hasta la } de su bloque. Hazlo lo
          más pequeño posible. No se pueden repetir nombres de locales en
          bloques internos.
        - final: una sola asignación. Comunica intención.
        - var: el compilador deduce el tipo, que sigue siendo fijo. Solo
          en locales con valor inicial y cuando el tipo es obvio.
        - camelCase, nombres que digan qué guardan y en qué unidad.
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
