package v11_patterns.part1_creational;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/*
    =====================================================================
    FACTORY METHOD (Y QUÉ ES UN PATRÓN DE DISEÑO)
    =====================================================================

    Ruta de patterns/: guía 1 de 28  (part1_creational)
    Anterior:  v10_design/part4_clean_code/Refactoring
    Siguiente: part1_creational/AbstractFactory

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: los métodos de fábrica ESTÁTICOS como List.of o
    LocalDate.of (v2_oop/part1_basics/Static, CAPA 6) y el principio
    Abierto/Cerrado (v10_design/part2_solid/OpenClosed).

    Índice:
        CAPA 1)  Qué es un patrón de diseño
        CAPA 2)  Cómo se van a estudiar los patrones en esta carpeta
        CAPA 3)  Tres cosas distintas que se llaman "fábrica"
        CAPA 4)  El problema que resuelve Factory Method
        CAPA 5)  La solución: un método que las subclases deciden
        CAPA 6)  La estructura, con sus nombres
        CAPA 7)  Variantes: fábrica con valor por defecto y con parámetro
        CAPA 8)  La alternativa moderna: pasar la fábrica como función
        CAPA 9)  Factory Method en el JDK
        CAPA 10) Un uso escondido: hacer el código probable
        CAPA 11) Cuándo usarlo y cuándo no
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class FactoryMethod {

    public static void main(String[] args) {
        check(simpleFactoryExample(), "CAPA 3");
        check(factoryMethodExample(), "CAPA 5");
        check(variantsExample(), "CAPA 7");
        check(functionalAlternativeExample(), "CAPA 8");
        check(jdkExample(), "CAPA 9");
        check(testSeamExample(), "CAPA 10");
        System.out.println("FactoryMethod: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN PATRÓN DE DISEÑO
        =================================================================

        Un PATRÓN DE DISEÑO es una solución con NOMBRE a un problema de
        diseño que aparece una y otra vez. No es código para copiar y
        pegar ni una librería: es una IDEA de cómo repartir
        responsabilidades entre clases, que se adapta a cada caso.

        La idea viene de la arquitectura (Christopher Alexander catalogó
        patrones para construir ciudades y casas) y llegó al software con el
        libro "Design Patterns: Elements of Reusable Object-Oriented
        Software" (1994), de Gamma, Helm, Johnson y Vlissides, conocidos
        como la "Gang of Four" (GoF, la banda de los cuatro). Describieron
        23 patrones en tres grupos:

        - CREACIONALES (5): cómo se crean los objetos.
        - ESTRUCTURALES (7): cómo se combinan clases y objetos en
          estructuras más grandes.
        - DE COMPORTAMIENTO (11): cómo se reparten las responsabilidades y
          se comunican los objetos.

        Para qué sirven, de verdad:
        1) VOCABULARIO: decir "aquí va un Decorator" comunica en dos
           palabras un diseño completo a cualquier programador.
        2) SOLUCIONES PROBADAS: alguien ya pensó las ventajas, desventajas
           y variantes.
        3) RECONOCERLOS en el código y en las librerías que usas (el JDK y
           Spring están llenos de ellos).

        La advertencia más importante: un patrón es una solución a un
        PROBLEMA. Sin el problema, el patrón solo agrega complejidad
        (v10_design/part1_basics/BasicPrinciples, CAPAS 7 y 8). El error
        clásico de quien acaba de aprenderlos es meterlos en todos lados.
    */

    /*
        =================================================================
        CAPA 2) CÓMO SE VAN A ESTUDIAR LOS PATRONES EN ESTA CARPETA
        =================================================================

        Cada guía de patterns/ responde siempre lo mismo:
        - el PROBLEMA, con código que lo sufre;
        - la SOLUCIÓN, con un ejemplo de negocio realista;
        - la ESTRUCTURA y los nombres de sus piezas;
        - la versión MODERNA en Java (lambdas, records, sealed), porque
          varios patrones de 1994 hoy se escriben mucho más cortos;
        - dónde aparece en el JDK o en frameworks;
        - y cuándo NO usarlo.

        Y con honestidad sobre su importancia: algunos patrones los usarás
        cada semana (Strategy, Builder, Decorator, Adapter, Observer,
        Factory) y otros casi nunca (Interpreter, Flyweight, Memento). Cada
        guía lo dice al principio.

        Importancia de Factory Method: ALTA para reconocerlo (está en todas
        las librerías); MEDIA para escribirlo tal cual, porque hoy muchas
        veces se reemplaza por pasar una función (CAPA 8).
    */

    /*
        =================================================================
        CAPA 3) TRES COSAS DISTINTAS QUE SE LLAMAN "FÁBRICA"
        =================================================================

        La palabra "factory" se usa para tres ideas distintas, y conviene
        no confundirlas:

        1) MÉTODO DE FÁBRICA ESTÁTICO: un método static que crea objetos de
           su clase: List.of, LocalDate.of, Integer.valueOf. Tiene nombre,
           puede reutilizar instancias y devolver subtipos. NO es un patrón
           del libro GoF; ya está explicado en v2_oop/part1_basics/Static,
           CAPA 6.

        2) FÁBRICA SIMPLE: una clase (o método) que recibe un "tipo" y
           devuelve el objeto que corresponde: create("PDF") ->
           PdfExporter. Centraliza el new y el switch en UN lugar (como el
           registro de v10_design/part2_solid/OpenClosed, CAPA 5). Muy
           usada; tampoco es un patrón GoF formal.

        3) FACTORY METHOD (el patrón GoF, el tema de esta guía): una clase
           base implementa un proceso que necesita CREAR un objeto, y deja
           que sus SUBCLASES decidan cuál. Se basa en herencia y
           polimorfismo.

        El ejemplo de abajo es la fábrica simple, para tenerla clara antes
        de pasar al patrón.
    */
    interface Exporter {
        String export(List<String> rows);
    }

    static final class CsvExporter implements Exporter {
        @Override
        public String export(List<String> rows) {
            return String.join("\n", rows);
        }
    }

    static final class HtmlExporter implements Exporter {
        @Override
        public String export(List<String> rows) {
            StringBuilder html = new StringBuilder("<ul>");
            rows.forEach(row -> html.append("<li>").append(row).append("</li>"));
            return html.append("</ul>").toString();
        }
    }

    static final class ExporterFactory {                    // FÁBRICA SIMPLE: el new y la decisión, en un lugar
        private static final Map<String, Supplier<Exporter>> BY_FORMAT = Map.of(
                "csv", CsvExporter::new,
                "html", HtmlExporter::new);

        static Exporter create(String format) {
            Supplier<Exporter> constructor = BY_FORMAT.get(format);
            if (constructor == null) {
                throw new IllegalArgumentException("Formato no soportado: " + format);
            }
            return constructor.get();
        }
    }

    static boolean simpleFactoryExample() {
        return ExporterFactory.create("csv").export(List.of("a", "b")).equals("a\nb")
                && ExporterFactory.create("html").export(List.of("a")).equals("<ul><li>a</li></ul>");
    }

    /*
        =================================================================
        CAPA 4) EL PROBLEMA QUE RESUELVE FACTORY METHOD
        =================================================================

        Una tienda lanza CAMPAÑAS de marketing. Lanzar una campaña siempre
        sigue los mismos pasos:
        1) validar que haya destinatarios;
        2) para cada destinatario, CREAR un mensaje y enviarlo;
        3) registrar cuántos se enviaron.

        Pero el MENSAJE cambia según el canal: un correo tiene asunto y
        HTML; un SMS tiene límite de 160 caracteres; una notificación push
        tiene título corto.

        Opciones malas:
        - Copiar el proceso completo para cada canal (EmailCampaign,
          SmsCampaign con los mismos pasos duplicados): cuando cambia la
          validación o el registro, hay que cambiarlo en todas.
        - Un if/switch por canal dentro del proceso: cada canal nuevo
          obliga a modificar el proceso (v10_design/part2_solid/OpenClosed).

        Lo que se quiere: el proceso escrito UNA vez, y cada canal
        aportando SOLO lo que cambia: cómo crear su mensaje.
    */

    /*
        =================================================================
        CAPA 5) LA SOLUCIÓN: UN MÉTODO QUE LAS SUBCLASES DECIDEN
        =================================================================

        FACTORY METHOD: "Define una interface para crear un objeto, pero
        deja que las subclases decidan qué clase instanciar."

        - La clase base Campaign tiene el proceso completo, launch(), y
          cuando necesita un mensaje llama a createMessage(...), un método
          ABSTRACTO: el "método fábrica".
        - Cada subclase (EmailCampaign, SmsCampaign) implementa SOLO
          createMessage, devolviendo su tipo de mensaje.
        - launch() trabaja con la interface Message: no sabe qué mensaje
          concreto se creó.

        Agregar un canal (push) es una subclase nueva con un método. El
        proceso no se toca.
    */
    interface Message {
        String render();
    }

    record EmailMessage(String to, String subject, String body) implements Message {
        @Override
        public String render() {
            return "EMAIL " + to + " [" + subject + "] " + body;
        }
    }

    record SmsMessage(String phone, String text) implements Message {
        SmsMessage {
            if (text.length() > 160) {
                text = text.substring(0, 157) + "...";      // la regla del SMS vive en el SMS
            }
        }

        @Override
        public String render() {
            return "SMS " + phone + ": " + text;
        }
    }

    abstract static class Campaign {                        // CREATOR
        private final List<String> sentLog = new ArrayList<>();

        /** El proceso: escrito UNA vez para todos los canales. */
        final int launch(List<String> recipients, String offer) {
            if (recipients.isEmpty()) {
                throw new IllegalArgumentException("La campaña no tiene destinatarios");
            }
            for (String recipient : recipients) {
                Message message = createMessage(recipient, offer);   // el método fábrica
                sentLog.add(message.render());
            }
            return sentLog.size();
        }

        /** EL FACTORY METHOD: cada canal decide qué mensaje crear. */
        protected abstract Message createMessage(String recipient, String offer);

        List<String> sentLog() {
            return List.copyOf(sentLog);
        }
    }

    static final class EmailCampaign extends Campaign {    // CONCRETE CREATOR
        @Override
        protected Message createMessage(String recipient, String offer) {
            return new EmailMessage(recipient, "Oferta para ti", "<p>" + offer + "</p>");
        }
    }

    static final class SmsCampaign extends Campaign {      // CONCRETE CREATOR
        @Override
        protected Message createMessage(String recipient, String offer) {
            return new SmsMessage(recipient, offer);
        }
    }

    static boolean factoryMethodExample() {
        Campaign email = new EmailCampaign();
        int sent = email.launch(List.of("ana@correo.com", "luis@correo.com"), "20% en teclados");

        Campaign sms = new SmsCampaign();
        sms.launch(List.of("5551234"), "x".repeat(200));   // el SMS se recorta solo

        boolean validationShared;
        try {
            new SmsCampaign().launch(List.of(), "nada");   // la validación vive en el proceso común
            validationShared = false;
        } catch (IllegalArgumentException e) {
            validationShared = true;
        }

        return sent == 2
                && email.sentLog().get(0).equals("EMAIL ana@correo.com [Oferta para ti] <p>20% en teclados</p>")
                && sms.sentLog().get(0).length() == "SMS 5551234: ".length() + 160
                && validationShared;
    }

    /*
        =================================================================
        CAPA 6) LA ESTRUCTURA, CON SUS NOMBRES
        =================================================================

        Los nombres de las piezas en el libro, que verás en cualquier
        explicación:

            Product            (Message)         lo que se crea: una interface
            ConcreteProduct    (EmailMessage)    cada implementación
            Creator            (Campaign)        tiene el proceso y declara el
                                                 método fábrica
            ConcreteCreator    (EmailCampaign)   implementa el método fábrica

            Campaign.launch() ──llama a──> createMessage() ──devuelve──> Message
                                                ^
                                 EmailCampaign / SmsCampaign deciden cuál

        Dos detalles de diseño del ejemplo:
        - launch() es final: las subclases NO pueden cambiar el proceso,
          solo el paso de creación. Así el proceso común no se rompe.
        - createMessage() es protected: es un punto de extensión para las
          subclases, no parte de lo que usan los clientes.

        Si te parece familiar: es un caso particular del patrón TEMPLATE
        METHOD (part3_behavioral/TemplateMethod), donde el paso que cambia
        es justamente CREAR un objeto.
    */

    /*
        =================================================================
        CAPA 7) VARIANTES: FÁBRICA CON VALOR POR DEFECTO Y CON PARÁMETRO
        =================================================================

        1) CON IMPLEMENTACIÓN POR DEFECTO: el método fábrica no es
           abstracto; la clase base crea algo razonable, y las subclases lo
           reemplazan solo si necesitan otra cosa. Así una subclase puede
           cambiar UNO de varios pasos sin tener que implementarlos todos.

        2) CON PARÁMETRO: el método fábrica recibe un dato que decide qué
           crear (createMessage(recipient, offer, urgent)). Mezcla la
           fábrica simple (CAPA 3) con la herencia.

        El ejemplo: un reporte que por defecto usa formato de texto, y una
        subclase que solo cambia ese paso.
    */
    abstract static class Report {
        final String generate(List<String> rows) {
            return title() + "\n" + createFormatter().export(rows);
        }

        protected abstract String title();

        protected Exporter createFormatter() {             // método fábrica CON valor por defecto
            return new CsvExporter();
        }
    }

    static final class SalesReport extends Report {        // no cambia la fábrica: usa la de texto
        @Override
        protected String title() {
            return "VENTAS";
        }
    }

    static final class WebSalesReport extends Report {     // solo cambia la fábrica
        @Override
        protected String title() {
            return "VENTAS";
        }

        @Override
        protected Exporter createFormatter() {
            return new HtmlExporter();
        }
    }

    static boolean variantsExample() {
        List<String> rows = List.of("teclado: 3");
        return new SalesReport().generate(rows).equals("VENTAS\nteclado: 3")
                && new WebSalesReport().generate(rows).equals("VENTAS\n<ul><li>teclado: 3</li></ul>");
    }

    /*
        =================================================================
        CAPA 8) LA ALTERNATIVA MODERNA: PASAR LA FÁBRICA COMO FUNCIÓN
        =================================================================

        El Factory Method clásico usa HERENCIA: una subclase por variante.
        Hoy, en Java moderno, muchas veces es más simple usar COMPOSICIÓN:
        la clase RECIBE la fábrica como un parámetro, una función que crea
        el objeto (v7_functional/part1_lambdas/FunctionalInterfaces:
        Supplier, Function, BiFunction; o una interface propia).

            new FlexibleCampaign((recipient, offer) -> new SmsMessage(recipient, offer))

        Ventajas:
        - No hay que crear una subclase por variante.
        - Se puede cambiar la fábrica al ejecutar (por configuración).
        - Es composición sobre herencia (v2_oop/part3_interfaces_and_
          relationships/CompositionAndAggregation, CAPA 6), y se inyecta
          como cualquier dependencia (v10_design/part3_responsibilities/
          DependencyInjection).

        Cuándo sigue siendo mejor el clásico: cuando la subclase cambia
        VARIOS pasos relacionados a la vez (no solo la creación), o cuando
        un framework te pide extender su clase base.

        El ejemplo produce exactamente lo mismo que la CAPA 5, sin
        subclases.
    */
    @FunctionalInterface
    interface MessageFactory {
        Message create(String recipient, String offer);
    }

    static final class FlexibleCampaign {
        private final MessageFactory messageFactory;       // la fábrica, recibida
        private final List<String> sentLog = new ArrayList<>();

        FlexibleCampaign(MessageFactory messageFactory) {
            this.messageFactory = messageFactory;
        }

        int launch(List<String> recipients, String offer) {
            if (recipients.isEmpty()) {
                throw new IllegalArgumentException("La campaña no tiene destinatarios");
            }
            for (String recipient : recipients) {
                sentLog.add(messageFactory.create(recipient, offer).render());
            }
            return sentLog.size();
        }

        List<String> sentLog() {
            return List.copyOf(sentLog);
        }
    }

    static boolean functionalAlternativeExample() {
        FlexibleCampaign email = new FlexibleCampaign(
                (recipient, offer) -> new EmailMessage(recipient, "Oferta para ti", "<p>" + offer + "</p>"));
        FlexibleCampaign sms = new FlexibleCampaign(SmsMessage::new);   // el constructor ES la fábrica

        EmailCampaign classic = new EmailCampaign();
        List<String> recipients = List.of("ana@correo.com", "luis@correo.com");
        classic.launch(recipients, "20% en teclados");
        email.launch(recipients, "20% en teclados");
        sms.launch(List.of("5551234"), "hola");

        return email.sentLog().equals(classic.sentLog())   // mismo resultado que la versión con herencia
                && sms.sentLog().equals(List.of("SMS 5551234: hola"));
    }

    /*
        =================================================================
        CAPA 9) FACTORY METHOD EN EL JDK
        =================================================================

        El ejemplo más usado, y que usas todos los días sin notarlo:

            Iterable.iterator()

        Iterable define el proceso (el for-each, forEach, spliterator) y
        declara iterator() como el método fábrica. Cada colección CREA su
        propio iterador: ArrayList uno que avanza por posiciones, LinkedList
        uno que sigue los nodos, HashSet uno que recorre los cajones
        (v5_collections/part1_basics/Iterators). Tu for-each no sabe cuál
        recibió. Es Factory Method de libro.

        Otros:
        - Collection.spliterator(): cada colección crea el recorredor
          adecuado a su estructura (ArrayList, LinkedList y HashSet lo
          sobrescriben; stream() es un default de Collection que lo usa).
        - Los métodos createXxx() que verás en frameworks: los puntos de
          extensión donde tu subclase decide qué objeto usar.

        (Y los estáticos, como NumberFormat.getInstance() o
        Calendar.getInstance(), son la otra "fábrica" de la CAPA 3, punto 1:
        devuelven un subtipo según el Locale.)
    */
    static boolean jdkExample() {
        List<Iterable<Integer>> sources = List.of(new ArrayList<>(List.of(1, 2, 3)), new LinkedList<>(List.of(1, 2, 3)));
        boolean differentIterators = !sources.get(0).iterator().getClass().equals(sources.get(1).iterator().getClass());

        boolean sameUsage = true;
        for (Iterable<Integer> source : sources) {
            int sum = 0;
            Iterator<Integer> it = source.iterator();       // el método fábrica: cada colección crea el suyo
            while (it.hasNext()) {
                sum += it.next();
            }
            sameUsage &= sum == 6;
        }
        return differentIterators && sameUsage;
    }

    /*
        =================================================================
        CAPA 10) UN USO ESCONDIDO: HACER EL CÓDIGO PROBABLE
        =================================================================

        Un truco muy usado con código heredado (Michael Feathers lo llama
        "subclass and override"): si una clase crea por dentro algo difícil
        de usar en pruebas (una conexión real, la hora del sistema),
        extraes esa creación a un método fábrica protected. En la prueba,
        una subclase lo sobrescribe y devuelve una versión de prueba.

        Es útil para meter pruebas en código viejo SIN rediseñarlo todavía.
        En código nuevo, la forma limpia es inyectar la dependencia
        (v10_design/part3_responsibilities/DependencyInjection) o pasar la
        fábrica (CAPA 8).
    */
    static class NightlyBackup {
        String run() {
            Exporter exporter = createExporter();           // creación extraída a un método fábrica
            return exporter.export(List.of("respaldo ok"));
        }

        protected Exporter createExporter() {               // en producción: el "real"
            return rows -> { throw new IllegalStateException("No hay conexión al servidor de respaldos"); };
        }
    }

    static boolean testSeamExample() {
        NightlyBackup inTest = new NightlyBackup() {        // la prueba sobrescribe SOLO la creación
            @Override
            protected Exporter createExporter() {
                return new CsvExporter();
            }
        };

        boolean realOneUnavailable;
        try {
            new NightlyBackup().run();
            realOneUnavailable = false;
        } catch (IllegalStateException e) {
            realOneUnavailable = true;
        }
        return realOneUnavailable && inTest.run().equals("respaldo ok");
    }

    /*
        =================================================================
        CAPA 11) CUÁNDO USARLO Y CUÁNDO NO
        =================================================================

        ÚSALO cuando:
        - Tienes un PROCESO común y lo único que varía es QUÉ objeto se
          crea en uno de sus pasos.
        - Escribes una clase base o un framework que otros extenderán y
          quieres darles un punto para decidir qué crear.
        - Necesitas una forma rápida de meter pruebas en código heredado
          (CAPA 10).

        PREFIERE otra cosa cuando:
        - Solo hay que elegir una clase según un dato: fábrica simple o
          registro (CAPA 3).
        - La variación es solo la creación y quieres evitar subclases:
          pasa la fábrica como función (CAPA 8).
        - Hay que crear FAMILIAS de objetos que deben combinar entre sí:
          part1_creational/AbstractFactory (la siguiente guía).
        - Solo hay una forma de crear el objeto: new, sin patrón.
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Llamar "Factory Method" a cualquier método que crea algo:
           distingue estático, fábrica simple y el patrón (CAPA 3).

        2) Crear una jerarquía de subclases solo para cambiar QUÉ se crea,
           cuando una función bastaba (CAPA 8).

        3) Dejar el proceso de la clase base sin final: una subclase lo
           sobrescribe y rompe los pasos comunes (CAPA 6).

        4) Hacer público el método fábrica cuando es solo un punto de
           extensión: protected (CAPA 6).

        5) Fábricas para objetos que no varían: un new directo es más claro
           (CAPA 11).

        6) Aplicar patrones porque sí, sin un problema que resolver
           (CAPA 1).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Patrón de diseño: solución con nombre a un problema recurrente;
          vocabulario, no código para copiar. 23 del libro GoF: creacionales,
          estructurales y de comportamiento. Sin problema, no hay patrón.
        - "Fábrica" significa tres cosas: método estático (List.of),
          fábrica simple (create(tipo)) y el patrón Factory Method.
        - Factory Method: la clase base tiene el proceso y llama a un método
          de creación que las subclases implementan. Creator, Product y sus
          concretos. Es un Template Method aplicado a crear.
        - Proceso final, método fábrica protected; puede tener valor por
          defecto.
        - Hoy, muchas veces mejor: recibir la fábrica como función.
        - En el JDK: Iterable.iterator(), Collection.spliterator().
        - Truco de pruebas para código heredado: sobrescribir la creación.
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
