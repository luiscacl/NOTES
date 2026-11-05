package v11_patterns.part2_structural;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
    =====================================================================
    BRIDGE: SEPARAR DOS COSAS QUE VARÍAN
    =====================================================================

    Ruta de patterns/: guía 7 de 28  (part2_structural)
    Anterior:  part2_structural/Adapter
    Siguiente: part2_structural/Composite

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: BAJA-MEDIA. Pocas veces se nombra, pero la idea (dos
    jerarquías unidas por composición en vez de una jerarquía que las
    multiplica) evita uno de los errores de diseño más comunes con la
    herencia.

    Índice:
        CAPA 1)  El problema: la explosión de clases
        CAPA 2)  La solución: dos jerarquías unidas por un campo
        CAPA 3)  La estructura, con sus nombres (y una confusión de nombres)
        CAPA 4)  El "puente" en acción: cada lado se adapta al otro
        CAPA 5)  Combinar al ejecutar
        CAPA 6)  Bridge en el JDK y en los proyectos
        CAPA 7)  Bridge, Strategy y Adapter
        CAPA 8)  Cuándo usarlo y cuándo no
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Bridge {

    public static void main(String[] args) {
        check(explosionExample(), "CAPA 1");
        check(bridgeExample(), "CAPA 2");
        check(capabilitiesExample(), "CAPA 4");
        check(runtimeCombinationExample(), "CAPA 5");
        System.out.println("Bridge: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: LA EXPLOSIÓN DE CLASES
        =================================================================

        La tienda manda notificaciones. Varían en DOS cosas
        independientes:
        - el TIPO de notificación, que decide QUÉ se dice y cómo se arma
          el mensaje: alerta de seguridad, recordatorio de pago, promoción;
        - el CANAL, que decide CÓMO se entrega: correo, SMS, notificación
          push.

        Si se modela con HERENCIA, una clase por combinación:

            SecurityAlertByEmail, SecurityAlertBySms, SecurityAlertByPush,
            PaymentReminderByEmail, PaymentReminderBySms, ...

        3 tipos x 3 canales = 9 clases. Y crece MULTIPLICANDO:
        - un canal nuevo (WhatsApp) = 3 clases más (una por tipo);
        - un tipo nuevo = 4 clases más (una por canal).
        Además, el código de "cómo se manda un SMS" se repite en todas las
        clases ...BySms, y el de "cómo se arma una alerta" en todas las
        SecurityAlert...

        El ejemplo muestra 4 de esas clases (2 x 2) para que se vea la
        duplicación: cada una repite media lógica de otra.
    */
    static final class SecurityAlertByEmail {
        String send(String user, String detail) {
            return "[correo a " + user + "] ALERTA: " + detail;   // armado de alerta + envío por correo
        }
    }

    static final class SecurityAlertBySms {
        String send(String user, String detail) {
            return "[sms a " + user + "] ALERTA: " + detail;      // armado de alerta (repetido) + envío por SMS
        }
    }

    static final class PaymentReminderByEmail {
        String send(String user, String detail) {
            return "[correo a " + user + "] Recordatorio: " + detail;   // envío por correo (repetido)
        }
    }

    static final class PaymentReminderBySms {
        String send(String user, String detail) {
            return "[sms a " + user + "] Recordatorio: " + detail;      // envío por SMS (repetido)
        }
    }

    static boolean explosionExample() {
        return new SecurityAlertBySms().send("ana", "inicio de sesión nuevo").equals("[sms a ana] ALERTA: inicio de sesión nuevo")
                && new PaymentReminderByEmail().send("luis", "vence mañana").startsWith("[correo a luis]");
    }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: DOS JERARQUÍAS UNIDAS POR UN CAMPO
        =================================================================

        BRIDGE: "Desacopla una abstracción de su implementación para que
        ambas puedan variar de forma independiente."

        En vez de una jerarquía que combina las dos dimensiones, DOS
        jerarquías, cada una con UNA dimensión:

            Notification (qué se dice)            Channel (cómo se entrega)
              ├─ SecurityAlert                       ├─ EmailChannel
              ├─ PaymentReminder                     ├─ SmsChannel
              └─ Promotion                           └─ PushChannel

        y la notificación TIENE un canal (un campo). Ese campo es el
        "puente" entre las dos jerarquías.

        Ahora:
        - 3 tipos + 3 canales = 6 clases (no 9), y crece SUMANDO: un canal
          nuevo es UNA clase, que funciona con todos los tipos.
        - Cómo se arma una alerta vive en UN lugar; cómo se manda un SMS,
          en otro.
        - Es composición sobre herencia (v2_oop/part3_interfaces_and_
          relationships/CompositionAndAggregation, CAPA 6) aplicada a dos
          dimensiones.
    */
    interface Channel {                                     // IMPLEMENTOR: cómo se entrega
        String deliver(String recipient, String text);
    }

    static final class EmailChannel implements Channel {
        @Override
        public String deliver(String recipient, String text) {
            return "[correo a " + recipient + "] " + text;
        }
    }

    static final class SmsChannel implements Channel {
        @Override
        public String deliver(String recipient, String text) {
            return "[sms a " + recipient + "] " + text;
        }
    }

    abstract static class Notification {                    // ABSTRACTION: qué se dice
        protected final Channel channel;                    // EL PUENTE

        Notification(Channel channel) {
            this.channel = channel;
        }

        abstract String send(String recipient, String detail);
    }

    static final class SecurityAlert extends Notification {
        SecurityAlert(Channel channel) {
            super(channel);
        }

        @Override
        String send(String recipient, String detail) {
            return channel.deliver(recipient, "ALERTA: " + detail);
        }
    }

    static final class PaymentReminder extends Notification {
        PaymentReminder(Channel channel) {
            super(channel);
        }

        @Override
        String send(String recipient, String detail) {
            return channel.deliver(recipient, "Recordatorio: " + detail);
        }
    }

    // Agregar un canal: UNA clase, y ya funciona con todos los tipos.
    static final class PushChannel implements Channel {
        @Override
        public String deliver(String recipient, String text) {
            return "[push a " + recipient + "] " + text;
        }
    }

    static boolean bridgeExample() {
        boolean sameAsBefore = new SecurityAlert(new SmsChannel()).send("ana", "inicio de sesión nuevo")
                .equals(new SecurityAlertBySms().send("ana", "inicio de sesión nuevo"))
                && new PaymentReminder(new EmailChannel()).send("luis", "vence mañana")
                .equals(new PaymentReminderByEmail().send("luis", "vence mañana"));

        boolean newChannelWorksWithAllTypes = new SecurityAlert(new PushChannel()).send("ana", "x").startsWith("[push a ana]")
                && new PaymentReminder(new PushChannel()).send("ana", "x").startsWith("[push a ana]");

        return sameAsBefore && newChannelWorksWithAllTypes;
    }

    /*
        =================================================================
        CAPA 3) LA ESTRUCTURA, CON SUS NOMBRES (Y UNA CONFUSIÓN DE NOMBRES)
        =================================================================

            Abstraction          (Notification)     el lado que usa el cliente;
                                                    tiene el campo hacia el otro lado
            RefinedAbstraction   (SecurityAlert)    sus variantes
            Implementor          (Channel)          la interface del otro lado
            ConcreteImplementor  (SmsChannel)       sus variantes

        Ojo con los nombres del libro: aquí "abstracción" e
        "implementación" NO significan "clase abstracta" e "implements" de
        Java. Significan:
        - "abstracción" = la parte de ALTO nivel, la que piensa en términos
          del negocio (qué notificar);
        - "implementación" = la parte de BAJO nivel, la que sabe hacer la
          tarea técnica (cómo entregar).
        Muchos prefieren decirlo así: "dos jerarquías, una usa a la otra".
    */

    /*
        =================================================================
        CAPA 4) EL "PUENTE" EN ACCIÓN: CADA LADO SE ADAPTA AL OTRO
        =================================================================

        El puente no es solo un campo: es una CONVERSACIÓN entre los dos
        lados a través de una interface pequeña. El lado de bajo nivel
        puede informar sus CAPACIDADES, y el de alto nivel decidir con
        ellas, sin conocer el canal concreto.

        Ejemplo real: el SMS solo admite 160 caracteres. La notificación no
        pregunta "¿eres un SMS?" (eso rompería el diseño; v10_design/
        part2_solid/LiskovSubstitution, CAPA 2): pregunta al canal su
        largo máximo, y si no le cabe el mensaje completo, manda la versión
        corta. Un canal nuevo con otro límite funciona sin tocar las
        notificaciones.
    */
    interface CapableChannel {
        String deliver(String recipient, String text);

        int maxLength();                                    // capacidad que informa el canal
    }

    record SimpleChannel(String name, int maxLength) implements CapableChannel {
        @Override
        public String deliver(String recipient, String text) {
            return "[" + name + " a " + recipient + "] " + text;
        }
    }

    static final class OrderShipped {                      // decide con la capacidad, no con el tipo de canal
        private final CapableChannel channel;

        OrderShipped(CapableChannel channel) {
            this.channel = channel;
        }

        String send(String recipient, String orderId, String carrier, String trackingUrl) {
            String full = "Tu pedido " + orderId + " salió con " + carrier + ". Síguelo aquí: " + trackingUrl;
            String brief = "Pedido " + orderId + " enviado";
            return channel.deliver(recipient, full.length() <= channel.maxLength() ? full : brief);
        }
    }

    static boolean capabilitiesExample() {
        String url = "https://tienda.com/rastreo/" + "x".repeat(140);
        String byEmail = new OrderShipped(new SimpleChannel("correo", 10_000)).send("ana", "A-7", "FastShip", url);
        String bySms = new OrderShipped(new SimpleChannel("sms", 160)).send("ana", "A-7", "FastShip", url);
        return byEmail.contains(url) && bySms.equals("[sms a ana] Pedido A-7 enviado");
    }

    /*
        =================================================================
        CAPA 5) COMBINAR AL EJECUTAR
        =================================================================

        Con herencia (CAPA 1), la combinación tipo + canal se decide AL
        ESCRIBIR el código (qué clase haces new). Con Bridge, se decide AL
        EJECUTAR: el canal es un objeto que se pasa, así que puede salir de
        las preferencias de cada usuario, de una configuración o de un
        registro (v10_design/part2_solid/OpenClosed, CAPA 5).

        El ejemplo manda la misma alerta a tres usuarios, cada uno por el
        canal que eligió.
    */
    static final Map<String, Channel> CHANNELS = Map.of(
            "email", new EmailChannel(),
            "sms", new SmsChannel(),
            "push", new PushChannel());

    static boolean runtimeCombinationExample() {
        Map<String, String> preferredChannel = Map.of("ana", "sms", "luis", "email", "mia", "push");
        List<String> sent = new ArrayList<>();
        for (String user : List.of("ana", "luis", "mia")) {
            Notification alert = new SecurityAlert(CHANNELS.get(preferredChannel.get(user)));   // combinación al ejecutar
            sent.add(alert.send(user, "cambio de contraseña"));
        }
        return sent.equals(List.of(
                "[sms a ana] ALERTA: cambio de contraseña",
                "[correo a luis] ALERTA: cambio de contraseña",
                "[push a mia] ALERTA: cambio de contraseña"));
    }

    /*
        =================================================================
        CAPA 6) BRIDGE EN EL JDK Y EN LOS PROYECTOS
        =================================================================

        - JDBC, la API de Java para bases de datos: tu código usa las
          interfaces de java.sql (Connection, PreparedStatement, ResultSet),
          que son el lado de "alto nivel"; cada base de datos trae su
          DRIVER, que es el lado de "bajo nivel". Cambiar de MySQL a
          PostgreSQL es cambiar el driver, sin tocar tu código. Las dos
          jerarquías crecen por separado: Java agrega funciones a la API,
          los fabricantes publican drivers.

        - LOGGING: tu código escribe logs con una API (en Java, la más usada
          es SLF4J; tools/), y por debajo un "backend" decide cómo y dónde
          se guardan (consola, archivo, un servicio de logs). Se cambia el
          backend sin tocar ni una línea de las que escriben logs.

        - Los "drivers" en general (de impresoras, de video, del sistema
          operativo) son la misma idea: una interface estable para quien
          usa, implementaciones intercambiables para cada dispositivo.
    */

    /*
        =================================================================
        CAPA 7) BRIDGE, STRATEGY Y ADAPTER
        =================================================================

        Se parecen mucho en el código (un objeto con un campo hacia una
        interface). La diferencia es el PROBLEMA:

        | Patrón   | Problema                                         | Cuándo se decide            |
        |----------|--------------------------------------------------|-----------------------------|
        | Bridge   | DOS dimensiones que varían y se multiplicarían   | al DISEÑAR, por adelantado  |
        |          | con herencia                                     |                             |
        | Strategy | UN algoritmo intercambiable dentro de un objeto  | al diseñar                  |
        |          | (part3_behavioral/Strategy)                      |                             |
        | Adapter  | dos cosas que YA existen no encajan              | DESPUÉS, al integrar        |
        |          | (part2_structural/Adapter)                       |                             |

        Si solo UNA de las dos jerarquías varía, lo que tienes es un
        Strategy, y está bien llamarlo así.
    */

    /*
        =================================================================
        CAPA 8) CUÁNDO USARLO Y CUÁNDO NO
        =================================================================

        ÚSALO cuando:
        - Ves (o vas a ver) nombres de clase que combinan dos ideas:
          AlertBySms, PdfReportForAdmin, MysqlUserRepository: cada palabra
          es una dimensión.
        - Las dos dimensiones crecen por separado (más canales, más tipos).
        - Quieres elegir la combinación al ejecutar (CAPA 5).

        NO lo uses cuando:
        - Solo hay una dimensión que varía: basta una interface (Strategy).
        - Las combinaciones son pocas y fijas, y no van a crecer: unas
          pocas clases son más simples que dos jerarquías.
        - Las dos "dimensiones" no son independientes: si cada tipo de
          notificación solo puede ir por UN canal, no hay nada que
          combinar.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Heredar una clase por combinación de dos dimensiones: la
           explosión de clases (CAPA 1).

        2) Que la abstracción pregunte el tipo concreto del implementador
           (instanceof SmsChannel) en vez de usar sus capacidades
           (CAPA 4).

        3) Interfaces del lado bajo con métodos del negocio (o al revés):
           cada lado debe hablar de lo suyo (CAPA 3).

        4) Aplicarlo con una sola dimensión que varía (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Problema: dos dimensiones que varían (tipo x canal) modeladas con
          herencia multiplican las clases y duplican la lógica.
        - Bridge: dos jerarquías (qué / cómo) unidas por un campo; crece
          sumando, no multiplicando.
        - "Abstracción" = alto nivel (negocio); "implementación" = bajo
          nivel (técnica). No son abstract ni implements de Java.
        - Los lados conversan por una interface pequeña (capacidades), sin
          instanceof.
        - La combinación se elige al ejecutar.
        - En la práctica: JDBC y sus drivers, las APIs de logging y sus
          backends.
        - Con una sola dimensión, es un Strategy.
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
