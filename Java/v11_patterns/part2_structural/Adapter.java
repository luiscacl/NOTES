package v11_patterns.part2_structural;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/*
    =====================================================================
    ADAPTER: HACER QUE ENCAJE LO QUE NO ENCAJA
    =====================================================================

    Ruta de patterns/: guía 6 de 28  (part2_structural)
    Anterior:  part1_creational/Singleton
    Siguiente: part2_structural/Bridge

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: ALTA. Cada vez que integras un servicio externo, una
    librería o código heredado, escribes un adaptador.

    Qué se da por sabido: el ejemplo corto de adaptador de GRASP
    (v10_design/part3_responsibilities/GRASP, CAPA 8: Indirection) y la
    inversión de dependencias (v10_design/part2_solid/DependencyInversion).

    Índice:
        CAPA 1)  El problema: dos formas que no encajan
        CAPA 2)  La solución: un objeto que traduce
        CAPA 3)  Qué traduce un adaptador
        CAPA 4)  Varios adaptadores, una sola interface
        CAPA 5)  Qué NO debe hacer un adaptador
        CAPA 6)  Adaptar con una lambda
        CAPA 7)  Adaptadores en el JDK
        CAPA 8)  Puertos y adaptadores: el patrón en la arquitectura
        CAPA 9)  Adapter, Facade, Decorator y Proxy
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class Adapter {

    public static void main(String[] args) throws Exception {
        check(adapterExample(), "CAPA 2");
        check(translationExample(), "CAPA 3");
        check(severalAdaptersExample(), "CAPA 4");
        check(lambdaAdapterExample(), "CAPA 6");
        check(jdkAdaptersExample(), "CAPA 7");
        System.out.println("Adapter: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: DOS FORMAS QUE NO ENCAJAN
        =================================================================

        Como un adaptador de enchufe en un viaje: tu cargador tiene una
        forma, la pared otra, y ninguno de los dos se puede cambiar.

        En código pasa todo el tiempo. Tu tienda define, en SUS términos,
        lo que necesita para cotizar envíos:

            interface ShippingQuoter {
                Optional<Quote> quote(Parcel parcel);   // gramos, código postal, BigDecimal
            }

        Pero la paquetería te da su librería (su "SDK"), con SU forma:
        - el peso en KILOGRAMOS como double;
        - el código postal como int;
        - la tarifa en CENTAVOS como long;
        - y si no hay cobertura, devuelve -1 (un código de error).

        No puedes cambiar la librería (no es tuya) ni deberías cambiar tu
        código de negocio para hablar en sus términos (DependencyInversion,
        CAPA 5: abstracción con fugas). Usarla directamente llenaría tu
        negocio de conversiones y de "if (rate == -1)".
    */
    record Parcel(int grams, String destinationZip, boolean express) { }

    record Quote(String carrier, BigDecimal price, int days) { }

    interface ShippingQuoter {                               // TARGET: la interface que tu código espera
        Optional<Quote> quote(Parcel parcel);
    }

    // ADAPTEE: la librería de la paquetería. No es tuya y no se puede cambiar.
    static final class FastShipClient {
        /** Devuelve la tarifa en CENTAVOS, o -1 si no hay cobertura. */
        long getRateCents(double weightKg, int zip, String serviceCode) {
            if (zip >= 90_000) {
                return -1;                                   // sin cobertura en esa zona
            }
            long base = serviceCode.equals("XPR") ? 25_000 : 12_000;
            return base + Math.round(weightKg * 1_500);
        }

        int getTransitDays(String serviceCode) {
            return serviceCode.equals("XPR") ? 1 : 4;
        }
    }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: UN OBJETO QUE TRADUCE
        =================================================================

        ADAPTER: "Convierte la interface de una clase en otra interface que
        los clientes esperan. Permite que trabajen juntas clases que de
        otro modo no podrían por tener interfaces incompatibles."

        - El adaptador IMPLEMENTA la interface que tu código espera
          (ShippingQuoter).
        - CONTIENE el objeto de la librería (FastShipClient).
        - Cada método traduce la petición a la forma de la librería, la
          llama, y traduce la respuesta de vuelta.

            Tu código  ──quote(Parcel)──>  FastShipAdapter  ──getRateCents(kg, int, "XPR")──>  FastShipClient
                       <──Optional<Quote>──                 <──────────── long centavos ──────

        Nombres del libro: TARGET (la interface que esperas), ADAPTEE (lo
        que hay que adaptar), ADAPTER (el traductor), CLIENT (tu código).

        (Existe una variante que hereda de la clase adaptada en vez de
        contenerla, el "adaptador de clase"; en Java casi no se usa porque
        solo se puede heredar de una clase y acopla de más. La de esta
        guía, que contiene al objeto, es la estándar.)
    */
    static final class FastShipAdapter implements ShippingQuoter {   // ADAPTER
        private final FastShipClient client;                // contiene al adaptado

        FastShipAdapter(FastShipClient client) {
            this.client = client;
        }

        @Override
        public Optional<Quote> quote(Parcel parcel) {
            String service = parcel.express() ? "XPR" : "STD";                      // tus conceptos -> los suyos
            double kilograms = parcel.grams() / 1_000.0;                            // gramos -> kg
            int zip = Integer.parseInt(parcel.destinationZip());                    // String -> int
            long cents = client.getRateCents(kilograms, zip, service);
            if (cents < 0) {
                return Optional.empty();                                            // código de error -> Optional vacío
            }
            BigDecimal price = BigDecimal.valueOf(cents, 2);                        // centavos -> pesos exactos
            return Optional.of(new Quote("FastShip", price, client.getTransitDays(service)));
        }
    }

    static boolean adapterExample() {
        ShippingQuoter quoter = new FastShipAdapter(new FastShipClient());   // tu código solo ve ShippingQuoter
        Optional<Quote> quote = quoter.quote(new Parcel(2_000, "44100", false));
        return quote.isPresent()
                && quote.get().price().compareTo(new BigDecimal("150.00")) == 0   // 12,000 + 3,000 centavos
                && quote.get().days() == 4;
    }

    /*
        =================================================================
        CAPA 3) QUÉ TRADUCE UN ADAPTADOR
        =================================================================

        La traducción casi siempre cubre estas cinco cosas, y conviene
        revisarlas todas al escribir uno:

        1) NOMBRES y CONCEPTOS: express=true -> serviceCode "XPR".
        2) UNIDADES: gramos -> kilogramos; centavos -> pesos.
        3) TIPOS: String -> int; long -> BigDecimal; sus clases -> tus
           records.
        4) AUSENCIA y ERRORES: el -1 de la librería -> Optional.empty(); sus
           excepciones técnicas -> excepciones de tu negocio, con la
           original como causa (DependencyInversion, CAPA 6).
        5) DATOS INVÁLIDOS: lo que la librería no acepta (un código postal
           con letras) se detecta en el adaptador con un mensaje claro, en
           vez de dejar que falle adentro de la librería con un error
           confuso.

        El ejemplo revisa los puntos 4 y 5: la zona sin cobertura sale como
        Optional vacío, y un código postal inválido falla con un mensaje en
        términos del negocio.
    */
    static final class InvalidParcelException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidParcelException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class SafeFastShipAdapter implements ShippingQuoter {
        private final FastShipAdapter inner;

        SafeFastShipAdapter(FastShipClient client) {
            this.inner = new FastShipAdapter(client);
        }

        @Override
        public Optional<Quote> quote(Parcel parcel) {
            try {
                return inner.quote(parcel);
            } catch (NumberFormatException technical) {     // el error técnico, traducido
                throw new InvalidParcelException("Código postal inválido: " + parcel.destinationZip(), technical);
            }
        }
    }

    static boolean translationExample() {
        ShippingQuoter quoter = new SafeFastShipAdapter(new FastShipClient());
        boolean noCoverageIsEmpty = quoter.quote(new Parcel(1_000, "97000", false)).isEmpty();

        boolean invalidZipExplained;
        try {
            quoter.quote(new Parcel(1_000, "44A00", false));
            invalidZipExplained = false;
        } catch (InvalidParcelException e) {
            invalidZipExplained = e.getMessage().equals("Código postal inválido: 44A00")
                    && e.getCause() instanceof NumberFormatException;
        }
        return noCoverageIsEmpty && invalidZipExplained;
    }

    /*
        =================================================================
        CAPA 4) VARIOS ADAPTADORES, UNA SOLA INTERFACE
        =================================================================

        El verdadero poder aparece con VARIAS librerías incompatibles entre
        sí. Una segunda paquetería tiene una API completamente distinta
        (otro nombre de método, el precio como String con el símbolo de
        moneda, el peso en libras...). Con un adaptador para cada una:
        - las dos se ven IGUALES para tu código: ShippingQuoter;
        - puedes recorrerlas en una lista y elegir la más barata, sin un
          solo if por paquetería (polimorfismo; v10_design/part2_solid/
          OpenClosed);
        - agregar una tercera es escribir otro adaptador.
    */
    // Otra librería, con otra forma completamente distinta.
    static final class EcoParcelApi {
        Map<String, String> estimate(double pounds, String postalCode) {
            if (postalCode.startsWith("0")) {
                return Map.of("status", "NO_SERVICE");
            }
            double price = 80 + pounds * 20;
            return Map.of("status", "OK", "price", "MXN " + String.format(Locale.ROOT, "%.2f", price), "eta", "6");
        }
    }

    static final class EcoParcelAdapter implements ShippingQuoter {
        private static final double GRAMS_PER_POUND = 453.592;
        private final EcoParcelApi api;

        EcoParcelAdapter(EcoParcelApi api) {
            this.api = api;
        }

        @Override
        public Optional<Quote> quote(Parcel parcel) {
            if (parcel.express()) {
                return Optional.empty();                    // esta paquetería no tiene servicio exprés
            }
            Map<String, String> response = api.estimate(parcel.grams() / GRAMS_PER_POUND, parcel.destinationZip());
            if (!"OK".equals(response.get("status"))) {
                return Optional.empty();
            }
            BigDecimal price = new BigDecimal(response.get("price").replace("MXN ", "")).setScale(2, RoundingMode.HALF_UP);
            return Optional.of(new Quote("EcoParcel", price, Integer.parseInt(response.get("eta"))));
        }
    }

    static Optional<Quote> cheapest(List<ShippingQuoter> quoters, Parcel parcel) {   // no conoce ninguna paquetería
        return quoters.stream()
                .map(quoter -> quoter.quote(parcel))
                .flatMap(Optional::stream)
                .min(Comparator.comparing(Quote::price));
    }

    static boolean severalAdaptersExample() {
        List<ShippingQuoter> carriers = List.of(new FastShipAdapter(new FastShipClient()), new EcoParcelAdapter(new EcoParcelApi()));
        Parcel heavy = new Parcel(5_000, "44100", false);   // FastShip: 195.00; EcoParcel: 80 + 11.02 lb * 20 = 300.46
        Parcel light = new Parcel(500, "44100", false);     // FastShip: 127.50; EcoParcel: 80 + 1.10 lb * 20 = 102.05
        Parcel expressParcel = new Parcel(500, "44100", true);

        return cheapest(carriers, heavy).orElseThrow().carrier().equals("FastShip")
                && cheapest(carriers, light).orElseThrow().carrier().equals("EcoParcel")
                && cheapest(carriers, expressParcel).orElseThrow().carrier().equals("FastShip");   // la única con exprés
    }

    /*
        =================================================================
        CAPA 5) QUÉ NO DEBE HACER UN ADAPTADOR
        =================================================================

        Un adaptador TRADUCE. Debe ser delgado y "tonto":
        - NO toma decisiones de negocio (qué paquetería elegir, si aplica
          envío gratis): eso va en tu código, que trabaja con la interface
          (como cheapest en la CAPA 4).
        - NO agrega funcionalidad nueva: si quieres caché, reintentos o logs
          alrededor, eso es un Decorator (part2_structural/Decorator).
        - NO expone tipos de la librería en su interface pública: si
          ShippingQuoter devolviera un objeto de FastShip, la librería se
          "filtraría" a todo tu código.

        Se prueba de dos formas: con la librería falsa (para revisar la
        traducción, como aquí) y con la librería real contra el servicio de
        pruebas del proveedor (pruebas de integración; testing/).
    */

    /*
        =================================================================
        CAPA 6) ADAPTAR CON UNA LAMBDA
        =================================================================

        Si la interface que esperas es FUNCIONAL (un solo método; v2_oop/
        part3_interfaces_and_relationships/Interfaces, CAPA 8), el
        adaptador puede ser una LAMBDA o una REFERENCIA A MÉTODO, sin
        escribir una clase:

            ShippingQuoter flat = parcel -> Optional.of(new Quote(...legacy.flatRate()...));
            Comparator<Product> byPrice = legacy::comparePrices;   // un método con la forma de compare

        Las referencias a métodos son, de hecho, adaptadores automáticos:
        convierten un método que ya existe en una implementación de la
        interface que pide el código (v7_functional/part1_lambdas/
        MethodReferences). Para traducciones de varias líneas, una clase
        con nombre se lee y se prueba mejor.
    */
    static final class LegacyLocalCourier {                 // código heredado: tarifa fija por zona
        int flatRateInPesos(boolean sameCity) {
            return sameCity ? 60 : 140;
        }
    }

    static boolean lambdaAdapterExample() {
        LegacyLocalCourier legacy = new LegacyLocalCourier();
        ShippingQuoter courier = parcel -> Optional.of(new Quote(       // un adaptador en una expresión
                "Mensajería local",
                BigDecimal.valueOf(legacy.flatRateInPesos(parcel.destinationZip().startsWith("44"))).setScale(2),
                1));

        return courier.quote(new Parcel(800, "44100", false)).orElseThrow().price().compareTo(new BigDecimal("60.00")) == 0
                && cheapest(List.of(courier, new FastShipAdapter(new FastShipClient())), new Parcel(800, "44100", false))
                        .orElseThrow().carrier().equals("Mensajería local");
    }

    /*
        =================================================================
        CAPA 7) ADAPTADORES EN EL JDK
        =================================================================

        - InputStreamReader: adapta un InputStream (bytes) a un Reader
          (caracteres), traduciendo con un charset
          (v8_apis/part2_io_and_network/FilesAndIO, CAPA 9). Es el adaptador
          más usado del JDK.
        - Arrays.asList(arreglo): adapta un arreglo a la interface List, sin
          copiarlo: escribir en la lista escribe en el arreglo
          (v1_fundamentals/part4_data_and_input/Arrays, CAPA 7).
        - Executors.callable(runnable, resultado): adapta un Runnable (no
          devuelve nada) a un Callable (devuelve un valor), para usarlo
          donde se pide un Callable (v13_concurrency/).
        - Collections.enumeration(coleccion): adapta una colección moderna a
          la vieja interface Enumeration, para código antiguo que todavía la
          pide (es una VISTA: no copia). Su pareja Collections.list(...) va
          al revés, pero NO es un adaptador: COPIA los elementos a una
          ArrayList nueva (medido: agregar a la lista no cambia el origen).
    */
    static boolean jdkAdaptersExample() throws Exception {
        String[] backing = {"a", "b"};
        List<String> view = Arrays.asList(backing);         // el arreglo, visto como List
        view.set(0, "z");

        StringBuilder log = new StringBuilder();
        Callable<String> adapted = Executors.callable(() -> log.append("respaldo listo"), "OK");   // Runnable -> Callable

        return backing[0].equals("z") && adapted.call().equals("OK") && log.toString().equals("respaldo listo");
    }

    /*
        =================================================================
        CAPA 8) PUERTOS Y ADAPTADORES: EL PATRÓN EN LA ARQUITECTURA
        =================================================================

        Llevado a todo un sistema, este patrón le da nombre a una de las
        arquitecturas más usadas: PUERTOS Y ADAPTADORES (también llamada
        arquitectura hexagonal; v14_architecture/).

        - PUERTOS: las interfaces que define tu negocio, en sus términos
          (ShippingQuoter, OrderRepository, PaymentGateway).
        - ADAPTADORES: las implementaciones que conectan esos puertos con el
          mundo exterior: uno para la base de datos, uno para cada
          paquetería, uno para la pasarela de pagos, uno para recibir
          peticiones web.

        El negocio queda en el centro sin depender de nada técnico; cada
        tecnología es un adaptador reemplazable. Es la inversión de
        dependencias (v10_design/part2_solid/DependencyInversion, CAPA 11)
        con los adaptadores de esta guía en los bordes.
    */

    /*
        =================================================================
        CAPA 9) ADAPTER, FACADE, DECORATOR Y PROXY
        =================================================================

        Los cuatro "envuelven" a otro objeto, y por eso se confunden. La
        diferencia es la INTENCIÓN:

        | Patrón    | Envuelve para...                               | ¿Cambia la interface?          |
        |-----------|------------------------------------------------|--------------------------------|
        | Adapter   | hacer que algo ENCAJE en la interface que       | SÍ: de la suya a la tuya       |
        |           | esperas                                        |                                |
        | Facade    | SIMPLIFICAR un subsistema complicado           | ofrece una nueva, más simple   |
        | Decorator | AGREGAR comportamiento (logs, caché, reintentos)| NO: la misma interface         |
        | Proxy     | CONTROLAR el acceso (permisos, carga diferida, | NO: la misma interface         |
        |           | llamada remota)                                |                                |
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Usar la librería externa directamente en todo el negocio, con
           sus tipos, unidades y códigos de error (CAPA 1).

        2) Traducir a medias: olvidar unidades, el -1 o las excepciones
           técnicas (CAPA 3).

        3) Meter lógica de negocio o funcionalidad extra en el adaptador
           (CAPA 5).

        4) Una interface "adaptada" que expone tipos de la librería
           (CAPA 5).

        5) Adaptadores de clase (por herencia) cuando el de objeto es más
           simple y menos acoplado (CAPA 2).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Adapter: un objeto que implementa la interface que tu código
          espera y traduce hacia una clase incompatible que contiene.
        - Traduce nombres, unidades, tipos, ausencias/errores y validaciones.
        - Varios adaptadores detrás de una interface = polimorfismo sobre
          APIs incompatibles; agregar otra es otro adaptador.
        - Delgado: sin negocio, sin funcionalidad extra, sin filtrar tipos
          externos.
        - Si la interface es funcional, basta una lambda o referencia a
          método.
        - En el JDK: InputStreamReader, Arrays.asList, Executors.callable.
        - En la arquitectura: puertos (tus interfaces) y adaptadores (las
          tecnologías).
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
