package v10_design.part4_clean_code;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/*
    =====================================================================
    REFACTORIZAR: MEJORAR EL DISEÑO SIN CAMBIAR EL COMPORTAMIENTO
    =====================================================================

    Ruta de design/: guía 10 de 10  (part4_clean_code)
    Anterior:  part4_clean_code/CleanCode
    Siguiente: v11_patterns/part1_creational/FactoryMethod  (última de design/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Cada refactorización tiene su "antes" y su "después", y main
      comprueba con una PRUEBA DE CARACTERIZACIÓN (CAPA 3) que los dos
      dan exactamente los mismos resultados. Así se trabaja de verdad.

    Qué se da por sabido: los code smells y las técnicas de
    part4_clean_code/CleanCode (extraer funciones, cláusulas de guarda,
    parameter object, tipos de valor). Aquí se ve el PROCESO y el resto
    del catálogo.

    Índice:
        CAPA 1)  Qué es refactorizar (y qué no)
        CAPA 2)  Los dos sombreros: nunca refactorizar y agregar a la vez
        CAPA 3)  La red de seguridad: pruebas de caracterización
        CAPA 4)  Pasos pequeños, siempre en verde
        CAPA 5)  Reemplazar variable temporal con consulta
        CAPA 6)  Dividir un ciclo y pasarlo a un pipeline
        CAPA 7)  Consolidar y descomponer condicionales
        CAPA 8)  Mover función (feature envy)
        CAPA 9)  Reemplazar condicional con polimorfismo
        CAPA 10) Encapsular una colección
        CAPA 11) Reemplazar código de error con excepción
        CAPA 12) Cambios grandes: expandir y contraer
        CAPA 13) Cuándo refactorizar y cuándo no
        CAPA 14) Errores comunes
        RESUMEN FINAL
*/
public class Refactoring {

    public static void main(String[] args) {
        check(characterizationExample(), "CAPA 3");
        check(replaceTempWithQueryExample(), "CAPA 5");
        check(splitLoopExample(), "CAPA 6");
        check(conditionalsExample(), "CAPA 7");
        check(moveFunctionExample(), "CAPA 8");
        check(polymorphismExample(), "CAPA 9");
        check(encapsulateCollectionExample(), "CAPA 10");
        check(errorCodeExample(), "CAPA 11");
        check(expandContractExample(), "CAPA 12");
        System.out.println("Refactoring: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES REFACTORIZAR (Y QUÉ NO)
        =================================================================

        La definición de Martin Fowler (autor del libro "Refactoring", la
        referencia del tema):

            Refactorizar es cambiar la estructura INTERNA del software para
            hacerlo más fácil de entender y más barato de modificar, SIN
            CAMBIAR SU COMPORTAMIENTO OBSERVABLE.

        Tres palabras clave:
        - ESTRUCTURA: nombres, funciones, clases, dónde vive cada cosa.
        - SIN CAMBIAR EL COMPORTAMIENTO: para quien usa el código, antes y
          después es IDÉNTICO: mismas respuestas, mismos errores, mismos
          efectos. Si cambia algo de eso, no es refactorizar: es cambiar la
          funcionalidad (o meter un error).
        - Con un PROPÓSITO: que el siguiente cambio sea más fácil.

        Lo que NO es refactorizar:
        - Corregir un error (cambia el comportamiento, aunque sea para
          bien).
        - Agregar funcionalidad.
        - Optimizar rendimiento (puede empeorar la claridad; es otra
          actividad, con mediciones).
        - REESCRIBIR desde cero: eso es tirar código probado y apostar a no
          repetir sus errores. Refactorizar es avanzar en pasos pequeños
          sobre algo que siempre funciona.
    */

    /*
        =================================================================
        CAPA 2) LOS DOS SOMBREROS: NUNCA REFACTORIZAR Y AGREGAR A LA VEZ
        =================================================================

        Kent Beck (creador de la programación extrema y del desarrollo
        guiado por pruebas) describe dos "sombreros" que te pones al
        programar:
        - Sombrero de AGREGAR funcionalidad: el comportamiento cambia;
          agregas pruebas nuevas.
        - Sombrero de REFACTORIZAR: el comportamiento NO cambia; las pruebas
          siguen igual y siguen pasando.

        Nunca los dos a la vez. Si mezclas, cuando algo falla no sabes si
        fue la funcionalidad nueva o la reestructuración, y la revisión de
        código se vuelve imposible (¿este cambio es intencional o
        accidental?).

        En la práctica se alternan rápido, y Beck lo resume así:
            "Primero haz que el cambio sea fácil (esto puede ser difícil),
             y luego haz el cambio fácil."
        Es decir: antes de agregar algo, refactoriza para prepararle un
        lugar (sombrero 1); luego agrégalo (sombrero 2). Y en el control de
        versiones, en cambios (commits) separados (tools/).
    */

    /*
        =================================================================
        CAPA 3) LA RED DE SEGURIDAD: PRUEBAS DE CARACTERIZACIÓN
        =================================================================

        ¿Cómo sabes que no cambiaste el comportamiento? Con PRUEBAS. Sin
        ellas, refactorizar es adivinar.

        El problema típico: el código que más necesita refactorización es
        el que NO tiene pruebas, y nadie sabe exactamente qué hace. La
        técnica para ese caso son las PRUEBAS DE CARACTERIZACIÓN (Michael
        Feathers, "Working Effectively with Legacy Code"):

        1) Ejecuta el código ACTUAL con muchas entradas (normales, límites,
           raras) y GUARDA lo que devuelve, sea lo que sea. No juzgas si es
           correcto: describes lo que HACE hoy.
        2) Refactoriza.
        3) Ejecuta las mismas entradas: los resultados deben ser IGUALES.

        Si el código viejo tiene un error, la prueba de caracterización lo
        conserva a propósito. Arreglarlo es otra tarea, con el otro
        sombrero (CAPA 2).

        El método sameBehavior de abajo es exactamente eso, y se usa en
        todas las capas de esta guía. (Con JUnit se hace igual, con más
        comodidades; testing/.)
    */
    static <I, O> boolean sameBehavior(Function<I, O> before, Function<I, O> after, List<I> inputs) {
        for (I input : inputs) {
            Object expected = outcomeOf(before, input);
            Object actual = outcomeOf(after, input);
            if (!expected.equals(actual)) {
                return false;
            }
        }
        return true;
    }

    /** El resultado, o la excepción lanzada: una excepción también es comportamiento observable. */
    private static <I, O> Object outcomeOf(Function<I, O> function, I input) {
        try {
            return String.valueOf(function.apply(input));
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // Código heredado, sin pruebas: ¿qué hace exactamente con 0? ¿con negativos?
    static String legacyShippingLabel(int grams) {
        String r = "";
        if (grams > 0) {
            if (grams < 1000) {
                r = "chico";
            } else {
                if (grams < 10000) {
                    r = "mediano";
                } else {
                    r = "grande";
                }
            }
        } else {
            r = "chico";                                    // ¿error o intención? La caracterización lo conserva
        }
        return r;
    }

    static String shippingLabel(int grams) {
        if (grams < 1_000) {
            return "chico";
        }
        if (grams < 10_000) {
            return "mediano";
        }
        return "grande";
    }

    // Una "refactorización" que, sin querer, cambió el caso del 0 (parecía "más lógico").
    static String shippingLabelChangedBehavior(int grams) {
        if (grams <= 0) {
            return "sin peso";
        }
        return shippingLabel(grams);
    }

    static boolean characterizationExample() {
        List<Integer> inputs = List.of(-5, 0, 1, 999, 1_000, 9_999, 10_000, 250_000);   // incluye límites y raros
        return sameBehavior(Refactoring::legacyShippingLabel, Refactoring::shippingLabel, inputs)
                && !sameBehavior(Refactoring::legacyShippingLabel, Refactoring::shippingLabelChangedBehavior, inputs);   // la red lo atrapa
    }

    /*
        =================================================================
        CAPA 4) PASOS PEQUEÑOS, SIEMPRE EN VERDE
        =================================================================

        La forma de refactorizar sin miedo es en pasos MUY pequeños, y que
        después de CADA paso todo compile y las pruebas pasen ("en verde"):

            cambio pequeño -> compilar -> correr las pruebas -> (guardar) -> repetir

        Si algo falla, sabes exactamente qué lo rompió (el último paso, de
        unos segundos) y lo deshaces. Un cambio grande de dos horas que
        falla no te dice nada.

        HERRAMIENTAS: los IDE (IntelliJ, VS Code, Eclipse) traen
        refactorizaciones AUTOMÁTICAS: renombrar, extraer método o
        variable, cambiar la firma de un método, mover. Son seguras porque
        entienden el código (actualizan todas las llamadas y respetan el
        alcance), cosa que un "buscar y reemplazar" de texto no hace. Úsalas
        siempre que existan: renombrar a mano en 40 archivos es la forma más
        común de romper algo.

        Cada refactorización del catálogo de Fowler viene con sus pasos
        mecánicos. Las capas siguientes muestran los más usados que no se
        vieron en CleanCode.
    */

    /*
        =================================================================
        CAPA 5) REEMPLAZAR VARIABLE TEMPORAL CON CONSULTA
        =================================================================

        Una variable local que guarda un cálculo solo es visible en ese
        método. Si el mismo cálculo hace falta en otro método, se duplica.
        Convertirla en un MÉTODO (una "consulta") la hace reutilizable y le
        da un nombre que forma parte de la clase.

        Pasos:
        1) Verifica que la variable se asigna UNA sola vez.
        2) Extrae su expresión a un método con buen nombre.
        3) Reemplaza los usos de la variable por llamadas al método.
        4) Elimina la variable.

        (Si el cálculo es muy costoso y se usa muchas veces, una variable
        sigue siendo razonable. Primero claridad; rendimiento, con
        mediciones.)
    */
    record OrderLine(int quantity, BigDecimal unitPrice) { }

    // ANTES
    static BigDecimal finalPriceBefore(OrderLine line) {
        BigDecimal basePrice = line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()));
        BigDecimal discount = basePrice.compareTo(new BigDecimal("1000")) > 0
                ? basePrice.multiply(new BigDecimal("0.05")) : BigDecimal.ZERO;
        return basePrice.subtract(discount).setScale(2, RoundingMode.HALF_UP);
    }

    // DESPUÉS
    static BigDecimal finalPrice(OrderLine line) {
        return basePrice(line).subtract(discount(line)).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal basePrice(OrderLine line) {           // ahora cualquier método puede preguntarlo
        return line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()));
    }

    static BigDecimal discount(OrderLine line) {
        return basePrice(line).compareTo(new BigDecimal("1000")) > 0
                ? basePrice(line).multiply(new BigDecimal("0.05")) : BigDecimal.ZERO;
    }

    static boolean replaceTempWithQueryExample() {
        List<OrderLine> inputs = List.of(new OrderLine(1, new BigDecimal("100")), new OrderLine(3, new BigDecimal("400")),
                new OrderLine(10, new BigDecimal("100")), new OrderLine(0, new BigDecimal("999")));
        return sameBehavior(Refactoring::finalPriceBefore, Refactoring::finalPrice, inputs);
    }

    /*
        =================================================================
        CAPA 6) DIVIDIR UN CICLO Y PASARLO A UN PIPELINE
        =================================================================

        Un ciclo que calcula DOS cosas a la vez (el total y el más caro)
        mezcla dos responsabilidades: para cambiar una hay que entender las
        dos.

        DIVIDIR EL CICLO (Split Loop): uno por cada cálculo. Parece
        ineficiente (recorre dos veces), pero casi nunca importa, y si
        importa se mide (v12_jvm/). Una vez separados, cada ciclo:
        - se puede extraer a su propio método con nombre;
        - y muchas veces se puede reemplazar por un PIPELINE de streams
          (Replace Loop with Pipeline; v7_functional/part2_streams/
          StreamsBasics), que se lee como la descripción del cálculo.
    */
    record Product(String name, BigDecimal price) { }

    record Summary(BigDecimal total, String mostExpensive) { }

    // ANTES: un ciclo, dos trabajos.
    static Summary summarizeBefore(List<Product> products) {
        BigDecimal total = BigDecimal.ZERO;
        Product mostExpensive = null;
        for (Product product : products) {
            total = total.add(product.price());
            if (mostExpensive == null || product.price().compareTo(mostExpensive.price()) > 0) {
                mostExpensive = product;
            }
        }
        return new Summary(total, mostExpensive == null ? "ninguno" : mostExpensive.name());
    }

    // DESPUÉS: cada cálculo por separado, como pipeline con nombre.
    static Summary summarize(List<Product> products) {
        return new Summary(totalPrice(products), mostExpensiveName(products));
    }

    static BigDecimal totalPrice(List<Product> products) {
        return products.stream().map(Product::price).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String mostExpensiveName(List<Product> products) {
        return products.stream()
                .reduce((best, candidate) -> candidate.price().compareTo(best.price()) > 0 ? candidate : best)
                .map(Product::name)
                .orElse("ninguno");
    }

    static boolean splitLoopExample() {
        List<List<Product>> inputs = List.of(
                List.of(),
                List.of(new Product("cable", new BigDecimal("80"))),
                List.of(new Product("teclado", new BigDecimal("500")), new Product("monitor", new BigDecimal("3000")),
                        new Product("mouse", new BigDecimal("3000"))));     // empate: el PRIMERO, en las dos versiones
        return sameBehavior(Refactoring::summarizeBefore, Refactoring::summarize, inputs);
    }

    /*
        =================================================================
        CAPA 7) CONSOLIDAR Y DESCOMPONER CONDICIONALES
        =================================================================

        Dos refactorizaciones que suelen ir juntas:

        1) CONSOLIDAR (Consolidate Conditional Expression): varios if
           seguidos que dan el MISMO resultado son en realidad UNA regla con
           varias partes. Únelos con || y extrae la condición con nombre.

        2) DESCOMPONER (Decompose Conditional): una condición complicada
           con ramas largas se divide en métodos: uno para la condición y
           uno para cada rama (CleanCode, CAPA 7, hizo la parte de la
           condición).

        El ejemplo: cuatro if que devuelven 0 son la regla "no aplica
        seguro" escrita en pedazos.
    */
    record Shipment(boolean international, boolean fragile, BigDecimal declaredValue, String carrier) { }

    // ANTES: la misma respuesta repartida en cuatro if.
    static BigDecimal insuranceBefore(Shipment shipment) {
        if (shipment.international()) {
            return BigDecimal.ZERO;
        }
        if (shipment.declaredValue().compareTo(new BigDecimal("500")) < 0) {
            return BigDecimal.ZERO;
        }
        if (shipment.carrier().equals("LOCAL")) {
            return BigDecimal.ZERO;
        }
        if (!shipment.fragile() && shipment.declaredValue().compareTo(new BigDecimal("5000")) < 0) {
            return BigDecimal.ZERO;
        }
        return shipment.declaredValue().multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
    }

    // DESPUÉS: una regla con nombre, y el cálculo aparte.
    static BigDecimal insurance(Shipment shipment) {
        if (isNotInsurable(shipment)) {
            return BigDecimal.ZERO;
        }
        return insurancePremium(shipment);
    }

    static boolean isNotInsurable(Shipment shipment) {
        return shipment.international()
                || shipment.declaredValue().compareTo(new BigDecimal("500")) < 0
                || shipment.carrier().equals("LOCAL")
                || isLowRisk(shipment);
    }

    static boolean isLowRisk(Shipment shipment) {
        return !shipment.fragile() && shipment.declaredValue().compareTo(new BigDecimal("5000")) < 0;
    }

    static BigDecimal insurancePremium(Shipment shipment) {
        return shipment.declaredValue().multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
    }

    static boolean conditionalsExample() {
        List<Shipment> inputs = new ArrayList<>();
        for (boolean international : new boolean[] {true, false}) {
            for (boolean fragile : new boolean[] {true, false}) {
                for (String value : List.of("100", "1000", "8000")) {
                    for (String carrier : List.of("LOCAL", "DHL")) {
                        inputs.add(new Shipment(international, fragile, new BigDecimal(value), carrier));   // 24 combinaciones
                    }
                }
            }
        }
        return sameBehavior(Refactoring::insuranceBefore, Refactoring::insurance, inputs);
    }

    /*
        =================================================================
        CAPA 8) MOVER FUNCIÓN (FEATURE ENVY)
        =================================================================

        Un método que usa más los datos de OTRA clase que los de la suya
        tiene "envidia" (feature envy; CleanCode, CAPA 12). Según
        Information Expert (part3_responsibilities/GRASP, CAPA 2), debería
        vivir en la clase que tiene esos datos.

        Pasos:
        1) Copia el método a la clase destino, ajustándolo para usar sus
           propios campos.
        2) Haz que el método original DELEGUE en el nuevo (todo sigue
           funcionando; pruebas en verde).
        3) Cambia las llamadas para que usen el nuevo directamente.
        4) Elimina el original.

        El ejemplo: el pedido calculaba el descuento del cliente leyendo
        todos sus datos. Se mueve a Customer.
    */
    record Customer(String tier, int yearsAsCustomer) {
        BigDecimal discountRate() {                         // DESPUÉS: vive con los datos que usa
            BigDecimal tierRate = tier.equals("GOLD") ? new BigDecimal("0.10") : new BigDecimal("0.02");
            return yearsAsCustomer >= 5 ? tierRate.add(new BigDecimal("0.03")) : tierRate;
        }
    }

    record Order(Customer customer, BigDecimal subtotal) {
        // ANTES: el pedido "envidia" al cliente: todo lo que lee es del cliente.
        BigDecimal discountRateEnvious() {
            BigDecimal tierRate = customer.tier().equals("GOLD") ? new BigDecimal("0.10") : new BigDecimal("0.02");
            return customer.yearsAsCustomer() >= 5 ? tierRate.add(new BigDecimal("0.03")) : tierRate;
        }

        BigDecimal total() {
            return subtotal.subtract(subtotal.multiply(customer.discountRate()));   // le pregunta al experto
        }
    }

    static boolean moveFunctionExample() {
        List<Order> inputs = List.of(
                new Order(new Customer("GOLD", 6), new BigDecimal("1000")),
                new Order(new Customer("GOLD", 1), new BigDecimal("1000")),
                new Order(new Customer("BASIC", 7), new BigDecimal("1000")));
        return sameBehavior(Order::discountRateEnvious, order -> order.customer().discountRate(), inputs)
                && inputs.get(0).total().compareTo(new BigDecimal("870")) == 0;
    }

    /*
        =================================================================
        CAPA 9) REEMPLAZAR CONDICIONAL CON POLIMORFISMO
        =================================================================

        El cambio que lleva del "switch por tipo" al diseño de
        part2_solid/OpenClosed, CAPA 4. Se hace por pasos, sin romper nada:

        1) Crea la interface con el método que hoy decide el switch.
        2) Crea una clase por cada rama, copiando el código de esa rama.
        3) Crea UN lugar que elige la clase según el tipo (un registro o un
           mapa; OpenClosed, CAPA 5).
        4) Haz que el método con el switch delegue en el objeto elegido.
           Pruebas en verde.
        5) Cambia los demás switch sobre el mismo tipo para usar el objeto.
        6) Elimina los switch.

        Ojo: solo vale la pena si el switch se repite o las variantes
        crecen (OpenClosed, CAPA 11). Si es un switch pequeño en un solo
        lugar, déjalo.
    */
    // ANTES: el switch por tipo.
    static BigDecimal shippingCostBefore(String type, int grams) {
        return switch (type) {
            case "STANDARD" -> new BigDecimal("99");
            case "EXPRESS" -> new BigDecimal("99").add(BigDecimal.valueOf(grams / 1000 * 20L));
            case "PICKUP" -> BigDecimal.ZERO;
            default -> throw new IllegalArgumentException("Tipo de envío desconocido: " + type);
        };
    }

    // DESPUÉS: una clase por rama (aquí lambdas, por ser cortas) y UN lugar que elige.
    interface ShippingMethod {
        BigDecimal cost(int grams);
    }

    static final Map<String, ShippingMethod> SHIPPING_METHODS = Map.of(
            "STANDARD", grams -> new BigDecimal("99"),
            "EXPRESS", grams -> new BigDecimal("99").add(BigDecimal.valueOf(grams / 1000 * 20L)),
            "PICKUP", grams -> BigDecimal.ZERO);

    static BigDecimal shippingCost(String type, int grams) {
        ShippingMethod method = SHIPPING_METHODS.get(type);
        if (method == null) {
            throw new IllegalArgumentException("Tipo de envío desconocido: " + type);   // mismo error: mismo comportamiento
        }
        return method.cost(grams);
    }

    static boolean polymorphismExample() {
        List<String> inputs = List.of("STANDARD:500", "EXPRESS:3500", "PICKUP:9000", "DRONE:100");
        return sameBehavior(
                (String input) -> shippingCostBefore(input.split(":")[0], Integer.parseInt(input.split(":")[1])),
                (String input) -> shippingCost(input.split(":")[0], Integer.parseInt(input.split(":")[1])),
                inputs);
    }

    /*
        =================================================================
        CAPA 10) ENCAPSULAR UNA COLECCIÓN
        =================================================================

        Un getter que devuelve la lista INTERNA entrega la llave: cualquiera
        puede agregar, quitar o vaciar, saltándose las reglas de la clase
        (v2_oop/part2_pillars/Encapsulation, CAPA 8).

        Pasos:
        1) Agrega métodos para modificar con reglas: addTag, removeTag.
        2) Cambia el código externo que modificaba la lista para usarlos.
        3) Haz que el getter devuelva una vista de solo lectura (o una
           copia; v5_collections/part4_tools/ImmutableCollections, CAPA 7).

        Ojo: el paso 3 SÍ cambia algo observable (antes se podía modificar
        desde afuera, ahora lanza excepción). Por eso los pasos 1 y 2 van
        primero: cuando llega el 3, ya nadie modifica desde afuera, y para
        el resto del sistema el comportamiento es el mismo.
    */
    static final class ArticleBefore {
        private final List<String> tags = new ArrayList<>();

        List<String> getTags() {
            return tags;                                    // la lista interna, sin protección
        }
    }

    static final class Article {
        private static final int MAX_TAGS = 3;
        private final List<String> tags = new ArrayList<>();

        void addTag(String tag) {                           // las reglas viven aquí
            String normalized = tag.strip().toLowerCase();
            if (!tags.contains(normalized) && tags.size() < MAX_TAGS) {
                tags.add(normalized);
            }
        }

        List<String> tags() {
            return Collections.unmodifiableList(tags);
        }
    }

    static boolean encapsulateCollectionExample() {
        ArticleBefore before = new ArticleBefore();
        before.getTags().add("Java");
        before.getTags().add("Java");                       // duplicado: nadie lo impide
        before.getTags().clear();                           // ni esto

        Article after = new Article();
        for (String tag : List.of("Java", " java ", "Spring", "SQL", "Docker")) {
            after.addTag(tag);
        }
        boolean protectedFromOutside;
        try {
            after.tags().clear();
            protectedFromOutside = false;
        } catch (UnsupportedOperationException e) {
            protectedFromOutside = true;
        }
        return before.getTags().isEmpty()
                && after.tags().equals(List.of("java", "spring", "sql"))   // normalizado, sin repetidos, máximo 3
                && protectedFromOutside;
    }

    /*
        =================================================================
        CAPA 11) REEMPLAZAR CÓDIGO DE ERROR CON EXCEPCIÓN
        =================================================================

        Código viejo que avisa errores devolviendo valores especiales (-1,
        null, "ERROR") obliga a cada llamada a revisarlos, y si una lo
        olvida, el valor especial viaja como si fuera un dato
        (v3_exceptions/part1_concepts/WhatAreExceptions, CAPA 1).

        Pasos:
        1) Crea el método nuevo que LANZA la excepción en vez de devolver
           el código.
        2) Haz que el viejo delegue en el nuevo y convierta la excepción en
           el código (las llamadas viejas siguen funcionando igual).
        3) Migra las llamadas una por una al nuevo, manejando la excepción
           donde corresponde.
        4) Elimina el viejo.

        Es otro caso del patrón de la CAPA 12: el viejo y el nuevo conviven
        mientras se migra. (Si "no encontrado" es normal, el reemplazo es
        Optional en vez de excepción; v7_functional/part2_streams/
        OptionalUsage.)
    */
    static final Map<String, Integer> STOCK = Map.of("KBD-1", 5, "MOU-1", 0);

    // El método NUEVO: el error es una excepción con información.
    static int stockOf(String sku) {
        Integer units = STOCK.get(sku);
        if (units == null) {
            throw new IllegalArgumentException("SKU desconocido: " + sku);
        }
        return units;
    }

    // El VIEJO, ahora delegando: las llamadas viejas siguen recibiendo -1, mientras se migran.
    static int stockOfOrMinusOne(String sku) {
        try {
            return stockOf(sku);
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    // Así era el viejo antes de delegar.
    static int legacyStockOf(String sku) {
        Integer units = STOCK.get(sku);
        return units == null ? -1 : units;
    }

    static boolean errorCodeExample() {
        List<String> inputs = List.of("KBD-1", "MOU-1", "XXX-9");
        boolean oldCallersUnaffected = sameBehavior(Refactoring::legacyStockOf, Refactoring::stockOfOrMinusOne, inputs);
        boolean newOneThrows;
        try {
            stockOf("XXX-9");
            newOneThrows = false;
        } catch (IllegalArgumentException e) {
            newOneThrows = e.getMessage().equals("SKU desconocido: XXX-9");
        }
        return oldCallersUnaffected && newOneThrows;
    }

    /*
        =================================================================
        CAPA 12) CAMBIOS GRANDES: EXPANDIR Y CONTRAER
        =================================================================

        ¿Cómo cambias la firma de un método que se llama desde 200 lugares,
        o que usan otros equipos? Si cambias todo de golpe, el sistema queda
        roto hasta que terminas. La técnica es EXPANDIR Y CONTRAER
        (también llamada "cambio en paralelo"):

        1) EXPANDIR: agrega la versión NUEVA junto a la vieja. La vieja
           delega en la nueva. Nada se rompe.
        2) MIGRAR: cambia las llamadas a la nueva, de a poco (en varios
           cambios, incluso en varios días). Marca la vieja como
           @Deprecated para que el compilador avise a quien todavía la usa.
        3) CONTRAER: cuando nadie usa la vieja, bórrala.

        En cada momento el sistema funciona. Es como se cambian las APIs
        públicas (el JDK mismo marca métodos @Deprecated años antes de
        quitarlos), las columnas de una base de datos y los formatos de los
        mensajes entre servicios.

        Para reemplazar un sistema COMPLETO se usa la misma idea a gran
        escala: el patrón "strangler fig" (higuera estranguladora): el
        sistema nuevo crece alrededor del viejo, toma funcionalidades una a
        una, hasta que el viejo se puede apagar (v14_architecture/).

        El ejemplo: el precio pasaba como double (mala idea para dinero;
        v1_fundamentals/part4_data_and_input/Numbers) y se migra a
        BigDecimal.
    */
    /** Versión vieja: el dinero como double. Se conserva mientras se migran las llamadas. */
    @Deprecated(since = "2.0", forRemoval = true)
    static String priceTag(double price) {
        return priceTag(BigDecimal.valueOf(price));        // 1) EXPANDIR: la vieja delega en la nueva
    }

    static String priceTag(BigDecimal price) {             // la versión nueva
        return "$" + price.setScale(2, RoundingMode.HALF_UP);
    }

    static boolean expandContractExample() {
        @SuppressWarnings("removal")                        // a propósito: una llamada que todavía no se migra
        String oldCall = priceTag(19.9);
        String newCall = priceTag(new BigDecimal("19.9"));
        return oldCall.equals(newCall) && newCall.equals("$19.90");
    }

    /*
        =================================================================
        CAPA 13) CUÁNDO REFACTORIZAR Y CUÁNDO NO
        =================================================================

        SÍ, y lo más común es refactorizar como parte del trabajo diario,
        no en "proyectos de refactorización":
        - PREPARATORIA: antes de agregar algo, para que quepa fácil
          (CAPA 2: "haz que el cambio sea fácil").
        - DE COMPRENSIÓN: mientras lees código para entenderlo, renombra y
          extrae lo que te costó descifrar; el siguiente no pagará ese
          costo.
        - A LA TERCERA: cuando algo se repite por tercera vez
          (part1_basics/BasicPrinciples, CAPA 6).
        - EN LA REVISIÓN DE CÓDIGO: cuando alguien no entiende algo, es la
          señal.
        - La regla del Boy Scout (CleanCode, CAPA 1).

        NO, o con mucho cuidado:
        - Sin pruebas y sin posibilidad de escribirlas: primero las pruebas
          de caracterización (CAPA 3).
        - Código que funciona, nadie toca y no vas a cambiar: limpiarlo no
          aporta, solo arriesga.
        - Código que se va a tirar o reemplazar pronto.
        - Mezclado con una corrección urgente en producción: arregla
          primero, mínimo y claro; refactoriza después, aparte.
        - La tentación de REESCRIBIR todo desde cero "porque es más
          rápido": casi nunca lo es, porque el código viejo contiene años de
          casos especiales que nadie recuerda (CAPA 1).
    */

    /*
        =================================================================
        CAPA 14) ERRORES COMUNES
        =================================================================

        1) Llamar "refactorizar" a cambios que modifican el comportamiento
           (CAPA 1).

        2) Refactorizar y agregar funcionalidad en el mismo cambio
           (CAPA 2).

        3) Refactorizar sin pruebas, "a ojo" (CAPA 3).

        4) Pasos grandes que dejan el código roto por horas (CAPA 4).

        5) Renombrar o mover a mano con buscar y reemplazar, en vez de con
           las herramientas del IDE (CAPA 4).

        6) Olvidar que una excepción también es comportamiento observable:
           el "después" debe lanzar lo mismo que el "antes" (CAPAS 3 y 9).

        7) Cambiar una firma usada en muchos lugares de golpe, en vez de
           expandir y contraer (CAPA 12).

        8) Reescribir desde cero en vez de refactorizar por pasos
           (CAPA 13).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Refactorizar: mejorar la estructura sin cambiar el comportamiento
          observable, para que el siguiente cambio sea fácil.
        - Dos sombreros: o refactorizas o agregas; nunca a la vez. "Haz
          fácil el cambio, luego haz el cambio fácil."
        - Red de seguridad: pruebas; en código heredado, pruebas de
          caracterización (incluidas las excepciones).
        - Pasos pequeños, siempre en verde, con las herramientas del IDE.
        - Catálogo: variable temporal -> consulta; dividir ciclos y pasar a
          pipelines; consolidar y descomponer condicionales; mover función;
          condicional -> polimorfismo; encapsular colecciones; código de
          error -> excepción; y los de CleanCode.
        - Cambios grandes: expandir, migrar, contraer (@Deprecated).
        - Refactoriza como parte del trabajo diario; no reescribas desde
          cero.
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
