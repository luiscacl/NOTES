package v2_oop.part4_advanced_types;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/*
    =====================================================================
    EL CONTRATO DE OBJECT — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 13 de 14  (part4_advanced_types)
    Anterior:  part4_advanced_types/Records
    Siguiente: part4_advanced_types/SealedAndPatternMatching

    Requisito: ClassesAndObjects, Inheritance (CAPA 7: Object es la
    raíz) y Records.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Los métodos que todo objeto hereda de Object
        CAPA 2)  == vs equals: identidad vs igualdad
        CAPA 3)  Cómo escribir equals correctamente
        CAPA 4)  Las 5 reglas del contrato de equals
        CAPA 5)  Cómo se rompe la simetría
        CAPA 6)  Cómo se rompe la transitividad: herencia y equals
        CAPA 7)  hashCode: el compañero obligatorio de equals
        CAPA 8)  hashCode con campos mutables: objetos perdidos
        CAPA 9)  Comparable: el orden natural
        CAPA 10) Comparator: órdenes externos
        CAPA 11) clone y finalize: por qué evitarlos
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class ObjectContract {

    public static void main(String[] args) {
        check(identityVsEqualityExample(), "CAPA 2");
        check(correctEqualsExample(), "CAPA 3");
        check(brokenSymmetryExample(), "CAPA 5");
        check(brokenTransitivityExample(), "CAPA 6");
        check(hashCodeExample(), "CAPA 7");
        check(mutableHashCodeExample(), "CAPA 8");
        check(comparableExample(), "CAPA 9");
        check(comparatorExample(), "CAPA 10");
        check(cloneExample(), "CAPA 11");
        System.out.println("ObjectContract: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) LOS MÉTODOS QUE TODO OBJETO HEREDA DE OBJECT
        =================================================================

        Toda clase hereda de java.lang.Object (Inheritance, CAPA 7), y
        con ello estos métodos:

        | Método                    | Para qué                                  | Dónde se estudia           |
        |---------------------------|-------------------------------------------|----------------------------|
        | equals(Object)            | ¿dos objetos son "iguales"?               | esta guía, CAPAS 2–6       |
        | hashCode()                | número para HashMap/HashSet               | esta guía, CAPAS 7–8       |
        | toString()                | texto para depurar                        | ClassesAndObjects, CAPA 9     |
        | getClass()                | la clase real del objeto                  | Polymorphism, CAPA 4       |
        | clone()                   | copiar un objeto (diseño defectuoso)      | esta guía, CAPA 11         |
        | finalize()                | limpieza antes del GC (obsoleto)          | esta guía, CAPA 11         |
        | wait(), notify(), notifyAll() | coordinar hilos (bajo nivel)          | v13: Synchronization       |

        Cada uno trae un CONTRATO: reglas escritas en su documentación
        que toda sobrescritura debe cumplir. Las colecciones de Java
        (HashMap, HashSet, TreeSet, List.contains...) CONFÍAN en que los
        cumplas. Si no los cumples, no hay error de compilación ni
        excepción: las colecciones simplemente se comportan mal, en
        silencio. Por eso este tema es tan importante.

        (Comparable no es de Object, pero su contrato está tan ligado a
        equals que se estudia aquí, en las CAPAS 9 y 10.)
    */

    /*
        =================================================================
        CAPA 2) == VS EQUALS: IDENTIDAD VS IGUALDAD
        =================================================================

        Dos preguntas distintas:
        - IDENTIDAD (==): ¿son el MISMO objeto en memoria?
        - IGUALDAD (equals): ¿REPRESENTAN lo mismo?

        Con primitivos, == compara valores (5 == 5).
        Con objetos, == compara referencias: solo es true si las dos
        variables apuntan al mismo objeto.

        El equals que heredas de Object es exactamente ==. Si tu clase no
        lo sobrescribe, dos objetos con los mismos datos NO son iguales.
        String, Integer, BigDecimal, LocalDate, List... sí lo sobrescriben
        para comparar contenido.
    */
    static final class PhoneWithoutEquals {
        private final String number;

        PhoneWithoutEquals(String number) {
            this.number = number;
        }

        String number() {
            return number;
        }
    }

    static boolean identityVsEqualityExample() {
        String a = new String("hola");
        String b = new String("hola");
        boolean strings = a != b && a.equals(b);                 // objetos distintos, contenido igual

        PhoneWithoutEquals p1 = new PhoneWithoutEquals("3312345678");
        PhoneWithoutEquals p2 = new PhoneWithoutEquals("3312345678");
        boolean inheritedEqualsIsIdentity = !p1.equals(p2);      // sin sobrescribir: equals es ==

        return strings && inheritedEqualsIsIdentity;
    }

    /*
        Cuándo sobrescribir equals:
        - VALORES (dinero, teléfono, coordenada): dos objetos con los
          mismos datos son intercambiables -> SÍ, por todos sus campos.
          (O mejor aún: hazlos records, que ya lo traen.)
        - ENTIDADES (cliente, pedido): tienen identidad propia -> a veces
          por su id, a veces no se sobrescribe. Ver Records, CAPA 11.
        - Objetos donde "igual" no tiene sentido (un hilo, una conexión)
          -> NO. La identidad de Object es correcta.
    */

    /*
        =================================================================
        CAPA 3) CÓMO ESCRIBIR EQUALS CORRECTAMENTE
        =================================================================

        La receta (versión moderna de la de Effective Java):

        1) La firma es equals(Object), con @Override. Si escribes
           equals(PhoneNumber), creas una sobrecarga que las colecciones
           nunca llaman (Inheritance, CAPA 5).
        2) Si es el mismo objeto, true (atajo barato).
        3) instanceof con patrón: si no es del tipo correcto (o es null),
           false. instanceof con null siempre da false, así que esta línea
           también cubre el null.
        4) Compara los campos SIGNIFICATIVOS:
           - primitivos con ==  (double/float con Double.compare / Float.compare)
           - objetos con equals, o Objects.equals si pueden ser null
        5) Escribe hashCode con los MISMOS campos (CAPA 7).

        Y haz la clase final (CAPA 6 explica por qué).
    */
    static final class PhoneNumber {
        private final int areaCode;
        private final int prefix;
        private final int line;

        PhoneNumber(int areaCode, int prefix, int line) {
            this.areaCode = rangeCheck(areaCode, 999, "areaCode");
            this.prefix = rangeCheck(prefix, 999, "prefix");
            this.line = rangeCheck(line, 9999, "line");
        }

        private static int rangeCheck(int value, int max, String name) {
            if (value < 0 || value > max) {
                throw new IllegalArgumentException(name + " fuera de rango: " + value);
            }
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;                                     // paso 2
            }
            if (!(other instanceof PhoneNumber that)) {
                return false;                                    // paso 3 (también cubre null)
            }
            return areaCode == that.areaCode                     // paso 4
                    && prefix == that.prefix
                    && line == that.line;
        }

        @Override
        public int hashCode() {                                  // paso 5 (CAPA 7)
            int result = Integer.hashCode(areaCode);
            result = 31 * result + Integer.hashCode(prefix);
            result = 31 * result + Integer.hashCode(line);
            return result;
        }

        @Override
        public String toString() {
            return String.format("(%03d) %03d-%04d", areaCode, prefix, line);
        }
    }

    static boolean correctEqualsExample() {
        PhoneNumber a = new PhoneNumber(33, 123, 4567);
        PhoneNumber b = new PhoneNumber(33, 123, 4567);

        Set<PhoneNumber> phones = new HashSet<>(List.of(a));
        Map<PhoneNumber, String> owners = new HashMap<>();
        owners.put(a, "Ana");

        return a.equals(b)
                && !a.equals(null)
                && phones.contains(b)                  // HashSet encuentra uno igual, aunque sea otro objeto
                && owners.get(b).equals("Ana")
                && a.toString().equals("(033) 123-4567");
    }

    /*
        Sobre getClass() vs instanceof en equals:
        En tu Person de JavaDataStructuresGuide usabas
            if (obj == null || getClass() != obj.getClass()) return false;
        Es una alternativa válida. La diferencia:
        - getClass(): un objeto solo es igual a otro de la MISMA clase
          exacta. Una subclase nunca es igual a su padre, aunque no
          agregue nada. Eso rompe el principio de Liskov (Inheritance,
          CAPA 10): una hija no puede usarse donde se espera al padre.
        - instanceof: permite subclases, pero obliga a que equals se
          comporte bien en toda la jerarquía (CAPA 6).
        La salida limpia: clases final + instanceof (como PhoneNumber), o
        records, que son final y ya traen el equals correcto.
    */

    /*
        =================================================================
        CAPA 4) LAS 5 REGLAS DEL CONTRATO DE EQUALS
        =================================================================

        La documentación de Object exige que equals sea:

        1) REFLEXIVO:   x.equals(x) es true.
        2) SIMÉTRICO:   si x.equals(y), entonces y.equals(x).
        3) TRANSITIVO:  si x.equals(y) y y.equals(z), entonces x.equals(z).
        4) CONSISTENTE: llamarlo varias veces da el mismo resultado,
                        mientras los objetos no cambien.
        5) NO NULO:     x.equals(null) es false (nunca una excepción).

        Suenan obvias, pero las dos del medio se rompen con mucha
        facilidad. Las siguientes dos capas muestran cómo.
    */

    /*
        =================================================================
        CAPA 5) CÓMO SE ROMPE LA SIMETRÍA
        =================================================================

        Ejemplo clásico: un texto que ignora mayúsculas, y que además
        quiere ser "amable" y compararse con String normales.
    */
    static final class CaseInsensitiveString {
        private final String value;

        CaseInsensitiveString(String value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof CaseInsensitiveString that) {
                return value.equalsIgnoreCase(that.value);
            }
            if (other instanceof String text) {           // MAL: String no sabe nada de esta clase
                return value.equalsIgnoreCase(text);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return value.toLowerCase().hashCode();
        }
    }

    static boolean brokenSymmetryExample() {
        CaseInsensitiveString cis = new CaseInsensitiveString("Hola");
        String plain = "hola";

        boolean oneWay = cis.equals(plain);       // true
        boolean otherWay = plain.equals(cis);     // false: String.equals solo acepta String

        // Consecuencia: el resultado depende de QUIÉN pregunta.
        List<CaseInsensitiveString> list = new ArrayList<>(List.of(cis));
        boolean listSaysNo = !list.contains(plain);   // contains llama plain.equals(elemento): false

        return oneWay && !otherWay && listSaysNo;
    }

    /*
        La causa: equals intentó ser igual a objetos de OTRA clase que no
        sabe que existe. La regla: equals solo compara contra objetos de
        su propio tipo. Si quieres comparar con String, ofrece un método
        aparte con otro nombre (matches(String)).
    */

    /*
        =================================================================
        CAPA 6) CÓMO SE ROMPE LA TRANSITIVIDAD: HERENCIA Y EQUALS
        =================================================================

        El problema más profundo del tema. Tienes Point (x, y) con un
        equals correcto, y creas ColorPoint extends Point agregando un
        color. ¿Cómo debe comparar ColorPoint?

        Si ignora el color, rompes la lógica (dos puntos de distinto color
        serían iguales). Si lo compara siempre, rompes la simetría con
        Point. El "arreglo" típico (comparar color solo contra otro
        ColorPoint, e ignorarlo contra un Point) rompe la transitividad:
    */
    static class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Point that && x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return 31 * Integer.hashCode(x) + Integer.hashCode(y);
        }
    }

    static final class ColorPoint extends Point {
        private final String color;

        ColorPoint(int x, int y, String color) {
            super(x, y);
            this.color = color;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Point)) {
                return false;
            }
            if (!(other instanceof ColorPoint that)) {
                return other.equals(this);           // contra un Point simple: ignora el color
            }
            return super.equals(that) && color.equals(that.color);
        }

        @Override
        public int hashCode() {
            return super.hashCode();                 // coherente: los iguales comparten (x, y)
        }
    }

    static boolean brokenTransitivityExample() {
        ColorPoint red = new ColorPoint(1, 2, "rojo");
        Point plain = new Point(1, 2);
        ColorPoint blue = new ColorPoint(1, 2, "azul");

        boolean redEqualsPlain = red.equals(plain);    // true  (ignora color)
        boolean plainEqualsBlue = plain.equals(blue);  // true  (Point no ve el color)
        boolean redEqualsBlue = red.equals(blue);      // false (compara color)

        // x = y, y = z, pero x != z: transitividad rota.
        return redEqualsPlain && plainEqualsBlue && !redEqualsBlue;
    }

    /*
        La conclusión (Effective Java): NO EXISTE una forma de heredar de
        una clase instanciable, agregarle un campo que importe para la
        igualdad, y cumplir el contrato de equals. Es un límite del
        diseño, no de tu habilidad.

        Las salidas:
        1) COMPOSICIÓN: ColorPoint TIENE un Point y un color, en vez de
           SER un Point (CompositionAndAggregation, CAPA 6).
        2) Clases final o records: si nadie puede heredar, el problema no
           existe.
        3) Si la clase padre es abstracta (no hay objetos Point "simples"),
           las hijas sí pueden agregar campos sin conflicto.
    */

    /*
        =================================================================
        CAPA 7) HASHCODE: EL COMPAÑERO OBLIGATORIO DE EQUALS
        =================================================================

        Cómo busca un HashSet o HashMap (versión simplificada):
        1) Calcula hashCode() del objeto para saber en qué "cajón"
           (bucket) buscar.
        2) Solo dentro de ese cajón, compara con equals.
        Si dos objetos iguales dan hashCode distinto, el HashSet busca en
        el cajón equivocado y nunca llega a llamar equals.

        El contrato de hashCode:
        1) Si a.equals(b), entonces a.hashCode() == b.hashCode().
           OBLIGATORIO.
        2) Si NO son iguales, PUEDEN tener el mismo hashCode (colisión),
           pero cuantas menos colisiones, más rápido el mapa.
        3) Consistente: mismo valor mientras el objeto no cambie.

        Por eso la regla: SIEMPRE que sobrescribas equals, sobrescribe
        hashCode con los MISMOS campos. El propio compilador lo advierte
        (con -Xlint): "Class X overrides equals, but neither it nor any
        superclass overrides hashCode method".

        Cómo escribirlo:
        - Objects.hash(campo1, campo2, ...): corto y claro; un poco más
          lento (crea un arreglo en cada llamada). Suficiente casi siempre.
        - A mano con 31 * result + ... (como PhoneNumber): más rápido.
          31 es primo e impar, lo que reparte bien los valores, y la JVM
          optimiza la multiplicación por 31.
    */
    @SuppressWarnings("overrides") // a propósito: este es el error que el compilador advierte
    static final class PhoneOnlyEquals {
        private final String number;

        PhoneOnlyEquals(String number) {
            this.number = number;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PhoneOnlyEquals that && number.equals(that.number);
        }
        // sin hashCode: usa el de Object, distinto para cada objeto
    }

    static boolean hashCodeExample() {
        PhoneOnlyEquals a = new PhoneOnlyEquals("3312345678");
        PhoneOnlyEquals b = new PhoneOnlyEquals("3312345678");

        Set<PhoneOnlyEquals> set = new HashSet<>();
        set.add(a);
        set.add(b);

        // equals dice que son iguales, pero el HashSet guardó los DOS y no encuentra uno nuevo.
        return a.equals(b)
                && set.size() == 2
                && !set.contains(new PhoneOnlyEquals("3312345678"));
    }

    /*
        =================================================================
        CAPA 8) HASHCODE CON CAMPOS MUTABLES: OBJETOS PERDIDOS
        =================================================================

        Si equals y hashCode dependen de un campo que CAMBIA, y cambias
        ese campo mientras el objeto está dentro de un HashSet o es clave
        de un HashMap, el objeto queda en el cajón de su hashCode VIEJO.
        Ya no lo encuentras, ni lo puedes quitar: sigue ahí, perdido.
    */
    static final class MutableTag {
        private String name;

        MutableTag(String name) {
            this.name = name;
        }

        void rename(String newName) {
            this.name = newName;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableTag that && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    static boolean mutableHashCodeExample() {
        MutableTag tag = new MutableTag("oferta");
        Set<MutableTag> tags = new HashSet<>();
        tags.add(tag);

        tag.rename("liquidación");   // cambia el hashCode mientras está DENTRO del set

        boolean lost = !tags.contains(tag);   // busca en el cajón nuevo: no está
        boolean cannotRemove = !tags.remove(tag);
        boolean stillThere = tags.size() == 1;

        return lost && cannotRemove && stillThere;
    }

    /*
        Reglas:
        - Lo ideal: objetos usados como claves o en Sets son INMUTABLES
          (String, records, clases final con campos final).
        - Si un objeto debe ser mutable, que equals/hashCode NO dependan
          de los campos que cambian (por ejemplo, solo de un id que nunca
          cambia).
    */

    /*
        =================================================================
        CAPA 9) COMPARABLE: EL ORDEN NATURAL
        =================================================================

        equals responde "¿son iguales?". Para ORDENAR hace falta otra
        pregunta: "¿cuál va primero?". Eso es Comparable:

            interface Comparable<T> {
                int compareTo(T other);
            }

        compareTo devuelve:
            negativo -> este va ANTES que other
            cero     -> son equivalentes en orden
            positivo -> este va DESPUÉS que other
        (Solo importa el SIGNO, no el número exacto.)

        Implementar Comparable define el ORDEN NATURAL de una clase: el
        que usan Collections.sort, List.sort(null), TreeSet y TreeMap si
        no les das otro. String (alfabético), Integer (numérico) y
        LocalDate (cronológico) lo implementan.

        Ejemplo: versiones de software. Como texto, "1.10.0" va ANTES que
        "1.9.3" (compara carácter por carácter, y '1' < '9'). El orden
        natural correcto compara número por número.
    */
    record Version(int major, int minor, int patch) implements Comparable<Version> {

        static Version parse(String text) {
            String[] parts = text.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Formato esperado: mayor.menor.parche");
            }
            return new Version(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }

        @Override
        public int compareTo(Version other) {
            int result = Integer.compare(major, other.major);   // el campo más importante primero
            if (result == 0) {
                result = Integer.compare(minor, other.minor);
            }
            if (result == 0) {
                result = Integer.compare(patch, other.patch);
            }
            return result;
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    // NUNCA compares restando: con números grandes, la resta se desborda y da el signo equivocado.
    static int compareBySubtraction(int a, int b) {
        return a - b;
    }

    static boolean comparableExample() {
        List<String> asText = new ArrayList<>(List.of("1.9.3", "1.10.0", "1.2.0"));
        asText.sort(null);   // orden natural de String

        List<Version> versions = new ArrayList<>();
        for (String text : List.of("1.9.3", "1.10.0", "1.2.0")) {
            versions.add(Version.parse(text));
        }
        versions.sort(null); // orden natural de Version

        boolean textOrderIsWrong = asText.equals(List.of("1.10.0", "1.2.0", "1.9.3"));
        boolean versionOrderIsRight = versions.toString().equals("[1.2.0, 1.9.3, 1.10.0]");

        // Integer.MIN_VALUE - 1 se desborda a un número positivo enorme: dice que MIN_VALUE es MAYOR que 1.
        boolean subtractionOverflows = compareBySubtraction(Integer.MIN_VALUE, 1) > 0;
        boolean compareIsSafe = Integer.compare(Integer.MIN_VALUE, 1) < 0;

        return textOrderIsWrong && versionOrderIsRight && subtractionOverflows && compareIsSafe;
    }

    /*
        El contrato de compareTo (paralelo al de equals):
        1) Antisimétrico: signo(a.compareTo(b)) == -signo(b.compareTo(a)).
        2) Transitivo: si a > b y b > c, entonces a > c.
        3) Si a.compareTo(b) == 0, a y b se comparan igual contra todo lo
           demás.
        4) MUY RECOMENDADO: consistente con equals. Es decir,
           compareTo == 0 exactamente cuando equals es true.

        Qué pasa si no es consistente con equals: HashSet usa equals,
        TreeSet usa compareTo, y dan resultados distintos. El ejemplo
        real del JDK es BigDecimal: 1.0 y 1.00 NO son iguales para equals
        (distinta escala), pero compareTo dice 0.
    */
    static boolean bigDecimalInconsistencyExample() {
        Set<BigDecimal> hashSet = new HashSet<>(List.of(new BigDecimal("1.0"), new BigDecimal("1.00")));
        Set<BigDecimal> treeSet = new TreeSet<>(List.of(new BigDecimal("1.0"), new BigDecimal("1.00")));
        return hashSet.size() == 2 && treeSet.size() == 1;   // el "mismo" conjunto, dos tamaños
    }

    /*
        =================================================================
        CAPA 10) COMPARATOR: ÓRDENES EXTERNOS
        =================================================================

        Comparable da UN orden, escrito DENTRO de la clase. Pero muchas
        veces necesitas ordenar de varias formas (productos por precio,
        por nombre, por stock), o ordenar una clase que no es tuya.

        Para eso existe Comparator: un objeto APARTE que sabe comparar
        dos objetos.
            interface Comparator<T> {
                int compare(T a, T b);
            }

        | Comparable                          | Comparator                               |
        |-------------------------------------|------------------------------------------|
        | Dentro de la clase                  | Fuera de la clase                        |
        | UN orden: el natural                | Tantos órdenes como quieras              |
        | compareTo(other)                    | compare(a, b)                            |
        | Se usa por defecto (sort(null))     | Se pasa explícitamente (sort(comparator))|

        Comparator trae métodos de fábrica para construirlos sin escribir
        la comparación a mano. Abajo se usan con referencias a métodos
        (Product::price), que se explican en v7_functional/part1_lambdas/MethodReferences;
        se leen como "compara por el precio de cada producto". El tema de
        ordenar a fondo está en v5_collections/part4_tools/Sorting.
    */
    record Product(String name, BigDecimal price, int stock) { }

    static boolean comparatorExample() {
        List<Product> products = new ArrayList<>(List.of(
                new Product("Mouse", new BigDecimal("300"), 50),
                new Product("Teclado", new BigDecimal("800"), 10),
                new Product("Cable", new BigDecimal("300"), 200)));

        // Por precio y, si empatan, por nombre.
        products.sort(Comparator.comparing(Product::price).thenComparing(Product::name));
        boolean byPriceThenName = products.get(0).name().equals("Cable") && products.get(1).name().equals("Mouse");

        // El mismo dato, otro orden: más stock primero.
        products.sort(Comparator.comparingInt(Product::stock).reversed());
        boolean byStockDesc = products.get(0).name().equals("Cable") && products.get(2).name().equals("Teclado");

        return byPriceThenName && byStockDesc && bigDecimalInconsistencyExample();
    }

    /*
        =================================================================
        CAPA 11) CLONE Y FINALIZE: POR QUÉ EVITARLOS
        =================================================================

        CLONE
        La idea era "copiar un objeto". El diseño salió mal:
        - Cloneable es una interface marcadora SIN el método clone
          (Interfaces, CAPA 9). clone() está en Object, es protected, y
          lanza CloneNotSupportedException (checked) si no implementas
          Cloneable. Todo al revés.
        - La copia es SUPERFICIAL: copia las referencias, no los objetos a
          los que apuntan. Una lista interna queda COMPARTIDA entre el
          original y la copia.
        - No funciona bien con campos final: para hacer una copia profunda
          tendrías que reasignar el campo después de clonar, y final no lo
          permite.
        - No pasa por ningún constructor: se salta tus validaciones.
    */
    static final class Wishlist implements Cloneable {
        private final List<String> items = new ArrayList<>();

        Wishlist() {
        }

        // La alternativa correcta: CONSTRUCTOR DE COPIA. Pasa por un constructor y copia lo mutable.
        Wishlist(Wishlist other) {
            items.addAll(other.items);
        }

        void add(String item) {
            items.add(item);
        }

        int size() {
            return items.size();
        }

        @Override
        public Wishlist clone() {
            try {
                return (Wishlist) super.clone();   // copia superficial: la lista es la MISMA
            } catch (CloneNotSupportedException e) {
                throw new AssertionError("No puede pasar: implementamos Cloneable", e);
            }
        }
    }

    static boolean cloneExample() {
        Wishlist original = new Wishlist();
        original.add("Audífonos");

        Wishlist cloned = original.clone();
        cloned.add("Monitor");                   // ¡también aparece en el original!
        boolean cloneShares = original.size() == 2;

        Wishlist copied = new Wishlist(original);
        copied.add("Silla");                     // no afecta al original
        boolean copyIsIndependent = original.size() == 2 && copied.size() == 3;

        return cloneShares && copyIsIndependent;
    }

    /*
        Alternativas a clone:
        - Constructor de copia: new Wishlist(original).
        - Fábrica de copia: Wishlist.copyOf(original).
        - Objetos inmutables (records, List.copyOf): no necesitan copiarse,
          se comparten sin riesgo.
        Excepción conocida: clonar ARREGLOS con array.clone() sí está bien
        y es la forma más rápida de copiarlos.

        FINALIZE
        Debía ejecutarse "antes de que el recolector de basura libere el
        objeto". En la práctica no hay garantía de CUÁNDO, ni de SI se
        ejecuta, y hace todo más lento. Está obsoleto (deprecated) y
        marcado para eliminarse. Para liberar recursos (archivos,
        conexiones) se usa try-with-resources (v3_exceptions/TryWithResources).
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Comparar objetos con == cuando quieres comparar contenido
           (Strings incluidos) (CAPA 2).

        2) Escribir equals(MiClase) en vez de equals(Object), sin
           @Override (CAPA 3).

        3) equals que lanza NullPointerException con null (CAPA 4).

        4) equals que intenta ser igual a objetos de otra clase (CAPA 5).

        5) Heredar de una clase con equals y agregar campos que importan
           para la igualdad (CAPA 6).

        6) Sobrescribir equals sin hashCode (CAPA 7).

        7) equals y hashCode basados en campos mutables de objetos que
           van en HashSet o como claves de HashMap (CAPA 8).

        8) compareTo restando números (CAPA 9).

        9) compareTo inconsistente con equals sin documentarlo (CAPA 9).

        10) Usar clone o finalize (CAPA 11).

        Y la recomendación general: si tu clase es un VALOR, usa un record
        y obtienes equals, hashCode y toString correctos sin escribirlos.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Object da a todos: equals, hashCode, toString, getClass, clone,
          finalize, wait/notify. Cada uno tiene un contrato que las
          colecciones dan por cumplido.
        - == compara identidad; equals, igualdad. El equals heredado es ==.
        - equals: equals(Object) + @Override, atajo con ==, instanceof con
          patrón, comparar campos significativos. Reflexivo, simétrico,
          transitivo, consistente, false con null.
        - Heredar y agregar campos a una clase con equals rompe el
          contrato: composición, final o records.
        - Siempre hashCode junto con equals, con los mismos campos; nunca
          con campos que cambian mientras el objeto está en un Set o Map.
        - Comparable: un orden natural dentro de la clase; Comparator:
          órdenes externos. Compara con Integer.compare, nunca restando.
          Idealmente, consistente con equals.
        - Evita clone (usa constructores de copia) y finalize.
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
