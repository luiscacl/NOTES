package v10_design.part2_solid;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/*
    =====================================================================
    PRINCIPIO ABIERTO/CERRADO (LA O DE SOLID)
    =====================================================================

    Ruta de design/: guía 3 de 10  (part2_solid)
    Anterior:  part2_solid/SingleResponsibility
    Siguiente: part2_solid/LiskovSubstitution

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué dice el principio
        CAPA 2)  Por qué modificar código que funciona es caro
        CAPA 3)  El problema: el switch por tipo repartido por todo el código
        CAPA 4)  La solución clásica: una interface y una clase por variante
        CAPA 5)  Elegir la variante en tiempo de ejecución: un registro
        CAPA 6)  Extender con una lista de reglas
        CAPA 7)  El JDK ya funciona así
        CAPA 8)  Cerrado ante QUÉ cambios: el cierre estratégico
        CAPA 9)  sealed + switch: la otra forma de estar protegido
        CAPA 10) Cuando la variación son datos, no código
        CAPA 11) Cuándo NO aplicarlo
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class OpenClosed {

    public static void main(String[] args) {
        check(switchProblemExample(), "CAPA 3");
        check(polymorphicExample(), "CAPA 4");
        check(registryExample(), "CAPA 5");
        check(rulesExample(), "CAPA 6");
        check(jdkExample(), "CAPA 7");
        check(sealedExample(), "CAPA 9");
        check(dataInsteadOfCodeExample(), "CAPA 10");
        System.out.println("OpenClosed: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ DICE EL PRINCIPIO
        =================================================================

        Bertrand Meyer lo nombró en 1988 (y lo lograba con herencia de
        clases). La forma en que se cita hoy es de Robert C. Martin (1996):
            "Las entidades de software (clases, módulos, funciones) deben
             estar ABIERTAS a la extensión, pero CERRADAS a la modificación."

        Suena contradictorio. Significa:
        - ABIERTO A LA EXTENSIÓN: se le puede agregar comportamiento NUEVO
          (un método de pago nuevo, una regla de descuento nueva).
        - CERRADO A LA MODIFICACIÓN: para lograrlo NO hay que editar el
          código que ya existe y funciona.

        Dicho de forma práctica: lo nuevo se agrega ESCRIBIENDO CÓDIGO
        NUEVO (una clase, una regla), no metiendo otro if en el código
        viejo.

        La herramienta para lograrlo (la versión de Martin, la que se usa
        hoy) es la que ya conoces: ABSTRACCIÓN + POLIMORFISMO (v2_oop/part2_pillars/Abstraction y Polymorphism). El
        código estable depende de una interface; cada variante nueva es una
        implementación nueva.
    */

    /*
        =================================================================
        CAPA 2) POR QUÉ MODIFICAR CÓDIGO QUE FUNCIONA ES CARO
        =================================================================

        Si al final todo es código, ¿por qué importa si agrego o modifico?

        1) RIESGO: el código que ya funciona está probado (por pruebas y
           por los usuarios). Cada vez que lo editas puedes romper algo que
           funcionaba: una "regresión". Agregar una clase nueva no puede
           romper las que no tocaste.

        2) ALCANCE: si editas una clase que usan diez partes del sistema,
           hay que volver a probar las diez. Si agregas una clase nueva,
           solo pruebas la nueva.

        3) CONFLICTOS: cinco personas agregando cinco métodos de pago en el
           MISMO switch chocan entre sí. Cinco clases nuevas no.

        4) LIBRERÍAS: a veces NO PUEDES modificar. Si el código está en una
           librería (el JDK, un framework), sus usuarios no pueden editarlo;
           solo pueden EXTENDERLO por los puntos que dejó abiertos. Por eso
           las buenas librerías se diseñan así (CAPA 7).
    */

    /*
        =================================================================
        CAPA 3) EL PROBLEMA: EL SWITCH POR TIPO REPARTIDO POR TODO EL CÓDIGO
        =================================================================

        Una tienda acepta pagos con tarjeta, transferencia y efectivo. El
        código decide por un String:

            BigDecimal fee(String method, BigDecimal amount) {
                switch (method) {
                    case "CARD" -> ...
                    case "TRANSFER" -> ...
                    default -> BigDecimal.ZERO;
                }
            }

        Y como cada tipo tiene más comportamiento, aparecen MÁS switch en
        otros lugares: el nombre para mostrar, el tiempo de acreditación,
        si permite reembolso...

        Llega un pedido: "aceptar billetera digital". Hay que encontrar
        TODOS los switch del sistema y agregar el caso. Si olvidas uno,
        el default lo atrapa EN SILENCIO: la comisión de la billetera sale
        en 0, y nadie se entera hasta que finanzas revisa los números.

        Esto tiene nombre: "cirugía de escopeta" (shotgun surgery): un
        cambio pequeño obliga a tocar muchos lugares dispersos.
    */
    static BigDecimal feeWithSwitch(String method, BigDecimal amount) {
        return switch (method) {
            case "CARD" -> amount.multiply(new BigDecimal("0.035"));
            case "TRANSFER" -> new BigDecimal("5");
            case "CASH" -> BigDecimal.ZERO;
            default -> BigDecimal.ZERO;                     // la billetera nueva cae aquí sin avisar
        };
    }

    static String displayNameWithSwitch(String method) {   // otro switch, en otro archivo...
        return switch (method) {
            case "CARD" -> "Tarjeta";
            case "TRANSFER" -> "Transferencia";
            case "CASH" -> "Efectivo";
            case "WALLET" -> "Billetera digital";           // ...que sí se acordaron de actualizar
            default -> method;
        };
    }

    static boolean switchProblemExample() {
        BigDecimal walletFee = feeWithSwitch("WALLET", new BigDecimal("1000"));
        return displayNameWithSwitch("WALLET").equals("Billetera digital")   // un switch actualizado...
                && walletFee.signum() == 0;                                   // ...y el otro, olvidado: comisión 0
    }

    /*
        =================================================================
        CAPA 4) LA SOLUCIÓN CLÁSICA: UNA INTERFACE Y UNA CLASE POR VARIANTE
        =================================================================

        Todo lo que dependía del tipo se mueve a una interface. Cada método
        de pago es una clase que la implementa y tiene TODO su
        comportamiento junto (alta cohesión):

            interface PaymentMethod {
                String displayName();
                BigDecimal fee(BigDecimal amount);
            }

        El código que procesa pagos solo conoce la interface. Para agregar
        la billetera:
        - se escribe UNA clase nueva, Wallet;
        - el procesador NO se toca (cerrado a la modificación);
        - y no hay forma de "olvidar" un caso: si Wallet no implementa
          fee(), NO COMPILA. El compilador reemplaza a tu memoria.

        Es el patrón Strategy (v11_patterns/): cada variante del algoritmo es
        un objeto intercambiable.
    */
    interface PaymentMethod {
        String code();

        String displayName();

        BigDecimal fee(BigDecimal amount);
    }

    static final class Card implements PaymentMethod {
        @Override
        public String code() {
            return "CARD";
        }

        @Override
        public String displayName() {
            return "Tarjeta";
        }

        @Override
        public BigDecimal fee(BigDecimal amount) {
            return amount.multiply(new BigDecimal("0.035"));
        }
    }

    static final class Transfer implements PaymentMethod {
        @Override
        public String code() {
            return "TRANSFER";
        }

        @Override
        public String displayName() {
            return "Transferencia";
        }

        @Override
        public BigDecimal fee(BigDecimal amount) {
            return new BigDecimal("5");
        }
    }

    static final class Cash implements PaymentMethod {
        @Override
        public String code() {
            return "CASH";
        }

        @Override
        public String displayName() {
            return "Efectivo";
        }

        @Override
        public BigDecimal fee(BigDecimal amount) {
            return BigDecimal.ZERO;
        }
    }

    // La extensión: código NUEVO. Nada de lo anterior cambió.
    static final class Wallet implements PaymentMethod {
        @Override
        public String code() {
            return "WALLET";
        }

        @Override
        public String displayName() {
            return "Billetera digital";
        }

        @Override
        public BigDecimal fee(BigDecimal amount) {
            return amount.multiply(new BigDecimal("0.02"));
        }
    }

    // CERRADO: no sabe cuántos métodos de pago existen, ni le importa.
    static final class PaymentProcessor {
        String charge(PaymentMethod method, BigDecimal amount) {
            BigDecimal total = amount.add(method.fee(amount)).setScale(2, RoundingMode.HALF_UP);
            return method.displayName() + ": " + total;
        }
    }

    static boolean polymorphicExample() {
        PaymentProcessor processor = new PaymentProcessor();
        BigDecimal amount = new BigDecimal("1000");
        return processor.charge(new Card(), amount).equals("Tarjeta: 1035.00")
                && processor.charge(new Wallet(), amount).equals("Billetera digital: 1020.00");   // sin tocar el procesador
    }

    /*
        =================================================================
        CAPA 5) ELEGIR LA VARIANTE EN TIEMPO DE EJECUCIÓN: UN REGISTRO
        =================================================================

        En la vida real, el tipo llega como DATO: el cliente elige "WALLET"
        en un formulario, o viene en un JSON. En algún lugar hay que
        convertir ese texto en el objeto correcto. Si ahí vuelve a aparecer
        un switch, ¿no perdimos lo ganado?

        La salida: un REGISTRO, un mapa de código -> implementación
        (v7_functional/part1_lambdas/Lambdas, CAPA 11, hizo lo mismo con
        lambdas). El registro se arma en UN solo lugar al arrancar la
        aplicación; buscar es map.get, sin if.

        Ojo con la honestidad del principio: algo SÍ cambia al agregar una
        variante: la línea que la registra. OCP nunca es "cero cambios";
        es concentrar los cambios en UN lugar previsto (el punto donde se
        arman los objetos, que en v14_architecture/ se llama "composition
        root"), en vez de diez lugares dispersos. Los frameworks como
        Spring incluso hacen ese registro solos: encuentran todas las
        implementaciones de PaymentMethod al arrancar (v15_spring/).

        Y si llega un código desconocido, el registro FALLA con un mensaje
        claro, en lugar del default silencioso de la CAPA 3.
    */
    static final class PaymentMethodRegistry {
        private final Map<String, PaymentMethod> byCode = new LinkedHashMap<>();

        PaymentMethodRegistry register(PaymentMethod method) {
            byCode.put(method.code(), method);
            return this;
        }

        PaymentMethod find(String code) {
            PaymentMethod method = byCode.get(code);
            if (method == null) {
                throw new IllegalArgumentException("Método de pago no soportado: " + code);   // nada silencioso
            }
            return method;
        }

        List<String> available() {
            return byCode.values().stream().map(PaymentMethod::displayName).toList();
        }
    }

    static PaymentMethodRegistry buildRegistry() {          // el ÚNICO lugar que cambia al agregar un método
        return new PaymentMethodRegistry()
                .register(new Card())
                .register(new Transfer())
                .register(new Cash())
                .register(new Wallet());
    }

    static boolean registryExample() {
        PaymentMethodRegistry registry = buildRegistry();
        String fromForm = "WALLET";                         // llega como texto

        boolean unknownFailsLoudly;
        try {
            registry.find("BITCOIN");
            unknownFailsLoudly = false;
        } catch (IllegalArgumentException e) {
            unknownFailsLoudly = e.getMessage().equals("Método de pago no soportado: BITCOIN");
        }

        return new PaymentProcessor().charge(registry.find(fromForm), new BigDecimal("500")).equals("Billetera digital: 510.00")
                && registry.available().equals(List.of("Tarjeta", "Transferencia", "Efectivo", "Billetera digital"))
                && unknownFailsLoudly;
    }

    /*
        =================================================================
        CAPA 6) EXTENDER CON UNA LISTA DE REGLAS
        =================================================================

        Otra forma muy usada: cuando el comportamiento es una SECUENCIA de
        pasos o reglas, el código estable recorre una LISTA, y extender es
        agregar un elemento a la lista.

        Ejemplo: el precio final pasa por reglas (descuento por volumen,
        cupón, Buen Fin...). La calculadora no conoce ninguna regla en
        particular: aplica las que le den, en orden. Agregar la promoción
        de temporada es escribir la regla y agregarla a la lista; la
        calculadora no se toca.

        Las reglas pueden ser clases o, si son cortas, lambdas
        (v7_functional/part1_lambdas/FunctionalInterfaces): aquí son
        UnaryOperator<BigDecimal>, "un precio entra, un precio sale".
        Los filtros de las aplicaciones web, los interceptores y los
        validadores funcionan con esta misma idea.
    */
    static final class PriceCalculator {                    // CERRADA: no sabe qué reglas existen
        private final List<UnaryOperator<BigDecimal>> rules;

        PriceCalculator(List<UnaryOperator<BigDecimal>> rules) {
            this.rules = List.copyOf(rules);
        }

        BigDecimal finalPrice(BigDecimal basePrice) {
            BigDecimal price = basePrice;
            for (UnaryOperator<BigDecimal> rule : rules) {
                price = rule.apply(price);
            }
            return price.setScale(2, RoundingMode.HALF_UP);
        }
    }

    static final UnaryOperator<BigDecimal> VOLUME_DISCOUNT =
            price -> price.compareTo(new BigDecimal("1000")) >= 0 ? price.multiply(new BigDecimal("0.95")) : price;
    static final UnaryOperator<BigDecimal> COUPON_50 = price -> price.subtract(new BigDecimal("50")).max(BigDecimal.ZERO);
    static final UnaryOperator<BigDecimal> SEASONAL_SALE = price -> price.multiply(new BigDecimal("0.90"));   // regla NUEVA

    static boolean rulesExample() {
        List<UnaryOperator<BigDecimal>> rules = new ArrayList<>(List.of(VOLUME_DISCOUNT, COUPON_50));
        BigDecimal before = new PriceCalculator(rules).finalPrice(new BigDecimal("1000"));

        rules.add(SEASONAL_SALE);                           // extender = agregar a la lista
        BigDecimal after = new PriceCalculator(rules).finalPrice(new BigDecimal("1000"));

        return before.compareTo(new BigDecimal("900.00")) == 0      // 1000 * 0.95 - 50
                && after.compareTo(new BigDecimal("810.00")) == 0;  // ... * 0.90
    }

    /*
        =================================================================
        CAPA 7) EL JDK YA FUNCIONA ASÍ
        =================================================================

        Llevas toda la ruta usando código abierto/cerrado sin llamarlo así:

        - list.sort(comparador): el algoritmo de ordenamiento del JDK está
          CERRADO (no lo puedes ni lo necesitas editar) y ABIERTO a
          cualquier criterio de orden que inventes (v5_collections/
          part4_tools/Sorting).
        - HashMap y TreeMap funcionan con TUS clases, sin conocerlas,
          porque dependen de equals/hashCode/compareTo (v2_oop/
          part4_advanced_types/ObjectContract).
        - stream.filter(...).map(...): los streams no saben nada de tus
          reglas; tú las pasas como lambdas.
        - try-with-resources funciona con cualquier clase que implemente
          AutoCloseable.

        El ejemplo ordena con un criterio que el JDK jamás conoció, sin
        tocar una línea del JDK.
    */
    record Product(String name, int stock, int price) { }

    static boolean jdkExample() {
        List<Product> products = new ArrayList<>(List.of(
                new Product("mouse", 0, 250), new Product("teclado", 12, 500), new Product("cable", 3, 80)));

        Comparator<Product> inStockFirstThenCheapest = Comparator      // un criterio NUEVO, nuestro
                .comparing((Product product) -> product.stock() == 0)
                .thenComparingInt(Product::price);
        products.sort(inStockFirstThenCheapest);                       // el sort del JDK no cambió

        return products.stream().map(Product::name).toList().equals(List.of("cable", "teclado", "mouse"));
    }

    /*
        =================================================================
        CAPA 8) CERRADO ANTE QUÉ CAMBIOS: EL CIERRE ESTRATÉGICO
        =================================================================

        Ninguna clase está cerrada ante TODOS los cambios. PaymentProcessor
        está cerrado ante "agregar un método de pago". Pero si mañana piden
        "cobrar en cuotas", hay que modificarlo. Robert C. Martin lo llama
        CIERRE ESTRATÉGICO: eliges ante qué tipo de cambio proteges cada
        parte.

        ¿Cómo eliges? Por los cambios PROBABLES:
        - Los que ya ocurrieron: si ya llegaron tres métodos de pago, va a
          llegar un cuarto. La historia del proyecto es la mejor pista.
        - Los que el negocio anuncia: "este año abrimos en Colombia" ->
          impuestos y monedas van a variar.
        - Los que son naturales del dominio: reglas de descuento,
          proveedores de envío, formatos de exportación.

        A esto también se le llama "proteger los puntos de variación"
        (Protected Variations; part3_responsibilities/GRASP): identificas
        dónde se espera variación y pones una interface ESTABLE alrededor.
        En el resto del código, lo simple (BasicPrinciples, CAPAS 7 y 8).
    */

    /*
        =================================================================
        CAPA 9) SEALED + SWITCH: LA OTRA FORMA DE ESTAR PROTEGIDO
        =================================================================

        Java 21 cambió la discusión. Con una jerarquía sealed y un switch
        SIN default (v2_oop/part4_advanced_types/SealedAndPatternMatching),
        el compilador conoce TODAS las variantes. Si agregas una nueva, cada
        switch que no la cubre deja de COMPILAR:

            // ERROR: the switch expression does not cover all possible input values

        Eso resuelve el peligro real de la CAPA 3 (el caso olvidado que cae
        en silencio en un default), aunque técnicamente sí "modificas"
        cada switch. Es la otra mitad del "problema de la expresión" que
        explica SealedAndPatternMatching, CAPA 10:
        - Interface + clases (CAPA 4): agregar VARIANTES es fácil y
          cerrado; agregar OPERACIONES obliga a tocar todas las clases.
        - sealed + switch: agregar OPERACIONES es fácil (un switch nuevo);
          agregar VARIANTES obliga a tocar los switch, pero el compilador
          te lleva a cada uno.

        Para OCP, la conclusión práctica:
        - Variantes ABIERTAS (otros equipos o clientes agregarán las
          suyas; plugins; una librería): interface + polimorfismo.
        - Variantes FIJAS y tuyas, con muchas operaciones distintas
          (resultados, eventos, estados): sealed + switch exhaustivo.
        - Lo que NUNCA: switch sobre String o enum con default silencioso
          para decidir comportamiento repartido por el sistema.
    */
    sealed interface Shipment permits Standard, Express, StorePickup { }

    record Standard(int days) implements Shipment { }

    record Express(BigDecimal surcharge) implements Shipment { }

    record StorePickup(String store) implements Shipment { }

    static BigDecimal shippingCost(Shipment shipment) {
        return switch (shipment) {                          // sin default: si agregas una variante, no compila
            case Standard standard -> new BigDecimal("99");
            case Express express -> new BigDecimal("99").add(express.surcharge());
            case StorePickup pickup -> BigDecimal.ZERO;
        };
    }

    static String trackingMessage(Shipment shipment) {      // operación NUEVA: fácil, no toca los records
        return switch (shipment) {
            case Standard standard -> "Llega en " + standard.days() + " días";
            case Express express -> "Llega mañana";
            case StorePickup pickup -> "Recoge en " + pickup.store();
        };
    }

    static boolean sealedExample() {
        return shippingCost(new Express(new BigDecimal("150"))).compareTo(new BigDecimal("249")) == 0
                && shippingCost(new StorePickup("Centro")).signum() == 0
                && trackingMessage(new Standard(3)).equals("Llega en 3 días");
    }

    /*
        =================================================================
        CAPA 10) CUANDO LA VARIACIÓN SON DATOS, NO CÓDIGO
        =================================================================

        No toda variación necesita clases. Si lo que cambia es un VALOR
        (la tasa de IVA de cada país, el costo de envío por zona, el límite
        de crédito por tipo de cliente), una clase por caso es
        sobreingeniería. La extensión es agregar una FILA: a un mapa, a un
        archivo de configuración o a una tabla de la base de datos.

            Necesita CÓDIGO distinto por caso (algoritmos, pasos, llamadas
            a otros sistemas)              -> interface + clases (CAPA 4)
            Solo cambian NÚMEROS o textos  -> datos / configuración

        Así, abrir en Colombia puede no requerir ni recompilar: se agrega
        su tasa a la configuración. Es el código más cerrado posible.
    */
    static final class VatTable {
        private final Map<String, BigDecimal> rateByCountry;

        VatTable(Map<String, BigDecimal> rateByCountry) {
            this.rateByCountry = Map.copyOf(rateByCountry);  // vendría de configuración o de la base de datos
        }

        BigDecimal vatFor(String country, BigDecimal amount) {
            BigDecimal rate = rateByCountry.get(country);
            if (rate == null) {
                throw new IllegalArgumentException("País sin tasa configurada: " + country);
            }
            return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }
    }

    static boolean dataInsteadOfCodeExample() {
        VatTable table = new VatTable(Map.of(
                "MX", new BigDecimal("0.16"),
                "CO", new BigDecimal("0.19")));            // Colombia: una fila, cero clases
        return table.vatFor("MX", new BigDecimal("100")).compareTo(new BigDecimal("16.00")) == 0
                && table.vatFor("CO", new BigDecimal("100")).compareTo(new BigDecimal("19.00")) == 0;
    }

    /*
        =================================================================
        CAPA 11) CUÁNDO NO APLICARLO
        =================================================================

        OCP tiene un costo: más piezas (interfaces, clases, registros) y
        una indirección más al leer ("¿qué implementación se usa aquí?").
        Pagarlo donde NO hay variación es la sobreingeniería de
        BasicPrinciples, CAPAS 7 y 8.

        Un camino práctico y muy usado:
        1) La primera vez, escribe lo simple: un if, o un método directo.
        2) Cuando aparece la SEGUNDA variante, un if/else o un switch
           pequeño es aceptable.
        3) Cuando aparece la TERCERA, o el switch se repite en varios
           lugares, ese es el punto de variación real: refactoriza a
           interface + clases (part4_clean_code/Refactoring:
           "reemplazar condicional con polimorfismo").

        Y no lo apliques en lo que casi nunca cambia: un cálculo
        matemático, una conversión de unidades, un formato estándar.
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) switch/if por tipo repetido en varios lugares, con un default
           que esconde los casos olvidados (CAPA 3).

        2) Hacer polimorfismo y luego elegir la clase con un switch en cada
           lugar que la necesita: el switch debe vivir en UN solo lugar, o
           ser un registro (CAPA 5).

        3) Registros que devuelven null o una implementación "por defecto"
           ante un código desconocido, en vez de fallar (CAPA 5).

        4) Creer que OCP significa que nunca se modifica nada (CAPA 8).

        5) Interfaces y fábricas para algo que tiene una sola variante y no
           va a tener más (CAPA 11).

        6) Una clase por cada valor cuando lo que cambia son datos
           (CAPA 10).

        7) Poner default en un switch sobre un tipo sealed: se pierde el
           aviso del compilador (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Abierto a la extensión, cerrado a la modificación: lo nuevo se
          agrega con código NUEVO, no editando lo que funciona.
        - Por qué: menos regresiones, menos que volver a probar, menos
          conflictos, y las librerías no se pueden editar.
        - El síntoma: switch por tipo repetido ("cirugía de escopeta"),
          con defaults silenciosos.
        - Soluciones: interface + una clase por variante (Strategy); un
          registro para elegir por código; una lista de reglas.
        - Siempre algo cambia: concéntralo en UN lugar.
        - Cierre estratégico: protege los puntos de variación probables.
        - Variantes abiertas: polimorfismo. Variantes fijas con muchas
          operaciones: sealed + switch sin default.
        - Si lo que varía son números, son datos, no clases.
        - Primera vez simple; a la tercera variante, refactoriza.
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
