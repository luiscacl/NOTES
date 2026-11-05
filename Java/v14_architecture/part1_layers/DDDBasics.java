package v14_architecture.part1_layers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/*
    =====================================================================
    DDD: MODELAR EL NEGOCIO EN EL CÓDIGO
    =====================================================================

    Ruta de architecture/: guía 2 de 6  (part1_layers)
    Anterior:  part1_layers/LayeredArchitecture
    Siguiente: part2_boundaries/Hexagonal

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Lo que ya se vio: el repositorio por agregado (v11_patterns/
    part4_modern/Repository, CAPA 7), el dominio anémico (v11_patterns/
    part4_modern/ServiceLayer, CAPA 3), el Information Expert (v10_design/
    part3_responsibilities/GRASP) y los records (v2_oop/
    part4_advanced_types/Records).

    Importancia: ALTA. Los términos (entidad, value object, agregado,
    evento de dominio, contexto delimitado) se usan en todo equipo serio,
    y las ideas mejoran cualquier dominio con reglas, aunque no se haga
    "DDD completo".

    Índice:
        CAPA 1)  Qué es DDD
        CAPA 2)  El lenguaje ubicuo: el código habla como el negocio
        CAPA 3)  Value objects: valores con reglas
        CAPA 4)  Entidades: lo que tiene identidad
        CAPA 5)  Agregados: el límite de consistencia
        CAPA 6)  Reglas de los agregados
        CAPA 7)  Servicios de dominio
        CAPA 8)  Eventos de dominio
        CAPA 9)  Contextos delimitados: la misma palabra, modelos distintos
        CAPA 10) Cuándo usar DDD y errores comunes
        RESUMEN FINAL
*/
public class DDDBasics {

    public static void main(String[] args) {
        check(valueObjectExample(), "CAPA 3");
        check(entityExample(), "CAPA 4");
        check(aggregateExample(), "CAPA 5");
        check(domainServiceExample(), "CAPA 7");
        check(domainEventsExample(), "CAPA 8");
        check(boundedContextExample(), "CAPA 9");
        System.out.println("DDDBasics: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES DDD
        =================================================================

        DDD (Domain-Driven Design, "diseño guiado por el dominio") es el
        nombre del libro de Eric Evans (2003). Su tesis: en software de
        negocio, lo difícil no es la tecnología, es ENTENDER el negocio
        (el "dominio"), y el código debe ser un modelo fiel de ese
        entendimiento.

        Tiene dos partes:
        - ESTRATÉGICA: cómo dividir un sistema grande en partes con su
          propio modelo y su propio lenguaje (CAPAS 2 y 9). Es la parte más
          valiosa y la menos conocida.
        - TÁCTICA: los bloques para escribir el modelo: value objects,
          entidades, agregados, servicios y eventos de dominio,
          repositorios (CAPAS 3 a 8).

        No es un framework ni una tecnología: es una forma de pensar y de
        organizar el dominio. Vive en la capa de dominio de la arquitectura
        (part1_layers/LayeredArchitecture, CAPA 4): Java puro, sin base de
        datos ni HTTP.
    */

    /*
        =================================================================
        CAPA 2) EL LENGUAJE UBICUO: EL CÓDIGO HABLA COMO EL NEGOCIO
        =================================================================

        Si los de ventas dicen "apartar inventario" y el código dice
        updateStockFlag(sku, 2), cada conversación necesita un traductor,
        y en cada traducción se pierde algo. Así nacen los errores de
        "eso no era lo que pedimos".

        LENGUAJE UBICUO: un solo vocabulario, acordado con los expertos del
        negocio, usado en las conversaciones, en los documentos Y en el
        código (clases, métodos, eventos):

            mal:   order.setStatus(3); order.setPaidDate(now);
            bien:  order.pay(payment);           -> evento OrderPaid

        Si en el código aparece una palabra que el negocio no usa
        ("procesar", "manejar", "datos"), o el negocio usa una que el
        código no tiene ("pedido apartado"), hay un concepto mal entendido.
        Los nombres (v10_design/part4_clean_code/CleanCode) aquí no son
        estilo: son el modelo.
    */

    /*
        =================================================================
        CAPA 3) VALUE OBJECTS: VALORES CON REGLAS
        =================================================================

        Un VALUE OBJECT es un concepto que se define SOLO por su valor:
        dinero, una dirección de correo, un rango de fechas, una cantidad.
        Dos billetes de 100 pesos son intercambiables: no importa "cuál"
        es, solo cuánto vale.

        Sus características:
        - IGUALDAD POR VALOR: 100 MXN == 100 MXN;
        - INMUTABLE: "sumar" devuelve uno NUEVO;
        - SE VALIDA A SÍ MISMO al crearse: no existe un Money negativo ni un
          Email sin @;
        - TIENE COMPORTAMIENTO: las reglas de su concepto viven en él.

        En Java moderno son RECORDS (igualdad por valor e inmutabilidad
        gratis), con validación en el constructor compacto.

        El problema que resuelven, "obsesión por los primitivos": pasar
        long cents, String currency, String email por todos lados. Nada
        impide sumar pesos con dólares, pasar el correo donde iba el nombre
        o guardar un precio negativo. Con value objects, esos errores
        dejan de compilar o se rechazan en la frontera.
    */
    record Money(long cents, String currency) {
        Money {
            Objects.requireNonNull(currency, "currency");
            if (cents < 0) {
                throw new IllegalArgumentException("El dinero no puede ser negativo: " + cents);
            }
        }

        static Money mxn(long cents) {
            return new Money(cents, "MXN");
        }

        Money plus(Money other) {
            requireSameCurrency(other);
            return new Money(cents + other.cents, currency);   // nuevo: el original no cambia
        }

        Money times(int quantity) {
            return new Money(cents * quantity, currency);
        }

        boolean isGreaterThan(Money other) {
            requireSameCurrency(other);
            return cents > other.cents;
        }

        private void requireSameCurrency(Money other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("No se pueden mezclar " + currency + " y " + other.currency);
            }
        }
    }

    record Email(String value) {
        Email {
            value = Objects.requireNonNull(value, "value").strip().toLowerCase(Locale.ROOT);
            if (!value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                throw new IllegalArgumentException("Correo inválido: " + value);
            }
        }
    }

    static boolean valueObjectExample() {
        boolean equalByValue = Money.mxn(10_000).equals(new Money(10_000, "MXN"));
        boolean immutable = Money.mxn(100).plus(Money.mxn(50)).equals(Money.mxn(150));
        boolean mixingRejected = rejects(() -> Money.mxn(100).plus(new Money(100, "USD")));
        boolean negativeRejected = rejects(() -> Money.mxn(-1));
        boolean emailNormalized = new Email("  Ana@Correo.COM ").equals(new Email("ana@correo.com"));
        boolean badEmailRejected = rejects(() -> new Email("ana.correo.com"));
        return equalByValue && immutable && mixingRejected && negativeRejected && emailNormalized && badEmailRejected;
    }

    /*
        =================================================================
        CAPA 4) ENTIDADES: LO QUE TIENE IDENTIDAD
        =================================================================

        Una ENTIDAD es algo que se distingue por su IDENTIDAD, no por sus
        datos, y que CAMBIA a lo largo del tiempo sin dejar de ser ella: un
        cliente que cambia de correo sigue siendo el mismo cliente; dos
        clientes llamados "Ana López" son personas distintas.

        Por eso:
        - tiene un ID estable (asignado al crearla);
        - equals y hashCode por ID, no por sus datos (v2_oop/
          part4_advanced_types/ObjectContract): el mismo cliente antes y
          después de cambiar su correo es "igual";
        - su estado cambia por MÉTODOS CON NOMBRE DEL NEGOCIO que validan
          (changeEmail, deactivate), no por setters sueltos.

        Las entidades suelen ser clases (mutables, con cuidado); los value
        objects, records. Una entidad USA value objects para sus datos:
        Customer tiene un Email, no un String.
    */
    static final class Customer {
        private final long id;                              // la identidad: nunca cambia
        private Email email;
        private boolean active = true;

        Customer(long id, Email email) {
            this.id = id;
            this.email = email;
        }

        void changeEmail(Email newEmail) {
            if (!active) {
                throw new IllegalStateException("Un cliente dado de baja no puede cambiar su correo");
            }
            this.email = newEmail;
        }

        void deactivate() {
            active = false;
        }

        Email email() {
            return email;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Customer customer && customer.id == id;   // por identidad
        }

        @Override
        public int hashCode() {
            return Long.hashCode(id);
        }
    }

    static boolean entityExample() {
        Customer ana = new Customer(7, new Email("ana@correo.com"));
        Customer sameAnaLoadedAgain = new Customer(7, new Email("ana@correo.com"));
        Customer otherAna = new Customer(8, new Email("ana@correo.com"));
        ana.changeEmail(new Email("ana@nuevo.com"));
        boolean stillTheSame = ana.equals(sameAnaLoadedAgain);   // cambió su correo, sigue siendo ella
        boolean differentPeople = !sameAnaLoadedAgain.equals(otherAna);   // mismos datos, otra persona
        ana.deactivate();
        boolean ruleEnforced = rejects(() -> ana.changeEmail(new Email("otra@correo.com")));
        return stillTheSame && differentPeople && ruleEnforced;
    }

    /*
        =================================================================
        CAPA 5) AGREGADOS: EL LÍMITE DE CONSISTENCIA
        =================================================================

        Un pedido tiene líneas. Las reglas del negocio involucran a TODO el
        conjunto:
        - máximo 20 líneas por pedido;
        - el total es la suma de las líneas;
        - un pedido pagado ya no se modifica.

        Si cualquier parte del código pudiera agregar una línea
        directamente a la lista, o cambiar una cantidad, esas reglas se
        romperían desde algún rincón.

        Un AGREGADO es un grupo de objetos que se trata como UNA unidad para
        los cambios, con una RAÍZ (aquí, Order) que es la única puerta de
        entrada:
        - desde afuera solo se habla con la raíz: order.addLine(...), nunca
          order.lines().add(...);
        - la raíz revisa TODAS las reglas del grupo en cada cambio;
        - lo que devuelve hacia afuera no permite modificar su interior
          (una lista no modificable, value objects inmutables).

        Así, las reglas del pedido se cumplen SIEMPRE, sin importar quién lo
        use. Es el encapsulamiento (v2_oop/part2_pillars/) aplicado a un
        grupo de objetos.
    */
    record OrderLine(String sku, int quantity, Money unitPrice) {
        OrderLine {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Cantidad inválida: " + quantity);
            }
        }

        Money subtotal() {
            return unitPrice.times(quantity);
        }
    }

    enum OrderStatus { OPEN, PAID }

    sealed interface OrderEvent permits OrderPaid {
    }

    record OrderPaid(long orderId, Money total) implements OrderEvent {
    }

    static final class Order {
        static final int MAX_LINES = 20;

        private final long id;
        private final long customerId;                      // otro agregado: por ID, no el objeto (CAPA 6)
        private final List<OrderLine> lines = new ArrayList<>();
        private final List<OrderEvent> events = new ArrayList<>();   // CAPA 8
        private OrderStatus status = OrderStatus.OPEN;

        Order(long id, long customerId) {
            this.id = id;
            this.customerId = customerId;
        }

        void addLine(String sku, int quantity, Money unitPrice) {
            requireOpen();
            if (lines.size() == MAX_LINES) {
                throw new IllegalStateException("Un pedido tiene máximo " + MAX_LINES + " líneas");
            }
            lines.add(new OrderLine(sku, quantity, unitPrice));
        }

        void pay(Money amount) {
            requireOpen();
            if (lines.isEmpty()) {
                throw new IllegalStateException("No se puede pagar un pedido vacío");
            }
            if (!amount.equals(total())) {
                throw new IllegalArgumentException("El pago no cubre el total: " + total().cents());
            }
            status = OrderStatus.PAID;
            events.add(new OrderPaid(id, amount));          // "pasó algo importante" (CAPA 8)
        }

        Money total() {
            return lines.stream().map(OrderLine::subtotal).reduce(Money.mxn(0), Money::plus);
        }

        List<OrderLine> lines() {
            return Collections.unmodifiableList(lines);     // ver sí; cambiar, solo por la raíz
        }

        long customerId() {
            return customerId;
        }

        OrderStatus status() {
            return status;
        }

        List<OrderEvent> pullEvents() {                     // CAPA 8
            List<OrderEvent> pending = List.copyOf(events);
            events.clear();
            return pending;
        }

        private void requireOpen() {
            if (status != OrderStatus.OPEN) {
                throw new IllegalStateException("El pedido " + id + " ya está pagado");
            }
        }
    }

    static boolean aggregateExample() {
        Order order = new Order(1, 7);
        order.addLine("CAFE", 2, Money.mxn(4_500));
        order.addLine("TAZA", 1, Money.mxn(12_000));

        boolean totalOk = order.total().equals(Money.mxn(21_000));
        boolean noBackdoor = rejects(() -> order.lines().add(new OrderLine("GRATIS", 1, Money.mxn(0))));
        boolean wrongPayment = rejects(() -> order.pay(Money.mxn(100)));
        order.pay(Money.mxn(21_000));
        boolean frozenAfterPaid = rejects(() -> order.addLine("FILTRO", 1, Money.mxn(3_000)));

        Order big = new Order(2, 7);
        for (int i = 0; i < Order.MAX_LINES; i++) {
            big.addLine("SKU-" + i, 1, Money.mxn(100));
        }
        boolean limitEnforced = rejects(() -> big.addLine("UNA-MAS", 1, Money.mxn(100)));
        return totalOk && noBackdoor && wrongPayment && frozenAfterPaid && limitEnforced
                && order.status() == OrderStatus.PAID;
    }

    /*
        =================================================================
        CAPA 6) REGLAS DE LOS AGREGADOS
        =================================================================

        Las reglas de Vaughn Vernon, las más citadas:

        1) PROTEGER LAS REGLAS DENTRO DEL LÍMITE: todo lo que debe ser
           consistente AL INSTANTE va dentro del mismo agregado (CAPA 5).

        2) AGREGADOS PEQUEÑOS: un agregado se carga y se guarda COMPLETO. Un
           "Cliente" que contiene todos sus pedidos de 10 años se vuelve
           lento de cargar y, con varios usuarios, se pelean por él
           (bloqueos, conflictos). Ante la duda, más pequeño.

        3) REFERENCIAR OTROS AGREGADOS POR ID: Order guarda customerId, no
           un objeto Customer. Así no se cargan grafos enormes, y queda
           claro que el pedido no puede cambiar al cliente.

        4) UN AGREGADO POR TRANSACCIÓN: un caso de uso modifica UN agregado
           y lo guarda. Si "pagar el pedido" también debe "sumar puntos al
           cliente", el cliente se actualiza DESPUÉS, reaccionando a un
           evento (CAPA 8): eso es "consistencia eventual" (part3_systems/
           CommandsQueriesAndConsistency).

        Y de ahí sale el repositorio: UNO por agregado, que guarda y carga
        el agregado completo (v11_patterns/part4_modern/Repository, CAPA 7).
    */

    /*
        =================================================================
        CAPA 7) SERVICIOS DE DOMINIO
        =================================================================

        Algunas reglas del negocio no son naturales en NINGUNA entidad ni
        value object, porque involucran a varios: "¿cuánto envío paga este
        pedido según la zona del cliente y la promoción vigente?". Meterla
        en Order lo obliga a saber de zonas; en Customer, de pedidos.

        Un SERVICIO DE DOMINIO es una clase SIN ESTADO, con nombre del
        negocio, que contiene esa regla:
        - vive en la capa de dominio y habla en términos del dominio
          (recibe Order y Money, no DTOs ni filas);
        - NO es el servicio de aplicación (v11_patterns/part4_modern/
          ServiceLayer): ese coordina el caso de uso (cargar, guardar,
          transacción); este contiene una REGLA.

        Cuidado con el exceso: si todo se vuelve "servicio", el dominio
        queda anémico otra vez (ServiceLayer, CAPA 3). Primero se intenta
        poner la regla en la entidad o el value object.
    */
    record ShippingZone(String name, Money baseCost) {
    }

    static final class ShippingPolicy {                     // servicio de dominio: una regla, sin estado
        private static final Money FREE_SHIPPING_FROM = Money.mxn(50_000);

        Money shippingFor(Order order, ShippingZone zone) {
            return order.total().isGreaterThan(FREE_SHIPPING_FROM) ? Money.mxn(0) : zone.baseCost();
        }
    }

    static boolean domainServiceExample() {
        ShippingPolicy policy = new ShippingPolicy();
        ShippingZone north = new ShippingZone("Norte", Money.mxn(9_900));
        Order small = new Order(3, 7);
        small.addLine("CAFE", 1, Money.mxn(4_500));
        Order large = new Order(4, 7);
        large.addLine("CAFETERA", 1, Money.mxn(80_000));
        return policy.shippingFor(small, north).equals(Money.mxn(9_900))
                && policy.shippingFor(large, north).equals(Money.mxn(0));
    }

    /*
        =================================================================
        CAPA 8) EVENTOS DE DOMINIO
        =================================================================

        Un EVENTO DE DOMINIO es un hecho del negocio que YA PASÓ, nombrado
        en pasado y en el lenguaje ubicuo: OrderPaid, CustomerRegistered,
        StockReserved. Es un record inmutable con lo necesario para
        reaccionar (v11_patterns/part3_behavioral/Observer, CAPA 3).

        Para qué:
        - DESACOPLAR: Order no sabe que al pagarse hay que mandar un correo,
          sumar puntos y avisar al almacén. Solo registra "me pagaron".
          Quien le interese, reacciona;
        - respetar "un agregado por transacción" (CAPA 6): los otros
          agregados se actualizan reaccionando al evento.

        La forma más común: el agregado ACUMULA sus eventos al cambiar
        (Order.pay agrega OrderPaid), y el servicio de aplicación, DESPUÉS
        de guardar, los saca (pullEvents) y los publica. Publicarlos antes
        de guardar arriesga avisar de algo que luego falló (ServiceLayer,
        CAPA 6; y la técnica "outbox" en part3_systems/).
    */
    static boolean domainEventsExample() {
        Order order = new Order(5, 7);
        order.addLine("CAFE", 2, Money.mxn(4_500));
        boolean nothingYet = order.pullEvents().isEmpty();
        order.pay(Money.mxn(9_000));

        List<String> reactions = new ArrayList<>();         // "el servicio de aplicación publica"
        for (OrderEvent event : order.pullEvents()) {
            switch (event) {
                case OrderPaid(long orderId, Money total) -> {
                    reactions.add("correo de confirmación del pedido " + orderId);
                    reactions.add("sumar " + total.cents() / 1_000 + " puntos");
                }
            }
        }
        return nothingYet
                && reactions.equals(List.of("correo de confirmación del pedido 5", "sumar 9 puntos"))
                && order.pullEvents().isEmpty();            // ya se publicaron: no se repiten
    }

    /*
        =================================================================
        CAPA 9) CONTEXTOS DELIMITADOS: LA MISMA PALABRA, MODELOS DISTINTOS
        =================================================================

        La idea más importante de DDD, y la que más se ignora. "Producto"
        significa cosas distintas según quién hable:

            CATÁLOGO     nombre, descripción, fotos, categoría
            ALMACÉN      ubicación, peso, existencias, lote
            FACTURACIÓN  clave fiscal, tasa de impuesto, precio

        El error clásico: UNA clase Product con los 40 campos de todos,
        que cada área modifica y que nadie entiende completa. Cada cambio
        para el almacén arriesga romper la facturación.

        Un CONTEXTO DELIMITADO (bounded context) es una frontera dentro de
        la cual un modelo y su lenguaje tienen UN significado preciso.
        Cada contexto tiene SU Product, con solo lo que le importa, y se
        comunican por identificadores y mensajes, no compartiendo clases.

        Cuando un contexto recibe datos de otro (o de un sistema viejo o
        externo), los TRADUCE a su propio modelo en la frontera, para que
        el modelo ajeno no "contamine" el suyo. A esa traducción se le llama
        CAPA ANTICORRUPCIÓN (es un Adapter: v11_patterns/part2_structural/
        Adapter).

        Los contextos delimitados son también la mejor guía para dividir un
        sistema en módulos o en microservicios (part3_systems/
        MonolithVsMicroservices): un servicio por contexto, no uno por
        tabla.

        El ejemplo: el almacén recibe el producto "como lo manda el
        catálogo" (un mapa con sus nombres) y lo traduce a su propio
        modelo, quedándose solo con lo que le importa.
    */
    record WarehouseItem(String sku, int weightGrams, String location) {    // el Product del almacén
    }

    static final class CatalogTranslator {                  // capa anticorrupción del almacén
        WarehouseItem fromCatalog(Map<String, String> catalogProduct, String assignedLocation) {
            return new WarehouseItem(
                    catalogProduct.get("productCode"),      // el catálogo lo llama "productCode"
                    (int) Math.round(Double.parseDouble(catalogProduct.get("weightKg")) * 1_000),   // kg -> gramos
                    assignedLocation);                      // descripción, fotos, categoría: no le importan
        }
    }

    static boolean boundedContextExample() {
        Map<String, String> fromCatalog = Map.of(
                "productCode", "CAFE-500",
                "title", "Café de Chiapas 500 g",
                "photoUrl", "https://cdn/cafe.jpg",
                "weightKg", "0.52");
        WarehouseItem item = new CatalogTranslator().fromCatalog(fromCatalog, "Pasillo 4, nivel 2");
        return item.equals(new WarehouseItem("CAFE-500", 520, "Pasillo 4, nivel 2"));
    }

    /*
        =================================================================
        CAPA 10) CUÁNDO USAR DDD Y ERRORES COMUNES
        =================================================================

        DDD COMPLETO (contextos, agregados, eventos, expertos del negocio
        en el equipo) vale la pena cuando el dominio es COMPLEJO y es la
        ventaja del negocio: seguros, logística, pagos, reservaciones. Para
        un CRUD de catálogos es exceso.

        Lo que conviene SIEMPRE, aunque sea un sistema simple: el lenguaje
        del negocio en el código, value objects en vez de primitivos, y
        reglas dentro de los objetos en vez de en servicios.

        Errores comunes:

        1) Nombres técnicos donde el negocio tiene palabras propias
           (CAPA 2).

        2) Obsesión por los primitivos: long cents y String email por todos
           lados (CAPA 3).

        3) Entidades con setters públicos para todo; equals por datos en vez
           de por identidad (CAPA 4).

        4) Agregados que dejan modificar su interior desde afuera (CAPA 5).

        5) Agregados gigantes; referencias a otros agregados por objeto;
           modificar varios agregados en una transacción (CAPA 6).

        6) Todo en "servicios de dominio": el dominio anémico de nuevo
           (CAPA 7).

        7) Publicar eventos antes de guardar; eventos en presente o con
           nombres técnicos (CAPA 8).

        8) Un solo modelo gigante compartido por todas las áreas (CAPA 9).

        9) Aplicar todo DDD a un CRUD (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - DDD: el código como modelo fiel del negocio; parte estratégica
          (lenguaje, contextos) y táctica (los bloques).
        - Lenguaje ubicuo: las mismas palabras en conversación y código.
        - Value object: igualdad por valor, inmutable, se valida solo, con
          comportamiento (records).
        - Entidad: identidad estable, equals por id, cambios por métodos del
          negocio.
        - Agregado: grupo con una raíz que protege todas sus reglas;
          pequeño, referencias por id, uno por transacción, un repositorio.
        - Servicio de dominio: una regla sin hogar natural, sin estado.
        - Evento de dominio: un hecho en pasado; se publica después de
          guardar.
        - Contextos delimitados: cada área con su modelo; traducción en la
          frontera (anticorrupción).
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static boolean rejects(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            return true;
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
