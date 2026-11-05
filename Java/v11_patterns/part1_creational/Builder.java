package v11_patterns.part1_creational;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/*
    =====================================================================
    BUILDER: ARMAR OBJETOS COMPLICADOS PASO A PASO
    =====================================================================

    Ruta de patterns/: guía 3 de 28  (part1_creational)
    Anterior:  part1_creational/AbstractFactory
    Siguiente: part1_creational/Prototype

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: ALTA. Lo vas a usar y a escribir constantemente, y lo
    verás en casi toda librería moderna.

    Qué se da por sabido: clases anidadas static (el Builder de Playlist
    en v2_oop/part3_interfaces_and_relationships/NestedClasses, CAPA 2),
    records e inmutabilidad.

    Índice:
        CAPA 1)  El problema 1: constructores con demasiados parámetros
        CAPA 2)  El problema 2: armar con setters deja objetos a medias
        CAPA 3)  La solución: un Builder
        CAPA 4)  build(): el único lugar donde se valida todo
        CAPA 5)  Inmutabilidad y copias: el builder y el objeto son independientes
        CAPA 6)  Builder para records
        CAPA 7)  Step builder: obligar los pasos con tipos
        CAPA 8)  Builders en el JDK
        CAPA 9)  ProcessBuilder: armar un proceso del sistema operativo
        CAPA 10) Builder, constructor, fábrica o "with"
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Builder {

    public static void main(String[] args) throws Exception {
        check(telescopingExample(), "CAPA 1");
        check(settersExample(), "CAPA 2");
        check(builderExample(), "CAPA 3");
        check(validationExample(), "CAPA 4");
        check(independenceExample(), "CAPA 5");
        check(recordBuilderExample(), "CAPA 6");
        check(stepBuilderExample(), "CAPA 7");
        check(jdkBuildersExample(), "CAPA 8");
        check(processBuilderExample(), "CAPA 9");
        System.out.println("Builder: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA 1: CONSTRUCTORES CON DEMASIADOS PARÁMETROS
        =================================================================

        Un pedido de la tienda tiene: cliente y productos (obligatorios), y
        dirección de envío, envoltura de regalo, mensaje de regalo, cupón,
        notas y envío exprés (opcionales).

        Con constructores, aparecen dos formas malas:

        1) UN CONSTRUCTOR CON TODO, y null o false para lo que no aplica:
               new ShopOrder("Ana", items, null, false, null, null, "", true)
           ¿Qué es cada null? ¿Qué es el true del final? Si inviertes dos
           parámetros del mismo tipo (dos String, dos boolean), COMPILA y
           queda mal.

        2) CONSTRUCTORES TELESCÓPICOS: uno con 2 parámetros, otro con 3,
           otro con 4... cada uno llamando al siguiente. Con 6 opcionales
           habría que escribir decenas para cubrir todas las combinaciones,
           y aun así no se pueden tener dos con la misma firma (dos con un
           String extra: ¿cupón o notas?).

        El ejemplo muestra el constructor gigante: dos llamadas que se ven
        casi iguales y significan cosas muy distintas.
    */
    record OrderLine(String sku, int quantity, BigDecimal unitPrice) { }

    static final class TelescopingOrder {
        final String customer;
        final List<OrderLine> lines;
        final String address;
        final boolean giftWrap;
        final String coupon;
        final String notes;

        TelescopingOrder(String customer, List<OrderLine> lines, String address, boolean giftWrap,
                         String coupon, String notes) {
            this.customer = customer;
            this.lines = lines;
            this.address = address;
            this.giftWrap = giftWrap;
            this.coupon = coupon;
            this.notes = notes;
        }
    }

    static final List<OrderLine> LINES = List.of(new OrderLine("KBD-1", 1, new BigDecimal("500")));

    static boolean telescopingExample() {
        TelescopingOrder intended = new TelescopingOrder("Ana", LINES, "Reforma 10", false, "VERANO10", "tocar timbre");
        TelescopingOrder swapped = new TelescopingOrder("Ana", LINES, "Reforma 10", false, "tocar timbre", "VERANO10");
        return !intended.coupon.equals(swapped.coupon)      // compiló igual, y el cupón ahora es "tocar timbre"
                && swapped.coupon.equals("tocar timbre");
    }

    /*
        =================================================================
        CAPA 2) EL PROBLEMA 2: ARMAR CON SETTERS DEJA OBJETOS A MEDIAS
        =================================================================

        La otra forma tradicional (el estilo "JavaBean"): un constructor
        vacío y un setter por campo.

            ShopOrder order = new ShopOrder();
            order.setCustomer("Ana");
            order.setAddress("Reforma 10");
            ...

        Se lee mejor, pero:
        - Entre el new y el último setter, el objeto existe INCOMPLETO. Si
          alguien lo usa antes (o se olvida un setter), funciona con datos
          a medias.
        - No hay un momento para validar reglas entre campos ("si hay
          mensaje de regalo, debe haber envoltura"): ¿en qué setter las
          revisas, si los campos llegan en cualquier orden?
        - El objeto NO PUEDE ser inmutable (los setters lo cambian
          siempre), con todo lo que eso cuesta (v5_collections/part4_tools/
          ImmutableCollections, CAPA 1).
    */
    static final class BeanOrder {
        private String customer;
        private final List<OrderLine> lines = new ArrayList<>();

        void setCustomer(String customer) {
            this.customer = customer;
        }

        void addLine(OrderLine line) {
            lines.add(line);
        }

        String summary() {
            return customer.toUpperCase() + " (" + lines.size() + " productos)";
        }
    }

    static boolean settersExample() {
        BeanOrder order = new BeanOrder();
        order.addLine(LINES.get(0));                        // alguien olvidó setCustomer...
        try {
            order.summary();                                // ...y el objeto incompleto ya circula
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }

    /*
        =================================================================
        CAPA 3) LA SOLUCIÓN: UN BUILDER
        =================================================================

        BUILDER: "Separa la construcción de un objeto complejo de su
        representación." En la versión que se usa en Java (la de Joshua
        Bloch en "Effective Java"):

        - El objeto (ShopOrder) es INMUTABLE: campos final, constructor
          PRIVADO que recibe el builder.
        - Una clase anidada static Builder acumula los valores con métodos
          que tienen NOMBRE y devuelven this, para poder encadenarlos
          (interfaz "fluida").
        - Los obligatorios se piden al CREAR el builder (no se pueden
          olvidar); los opcionales, con métodos.
        - build() valida todo y crea el objeto, completo y válido, de una
          sola vez.

            ShopOrder order = ShopOrder.builder("Ana")
                    .line("KBD-1", 1, "500")
                    .shipTo("Reforma 10")
                    .coupon("VERANO10")
                    .build();

        Cada valor dice qué es; el orden de los opcionales no importa; lo
        que no aplica, simplemente no se escribe; y nunca existe un pedido
        a medias.
    */
    static final class ShopOrder {
        private final String customer;
        private final List<OrderLine> lines;
        private final Optional<String> address;
        private final boolean giftWrap;
        private final Optional<String> giftMessage;
        private final Optional<String> coupon;
        private final boolean express;

        private ShopOrder(OrderBuilder builder) {           // PRIVADO: solo el builder puede crear pedidos
            this.customer = builder.customer;
            this.lines = List.copyOf(builder.lines);        // copia inmutable (CAPA 5)
            this.address = Optional.ofNullable(builder.address);
            this.giftWrap = builder.giftWrap;
            this.giftMessage = Optional.ofNullable(builder.giftMessage);
            this.coupon = Optional.ofNullable(builder.coupon);
            this.express = builder.express;
        }

        static OrderBuilder builder(String customer) {       // lo obligatorio, al empezar
            return new OrderBuilder(customer);
        }

        BigDecimal subtotal() {
            return lines.stream()
                    .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        String customer() {
            return customer;
        }

        List<OrderLine> lines() {
            return lines;
        }

        Optional<String> coupon() {
            return coupon;
        }

        boolean giftWrap() {
            return giftWrap;
        }

        Optional<String> giftMessage() {
            return giftMessage;
        }

        boolean express() {
            return express;
        }

        Optional<String> address() {
            return address;
        }
    }

    static final class OrderBuilder {
        private final String customer;
        private final List<OrderLine> lines = new ArrayList<>();
        private String address;
        private boolean giftWrap;
        private String giftMessage;
        private String coupon;
        private boolean express;

        private OrderBuilder(String customer) {
            this.customer = Objects.requireNonNull(customer, "customer");
        }

        OrderBuilder line(String sku, int quantity, String unitPrice) {
            lines.add(new OrderLine(sku, quantity, new BigDecimal(unitPrice)));
            return this;                                    // devolver this permite encadenar
        }

        OrderBuilder shipTo(String address) {
            this.address = address;
            return this;
        }

        OrderBuilder giftWrapped(String message) {           // un método con nombre puede fijar dos campos coherentes
            this.giftWrap = true;
            this.giftMessage = message;
            return this;
        }

        OrderBuilder coupon(String code) {
            this.coupon = code;
            return this;
        }

        OrderBuilder express() {
            this.express = true;
            return this;
        }

        ShopOrder build() {
            validate();                                     // CAPA 4
            return new ShopOrder(this);
        }

        private void validate() {
            List<String> problems = new ArrayList<>();
            if (lines.isEmpty()) {
                problems.add("el pedido no tiene productos");
            }
            if (lines.stream().anyMatch(line -> line.quantity() <= 0)) {
                problems.add("hay cantidades inválidas");
            }
            if (express && address == null) {
                problems.add("el envío exprés necesita dirección");   // regla ENTRE campos
            }
            if (coupon != null && !coupon.matches("[A-Z]+\\d{2}")) {
                problems.add("cupón con formato inválido: " + coupon);
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException("Pedido inválido: " + String.join("; ", problems));
            }
        }
    }

    static boolean builderExample() {
        ShopOrder order = ShopOrder.builder("Ana")
                .line("KBD-1", 1, "500")
                .line("MOU-1", 2, "250")
                .shipTo("Reforma 10")
                .coupon("VERANO10")
                .build();
        ShopOrder minimal = ShopOrder.builder("Luis").line("CAB-1", 1, "80").build();   // solo lo necesario

        return order.subtotal().compareTo(new BigDecimal("1000")) == 0
                && order.coupon().orElseThrow().equals("VERANO10")
                && minimal.address().isEmpty() && !minimal.giftWrap();
    }

    /*
        =================================================================
        CAPA 4) BUILD(): EL ÚNICO LUGAR DONDE SE VALIDA TODO
        =================================================================

        build() es el momento perfecto para validar, porque es el ÚNICO
        punto en el que se tienen TODOS los valores juntos:
        - campos obligatorios presentes;
        - valores válidos (cantidades positivas, formato del cupón);
        - reglas ENTRE campos que con setters no tenían dónde revisarse:
          "el envío exprés necesita dirección".

        Y como el constructor del objeto es privado, la única forma de
        obtener un ShopOrder es pasar por build(): no existe ningún pedido
        que no haya sido validado (fail fast; v3_exceptions/
        part2_in_practice/ErrorHandlingBestPractices, CAPA 3).

        El ejemplo reúne TODOS los problemas en un solo mensaje (como en un
        formulario; ErrorHandlingBestPractices, CAPA 5), en lugar de fallar
        en el primero.
    */
    static boolean validationExample() {
        try {
            ShopOrder.builder("Ana").express().coupon("descuento").build();
            return false;
        } catch (IllegalStateException e) {
            return e.getMessage().equals("Pedido inválido: el pedido no tiene productos; "
                    + "el envío exprés necesita dirección; cupón con formato inválido: descuento");
        }
    }

    /*
        =================================================================
        CAPA 5) INMUTABILIDAD Y COPIAS: EL BUILDER Y EL OBJETO SON INDEPENDIENTES
        =================================================================

        Un detalle que separa un builder profesional de uno descuidado: el
        objeto construido NO debe compartir nada mutable con el builder.

        En ShopOrder, el constructor hace List.copyOf(builder.lines). Si en
        cambio guardara la MISMA lista del builder, seguir usando el
        builder (agregar otra línea) cambiaría un pedido ya construido,
        que se suponía inmutable.

        Con copias, el builder se puede REUTILIZAR como plantilla: armar
        una base común y llamar build() varias veces, agregando cosas entre
        llamadas, y cada pedido queda independiente.
    */
    static boolean independenceExample() {
        OrderBuilder template = ShopOrder.builder("Mayoreo SA").line("CAB-1", 10, "80").shipTo("Bodega 3");
        ShopOrder first = template.build();
        template.line("KBD-1", 5, "500");                   // seguir usando el builder...
        ShopOrder second = template.build();

        return first.lines().size() == 1                    // ...no altera el pedido ya construido
                && second.lines().size() == 2;
    }

    /*
        =================================================================
        CAPA 6) BUILDER PARA RECORDS
        =================================================================

        Un record (v2_oop/part4_advanced_types/Records) ya es inmutable y
        valida en su constructor compacto. Pero su constructor canónico
        tiene TODOS los componentes, en orden: con muchos opcionales vuelve
        el problema de la CAPA 1.

        La solución: el record conserva su constructor (y su validación), y
        se le agrega un builder estático que solo junta los valores y llama
        al constructor al final. La validación sigue en UN lugar: el
        constructor compacto, que corre siempre.

        Para "modificar" un record ya creado en uno o dos campos, en vez de
        un builder se usan métodos "with" (Records, CAPA 8).
    */
    record Notification(String to, String title, String body, String channel, int priority, boolean silent) {
        Notification {
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(title, "title");
            if (priority < 1 || priority > 5) {
                throw new IllegalArgumentException("Prioridad entre 1 y 5");
            }
        }

        static NotificationBuilder to(String recipient) {
            return new NotificationBuilder(recipient);
        }

        static final class NotificationBuilder {
            private final String to;
            private String title = "";
            private String body = "";
            private String channel = "email";               // valores por defecto en un solo lugar
            private int priority = 3;
            private boolean silent;

            private NotificationBuilder(String to) {
                this.to = to;
            }

            NotificationBuilder title(String title) {
                this.title = title;
                return this;
            }

            NotificationBuilder body(String body) {
                this.body = body;
                return this;
            }

            NotificationBuilder viaSms() {
                this.channel = "sms";
                return this;
            }

            NotificationBuilder urgent() {
                this.priority = 5;
                return this;
            }

            NotificationBuilder silent() {
                this.silent = true;
                return this;
            }

            Notification build() {
                return new Notification(to, title, body, channel, priority, silent);   // la validación es la del record
            }
        }
    }

    static boolean recordBuilderExample() {
        Notification alert = Notification.to("ana").title("Pago rechazado").viaSms().urgent().build();
        Notification plain = Notification.to("luis").title("Bienvenido").build();
        return alert.channel().equals("sms") && alert.priority() == 5
                && plain.channel().equals("email") && plain.priority() == 3 && !plain.silent();
    }

    /*
        =================================================================
        CAPA 7) STEP BUILDER: OBLIGAR LOS PASOS CON TIPOS
        =================================================================

        El builder normal valida lo obligatorio AL EJECUTAR build(). Si
        alguien olvida un dato obligatorio, se entera al correr el programa.

        El STEP BUILDER (builder por pasos) usa el SISTEMA DE TIPOS para
        que sea imposible saltarse un paso: cada método devuelve una
        interface DISTINTA que solo ofrece el siguiente paso.

            Shipment.from("CDMX")       -> devuelve DestinationStep (solo tiene .to)
                    .to("MTY")          -> devuelve WeightStep      (solo tiene .weighing)
                    .weighing(3_000)    -> devuelve OptionalSteps   (opcionales y build)
                    .insured()
                    .build();

        Escribir Shipment.from("CDMX").build() ni siquiera compila: el
        objeto que devuelve from no tiene método build. El IDE, además,
        sugiere exactamente el siguiente paso.

        Cuesta más código (una interface por paso). Vale la pena en APIs
        públicas o muy usadas, donde un error de uso es caro.
    */
    record Shipment(String origin, String destination, int grams, boolean insured) {
        static DestinationStep from(String origin) {
            return new Steps(origin);
        }

        interface DestinationStep {
            WeightStep to(String destination);
        }

        interface WeightStep {
            OptionalSteps weighing(int grams);
        }

        interface OptionalSteps {
            OptionalSteps insured();

            Shipment build();
        }

        private static final class Steps implements DestinationStep, WeightStep, OptionalSteps {
            private final String origin;
            private String destination;
            private int grams;
            private boolean insured;

            private Steps(String origin) {
                this.origin = origin;
            }

            @Override
            public WeightStep to(String destination) {
                this.destination = destination;
                return this;
            }

            @Override
            public OptionalSteps weighing(int grams) {
                this.grams = grams;
                return this;
            }

            @Override
            public OptionalSteps insured() {
                this.insured = true;
                return this;
            }

            @Override
            public Shipment build() {
                return new Shipment(origin, destination, grams, insured);
            }
        }
    }

    static boolean stepBuilderExample() {
        Shipment shipment = Shipment.from("CDMX").to("MTY").weighing(3_000).insured().build();
        // ERROR (no compila): Shipment.from("CDMX").build() -> DestinationStep no tiene build()
        return shipment.destination().equals("MTY") && shipment.grams() == 3_000 && shipment.insured();
    }

    /*
        =================================================================
        CAPA 8) BUILDERS EN EL JDK
        =================================================================

        Una vez que conoces la forma, la ves en todos lados:

        - StringBuilder: acumula texto con append (mutable, barato) y al
          final toString() produce el String inmutable
          (v1_fundamentals/part3_text_and_references/Strings).
        - HttpRequest.newBuilder(uri).header(...).timeout(...).POST(...)
          .build() y HttpClient.newBuilder() (v8_apis/part2_io_and_network/
          HttpClientUsage, CAPA 3): exactamente el builder de la CAPA 3.
        - Locale.Builder: arma un Locale validando cada parte.
        - Stream.builder(): agrega elementos y build() da el Stream.
        - ProcessBuilder: arma un proceso del sistema operativo (CAPA 9).

        Fíjate en las convenciones del JDK: newBuilder() o builder() para
        empezar, métodos con el nombre del dato (sin "set"), y build().
    */
    static boolean jdkBuildersExample() {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.tienda.com/pedidos"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();                                   // no envía nada: solo construye la petición

        Locale locale = new Locale.Builder().setLanguage("es").setRegion("MX").build();
        List<String> streamed = Stream.<String>builder().add("a").add("b").build().toList();
        String text = new StringBuilder("Hola").append(", ").append("mundo").toString();

        return request.method().equals("POST") && request.timeout().orElseThrow().getSeconds() == 5
                && locale.toLanguageTag().equals("es-MX")
                && streamed.equals(List.of("a", "b"))
                && text.equals("Hola, mundo");
    }

    /*
        =================================================================
        CAPA 9) PROCESSBUILDER: ARMAR UN PROCESO DEL SISTEMA OPERATIVO
        =================================================================

        ProcessBuilder es un builder de algo muy concreto: un PROCESO nuevo
        del sistema operativo (otro programa que corre al lado del tuyo).
        Sus "campos" son justo lo que un proceso necesita para nacer:

            command(...)        el programa y sus argumentos
            directory(...)      su DIRECTORIO DE TRABAJO (v8_apis/
                                part2_io_and_network/FilesAndIO, CAPA 4)
            environment()       sus VARIABLES DE ENTORNO (un mapa que
                                puedes modificar antes de lanzarlo)
            redirectErrorStream(true)   unir su salida de errores con la normal

        Configurarlo no crea nada. start() es el "build": le pide al
        sistema operativo que cree el proceso. En Linux, la idea es fork
        (copiar el proceso actual) y exec (reemplazarlo por el programa
        pedido); el JDK usa posix_spawn, que hace ese trabajo de forma más
        eficiente. En Windows, CreateProcess.
        El proceso hijo hereda lo que el builder configuró, y se conecta con
        el tuyo por TUBERÍAS (pipes): lo que escribe en su salida estándar
        (su descriptor 1), tú lo lees con process.getInputStream().

        Y como cualquier builder, se puede reutilizar: llamar start() varias
        veces lanza varios procesos con la misma configuración.

        El ejemplo lanza de verdad la misma JVM con -version, en la carpeta
        temporal, con una variable de entorno propia, lee lo que imprime y
        espera a que termine (waitFor devuelve su código de salida: 0 =
        terminó bien). Por eso main declara throws Exception.
    */
    static boolean processBuilderExample() throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        ProcessBuilder builder = new ProcessBuilder(javaExecutable.toString(), "-version")
                .directory(Path.of(System.getProperty("java.io.tmpdir")).toFile())   // su cwd
                .redirectErrorStream(true);                 // java -version escribe en la salida de errores
        builder.environment().put("TIENDA_MODO", "prueba"); // variable de entorno solo para el hijo

        Process child = builder.start();                    // aquí el sistema operativo crea el proceso
        String output;
        try (var out = child.getInputStream()) {            // la tubería conectada a su salida
            output = new String(out.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = child.waitFor();

        return exitCode == 0
                && output.contains("version")
                && child.pid() != ProcessHandle.current().pid()      // otro proceso, con otro PID
                && builder.environment().get("TIENDA_MODO").equals("prueba");
    }

    /*
        =================================================================
        CAPA 10) BUILDER, CONSTRUCTOR, FÁBRICA O "WITH"
        =================================================================

        | Situación                                        | Usa                                   |
        |--------------------------------------------------|---------------------------------------|
        | Pocos parámetros (hasta 3), todos obligatorios   | constructor o record                  |
        | Varias formas de crear con significado distinto  | métodos de fábrica estáticos con      |
        |                                                  | nombre (v2_oop/part1_basics/Static)   |
        | Muchos parámetros, varios opcionales, reglas     | Builder                               |
        | entre campos                                     |                                       |
        | Obligatorios que no se deben poder olvidar, en   | Step builder                          |
        | una API pública                                  |                                       |
        | Cambiar uno o dos campos de un objeto existente  | métodos "with" en el record           |
        | Varios objetos coherentes entre sí               | part1_creational/AbstractFactory      |

        Señal para pasar a Builder: el constructor llegó a 4 o más
        parámetros, o aparecen null y false "de relleno" en las llamadas.
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Constructores con muchos parámetros del mismo tipo y valores de
           relleno (CAPA 1).

        2) Armar objetos importantes con setters: quedan a medias y no
           pueden ser inmutables (CAPA 2).

        3) Dejar público el constructor del objeto: se puede crear sin
           pasar por la validación de build() (CAPA 3).

        4) No validar en build(), o validar en cada método del builder
           reglas que dependen de varios campos (CAPA 4).

        5) Compartir colecciones mutables entre el builder y el objeto
           (CAPA 5).

        6) Builders para objetos de dos campos (CAPA 10).

        7) Olvidar que start() de ProcessBuilder crea un proceso real del
           sistema operativo que hay que esperar (waitFor) y cuyas salidas
           hay que leer; si nadie lee, el hijo puede quedarse bloqueado
           cuando se llena la tubería (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Problemas: constructores con muchos parámetros (ilegibles,
          intercambiables por error) y setters (objetos a medias,
          mutables).
        - Builder: objeto inmutable con constructor privado; builder
          anidado con métodos con nombre que devuelven this; obligatorios al
          empezar; build() valida todo y crea.
        - build() es el único lugar con todos los datos: ahí van las reglas
          entre campos.
        - Copias defensivas: el builder se puede reutilizar como plantilla.
        - Records: conservan su validación; el builder solo junta valores.
        - Step builder: el compilador obliga el orden de los pasos.
        - En el JDK: StringBuilder, HttpRequest/HttpClient.newBuilder,
          Locale.Builder, Stream.builder, ProcessBuilder (que crea
          procesos del sistema operativo con fork/exec o CreateProcess).
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
