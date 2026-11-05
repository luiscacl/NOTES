package v11_patterns.part3_behavioral;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/*
    =====================================================================
    STATE: UN OBJETO QUE SE COMPORTA DISTINTO SEGÚN SU ESTADO
    =====================================================================

    Ruta de patterns/: guía 20 de 28  (part3_behavioral)
    Anterior:  part3_behavioral/Observer
    Siguiente: part3_behavioral/Strategy

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: MEDIA-ALTA. Pedidos, pagos, suscripciones, tickets de
    soporte, envíos: casi todo objeto de negocio tiene un ciclo de vida.

    Qué se da por sabido: el enum OrderStatus de v2_oop/
    part4_advanced_types/Enums, CAPA 3, que valida qué TRANSICIONES son
    posibles. Aquí se da el paso siguiente: cuando lo que cambia según el
    estado es el COMPORTAMIENTO de cada operación.

    Índice:
        CAPA 1)  El problema: el mismo switch de estados en cada método
        CAPA 2)  La solución clásica: una clase por estado
        CAPA 3)  Forma compacta: un enum con comportamiento por constante
        CAPA 4)  Forma moderna: estados con datos (sealed + records)
        CAPA 5)  Transiciones: explícitas, en un lugar, y que avisen
        CAPA 6)  Guardar el estado
        CAPA 7)  State y Strategy
        CAPA 8)  Cuándo no hace falta
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class State {

    public static void main(String[] args) {
        check(switchEverywhereExample(), "CAPA 1");
        check(stateClassesExample(), "CAPA 2");
        check(enumStateExample(), "CAPA 3");
        check(sealedStateExample(), "CAPA 4");
        check(transitionsExample(), "CAPA 5");
        System.out.println("State: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: EL MISMO SWITCH DE ESTADOS EN CADA MÉTODO
        =================================================================

        Una suscripción mensual al servicio de envíos gratis de la tienda
        pasa por estos estados:

            PRUEBA ──(paga)──> ACTIVA ──(falla el cobro)──> PAGO_VENCIDO
              │                  │ ^                              │
              │                  │ └──────(paga)──────────────────┘
              └──(cancela)──> CANCELADA <──(cancela / 3 fallos)───┘

        Y cada operación depende del estado:
        - ¿puede usar el envío gratis?  sí en PRUEBA y ACTIVA; en
          PAGO_VENCIDO todavía sí (periodo de gracia); en CANCELADA no.
        - ¿cuánto se cobra este mes?    0 en PRUEBA; el precio en ACTIVA;
          el precio + recargo en PAGO_VENCIDO; nada en CANCELADA.
        - ¿qué pasa si falla un cobro?  en ACTIVA pasa a PAGO_VENCIDO; en
          PAGO_VENCIDO suma un intento y al tercero se cancela...

        Escrito con un campo de estado, CADA método tiene su switch sobre el
        estado. Consecuencias:
        - Un estado nuevo (PAUSADA) obliga a revisar TODOS los métodos.
        - Las reglas de un estado están repartidas en todos los métodos:
          para saber "qué pasa en PAGO_VENCIDO" hay que leer el archivo
          entero.
        - Es fácil que un método trate un estado distinto que otro.
    */
    static final class SubscriptionWithSwitches {
        enum Status { TRIAL, ACTIVE, PAST_DUE, CANCELLED }

        private Status status = Status.TRIAL;
        private int failedCharges;

        boolean canUseFreeShipping() {
            return switch (status) {                        // switch #1
                case TRIAL, ACTIVE, PAST_DUE -> true;
                case CANCELLED -> false;
            };
        }

        BigDecimal monthlyCharge() {
            return switch (status) {                        // switch #2: las mismas cuatro ramas otra vez
                case TRIAL, CANCELLED -> BigDecimal.ZERO;
                case ACTIVE -> new BigDecimal("99");
                case PAST_DUE -> new BigDecimal("119");
            };
        }

        void paymentFailed() {
            switch (status) {                               // switch #3
                case ACTIVE -> {
                    status = Status.PAST_DUE;
                    failedCharges = 1;
                }
                case PAST_DUE -> {
                    failedCharges++;
                    if (failedCharges >= 3) {
                        status = Status.CANCELLED;
                    }
                }
                case TRIAL, CANCELLED -> { }                // no aplica
            }
        }

        void paymentSucceeded() {
            if (status != Status.CANCELLED) {               // y aquí un if en vez de switch...
                status = Status.ACTIVE;
                failedCharges = 0;
            }
        }
    }

    static boolean switchEverywhereExample() {
        SubscriptionWithSwitches subscription = new SubscriptionWithSwitches();
        subscription.paymentSucceeded();
        subscription.paymentFailed();
        subscription.paymentFailed();
        subscription.paymentFailed();
        return !subscription.canUseFreeShipping() && subscription.monthlyCharge().signum() == 0;   // cancelada
    }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN CLÁSICA: UNA CLASE POR ESTADO
        =================================================================

        STATE: "Permite que un objeto altere su comportamiento cuando su
        estado interno cambia. Parecerá que el objeto cambió de clase."

        - Una interface con TODAS las operaciones que dependen del estado.
        - Una clase por estado: cada una contiene SU comportamiento completo.
          Todo lo de "pago vencido" está en PastDue, junto.
        - El CONTEXTO (Subscription) guarda el estado actual y le DELEGA
          cada operación.
        - Las TRANSICIONES: cada operación devuelve el estado siguiente
          (puede ser el mismo). El contexto solo lo reemplaza.

        Para quien usa la suscripción, nada cambia: llama a
        canUseFreeShipping(). Pero ahora un estado nuevo es una clase nueva,
        y las reglas de cada estado se leen en un solo lugar.
    */
    interface SubscriptionState {
        boolean canUseFreeShipping();

        BigDecimal monthlyCharge();

        SubscriptionState paymentSucceeded();               // devuelve el estado siguiente

        SubscriptionState paymentFailed();

        SubscriptionState cancel();

        String name();
    }

    static final class Trial implements SubscriptionState {
        @Override public boolean canUseFreeShipping() { return true; }
        @Override public BigDecimal monthlyCharge() { return BigDecimal.ZERO; }
        @Override public SubscriptionState paymentSucceeded() { return new Active(); }
        @Override public SubscriptionState paymentFailed() { return this; }        // en prueba no se cobra: no aplica
        @Override public SubscriptionState cancel() { return new Cancelled(); }
        @Override public String name() { return "prueba"; }
    }

    static final class Active implements SubscriptionState {
        @Override public boolean canUseFreeShipping() { return true; }
        @Override public BigDecimal monthlyCharge() { return new BigDecimal("99"); }
        @Override public SubscriptionState paymentSucceeded() { return this; }
        @Override public SubscriptionState paymentFailed() { return new PastDue(1); }
        @Override public SubscriptionState cancel() { return new Cancelled(); }
        @Override public String name() { return "activa"; }
    }

    static final class PastDue implements SubscriptionState {   // todo lo de "pago vencido", junto
        private final int failedCharges;

        PastDue(int failedCharges) {
            this.failedCharges = failedCharges;
        }

        @Override public boolean canUseFreeShipping() { return true; }             // periodo de gracia
        @Override public BigDecimal monthlyCharge() { return new BigDecimal("119"); }   // con recargo
        @Override public SubscriptionState paymentSucceeded() { return new Active(); }
        @Override public SubscriptionState paymentFailed() {
            return failedCharges + 1 >= 3 ? new Cancelled() : new PastDue(failedCharges + 1);
        }
        @Override public SubscriptionState cancel() { return new Cancelled(); }
        @Override public String name() { return "pago vencido (" + failedCharges + " fallos)"; }
    }

    static final class Cancelled implements SubscriptionState {
        @Override public boolean canUseFreeShipping() { return false; }
        @Override public BigDecimal monthlyCharge() { return BigDecimal.ZERO; }
        @Override public SubscriptionState paymentSucceeded() { return this; }
        @Override public SubscriptionState paymentFailed() { return this; }
        @Override public SubscriptionState cancel() { return this; }
        @Override public String name() { return "cancelada"; }
    }

    static final class Subscription {                       // CONTEXT
        private SubscriptionState state = new Trial();

        boolean canUseFreeShipping() {
            return state.canUseFreeShipping();              // delega: sin switch
        }

        BigDecimal monthlyCharge() {
            return state.monthlyCharge();
        }

        void paymentSucceeded() {
            state = state.paymentSucceeded();               // la transición la decide el estado
        }

        void paymentFailed() {
            state = state.paymentFailed();
        }

        void cancel() {
            state = state.cancel();
        }

        String status() {
            return state.name();
        }
    }

    static boolean stateClassesExample() {
        Subscription subscription = new Subscription();
        boolean trialIsFree = subscription.monthlyCharge().signum() == 0 && subscription.canUseFreeShipping();

        subscription.paymentSucceeded();
        subscription.paymentFailed();
        boolean graceWithSurcharge = subscription.status().equals("pago vencido (1 fallos)")
                && subscription.canUseFreeShipping()
                && subscription.monthlyCharge().compareTo(new BigDecimal("119")) == 0;

        subscription.paymentFailed();
        subscription.paymentFailed();
        boolean autoCancelled = subscription.status().equals("cancelada") && !subscription.canUseFreeShipping();

        return trialIsFree && graceWithSurcharge && autoCancelled;
    }

    /*
        =================================================================
        CAPA 3) FORMA COMPACTA: UN ENUM CON COMPORTAMIENTO POR CONSTANTE
        =================================================================

        Si los estados NO tienen datos propios (el PastDue de la CAPA 2
        lleva la cuenta de fallos; los demás no), un ENUM es la forma más
        compacta del patrón: cada constante sobrescribe los métodos que le
        cambian (v2_oop/part4_advanced_types/Enums, CAPA 4: comportamiento
        por constante).

        - Las constantes son únicas y sin estado: compartirlas es seguro.
        - Se guardan y se leen fácil (CAPA 6).
        - El compilador obliga a que cada constante implemente los métodos
          abstractos.

        El ejemplo modela un ticket de soporte: abierto, en progreso,
        resuelto; cada estado responde distinto a "responder" y "cerrar".
    */
    enum TicketState {
        OPEN {
            @Override TicketState reply() { return IN_PROGRESS; }
            @Override TicketState close() { throw new IllegalStateException("No se puede cerrar sin atender"); }
        },
        IN_PROGRESS {
            @Override TicketState reply() { return IN_PROGRESS; }
            @Override TicketState close() { return RESOLVED; }
        },
        RESOLVED {
            @Override TicketState reply() { return IN_PROGRESS; }   // el cliente responde: se reabre
            @Override TicketState close() { return RESOLVED; }
        };

        abstract TicketState reply();

        abstract TicketState close();
    }

    static boolean enumStateExample() {
        TicketState ticket = TicketState.OPEN;
        boolean cannotCloseUnattended;
        try {
            ticket.close();
            cannotCloseUnattended = false;
        } catch (IllegalStateException e) {
            cannotCloseUnattended = true;
        }
        ticket = ticket.reply().close();                    // atendido y cerrado
        TicketState reopened = ticket.reply();
        return cannotCloseUnattended && ticket == TicketState.RESOLVED && reopened == TicketState.IN_PROGRESS;
    }

    /*
        =================================================================
        CAPA 4) FORMA MODERNA: ESTADOS CON DATOS (SEALED + RECORDS)
        =================================================================

        Cuando cada estado tiene DATOS DISTINTOS, un enum ya no alcanza (sus
        constantes son únicas: no pueden llevar "3 fallos desde el 5 de
        marzo"). Con una jerarquía sealed de records:
        - cada estado es un record con SUS datos: Trial(díasRestantes),
          PastDue(fallos), Cancelled(motivo);
        - los estados son INMUTABLES: una transición crea un estado nuevo;
        - las operaciones son funciones con switch exhaustivo (v2_oop/
          part4_advanced_types/SealedAndPatternMatching): si se agrega un
          estado, el compilador marca cada switch que no lo considera.

        Es la forma más usada hoy en código nuevo, porque además los
        estados se pueden guardar, comparar y registrar como valores. Y un
        dato imposible (un motivo de cancelación en una suscripción activa)
        ni siquiera se puede representar.
    */
    sealed interface Plan permits TrialPlan, ActivePlan, PastDuePlan, CancelledPlan { }

    record TrialPlan(int daysLeft) implements Plan { }

    record ActivePlan() implements Plan { }

    record PastDuePlan(int failedCharges) implements Plan { }

    record CancelledPlan(String reason) implements Plan { }

    static Plan onPaymentFailed(Plan plan) {
        return switch (plan) {
            case ActivePlan p -> new PastDuePlan(1);
            case PastDuePlan p -> p.failedCharges() + 1 >= 3
                    ? new CancelledPlan("3 cobros fallidos")
                    : new PastDuePlan(p.failedCharges() + 1);
            case TrialPlan p -> p;
            case CancelledPlan p -> p;
        };
    }

    static String banner(Plan plan) {                       // otra operación: otro switch, sin tocar los records
        return switch (plan) {
            case TrialPlan p -> "Te quedan " + p.daysLeft() + " días de prueba";
            case ActivePlan p -> "Envío gratis activo";
            case PastDuePlan p -> "Actualiza tu tarjeta (" + p.failedCharges() + " intentos fallidos)";
            case CancelledPlan p -> "Suscripción cancelada: " + p.reason();
        };
    }

    static boolean sealedStateExample() {
        Plan plan = new ActivePlan();
        plan = onPaymentFailed(plan);
        boolean firstFailure = banner(plan).equals("Actualiza tu tarjeta (1 intentos fallidos)");
        plan = onPaymentFailed(onPaymentFailed(plan));
        return firstFailure
                && banner(plan).equals("Suscripción cancelada: 3 cobros fallidos")
                && banner(new TrialPlan(12)).equals("Te quedan 12 días de prueba");
    }

    /*
        =================================================================
        CAPA 5) TRANSICIONES: EXPLÍCITAS, EN UN LUGAR, Y QUE AVISEN
        =================================================================

        Las transiciones son donde más errores hay. Tres reglas:

        1) Una transición INVÁLIDA debe FALLAR con claridad, no ignorarse en
           silencio: cerrar un ticket que nadie atendió lanza excepción
           (CAPA 3). Ignorarla deja al usuario creyendo que funcionó. La
           excepción: eventos que el NEGOCIO decide que no hacen nada en un
           estado (en la suscripción de la CAPA 2, un pago que llega a una
           suscripción cancelada se ignora a propósito). Eso debe ser una
           decisión explícita y documentada, no un olvido.

        2) Que el estado SOLO cambie a través de las operaciones: nada de un
           setState(cualquierCosa) público, que permitiría saltar de
           CANCELADA a ACTIVA sin pagar.

        3) Que el cambio de estado se NOTIFIQUE: otras partes del sistema
           suelen reaccionar (mandar un correo cuando la suscripción entra
           en pago vencido). Es el lugar natural para un evento
           (part3_behavioral/Observer): el contexto compara el estado antes
           y después de cada operación y avisa si cambió. Así también queda
           un registro (auditoría) de cada transición.
    */
    record StatusChanged(String from, String to) { }

    static final class AuditedSubscription {
        private SubscriptionState state = new Trial();
        final List<StatusChanged> history = new ArrayList<>();

        void paymentSucceeded() {
            apply(state.paymentSucceeded());
        }

        void paymentFailed() {
            apply(state.paymentFailed());
        }

        void cancel() {
            apply(state.cancel());
        }

        private void apply(SubscriptionState next) {        // EL único lugar donde cambia el estado
            if (!next.name().equals(state.name())) {
                history.add(new StatusChanged(state.name(), next.name()));   // aquí se avisaría a los observadores
            }
            state = next;
        }
    }

    static boolean transitionsExample() {
        AuditedSubscription subscription = new AuditedSubscription();
        subscription.paymentSucceeded();
        subscription.paymentSucceeded();                    // sigue activa: no es una transición
        subscription.paymentFailed();
        subscription.cancel();
        return subscription.history.equals(List.of(
                new StatusChanged("prueba", "activa"),
                new StatusChanged("activa", "pago vencido (1 fallos)"),
                new StatusChanged("pago vencido (1 fallos)", "cancelada")));
    }

    /*
        =================================================================
        CAPA 6) GUARDAR EL ESTADO
        =================================================================

        Los objetos de estado viven en memoria; en la base de datos se
        guarda un VALOR que los representa:
        - Con un enum: su NOMBRE ("PAST_DUE") o un código propio estable,
          NUNCA ordinal() (el número de posición cambia si alguien reordena
          las constantes; v2_oop/part4_advanced_types/Enums, CAPA 9).
        - Con clases o records: un código de estado más sus datos (el
          número de fallos), y una función que reconstruye el objeto al
          leer.

        Los datos que solo existen en ciertos estados (el motivo de
        cancelación) son exactamente lo que los records de la CAPA 4
        modelan bien.
    */

    /*
        =================================================================
        CAPA 7) STATE Y STRATEGY
        =================================================================

        En el código se ven IGUALES: un objeto que delega en otro que
        implementa una interface. La diferencia es QUIÉN cambia ese objeto
        y POR QUÉ:

        | Aspecto              | State                                   | Strategy                            |
        |----------------------|-----------------------------------------|-------------------------------------|
        | Qué representa       | en qué etapa está el objeto             | cómo hacer una tarea                |
        | Quién lo cambia      | el propio objeto, como resultado de sus | quien lo usa, desde afuera          |
        |                      | operaciones (transiciones)              | (configuración, elección)           |
        | ¿Se conocen entre sí?| sí: un estado sabe a cuál pasar         | no: las estrategias son             |
        |                      |                                         | independientes                      |

        (part3_behavioral/Strategy es la siguiente guía.)
    */

    /*
        =================================================================
        CAPA 8) CUÁNDO NO HACE FALTA
        =================================================================

        - Dos estados y una o dos operaciones que cambian: un boolean y un
          if se leen mejor (activo / inactivo).
        - Si lo que cambia por estado son solo DATOS (etiqueta, color) y
          las TRANSICIONES válidas, alcanza el enum de Enums, CAPA 3.
        - El patrón empieza a pagar cuando VARIAS operaciones cambian de
          comportamiento según VARIOS estados (el "mismo switch en cada
          método" de la CAPA 1).
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Un switch sobre el estado repetido en cada método (CAPA 1).

        2) Transiciones inválidas ignoradas en silencio (CAPA 5).

        3) Un setter público para el estado que permite saltarse las reglas
           (CAPA 5).

        4) Guardar el ordinal() de un enum en la base de datos (CAPA 6).

        5) Estados mutables compartidos: si dos suscripciones comparten el
           mismo objeto de estado con datos que cambian, una afecta a la
           otra (por eso PastDue y los records son inmutables).

        6) Clases por estado para algo que era un boolean (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - State: el comportamiento de un objeto depende de su estado; cada
          estado encapsula el suyo y el objeto (contexto) le delega.
        - Elimina el switch repetido en cada método; las reglas de un estado
          quedan juntas.
        - Tres formas: clases por estado (clásica); enum con comportamiento
          por constante (estados sin datos); sealed + records (estados con
          datos, inmutables, switch exhaustivo).
        - Transiciones: solo por operaciones, las inválidas fallan, y cada
          cambio se registra o notifica.
        - Guarda el nombre o un código, nunca ordinal().
        - State cambia desde adentro; Strategy se elige desde afuera.
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
