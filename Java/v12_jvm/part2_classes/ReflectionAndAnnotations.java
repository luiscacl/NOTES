package v12_jvm.part2_classes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/*
    =====================================================================
    REFLEXIÓN Y ANOTACIONES: CÓMO LOS FRAMEWORKS LEEN TU CÓDIGO
    =====================================================================

    Ruta de jvm/: guía 4 de 6  (part2_classes)
    Anterior:  part2_classes/ClassLoadingAndBytecode
    Siguiente: part2_classes/Modules

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Mensajes y tiempos medidos en Windows 11, JDK 26.

    Lo que ya se usó y aquí se explica completo:
    - el contenedor de inyección de juguete (v10_design/
      part3_responsibilities/DependencyInjection, CAPA 8);
    - leer y llenar records por nombre, como Jackson (v11_patterns/
      part4_modern/DTO);
    - los proxies dinámicos (v11_patterns/part2_structural/Proxy, CAPA 5);
    - Class<T> para trabajar con el borrado (v4_generics/AdvancedGenerics,
      CAPA 9).

    Importancia: MEDIA. En tu código casi nunca escribirás reflexión, pero
    TODOS los frameworks importantes (Spring, JUnit, Jackson, Hibernate)
    funcionan con ella y con anotaciones. Entenderla quita la "magia".

    Índice:
        CAPA 1)  Qué es la reflexión: el objeto Class
        CAPA 2)  Leer los miembros: métodos, campos, constructores
        CAPA 3)  Usar los miembros: invocar, crear, leer y escribir
        CAPA 4)  Lo privado, los módulos y los final
        CAPA 5)  Arreglos genéricos con java.lang.reflect.Array
        CAPA 6)  Qué es una anotación
        CAPA 7)  Escribir anotaciones propias: retención y destino
        CAPA 8)  Cómo los usan los frameworks: un validador y un ejecutor de pruebas
        CAPA 9)  Lo que cuesta, y MethodHandle
        CAPA 10) Cuándo usarla y errores comunes
        RESUMEN FINAL
*/
public class ReflectionAndAnnotations {

    public static void main(String[] args) throws Throwable {
        check(classObjectExample(), "CAPA 1");
        check(membersExample(), "CAPA 2");
        check(invokeExample(), "CAPA 3");
        check(accessExample(), "CAPA 4");
        check(genericArrayExample(), "CAPA 5");
        check(retentionExample(), "CAPA 7");
        check(frameworkExample(), "CAPA 8");
        check(methodHandleExample(), "CAPA 9");
        System.out.println("ReflectionAndAnnotations: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES LA REFLEXIÓN: EL OBJETO CLASS
        =================================================================

        Normalmente el código conoce sus tipos al COMPILAR: llamas a
        account.deposit(100) y el compilador revisa que exista. La
        REFLEXIÓN es preguntar y usar los tipos al EJECUTAR: "¿qué métodos
        tiene este objeto? llama al que se llama 'deposit'".

        Para eso, por cada clase cargada la JVM tiene un objeto Class (el
        que creó el cargador; ClassLoadingAndBytecode, CAPA 3). Se obtiene
        de tres formas:

            Account.class                  conoces el tipo al escribir
            account.getClass()             tienes un objeto (su clase REAL,
                                           no la de la variable)
            Class.forName("paquete.Clase") solo tienes el nombre en texto
                                           (de un archivo de configuración)

        Con Class se pregunta todo lo que se declaró: nombre, padre,
        interfaces, modificadores, si es record o enum, qué subclases
        permite si es sealed...
    */
    sealed interface Shape permits Circle, Square {
    }

    record Circle(double radius) implements Shape {
    }

    record Square(double side) implements Shape {
    }

    static boolean classObjectExample() throws ClassNotFoundException {
        Shape shape = new Circle(2);
        Class<?> real = shape.getClass();                   // la clase real: Circle, no Shape
        Class<?> byName = Class.forName("v12_jvm.part2_classes.ReflectionAndAnnotations$Circle");
        return real == Circle.class && byName == Circle.class          // un solo objeto Class por clase
                && real.isRecord()
                && Arrays.asList(real.getInterfaces()).contains(Shape.class)
                && Shape.class.getPermittedSubclasses().length == 2
                && Modifier.isFinal(real.getModifiers());              // los records son final
    }

    /*
        =================================================================
        CAPA 2) LEER LOS MIEMBROS: MÉTODOS, CAMPOS, CONSTRUCTORES
        =================================================================

        Cada miembro tiene dos versiones de búsqueda:

            getMethods()          los PUBLIC, incluidos los HEREDADOS
                                  (toString, hashCode... de Object)
            getDeclaredMethods()  TODOS los declarados en ESA clase
                                  (privados incluidos), sin los heredados

        Igual con getFields/getDeclaredFields y getConstructors/
        getDeclaredConstructors. Y de uno en uno: getDeclaredMethod("add",
        int.class), donde los tipos de los parámetros distinguen las
        sobrecargas.

        Cuidado: el ORDEN en que vienen no está garantizado. Un framework
        que dependa del orden de getDeclaredMethods tiene un error
        esperando a pasar. Si el orden importa, se ordena (aquí, por
        nombre).

        En records, getRecordComponents() devuelve los componentes EN
        ORDEN de declaración: es lo que usan las librerías de JSON.
    */
    static final class Account {
        private final String owner;
        private int balance;

        Account(String owner) {
            this.owner = owner;
        }

        public int deposit(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("monto inválido: " + amount);
            }
            balance += amount;
            return balance;
        }

        private String secret() {
            return "solo para " + owner;
        }
    }

    static boolean membersExample() throws NoSuchMethodException {
        List<String> declared = Arrays.stream(Account.class.getDeclaredMethods())
                .map(Method::getName).sorted().toList();
        boolean publicIncludesInherited = Arrays.stream(Account.class.getMethods())
                .anyMatch(method -> method.getName().equals("toString"));
        Method deposit = Account.class.getDeclaredMethod("deposit", int.class);

        return declared.equals(List.of("deposit", "secret"))
                && publicIncludesInherited
                && deposit.getReturnType() == int.class
                && Arrays.stream(Circle.class.getRecordComponents()).map(RecordComponent::getName)
                        .toList().equals(List.of("radius"));
    }

    /*
        =================================================================
        CAPA 3) USAR LOS MIEMBROS: INVOCAR, CREAR, LEER Y ESCRIBIR
        =================================================================

            constructor.newInstance(args...)    crea un objeto
            method.invoke(objeto, args...)      llama (objeto null si es static)
            field.get(objeto) / field.set(...)  lee / escribe un campo

        Todo recibe y devuelve Object: los primitivos se envuelven (int ->
        Integer) y los tipos se revisan al EJECUTAR, no al compilar. Lo que
        el compilador habría rechazado, aquí es una excepción:

            argumentos equivocados      IllegalArgumentException:
                                        argument type mismatch
            el MÉTODO lanza algo        InvocationTargetException, con la
                                        excepción real en getCause()

        Lo segundo es la trampa: la excepción de TU método llega
        envuelta. Un framework que no la desenvuelve muestra
        "InvocationTargetException" en el log, con el error verdadero
        escondido en el "Caused by".
    */
    static boolean invokeExample() throws ReflectiveOperationException {
        Constructor<Account> constructor = Account.class.getDeclaredConstructor(String.class);
        Account account = constructor.newInstance("ana");
        Method deposit = Account.class.getDeclaredMethod("deposit", int.class);

        Object balance = deposit.invoke(account, 100);      // 100 se envuelve en Integer

        boolean wrongType;
        try {
            deposit.invoke(account, "cien");
            wrongType = false;
        } catch (IllegalArgumentException e) {
            wrongType = e.getMessage().equals("argument type mismatch");
        }

        boolean wrapped;
        try {
            deposit.invoke(account, -5);
            wrapped = false;
        } catch (InvocationTargetException e) {
            wrapped = e.getCause() instanceof IllegalArgumentException cause
                    && cause.getMessage().equals("monto inválido: -5");   // la real, adentro
        }
        return balance.equals(100) && wrongType && wrapped;
    }

    /*
        =================================================================
        CAPA 4) LO PRIVADO, LOS MÓDULOS Y LOS FINAL
        =================================================================

        setAccessible(true) apaga la revisión de private/protected. Dentro
        de TU código funciona: así los frameworks llenan campos privados
        (Hibernate, Jackson con campos) y llaman métodos privados.

        Pero NO dentro del JDK. Desde Java 17 sus paquetes internos están
        encapsulados por los módulos (part2_classes/Modules), y medido:

            field String.value -> setAccessible(true)
            InaccessibleObjectException: Unable to make field private final
            byte[] java.lang.String.value accessible: module java.base does
            not "opens java.lang" to unnamed module @1dbd16a6

        Solo se abre con una opción al arrancar (--add-opens
        java.base/java.lang=ALL-UNNAMED). Si una librería vieja te pide
        eso, es señal de que depende de detalles internos del JDK y hay que
        actualizarla.

        Y los final:
        - en un RECORD, un campo final no se puede cambiar ni con
          setAccessible: IllegalAccessException ("Can not set final int
          field ...").
        - en una clase normal, HOY se puede, pero Java 26 ya avisa al
          hacerlo (medido, impreso en la consola):

            WARNING: Final field owner in class Refl$Account has been
            mutated reflectively by class Refl in unnamed module ...
            WARNING: Mutating final fields will be blocked in a future
            release unless final field mutation is enabled

          "final" va a significar final de verdad. Código que cambia
          finales por reflexión (algunas librerías de pruebas o de
          serialización viejas) dejará de funcionar.
    */
    static boolean accessExample() throws ReflectiveOperationException {
        Account account = new Account("ana");
        Method secret = Account.class.getDeclaredMethod("secret");
        secret.setAccessible(true);                         // en tu propio código: permitido
        boolean privateCalled = secret.invoke(account).equals("solo para ana");

        boolean jdkClosed;
        try {
            String.class.getDeclaredField("value").setAccessible(true);
            jdkClosed = false;
        } catch (InaccessibleObjectException e) {
            jdkClosed = e.getMessage().contains("does not \"opens java.lang\"");
        }

        Field radius = Circle.class.getDeclaredField("radius");
        radius.setAccessible(true);
        boolean recordProtected;
        try {
            radius.set(new Circle(1), 5.0);
            recordProtected = false;
        } catch (IllegalAccessException e) {
            recordProtected = e.getMessage().startsWith("Can not set final double field");
        }
        return privateCalled && jdkClosed && recordProtected;
    }

    /*
        =================================================================
        CAPA 5) ARREGLOS GENÉRICOS CON JAVA.LANG.REFLECT.ARRAY
        =================================================================

        "new T[n]" no compila: por el borrado, al ejecutar no se sabe qué
        es T (AdvancedGenerics, CAPAS 8-9). Si se recibe el Class<T>,
        Array.newInstance crea un arreglo del tipo REAL. Es lo que hace
        List.toArray(T[]) por dentro, y Arrays.copyOf.

        La diferencia importa: un Object[] disfrazado de String[] lanza
        ClassCastException en cuanto alguien lo usa como String[]. El
        creado con Array.newInstance es un String[] de verdad.
    */
    static <T> T[] newArray(Class<T> type, int length) {
        @SuppressWarnings("unchecked")                      // seguro: el arreglo ES de tipo T
        T[] array = (T[]) Array.newInstance(type, length);
        return array;
    }

    static boolean genericArrayExample() {
        String[] names = newArray(String.class, 3);
        return names.getClass() == String[].class && names.length == 3;
    }

    /*
        =================================================================
        CAPA 6) QUÉ ES UNA ANOTACIÓN
        =================================================================

        Una ANOTACIÓN es una etiqueta con datos que se pega al código
        (clases, métodos, campos, parámetros...). Por sí sola NO HACE NADA:
        es información. Alguien tiene que leerla:

        - el COMPILADOR: @Override (error si no sobrescribe nada),
          @FunctionalInterface (error si tiene más de un método abstracto),
          @Deprecated(forRemoval = true) (aviso a quien lo use),
          @SuppressWarnings("unchecked") (calla un aviso concreto),
          @SafeVarargs.
        - una HERRAMIENTA al compilar: procesadores de anotaciones que
          GENERAN código (CAPA 8).
        - un FRAMEWORK al ejecutar, con reflexión: @Test (JUnit),
          @Service y @Transactional (Spring), @JsonProperty (Jackson),
          @NotBlank (Bean Validation, la validación estándar que usa
          Spring).

        Es la forma en que tu código "le habla" a un framework sin heredar
        de sus clases: escribes una clase normal y le pones etiquetas.
    */

    /*
        =================================================================
        CAPA 7) ESCRIBIR ANOTACIONES PROPIAS: RETENCIÓN Y DESTINO
        =================================================================

        Se declaran con @interface. Sus "elementos" parecen métodos y
        pueden tener valor por defecto; solo admiten primitivos, String,
        Class, enums, otras anotaciones y arreglos de esos.

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.RECORD_COMPONENT)
            @interface Range {
                int min() default 0;
                int max();
            }
            uso:  @Range(max = 120) int age

        Si el único elemento se llama value, se puede omitir el nombre:
        @Label("Edad") en vez de @Label(value = "Edad").

        @Retention dice HASTA DÓNDE llega:
            SOURCE    solo el compilador la ve; no llega al .class
                      (@Override, @SuppressWarnings)
            CLASS     llega al .class, pero la reflexión NO la ve. ES EL
                      VALOR POR DEFECTO.
            RUNTIME   visible con reflexión. La que usan los frameworks.

        La trampa clásica: escribir una anotación, olvidar @Retention, y
        que el framework "no la vea". El ejemplo lo demuestra.

        @Target dice DÓNDE se puede poner (TYPE, METHOD, FIELD, PARAMETER,
        RECORD_COMPONENT...): ponerla en otro lugar no compila. Otras dos:
        @Inherited (la anotación de una clase se hereda a sus subclases) y
        @Repeatable (se puede poner varias veces en el mismo lugar).
    */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.RECORD_COMPONENT)
    @interface NotBlank {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.RECORD_COMPONENT)
    @interface Range {
        int min() default 0;

        int max();
    }

    @Target(ElementType.METHOD)                             // sin @Retention: CLASS, invisible al ejecutar
    @interface Forgotten {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Remembered {
    }

    static final class Annotated {
        @Forgotten
        @Remembered
        void work() {
            // solo existe para llevar las anotaciones
        }
    }

    static boolean retentionExample() throws NoSuchMethodException {
        Method work = Annotated.class.getDeclaredMethod("work");
        return work.isAnnotationPresent(Remembered.class)
                && !work.isAnnotationPresent(Forgotten.class);   // está en el código, pero no al ejecutar
    }

    /*
        =================================================================
        CAPA 8) CÓMO LOS USAN LOS FRAMEWORKS: UN VALIDADOR Y UN EJECUTOR
                DE PRUEBAS
        =================================================================

        Dos frameworks de juguete, de pocas líneas, que hacen lo mismo que
        los reales:

        1) VALIDADOR (como Bean Validation): recorre los componentes del
           record, lee sus anotaciones, lee el valor y junta los errores.
           La clase validada no sabe nada del validador.

        2) EJECUTOR DE PRUEBAS (como JUnit): busca los métodos con @Test,
           crea una instancia NUEVA de la clase por cada prueba (para que
           una prueba no ensucie a otra; JUnit hace lo mismo), los llama, y
           una prueba falla si lanza una excepción, que llega envuelta
           (CAPA 3) y hay que desenvolver.

        Lo que estos ejemplos NO hacen y los reales sí: GUARDAR lo leído.
        Leer anotaciones con reflexión es lento comparado con una llamada
        normal (CAPA 9), así que los frameworks leen cada clase UNA vez y
        guardan el resultado ("metadatos") en un mapa.

        La alternativa moderna: procesadores de anotaciones que leen las
        anotaciones AL COMPILAR (con retención SOURCE o CLASS) y GENERAN
        código normal. El programa arranca más rápido (no hay que leer
        nada) y los errores aparecen al compilar. Algunos frameworks
        recientes funcionan así; Spring sigue usando sobre todo reflexión.
    */
    record SignUp(@NotBlank String name, @Range(min = 18, max = 120) int age) {
    }

    static List<String> validate(Record value) throws ReflectiveOperationException {
        List<String> errors = new ArrayList<>();
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            Object fieldValue = component.getAccessor().invoke(value);
            if (component.isAnnotationPresent(NotBlank.class)
                    && (fieldValue == null || fieldValue.toString().isBlank())) {
                errors.add(component.getName() + ": no puede estar vacío");
            }
            Range range = component.getAnnotation(Range.class);
            if (range != null && fieldValue instanceof Integer number
                    && (number < range.min() || number > range.max())) {
                errors.add(component.getName() + ": debe estar entre " + range.min() + " y " + range.max());
            }
        }
        return errors;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Test {
    }

    static final class CartTests {
        @Test
        void emptyCartTotalIsZero() {
            if (new ArrayList<Integer>().stream().mapToInt(Integer::intValue).sum() != 0) {
                throw new AssertionError("el total debía ser 0");
            }
        }

        @Test
        void failsOnPurpose() {
            throw new AssertionError("esperaba 3 productos y hay 2");
        }

        void helperNotATest() {
            throw new IllegalStateException("no debe ejecutarse: no tiene @Test");
        }
    }

    record TestReport(List<String> passed, List<String> failed) {
    }

    static TestReport runTests(Class<?> testClass) throws ReflectiveOperationException {
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<Method> tests = Arrays.stream(testClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .sorted(Comparator.comparing(Method::getName))      // orden estable (CAPA 2)
                .toList();
        Constructor<?> constructor = testClass.getDeclaredConstructor();
        for (Method test : tests) {
            Object instance = constructor.newInstance();     // una instancia nueva por prueba
            try {
                test.invoke(instance);
                passed.add(test.getName());
            } catch (InvocationTargetException e) {
                failed.add(test.getName() + ": " + e.getCause().getMessage());   // desenvolver
            }
        }
        return new TestReport(passed, failed);
    }

    static boolean frameworkExample() throws ReflectiveOperationException {
        boolean validOk = validate(new SignUp("Ana", 30)).isEmpty();
        List<String> errors = validate(new SignUp(" ", 15));
        TestReport report = runTests(CartTests.class);

        return validOk
                && errors.equals(List.of("name: no puede estar vacío", "age: debe estar entre 18 y 120"))
                && report.passed().equals(List.of("emptyCartTotalIsZero"))
                && report.failed().equals(List.of("failsOnPurpose: esperaba 3 productos y hay 2"));
    }

    /*
        =================================================================
        CAPA 9) LO QUE CUESTA, Y METHODHANDLE
        =================================================================

        Medido, 10 millones de llamadas a un método static pequeño, ya
        calentado (ClassLoadingAndBytecode, CAPA 7):

            llamada directa               ~0.5 a 2 ns
            Method.invoke                 ~7 a 12 ns
            MethodHandle.invokeExact      ~2 ns

        Method.invoke cuesta varias veces más que la llamada directa: tiene
        que revisar permisos, envolver los argumentos en Object[] y los
        primitivos en wrappers, y el JIT puede hacer menos. Aun así son
        nanosegundos: en una petición web que tarda milisegundos, no se
        nota. Lo que SÍ es caro es BUSCAR (getDeclaredMethod, leer
        anotaciones) en cada llamada: por eso se busca una vez y se guarda.

        METHODHANDLE (java.lang.invoke) es la forma moderna y rápida de
        referirse a un método al ejecutar. Se busca una vez con un
        "lookup" (que revisa los permisos AL BUSCAR, no en cada llamada) y
        después se llama casi como uno directo. Desde Java 18, la propia
        reflexión (Method.invoke) está construida sobre MethodHandle. Su
        pariente VarHandle hace lo mismo para campos, con operaciones
        atómicas (v13_concurrency/).

        Para tu código: si algún día necesitas llamar algo dinámicamente en
        un ciclo caliente, MethodHandle guardado en un campo static final.
    */
    private static final MethodHandle DEPOSIT = findDeposit();

    private static MethodHandle findDeposit() {
        try {
            return MethodHandles.lookup().findVirtual(Account.class, "deposit",
                    MethodType.methodType(int.class, int.class));          // se busca UNA vez
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static boolean methodHandleExample() throws Throwable {
        Account account = new Account("ana");
        int first = (int) DEPOSIT.invokeExact(account, 50);    // tipos exactos: Account, int -> int
        int second = (int) DEPOSIT.invokeExact(account, 25);
        return first == 50 && second == 75;
    }

    /*
        =================================================================
        CAPA 10) CUÁNDO USARLA Y ERRORES COMUNES
        =================================================================

        Úsala cuando escribes algo GENÉRICO que trabaja con clases que no
        conoces: un framework, una librería de serialización, una
        herramienta de pruebas. En código de negocio, casi nunca: si
        conoces el tipo, una interface o un switch sobre un sealed hacen
        lo mismo con revisión del compilador.

        Lo que se pierde con reflexión:
        - el compilador ya no revisa: un método renombrado rompe en
          producción, no al compilar;
        - las herramientas del editor ("buscar usos", "renombrar") no ven
          las llamadas por texto;
        - el encapsulamiento: private deja de proteger.

        Errores comunes:

        1) Depender del orden de getDeclaredMethods o getDeclaredFields
           (CAPA 2).

        2) Mostrar o atrapar InvocationTargetException sin desenvolver la
           causa real (CAPA 3).

        3) Abrir el JDK con --add-opens para que funcione algo, en vez de
           actualizarlo; cambiar campos final por reflexión (CAPA 4).

        4) Crear arreglos genéricos con (T[]) new Object[n] (CAPA 5).

        5) Anotaciones propias sin @Retention(RUNTIME) (CAPA 7).

        6) Buscar métodos y leer anotaciones en cada llamada en vez de una
           vez (CAPAS 8 y 9).

        7) Reflexión en código de negocio donde bastaba una interface
           (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Reflexión: preguntar y usar los tipos al ejecutar, a partir del
          objeto Class (.class, getClass(), Class.forName).
        - getX: lo público con lo heredado; getDeclaredX: todo lo de esa
          clase. El orden no está garantizado.
        - invoke y newInstance revisan al ejecutar; los errores del método
          llegan envueltos en InvocationTargetException.
        - setAccessible abre lo privado de tu código, no el JDK (módulos);
          los final serán finales de verdad.
        - Anotaciones: datos pegados al código que alguien lee; para la
          reflexión, @Retention(RUNTIME); @Target dice dónde.
        - Los frameworks leen anotaciones con reflexión una vez y guardan
          el resultado; otros generan código al compilar.
        - Method.invoke cuesta nanosegundos; lo caro es buscar.
          MethodHandle es la versión rápida.
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
