package v10_design.part4_clean_code;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/*
    =====================================================================
    CÓDIGO LIMPIO: NOMBRES, FUNCIONES, COMENTARIOS Y CODE SMELLS
    =====================================================================

    Ruta de design/: guía 9 de 10  (part4_clean_code)
    Anterior:  part3_responsibilities/GRASP
    Siguiente: part4_clean_code/Refactoring

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Muchos ejemplos tienen una versión "antes" y una "después" que dan
      EXACTAMENTE el mismo resultado (main lo comprueba). La diferencia no
      está en lo que hacen, sino en lo que cuesta entenderlas.

    Índice:
        CAPA 1)  Qué es código limpio y por qué importa
        CAPA 2)  Nombres que revelan la intención
        CAPA 3)  Números y textos mágicos
        CAPA 4)  Funciones: pequeñas, una cosa, un nivel
        CAPA 5)  Cláusulas de guarda: aplanar los if anidados
        CAPA 6)  Parámetros: pocos y con sentido
        CAPA 7)  Condiciones con nombre
        CAPA 8)  Comentarios: el porqué, no el qué
        CAPA 9)  Formato: consistencia antes que gustos
        CAPA 10) Errores limpios
        CAPA 11) Obsesión por los primitivos: tipos con significado
        CAPA 12) Catálogo de code smells
        CAPA 13) Los límites del "código limpio"
        CAPA 14) Errores comunes
        RESUMEN FINAL
*/
public class CleanCode {

    public static void main(String[] args) {
        check(namesExample(), "CAPA 2");
        check(magicNumbersExample(), "CAPA 3");
        check(functionsExample(), "CAPA 4");
        check(guardClausesExample(), "CAPA 5");
        check(parameterObjectExample(), "CAPA 6");
        check(namedConditionsExample(), "CAPA 7");
        check(explanatoryVariablesExample(), "CAPA 8");
        check(valueObjectsExample(), "CAPA 11");
        System.out.println("CleanCode: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES CÓDIGO LIMPIO Y POR QUÉ IMPORTA
        =================================================================

        CÓDIGO LIMPIO es código que otra persona (o tú dentro de seis
        meses) puede LEER, ENTENDER y CAMBIAR con confianza y rápido.

        No es código "bonito" ni "ingenioso". Es código con nombres que
        dicen lo que hace, funciones que se entienden de una leída, sin
        sorpresas, y donde un cambio toca pocos lugares (lo que vimos en
        part1_basics y part2_solid, ahora a nivel de líneas).

        Por qué importa tanto:
        - El código se LEE muchas más veces de las que se escribe: cada
          corrección, cada funcionalidad nueva y cada revisión empieza por
          leer y entender lo que ya existe. Robert C. Martin (autor del libro
          "Clean Code", que dio nombre al tema) estima que el TIEMPO que se
          pasa leyendo código supera más de 10 a 1 al que se pasa
          escribiéndolo.
        - El desorden se ACUMULA: cada atajo hace el siguiente cambio un
          poco más lento, hasta que el equipo pasa más tiempo descifrando
          que construyendo. A esa carga se le llama DEUDA TÉCNICA.

        La "regla del Boy Scout": deja el código un poco más limpio de lo
        que lo encontraste. Un nombre mejor, una función extraída, en cada
        cambio que haces. Sin grandes reescrituras.
    */

    /*
        =================================================================
        CAPA 2) NOMBRES QUE REVELAN LA INTENCIÓN
        =================================================================

        El nombre es la documentación que SIEMPRE se lee. Un buen nombre
        responde: qué es, para qué existe y cómo se usa, sin necesidad de un
        comentario.

        Reglas prácticas:
        - Que diga la INTENCIÓN: d -> elapsedDays; list1 -> overdueInvoices.
        - Sin DESINFORMACIÓN: no llames accountList a algo que es un Set,
          ni getTotal a algo que además guarda en la base de datos.
        - CLASES con sustantivos (Invoice, PaymentProcessor); MÉTODOS con
          verbos (send, calculateTotal); BOOLEANOS que se lean como pregunta
          (isOverdue, hasStock, canRefund).
        - Un VOCABULARIO consistente: si en un lado es "get", que no sea
          "fetch" en otro y "retrieve" en otro para lo mismo.
        - Sin codificar tipos ni prefijos: strName, iCount, IOrderService.
          El tipo ya lo dice el compilador.
        - Largo proporcional al ALCANCE: una variable de un ciclo de dos
          líneas puede ser i; un campo que vive en toda la clase necesita
          un nombre completo.
        - Que se pueda BUSCAR: "MAX_LOGIN_ATTEMPTS" se encuentra en el
          proyecto; el número 5 aparece en mil lugares (CAPA 3).

        El mismo código, antes y después. Hace lo mismo; solo cambió lo que
        cuesta entenderlo.
    */
    record Inv(LocalDate d, BigDecimal a, boolean p) { }   // ¿qué es d? ¿a? ¿p?

    // ANTES: hay que descifrarlo.
    static List<Inv> get(List<Inv> l, LocalDate x) {
        return l.stream().filter(i -> !i.p() && ChronoUnit.DAYS.between(i.d(), x) > 30).toList();
    }

    record Invoice(LocalDate issuedOn, BigDecimal amount, boolean paid) { }

    // DESPUÉS: se lee como una frase.
    static List<Invoice> overdueInvoices(List<Invoice> invoices, LocalDate today) {
        return invoices.stream()
                .filter(invoice -> !invoice.paid() && daysSinceIssued(invoice, today) > PAYMENT_TERM_DAYS)
                .toList();
    }

    static final int PAYMENT_TERM_DAYS = 30;

    static long daysSinceIssued(Invoice invoice, LocalDate today) {
        return ChronoUnit.DAYS.between(invoice.issuedOn(), today);
    }

    static boolean namesExample() {
        LocalDate today = LocalDate.of(2024, 6, 1);
        List<Inv> cryptic = List.of(new Inv(LocalDate.of(2024, 4, 1), BigDecimal.TEN, false),
                new Inv(LocalDate.of(2024, 5, 25), BigDecimal.TEN, false));
        List<Invoice> clear = List.of(new Invoice(LocalDate.of(2024, 4, 1), BigDecimal.TEN, false),
                new Invoice(LocalDate.of(2024, 5, 25), BigDecimal.TEN, false));
        return get(cryptic, today).size() == overdueInvoices(clear, today).size()
                && overdueInvoices(clear, today).size() == 1;
    }

    /*
        =================================================================
        CAPA 3) NÚMEROS Y TEXTOS MÁGICOS
        =================================================================

        Un NÚMERO MÁGICO es un valor literal en medio del código cuyo
        significado no se ve: if (status == 3), price * 0.16, if (attempts
        > 5). ¿Qué es 3? ¿Por qué 0.16? Además, si el mismo valor aparece
        en varios lugares, cambiarlo es buscar a mano (y olvidar uno;
        part1_basics/BasicPrinciples, CAPA 5).

        Solución: una CONSTANTE con nombre (v2_oop/part1_basics/Static,
        CAPA 3), o un ENUM cuando son estados u opciones
        (v2_oop/part4_advanced_types/Enums). Lo mismo con los TEXTOS
        mágicos: "ADMIN", "PENDIENTE" escritos a mano en varios lugares son
        un error de tipeo esperando a pasar.

        No todo literal es mágico: 0, 1, una cadena vacía, o un 100 en
        "porcentaje / 100" se entienden solos.
    */
    // ANTES
    static boolean canLogInMagic(int failedAttempts, int status) {
        return failedAttempts < 5 && status != 3;
    }

    // DESPUÉS
    static final int MAX_FAILED_ATTEMPTS = 5;

    enum AccountStatus { ACTIVE, SUSPENDED, BLOCKED }

    static boolean canLogIn(int failedAttempts, AccountStatus status) {
        return failedAttempts < MAX_FAILED_ATTEMPTS && status != AccountStatus.BLOCKED;
    }

    static boolean magicNumbersExample() {
        return canLogInMagic(2, 1) == canLogIn(2, AccountStatus.SUSPENDED)
                && canLogInMagic(2, 3) == canLogIn(2, AccountStatus.BLOCKED)
                && !canLogIn(5, AccountStatus.ACTIVE);
    }

    /*
        =================================================================
        CAPA 4) FUNCIONES: PEQUEÑAS, UNA COSA, UN NIVEL
        =================================================================

        Una función limpia:
        1) Hace UNA cosa (part2_solid/SingleResponsibility, CAPA 9).
        2) A UN SOLO NIVEL DE DETALLE: no mezcla "procesar el pedido" con
           "sumar centavos en un ciclo". Las ideas de alto nivel llaman a
           funciones con nombre que esconden el detalle.
        3) Es CORTA: que quepa en la pantalla sin desplazarte, y mejor si es
           mucho menos. No hay un número mágico; si tienes que dejar de
           leer para entender, es larga.
        4) Se lee DE ARRIBA HACIA ABAJO: la función principal primero, y
           debajo las que usa, como un artículo de periódico que va del
           titular a los detalles ("step-down rule").
        5) No tiene efectos escondidos: hace lo que su nombre dice, y nada
           más (BasicPrinciples, CAPA 9).

        El ejemplo: un resumen de pedido escrito todo en un método, y el
        mismo dividido. El método principal ahora se lee como la lista de
        pasos, y cada paso se puede entender, probar y cambiar solo.
    */
    record Item(String name, int quantity, BigDecimal unitPrice) { }

    // ANTES: todo mezclado, todos los niveles a la vez.
    static String orderSummaryLong(String customer, List<Item> items) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Item item : items) {
            subtotal = subtotal.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        BigDecimal discount = BigDecimal.ZERO;
        if (subtotal.compareTo(new BigDecimal("1000")) >= 0) {
            discount = subtotal.multiply(new BigDecimal("0.05"));
        }
        BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        return customer.trim().toUpperCase() + " | " + items.size() + " artículos | $" + total;
    }

    // DESPUÉS: el principal cuenta la historia; los detalles van abajo.
    static String orderSummary(String customer, List<Item> items) {
        BigDecimal total = applyVolumeDiscount(subtotalOf(items));
        return formatSummary(customer, items.size(), total);
    }

    static BigDecimal subtotalOf(List<Item> items) {
        return items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static final BigDecimal VOLUME_DISCOUNT_FROM = new BigDecimal("1000");
    static final BigDecimal VOLUME_DISCOUNT_RATE = new BigDecimal("0.05");

    static BigDecimal applyVolumeDiscount(BigDecimal subtotal) {
        BigDecimal discount = subtotal.compareTo(VOLUME_DISCOUNT_FROM) >= 0
                ? subtotal.multiply(VOLUME_DISCOUNT_RATE) : BigDecimal.ZERO;
        return subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
    }

    static String formatSummary(String customer, int itemCount, BigDecimal total) {
        return customer.trim().toUpperCase() + " | " + itemCount + " artículos | $" + total;
    }

    static boolean functionsExample() {
        List<Item> items = List.of(new Item("teclado", 2, new BigDecimal("500")), new Item("cable", 1, new BigDecimal("80")));
        return orderSummaryLong(" ana ", items).equals(orderSummary(" ana ", items))
                && orderSummary(" ana ", items).equals("ANA | 2 artículos | $1026.00");
    }

    /*
        =================================================================
        CAPA 5) CLÁUSULAS DE GUARDA: APLANAR LOS IF ANIDADOS
        =================================================================

        Un clásico de código difícil de leer es la "pirámide" (o "código
        flecha"): ifs dentro de ifs, con el caso normal enterrado al fondo
        y los else de los casos raros al final, lejos de su if.

        Las CLÁUSULAS DE GUARDA invierten el enfoque: revisa primero los
        casos que impiden continuar y SAL de inmediato (return o throw).
        Lo que queda abajo es el caso normal, sin sangría extra.

        Además se ve cada regla y su respuesta JUNTAS, en una línea, en vez
        de buscar qué else corresponde a qué if.
    */
    record Customer(String name, boolean active, boolean hasDebt, int loyaltyYears) { }

    // ANTES: la pirámide.
    static String creditDecisionNested(Customer customer, BigDecimal amount) {
        String result;
        if (customer != null) {
            if (customer.active()) {
                if (!customer.hasDebt()) {
                    if (amount.compareTo(new BigDecimal("50000")) <= 0) {
                        result = "APROBADO";
                    } else {
                        result = "RECHAZADO: monto";
                    }
                } else {
                    result = "RECHAZADO: adeudo";
                }
            } else {
                result = "RECHAZADO: inactivo";
            }
        } else {
            result = "RECHAZADO: sin cliente";
        }
        return result;
    }

    // DESPUÉS: guardas primero, el caso normal al final.
    static final BigDecimal MAX_CREDIT = new BigDecimal("50000");

    static String creditDecision(Customer customer, BigDecimal amount) {
        if (customer == null) {
            return "RECHAZADO: sin cliente";
        }
        if (!customer.active()) {
            return "RECHAZADO: inactivo";
        }
        if (customer.hasDebt()) {
            return "RECHAZADO: adeudo";
        }
        if (amount.compareTo(MAX_CREDIT) > 0) {
            return "RECHAZADO: monto";
        }
        return "APROBADO";
    }

    static boolean guardClausesExample() {
        Customer good = new Customer("ana", true, false, 3);
        Customer debtor = new Customer("luis", true, true, 1);
        BigDecimal amount = new BigDecimal("20000");
        return creditDecisionNested(good, amount).equals(creditDecision(good, amount))
                && creditDecisionNested(debtor, amount).equals(creditDecision(debtor, amount))
                && creditDecision(null, amount).equals("RECHAZADO: sin cliente")
                && creditDecision(good, new BigDecimal("90000")).equals("RECHAZADO: monto");
    }

    /*
        =================================================================
        CAPA 6) PARÁMETROS: POCOS Y CON SENTIDO
        =================================================================

        Cada parámetro es algo más que entender y que pasar en el orden
        correcto. Guía práctica:
        - 0 a 2 parámetros: ideal.
        - 3: aceptable si tienen sentido juntos.
        - Más de 3: casi siempre hay un concepto escondido.

        Problemas típicos:
        1) LISTAS LARGAS de parámetros del mismo tipo:
               schedule("Ana", "Luis", 9, 30, 60, true)
           ¿cuál es la hora? ¿cuál la duración? Invertir dos no da error de
           compilación y sí un error de lógica.
        2) PARÁMETROS BANDERA (boolean que cambia lo que hace la función):
           dos funciones con nombre (BasicPrinciples, CAPA 3).
        3) PARÁMETROS DE SALIDA (pasar una lista para que la función la
           llene): devuelve el resultado.

        Solución para listas largas: agrupar los que van juntos en un
        PARAMETER OBJECT, un record con nombre (v2_oop/
        part4_advanced_types/Records). Los datos que siempre viajan juntos
        (fecha + hora + duración) son un concepto: dale nombre. Y si son
        muchos opcionales, un Builder (v11_patterns/).
    */
    // ANTES: ¿qué es cada número?
    static String scheduleMeeting(String organizer, String guest, int hour, int minute, int durationMinutes) {
        return organizer + " con " + guest + " a las " + hour + ":" + String.format(Locale.ROOT, "%02d", minute) + " (" + durationMinutes + " min)";
    }

    // DESPUÉS: los datos que van juntos tienen nombre.
    record TimeSlot(int hour, int minute, int durationMinutes) {
        TimeSlot {
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || durationMinutes <= 0) {
                throw new IllegalArgumentException("Horario inválido");   // y ahora se valida en un solo lugar
            }
        }

        String label() {
            return hour + ":" + String.format(Locale.ROOT, "%02d", minute) + " (" + durationMinutes + " min)";
        }
    }

    static String scheduleMeeting(String organizer, String guest, TimeSlot slot) {
        return organizer + " con " + guest + " a las " + slot.label();
    }

    static boolean parameterObjectExample() {
        String before = scheduleMeeting("Ana", "Luis", 9, 30, 60);
        String after = scheduleMeeting("Ana", "Luis", new TimeSlot(9, 30, 60));
        String swapped = scheduleMeeting("Ana", "Luis", 9, 60, 30);   // compila, y es un error de lógica
        return before.equals(after) && before.equals("Ana con Luis a las 9:30 (60 min)") && !swapped.equals(before);
    }

    /*
        =================================================================
        CAPA 7) CONDICIONES CON NOMBRE
        =================================================================

        Una condición larga en un if obliga a "ejecutarla" en la cabeza
        para saber qué significa:

            if (customer.loyaltyYears() >= 2 && !customer.hasDebt() && total.compareTo(...) >= 0)

        Extráela a un método (o una variable) cuyo NOMBRE diga la regla de
        negocio: if (qualifiesForFreeShipping(customer, total)). Quien lee
        el if entiende QUÉ se decide; quien necesita el detalle, entra al
        método.

        Y escríbelas en POSITIVO: !isNotValid es una doble negación que
        hay que descifrar; isValid se lee solo.
    */
    // ANTES
    static boolean freeShippingRaw(Customer customer, BigDecimal total) {
        return customer.loyaltyYears() >= 2 && !customer.hasDebt() && total.compareTo(new BigDecimal("499")) >= 0
                || total.compareTo(new BigDecimal("1500")) >= 0;
    }

    // DESPUÉS: cada pedazo de la regla tiene nombre.
    static boolean qualifiesForFreeShipping(Customer customer, BigDecimal total) {
        return isLoyalCustomerWithMinimumPurchase(customer, total) || isLargeOrder(total);
    }

    static boolean isLoyalCustomerWithMinimumPurchase(Customer customer, BigDecimal total) {
        return isLoyalInGoodStanding(customer) && total.compareTo(new BigDecimal("499")) >= 0;
    }

    static boolean isLoyalInGoodStanding(Customer customer) {
        return customer.loyaltyYears() >= 2 && !customer.hasDebt();
    }

    static boolean isLargeOrder(BigDecimal total) {
        return total.compareTo(new BigDecimal("1500")) >= 0;
    }

    static boolean namedConditionsExample() {
        Customer loyal = new Customer("ana", true, false, 3);
        Customer newcomer = new Customer("mia", true, false, 0);
        for (Customer customer : List.of(loyal, newcomer)) {
            for (String total : List.of("100", "600", "2000")) {
                if (freeShippingRaw(customer, new BigDecimal(total)) != qualifiesForFreeShipping(customer, new BigDecimal(total))) {
                    return false;
                }
            }
        }
        return qualifiesForFreeShipping(loyal, new BigDecimal("600")) && !qualifiesForFreeShipping(newcomer, new BigDecimal("600"));
    }

    /*
        =================================================================
        CAPA 8) COMENTARIOS: EL PORQUÉ, NO EL QUÉ
        =================================================================

        Un comentario es una explicación que el código no supo dar. Antes de
        escribir uno, intenta que el CÓDIGO lo diga (un nombre mejor, una
        función extraída, una variable explicativa). Por qué: los
        comentarios NO SE EJECUTAN ni se prueban. Cuando el código cambia y
        nadie actualiza el comentario, el comentario MIENTE, y un comentario
        que miente es peor que ninguno.

        COMENTARIOS MALOS:
        - Los que repiten el código: i++; // incrementa i
        - Código comentado "por si acaso": para eso está el control de
          versiones (tools/, Git). Nadie se atreve a borrarlo y se pudre.
        - Diarios de cambios ("15/03 Juan cambió esto"): eso es el
          historial de Git.
        - Comentarios de cierre: } // fin del for. Si hacen falta, la
          función es demasiado larga.
        - Los que tapan código confuso ("// esto calcula el total, más o
          menos"): arregla el código.

        COMENTARIOS BUENOS:
        - El PORQUÉ de una decisión no obvia: "Usamos HALF_EVEN porque así
          lo pidió contabilidad para cuadrar con el banco".
        - ADVERTENCIAS de consecuencias: "No cambiar el orden: el banco
          valida el archivo por posición".
        - TODO con un ticket: // TODO(TICKET-123): soportar pagos en USD.
        - Javadoc en APIs públicas: qué hace, parámetros, qué lanza (el
          contrato; part2_solid/LiskovSubstitution, CAPA 4).
        - Aclarar algo que el lenguaje no puede expresar: una regex
          complicada, un algoritmo conocido.

        (Estas guías tienen muchos comentarios porque su propósito es
        ENSEÑAR. En código de producción, la mayoría de esto viviría en
        nombres y estructura.)

        El ejemplo: un comentario que explica una expresión, reemplazado por
        VARIABLES EXPLICATIVAS que dicen lo mismo y no se pueden
        desactualizar sin que se note.
    */
    // ANTES: el comentario explica lo que el código no dice.
    static boolean isEligibleCommented(LocalDate birthDate, LocalDate today, BigDecimal monthlyIncome) {
        // mayor de edad y con ingreso de al menos 3 salarios mínimos (de 7,468)
        return ChronoUnit.YEARS.between(birthDate, today) >= 18
                && monthlyIncome.compareTo(new BigDecimal("7468").multiply(BigDecimal.valueOf(3))) >= 0;
    }

    // DESPUÉS: el código se explica solo.
    static final BigDecimal MONTHLY_MINIMUM_WAGE = new BigDecimal("7468");
    static final int ADULT_AGE = 18;
    static final int REQUIRED_MINIMUM_WAGES = 3;

    static boolean isEligible(LocalDate birthDate, LocalDate today, BigDecimal monthlyIncome) {
        boolean isAdult = ChronoUnit.YEARS.between(birthDate, today) >= ADULT_AGE;
        BigDecimal requiredIncome = MONTHLY_MINIMUM_WAGE.multiply(BigDecimal.valueOf(REQUIRED_MINIMUM_WAGES));
        boolean earnsEnough = monthlyIncome.compareTo(requiredIncome) >= 0;
        return isAdult && earnsEnough;
    }

    static boolean explanatoryVariablesExample() {
        LocalDate today = LocalDate.of(2024, 6, 1);
        LocalDate adult = LocalDate.of(1990, 1, 1);
        LocalDate minor = LocalDate.of(2010, 1, 1);
        BigDecimal income = new BigDecimal("25000");
        return isEligible(adult, today, income) == isEligibleCommented(adult, today, income)
                && isEligible(minor, today, income) == isEligibleCommented(minor, today, income)
                && isEligible(adult, today, income) && !isEligible(minor, today, income);
    }

    /*
        =================================================================
        CAPA 9) FORMATO: CONSISTENCIA ANTES QUE GUSTOS
        =================================================================

        Dónde van las llaves, cuántos espacios, el largo de la línea...
        importan menos de lo que parece, con UNA condición: que todo el
        proyecto use LO MISMO. Leer un archivo con tres estilos distintos
        cansa y distrae.

        En la práctica:
        - Un FORMATEADOR AUTOMÁTICO configurado para todo el equipo (el del
          IDE, con una configuración compartida en el repositorio). Nadie
          discute ni corrige a mano. (En este proyecto ajustamos VS Code
          para que no reformatee los comentarios largos de las guías.)
        - Las convenciones de Java que todo el mundo espera: clases en
          PascalCase, métodos y variables en camelCase, constantes en
          MAYÚSCULAS_CON_GUION_BAJO, paquetes en minúsculas
          (v1_fundamentals/part1_first_steps/Variables).

        Y dos reglas de formato que sí ayudan a entender:
        - DISTANCIA VERTICAL: declara las variables cerca de donde se usan,
          y pon las funciones que se llaman entre sí cerca una de otra.
        - Líneas en blanco para separar IDEAS, como párrafos.
    */

    /*
        =================================================================
        CAPA 10) ERRORES LIMPIOS
        =================================================================

        El manejo de errores es donde el código se ensucia más rápido. Ya
        está a fondo en v3_exceptions/; lo que importa para la limpieza:

        - Excepciones en vez de códigos de error: un return -1 obliga a
          cada llamada a recordar revisarlo (v3_exceptions/part1_concepts/
          WhatAreExceptions).
        - No devuelvas null: Optional para "puede no haber", colección
          vacía para "no hay elementos" (v7_functional/part2_streams/
          OptionalUsage, CAPA 10).
        - No pases null como argumento para decir "no aplica": dos métodos
          o un valor por defecto.
        - Separa el camino feliz del manejo de errores: si una función es
          un try/catch enorme, extrae el contenido del try a otra función.
        - Mensajes de error que digan QUÉ falló y con QUÉ dato ("Producto
          desconocido: XXX-9", no "Error").
    */

    /*
        =================================================================
        CAPA 11) OBSESIÓN POR LOS PRIMITIVOS: TIPOS CON SIGNIFICADO
        =================================================================

        Usar String, int y double para TODO: el correo es un String, el
        dinero un double, el código postal un int, el teléfono un String.
        Es un code smell muy común ("primitive obsession"):
        - La validación se repite en cada lugar que recibe el dato (o se
          olvida).
        - Nada impide confundirlos: un método send(String email, String
          phone) acepta los dos al revés sin error.
        - Las reglas del concepto (normalizar el correo a minúsculas,
          redondear el dinero) quedan dispersas.

        Solución: pequeños TIPOS DE VALOR, normalmente records con
        validación en el constructor compacto: Email, Money, PostalCode.
        Una vez creado, un Email es VÁLIDO siempre, y el compilador impide
        pasar un teléfono donde va un correo.

        Es una de las mejoras más baratas y más efectivas que hay.
    */
    record Email(String value) {
        Email {
            Objects.requireNonNull(value, "email");
            value = value.strip().toLowerCase();            // normalizado en UN solo lugar
            if (!value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                throw new IllegalArgumentException("Correo inválido: " + value);
            }
        }
    }

    record Money(BigDecimal amount) {
        Money {
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("Monto negativo: " + amount);
            }
            amount = amount.setScale(2, RoundingMode.HALF_UP);   // siempre con centavos
        }

        Money plus(Money other) {
            return new Money(amount.add(other.amount));
        }
    }

    static String sendReceipt(Email to, Money total) {      // imposible pasar los argumentos al revés
        return "Recibo de $" + total.amount() + " enviado a " + to.value();
    }

    static boolean valueObjectsExample() {
        Email email = new Email("  Ana@Correo.COM ");
        boolean invalidRejected;
        try {
            new Email("ana.correo.com");
            invalidRejected = false;
        } catch (IllegalArgumentException e) {
            invalidRejected = true;
        }
        Money total = new Money(new BigDecimal("100")).plus(new Money(new BigDecimal("25.5")));
        return sendReceipt(email, total).equals("Recibo de $125.50 enviado a ana@correo.com") && invalidRejected;
    }

    /*
        =================================================================
        CAPA 12) CATÁLOGO DE CODE SMELLS
        =================================================================

        Un CODE SMELL ("mal olor en el código") es una señal en la
        superficie que SUELE indicar un problema de diseño más profundo. No
        es un error: el código funciona. Es una pista para revisar. El
        nombre y el catálogo vienen del libro "Refactoring" de Martin
        Fowler; la forma de arreglar cada uno está en
        part4_clean_code/Refactoring.

        | Smell                     | Cómo se ve                                    | Dónde se explica / qué hacer      |
        |---------------------------|-----------------------------------------------|-----------------------------------|
        | Método largo              | hay que desplazarse para leerlo               | CAPA 4: extraer funciones         |
        | Clase grande              | cientos de líneas, muchos campos              | SingleResponsibility              |
        | Muchos parámetros         | más de 3, del mismo tipo                      | CAPA 6: parameter object          |
        | Código duplicado          | la misma regla en varios lugares              | BasicPrinciples, CAPAS 5 y 6      |
        | Números mágicos           | literales sin nombre                          | CAPA 3: constantes, enums         |
        | Obsesión por primitivos   | String/double para todo                       | CAPA 11: tipos de valor           |
        | Grupos de datos           | los mismos 3 campos viajan siempre juntos     | CAPA 6: un record                 |
        | Envidia de funcionalidad  | un método usa más los datos de OTRA clase     | GRASP, CAPA 2: Information Expert |
        | (feature envy)            | que los suyos                                 |                                   |
        | Cadenas de mensajes       | a.getB().getC().getD()                        | Ley de Deméter (Composition-      |
        |                           |                                               | AndAggregation, CAPA 8)           |
        | Switch por tipo           | el mismo switch en varios lugares             | OpenClosed, CAPAS 3 y 4           |
        | Cirugía de escopeta       | un cambio toca muchos archivos                | OpenClosed, CAPA 3                |
        | Cambio divergente         | una clase cambia por muchas razones distintas | SingleResponsibility              |
        | Generalidad especulativa  | interfaces y parámetros "por si acaso"        | BasicPrinciples, CAPA 8 (YAGNI)   |
        | Código muerto             | métodos, parámetros o ramas que nadie usa     | borrarlo (Git lo recuerda)        |
        | Comentarios desodorante   | comentarios que tapan código confuso          | CAPA 8                            |
        | Campo temporal            | un campo que solo tiene valor a veces         | moverlo a donde se usa            |
        | Estado mutable compartido | variables static que cualquiera cambia        | BasicPrinciples, CAPA 3           |
        | Pirámide de if            | ifs anidados, el caso normal al fondo         | CAPA 5: cláusulas de guarda       |

        Muchos IDE detectan varios automáticamente, y hay herramientas de
        análisis que los revisan en cada cambio (tools/).
    */

    /*
        =================================================================
        CAPA 13) LOS LÍMITES DEL "CÓDIGO LIMPIO"
        =================================================================

        Las reglas de esta guía son guías, igual que los principios. Hay
        críticas serias a llevarlas al extremo:

        - FUNCIONES DIMINUTAS EN TODAS PARTES: dividir una lógica de 15
          líneas en diez funciones de 2 obliga a saltar entre diez lugares
          para entender algo que se leía de corrido. Extrae cuando el nombre
          aporta, no para cumplir un número.

        - INDIRECCIÓN EXCESIVA: interfaces, capas y clases para cosas
          simples (BasicPrinciples, CAPA 7).

        - CÓDIGO CRÍTICO EN RENDIMIENTO: a veces un ciclo explícito y
          "menos elegante" es lo correcto, y se justifica con mediciones
          (v12_jvm/).

        - CONSISTENCIA CON EL PROYECTO: en un código existente, seguir sus
          convenciones vale más que imponer las tuyas archivo por archivo.

        La prueba final no es "¿cumple las reglas?", sino: ¿alguien que no
        lo escribió lo entiende rápido y lo puede cambiar sin miedo?
    */

    /*
        =================================================================
        CAPA 14) ERRORES COMUNES
        =================================================================

        1) Nombres crípticos, abreviados o que mienten (CAPA 2).

        2) Números y textos mágicos repetidos (CAPA 3).

        3) Funciones largas que mezclan niveles de detalle (CAPA 4).

        4) Pirámides de if en lugar de cláusulas de guarda (CAPA 5).

        5) Muchos parámetros del mismo tipo, banderas booleanas (CAPA 6).

        6) Condiciones largas sin nombre, dobles negaciones (CAPA 7).

        7) Comentarios que repiten el código, código comentado, comentarios
           desactualizados (CAPA 8).

        8) Formato inconsistente sin un formateador compartido (CAPA 9).

        9) Devolver null y códigos de error (CAPA 10).

        10) String y double para conceptos con reglas (CAPA 11).

        11) Aplicar las reglas como dogma (CAPA 13).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Código limpio: otro lo entiende y lo cambia rápido y sin miedo.
          Se lee mucho más de lo que se escribe; la deuda técnica se
          acumula. Regla del Boy Scout.
        - Nombres que revelan intención, sin mentir, consistentes,
          buscables; constantes y enums en lugar de valores mágicos.
        - Funciones cortas, de una cosa y un nivel, leídas de arriba abajo;
          cláusulas de guarda; pocos parámetros (parameter object).
        - Condiciones con nombre y en positivo.
        - Comentarios para el PORQUÉ; el QUÉ lo dice el código.
        - Formato consistente y automático.
        - Errores con excepciones, sin null.
        - Tipos de valor (Email, Money) en lugar de primitivos.
        - Los code smells son pistas; Refactoring es la cura.
        - Sin dogma: lo que importa es que se entienda.
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
