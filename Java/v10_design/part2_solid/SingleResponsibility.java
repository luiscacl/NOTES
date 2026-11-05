package v10_design.part2_solid;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
    =====================================================================
    PRINCIPIO DE RESPONSABILIDAD ÚNICA (LA S DE SOLID)
    =====================================================================

    Ruta de design/: guía 2 de 10  (part2_solid)
    Anterior:  part1_basics/BasicPrinciples
    Siguiente: part2_solid/OpenClosed

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  SOLID: qué es y de dónde viene
        CAPA 2)  El malentendido: "una clase hace una sola cosa"
        CAPA 3)  La definición real: una razón para cambiar = un actor
        CAPA 4)  El problema en vivo: un cambio que rompe a otro equipo
        CAPA 5)  La solución: separar por actor
        CAPA 6)  Un caso de todos los días: el servicio que hace todo
        CAPA 7)  Cómo detectar que una clase tiene varias responsabilidades
        CAPA 8)  El error contrario: dividir de más
        CAPA 9)  La responsabilidad única en métodos, clases y paquetes
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class SingleResponsibility {

    public static void main(String[] args) {
        check(sharedHelperBreaksExample(), "CAPA 4");
        check(separatedByActorExample(), "CAPA 5");
        check(orderServiceExample(), "CAPA 6");
        check(methodLevelExample(), "CAPA 9");
        System.out.println("SingleResponsibility: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) SOLID: QUÉ ES Y DE DÓNDE VIENE
        =================================================================

        SOLID es un acrónimo de cinco principios de diseño orientado a
        objetos, reunidos por Robert C. Martin a principios de los 2000:

            S  Single Responsibility    responsabilidad única      (esta guía)
            O  Open/Closed              abierto/cerrado            (OpenClosed)
            L  Liskov Substitution      sustitución de Liskov      (LiskovSubstitution)
            I  Interface Segregation    segregación de interfaces  (InterfaceSegregation)
            D  Dependency Inversion     inversión de dependencias  (DependencyInversion)

        No son reglas nuevas: son formas concretas de lograr lo que vimos
        en part1_basics/BasicPrinciples, ALTA COHESIÓN y BAJO ACOPLAMIENTO,
        en código orientado a objetos. Son de lo más preguntado en
        entrevistas y lo más usado para discutir diseños en revisiones de
        código, así que conviene entenderlos bien, no solo memorizar las
        siglas.
    */

    /*
        =================================================================
        CAPA 2) EL MALENTENDIDO: "UNA CLASE HACE UNA SOLA COSA"
        =================================================================

        Casi todo el mundo resume la S como "cada clase debe hacer una sola
        cosa". Suena bien, pero no es lo que dice, y aplicarlo así lleva a
        malos diseños:
        - "Una sola cosa" no tiene medida. ¿Un carrito que agrega, quita y
          calcula el total hace una cosa o tres? Según cómo lo mires.
        - Llevado al extremo, produce una clase por método (CAPA 8).

        "Hacer una sola cosa" es un buen consejo, pero para los MÉTODOS
        (CAPA 9). Para las clases, el principio habla de otra cosa: de
        quién pide los CAMBIOS.
    */

    /*
        =================================================================
        CAPA 3) LA DEFINICIÓN REAL: UNA RAZÓN PARA CAMBIAR = UN ACTOR
        =================================================================

        La formulación original:
            "Una clase debe tener una, y solo una, razón para cambiar."

        ¿Qué es una "razón para cambiar"? El código cambia porque ALGUIEN
        lo pide: una persona o un grupo con un interés concreto. El autor
        lo aclaró después:
            "Un módulo debe ser responsable ante uno, y solo un, ACTOR."

        Un ACTOR es un rol que pide cambios: el área de finanzas, recursos
        humanos, el equipo de base de datos, el equipo de diseño de la
        interfaz, el área legal...

        Así la pregunta deja de ser "¿hace una cosa?" y pasa a ser:
            "¿Quiénes podrían pedirme cambiar esta clase?"
        Si la respuesta incluye a varios actores con intereses distintos,
        la clase tiene varias responsabilidades.

        Por qué importa: cuando dos actores comparten una clase, un cambio
        que pide uno puede romper lo que usa el otro, y el otro ni
        siquiera se entera (CAPA 4). Además, los dos equipos editan el
        mismo archivo al mismo tiempo y chocan.

        Es la cohesión de BasicPrinciples (CAPA 2) dicha de forma
        práctica: lo que cambia por la misma razón, va junto; lo que
        cambia por razones distintas, separado.
    */

    /*
        =================================================================
        CAPA 4) EL PROBLEMA EN VIVO: UN CAMBIO QUE ROMPE A OTRO EQUIPO
        =================================================================

        El ejemplo clásico. Una clase Employee con tres métodos:
            calculatePay()   lo pide FINANZAS (cuánto se paga)
            reportHours()    lo pide RECURSOS HUMANOS (reporte de horas)
            save()           lo pide el equipo de BASE DE DATOS

        Tres actores en una clase. Y calculatePay y reportHours comparten
        un método auxiliar, regularHours(), que separa las horas normales
        de las extra (las que pasan de 40 a la semana). Compartirlo parece
        DRY.

        Un día FINANZAS pide: "para la nómina, las horas normales ahora son
        hasta 36". El programador modifica regularHours(). Las pruebas de
        nómina pasan. Pero reportHours() también usaba regularHours(): el
        reporte de RECURSOS HUMANOS empieza a mostrar números distintos, y
        RH nunca pidió nada. Nadie lo nota hasta que los reportes no
        cuadran con los contratos.

        El ejemplo pone las dos versiones de la clase (antes y después del
        cambio de finanzas) y muestra que el reporte de RH cambió.
        Fíjate en que es la duplicación ACCIDENTAL de BasicPrinciples,
        CAPA 6: las dos reglas se parecían, pero pertenecían a actores
        distintos.
    */
    record Timesheet(String employee, int hoursWorked, BigDecimal hourlyRate) { }

    // Versión 1: un Employee que atiende a tres actores.
    static final class EmployeeV1 {
        private final Timesheet timesheet;
        final List<String> database = new ArrayList<>();

        EmployeeV1(Timesheet timesheet) {
            this.timesheet = timesheet;
        }

        private int regularHours() {                         // compartido por finanzas y RH
            return Math.min(timesheet.hoursWorked(), 40);
        }

        BigDecimal calculatePay() {                          // FINANZAS
            int overtime = timesheet.hoursWorked() - regularHours();
            return timesheet.hourlyRate().multiply(BigDecimal.valueOf(regularHours()))
                    .add(timesheet.hourlyRate().multiply(new BigDecimal("1.5")).multiply(BigDecimal.valueOf(overtime)));
        }

        String reportHours() {                               // RECURSOS HUMANOS
            return timesheet.employee() + ": " + regularHours() + " normales";
        }

        void save() {                                        // BASE DE DATOS
            database.add(timesheet.employee() + "|" + timesheet.hoursWorked());
        }
    }

    // Versión 2: finanzas pidió "horas normales hasta 36 para la nómina".
    static final class EmployeeV2 {
        private final Timesheet timesheet;

        EmployeeV2(Timesheet timesheet) {
            this.timesheet = timesheet;
        }

        private int regularHours() {
            return Math.min(timesheet.hoursWorked(), 36);    // el cambio que pidió FINANZAS
        }

        BigDecimal calculatePay() {
            int overtime = timesheet.hoursWorked() - regularHours();
            return timesheet.hourlyRate().multiply(BigDecimal.valueOf(regularHours()))
                    .add(timesheet.hourlyRate().multiply(new BigDecimal("1.5")).multiply(BigDecimal.valueOf(overtime)));
        }

        String reportHours() {                               // RH no pidió nada... y su reporte cambió
            return timesheet.employee() + ": " + regularHours() + " normales";
        }
    }

    static boolean sharedHelperBreaksExample() {
        Timesheet week = new Timesheet("Ana", 40, new BigDecimal("100"));

        boolean financeGotWhatItAsked = new EmployeeV2(week).calculatePay()
                .compareTo(new BigDecimal("4200")) == 0;     // 36 normales + 4 extra a 1.5
        boolean hrReportChangedSilently = new EmployeeV1(week).reportHours().equals("Ana: 40 normales")
                && new EmployeeV2(week).reportHours().equals("Ana: 36 normales");

        return financeGotWhatItAsked && hrReportChangedSilently;
    }

    /*
        =================================================================
        CAPA 5) LA SOLUCIÓN: SEPARAR POR ACTOR
        =================================================================

        Se separan los DATOS del COMPORTAMIENTO de cada actor:
        - Timesheet: solo los datos (un record).
        - PayCalculator: la lógica de FINANZAS, con SU regla de horas
          normales.
        - HoursReporter: la lógica de RECURSOS HUMANOS, con SU regla.
        - TimesheetRepository: el guardado, del equipo de BASE DE DATOS.

        Ahora cuando finanzas cambia su regla a 36 horas, solo se toca
        PayCalculator. HoursReporter ni se compila de nuevo. Cada clase
        tiene UN actor, y su propio archivo, sus propias pruebas.

        Nota: cada clase tiene su propia regla de "horas normales", aunque
        hoy se parezcan. Es a propósito: es la misma palabra, pero son DOS
        conocimientos de dos actores distintos (BasicPrinciples, CAPA 6).

        ¿Y si quien usa todo esto quiere un solo punto de entrada? Se puede
        agregar una clase que solo DELEGA en las otras (una "fachada";
        v11_patterns/). Coordinar no es lo mismo que contener la lógica.
    */
    static final class PayCalculator {                       // actor: FINANZAS
        private static final int PAYROLL_REGULAR_HOURS = 36;
        private static final BigDecimal OVERTIME_FACTOR = new BigDecimal("1.5");

        BigDecimal payFor(Timesheet timesheet) {
            int regular = Math.min(timesheet.hoursWorked(), PAYROLL_REGULAR_HOURS);
            int overtime = timesheet.hoursWorked() - regular;
            return timesheet.hourlyRate().multiply(BigDecimal.valueOf(regular))
                    .add(timesheet.hourlyRate().multiply(OVERTIME_FACTOR).multiply(BigDecimal.valueOf(overtime)));
        }
    }

    static final class HoursReporter {                       // actor: RECURSOS HUMANOS
        private static final int CONTRACT_REGULAR_HOURS = 40;

        String reportFor(Timesheet timesheet) {
            return timesheet.employee() + ": " + Math.min(timesheet.hoursWorked(), CONTRACT_REGULAR_HOURS) + " normales";
        }
    }

    static final class TimesheetRepository {                 // actor: BASE DE DATOS
        private final List<String> rows = new ArrayList<>();

        void save(Timesheet timesheet) {
            rows.add(timesheet.employee() + "|" + timesheet.hoursWorked());
        }

        int count() {
            return rows.size();
        }
    }

    static boolean separatedByActorExample() {
        Timesheet week = new Timesheet("Ana", 40, new BigDecimal("100"));
        TimesheetRepository repository = new TimesheetRepository();
        repository.save(week);

        return new PayCalculator().payFor(week).compareTo(new BigDecimal("4200")) == 0   // la regla nueva de finanzas
                && new HoursReporter().reportFor(week).equals("Ana: 40 normales")        // RH intacto
                && repository.count() == 1;
    }

    /*
        =================================================================
        CAPA 6) UN CASO DE TODOS LOS DÍAS: EL SERVICIO QUE HACE TODO
        =================================================================

        En proyectos reales, la violación más común no es Employee: es el
        "servicio" que creció sin control. Un OrderService.placeOrder()
        que en 200 líneas:
        - valida el pedido (reglas de negocio),
        - calcula precios y descuentos (lo pide comercial),
        - guarda en la base de datos (persistencia),
        - manda el correo de confirmación (lo pide marketing: el texto, el
          diseño),
        - y escribe el registro para auditoría (lo pide el área legal).

        Cinco razones para cambiar. Y para probar UNA regla de descuento
        hay que preparar una base de datos y un servidor de correo.

        Se divide en piezas, cada una con un actor:
            OrderValidator   reglas de validez
            PricingPolicy    precios y descuentos
            OrderRepository  guardar (una interface: hoy en memoria,
                             mañana en una base de datos)
            Notifier         avisar al cliente (una interface)

        Y OrderService se queda con UNA responsabilidad, que también es
        real: ORQUESTAR el proceso (el orden de los pasos y qué pasa si uno
        falla). Su código se lee como la descripción del caso de uso.

        Bonus inmediato: en las pruebas, el repositorio y el notificador se
        reemplazan por versiones de prueba en memoria (como las de abajo).
        Probar el flujo completo no necesita ni base de datos ni correo.
        (Por qué se usan interfaces aquí: part2_solid/DependencyInversion.)
    */
    record OrderLine(String sku, int quantity, BigDecimal unitPrice) { }

    record OrderRequest(String customerEmail, List<OrderLine> lines) { }

    record PlacedOrder(String id, String customerEmail, BigDecimal total) { }

    static final class OrderValidator {                      // reglas de validez
        List<String> problemsWith(OrderRequest request) {
            List<String> problems = new ArrayList<>();
            if (!request.customerEmail().contains("@")) {
                problems.add("correo inválido");
            }
            if (request.lines().isEmpty()) {
                problems.add("pedido vacío");
            }
            return problems;
        }
    }

    static final class PricingPolicy {                       // precios: lo pide comercial
        BigDecimal totalFor(List<OrderLine> lines) {
            BigDecimal subtotal = lines.stream()
                    .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal discount = subtotal.compareTo(new BigDecimal("1000")) >= 0
                    ? subtotal.multiply(new BigDecimal("0.10")) : BigDecimal.ZERO;
            return subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        }
    }

    interface OrderRepository {                              // persistencia
        PlacedOrder save(String customerEmail, BigDecimal total);

        Optional<PlacedOrder> findById(String id);
    }

    interface Notifier {                                     // avisar al cliente
        void orderConfirmed(PlacedOrder order);
    }

    static final class InMemoryOrderRepository implements OrderRepository {
        private final Map<String, PlacedOrder> orders = new HashMap<>();

        @Override
        public PlacedOrder save(String customerEmail, BigDecimal total) {
            PlacedOrder order = new PlacedOrder("ORD-" + (orders.size() + 1), customerEmail, total);
            orders.put(order.id(), order);
            return order;
        }

        @Override
        public Optional<PlacedOrder> findById(String id) {
            return Optional.ofNullable(orders.get(id));
        }
    }

    static final class RecordingNotifier implements Notifier {   // versión de prueba: anota en vez de enviar
        final List<String> sent = new ArrayList<>();

        @Override
        public void orderConfirmed(PlacedOrder order) {
            sent.add(order.customerEmail() + " -> " + order.id());
        }
    }

    static final class InvalidOrderException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidOrderException(List<String> problems) {
            super("Pedido inválido: " + String.join(", ", problems));
        }
    }

    static final class OrderService {                        // UNA responsabilidad: orquestar
        private final OrderValidator validator;
        private final PricingPolicy pricing;
        private final OrderRepository repository;
        private final Notifier notifier;

        OrderService(OrderValidator validator, PricingPolicy pricing, OrderRepository repository, Notifier notifier) {
            this.validator = validator;
            this.pricing = pricing;
            this.repository = repository;
            this.notifier = notifier;
        }

        PlacedOrder placeOrder(OrderRequest request) {
            List<String> problems = validator.problemsWith(request);
            if (!problems.isEmpty()) {
                throw new InvalidOrderException(problems);  // nada se guarda ni se notifica
            }
            BigDecimal total = pricing.totalFor(request.lines());
            PlacedOrder order = repository.save(request.customerEmail(), total);
            notifier.orderConfirmed(order);
            return order;
        }
    }

    static boolean orderServiceExample() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        RecordingNotifier notifier = new RecordingNotifier();
        OrderService service = new OrderService(new OrderValidator(), new PricingPolicy(), repository, notifier);

        PlacedOrder order = service.placeOrder(new OrderRequest("ana@correo.com",
                List.of(new OrderLine("KBD-001", 2, new BigDecimal("600")))));

        boolean rejected;
        try {
            service.placeOrder(new OrderRequest("sin-arroba", List.of()));
            rejected = false;
        } catch (InvalidOrderException e) {
            rejected = e.getMessage().equals("Pedido inválido: correo inválido, pedido vacío");
        }

        boolean pricingAlone = new PricingPolicy()                // la regla se prueba sin nada más
                .totalFor(List.of(new OrderLine("X", 1, new BigDecimal("999")))).compareTo(new BigDecimal("999.00")) == 0;

        return order.total().compareTo(new BigDecimal("1080.00")) == 0
                && repository.findById(order.id()).isPresent()
                && notifier.sent.equals(List.of("ana@correo.com -> ORD-1"))
                && rejected && notifier.sent.size() == 1          // el inválido no se notificó
                && pricingAlone;
    }

    /*
        =================================================================
        CAPA 7) CÓMO DETECTAR QUE UNA CLASE TIENE VARIAS RESPONSABILIDADES
        =================================================================

        Señales, de más a menos obvias:

        1) Varios ACTORES podrían pedirte cambiarla (la prueba de la
           CAPA 3). Pregúntalo explícitamente en cada revisión.
        2) Al describirla necesitas "y": "valida Y calcula Y guarda Y
           envía".
        3) Sus IMPORTS mezclan mundos: SQL, HTML, correo, PDF y reglas de
           negocio en el mismo archivo.
        4) El NOMBRE es vago: Manager, Processor, Handler, Helper, Utils,
           Service a secas. Un nombre vago acepta cualquier cosa.
        5) Grupos de métodos que usan grupos distintos de campos: son
           clases distintas viviendo juntas (BasicPrinciples, CAPA 2).
        6) Para probar UNA regla hay que preparar muchas cosas que no
           tienen nada que ver (base de datos, correo, archivos).
        7) Crece SIEMPRE: cada funcionalidad nueva termina en ese archivo,
           que pasa de 300 a 3000 líneas.
        8) Personas de distintos equipos la editan a la vez y chocan
           (conflictos en el control de versiones, tools/).

        Una sola señal no condena a una clase; varias juntas, casi seguro.
    */

    /*
        =================================================================
        CAPA 8) EL ERROR CONTRARIO: DIVIDIR DE MÁS
        =================================================================

        Mal entendido como "una cosa por clase", el principio produce:
            CartItemAdder, CartItemRemover, CartTotalCalculator,
            CartItemCounter...
        Cuatro clases de cinco líneas que SIEMPRE cambian juntas (porque
        son el carrito) y que hay que abrir todas para entender algo
        simple.

        Eso empeora el diseño:
        - La cohesión se pierde: lo que pertenece junto quedó repartido.
        - El acoplamiento SUBE: las piezas se pasan datos entre sí todo el
          tiempo para poder hacer algo útil.
        - Cada cambio sigue tocando muchos archivos, que era justo lo que
          se quería evitar.

        La misma pregunta resuelve los dos errores: ¿estas piezas cambian
        por la MISMA razón (el mismo actor)? Si sí, van JUNTAS, aunque sean
        varios métodos. El Cart de BasicPrinciples (agregar, contar,
        subtotal) tiene una sola responsabilidad: ser el carrito.

        Y un record que agrupa datos con sus cálculos propios (Money con
        su suma y su redondeo) tampoco viola nada: todo cambia por la misma
        razón.
    */

    /*
        =================================================================
        CAPA 9) LA RESPONSABILIDAD ÚNICA EN MÉTODOS, CLASES Y PAQUETES
        =================================================================

        La idea se aplica a varios tamaños:

        - MÉTODOS: aquí sí vale "hacer UNA sola cosa", a un mismo nivel de
          detalle. Un método que valida, después recorre líneas sumando
          precios y después arma un texto mezcla niveles. Mejor que cada
          paso sea un método con nombre, y que el método principal se lea
          como una lista de pasos (part4_clean_code/CleanCode).

        - CLASES: un actor, una razón para cambiar (este principio).

        - PAQUETES Y MÓDULOS: lo que cambia junto, se publica junto. Por
          eso los proyectos grandes se organizan por FUNCIONALIDAD
          (pedidos/, pagos/, clientes/) más que por tipo técnico
          (controllers/, services/): un cambio en pagos queda dentro de
          pagos/ (v14_architecture/).

        El ejemplo muestra el nivel de método: el mismo cálculo escrito
        todo en uno, y dividido en pasos con nombre.
    */
    // Todo mezclado en un método: validar, calcular y formatear.
    static String receiptAllInOne(List<OrderLine> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("pedido vacío");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        return "Total a pagar: $" + total.setScale(2, RoundingMode.HALF_UP);
    }

    // El método principal se lee como una lista de pasos; cada paso hace una cosa.
    static String receipt(List<OrderLine> lines) {
        requireNotEmpty(lines);
        return formatTotal(sum(lines));
    }

    private static void requireNotEmpty(List<OrderLine> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("pedido vacío");
        }
    }

    private static BigDecimal sum(List<OrderLine> lines) {
        return lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String formatTotal(BigDecimal total) {
        return "Total a pagar: $" + total.setScale(2, RoundingMode.HALF_UP);
    }

    static boolean methodLevelExample() {
        List<OrderLine> lines = List.of(new OrderLine("A", 2, new BigDecimal("10.5")), new OrderLine("B", 1, new BigDecimal("4")));
        return receipt(lines).equals(receiptAllInOne(lines)) && receipt(lines).equals("Total a pagar: $25.00");
    }

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Entender la S como "una cosa por clase" y fabricar clases de un
           método (CAPAS 2 y 8).

        2) Compartir un método auxiliar entre dos actores porque "se
           parece": el cambio de uno rompe al otro (CAPA 4).

        3) El "servicio que hace todo": validación, precios, guardado,
           correo y auditoría en un método (CAPA 6).

        4) Nombres vagos (Manager, Utils) que invitan a meter cualquier
           cosa (CAPA 7).

        5) Separar la lógica pero dejar que el orquestador vuelva a
           contenerla: el servicio solo debe coordinar (CAPA 6).

        6) Métodos que mezclan niveles de detalle (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - SOLID: cinco principios para lograr alta cohesión y bajo
          acoplamiento en código orientado a objetos.
        - S: una clase debe tener una sola razón para cambiar = responder
          ante UN solo actor (finanzas, RH, base de datos...).
        - Cuando dos actores comparten código, el cambio de uno rompe al
          otro sin aviso.
        - Separa por actor; el que coordina tiene su propia
          responsabilidad: orquestar.
        - Señales: varios actores, "y" en la descripción, imports de muchos
          mundos, nombres vagos, pruebas difíciles, crecimiento sin fin.
        - No dividas de más: lo que cambia junto, va junto.
        - En métodos: una cosa, un nivel de detalle.
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
