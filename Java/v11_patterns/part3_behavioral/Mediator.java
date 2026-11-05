package v11_patterns.part3_behavioral;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/*
    =====================================================================
    MEDIATOR: UN COORDINADOR EN VEZ DE TODOS HABLANDO CON TODOS
    =====================================================================

    Ruta de patterns/: guía 17 de 28  (part3_behavioral)
    Anterior:  part3_behavioral/Iterator
    Siguiente: part3_behavioral/Memento

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: MEDIA. Aparece en formularios e interfaces con muchas
    piezas que reaccionan entre sí, y su versión moderna (un bus de
    eventos dentro de la aplicación) se usa mucho.

    Índice:
        CAPA 1)  El problema: todos conectados con todos
        CAPA 2)  La solución: un mediador en el centro
        CAPA 3)  Por qué las piezas quedan más simples
        CAPA 4)  La versión moderna: un bus de eventos
        CAPA 5)  El riesgo: el mediador que lo sabe todo
        CAPA 6)  Mediator, Observer y Facade
        CAPA 7)  Errores comunes
        RESUMEN FINAL
*/
public class Mediator {

    public static void main(String[] args) {
        check(mediatorExample(), "CAPA 2");
        check(isolatedComponentExample(), "CAPA 3");
        check(eventBusExample(), "CAPA 4");
        System.out.println("Mediator: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: TODOS CONECTADOS CON TODOS
        =================================================================

        La pantalla de checkout tiene varias piezas que reaccionan entre
        sí:
        - cambiar el PAÍS cambia los MÉTODOS DE ENVÍO disponibles;
        - elegir un MÉTODO DE ENVÍO cambia el TOTAL;
        - aplicar un CUPÓN cambia el TOTAL;
        - el TOTAL y el envío elegido deciden si el BOTÓN DE PAGAR se
          habilita.

        Si cada pieza llama directamente a las que afecta, cada una guarda
        referencias a varias otras:

            PaísSelector ──> EnvíoSelector ──> Resumen ──> BotónPagar
                 └────────────────────────────────┘          ^
            CupónCampo ──────────────────────────> Resumen ───┘

        Con N piezas puede haber hasta N x (N - 1) conexiones. Problemas:
        - Para entender qué pasa al cambiar el país hay que seguir llamadas
          de pieza en pieza por varios archivos.
        - Ninguna pieza se puede REUSAR en otra pantalla: el selector de país
          "sabe" del envío del checkout.
        - Probar una pieza exige armar todas las demás.
        - Una regla nueva ("si el envío es a tienda, el botón dice
          Reservar") obliga a tocar varias piezas.
    */

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: UN MEDIADOR EN EL CENTRO
        =================================================================

        MEDIATOR: "Define un objeto que encapsula cómo interactúa un
        conjunto de objetos. Promueve el bajo acoplamiento evitando que los
        objetos se refieran unos a otros explícitamente."

        - Cada pieza conoce SOLO al mediador. Cuando algo le pasa, le
          AVISA: "cambié" (y nada más).
        - El mediador conoce a todas las piezas y DECIDE qué hacer: a
          quién actualizar y cómo.

                       PaísSelector   CupónCampo
                              \          /
            EnvíoSelector ── CheckoutMediator ── BotónPagar
                                  |
                               Resumen

        De una red a una ESTRELLA. Las reglas de coordinación ("el país
        decide los envíos", "el botón se habilita si...") quedan en UN
        lugar que se lee de corrido.

        Como una torre de control: los aviones no se coordinan entre sí;
        cada uno habla con la torre, y la torre decide quién aterriza.
    */
    interface CheckoutMediator {
        void changed(Object component);                     // lo ÚNICO que las piezas saben hacer: avisar
    }

    static final class CountrySelector {
        private final CheckoutMediator mediator;
        private String country = "";

        CountrySelector(CheckoutMediator mediator) {
            this.mediator = mediator;
        }

        void select(String country) {
            this.country = country;
            mediator.changed(this);                         // no sabe a quién le importa
        }

        String country() {
            return country;
        }
    }

    static final class ShippingSelector {
        private final CheckoutMediator mediator;
        private Map<String, BigDecimal> options = Map.of();
        private String chosen;

        ShippingSelector(CheckoutMediator mediator) {
            this.mediator = mediator;
        }

        void showOptions(Map<String, BigDecimal> options) { // el mediador le dice qué mostrar
            this.options = options;
            // Revisar null ANTES: Map.of(...).containsKey(null) lanza NullPointerException
            // (v5_collections/part4_tools/ImmutableCollections, CAPA 4). Este error salió al probar la guía.
            if (chosen != null && !options.containsKey(chosen)) {
                chosen = null;                              // la opción elegida ya no existe
            }
        }

        void choose(String option) {
            if (options.containsKey(option)) {
                chosen = option;
                mediator.changed(this);
            }
        }

        BigDecimal cost() {
            return chosen == null ? BigDecimal.ZERO : options.get(chosen);
        }

        boolean hasChoice() {
            return chosen != null;
        }

        List<String> options() {
            return options.keySet().stream().sorted().toList();
        }
    }

    static final class OrderSummary {
        private final BigDecimal subtotal;
        private BigDecimal total;

        OrderSummary(BigDecimal subtotal) {
            this.subtotal = subtotal;
            this.total = subtotal;
        }

        void recalculate(BigDecimal shipping) {
            total = subtotal.add(shipping);
        }

        BigDecimal total() {
            return total;
        }
    }

    static final class PayButton {
        private boolean enabled;

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        boolean enabled() {
            return enabled;
        }
    }

    static final class Checkout implements CheckoutMediator {   // CONCRETE MEDIATOR: las reglas de coordinación
        private static final Map<String, Map<String, BigDecimal>> SHIPPING_BY_COUNTRY = Map.of(
                "MX", Map.of("estándar", new BigDecimal("99"), "exprés", new BigDecimal("249")),
                "CO", Map.of("estándar", new BigDecimal("150")));

        final CountrySelector country = new CountrySelector(this);
        final ShippingSelector shipping = new ShippingSelector(this);
        final OrderSummary summary;
        final PayButton payButton = new PayButton();

        Checkout(BigDecimal subtotal) {
            this.summary = new OrderSummary(subtotal);
        }

        @Override
        public void changed(Object component) {
            if (component == country) {                     // el país decide los envíos disponibles
                shipping.showOptions(SHIPPING_BY_COUNTRY.getOrDefault(country.country(), Map.of()));
            }
            summary.recalculate(shipping.cost());           // cualquier cambio: recalcular y revisar el botón
            payButton.setEnabled(shipping.hasChoice());
        }
    }

    static boolean mediatorExample() {
        Checkout checkout = new Checkout(new BigDecimal("1000"));

        checkout.country.select("MX");
        boolean optionsForMexico = checkout.shipping.options().equals(List.of("estándar", "exprés"))
                && !checkout.payButton.enabled();           // aún no eligió envío

        checkout.shipping.choose("exprés");
        boolean totalAndButton = checkout.summary.total().compareTo(new BigDecimal("1249")) == 0
                && checkout.payButton.enabled();

        checkout.country.select("CO");                      // el exprés no existe en Colombia
        boolean resetOnCountryChange = checkout.shipping.options().equals(List.of("estándar"))
                && !checkout.payButton.enabled()
                && checkout.summary.total().compareTo(new BigDecimal("1000")) == 0;

        return optionsForMexico && totalAndButton && resetOnCountryChange;
    }

    /*
        =================================================================
        CAPA 3) POR QUÉ LAS PIEZAS QUEDAN MÁS SIMPLES
        =================================================================

        Cada pieza ahora:
        - depende de UNA interface pequeña (CheckoutMediator), no de las
          otras piezas;
        - se puede REUSAR en otra pantalla con otro mediador (el selector de
          país sirve igual en el formulario de registro);
        - se PRUEBA sola, con un mediador falso que solo anota los avisos.

        El ejemplo prueba el selector de envío sin ninguna otra pieza: solo
        un mediador de prueba (una lambda que anota quién avisó).
    */
    static boolean isolatedComponentExample() {
        List<Object> notifications = new ArrayList<>();
        ShippingSelector selector = new ShippingSelector(notifications::add);   // mediador de prueba
        selector.showOptions(Map.of("estándar", new BigDecimal("99")));
        selector.choose("exprés");                          // no existe: no avisa
        selector.choose("estándar");                        // existe: avisa
        return notifications.size() == 1 && notifications.get(0) == selector;
    }

    /*
        =================================================================
        CAPA 4) LA VERSIÓN MODERNA: UN BUS DE EVENTOS
        =================================================================

        En lugar de un mediador que conoce a cada pieza y tiene un gran
        "if de quién cambió", muchas aplicaciones usan un BUS DE EVENTOS
        dentro del programa:
        - quien hace algo PUBLICA un evento (un record que describe lo que
          pasó: OrderPlaced);
        - quien quiere reaccionar se SUSCRIBE a ese tipo de evento;
        - el bus entrega cada evento a sus suscriptores.

        El bus es el mediador, pero GENÉRICO: no contiene reglas de
        negocio, solo reparte. Quien publica "pedido creado" no sabe que el
        inventario descuenta existencias, que se envía un correo y que se
        suman puntos de lealtad. Agregar una reacción nueva es suscribir
        otra, sin tocar al que publica (v10_design/part2_solid/OpenClosed).

        Spring trae uno (publicar y escuchar eventos de la aplicación;
        v15_spring/). Llevado a varios sistemas, la misma idea son las colas de
        mensajes entre servicios (v14_architecture/).
    */
    record OrderPlaced(String orderId, String customer, BigDecimal total) { }

    record OrderCancelled(String orderId) { }

    static final class EventBus {
        private final Map<Class<?>, List<Consumer<Object>>> subscribers = new HashMap<>();

        <E> void subscribe(Class<E> eventType, Consumer<E> handler) {
            subscribers.computeIfAbsent(eventType, type -> new ArrayList<>())
                    .add(event -> handler.accept(eventType.cast(event)));   // cast seguro: solo recibe su tipo
        }

        void publish(Object event) {
            subscribers.getOrDefault(event.getClass(), List.of()).forEach(handler -> handler.accept(event));
        }
    }

    static boolean eventBusExample() {
        EventBus bus = new EventBus();
        List<String> effects = new ArrayList<>();

        bus.subscribe(OrderPlaced.class, event -> effects.add("inventario: descontar " + event.orderId()));
        bus.subscribe(OrderPlaced.class, event -> effects.add("correo a " + event.customer()));
        bus.subscribe(OrderPlaced.class, event -> effects.add("puntos: +" + event.total().intValue() / 100));
        bus.subscribe(OrderCancelled.class, event -> effects.add("inventario: regresar " + event.orderId()));

        bus.publish(new OrderPlaced("A-7", "ana", new BigDecimal("1250")));   // quien publica no conoce a nadie
        bus.publish(new OrderCancelled("A-3"));

        return effects.equals(List.of("inventario: descontar A-7", "correo a ana", "puntos: +12", "inventario: regresar A-3"));
    }

    /*
        =================================================================
        CAPA 5) EL RIESGO: EL MEDIADOR QUE LO SABE TODO
        =================================================================

        Al concentrar la coordinación, el mediador puede crecer hasta
        convertirse en un "objeto dios": cientos de líneas de if, todas las
        reglas de la pantalla, imposible de cambiar sin miedo. Es el mismo
        problema que se quería resolver, ahora en una sola clase.

        Cómo evitarlo:
        - Un mediador por PANTALLA o caso de uso, no uno para toda la
          aplicación.
        - Que coordine, no que calcule: las REGLAS (cuánto cuesta el envío,
          cuándo aplica un cupón) viven en las piezas o en el dominio; el
          mediador solo decide quién se entera de qué (part2_structural/
          Facade, CAPA 3: la misma idea).
        - Cuando las reacciones son independientes entre sí, un bus de
          eventos (CAPA 4) reparte sin acumular reglas.

        El costo opuesto del bus: el flujo se vuelve INDIRECTO. Leyendo el
        código que publica no se ve qué pasa después; hay que buscar quién
        está suscrito. Con muchos eventos, eso también se vuelve difícil de
        seguir. Usa nombres de eventos claros y pocos niveles (que un
        suscriptor publique otro evento que dispara otro...).
    */

    /*
        =================================================================
        CAPA 6) MEDIATOR, OBSERVER Y FACADE
        =================================================================

        | Patrón   | Quién conoce a quién                     | Para qué                           |
        |----------|------------------------------------------|------------------------------------|
        | Mediator | las piezas conocen al mediador; él a     | coordinar piezas que interactúan   |
        |          | todas; las piezas no se conocen          | ENTRE SÍ                           |
        | Observer | el observado tiene una lista de          | avisar de un cambio a quien le     |
        |          | interesados y les avisa directamente     | interese (part3_behavioral/        |
        |          |                                          | Observer)                          |
        | Facade   | la fachada conoce al subsistema; el      | SIMPLIFICAR el uso desde afuera    |
        |          | subsistema no conoce a la fachada        | (part2_structural/Facade)          |

        El bus de eventos combina las dos primeras: es un mediador genérico
        construido con la mecánica de Observer.
    */

    /*
        =================================================================
        CAPA 7) ERRORES COMUNES
        =================================================================

        1) Piezas que se llaman directamente entre sí formando una red
           (CAPA 1).

        2) Piezas que, además de avisar al mediador, siguen guardando
           referencias a otras piezas: se pierde todo el beneficio
           (CAPA 2).

        3) Un mediador gigante para toda la aplicación, con las reglas de
           negocio adentro (CAPA 5).

        4) Cadenas de eventos que disparan eventos que disparan eventos:
           nadie puede seguir el flujo (CAPA 5).

        5) Usar un bus para cosas que son una simple llamada directa entre
           dos objetos: indirección sin beneficio.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Mediator: las piezas no se hablan entre sí; avisan a un mediador,
          que decide quién reacciona. De una red a una estrella.
        - Piezas más simples: dependen de una interface pequeña, se reusan
          y se prueban solas.
        - Versión moderna: un bus de eventos (publicar/suscribir) dentro de
          la aplicación; quien publica no conoce a quien reacciona.
        - Riesgo: el mediador que lo sabe todo; uno por pantalla o caso de
          uso, y que coordine sin contener las reglas.
        - El bus agrega indirección: eventos claros y pocos niveles.
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
