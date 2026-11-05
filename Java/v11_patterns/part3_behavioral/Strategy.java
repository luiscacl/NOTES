package v11_patterns.part3_behavioral;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

/*
    =====================================================================
    STRATEGY: ALGORITMOS INTERCAMBIABLES
    =====================================================================

    Ruta de patterns/: guía 21 de 28  (part3_behavioral)
    Anterior:  part3_behavioral/State
    Siguiente: part3_behavioral/TemplateMethod

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: ALTA. Es probablemente el patrón más usado de todos, y
    ya lo has visto muchas veces sin el nombre: los métodos de pago de
    v10_design/part2_solid/OpenClosed, cada Comparator que pasas a sort,
    cada lambda que pasas a filter. Esta guía lo formaliza y se enfoca en
    las decisiones prácticas: cómo elegirla, combinarla y cuándo NO crearla.

    Índice:
        CAPA 1)  Qué es, en una frase
        CAPA 2)  Estrategias como clases o como lambdas
        CAPA 3)  Elegir la estrategia al ejecutar, en un solo lugar
        CAPA 4)  Combinar estrategias
        CAPA 5)  Un conjunto fijo de estrategias: un enum
        CAPA 6)  Cuando un parámetro basta
        CAPA 7)  Strategy en el JDK
        CAPA 8)  Strategy, State y Template Method
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Strategy {

    public static void main(String[] args) {
        check(classesAndLambdasExample(), "CAPA 2");
        check(runtimeSelectionExample(), "CAPA 3");
        check(combinationExample(), "CAPA 4");
        check(enumStrategyExample(), "CAPA 5");
        check(parameterInsteadExample(), "CAPA 6");
        check(jdkStrategyExample(), "CAPA 7");
        System.out.println("Strategy: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES, EN UNA FRASE
        =================================================================

        STRATEGY: "Define una familia de algoritmos, encapsula cada uno y
        hazlos intercambiables. Permite que el algoritmo varíe
        independientemente de los clientes que lo usan."

        Dos piezas:
        - la ESTRATEGIA: una interface con la operación que varía (calcular
          un precio, comparar dos productos, elegir una ruta);
        - el CONTEXTO: el objeto que la USA sin saber cuál es (la
          cotización, el sort, el planificador de envíos).

        El problema que resuelve (el if/switch por tipo que crece y se
        repite) ya está desarrollado en v10_design/part2_solid/OpenClosed,
        CAPAS 3 y 4. Aquí el ejemplo es la cotización de la tienda: el
        precio final se calcula distinto para un cliente normal, uno de
        mayoreo y un empleado.
    */
    record Customer(String name, String segment) { }

    interface PricingStrategy {                             // STRATEGY
        BigDecimal priceFor(BigDecimal listPrice, int quantity);
    }

    static final class Quote {                              // CONTEXT
        private final PricingStrategy pricing;

        Quote(PricingStrategy pricing) {
            this.pricing = pricing;
        }

        BigDecimal total(BigDecimal listPrice, int quantity) {
            return pricing.priceFor(listPrice, quantity).setScale(2, RoundingMode.HALF_UP);   // no sabe cuál es
        }
    }

    /*
        =================================================================
        CAPA 2) ESTRATEGIAS COMO CLASES O COMO LAMBDAS
        =================================================================

        Si la interface de la estrategia es FUNCIONAL (un solo método), cada
        estrategia puede ser:
        - una CLASE, cuando tiene lógica de varias líneas, datos propios
          (una tabla de escalones) o necesita un nombre para encontrarla y
          probarla;
        - una LAMBDA, cuando cabe en una expresión clara.

        Las dos son intercambiables para el contexto: Quote recibe "un
        PricingStrategy", venga de donde venga. Es la razón por la que hoy
        muchas estrategias son simplemente lambdas o referencias a métodos
        (v7_functional/part1_lambdas/Lambdas, CAPA 1: pasar comportamiento
        como parámetro).
    */
    static final class VolumePricing implements PricingStrategy {   // con lógica y datos: una clase
        @Override
        public BigDecimal priceFor(BigDecimal listPrice, int quantity) {
            BigDecimal rate = quantity >= 100 ? new BigDecimal("0.80")
                    : quantity >= 20 ? new BigDecimal("0.90")
                    : BigDecimal.ONE;                       // escalones de mayoreo
            return listPrice.multiply(rate).multiply(BigDecimal.valueOf(quantity));
        }
    }

    static final PricingStrategy STANDARD =                 // simple: una lambda
            (listPrice, quantity) -> listPrice.multiply(BigDecimal.valueOf(quantity));

    static final PricingStrategy EMPLOYEE =
            (listPrice, quantity) -> listPrice.multiply(new BigDecimal("0.70")).multiply(BigDecimal.valueOf(quantity));

    static boolean classesAndLambdasExample() {
        BigDecimal price = new BigDecimal("100");
        return new Quote(STANDARD).total(price, 30).compareTo(new BigDecimal("3000.00")) == 0
                && new Quote(new VolumePricing()).total(price, 30).compareTo(new BigDecimal("2700.00")) == 0
                && new Quote(EMPLOYEE).total(price, 30).compareTo(new BigDecimal("2100.00")) == 0;
    }

    /*
        =================================================================
        CAPA 3) ELEGIR LA ESTRATEGIA AL EJECUTAR, EN UN SOLO LUGAR
        =================================================================

        En la vida real, la estrategia no se elige al escribir el código:
        depende del CLIENTE (su segmento), de la FECHA (una promoción de
        temporada), de la CONFIGURACIÓN (un país). La decisión se toma en
        UN lugar, una función "selectora", y el resto del sistema solo
        recibe la estrategia ya elegida.

        Si la elección depende de la fecha, la fecha llega de un Clock
        (v10_design/part2_solid/DependencyInversion, CAPA 9), para poder
        probar "qué pasa en Buen Fin" sin esperar a noviembre.

        El ejemplo: empleados siempre con su precio; mayoristas con
        escalones; y en Buen Fin, los clientes normales tienen 15%.
    */
    static PricingStrategy selectPricing(Customer customer, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        boolean buenFin = !today.isBefore(LocalDate.of(2024, 11, 15)) && !today.isAfter(LocalDate.of(2024, 11, 18));
        return switch (customer.segment()) {
            case "EMPLEADO" -> EMPLOYEE;
            case "MAYOREO" -> new VolumePricing();
            default -> buenFin
                    ? (listPrice, quantity) -> STANDARD.priceFor(listPrice, quantity).multiply(new BigDecimal("0.85"))
                    : STANDARD;
        };
    }

    static boolean runtimeSelectionExample() {
        Clock normalDay = Clock.fixed(Instant.parse("2024-10-01T12:00:00Z"), ZoneOffset.UTC);
        Clock buenFinDay = Clock.fixed(Instant.parse("2024-11-16T12:00:00Z"), ZoneOffset.UTC);
        Customer regular = new Customer("Ana", "NORMAL");
        BigDecimal price = new BigDecimal("1000");

        return new Quote(selectPricing(regular, normalDay)).total(price, 1).compareTo(new BigDecimal("1000.00")) == 0
                && new Quote(selectPricing(regular, buenFinDay)).total(price, 1).compareTo(new BigDecimal("850.00")) == 0
                && new Quote(selectPricing(new Customer("Luis", "EMPLEADO"), buenFinDay)).total(price, 1)
                        .compareTo(new BigDecimal("700.00")) == 0;
    }

    /*
        =================================================================
        CAPA 4) COMBINAR ESTRATEGIAS
        =================================================================

        Como las estrategias son objetos, se pueden combinar para formar
        estrategias nuevas, sin escribir cada combinación a mano:

        1) EL MEJOR DE VARIOS: aplicar todas las estrategias que le tocan a
           un cliente y quedarse con el precio más bajo ("siempre recibes
           el mejor precio que te corresponde"). La estrategia combinada
           también es un PricingStrategy.

        2) UNA DESPUÉS DE OTRA: primero el descuento, luego el redondeo a un
           precio "comercial". Si las estrategias son funciones, andThen
           las encadena (v7_functional/part1_lambdas/FunctionalInterfaces,
           CAPA 7).
    */
    static PricingStrategy bestOf(List<PricingStrategy> candidates) {   // una estrategia hecha de estrategias
        return (listPrice, quantity) -> candidates.stream()
                .map(strategy -> strategy.priceFor(listPrice, quantity))
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    static boolean combinationExample() {
        PricingStrategy best = bestOf(List.of(STANDARD, new VolumePricing(), EMPLOYEE));
        boolean employeeWinsForFew = new Quote(best).total(new BigDecimal("100"), 10).compareTo(new BigDecimal("700.00")) == 0;

        UnaryOperator<BigDecimal> tenOff = amount -> amount.multiply(new BigDecimal("0.90"));
        UnaryOperator<BigDecimal> toWholePesos = amount -> amount.setScale(0, RoundingMode.HALF_UP);
        BigDecimal pipeline = tenOff.andThen(toWholePesos).apply(new BigDecimal("1234.56"));   // 1111.10 -> 1111

        return employeeWinsForFew && pipeline.compareTo(new BigDecimal("1111")) == 0;
    }

    /*
        =================================================================
        CAPA 5) UN CONJUNTO FIJO DE ESTRATEGIAS: UN ENUM
        =================================================================

        Si las estrategias son POCAS, CONOCIDAS y SIN DATOS propios, un enum
        con comportamiento por constante es la forma más compacta (v2_oop/
        part4_advanced_types/Enums, CAPA 4):
        - se elige por nombre fácilmente (desde configuración:
          PriceRounding.valueOf("PSYCHOLOGICAL"));
        - se guarda en la base de datos por su nombre;
        - el compilador obliga a que cada constante implemente el método.

        El ejemplo: tres estrategias de redondeo de precios para mostrar en
        la tienda: a centavos, a múltiplos de 5 pesos, o terminado en .99
        (los "precios psicológicos" que usa el comercio).
    */
    enum PriceRounding {
        CENTS {
            @Override
            BigDecimal round(BigDecimal price) {
                return price.setScale(2, RoundingMode.HALF_UP);
            }
        },
        NEAREST_FIVE {
            @Override
            BigDecimal round(BigDecimal price) {
                return price.divide(BigDecimal.valueOf(5), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(5));
            }
        },
        PSYCHOLOGICAL {
            @Override
            BigDecimal round(BigDecimal price) {
                return price.setScale(0, RoundingMode.CEILING).subtract(new BigDecimal("0.01"));   // 1111.10 -> 1111.99
            }
        };

        abstract BigDecimal round(BigDecimal price);
    }

    static boolean enumStrategyExample() {
        BigDecimal price = new BigDecimal("1111.10");
        PriceRounding fromConfig = PriceRounding.valueOf("PSYCHOLOGICAL");
        return PriceRounding.CENTS.round(price).compareTo(new BigDecimal("1111.10")) == 0
                && PriceRounding.NEAREST_FIVE.round(price).compareTo(new BigDecimal("1110")) == 0
                && fromConfig.round(price).compareTo(new BigDecimal("1111.99")) == 0;
    }

    /*
        =================================================================
        CAPA 6) CUANDO UN PARÁMETRO BASTA
        =================================================================

        Un error frecuente al descubrir el patrón: crear una clase por cada
        VALOR. TenPercentOff, FifteenPercentOff, TwentyPercentOff... son el
        MISMO algoritmo con distinto número. Eso no es una familia de
        algoritmos: es UN algoritmo con un parámetro.

        La pregunta: ¿cambia el CÓMO o solo el CUÁNTO?
        - Si solo cambia un número (porcentaje, monto, límite): UNA clase
          (o lambda) con un parámetro, y el valor viene de configuración
          (v10_design/part2_solid/OpenClosed, CAPA 10).
        - Si cambia la forma de calcular (escalones vs. porcentaje vs.
          mejor de varios): estrategias distintas.
    */
    static PricingStrategy percentageOff(String percent) {  // UNA estrategia parametrizada, no una clase por número
        BigDecimal factor = BigDecimal.ONE.subtract(new BigDecimal(percent).movePointLeft(2));
        return (listPrice, quantity) -> listPrice.multiply(factor).multiply(BigDecimal.valueOf(quantity));
    }

    static boolean parameterInsteadExample() {
        BigDecimal price = new BigDecimal("200");
        return new Quote(percentageOff("10")).total(price, 1).compareTo(new BigDecimal("180.00")) == 0
                && new Quote(percentageOff("25")).total(price, 1).compareTo(new BigDecimal("150.00")) == 0;
    }

    /*
        =================================================================
        CAPA 7) STRATEGY EN EL JDK
        =================================================================

        El JDK está lleno de estrategias; ya las usas:
        - Comparator: la estrategia de COMPARAR que recibe sort, TreeMap,
          PriorityQueue (v5_collections/part4_tools/Sorting).
        - RoundingMode: SELECCIONA la estrategia de REDONDEO que usa
          BigDecimal.setScale (v1_fundamentals/part4_data_and_input/Numbers,
          CAPA 7). Ojo: es un enum SIN comportamiento (la lógica vive dentro
          de BigDecimal): un selector de estrategia, no un enum con
          comportamiento por constante como el de la CAPA 5.
        - Los Collector de los streams: la estrategia de JUNTAR resultados
          (v7_functional/part2_streams/AdvancedStreams).
        - En concurrencia, qué hacer cuando un grupo de hilos está lleno se
          configura pasando una estrategia (v13_concurrency/).

        El ejemplo usa la estrategia de redondeo del JDK: el mismo número,
        tres estrategias, tres resultados.
    */
    static boolean jdkStrategyExample() {
        BigDecimal value = new BigDecimal("2.5");
        return value.setScale(0, RoundingMode.HALF_UP).intValue() == 3
                && value.setScale(0, RoundingMode.HALF_EVEN).intValue() == 2      // redondeo del banquero
                && value.setScale(0, RoundingMode.DOWN).intValue() == 2;
    }

    /*
        =================================================================
        CAPA 8) STRATEGY, STATE Y TEMPLATE METHOD
        =================================================================

        | Patrón          | Cómo varía el comportamiento                   | Quién decide                   |
        |-----------------|------------------------------------------------|--------------------------------|
        | Strategy        | un objeto (o lambda) intercambiable, por       | quien USA el contexto, desde   |
        |                 | COMPOSICIÓN                                    | afuera                         |
        | State           | igual en estructura, pero el objeto cambia de  | el propio objeto, en sus       |
        |                 | estrategia solo, según sus transiciones        | transiciones                   |
        |                 | (part3_behavioral/State, CAPA 7)               |                                |
        | Template Method | una SUBCLASE sobrescribe pasos de un proceso   | al escribir la clase (herencia)|
        |                 | (part3_behavioral/TemplateMethod)              |                                |

        Entre Strategy y Template Method, hoy se prefiere Strategy:
        composición sobre herencia (v2_oop/part3_interfaces_and_
        relationships/CompositionAndAggregation, CAPA 6), se cambia al
        ejecutar, y acepta lambdas.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Repetir el if/switch para elegir la estrategia en varios
           lugares, en vez de un selector único (CAPA 3).

        2) Elegir por fecha con LocalDate.now() escondido: imposible de
           probar (CAPA 3).

        3) Escribir a mano cada combinación de estrategias en vez de
           componerlas (CAPA 4).

        4) Una clase por cada valor de un parámetro (CAPA 6).

        5) Estrategias que necesitan saber qué tipo de contexto las usa
           (instanceof del contexto): ya no son intercambiables.

        6) Crear una interface de estrategia para un algoritmo que nunca va
           a tener otra versión (v10_design/part1_basics/BasicPrinciples,
           CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Strategy: algoritmos intercambiables detrás de una interface; el
          contexto los usa sin saber cuál es.
        - Clases si tienen lógica o datos; lambdas si caben en una
          expresión.
        - La elección se hace en UN selector (cliente, fecha con Clock,
          configuración).
        - Se combinan: el mejor de varios, o en cadena con andThen.
        - Pocas y fijas: un enum con comportamiento por constante.
        - Si solo cambia un número, es un parámetro, no otra estrategia.
        - En el JDK: Comparator, RoundingMode, Collector.
        - Strategy (composición, desde afuera) se prefiere sobre Template
          Method (herencia).
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
