package v14_architecture.part3_systems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/*
    =====================================================================
    CQRS Y CONSISTENCIA: LEER, ESCRIBIR Y AVISAR SIN PERDER NADA
    =====================================================================

    Ruta de architecture/: guía 5 de 6  (part3_systems)
    Anterior:  part2_boundaries/CleanArchitecture
    Siguiente: part3_systems/MonolithVsMicroservices

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - No hay base de datos ni cola de mensajes reales: hay versiones en
      memoria que SÍ tienen transacciones (deshacen todo si algo falla,
      como en v11_patterns/part4_modern/ServiceLayer, CAPA 5) y que pueden
      "caerse" a propósito en el peor momento, para ver qué se pierde.

    Pendientes que esta guía cierra: las consultas sin ceremonia y CQRS
    (ServiceLayer, CAPA 9), el correo que no puede perderse y el cobro que
    pasó aunque el pedido no (ServiceLayer, CAPA 6), y la consistencia
    eventual entre agregados (part1_layers/DDDBasics, CAPA 6).

    Importancia: ALTA. En cuanto hay más de un sistema (una base de datos
    y una cola, dos servicios, un proveedor de pagos), aparecen estos
    problemas, y las soluciones de aquí son las estándar.

    Índice:
        CAPA 1)  Comandos y consultas: separar
        CAPA 2)  CQRS: un modelo para escribir, otro para leer
        CAPA 3)  Consistencia eventual
        CAPA 4)  El problema de la doble escritura
        CAPA 5)  Outbox: el evento en la misma transacción
        CAPA 6)  Entregas repetidas: consumidores idempotentes
        CAPA 7)  Claves de idempotencia: reintentar sin cobrar dos veces
        CAPA 8)  Sagas: transacciones entre sistemas con compensaciones
        CAPA 9)  Event sourcing, en una página
        CAPA 10) Cuándo usar cada cosa y errores comunes
        RESUMEN FINAL
*/
public class CommandsQueriesAndConsistency {

    public static void main(String[] args) {
        check(cqrsExample(), "CAPA 2");
        check(eventualConsistencyExample(), "CAPA 3");
        check(dualWriteExample(), "CAPA 4");
        check(outboxExample(), "CAPA 5");
        check(idempotentConsumerExample(), "CAPA 6");
        check(idempotencyKeyExample(), "CAPA 7");
        check(sagaExample(), "CAPA 8");
        System.out.println("CommandsQueriesAndConsistency: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) COMANDOS Y CONSULTAS: SEPARAR
        =================================================================

        Bertrand Meyer propuso hace décadas CQS (Command-Query Separation):
        un método o CAMBIA algo (comando: no devuelve datos) o PREGUNTA algo
        (consulta: no cambia nada). Nunca las dos. Una consulta que también
        cambia estado sorprende a quien la llama dos veces "solo para ver".

        CQRS (Command Query Responsibility Segregation, Greg Young, ~2010)
        lleva la idea de los métodos a la ARQUITECTURA: el lado que escribe
        y el lado que lee tienen MODELOS distintos, porque sus necesidades
        son opuestas:

            escribir                              leer
            ------------------------------------  ----------------------------------
            pocas operaciones, con reglas         muchísimas, sin reglas
            agregados (DDDBasics, CAPA 5)         vistas listas para la pantalla
            normalizado: cada dato una vez        desnormalizado: todo junto,
                                                  aunque se repita
            consistencia estricta                 puede ir unos milisegundos atrás
    */

    /*
        =================================================================
        CAPA 2) CQRS: UN MODELO PARA ESCRIBIR, OTRO PARA LEER
        =================================================================

        El lado de ESCRITURA: los agregados con sus reglas. Cada cambio
        produce un evento (DDDBasics, CAPA 8).

        El lado de LECTURA: un "modelo de lectura" o PROYECCIÓN: tablas (o
        mapas, o un índice de búsqueda) con la forma EXACTA que pide cada
        pantalla. Un PROYECTOR escucha los eventos y actualiza esas vistas.

        Ejemplo: la pantalla "mis pedidos" muestra, por cliente, cuántos
        pedidos tiene y cuánto ha gastado. Sin CQRS, cada vez que alguien
        abre la pantalla se recorren todos sus pedidos y se suman. Con CQRS,
        la vista CustomerSummary ya tiene los números: leer es buscar UNA
        fila.

        El costo: dos modelos que mantener, y que el de lectura se
        actualice DESPUÉS (CAPA 3).
    */
    record OrderPlaced(String eventId, long orderId, String customer, long totalCents) {
    }

    record CustomerSummary(String customer, int orders, long spentCents) {   // la vista de lectura
    }

    static final class CustomerSummaryProjector {           // escucha eventos y mantiene la vista
        private final Map<String, CustomerSummary> view = new HashMap<>();

        void on(OrderPlaced event) {
            view.merge(event.customer(), new CustomerSummary(event.customer(), 1, event.totalCents()),
                    (old, one) -> new CustomerSummary(old.customer(), old.orders() + 1, old.spentCents() + one.spentCents()));
        }

        CustomerSummary summaryOf(String customer) {        // la consulta: una búsqueda, sin cálculos
            return view.getOrDefault(customer, new CustomerSummary(customer, 0, 0));
        }
    }

    static boolean cqrsExample() {
        CustomerSummaryProjector projector = new CustomerSummaryProjector();
        projector.on(new OrderPlaced("e1", 1, "ana", 25_000));
        projector.on(new OrderPlaced("e2", 2, "luis", 9_000));
        projector.on(new OrderPlaced("e3", 3, "ana", 15_000));
        return projector.summaryOf("ana").equals(new CustomerSummary("ana", 2, 40_000))
                && projector.summaryOf("eve").orders() == 0;
    }

    /*
        =================================================================
        CAPA 3) CONSISTENCIA EVENTUAL
        =================================================================

        Si la vista se actualiza al procesar el evento, y el evento se
        procesa DESPUÉS de guardar (por una cola, en otro hilo, en otro
        servicio), hay un momento en que el pedido ya existe pero la vista
        todavía no lo muestra. Se llama CONSISTENCIA EVENTUAL: el sistema
        no está de acuerdo en todo instante, pero lo estará en poco tiempo
        si no llegan cambios nuevos.

        No es un error: es un precio aceptado a cambio de escalar y
        desacoplar. Pero hay que diseñar para él:
        - en la interfaz, mostrar lo que el usuario acaba de hacer con la
          respuesta del comando (no releyendo la vista), o un "procesando";
        - nunca tomar decisiones de NEGOCIO leyendo el modelo de lectura
          ("¿tiene saldo?" se pregunta al lado de escritura, que es exacto).

        Y la consistencia INMEDIATA se reserva para dentro de un agregado
        (DDDBasics, CAPA 6). Entre agregados o entre sistemas, eventual.

        El ejemplo: el comando se guarda y el evento queda en una cola;
        mientras no se procesa, la vista está atrás.
    */
    static boolean eventualConsistencyExample() {
        List<OrderPlaced> queue = new ArrayList<>();
        CustomerSummaryProjector projector = new CustomerSummaryProjector();

        queue.add(new OrderPlaced("e1", 1, "ana", 25_000)); // el comando terminó: "pedido creado"
        boolean staleRightAfter = projector.summaryOf("ana").orders() == 0;   // la vista aún no lo sabe

        queue.forEach(projector::on);                       // el proyector alcanza
        queue.clear();
        return staleRightAfter && projector.summaryOf("ana").orders() == 1;
    }

    /*
        =================================================================
        CAPA 4) EL PROBLEMA DE LA DOBLE ESCRITURA
        =================================================================

        Al crear un pedido hay que hacer DOS cosas: guardarlo en la base de
        datos y publicar "OrderPlaced" en la cola de mensajes, para que el
        almacén, el correo y las vistas reaccionen. Son dos sistemas
        distintos, y NO hay una transacción que abarque a los dos.

            guardar en la base de datos     (commit)
            <-- el proceso se cae aquí: un despliegue, un corte, un OOM
            publicar en la cola             (nunca pasa)

        Resultado: el pedido existe, pero nadie se enteró. No se envía, no
        se manda el correo, la vista no lo muestra. Al revés (publicar
        primero, guardar después) es peor: se avisa de un pedido que, si
        falla el guardado, no existe.

        Es exactamente el correo de ServiceLayer, CAPA 6, en grande. El
        ejemplo lo provoca: se "cae" entre el commit y la publicación, y el
        evento se pierde.
    */
    static final class Database {                           // en memoria, con transacciones
        private Map<Long, OrderPlaced> orders = new LinkedHashMap<>();
        private Map<String, OrderPlaced> outbox = new LinkedHashMap<>();   // CAPA 5

        <T> T inTransaction(Supplier<T> work) {
            Map<Long, OrderPlaced> ordersBefore = new LinkedHashMap<>(orders);
            Map<String, OrderPlaced> outboxBefore = new LinkedHashMap<>(outbox);
            try {
                return work.get();                          // commit: todo queda
            } catch (RuntimeException e) {
                orders = ordersBefore;                      // rollback: nada queda
                outbox = outboxBefore;
                throw e;
            }
        }
    }

    static final class ProcessCrashed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ProcessCrashed() {
            super("el proceso se cayó");
        }
    }

    static boolean dualWriteExample() {
        Database database = new Database();
        List<OrderPlaced> broker = new ArrayList<>();       // la cola de mensajes
        OrderPlaced event = new OrderPlaced("e1", 1, "ana", 25_000);
        try {
            database.inTransaction(() -> database.orders.put(event.orderId(), event));   // 1) commit
            crashHere();                                    // 2) se cae justo aquí
            broker.add(event);                              // 3) nunca llega
        } catch (ProcessCrashed e) {
            // el proceso "reinicia": lo que quedó, quedó
        }
        return database.orders.containsKey(1L) && broker.isEmpty();   // existe, y nadie se enteró
    }

    /*
        =================================================================
        CAPA 5) OUTBOX: EL EVENTO EN LA MISMA TRANSACCIÓN
        =================================================================

        La solución estándar, el patrón OUTBOX ("bandeja de salida"):

        1) en la MISMA transacción en que se guarda el pedido, se guarda el
           evento en una tabla "outbox" de la MISMA base de datos. Como es
           una sola transacción, o quedan los dos o ninguno;
        2) un proceso aparte (el "relay") lee la outbox, publica cada evento
           en la cola, y lo marca como enviado (o lo borra).

        Si el proceso se cae después del commit, el evento está a salvo en
        la outbox, y el relay lo publica cuando vuelva. Ya no se pierde
        nada.

        El precio: "al menos una vez". Si el relay publica y se cae ANTES de
        marcar el evento como enviado, al volver lo publica OTRA VEZ. Los
        mensajes pueden llegar repetidos, y los consumidores tienen que
        aguantarlo (CAPA 6).

        En la práctica, el relay es un ciclo que consulta la outbox cada
        poco tiempo, o una herramienta que lee el registro de cambios de la
        base de datos ("change data capture").
    */
    static final class OrderServiceWithOutbox {
        private final Database database;

        OrderServiceWithOutbox(Database database) {
            this.database = database;
        }

        void placeOrder(OrderPlaced event, boolean crashAfterCommit) {
            database.inTransaction(() -> {
                database.orders.put(event.orderId(), event);
                database.outbox.put(event.eventId(), event); // el evento, en la MISMA transacción
                return null;
            });
            if (crashAfterCommit) {
                throw new ProcessCrashed();                 // ya no importa: el evento está guardado
            }
        }
    }

    static final class OutboxRelay {
        private final Database database;
        private final List<OrderPlaced> broker;

        OutboxRelay(Database database, List<OrderPlaced> broker) {
            this.database = database;
            this.broker = broker;
        }

        void publishPending(boolean crashBeforeMarking) {
            for (OrderPlaced event : List.copyOf(database.outbox.values())) {
                broker.add(event);                          // publicar
                if (crashBeforeMarking) {
                    throw new ProcessCrashed();             // publicado pero NO marcado: se repetirá
                }
                database.outbox.remove(event.eventId());    // marcar como enviado
            }
        }
    }

    static boolean outboxExample() {
        Database database = new Database();
        List<OrderPlaced> broker = new ArrayList<>();
        OrderServiceWithOutbox service = new OrderServiceWithOutbox(database);
        OutboxRelay relay = new OutboxRelay(database, broker);

        try {
            service.placeOrder(new OrderPlaced("e1", 1, "ana", 25_000), true);   // se cae tras el commit
        } catch (ProcessCrashed e) {
            // reinicia
        }
        boolean safeInOutbox = database.outbox.containsKey("e1");

        try {
            relay.publishPending(true);                     // publica y se cae antes de marcar
        } catch (ProcessCrashed e) {
            // reinicia
        }
        relay.publishPending(false);                        // al volver: lo publica OTRA vez
        return safeInOutbox
                && broker.size() == 2                       // al menos una vez... aquí, dos
                && database.outbox.isEmpty();
    }

    /*
        =================================================================
        CAPA 6) ENTREGAS REPETIDAS: CONSUMIDORES IDEMPOTENTES
        =================================================================

        Prácticamente toda la mensajería real entrega "al menos una vez":
        el relay de la CAPA 5, los reintentos de red, la cola que reenvía si
        el consumidor no confirmó a tiempo. "Exactamente una vez" no existe
        como garantía de punta a punta; lo que existe es HACER que repetir no
        cause daño.

        Una operación es IDEMPOTENTE si hacerla dos veces da lo mismo que
        hacerla una: "fijar el estado en PAGADO" lo es; "sumar 10 puntos" no.

        Cómo se hace un consumidor idempotente:
        - cada evento trae un ID único (eventId);
        - el consumidor guarda los IDs que ya procesó (en la misma
          transacción que su cambio) y, si llega uno repetido, lo ignora.

        El ejemplo: el evento de la CAPA 5 llega dos veces, y los puntos del
        cliente se suman una sola vez.
    */
    static final class LoyaltyPointsConsumer {
        private final Set<String> processedEventIds = new HashSet<>();
        private final Map<String, Long> points = new HashMap<>();

        void on(OrderPlaced event) {
            if (!processedEventIds.add(event.eventId())) {
                return;                                     // ya lo procesé: repetido, se ignora
            }
            points.merge(event.customer(), event.totalCents() / 1_000, Long::sum);
        }

        long pointsOf(String customer) {
            return points.getOrDefault(customer, 0L);
        }
    }

    static boolean idempotentConsumerExample() {
        LoyaltyPointsConsumer consumer = new LoyaltyPointsConsumer();
        OrderPlaced event = new OrderPlaced("e1", 1, "ana", 25_000);
        consumer.on(event);
        consumer.on(event);                                 // la entrega repetida
        return consumer.pointsOf("ana") == 25;              // no 50
    }

    /*
        =================================================================
        CAPA 7) CLAVES DE IDEMPOTENCIA: REINTENTAR SIN COBRAR DOS VECES
        =================================================================

        El mismo problema, del lado de quien LLAMA. Una app manda "cobrar
        $250", y la respuesta no llega (se cortó la red). ¿Se cobró o no?
        Si reintenta, puede cobrar dos veces; si no, puede no haber cobrado.

        La solución que usan todos los proveedores de pagos serios: una
        CLAVE DE IDEMPOTENCIA. El cliente genera un identificador único por
        operación (no por intento) y lo manda en cada intento. El servidor
        guarda "clave -> resultado": si la clave ya existe, devuelve el MISMO
        resultado sin volver a ejecutar nada.

        Esto también resuelve el cobro de ServiceLayer, CAPA 6: si el pedido
        falla después de cobrar, reintentar el pedido completo con la misma
        clave NO cobra otra vez.

        (Otra técnica de los pagos: AUTORIZAR primero, apartando el dinero
        sin cobrarlo, y CAPTURAR después, cuando el pedido quedó guardado; si
        algo falla, la autorización se libera o vence.)
    */
    record ChargeResult(String chargeId, long cents) {
    }

    static final class PaymentProvider {
        private final Map<String, ChargeResult> byIdempotencyKey = new HashMap<>();
        int realCharges;

        ChargeResult charge(String idempotencyKey, long cents) {
            return byIdempotencyKey.computeIfAbsent(idempotencyKey, key -> {
                realCharges++;                              // solo la primera vez con esta clave
                return new ChargeResult("ch_" + realCharges, cents);
            });
        }
    }

    static boolean idempotencyKeyExample() {
        PaymentProvider provider = new PaymentProvider();
        String key = "pedido-1-pago";                       // una por OPERACIÓN, la misma en cada intento
        ChargeResult first = provider.charge(key, 25_000);  // la respuesta "se perdió en la red"
        ChargeResult retry = provider.charge(key, 25_000);  // reintento con la misma clave
        ChargeResult otherOrder = provider.charge("pedido-2-pago", 9_000);
        return first.equals(retry) && provider.realCharges == 2 && !otherOrder.equals(first);
    }

    /*
        =================================================================
        CAPA 8) SAGAS: TRANSACCIONES ENTRE SISTEMAS CON COMPENSACIONES
        =================================================================

        Hacer un pedido toca TRES sistemas: apartar inventario (almacén),
        cobrar (pagos) y programar el envío (logística). No hay una
        transacción que los abarque. Si el envío falla, ¿cómo se "deshace"
        lo anterior?

        Una SAGA es una secuencia de pasos locales, cada uno con su
        COMPENSACIÓN: una acción de negocio que deshace su efecto.

            paso                 compensación
            apartar inventario   liberar inventario
            cobrar               reembolsar
            programar envío      (último: si falla, no hay nada que deshacer)

        Si un paso falla, se ejecutan las compensaciones de los pasos ya
        hechos, EN ORDEN INVERSO. No es un rollback: el cobro SÍ pasó, y el
        cliente verá un cargo y un reembolso. Por eso las compensaciones
        son decisiones de negocio, no técnicas.

        Dos formas de coordinarla:
        - ORQUESTACIÓN: un coordinador dice a cada sistema qué hacer y lleva
          la cuenta (el ejemplo). Más fácil de seguir.
        - COREOGRAFÍA: cada sistema reacciona a los eventos de los otros
          ("pago hecho" -> logística programa). Sin centro, más difícil de
          seguir con muchos pasos.

        Las compensaciones también pueden fallar: se reintentan (por eso
        deben ser idempotentes, CAPA 6), y lo que no se resuelve queda para
        revisión humana.
    */
    record SagaStep(String name, Runnable action, Runnable compensation) {
    }

    static List<String> runSaga(List<SagaStep> steps, List<String> log) {
        List<SagaStep> done = new ArrayList<>();
        for (SagaStep step : steps) {
            try {
                step.action().run();
                log.add("hecho: " + step.name());
                done.add(step);
            } catch (RuntimeException e) {
                log.add("falló: " + step.name());
                for (int i = done.size() - 1; i >= 0; i--) { // compensar en orden INVERSO
                    done.get(i).compensation().run();
                    log.add("compensado: " + done.get(i).name());
                }
                return log;
            }
        }
        return log;
    }

    static boolean sagaExample() {
        Map<String, Integer> stock = new HashMap<>(Map.of("CAFE", 10));
        List<String> charges = new ArrayList<>();
        List<String> log = new ArrayList<>();

        runSaga(List.of(
                new SagaStep("apartar inventario",
                        () -> stock.merge("CAFE", -2, Integer::sum),
                        () -> stock.merge("CAFE", 2, Integer::sum)),
                new SagaStep("cobrar",
                        () -> charges.add("cargo 25000"),
                        () -> charges.add("reembolso 25000")),
                new SagaStep("programar envío",
                        () -> {
                            throw new IllegalStateException("sin cobertura en el código postal");
                        },
                        () -> { })), log);

        return log.equals(List.of("hecho: apartar inventario", "hecho: cobrar", "falló: programar envío",
                        "compensado: cobrar", "compensado: apartar inventario"))
                && stock.get("CAFE") == 10                                   // el inventario volvió
                && charges.equals(List.of("cargo 25000", "reembolso 25000")); // el cobro pasó y se reembolsó
    }

    /*
        =================================================================
        CAPA 9) EVENT SOURCING, EN UNA PÁGINA
        =================================================================

        Llevado al extremo, los eventos pueden ser LA fuente de verdad: en
        vez de guardar "el pedido está PAGADO, total 250", se guarda la
        lista de lo que pasó (PedidoCreado, LíneaAgregada, PedidoPagado), y
        el estado actual se reconstruye aplicándolos en orden (la idea de
        Command de v11_patterns/part3_behavioral/Command, CAPA 6).

        Lo que da: historia completa y auditable ("¿qué tenía este pedido el
        martes?"), y la posibilidad de crear vistas nuevas re-procesando
        todos los eventos desde el principio.

        Lo que cuesta: los eventos guardados no se pueden cambiar nunca (hay
        que versionarlos), reconstruir es más complejo (se guardan "fotos"
        periódicas), y casi obliga a usar CQRS para las consultas. Se usa en
        dominios donde la historia ES el negocio (contabilidad, banca,
        logística). Para el resto, guardar el estado y publicar eventos con
        outbox basta.
    */

    /*
        =================================================================
        CAPA 10) CUÁNDO USAR CADA COSA Y ERRORES COMUNES
        =================================================================

            Situación                                         Herramienta
            ------------------------------------------------  -----------------------
            consultas pesadas o con forma muy distinta         CQRS (modelo de lectura)
            a la escritura
            guardar y avisar a otro sistema                    outbox
            mensajes que pueden llegar repetidos (siempre)     consumidor idempotente
            llamadas que se reintentan (pagos, APIs)           clave de idempotencia
            una operación de negocio que toca varios sistemas  saga con compensaciones
            la historia es el negocio                          event sourcing

        CQRS NO es para todo: en una aplicación donde las pantallas
        muestran lo mismo que se guarda, dos modelos son el doble de trabajo
        sin ganancia. Un término medio frecuente: el mismo almacenamiento,
        pero consultas de lectura escritas aparte, que devuelven
        proyecciones (v11_patterns/part4_modern/DTO, CAPA 6) sin pasar por
        los agregados.

        Errores comunes:

        1) Consultas que cambian estado (CAPA 1).

        2) CQRS en un CRUD (CAPAS 2 y 10).

        3) Decisiones de negocio leyendo el modelo de lectura; interfaces
           que no consideran el retraso (CAPA 3).

        4) Guardar y publicar como dos pasos independientes (CAPA 4).

        5) Creer que la cola entrega "exactamente una vez"; consumidores que
           no toleran repetidos (CAPAS 5 y 6).

        6) Reintentar pagos o llamadas que cambian algo sin clave de
           idempotencia (CAPA 7).

        7) Tratar una saga como si fuera un rollback; compensaciones que no
           son idempotentes (CAPA 8).

        8) Event sourcing "porque suena bien" (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - CQS: un método cambia o pregunta. CQRS: modelos distintos para
          escribir (agregados) y leer (proyecciones listas).
        - El modelo de lectura va un poco atrás: consistencia eventual; las
          decisiones, con el lado de escritura.
        - Guardar y publicar son dos sistemas: con outbox el evento se guarda
          en la misma transacción y un relay lo publica.
        - Las entregas son "al menos una vez": consumidores idempotentes
          (ids procesados).
        - Reintentos seguros con claves de idempotencia.
        - Sagas: pasos con compensaciones en orden inverso, orquestadas o
          por coreografía.
        - Event sourcing: los eventos como fuente de verdad, solo donde la
          historia es el negocio.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    /** Simula que el proceso muere en este punto (un despliegue, un corte de luz). */
    private static void crashHere() {
        throw new ProcessCrashed();
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
