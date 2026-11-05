package v2_oop.part1_basics;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/*
    =====================================================================
    STATIC — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 2 de 14  (part1_basics)
    Anterior:  part1_basics/ClassesAndObjects
    Siguiente: part1_basics/AccessModifiers

    Requisito: ClassesAndObjects (clase, objeto, campos, métodos,
    constructores, this).

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué significa static: de la clase, no del objeto
        CAPA 2)  Campos static: una sola copia compartida
        CAPA 3)  Constantes: static final
        CAPA 4)  Métodos static
        CAPA 5)  Clases utilitarias
        CAPA 6)  Métodos de fábrica estáticos
        CAPA 7)  Bloques static e inicialización de la clase
        CAPA 8)  import static
        CAPA 9)  Por qué main es static
        CAPA 10) Cuándo NO usar static
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Static {

    public static void main(String[] args) {
        check(staticFieldExample(), "CAPA 2");
        check(constantsExample(), "CAPA 3");
        check(staticMethodsExample(), "CAPA 4");
        check(utilityClassExample(), "CAPA 5");
        check(staticFactoryExample(), "CAPA 6");
        check(staticInitializationExample(), "CAPA 7");
        check(hiddenDependencyExample(), "CAPA 10");
        System.out.println("Static: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ SIGNIFICA STATIC: DE LA CLASE, NO DEL OBJETO
        =================================================================

        Hasta ahora, todo lo que declarabas en una clase pertenecía a
        CADA OBJETO: cada Book tiene SU título, SU página actual.

        static cambia eso: lo que se marca static pertenece a la CLASE
        misma. Existe UNA sola vez, sin importar cuántos objetos crees,
        e incluso si no creas ninguno.

        Analogía: un salón de clases.
        - Cada alumno tiene SU cuaderno           -> miembro de instancia
        - El pizarrón es UNO para todo el salón   -> miembro static
        Si un alumno escribe en el pizarrón, todos ven el cambio. Si
        escribe en su cuaderno, solo él.

        Se puede aplicar a:
        - campos              (CAPAS 2 y 3)
        - métodos             (CAPAS 4, 5 y 6)
        - bloques             (CAPA 7)
        - clases anidadas     (ver NestedClasses)

        Y se usa con el NOMBRE DE LA CLASE, no con un objeto:
            LibraryCard.issuedCount()     // en vez de card.issuedCount()
    */

    /*
        =================================================================
        CAPA 2) CAMPOS STATIC: UNA SOLA COPIA COMPARTIDA
        =================================================================

        Ejemplo: las credenciales de una biblioteca llevan un número
        consecutivo (LIB-0001, LIB-0002...). Cada credencial tiene SU
        número (campo de instancia), pero el contador que dice "cuál es
        el siguiente número" es UNO para todas (campo static).
    */
    static final class LibraryCard {
        private static int nextNumber = 1;   // UNO para toda la clase

        private final int number;            // UNO por cada credencial
        private final String holder;

        LibraryCard(String holder) {
            this.holder = requireNonNull(holder, "holder");
            this.number = nextNumber;   // dentro de la clase se puede usar sin el nombre
            nextNumber++;
        }

        // Método de instancia: usa datos del objeto Y puede usar los static.
        String describe() {
            return formatNumber(number) + " (" + holder + ")";
        }

        // Método static: solo datos de la clase. No hay "this" (CAPA 4).
        static int issuedCount() {
            return nextNumber - 1;
        }

        static String formatNumber(int number) {
            return "LIB-" + String.format("%04d", number);
        }
    }

    static boolean staticFieldExample() {
        int before = LibraryCard.issuedCount();

        LibraryCard ana = new LibraryCard("Ana");
        LibraryCard luis = new LibraryCard("Luis");

        // Cada objeto tiene su número; el contador es compartido.
        boolean consecutive = luis.number == ana.number + 1;
        boolean sharedCounter = LibraryCard.issuedCount() == before + 2;
        boolean formatted = LibraryCard.formatNumber(7).equals("LIB-0007")
                && ana.describe().equals(LibraryCard.formatNumber(ana.number) + " (Ana)");

        return consecutive && sharedCounter && formatted;
    }

    /*
        Cómo se ve en memoria:

            Clase LibraryCard (existe una vez)
            └── nextNumber = 3

            objeto ana                objeto luis
            ├── number = 1            ├── number = 2
            └── holder = "Ana"        └── holder = "Luis"

        Detalles técnicos:
        - Java PERMITE escribir ana.issuedCount() o ana.nextNumber, pero
          es engañoso: parece que depende de ana y no es así. Usa siempre
          el nombre de la clase. (El compilador con -Xlint lo marca.)
        - El campo static vive mientras la clase esté cargada, que en la
          práctica es toda la vida del programa.
        - Este contador NO es seguro con varios hilos: dos hilos pueden
          leer el mismo nextNumber a la vez y repetir número. La solución
          (AtomicInteger) está en v13_concurrency/Atomics.
    */

    /*
        =================================================================
        CAPA 3) CONSTANTES: STATIC FINAL
        =================================================================

        La combinación más común de static es con final:
        - static -> una sola copia para toda la clase
        - final  -> no se puede reasignar
        = una CONSTANTE.

        Convención: NOMBRES_EN_MAYÚSCULAS_CON_GUIONES_BAJOS.

        Por qué usar constantes en vez de escribir el valor directo:
        - Le dan NOMBRE al número: MAX_ACTIVE_LOANS dice qué es; un 3
          suelto en el código no ("número mágico").
        - Se cambian en UN lugar.
    */
    static final class LoanPolicy {
        static final int MAX_ACTIVE_LOANS = 3;
        static final int LOAN_DAYS = 14;
        static final BigDecimal DAILY_LATE_FEE = new BigDecimal("5.00");

        // static final NO basta si el objeto se puede modificar por dentro:
        static final List<String> MUTABLE_GENRES = new ArrayList<>(List.of("Novela", "Ensayo")); // MAL
        static final List<String> GENRES = List.of("Novela", "Ensayo");                           // BIEN

        private LoanPolicy() {
        }

        static BigDecimal lateFee(int daysLate) {
            if (daysLate <= 0) {
                return BigDecimal.ZERO;
            }
            return DAILY_LATE_FEE.multiply(BigDecimal.valueOf(daysLate));
        }
    }

    static boolean constantsExample() {
        boolean feeUsesConstant = LoanPolicy.lateFee(3).compareTo(new BigDecimal("15.00")) == 0;

        // final impide reasignar la VARIABLE, pero la lista mutable se puede cambiar por dentro.
        LoanPolicy.MUTABLE_GENRES.add("Terror");
        boolean mutableChanged = LoanPolicy.MUTABLE_GENRES.size() == 3;

        boolean immutableProtected;
        try {
            LoanPolicy.GENRES.add("Terror");
            immutableProtected = false;
        } catch (UnsupportedOperationException e) {
            immutableProtected = true;
        }

        return feeUsesConstant && mutableChanged && immutableProtected;
    }

    /*
        Lecciones de este ejemplo:

        1) Una constante de verdad debe ser INMUTABLE completa. Un
           static final List mutable es un objeto global que cualquiera
           puede modificar; peor que no tener constante. Usa List.of,
           BigDecimal, String, enums... (ver Encapsulation, CAPA 7).

        2) Dato técnico: las constantes de tipo primitivo o String
           asignadas con un valor fijo (como MAX_ACTIVE_LOANS = 3) son
           "constantes de compilación": el compilador COPIA el valor 3
           dentro de cada clase que la usa. Si la constante está en una
           librería y cambias su valor, las clases que la usaban siguen
           con el 3 viejo hasta que se recompilan. Por eso los valores
           que pueden cambiar (precios, límites del negocio) suelen
           venir de configuración, no de constantes.

        3) Cuando las constantes forman un grupo cerrado de opciones
           (días de la semana, estados de un préstamo), no uses varias
           int: usa un enum (Enums).
    */

    /*
        =================================================================
        CAPA 4) MÉTODOS STATIC
        =================================================================

        Un método static pertenece a la clase y se llama con su nombre:
            LibraryCard.formatNumber(7)
            Math.max(3, 8)

        Regla clave: un método static NO se ejecuta sobre ningún objeto.
        Por lo tanto:
        - NO existe this dentro de él.
        - NO puede usar campos ni métodos de instancia directamente,
          porque ¿de cuál objeto serían?

            static int issuedCount() {
                return number;   // ERROR: non-static variable number cannot be
                                 // referenced from a static context
            }

        - SÍ puede usar campos de instancia de un objeto que RECIBA como
          parámetro o que cree, porque ahí sí hay un objeto concreto.

        En sentido contrario no hay restricción: un método de instancia
        puede usar todo lo static de su clase (describe() usa
        formatNumber).

        Cuándo un método debe ser static:
        cuando su resultado depende SOLO de sus parámetros (y de
        constantes), no del estado de un objeto. A eso se le llama
        "función pura": mismos parámetros, mismo resultado, siempre.
            LibraryCard.formatNumber(7)  -> siempre "LIB-0007"
            LoanPolicy.lateFee(3)        -> siempre 15.00

        Y técnicamente: los métodos static NO son polimórficos. No se
        sobrescriben, se ocultan (ver Inheritance, CAPA 6).
    */
    static boolean staticMethodsExample() {
        // Un método static que trabaja con un objeto recibido: aquí sí hay instancia.
        LibraryCard card = new LibraryCard("Mia");
        return describeTwice(card).equals(card.describe() + " / " + card.describe());
    }

    static String describeTwice(LibraryCard card) {
        return card.describe() + " / " + card.describe();
    }

    /*
        =================================================================
        CAPA 5) CLASES UTILITARIAS
        =================================================================

        Una CLASE UTILITARIA es una clase que solo agrupa métodos static
        relacionados. No tiene estado y no tiene sentido crear objetos
        de ella. El JDK está lleno: Math, Arrays, Collections, Objects.

        Receta profesional:
        1) La clase es final: nadie debe heredar de ella.
        2) Constructor private: nadie (de fuera) puede hacer new.
        3) Ese constructor lanza AssertionError, por si alguien de DENTRO
           de la clase lo llama por error.
        4) Todos sus métodos son static y, de preferencia, funciones
           puras.

        Ejemplo: validar un ISBN-13 (el código de 13 dígitos de los
        libros). La regla real: se multiplican los dígitos por 1 y 3 de
        forma alternada, y la suma debe ser múltiplo de 10.
    */
    static final class Isbn13 {

        private Isbn13() {
            throw new AssertionError("Isbn13 es una clase utilitaria: no se instancia");
        }

        static boolean isValid(String isbn) {
            if (isbn == null) {
                return false;
            }
            String digits = isbn.replace("-", "");
            if (!digits.matches("\\d{13}")) {
                return false;
            }
            int sum = 0;
            for (int i = 0; i < digits.length(); i++) {
                int digit = digits.charAt(i) - '0';
                sum += (i % 2 == 0) ? digit : digit * 3;
            }
            return sum % 10 == 0;
        }
    }

    static boolean utilityClassExample() {
        return Isbn13.isValid("978-0-13-468599-1")      // Effective Java, 3ª ed.
                && !Isbn13.isValid("978-0-13-468599-2") // último dígito alterado
                && !Isbn13.isValid(null);
    }

    /*
        Ojo: el constructor private + AssertionError es SOLO para clases
        sin estado. Si una clase tiene campos y setters, impedir que se
        creen objetos la vuelve inútil (ese es el error de la clase
        Person en Ejercicios.java, sección 0 del roadmap).

        Señal de alarma: si tu clase utilitaria crece mucho o sus
        métodos reciben siempre el mismo tipo de objeto, probablemente
        esos métodos deberían ser métodos de instancia de ESE objeto.
        Clases como "BookUtils" o "Helper" con 40 métodos suelen ser
        programación procedural disfrazada.
    */

    /*
        =================================================================
        CAPA 6) MÉTODOS DE FÁBRICA ESTÁTICOS
        =================================================================

        Un MÉTODO DE FÁBRICA ESTÁTICO es un método static que crea y
        devuelve un objeto de su clase. Combinado con un constructor
        private, la clase controla POR COMPLETO cómo se crean sus objetos.

        Ya los usas: List.of(...), Integer.valueOf(...), LocalDate.of(...).

        Ventajas sobre un constructor:
        1) TIENEN NOMBRE: MembershipFee.student() se entiende solo;
           new MembershipFee(new BigDecimal("50"), "Estudiante") no.
        2) PUEDEN DEVOLVER UN OBJETO YA EXISTENTE en lugar de crear uno
           nuevo cada vez (caché). Un constructor SIEMPRE crea uno nuevo.
        3) Pueden devolver una subclase o una implementación oculta
           (ver Polymorphism, CAPA 5).
        4) Pueden validar o normalizar antes de crear.

        Ejemplo: las cuotas de membresía de la biblioteca. Solo existen
        tres; no tiene sentido crear una nueva cada vez.
    */
    static final class MembershipFee {
        private static final MembershipFee STANDARD = new MembershipFee("Estándar", new BigDecimal("100"));
        private static final MembershipFee STUDENT = new MembershipFee("Estudiante", new BigDecimal("50"));
        private static final MembershipFee SENIOR = new MembershipFee("Adulto mayor", new BigDecimal("30"));

        private final String name;
        private final BigDecimal monthlyAmount;

        // private: solo esta clase puede crear cuotas.
        private MembershipFee(String name, BigDecimal monthlyAmount) {
            this.name = name;
            this.monthlyAmount = monthlyAmount;
        }

        static MembershipFee standard() {
            return STANDARD;
        }

        static MembershipFee student() {
            return STUDENT;
        }

        static MembershipFee senior() {
            return SENIOR;
        }

        // Fábrica con lógica: decide QUÉ cuota corresponde.
        static MembershipFee forAge(int age, boolean isStudent) {
            if (age >= 65) {
                return SENIOR;
            }
            return isStudent ? STUDENT : STANDARD;
        }

        String getName() {
            return name;
        }

        BigDecimal getMonthlyAmount() {
            return monthlyAmount;
        }
    }

    static boolean staticFactoryExample() {
        // Siempre el MISMO objeto: no se crea uno nuevo en cada llamada.
        boolean cached = MembershipFee.student() == MembershipFee.student();
        boolean decided = MembershipFee.forAge(70, false) == MembershipFee.senior()
                && MembershipFee.forAge(20, true).getName().equals("Estudiante");

        return cached && decided;
        // new MembershipFee("Gratis", BigDecimal.ZERO);  <- desde otro archivo NO compila:
        // el constructor es private. (Aquí compilaría solo porque la clase está anidada.)
    }

    /*
        Convenciones de nombres que usa el JDK para fábricas:
            of(...)        -> crea a partir de valores: List.of, LocalDate.of
            valueOf(...)   -> convierte: Integer.valueOf, String.valueOf
            from(...)      -> convierte desde otro tipo: Instant.from
            parse(...)     -> interpreta texto: Integer.parseInt, LocalDate.parse
            getInstance()  -> devuelve una instancia, quizá compartida
            newXxx()       -> garantiza un objeto nuevo

        Cuando una cantidad fija de instancias es TODO lo que existe
        (como estas tres cuotas), un enum es aún mejor (Enums). El
        patrón completo está en v11_patterns/part1_creational/FactoryMethod.
    */

    /*
        =================================================================
        CAPA 7) BLOQUES STATIC E INICIALIZACIÓN DE LA CLASE
        =================================================================

        Así como un objeto se inicializa al hacer new, una CLASE también
        se inicializa: UNA sola vez, la primera vez que se usa de verdad
        (crear un objeto, llamar un método static, leer un campo static
        que no sea constante de compilación).

        Al inicializar la clase, Java ejecuta, en el orden en que están
        escritos:
        - los inicializadores de campos static
        - los bloques static { ... }

        Un bloque static sirve cuando una constante necesita varios pasos
        para calcularse.

        Orden completo cuando se crea el PRIMER objeto de una clase:
        1) Inicialización de la clase (static), UNA vez en todo el programa.
        2) Inicialización del objeto (ver ClassesAndObjects, CAPA 8), en
           CADA new.
    */
    static final class ShelfCatalog {
        static final List<String> LOG = new ArrayList<>(); // solo para ver el orden en este ejemplo
        static final List<String> SECTIONS;

        static {
            List<String> sections = new ArrayList<>();
            for (char letter = 'A'; letter <= 'D'; letter++) {
                sections.add("Estante " + letter);
            }
            SECTIONS = List.copyOf(sections);
            LOG.add("bloque static");
        }

        ShelfCatalog() {
            LOG.add("constructor");
        }
    }

    static boolean staticInitializationExample() {
        new ShelfCatalog();
        new ShelfCatalog();

        // El bloque static corrió UNA vez; el constructor, dos.
        return ShelfCatalog.LOG.equals(List.of("bloque static", "constructor", "constructor"))
                && ShelfCatalog.SECTIONS.equals(List.of("Estante A", "Estante B", "Estante C", "Estante D"));
    }

    /*
        Cuidado con los bloques static:
        - Si lanzan una excepción, la clase queda inutilizable para el
          resto del programa (ExceptionInInitializerError y luego
          NoClassDefFoundError). Un error difícil de diagnosticar.
        - No hagas trabajo pesado ni con efectos externos (leer archivos,
          conectarte a una base de datos) en un bloque static: corre en
          un momento que no controlas.
    */

    /*
        =================================================================
        CAPA 8) IMPORT STATIC
        =================================================================

        Normalmente un miembro static se usa con el nombre de su clase:
            Objects.requireNonNull(holder, "holder");

        import static trae el miembro para usarlo sin el nombre de la
        clase. Esta guía lo hace al principio del archivo:
            import static java.util.Objects.requireNonNull;
        y por eso LibraryCard puede escribir solo:
            requireNonNull(holder, "holder");

        Úsalo con moderación: si el nombre del método no deja claro de
        dónde viene, quitar la clase hace el código más difícil de leer.
        Es común con requireNonNull, con métodos de Math y en tests
        (assertEquals de JUnit). Evita import static Clase.* (todo).
    */

    /*
        =================================================================
        CAPA 9) POR QUÉ MAIN ES STATIC
        =================================================================

            public static void main(String[] args)

        Cuando arranca el programa no existe ningún objeto todavía. La
        JVM necesita un punto de entrada que pueda llamar SIN crear un
        objeto primero: por eso main es static.

        Desde Java 25, main puede omitir static y/o el parámetro String[]
        (por ejemplo: void main()): si no es static, la JVM crea el objeto
        por ti y luego llama a main. Es una comodidad para programas pequeños
        (Ejercicios.java lo usa).

        Error de principiante MUY común: como main es static, solo puede
        llamar directo a métodos static, así que el principiante empieza
        a marcar TODO como static para que "compile". El resultado es
        un programa procedural sin objetos. La solución correcta: en
        main, crea los objetos y llama a SUS métodos.
    */

    /*
        =================================================================
        CAPA 10) CUÁNDO NO USAR STATIC
        =================================================================

        static es cómodo porque se usa desde cualquier lado sin crear
        nada. Ese mismo "desde cualquier lado" es su problema.

        PROBLEMA 1: ESTADO GLOBAL MUTABLE
        Un campo static que cambia (como nextNumber) es una variable
        global: cualquier parte del programa la puede alterar, los tests
        se afectan entre sí (un test crea credenciales y cambia el número
        del siguiente) y con varios hilos aparecen errores. Úsalo solo
        cuando de verdad el dato es UNO para todo el programa.

        PROBLEMA 2: DEPENDENCIAS OCULTAS
        Un método que llama a algo static por dentro esconde de qué
        depende y no deja cambiarlo. El ejemplo clásico es la fecha
        actual:
    */
    static final class HardcodedDueDate {
        // Llama a LocalDate.now() (static) por dentro: depende del día real en que se ejecute.
        static LocalDate dueDate() {
            return LocalDate.now().plusDays(LoanPolicy.LOAN_DAYS);
        }
    }

    /*
        ¿Cómo pruebas que un préstamo hecho el 28 de diciembre vence el
        11 de enero? Con HardcodedDueDate no puedes: el resultado cambia
        cada día.

        La solución es la misma de Polymorphism (CAPA 8): RECIBIR la
        dependencia desde fuera. java.time ofrece Clock justo para esto:
        en producción se usa el reloj real, en pruebas uno fijo.
    */
    static final class DueDateCalculator {
        private final Clock clock;

        DueDateCalculator(Clock clock) {
            this.clock = requireNonNull(clock, "clock");
        }

        LocalDate dueDate() {
            return LocalDate.now(clock).plusDays(LoanPolicy.LOAN_DAYS);
        }
    }

    static boolean hiddenDependencyExample() {
        Clock fixedDay = Clock.fixed(LocalDate.of(2026, 12, 28).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        DueDateCalculator calculator = new DueDateCalculator(fixedDay);

        boolean testable = calculator.dueDate().equals(LocalDate.of(2027, 1, 11));
        // HardcodedDueDate.dueDate() da otro valor cada día: solo se puede comparar contra "hoy".
        boolean onlyRelativeToToday = HardcodedDueDate.dueDate().equals(LocalDate.now().plusDays(14));

        return testable && onlyRelativeToToday;
    }

    /*
        PROBLEMA 3: NO HAY POLIMORFISMO
        Un método static no se puede sobrescribir ni recibir como
        interface. Si algún día necesitas dos versiones (cálculo de
        multas normal y para miembros premium), static te obliga a
        llenar de if el método. Un objeto con una interface no.

        Regla práctica:
        | Úsalo para                                   | Evítalo para                         |
        |----------------------------------------------|--------------------------------------|
        | Constantes inmutables                        | Estado que cambia                    |
        | Funciones puras (formatNumber, isValid)      | Lógica que depende de algo externo   |
        | Fábricas estáticas                           | Lógica de negocio que puede variar   |
        | Clases utilitarias pequeñas                  | Evitar crear objetos "por comodidad" |
        | main                                         | Compartir datos entre clases         |
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Marcar todo static para poder llamarlo desde main (CAPA 9).

        2) Intentar usar this o campos de instancia en un método static
           (CAPA 4).

        3) Llamar miembros static a través de un objeto (card.issuedCount()).
           Compila, pero engaña (CAPA 2).

        4) Creer que static final hace inmutable al objeto. Solo impide
           reasignar la variable (CAPA 3).

        5) Usar campos static como "variables globales" para pasar datos
           entre clases (CAPA 10).

        6) Esconder dependencias tras llamadas static (LocalDate.now(),
           singletons) que impiden probar el código (CAPA 10).

        7) Clases utilitarias gigantes con lógica que pertenece a un
           objeto (CAPA 5).

        8) Constructor private + AssertionError en una clase con estado
           (CAPA 5).

        9) Esperar polimorfismo de un método static (CAPA 10 e
           Inheritance, CAPA 6).

        10) Trabajo pesado o que puede fallar dentro de un bloque static
            (CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - static = pertenece a la CLASE: una sola copia, existe aunque no
          haya objetos. Se usa con el nombre de la clase.
        - Campo static: compartido por todos los objetos. Si cambia, es
          estado global: con cuidado.
        - static final: constante. Nombre en MAYÚSCULAS y el objeto debe
          ser inmutable.
        - Método static: sin this ni acceso directo a la instancia. Ideal
          para funciones puras. No es polimórfico.
        - Clase utilitaria: final, constructor private, solo métodos static.
        - Fábrica estática: constructor private + métodos con nombre que
          crean o reutilizan objetos.
        - Bloque static: corre una vez, al inicializar la clase.
        - main es static porque al arrancar no hay objetos.
        - Evita static para estado mutable, dependencias externas y
          lógica que podría variar.
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
