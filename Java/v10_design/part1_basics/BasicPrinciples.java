package v10_design.part1_basics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/*
    =====================================================================
    PRINCIPIOS BÁSICOS DE DISEÑO — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de design/: guía 1 de 10  (part1_basics)
    Anterior:  v9_tools/.../quality/git/Git  (última de v9_tools/)
               (v9_tools/ se estudia cuando el proyecto pase a Maven)
    Siguiente: part2_solid/SingleResponsibility

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Varios ejemplos muestran primero la versión MALA y luego la buena.
      La mala también compila y funciona: el problema de un mal diseño
      casi nunca es que "no funcione", sino lo que cuesta CAMBIARLO.

    Índice:
        CAPA 1)  Qué es el diseño y para qué sirven los principios
        CAPA 2)  Cohesión: lo que está junto debe pertenecer junto
        CAPA 3)  Acoplamiento: cuánto sabe una parte de otra
        CAPA 4)  Separación de responsabilidades (separation of concerns)
        CAPA 5)  DRY: una sola fuente de verdad para cada regla
        CAPA 6)  Cuándo NO aplicar DRY: la duplicación accidental
        CAPA 7)  KISS: la solución más simple que funciona
        CAPA 8)  YAGNI: no construyas lo que no necesitas hoy
        CAPA 9)  Principio de menor sorpresa
        CAPA 10) Separar consultas de comandos (CQS)
        CAPA 11) Los principios que ya conoces
        CAPA 12) Cuando los principios chocan
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class BasicPrinciples {

    public static void main(String[] args) {
        check(cohesionExample(), "CAPA 2");
        check(couplingExample(), "CAPA 3");
        check(separationExample(), "CAPA 4");
        check(dryExample(), "CAPA 5");
        check(accidentalDuplicationExample(), "CAPA 6");
        check(kissExample(), "CAPA 7");
        check(leastSurpriseExample(), "CAPA 9");
        check(commandQueryExample(), "CAPA 10");
        check(failFastExample(), "CAPA 11");
        System.out.println("BasicPrinciples: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES EL DISEÑO Y PARA QUÉ SIRVEN LOS PRINCIPIOS
        =================================================================

        DISEÑAR es decidir CÓMO se reparte el código: qué clases existen,
        qué hace cada una, quién conoce a quién. Dos programas pueden hacer
        exactamente lo mismo hoy y tener diseños muy distintos.

        ¿Por qué importa, si los dos funcionan? Por el CAMBIO. Un programa
        real se escribe una vez y se modifica durante años: llegan reglas
        nuevas, se corrigen errores, entran personas que no lo escribieron.
        Se calcula que se pasa muchísimo más tiempo LEYENDO y MODIFICANDO
        código que escribiéndolo por primera vez.

        Un buen diseño es el que hace que los cambios PROBABLES sean
        BARATOS:
        - un cambio toca POCOS lugares, y es obvio cuáles;
        - cambiar una parte no rompe otras que no tienen nada que ver;
        - se puede entender una parte sin leer todo el programa;
        - se puede PROBAR cada parte por separado.

        Los PRINCIPIOS de diseño son lecciones aprendidas por generaciones
        de programadores sobre qué hace que el código sea fácil o difícil
        de cambiar. Tres advertencias antes de empezar:
        1) Son GUÍAS, no leyes. Aplicarlos sin pensar produce código peor.
        2) A veces CHOCAN entre sí (CAPA 12): hay que elegir.
        3) Casi todos dicen, de distintas formas, lo mismo: ALTA COHESIÓN y
           BAJO ACOPLAMIENTO (CAPAS 2 y 3). Si entiendes esas dos ideas,
           entiendes el resto.
    */

    /*
        =================================================================
        CAPA 2) COHESIÓN: LO QUE ESTÁ JUNTO DEBE PERTENECER JUNTO
        =================================================================

        La COHESIÓN mide qué tan relacionadas están las cosas DENTRO de una
        misma pieza (un método, una clase, un paquete).

        - ALTA cohesión: todo lo que hay dentro trabaja para UN mismo
          propósito. Una clase Cart con agregar, quitar y calcular el total
          del carrito.
        - BAJA cohesión: cosas sin relación juntas porque "había que
          ponerlas en algún lado". La clase Utils o Helper con enviar
          correos, calcular impuestos y formatear fechas.

        Por qué importa la baja cohesión:
        - Para entender o cambiar UNA cosa hay que navegar entre muchas que
          no tienen que ver.
        - Muchas razones distintas para modificar el mismo archivo: el
          equipo de impuestos y el de correos editan la misma clase y
          chocan.
        - Lo que usa una parte arrastra la dependencia de todo lo demás.

        Una prueba práctica: intenta describir la clase en UNA frase sin
        usar "y". "Calcula el total del carrito" es cohesiva. "Envía
        correos y calcula impuestos y formatea fechas" no.

        Otra señal: grupos de campos que solo usan grupos de métodos. Si
        la mitad de los métodos usa unos campos y la otra mitad usa otros,
        en realidad son dos clases.
    */

    // BAJA cohesión: tres propósitos sin relación en una clase.
    static final class StoreUtils {
        static BigDecimal taxFor(BigDecimal amount) {
            return amount.multiply(new BigDecimal("0.16"));
        }

        static String welcomeEmail(String name) {
            return "Hola " + name + ", bienvenido";
        }

        static boolean isValidSku(String sku) {
            return sku.matches("[A-Z]{3}-\\d{3}");
        }
    }

    // ALTA cohesión: todo gira alrededor del carrito.
    static final class Cart {
        private final List<BigDecimal> prices = new ArrayList<>();

        void add(BigDecimal price) {
            prices.add(price);
        }

        int itemCount() {
            return prices.size();
        }

        BigDecimal subtotal() {
            return prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    static boolean cohesionExample() {
        Cart cart = new Cart();
        cart.add(new BigDecimal("100"));
        cart.add(new BigDecimal("250"));
        return cart.subtotal().compareTo(new BigDecimal("350")) == 0 && cart.itemCount() == 2
                && StoreUtils.isValidSku("ABC-123");    // funciona... pero no pertenece con los otros dos
    }

    /*
        =================================================================
        CAPA 3) ACOPLAMIENTO: CUÁNTO SABE UNA PARTE DE OTRA
        =================================================================

        El ACOPLAMIENTO mide cuánto DEPENDE una pieza de otra: cuánto
        necesita saber de ella para funcionar, y por lo tanto, cuánto la
        afecta que la otra cambie.

        Cero acoplamiento es imposible (las piezas colaboran). La meta es
        que sea BAJO y por CANALES CLAROS. Formas de acoplamiento, de peor
        a mejor:

        1) DE CONTENIDO: una clase toca los datos internos de otra
           (campos públicos, listas internas expuestas). Cualquier cambio
           interno rompe a la otra (v2_oop/part2_pillars/Encapsulation).

        2) POR ESTADO GLOBAL: varias partes leen y escriben la misma
           variable static mutable. Nadie sabe quién la cambió; un cambio
           en un lugar afecta a todos (v2_oop/part1_basics/Static, CAPA 10).

        3) DE CONTROL: le pasas a un método una BANDERA que decide QUÉ
           hacer: print(report, true). Quien llama tiene que conocer la
           lógica interna ("true significa formato corto"), y el método
           hace dos cosas.

        4) DE DATOS: le pasas solo los datos que necesita, por parámetros,
           y él devuelve un resultado. Es el bueno.

        Y el acoplamiento más sano es hacia ABSTRACCIONES (interfaces) en
        vez de hacia clases concretas (v2_oop/part2_pillars/Abstraction,
        CAPA 4; part2_solid/DependencyInversion).

        El ejemplo muestra el acoplamiento de control: un booleano que
        nadie entiende al leer la llamada, contra dos métodos con nombre.
    */
    record Invoice(String customer, BigDecimal total) { }

    // Acoplamiento de CONTROL: ¿qué significa true?
    static String format(Invoice invoice, boolean shortVersion) {
        if (shortVersion) {
            return invoice.customer() + ": " + invoice.total();
        }
        return "Factura para " + invoice.customer() + "\nTotal: $" + invoice.total();
    }

    // Acoplamiento de DATOS: cada método hace una cosa y su nombre lo dice.
    static String summaryLine(Invoice invoice) {
        return invoice.customer() + ": " + invoice.total();
    }

    static String fullText(Invoice invoice) {
        return "Factura para " + invoice.customer() + "\nTotal: $" + invoice.total();
    }

    // Acoplamiento por ESTADO GLOBAL: cualquiera lo cambia, todos lo sufren.
    static String globalCurrency = "MXN";

    static String priceTagGlobal(BigDecimal amount) {
        return amount + " " + globalCurrency;               // depende de quién tocó la variable antes
    }

    static String priceTag(BigDecimal amount, String currency) {
        return amount + " " + currency;                     // todo lo que necesita llega por parámetro
    }

    static boolean couplingExample() {
        Invoice invoice = new Invoice("Ana", new BigDecimal("350"));
        boolean sameResult = format(invoice, true).equals(summaryLine(invoice))
                && format(invoice, false).equals(fullText(invoice));

        String before = priceTagGlobal(BigDecimal.TEN);
        globalCurrency = "USD";                             // otra parte del programa lo cambia...
        String after = priceTagGlobal(BigDecimal.TEN);      // ...y esta cambia sin que nadie la toque
        globalCurrency = "MXN";

        return sameResult && !before.equals(after)
                && priceTag(BigDecimal.TEN, "MXN").equals(priceTag(BigDecimal.TEN, "MXN"));   // siempre igual
    }

    /*
        =================================================================
        CAPA 4) SEPARACIÓN DE RESPONSABILIDADES (SEPARATION OF CONCERNS)
        =================================================================

        Un programa mezcla varias PREOCUPACIONES (concerns) distintas:
        - REGLAS DE NEGOCIO: cuánto descuento aplica, cuándo un pedido es
          válido. Es lo valioso y lo que más se prueba.
        - PRESENTACIÓN: cómo se muestra (texto, HTML, JSON, el formato de
          moneda).
        - PERSISTENCIA: cómo se guarda (base de datos, archivo).
        - INFRAESTRUCTURA: red, correo, logs, configuración.

        Separarlas significa que cada pieza se ocupa de UNA. Así:
        - cambiar el formato del recibo no puede romper el cálculo del
          descuento;
        - la regla de negocio se prueba sin base de datos ni pantalla;
        - se puede cambiar la base de datos sin tocar las reglas.

        Esta idea, aplicada a gran escala, da las "capas" de una
        aplicación (controlador, servicio, repositorio) que verás en
        v14_architecture/ y v15_spring/.

        El ejemplo: un método que calcula, formatea y "guarda" todo junto,
        contra la misma lógica separada. Fíjate en que la versión separada
        permite probar el cálculo con un simple assert, sin texto de por
        medio.
    */

    // Todo mezclado: cálculo + formato + guardado en el mismo método.
    static final class CheckoutMixed {
        final List<String> savedReceipts = new ArrayList<>();

        String checkout(String customer, BigDecimal subtotal) {
            BigDecimal discount = subtotal.compareTo(new BigDecimal("1000")) >= 0
                    ? subtotal.multiply(new BigDecimal("0.10")) : BigDecimal.ZERO;
            BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
            String receipt = String.format(Locale.ROOT, "%s pagó %.2f", customer, total);
            savedReceipts.add(receipt);                     // "guardar"
            return receipt;
        }
    }

    // Separado: cada pieza con una preocupación.
    static final class DiscountPolicy {                     // regla de negocio: pura, sin texto ni guardado
        BigDecimal totalFor(BigDecimal subtotal) {
            BigDecimal discount = subtotal.compareTo(new BigDecimal("1000")) >= 0
                    ? subtotal.multiply(new BigDecimal("0.10")) : BigDecimal.ZERO;
            return subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        }
    }

    static final class ReceiptFormatter {                   // presentación
        String format(String customer, BigDecimal total) {
            return String.format(Locale.ROOT, "%s pagó %.2f", customer, total);
        }
    }

    static final class ReceiptRepository {                  // persistencia (aquí, en memoria)
        final List<String> saved = new ArrayList<>();

        void save(String receipt) {
            saved.add(receipt);
        }
    }

    static boolean separationExample() {
        String mixed = new CheckoutMixed().checkout("Ana", new BigDecimal("1200"));

        DiscountPolicy policy = new DiscountPolicy();
        BigDecimal total = policy.totalFor(new BigDecimal("1200"));     // la regla se prueba SOLA
        ReceiptRepository repository = new ReceiptRepository();
        String receipt = new ReceiptFormatter().format("Ana", total);
        repository.save(receipt);

        return total.compareTo(new BigDecimal("1080.00")) == 0
                && policy.totalFor(new BigDecimal("999")).compareTo(new BigDecimal("999.00")) == 0
                && receipt.equals(mixed) && repository.saved.size() == 1;
    }

    /*
        =================================================================
        CAPA 5) DRY: UNA SOLA FUENTE DE VERDAD PARA CADA REGLA
        =================================================================

        DRY: "Don't Repeat Yourself", no te repitas. La definición
        original (del libro The Pragmatic Programmer) es más precisa que
        "no copies código":

            "Cada pieza de CONOCIMIENTO debe tener una representación
             única, sin ambigüedad y con autoridad dentro del sistema."

        Lo que no se debe repetir es el CONOCIMIENTO: una regla de negocio,
        un dato de configuración, una decisión. Por ejemplo, "el IVA es
        16%". Si ese 0.16 aparece escrito en tres lugares (el carrito, la
        factura y el reporte), el día que cambie alguien actualizará dos y
        olvidará uno: el carrito cobrará distinto de lo que dice la
        factura. El error no está en ninguna línea concreta: está en que la
        regla existía tres veces.

        La solución es una SOLA FUENTE DE VERDAD: una constante, un método
        o una clase que tiene la regla, y todos los demás la USAN.
    */
    static final class TaxRules {                           // la única fuente de verdad del IVA
        static final BigDecimal VAT_RATE = new BigDecimal("0.16");

        static BigDecimal vatFor(BigDecimal amount) {
            return amount.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
        }
    }

    static BigDecimal cartTotal(BigDecimal subtotal) {
        return subtotal.add(TaxRules.vatFor(subtotal));     // usa la regla, no la repite
    }

    static String invoiceTaxLine(BigDecimal subtotal) {
        return "IVA: " + TaxRules.vatFor(subtotal);         // la misma regla, el mismo resultado
    }

    static boolean dryExample() {
        BigDecimal subtotal = new BigDecimal("1000");
        return cartTotal(subtotal).compareTo(new BigDecimal("1160.00")) == 0
                && invoiceTaxLine(subtotal).equals("IVA: 160.00");
    }

    /*
        =================================================================
        CAPA 6) CUÁNDO NO APLICAR DRY: LA DUPLICACIÓN ACCIDENTAL
        =================================================================

        El error opuesto es igual de común: ver dos pedazos de código
        PARECIDOS y unirlos, aunque representen conocimientos DISTINTOS.

        Ejemplo: en la tienda, el envío es gratis desde $500, y desde $500
        también se regala una muestra. Mismo número, mismo if. Alguien
        aplica "DRY" y crea:
            static final BigDecimal MINIMUM = new BigDecimal("500");
        y lo usan las dos reglas.

        Seis meses después, marketing sube la muestra gratis a $800. Se
        cambia MINIMUM... y sin que nadie lo pida, el envío gratis TAMBIÉN
        sube a $800. Dos reglas que se parecían por CASUALIDAD quedaron
        unidas, y un cambio en una rompió la otra.

        La pregunta correcta no es "¿se ven iguales?", sino "¿cambian por
        la MISMA razón y al mismo tiempo?". Si la respuesta es no, es
        DUPLICACIÓN ACCIDENTAL: déjalas separadas, aunque tengan el mismo
        valor hoy.

        Una regla práctica muy usada, la "regla de tres": la primera vez,
        escribe; la segunda vez que algo se parece, tolera la repetición;
        a la tercera, cuando ya ves el patrón real, extrae. Una abstracción
        prematura y equivocada cuesta más que un poco de duplicación,
        porque deshacerla es más difícil.
    */
    // Unido por casualidad: un solo número para dos reglas distintas.
    static final class MergedThresholds {
        static BigDecimal minimum = new BigDecimal("500");

        static boolean freeShipping(BigDecimal subtotal) {
            return subtotal.compareTo(minimum) >= 0;
        }

        static boolean freeSample(BigDecimal subtotal) {
            return subtotal.compareTo(minimum) >= 0;
        }
    }

    // Cada regla con su propio conocimiento, aunque hoy valgan lo mismo.
    static final class ShippingPolicy {
        static final BigDecimal FREE_SHIPPING_FROM = new BigDecimal("500");
    }

    static final class PromotionPolicy {
        static final BigDecimal FREE_SAMPLE_FROM = new BigDecimal("800");   // marketing la cambió: nada más se movió
    }

    static boolean accidentalDuplicationExample() {
        BigDecimal subtotal = new BigDecimal("600");

        MergedThresholds.minimum = new BigDecimal("800");   // "solo cambiamos la muestra gratis"...
        boolean shippingBrokeToo = !MergedThresholds.freeShipping(subtotal);   // ...y el envío dejó de ser gratis
        MergedThresholds.minimum = new BigDecimal("500");

        boolean separateRulesStayCorrect = subtotal.compareTo(ShippingPolicy.FREE_SHIPPING_FROM) >= 0
                && subtotal.compareTo(PromotionPolicy.FREE_SAMPLE_FROM) < 0;

        return shippingBrokeToo && separateRulesStayCorrect && MergedThresholds.freeSample(subtotal);
    }

    /*
        =================================================================
        CAPA 7) KISS: LA SOLUCIÓN MÁS SIMPLE QUE FUNCIONA
        =================================================================

        KISS: "Keep It Simple, Stupid" (mantenlo simple). La solución más
        simple que resuelve el problema REAL suele ser la mejor: menos
        código que leer, menos lugares donde equivocarse, más fácil de
        cambiar.

        Hay dos tipos de complejidad:
        - ESENCIAL: la que tiene el problema mismo. Calcular impuestos de
          varios países ES complicado; el código no puede ser más simple
          que eso.
        - ACCIDENTAL: la que agregamos nosotros: capas, interfaces,
          configuraciones y patrones que el problema no pedía.
        KISS ataca la accidental.

        Señales de que no es simple:
        - Necesitas un diagrama para explicar algo que hace una sola cosa.
        - Una interface con una sola implementación "por si acaso"
          (CAPA 8).
        - Código "ingenioso": una línea que hace cinco cosas y hay que
          descifrar. Escribe para quien lo va a leer a las 3 de la mañana
          arreglando un error.
        - Reinventar algo que el JDK ya hace (v5_collections/part4_tools/
          CollectionsAndArraysUtilities).

        Simple NO significa "sin diseño" ni "todo en un método": un método
        de 300 líneas no es simple, es fácil de escribir y difícil de
        entender. Simple es lo que se entiende rápido.
    */
    // Complicado sin necesidad: una "fábrica de estrategias" para UNA regla que no varía.
    interface DiscountStrategy {
        BigDecimal apply(BigDecimal amount);
    }

    static final class DiscountStrategyFactory {
        static DiscountStrategy create(String type) {
            if (type.equals("STUDENT")) {
                return amount -> amount.multiply(new BigDecimal("0.90"));
            }
            throw new IllegalArgumentException("Tipo desconocido: " + type);
        }
    }

    // Simple: lo que el problema pide hoy.
    static BigDecimal studentPrice(BigDecimal price) {
        return price.multiply(new BigDecimal("0.90"));
    }

    static boolean kissExample() {
        BigDecimal price = new BigDecimal("100");
        return DiscountStrategyFactory.create("STUDENT").apply(price).compareTo(studentPrice(price)) == 0;
    }

    /*
        =================================================================
        CAPA 8) YAGNI: NO CONSTRUYAS LO QUE NO NECESITAS HOY
        =================================================================

        YAGNI: "You Aren't Gonna Need It" (no lo vas a necesitar). No
        agregues funcionalidad ni flexibilidad pensando en un futuro
        IMAGINADO: "por si algún día soportamos otra moneda", "por si
        cambiamos de base de datos", "dejo este parámetro por si acaso".

        Por qué, si "prepararse" suena responsable:
        - La mayoría de esos futuros NUNCA llegan, y cuando llega un cambio
          real, casi nunca es el que imaginaste.
        - Todo lo que construyes hoy hay que leerlo, probarlo y mantenerlo
          desde hoy, se use o no.
        - Las abstracciones especulativas hacen el código más difícil de
          cambiar en la dirección que de verdad haga falta.

        YAGNI NO dice "diseña mal". La forma de prepararse para el futuro
        es tener código FÁCIL DE CAMBIAR (cohesivo, poco acoplado, con
        pruebas), no código que ya incluya todos los cambios posibles.
        Cuando el cambio llegue, se agrega la flexibilidad que haga falta,
        y no antes.

        (Esta capa no tiene ejemplo de código a propósito: el mejor
        ejemplo de YAGNI es el código que no se escribió. El de la CAPA 7,
        la fábrica con una sola estrategia, es justo lo que YAGNI evita.)
    */

    /*
        =================================================================
        CAPA 9) PRINCIPIO DE MENOR SORPRESA
        =================================================================

        Un método, una clase o una API deben comportarse como esperaría
        alguien que lee su NOMBRE, sin sorpresas. Si hay que leer la
        implementación para saber qué hace, el nombre está mintiendo.

        Sorpresas típicas:
        - Un "getX()" que además modifica algo, guarda en la base de datos o
          tarda segundos.
        - Un método que cambia el objeto que le pasaste como parámetro
          cuando parecía solo leerlo.
        - equals sin hashCode, o compareTo inconsistente con equals
          (v2_oop/part4_advanced_types/ObjectContract): rompen lo que
          cualquiera espera de las colecciones.
        - Un método que a veces devuelve null y a veces lanza excepción
          para el mismo caso.

        El ejemplo: un método que "calcula el total" pero, de paso, vacía
        la lista que recibe. Quien lo llama dos veces obtiene dos
        resultados distintos.
    */
    // Sorpresa: el nombre dice "calcular", pero también destruye la entrada.
    static BigDecimal totalAndClear(List<BigDecimal> prices) {
        BigDecimal total = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        prices.clear();                                     // efecto escondido
        return total;
    }

    // Sin sorpresas: calcula y nada más.
    static BigDecimal total(List<BigDecimal> prices) {
        return prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static boolean leastSurpriseExample() {
        List<BigDecimal> prices = new ArrayList<>(List.of(BigDecimal.ONE, BigDecimal.TEN));
        BigDecimal first = totalAndClear(prices);
        BigDecimal second = totalAndClear(prices);          // ¿por qué ahora da 0?

        List<BigDecimal> safe = new ArrayList<>(List.of(BigDecimal.ONE, BigDecimal.TEN));
        return first.compareTo(new BigDecimal("11")) == 0 && second.signum() == 0
                && total(safe).equals(total(safe)) && safe.size() == 2;
    }

    /*
        =================================================================
        CAPA 10) SEPARAR CONSULTAS DE COMANDOS (CQS)
        =================================================================

        Command-Query Separation (Bertrand Meyer): cada método debería ser
        UNA de dos cosas:
        - una CONSULTA (query): devuelve información y NO cambia nada. La
          puedes llamar cien veces y el sistema queda igual.
        - un COMANDO (command): cambia el estado y NO devuelve datos
          (devuelve void, o como mucho un indicador de éxito).

        Por qué ayuda:
        - Las consultas son SEGURAS: se pueden llamar para depurar, en un
          log, en una prueba, sin miedo a efectos.
        - Se sabe exactamente dónde cambia el estado: en los comandos.

        Excepciones aceptadas y conocidas: pop() de una pila o poll() de
        una cola (devuelven el elemento Y lo sacan;
        v5_collections/part3_queues/QueuesAndStacks), o un add que
        devuelve si agregó (Sets, CAPA 1). Son casos donde separarlos
        sería incómodo o inseguro con hilos. Fuera de esos casos clásicos,
        sepáralos.

        El ejemplo: un contador de visitas donde "consultar" las visitas
        también las incrementaba.
    */
    static final class VisitCounterMixed {
        private int visits;

        int visits() {
            return ++visits;                                // ¡consultar cambia el valor!
        }
    }

    static final class VisitCounter {
        private int visits;

        void registerVisit() {                              // comando: cambia, no devuelve
            visits++;
        }

        int visits() {                                      // consulta: devuelve, no cambia
            return visits;
        }
    }

    static boolean commandQueryExample() {
        VisitCounterMixed mixed = new VisitCounterMixed();
        boolean queryChangesState = mixed.visits() != mixed.visits();   // dos lecturas, dos valores

        VisitCounter counter = new VisitCounter();
        counter.registerVisit();
        counter.registerVisit();
        return queryChangesState && counter.visits() == 2 && counter.visits() == 2;
    }

    /*
        =================================================================
        CAPA 11) LOS PRINCIPIOS QUE YA CONOCES
        =================================================================

        Varios principios clásicos ya se explicaron a fondo con su tema.
        Aquí, dónde está cada uno y cómo se relaciona con cohesión y
        acoplamiento:

        | Principio                         | Dónde está                                         | Qué protege          |
        |-----------------------------------|----------------------------------------------------|----------------------|
        | Encapsulación / ocultar el estado | v2_oop/part2_pillars/Encapsulation                 | bajo acoplamiento    |
        | Tell, Don't Ask                   | Encapsulation, CAPA 5                              | cohesión             |
        | Ley de Deméter                    | v2_oop/part3_interfaces_and_relationships/         | bajo acoplamiento    |
        |                                   | CompositionAndAggregation, CAPA 8                  |                      |
        | Composición sobre herencia        | CompositionAndAggregation, CAPA 6;                 | bajo acoplamiento    |
        |                                   | v2_oop/part2_pillars/Inheritance, CAPA 11          |                      |
        | Programar contra abstracciones    | v2_oop/part2_pillars/Abstraction, CAPA 4           | bajo acoplamiento    |
        | Polimorfismo en vez de if por tipo| v2_oop/part2_pillars/Polymorphism, CAPAS 2 y 3     | cohesión, extensión  |
        | Fail fast                         | v3_exceptions/part2_in_practice/                   | errores cerca de     |
        |                                   | ErrorHandlingBestPractices, CAPA 3                 | su causa             |
        | Inmutabilidad                     | Encapsulation, CAPA 7; v5_collections/part4_tools/ | razonar fácil        |
        |                                   | ImmutableCollections                               |                      |

        Recordatorio rápido de fail fast, porque aparece en todos los
        diseños: valida al ENTRAR (constructores, métodos públicos) y
        lanza el error en ese momento, en lugar de dejar que un dato
        inválido viaje y explote lejos de donde se originó:
            this.email = Objects.requireNonNull(email, "email");
    */
    record Customer(String email) {
        Customer {
            Objects.requireNonNull(email, "email");         // fail fast: el objeto nunca existe inválido
        }
    }

    static boolean failFastExample() {
        try {
            new Customer(null);                             // falla AQUÍ, no lejos de aquí
            return false;
        } catch (NullPointerException e) {
            return e.getMessage().equals("email");
        }
    }

    /*
        =================================================================
        CAPA 12) CUANDO LOS PRINCIPIOS CHOCAN
        =================================================================

        Los principios no siempre apuntan al mismo lado:

        - DRY vs BAJO ACOPLAMIENTO: compartir código entre dos módulos
          elimina duplicación, pero los ATA: cambiar lo compartido afecta a
          los dos (CAPA 6). Entre sistemas o equipos distintos, muchas
          veces es mejor duplicar un poco.

        - YAGNI vs ABIERTO/CERRADO (part2_solid/OpenClosed): uno dice "no
          prepares extensiones que no necesitas"; el otro, "diseña para
          extender sin modificar". Se reconcilian así: aplica la
          extensibilidad DONDE YA SABES que hay variación (ya hay tres
          métodos de pago y vendrán más), no en todos lados.

        - KISS vs SEPARACIÓN: separar en capas agrega piezas. Para un
          script de 50 líneas, separarlo en cinco clases es más complejo,
          no menos.

        Cómo decidir:
        1) ¿Qué cambia JUNTO? Lo que cambia junto, va junto (cohesión). Lo
           que cambia por razones distintas, separado (bajo acoplamiento).
           Esta pregunta resuelve la mayoría de los choques.
        2) ¿Cuál es el cambio PROBABLE? Diseña para ese, no para todos.
        3) ¿Quién lo va a leer? Optimiza para que se entienda.
        4) Ante la duda, lo SIMPLE: es más fácil agregar estructura a un
           código simple que quitársela a uno sobrediseñado.
    */

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) Clases "Utils" o "Manager" donde termina todo lo que no se supo
           dónde poner (CAPA 2).

        2) Parámetros booleanos que cambian lo que hace un método
           (CAPA 3).

        3) Estado global mutable compartido (CAPA 3).

        4) Mezclar reglas de negocio con formato y guardado en el mismo
           método: imposible de probar por separado (CAPA 4).

        5) La misma regla escrita en varios lugares (CAPA 5).

        6) Unir código que solo se parece por casualidad (CAPA 6).

        7) Interfaces, fábricas y capas "por si acaso" (CAPAS 7 y 8).

        8) Métodos cuyo nombre no dice todo lo que hacen (CAPA 9).

        9) Consultas que cambian el estado (CAPA 10).

        10) Aplicar un principio a ciegas sin preguntar qué cambio protege
            (CAPA 12).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Diseñar es repartir el código para que los cambios probables sean
          baratos. Los principios son guías, no leyes.
        - ALTA COHESIÓN: cada pieza con un propósito. BAJO ACOPLAMIENTO:
          cada pieza sabe lo mínimo de las demás, por canales claros
          (parámetros, abstracciones).
        - Separa reglas de negocio, presentación y persistencia.
        - DRY: una sola fuente de verdad para cada CONOCIMIENTO; pero no
          unas lo que se parece por casualidad (regla de tres).
        - KISS: sin complejidad accidental. YAGNI: sin flexibilidad
          especulativa; prepárate siendo fácil de cambiar.
        - Menor sorpresa: el nombre dice todo lo que hace.
        - CQS: o consulta, o comando.
        - Cuando chocan: lo que cambia junto, va junto.
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
