package v2_oop.part3_interfaces_and_relationships;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/*
    =====================================================================
    INTERFACES A FONDO — DE INTERMEDIO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 8 de 14  (part3_interfaces_and_relationships)
    Anterior:  part2_pillars/Abstraction
    Siguiente: part3_interfaces_and_relationships/CompositionAndAggregation

    Requisito: Abstraction. Allá se explica QUÉ es una interface
    (un contrato), implements, por qué depender de ella, implementar
    varias, un primer vistazo a default, e interface vs clase abstracta.
    Esta guía NO repite eso: cubre todo lo demás que puede tener y hacer
    una interface.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Anatomía completa: todo lo que puede llevar una interface
        CAPA 2)  Constantes y el antipatrón de "interface de constantes"
        CAPA 3)  Métodos static en interfaces
        CAPA 4)  Métodos private en interfaces
        CAPA 5)  Métodos default a fondo: evolucionar una API
        CAPA 6)  Reglas de resolución de default
        CAPA 7)  Herencia entre interfaces
        CAPA 8)  Interfaces funcionales
        CAPA 9)  Interfaces marcadoras
        CAPA 10) Diseñar buenas interfaces
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Interfaces {

    public static void main(String[] args) {
        check(anatomyExample(), "CAPA 1");
        check(constantInterfaceExample(), "CAPA 2");
        check(staticMethodsExample(), "CAPA 3");
        check(defaultEvolutionExample(), "CAPA 5");
        check(resolutionRulesExample(), "CAPA 6");
        check(interfaceInheritanceExample(), "CAPA 7");
        check(functionalInterfaceExample(), "CAPA 8");
        check(markerInterfaceExample(), "CAPA 9");
        System.out.println("Interfaces: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) ANATOMÍA COMPLETA: TODO LO QUE PUEDE LLEVAR UNA INTERFACE
        =================================================================

        Desde Java 9, una interface puede contener:

        | Miembro                 | Modificadores implícitos      | Desde   | Para qué                          |
        |-------------------------|-------------------------------|---------|-----------------------------------|
        | método abstracto        | public abstract               | siempre | el contrato                       |
        | constante               | public static final           | siempre | valores ligados al contrato       |
        | tipo anidado            | public static                 | siempre | tipos que el contrato necesita    |
        | método default          | public                        | Java 8  | comportamiento por defecto        |
        | método static           | public (se puede private)     | Java 8  | fábricas y utilidades del contrato|
        | método private          | private                       | Java 9  | código compartido por los default |

        Lo que una interface NUNCA puede tener:
        - campos de instancia (estado)        -> no hay "this.x" que guardar
        - constructores                       -> no se instancia
        - métodos protected                   -> todo lo público es public
        - bloques de inicialización

        "Implícitos" significa que Java los agrega aunque no los
        escribas. Escribir "public abstract" en un método de interface
        compila, pero es ruido: la convención es omitirlo.

        Una interface que usa TODO lo anterior. El contrato es calcular
        el costo de enviar un paquete según su peso.
    */
    interface ShippingCalculator {

        // Constante: implícitamente public static final.
        int MAX_WEIGHT_KG = 30;

        // Abstracto: implícitamente public abstract. ES el contrato.
        BigDecimal costFor(double weightKg);

        // Default: comportamiento que todas las implementaciones heredan.
        default BigDecimal costWithInsurance(double weightKg, BigDecimal declaredValue) {
            return roundMoney(costFor(weightKg).add(insuranceFor(declaredValue)));
        }

        // Default "agregado en la versión 2" (CAPA 5).
        default int estimatedDays() {
            return 5;
        }

        // Static: fábrica del contrato (CAPA 3).
        static ShippingCalculator flatRate(BigDecimal amount) {
            return new FlatRateShipping(amount);
        }

        // Static: utilidad para quien implemente la interface.
        static void requireValidWeight(double weightKg) {
            if (weightKg <= 0 || weightKg > MAX_WEIGHT_KG) {
                throw new IllegalArgumentException("El peso debe estar entre 0 y " + MAX_WEIGHT_KG + " kg");
            }
        }

        // Private (CAPA 4): solo para uso interno de los default de esta interface.
        private BigDecimal roundMoney(BigDecimal amount) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        // Private static: igual, pero sin necesitar una instancia.
        private static BigDecimal insuranceFor(BigDecimal declaredValue) {
            return Objects.requireNonNull(declaredValue, "declaredValue").multiply(new BigDecimal("0.02")); // 2 %
        }
    }

    /*
        Implementación 1: cobra por kilo. Solo implementa costFor; recibe
        gratis costWithInsurance y estimatedDays.
    */
    static final class WeightBasedShipping implements ShippingCalculator {
        private final BigDecimal pricePerKg;

        WeightBasedShipping(BigDecimal pricePerKg) {
            this.pricePerKg = Objects.requireNonNull(pricePerKg, "pricePerKg");
        }

        @Override
        public BigDecimal costFor(double weightKg) {
            ShippingCalculator.requireValidWeight(weightKg);
            return pricePerKg.multiply(BigDecimal.valueOf(weightKg));
        }
    }

    /*
        Implementación que devuelve la fábrica flatRate. En un proyecto
        real sería una clase de paquete (sin public) en su propio
        archivo: quien la usa solo conoce ShippingCalculator.flatRate(...).
    */
    private static final class FlatRateShipping implements ShippingCalculator {
        private final BigDecimal amount;

        FlatRateShipping(BigDecimal amount) {
            this.amount = Objects.requireNonNull(amount, "amount");
        }

        @Override
        public BigDecimal costFor(double weightKg) {
            ShippingCalculator.requireValidWeight(weightKg);
            return amount;
        }
    }

    static boolean anatomyExample() {
        ShippingCalculator byWeight = new WeightBasedShipping(new BigDecimal("12"));

        // costFor: implementado por la clase. 2.5 kg x 12 = 30
        boolean abstractMethod = byWeight.costFor(2.5).compareTo(new BigDecimal("30")) == 0;

        // costWithInsurance: heredado de la interface. 30 + 2 % de 1000 = 50.00
        boolean defaultMethod = byWeight.costWithInsurance(2.5, new BigDecimal("1000"))
                .equals(new BigDecimal("50.00"));

        boolean constant = ShippingCalculator.MAX_WEIGHT_KG == 30;

        return abstractMethod && defaultMethod && constant;
    }

    /*
        =================================================================
        CAPA 2) CONSTANTES Y EL ANTIPATRÓN DE "INTERFACE DE CONSTANTES"
        =================================================================

        Todo campo de una interface es una constante: public static final,
        aunque no lo escribas. No puede ser de otra forma, porque una
        interface no tiene estado.

        Una constante pertenece a una interface cuando es PARTE DEL
        CONTRATO: MAX_WEIGHT_KG le dice a toda implementación el límite
        que debe respetar.

        El antipatrón: crear una interface SOLO para agrupar constantes,
        e implementarla para poder escribirlas sin prefijo:
    */
    interface ShippingConstants {
        int MAX_PARCELS_PER_ORDER = 10;
        String DEFAULT_CARRIER = "Paquetería interna";
    }

    static final class ConstantsUser implements ShippingConstants { // MAL: implementa solo para ahorrarse el prefijo
        String carrier() {
            return DEFAULT_CARRIER;
        }
    }

    static boolean constantInterfaceExample() {
        ConstantsUser user = new ConstantsUser();
        Object asObject = user;

        // Ahora ConstantsUser "ES UN" ShippingConstants: un tipo sin ningún significado.
        boolean meaninglessType = asObject instanceof ShippingConstants;

        return user.carrier().equals("Paquetería interna") && meaninglessType;
    }

    /*
        Por qué está mal (Effective Java, "usa interfaces solo para
        definir tipos"):
        - implements significa "esta clase PUEDE HACER algo". Aquí no
          puede hacer nada nuevo: solo usa unos valores. El detalle
          interno (qué constantes usa) se volvió parte de su tipo público.
        - Todas sus hijas heredan esas constantes para siempre, aunque ya
          no las usen.

        Alternativas correctas:
        - Constantes de un concepto     -> en la clase o interface del concepto
        - Un grupo cerrado de valores   -> enum (Enums)
        - Constantes sueltas            -> clase final con constructor private
                                           (Static, CAPA 5)
        - Y si quieres escribirlas sin prefijo: import static (Static, CAPA 8)
    */

    /*
        =================================================================
        CAPA 3) MÉTODOS STATIC EN INTERFACES
        =================================================================

        Un método static de una interface sirve para dar utilidades o
        fábricas RELACIONADAS con ese contrato, sin necesitar una clase
        utilitaria aparte. El JDK los usa mucho:
            List.of(...)                Comparator.naturalOrder()
            Map.entry(k, v)             Path.of("archivo.txt")

        Antes de Java 8 había que crear una clase compañera (Collections
        para Collection, Paths para Path). Ahora todo vive en la interface.

        Diferencia técnica con los static de las CLASES:
        los métodos static de una interface NO se heredan. Solo se llaman
        con el nombre de la interface.

            ShippingCalculator.flatRate(...)     // OK
            WeightBasedShipping.flatRate(...)    // ERROR: cannot find symbol (no se hereda a la clase)
            weightBasedObject.flatRate(...)      // ERROR: cannot find symbol (tampoco con un objeto)

        Motivo: una clase puede implementar dos interfaces con un static
        del mismo nombre. Si se heredaran, no habría forma de saber cuál
        usar. Al obligar a escribir el nombre de la interface, no hay
        ambigüedad posible.
    */
    static boolean staticMethodsExample() {
        ShippingCalculator flat = ShippingCalculator.flatRate(new BigDecimal("99"));

        boolean factoryWorks = flat.costFor(1).compareTo(new BigDecimal("99")) == 0
                && flat.costFor(29).compareTo(new BigDecimal("99")) == 0;

        boolean utilityValidates;
        try {
            ShippingCalculator.requireValidWeight(45); // excede MAX_WEIGHT_KG
            utilityValidates = false;
        } catch (IllegalArgumentException e) {
            utilityValidates = true;
        }

        return factoryWorks && utilityValidates;
    }

    /*
        =================================================================
        CAPA 4) MÉTODOS PRIVATE EN INTERFACES
        =================================================================

        Problema que resuelven: dos métodos default necesitan el mismo
        cálculo interno. Antes de Java 9, ese cálculo tenía que ser otro
        default (y por lo tanto public: parte del contrato, aunque nadie
        de fuera debía usarlo) o duplicarse.

        Con private (Java 9+), la interface puede tener código interno
        que no forma parte del contrato. En ShippingCalculator:
        - roundMoney(...)      private de instancia: puede llamar a otros
                               métodos de la interface (como costFor).
        - insuranceFor(...)    private static: no necesita instancia.

        Reglas:
        - Un método private de interface DEBE tener cuerpo (no hay
          private abstract: nadie podría implementarlo).
        - Solo lo pueden usar los métodos default, static y private de la
          MISMA interface. Las clases que la implementan no lo ven.

            weightBasedObject.roundMoney(...)   // ERROR: cannot find symbol (para la clase no existe)
    */

    /*
        =================================================================
        CAPA 5) MÉTODOS DEFAULT A FONDO: EVOLUCIONAR UNA API
        =================================================================

        La razón REAL por la que existen los métodos default no es
        "ahorrar código": es poder AGREGAR métodos a una interface que ya
        usa mucha gente, sin romperla.

        El problema: imagina que ShippingCalculator ya se publicó con
        solo costFor(), y hay decenas de clases que la implementan en
        otros proyectos. Ahora necesitas agregar estimatedDays().

        - Si lo agregas como abstracto, TODAS esas clases dejan de
          compilar hasta que alguien lo implemente en cada una.
        - Si lo agregas como default con un valor razonable, todas
          siguen compilando y funcionando. Las que necesiten otro valor,
          lo sobrescriben.

        Así fue como Java 8 agregó forEach, removeIf, sort, stream...
        a Collection y List sin romper millones de clases existentes.

        Una clase "escrita antes de la versión 2" que no sabe que
        estimatedDays existe:
    */
    static final class LegacyCourierShipping implements ShippingCalculator {
        @Override
        public BigDecimal costFor(double weightKg) {
            ShippingCalculator.requireValidWeight(weightKg);
            return new BigDecimal("150");
        }
        // No menciona estimatedDays: lo hereda con el valor por defecto.
    }

    // Una clase nueva que sí necesita otro valor, lo sobrescribe.
    static final class ExpressShipping implements ShippingCalculator {
        @Override
        public BigDecimal costFor(double weightKg) {
            ShippingCalculator.requireValidWeight(weightKg);
            return new BigDecimal("300");
        }

        @Override
        public int estimatedDays() {
            return 1;
        }
    }

    static boolean defaultEvolutionExample() {
        List<ShippingCalculator> options = List.of(new LegacyCourierShipping(), new ExpressShipping());

        // El código cliente usa estimatedDays() en TODAS, incluso en la que nunca lo implementó.
        int fastest = Integer.MAX_VALUE;
        for (ShippingCalculator option : options) {
            fastest = Math.min(fastest, option.estimatedDays());
        }

        return new LegacyCourierShipping().estimatedDays() == 5 && fastest == 1;
    }

    /*
        Límites y cuidados de los default:

        1) No pueden tener estado: solo pueden trabajar llamando a otros
           métodos de la interface (como costWithInsurance llama a
           costFor).

        2) No pueden redefinir métodos de Object (equals, hashCode,
           toString). No compila:
               default String toString() { ... }
               // ERROR: default method toString in interface <Nombre> overrides a member of java.lang.Object
           Motivo: toda clase ya hereda esos métodos de Object, y la
           clase siempre gana (CAPA 6), así que ese default nunca se
           ejecutaría.

        3) Un default puede romper a una implementación que tenía reglas
           que el default desconoce. Ejemplo real: Collection.removeIf
           llegó como default, y las colecciones sincronizadas tuvieron
           que sobrescribirlo porque la versión por defecto no respetaba
           su sincronización. Al agregar un default a una interface
           pública, piensa en las implementaciones que no conoces.

        4) default no convierte a la interface en clase abstracta: sigue
           sin estado, sin constructores, y una clase puede implementar
           muchas.
    */

    /*
        =================================================================
        CAPA 6) REGLAS DE RESOLUCIÓN DE DEFAULT
        =================================================================

        Cuando una clase recibe el mismo método desde varios lugares,
        Java decide con tres reglas, en este orden:

        REGLA 1: LA CLASE GANA.
        Un método de la clase o de cualquier clase padre gana siempre a
        un default de una interface.
    */
    interface Labeled {
        default String label() {
            return "etiqueta de la interface";
        }
    }

    static class BaseParcel {
        public String label() {
            return "etiqueta de la clase padre";
        }
    }

    static final class Parcel extends BaseParcel implements Labeled {
        // No escribe label(): recibe una de BaseParcel y otra de Labeled.
    }

    /*
        REGLA 2: LA INTERFACE MÁS ESPECÍFICA GANA.
        Si una interface hija sobrescribe el default de su padre, gana
        la hija, porque es más concreta.
    */
    interface Tracker {
        default String status() {
            return "en tránsito";
        }
    }

    interface DetailedTracker extends Tracker {
        @Override
        default String status() {
            return "en tránsito: centro de distribución Norte";
        }
    }

    static final class SmartParcel implements Tracker, DetailedTracker {
        // Recibe status() de las dos, pero DetailedTracker es más específica.
    }

    /*
        REGLA 3: SI SIGUE HABIENDO EMPATE, DECIDES TÚ.
        Dos interfaces sin relación con el mismo default: la clase está
        OBLIGADA a sobrescribirlo, y puede llamar a cualquiera con
        Interfaz.super.metodo(). Ya lo viste en Inheritance, CAPA 9
        (Triathlete con Swimmer y Runner).
    */
    static boolean resolutionRulesExample() {
        boolean classWins = new Parcel().label().equals("etiqueta de la clase padre");
        boolean mostSpecificWins = new SmartParcel().status().equals("en tránsito: centro de distribución Norte");
        return classWins && mostSpecificWins;
    }

    /*
        =================================================================
        CAPA 7) HERENCIA ENTRE INTERFACES
        =================================================================

        Una interface puede extender a otras interfaces con extends, y a
        diferencia de las clases, puede extender VARIAS a la vez (no hay
        problema del diamante con el estado, porque no hay estado).

        Sirve para construir contratos grandes a partir de contratos
        pequeños, sin obligar a nadie a depender de lo que no usa:
        - quien solo necesita rastrear un paquete pide Trackable
        - quien solo necesita el valor asegurado pide Insurable
        - un envío premium es las dos cosas, más algo propio
    */
    interface Trackable {
        String trackingCode();
    }

    interface Insurable {
        BigDecimal insuredValue();
    }

    interface PremiumShipment extends Trackable, Insurable {
        BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");

        default boolean isHighValue() {
            return insuredValue().compareTo(HIGH_VALUE_THRESHOLD) >= 0;
        }
    }

    static final class PremiumParcel implements PremiumShipment {
        private final String trackingCode;
        private final BigDecimal insuredValue;

        PremiumParcel(String trackingCode, BigDecimal insuredValue) {
            this.trackingCode = Objects.requireNonNull(trackingCode, "trackingCode");
            this.insuredValue = Objects.requireNonNull(insuredValue, "insuredValue");
        }

        @Override
        public String trackingCode() {
            return trackingCode;
        }

        @Override
        public BigDecimal insuredValue() {
            return insuredValue;
        }
    }

    // Este método solo necesita poder rastrear: pide lo mínimo.
    static String trackingLabel(Trackable trackable) {
        return "Guía: " + trackable.trackingCode();
    }

    static boolean interfaceInheritanceExample() {
        PremiumParcel parcel = new PremiumParcel("MX-7788", new BigDecimal("25000"));

        // Un PremiumParcel ES Trackable, ES Insurable y ES PremiumShipment.
        boolean isAllThree = parcel instanceof Trackable && parcel instanceof Insurable;

        return isAllThree
                && trackingLabel(parcel).equals("Guía: MX-7788")
                && parcel.isHighValue();
    }

    /*
        Recuerda la sintaxis:
        - clase    -> extends UNA clase,  implements VARIAS interfaces
        - interface-> extends VARIAS interfaces (nunca implements)

            class PremiumParcel extends Parcel implements PremiumShipment, Comparable<...>
            interface PremiumShipment extends Trackable, Insurable
    */

    /*
        =================================================================
        CAPA 8) INTERFACES FUNCIONALES
        =================================================================

        Una INTERFACE FUNCIONAL es una interface con EXACTAMENTE UN
        método abstracto. (Los default, static y private no cuentan.
        Tampoco cuentan los que repiten métodos de Object, como equals:
        por eso Comparator sigue siendo funcional.)

        Representa UNA sola acción: "algo que transforma un precio",
        "algo que compara dos objetos", "algo que se puede ejecutar".

        La anotación @FunctionalInterface es opcional, pero pide al
        compilador que lo verifique: si alguien agrega un segundo método
        abstracto, deja de compilar.

            @FunctionalInterface
            interface PriceRule {
                BigDecimal apply(BigDecimal price);
                boolean isActive();      // ERROR: Unexpected @FunctionalInterface annotation
                                         //        (multiple non-overriding abstract methods)
            }
    */
    @FunctionalInterface
    interface PriceRule {
        BigDecimal apply(BigDecimal price);

        // default y static no cuentan: sigue siendo funcional.
        default String describe() {
            return "regla de precio";
        }
    }

    // Se puede implementar con una clase normal, como cualquier interface...
    static final class PercentageOff implements PriceRule {
        private final int percent;

        PercentageOff(int percent) {
            this.percent = percent;
        }

        @Override
        public BigDecimal apply(BigDecimal price) {
            return price.subtract(price.multiply(BigDecimal.valueOf(percent)).movePointLeft(2));
        }
    }

    static boolean functionalInterfaceExample() {
        PriceRule tenPercent = new PercentageOff(10);

        // ...pero su verdadero poder: se pueden implementar con una LAMBDA, en una línea.
        // (Las lambdas se explican en v7_functional/part1_lambdas/Lambdas; aquí solo verás que funcionan.)
        PriceRule fiftyOff = price -> price.subtract(new BigDecimal("50"));

        BigDecimal price = new BigDecimal("500");
        return tenPercent.apply(price).compareTo(new BigDecimal("450")) == 0
                && fiftyOff.apply(price).compareTo(new BigDecimal("450")) == 0;
    }

    /*
        Las interfaces funcionales que ya trae el JDK y que vas a usar
        todo el tiempo (cada una tiene su guía en v7_functional/):

            Runnable           void run()                    ejecutar algo
            Comparator<T>      int compare(T a, T b)         ordenar
            Function<T, R>     R apply(T t)                  transformar
            Predicate<T>       boolean test(T t)             filtrar
            Consumer<T>        void accept(T t)              consumir
            Supplier<T>        T get()                       producir

        Antes de crear tu propia interface funcional, revisa si una de
        estas ya sirve. Crea la tuya cuando el NOMBRE aporte significado
        de negocio (PriceRule dice más que Function<BigDecimal, BigDecimal>).
    */

    /*
        =================================================================
        CAPA 9) INTERFACES MARCADORAS
        =================================================================

        Una INTERFACE MARCADORA no tiene NINGÚN método. Solo sirve para
        "etiquetar" clases: implementarla dice "esta clase tiene cierta
        propiedad".

        Ejemplos del JDK:
            Serializable  -> los objetos se pueden convertir a bytes
            Cloneable     -> la clase permite clone() (diseño antiguo, evítalo)
            RandomAccess  -> la lista permite acceso rápido por índice

        RandomAccess es un caso real y útil: ArrayList la implementa
        (acceder a get(5000) es inmediato) y LinkedList no (tiene que
        recorrer 5000 nodos). Los algoritmos del JDK preguntan
        "instanceof RandomAccess" para elegir la estrategia más rápida.
    */
    interface Archivable {
        // vacía a propósito: solo marca
    }

    static final class Invoice implements Archivable {
        private final String folio;

        Invoice(String folio) {
            this.folio = folio;
        }

        String getFolio() {
            return folio;
        }
    }

    static final class Archive {
        private final List<Archivable> stored = new ArrayList<>();

        // El tipo del parámetro hace que el COMPILADOR rechace lo que no está marcado.
        void store(Archivable item) {
            stored.add(Objects.requireNonNull(item, "item"));
        }

        int size() {
            return stored.size();
        }
    }

    static boolean markerInterfaceExample() {
        Archive archive = new Archive();
        archive.store(new Invoice("F-0001"));
        // archive.store("un texto cualquiera");   // ERROR: String no es Archivable

        boolean jdkMarker = new ArrayList<String>() instanceof RandomAccess
                && !(new LinkedList<String>() instanceof RandomAccess);

        return archive.size() == 1 && jdkMarker;
    }

    /*
        Interface marcadora vs anotación (como @Deprecated):
        - Ambas sirven para etiquetar.
        - La interface CREA UN TIPO: el compilador puede rechazar en el
          momento de compilar lo que no está marcado (store(Archivable)).
          Una anotación no crea un tipo: el compilador no la revisa por sí
          solo (haría falta un procesador de anotaciones al compilar, o
          reflection al ejecutar).
        - La anotación sirve para etiquetar cosas que no son clases
          (métodos, campos) y para frameworks.
        Regla (Effective Java): si quieres poder escribir métodos que
        SOLO acepten objetos marcados, usa una interface marcadora.
    */

    /*
        =================================================================
        CAPA 10) DISEÑAR BUENAS INTERFACES
        =================================================================

        1) NOMBRES
           - Capacidades: adjetivos terminados en -able/-ible:
             Comparable, Runnable, Trackable, Insurable.
           - Roles: sustantivos: List, Repository, ShippingCalculator.
           - Nunca prefijo I (IShipping) ni sufijo Impl en las clases.

        2) PEQUEÑAS Y COHESIVAS
           Un método abstracto, o pocos y muy relacionados. Mejor varias
           pequeñas que se combinan (CAPA 7) que una gigante que obliga a
           implementar cosas que no aplican. (Es la "I" de SOLID:
           v10_design/part2_solid/InterfaceSegregation.)

        3) PIDE LO MÍNIMO EN TUS PARÁMETROS
           trackingLabel(Trackable) acepta cualquier cosa rastreable, no
           solo PremiumParcel.

        4) DEFAULT CON CUIDADO
           Úsalos para evolucionar la API o para comportamiento que se
           construye SOLO con los otros métodos del contrato. No para
           meter lógica de negocio que debería estar en una clase.

        5) PIENSA EN EL FUTURO ANTES DE PUBLICAR
           Agregar un método abstracto a una interface pública rompe a
           todos sus implementadores. Quitar cualquier método rompe a
           todos sus usuarios. El contrato público de una interface es lo
           más difícil de cambiar de todo un sistema.

        6) UNA INTERFACE POR CLASE NO ES UNA REGLA
           Crea la interface cuando haya un motivo: más de una
           implementación, o necesidad de cambiarla en pruebas (Abstraction,
           CAPA 10).

        7) SEALED
           Si quieres que SOLO ciertas clases puedan implementar tu
           interface, existe "sealed interface ... permits ..."
           (SealedAndPatternMatching).
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Escribir public abstract en cada método. Es implícito (CAPA 1).

        2) Creer que una interface puede tener estado. Sus campos son
           siempre constantes (CAPA 2).

        3) Crear interfaces solo para agrupar constantes (CAPA 2).

        4) Llamar un static de interface con el nombre de la clase que la
           implementa o con un objeto. Solo con el nombre de la interface
           (CAPA 3).

        5) Duplicar código entre métodos default en lugar de usar un
           método private (CAPA 4).

        6) Agregar un método abstracto a una interface ya publicada.
           Rompe a todos; usa default (CAPA 5).

        7) Intentar definir equals, hashCode o toString como default
           (CAPA 5).

        8) Esperar que el default gane sobre un método de una clase
           padre. La clase siempre gana (CAPA 6).

        9) Agregar un segundo método abstracto a una interface funcional
           y romper todas las lambdas que la usan. Por eso existe
           @FunctionalInterface (CAPA 8).

        10) Usar una anotación cuando necesitas que el compilador rechace
            lo que no está marcado (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Una interface puede tener: métodos abstractos, constantes, tipos
          anidados, default, static y private. Nunca estado ni
          constructores.
        - Constantes: solo las que son parte del contrato.
        - static: fábricas y utilidades del contrato; no se heredan.
        - private: código interno compartido por los default.
        - default: su propósito real es evolucionar APIs sin romper a los
          que ya implementan la interface.
        - Resolución: la clase gana; luego la interface más específica;
          si hay empate, decides tú con Interfaz.super.metodo().
        - Las interfaces extienden VARIAS interfaces.
        - Interface funcional: un solo método abstracto; se implementa con
          lambdas.
        - Interface marcadora: sin métodos; crea un tipo que el
          compilador revisa.
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
