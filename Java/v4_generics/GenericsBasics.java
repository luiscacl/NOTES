package v4_generics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/*
    =====================================================================
    GENÉRICOS BÁSICOS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v4_generics/: guía 1 de 2
    Anterior:  — (es la primera; viene de v3_exceptions/part2_in_practice/ErrorHandlingBestPractices)
    Siguiente: AdvancedGenerics

    Requisito: v2_oop/ (clases, interfaces) y v1_fundamentals/.../Wrappers.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  El problema: colecciones de Object
        CAPA 2)  Qué es un genérico: parámetros de TIPO
        CAPA 3)  Clases genéricas
        CAPA 4)  El operador diamante y la trampa con var
        CAPA 5)  Interfaces genéricas
        CAPA 6)  Métodos genéricos
        CAPA 7)  El T de la clase vs el T del método
        CAPA 8)  Raw types: genéricos sin su tipo
        CAPA 9)  Leer las firmas genéricas del JDK
        CAPA 10) Lo que un genérico no puede hacer (primer vistazo)
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class GenericsBasics {

    public static void main(String[] args) {
        check(objectCollectionsProblemExample(), "CAPA 1");
        check(genericClassExample(), "CAPA 3");
        check(diamondExample(), "CAPA 4");
        check(genericInterfaceExample(), "CAPA 5");
        check(genericMethodsExample(), "CAPA 6");
        check(classVsMethodTypeExample(), "CAPA 7");
        check(rawTypesExample(), "CAPA 8");
        check(jdkSignaturesExample(), "CAPA 9");
        System.out.println("GenericsBasics: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: COLECCIONES DE OBJECT
        =================================================================

        Antes de Java 5 (2004) no había genéricos. Una lista guardaba
        Object, el tipo padre de todo (Inheritance, CAPA 7), así que:

        1) Podías meter CUALQUIER cosa: una lista "de nombres" aceptaba
           también un número, sin ningún aviso.
        2) Al sacar un elemento, recibías un Object y tenías que hacer
           CAST para usarlo como String (Polymorphism, CAPA 7).
        3) Si alguien había metido algo del tipo equivocado, el cast
           fallaba con ClassCastException... AL EJECUTAR, lejos de donde se
           cometió el error.

        Lo mismo pasa si hoy escribes tu propia "caja" con Object:
    */
    static final class ObjectBox {
        private Object content;

        void put(Object content) {
            this.content = content;
        }

        Object get() {
            return content;
        }
    }

    static boolean objectCollectionsProblemExample() {
        ObjectBox box = new ObjectBox();
        box.put("Hola");
        String text = (String) box.get();       // obligado a castear

        box.put(42);                            // nada impide meter otro tipo
        boolean failsAtRuntime;
        try {
            String broken = (String) box.get(); // compila... y falla al ejecutar
            failsAtRuntime = broken.isEmpty();
        } catch (ClassCastException e) {
            failsAtRuntime = true;
        }

        return text.equals("Hola") && failsAtRuntime;
    }

    /*
        =================================================================
        CAPA 2) QUÉ ES UN GENÉRICO: PARÁMETROS DE TIPO
        =================================================================

        Tu antigua TypingGuide lo explicaba muy bien: así como un método recibe
        VALORES por parámetros, una clase genérica recibe TIPOS por
        parámetros.

            class Box<T> { ... }        T es un "parámetro de tipo": un hueco
                                        que se llena al usar la clase
            Box<String> textBox;        aquí T = String
            Box<Integer> numberBox;     aquí T = Integer

        Lo que ganas, contra la CAPA 1:
        1) El COMPILADOR revisa los tipos: meter un número en una
           Box<String> no compila. El error pasa de "al ejecutar, quién
           sabe dónde" a "al compilar, en la línea exacta".
        2) No hay casts: get() de una Box<String> ya devuelve String.
        3) Escribes la clase UNA vez y sirve para cualquier tipo.

        Convenciones de nombres (una sola letra mayúscula):
            T  type (un tipo cualquiera)       E  element (de una colección)
            K  key (clave de un mapa)          V  value (valor de un mapa)
            R  result (lo que devuelve)        U, S  segundo, tercer tipo
    */

    /*
        =================================================================
        CAPA 3) CLASES GENÉRICAS
        =================================================================

        El parámetro de tipo va después del nombre de la clase, y se usa
        DENTRO de la clase como si fuera un tipo cualquiera: en campos,
        parámetros, retornos y constructores. Es el mismo T en toda la
        clase (como decía tu antigua TypingGuide: "T vive en toda la clase").

        Puede haber varios: Pair<A, B>, Map<K, V>.
    */
    static final class Box<T> {
        private T content;

        Box(T content) {
            this.content = content;
        }

        void put(T content) {
            this.content = content;
        }

        T get() {
            return content;
        }

        boolean isEmpty() {
            return content == null;
        }
    }

    record Pair<A, B>(A first, B second) { }

    static boolean genericClassExample() {
        Box<String> textBox = new Box<>("Hola");
        String text = textBox.get();            // sin cast: el compilador sabe que es String
        textBox.put("Mundo");
        // textBox.put(42);                     // ERROR: incompatible types: int cannot be converted to String

        Box<Integer> numberBox = new Box<>(123);
        int doubled = numberBox.get() * 2;      // Integer, con unboxing automático

        Pair<String, Integer> stock = new Pair<>("teclado", 15);

        return text.equals("Hola") && textBox.get().equals("Mundo") && doubled == 246
                && stock.first().equals("teclado") && stock.second() == 15;
    }

    /*
        =================================================================
        CAPA 4) EL OPERADOR DIAMANTE Y LA TRAMPA CON VAR
        =================================================================

        Escribir el tipo dos veces era redundante:
            Box<String> box = new Box<String>("Hola");
        Desde Java 7, el OPERADOR DIAMANTE <> le dice al compilador:
        "deduce aquí el mismo tipo que ya se ve a la izquierda":
            Box<String> box = new Box<>("Hola");

        LA TRAMPA CON VAR: el diamante necesita ver el tipo a la IZQUIERDA.
        Con var no hay nada a la izquierda, así que el compilador no tiene
        de dónde deducir... y elige Object:
            var list = new ArrayList<>();      // ¡es ArrayList<Object>!
        Ahora list acepta cualquier cosa, y volvimos a la CAPA 1. Lo peor:
        no da NINGUNA advertencia, ni con -Xlint.
        Con var, escribe el tipo a la derecha: var list = new ArrayList<String>();
        (Es la trampa 1 de var en Variables, CAPA 8, llevada al extremo.)
    */
    static boolean diamondExample() {
        Box<String> withDiamond = new Box<>("Hola");          // <> deduce String de la izquierda

        var trap = new ArrayList<>();                         // ArrayList<Object>, sin aviso
        trap.add(1);
        trap.add("texto");                                    // acepta cualquier cosa

        var correct = new ArrayList<String>();                // con var, el tipo va a la derecha
        correct.add("solo textos");
        // correct.add(1);                                    // ERROR: no compila, como debe ser

        return withDiamond.get().equals("Hola")
                && trap.get(0) instanceof Integer && trap.get(1) instanceof String
                && correct.size() == 1;
    }

    /*
        =================================================================
        CAPA 5) INTERFACES GENÉRICAS
        =================================================================

        Una interface también puede tener parámetros de tipo. Es un
        contrato que sirve para cualquier tipo:
            interface Repository<T, ID> {
                void save(ID id, T entity);
                Optional<T> findById(ID id);
            }

        Al implementarla hay dos opciones:
        1) Fijar los tipos:      class ProductRepository implements Repository<Product, String>
        2) Seguir siendo genérica, pasando sus propios parámetros a la
           interface:            class InMemoryRepository<T, ID> implements Repository<T, ID>
           (Tu RepositorioMemoria8<T> hacía exactamente esto.)

        La segunda es la que se reutiliza: UNA implementación sirve para
        productos, clientes, pedidos... Es la base del patrón Repository
        (v11_patterns/part4_modern/Repository) y de cómo funciona Spring Data.
    */
    interface Repository<T, ID> {
        void save(ID id, T entity);

        Optional<T> findById(ID id);

        int count();
    }

    static final class InMemoryRepository<T, ID> implements Repository<T, ID> {
        private final Map<ID, T> storage = new HashMap<>();

        @Override
        public void save(ID id, T entity) {
            storage.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(entity, "entity"));
        }

        @Override
        public Optional<T> findById(ID id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public int count() {
            return storage.size();
        }
    }

    record Product(String sku, String name) { }

    record Customer(long id, String name) { }

    static boolean genericInterfaceExample() {
        // La MISMA implementación, para dos tipos de entidad y dos tipos de id.
        Repository<Product, String> products = new InMemoryRepository<>();
        Repository<Customer, Long> customers = new InMemoryRepository<>();

        products.save("TEC-01", new Product("TEC-01", "Teclado"));
        customers.save(7L, new Customer(7L, "Ana"));
        // customers.save("7", ...);          // ERROR: el id de clientes es Long, no String

        return products.findById("TEC-01").map(Product::name).orElse("").equals("Teclado")
                && customers.findById(7L).isPresent()
                && customers.findById(99L).isEmpty()
                && products.count() == 1;
    }

    /*
        =================================================================
        CAPA 6) MÉTODOS GENÉRICOS
        =================================================================

        Un MÉTODO puede tener sus propios parámetros de tipo, aunque su
        clase no sea genérica. Se declaran ANTES del tipo de retorno:

            static <T> T firstOrDefault(List<T> list, T defaultValue)
                   ^^^
                   "este método trabaja con un tipo T, el que sea"

        El compilador deduce T en cada llamada a partir de los argumentos
        (INFERENCIA): firstOrDefault(nombres, "nadie") usa T = String.
        Si alguna vez no puede deducirlo, se le dice explícitamente con un
        "testigo de tipo": Collections.<String>emptyList().

        Son ideales para utilidades que funcionan igual con cualquier tipo
        (como tu identidad(T valor)). El JDK está lleno: List.of,
        Collections.max, Objects.requireNonNull...
    */
    static <T> T firstOrDefault(List<T> list, T defaultValue) {
        return list.isEmpty() ? defaultValue : list.get(0);
    }

    static <T> List<T> repeat(T value, int times) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            result.add(value);
        }
        return result;
    }

    static <K, V> Map<V, K> invert(Map<K, V> map) {
        Map<V, K> inverted = new HashMap<>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            inverted.put(entry.getValue(), entry.getKey());
        }
        return inverted;
    }

    static boolean genericMethodsExample() {
        String first = firstOrDefault(List.of("Ana", "Luis"), "nadie");     // T = String
        Integer none = firstOrDefault(List.of(), 0);                        // T = Integer
        List<String> dashes = repeat("-", 3);                               // List<String>
        Map<Integer, String> byCode = invert(Map.of("uno", 1, "dos", 2));   // K = String, V = Integer

        List<String> explicit = Collections.<String>emptyList();            // testigo de tipo explícito

        return first.equals("Ana") && none == 0
                && dashes.equals(List.of("-", "-", "-"))
                && byCode.get(2).equals("dos")
                && explicit.isEmpty();
    }

    /*
        =================================================================
        CAPA 7) EL T DE LA CLASE VS EL T DEL MÉTODO
        =================================================================

        Esta era la idea central de tu antigua TypingGuide (Animal8<T> con su
        método <U> mostrarDosCosas). Hay dos "alcances" de parámetros de
        tipo:

        - El de la CLASE (class Box<T>) se fija al crear el objeto y es el
          MISMO en todos sus métodos de instancia.
        - El de un MÉTODO (<U> void show(U value)) se fija en CADA llamada
          y solo existe dentro de ese método.

        Un método puede usar los dos a la vez: el T de su clase y su
        propio U.

        Consecuencia técnica: los métodos STATIC no pueden usar el T de la
        clase, porque T se fija por OBJETO y un método static no tiene
        objeto (Static, CAPA 4):
            class Box<T> {
                static T make() { ... }
                // ERROR: non-static type variable T cannot be referenced from a static context
            }
        Un método static que necesite un tipo genérico declara el suyo:
            static <T> Box<T> of(T value)
    */
    static final class Labeled<T> {
        private final T value;

        Labeled(T value) {
            this.value = value;
        }

        T value() {
            return value;
        }

        // T viene de la clase (fijo para este objeto); U es propio del método (cambia en cada llamada).
        <U> Pair<T, U> pairWith(U other) {
            return new Pair<>(value, other);
        }

        // static: no puede usar el T de la clase, declara su propio T.
        static <T> Labeled<T> of(T value) {
            return new Labeled<>(value);
        }
    }

    static boolean classVsMethodTypeExample() {
        Labeled<String> dog = Labeled.of("perro");          // T de la clase = String

        Pair<String, Integer> withNumber = dog.pairWith(100);     // U = Integer en esta llamada
        Pair<String, Boolean> withFlag = dog.pairWith(true);      // U = Boolean en esta otra

        return withNumber.first().equals("perro") && withNumber.second() == 100
                && withFlag.second();
    }

    /*
        =================================================================
        CAPA 8) RAW TYPES: GENÉRICOS SIN SU TIPO
        =================================================================

        Usar una clase genérica SIN indicar su tipo (List en lugar de
        List<String>) se llama "raw type" (tipo crudo). Se permite solo
        por compatibilidad con código de antes de Java 5. Con un raw type
        pierdes todo lo que ganaste: vuelves a la CAPA 1.

        Es el error que tenías en Practice/Main.java:
            TryGenerics<String> tryGenerics = new TryGenerics("Hello");   // falta el <>
        Compila, pero con -Xlint el compilador avisa:
            warning: [rawtypes] found raw type: TryGenerics
            warning: [unchecked] unchecked call to TryGenerics(T) as a member of the raw type TryGenerics
            warning: [unchecked] unchecked conversion
        "unchecked" significa "no puedo verificar los tipos aquí: si algo
        sale mal, será ClassCastException al ejecutar". La corrección:
            new TryGenerics<>("Hello")

        Regla: NUNCA uses raw types en código nuevo, y trata las
        advertencias "unchecked" como errores.
    */
    @SuppressWarnings({"rawtypes", "unchecked"}) // a propósito: es lo que se quiere mostrar
    static boolean rawTypesExample() {
        List raw = new ArrayList();
        raw.add("Ana");
        raw.add(42);                            // un raw type acepta cualquier cosa

        List<String> names = raw;               // asignarlo a un List<String> "funciona"...
        boolean blowsUpLater;
        try {
            String second = names.get(1);       // ...hasta que lees el 42 como String
            blowsUpLater = second.isEmpty();
        } catch (ClassCastException e) {
            blowsUpLater = true;
        }
        return blowsUpLater;
    }

    /*
        =================================================================
        CAPA 9) LEER LAS FIRMAS GENÉRICAS DEL JDK
        =================================================================

        Saber leer genéricos te deja entender la documentación de Java de
        un vistazo:

            interface List<E>                    "lista de elementos E"
                boolean add(E e)                 recibe un E
                E get(int index)                 devuelve un E

            interface Map<K, V>                  "mapa de claves K a valores V"
                V get(Object key)                devuelve un V (o null)
                V put(K key, V value)

            class Optional<T>                    "un T que puede no estar"
            interface Comparable<T>              "se compara con otro T"
            interface Function<T, R>             "recibe T, devuelve R"

            static <T> List<T> of(T... elements)         método genérico con varargs
            static <T extends Comparable<? super T>> T max(Collection<? extends T> coll)

        La última asusta, pero se lee por partes: "un método que, para un
        T que se pueda comparar, recibe una colección de T y devuelve el T
        más grande". Los ? extends y ? super (comodines) son el tema de
        AdvancedGenerics.

        Y recuerda: los parámetros de tipo solo aceptan TIPOS DE
        REFERENCIA. List<int> no compila; se usa List<Integer>
        (Wrappers, CAPA 1).
    */
    static boolean jdkSignaturesExample() {
        Map<String, List<Integer>> scoresByStudent = new HashMap<>();   // genéricos anidados
        scoresByStudent.put("Ana", new ArrayList<>(List.of(9, 10)));
        scoresByStudent.get("Ana").add(8);

        Integer best = Collections.max(scoresByStudent.get("Ana"));     // T deducido como Integer

        return best == 10 && scoresByStudent.get("Ana").size() == 3;
    }

    /*
        =================================================================
        CAPA 10) LO QUE UN GENÉRICO NO PUEDE HACER (PRIMER VISTAZO)
        =================================================================

        Dentro de una clase genérica, T es un tipo "desconocido", así que
        hay cosas que no se pueden hacer con él:

            new T()                     // ERROR: unexpected type
            new T[10]                   // ERROR: generic array creation
            o instanceof List<String>   // desde un Object: no se puede revisar el tipo de los elementos
            static T campo;             // ERROR: el T es por objeto (CAPA 7)

        La razón de fondo se llama BORRADO DE TIPOS (type erasure): los
        genéricos existen para el COMPILADOR, pero al generar el bytecode
        Java los borra. Al ejecutar, una List<String> y una List<Integer>
        son la misma clase List, y la JVM no sabe qué era T.
        AdvancedGenerics explica por qué Java lo diseñó así y cómo
        trabajar con esas limitaciones.
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Usar Object (o colecciones de Object) donde un genérico daría
           seguridad de tipos (CAPA 1).

        2) Raw types: List en lugar de List<String>, u olvidar el <> en
           new (CAPA 8; era el caso de Practice/Main.java).

        3) Ignorar las advertencias "unchecked" (CAPA 8).

        4) var con el diamante vacío: var list = new ArrayList<>() es
           ArrayList<Object> (CAPA 4).

        5) Intentar usar el T de la clase en un método static (CAPA 7).

        6) Escribir List<int> (CAPA 9).

        7) Intentar new T() o new T[] (CAPA 10).

        8) Poner el <T> de un método genérico en otro lugar: va ANTES del
           tipo de retorno (CAPA 6).

        9) Hacer genérica una clase que solo se usa con un tipo
           ("genéricos por si acaso").
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Un genérico es un parámetro de TIPO: class Box<T>. Se llena al
          usarlo: Box<String>.
        - Ganas: revisión al compilar, sin casts, una clase para todos los
          tipos.
        - Diamante <> deduce el tipo de la izquierda; con var no hay
          izquierda: escribe el tipo a la derecha.
        - Interfaces genéricas: fija el tipo al implementar, o sigue siendo
          genérica para reutilizarla.
        - Métodos genéricos: <T> antes del retorno; el tipo se deduce en
          cada llamada.
        - T de clase: fijo por objeto. T de método: por llamada. Los static
          declaran el suyo.
        - Raw types y "unchecked": nunca.
        - Solo tipos de referencia (List<Integer>).
        - new T() y new T[] no existen: borrado de tipos (AdvancedGenerics).
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
