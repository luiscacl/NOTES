package v11_patterns.part2_structural;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/*
    =====================================================================
    FACADE: UNA PUERTA SIMPLE A UN SUBSISTEMA COMPLICADO
    =====================================================================

    Ruta de patterns/: guía 10 de 28  (part2_structural)
    Anterior:  part2_structural/Decorator
    Siguiente: part2_structural/Flyweight

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: MEDIA-ALTA. Lo escribirás seguido (a veces sin llamarlo
    así) y lo usas todos los días: java.nio.file.Files es una fachada.

    Índice:
        CAPA 1)  El problema: el cliente tiene que saber demasiado
        CAPA 2)  La solución: una fachada que coordina
        CAPA 3)  La fachada coordina; las reglas viven en el subsistema
        CAPA 4)  Fachadas sobre librerías complicadas
        CAPA 5)  Fachadas en el JDK
        CAPA 6)  La fachada como la puerta de un módulo
        CAPA 7)  Facade, Adapter y Mediator
        CAPA 8)  Errores comunes
        RESUMEN FINAL
*/
public class Facade {

    public static void main(String[] args) {
        check(clientKnowsTooMuchExample(), "CAPA 1");
        check(facadeExample(), "CAPA 2");
        check(compensationExample(), "CAPA 3");
        check(libraryFacadeExample(), "CAPA 4");
        System.out.println("Facade: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: EL CLIENTE TIENE QUE SABER DEMASIADO
        =================================================================

        Comprar en la tienda involucra varias piezas (un "subsistema"),
        cada una bien diseñada y con su responsabilidad:
            Inventory   reservar y liberar existencias
            Payments    autorizar un cobro
            Invoicing   emitir la factura
            Shipping    generar la guía de envío

        Para comprar hay que usarlas TODAS, en el ORDEN correcto, y
        deshacer pasos si algo falla. Si cada cliente (la página web, la
        app móvil, la importación de pedidos por lotes, el call center) lo
        hace por su cuenta:
        - todos tienen que conocer las cuatro piezas y el orden;
        - el proceso se REPITE en cada cliente;
        - basta que uno lo haga en otro orden para un error caro: cobrar
          antes de reservar, y que no haya existencias.

        El ejemplo: un cliente que cobra primero. Cuando no hay
        existencias, el cobro YA se hizo.
    */
    record OrderRequest(String customer, String sku, int quantity, BigDecimal amount, String address) { }

    static final class Inventory {
        private final Map<String, Integer> stock = new HashMap<>(Map.of("KBD-1", 5, "MOU-1", 0));

        boolean reserve(String sku, int quantity) {
            int available = stock.getOrDefault(sku, 0);
            if (available < quantity) {
                return false;
            }
            stock.put(sku, available - quantity);
            return true;
        }

        void release(String sku, int quantity) {
            stock.merge(sku, quantity, Integer::sum);
        }

        int available(String sku) {
            return stock.getOrDefault(sku, 0);
        }
    }

    static final class Payments {
        final List<String> charges = new ArrayList<>();

        boolean authorize(String customer, BigDecimal amount) {
            if (amount.compareTo(new BigDecimal("10000")) > 0) {
                return false;                               // rechazado por el banco
            }
            charges.add(customer + ":" + amount);
            return true;
        }
    }

    static final class Invoicing {
        private int folio;

        String issue(String customer, BigDecimal amount) {
            return "F-" + (++folio) + " " + customer + " $" + amount;
        }
    }

    static final class Shipping {
        String label(String address, String sku, int quantity) {
            return "GUIA[" + address + "|" + sku + "x" + quantity + "]";
        }
    }

    // Un cliente que coordina todo por su cuenta... en el orden equivocado.
    static String carelessCheckout(Inventory inventory, Payments payments, OrderRequest request) {
        payments.authorize(request.customer(), request.amount());          // cobra PRIMERO
        if (!inventory.reserve(request.sku(), request.quantity())) {
            return "sin existencias";                                       // ...y el cobro ya se hizo
        }
        return "ok";
    }

    static boolean clientKnowsTooMuchExample() {
        Inventory inventory = new Inventory();
        Payments payments = new Payments();
        String result = carelessCheckout(inventory, payments, new OrderRequest("ana", "MOU-1", 1, new BigDecimal("250"), "Reforma 10"));
        return result.equals("sin existencias") && payments.charges.size() == 1;   // cobró algo que no puede entregar
    }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: UNA FACHADA QUE COORDINA
        =================================================================

        FACADE: "Proporciona una interface UNIFICADA y simple a un conjunto
        de interfaces de un subsistema. Define una interface de más alto
        nivel que hace el subsistema más fácil de usar."

        Como la fachada de un edificio: por fuera, una puerta; por dentro,
        tuberías, cables y elevadores que el visitante no necesita conocer.

        CheckoutFacade.placeOrder(request) es la ÚNICA operación que los
        clientes necesitan. Por dentro:
        1) reserva existencias;
        2) autoriza el pago;
        3) emite la factura;
        4) genera la guía;
        y devuelve un resultado simple.

        Ahora el orden correcto está escrito UNA vez; la web, la app y la
        importación por lotes llaman a placeOrder y nada más. Si mañana
        se agrega un paso (antifraude), cambia la fachada, no los cuatro
        clientes.
    */
    record OrderResult(boolean placed, String detail) { }

    static final class CheckoutFacade {                    // FACADE
        private final Inventory inventory;
        private final Payments payments;
        private final Invoicing invoicing;
        private final Shipping shipping;

        CheckoutFacade(Inventory inventory, Payments payments, Invoicing invoicing, Shipping shipping) {
            this.inventory = inventory;
            this.payments = payments;
            this.invoicing = invoicing;
            this.shipping = shipping;
        }

        OrderResult placeOrder(OrderRequest request) {
            if (!inventory.reserve(request.sku(), request.quantity())) {
                return new OrderResult(false, "sin existencias");           // nada que deshacer
            }
            if (!payments.authorize(request.customer(), request.amount())) {
                inventory.release(request.sku(), request.quantity());       // deshacer la reserva (CAPA 3)
                return new OrderResult(false, "pago rechazado");
            }
            String invoice = invoicing.issue(request.customer(), request.amount());
            String label = shipping.label(request.address(), request.sku(), request.quantity());
            return new OrderResult(true, invoice + " | " + label);
        }
    }

    static boolean facadeExample() {
        Inventory inventory = new Inventory();
        Payments payments = new Payments();
        CheckoutFacade checkout = new CheckoutFacade(inventory, payments, new Invoicing(), new Shipping());

        OrderResult ok = checkout.placeOrder(new OrderRequest("ana", "KBD-1", 2, new BigDecimal("1000"), "Reforma 10"));
        OrderResult noStock = checkout.placeOrder(new OrderRequest("luis", "MOU-1", 1, new BigDecimal("250"), "Juárez 5"));

        return ok.placed() && ok.detail().equals("F-1 ana $1000 | GUIA[Reforma 10|KBD-1x2]")
                && !noStock.placed() && payments.charges.size() == 1;      // a Luis no se le cobró
    }

    /*
        =================================================================
        CAPA 3) LA FACHADA COORDINA; LAS REGLAS VIVEN EN EL SUBSISTEMA
        =================================================================

        La fachada es responsable del ORDEN y de deshacer pasos cuando algo
        falla (en el ejemplo: si el pago se rechaza, libera la reserva; a
        esto se le llama "compensar"). Eso es SU responsabilidad: orquestar
        (v10_design/part2_solid/SingleResponsibility, CAPA 6).

        Lo que NO debe contener son las REGLAS de cada pieza: cuánto
        inventario hay, cuándo el banco rechaza, cómo se numera una
        factura. Esas viven en Inventory, Payments, Invoicing. Si la
        fachada empieza a calcular descuentos o validar direcciones, se
        convierte en el "servicio que hace todo" y pierde su valor.

        Y una fachada no es una JAULA: simplifica el caso común, pero no
        prohíbe que un cliente avanzado use una pieza directamente (el
        reporte de inventario puede usar Inventory sin pasar por el
        checkout). Si quieres impedirlo, eso se logra con la visibilidad
        (CAPA 6).

        El ejemplo verifica la compensación: tras un pago rechazado, las
        existencias vuelven a estar disponibles.
    */
    static boolean compensationExample() {
        Inventory inventory = new Inventory();
        CheckoutFacade checkout = new CheckoutFacade(inventory, new Payments(), new Invoicing(), new Shipping());

        OrderResult rejected = checkout.placeOrder(new OrderRequest("mia", "KBD-1", 3, new BigDecimal("50000"), "Centro"));
        return !rejected.placed() && rejected.detail().equals("pago rechazado")
                && inventory.available("KBD-1") == 5;      // la reserva se deshizo
    }

    /*
        =================================================================
        CAPA 4) FACHADAS SOBRE LIBRERÍAS COMPLICADAS
        =================================================================

        El otro uso diario: muchas APIs son poderosas pero incómodas para
        el caso común. Una fachada propia expone SOLO lo que tu proyecto
        necesita, con nombres de tu proyecto, y esconde la ceremonia.

        Ejemplo con el JDK: calcular la huella SHA-256 de un texto (para
        verificar que un archivo no cambió, o para no guardar un dato
        sensible tal cual) requiere:
        - pedir el algoritmo a MessageDigest (y manejar
          NoSuchAlgorithmException, una excepción checked que con
          "SHA-256" nunca ocurre, porque todo JDK está obligado a traerlo);
        - convertir el texto a bytes con un charset;
        - calcular;
        - convertir los bytes resultantes a texto hexadecimal.

        Checksums.sha256(texto) lo hace en una línea para todo el
        proyecto, en un solo lugar. Si un día cambia el algoritmo, cambia
        aquí.

        (Diferencia con un Adapter: el adaptador hace que algo encaje en
        una interface que YA existe; la fachada INVENTA una interface nueva
        y más simple. part2_structural/Adapter, CAPA 9.)
    */
    static final class Checksums {                          // FACADE sobre MessageDigest + HexFormat
        private Checksums() { }

        static String sha256(String text) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(hash);
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("Todo JDK debe incluir SHA-256", impossible);
            }
        }
    }

    static boolean libraryFacadeExample() {
        String hash = Checksums.sha256("hola");
        return hash.equals("b221d9dbb083a7f33428d7c2a3c3198ae925614d70210e28716ccaa7cd4ddb79")
                && hash.length() == 64                      // 32 bytes = 64 caracteres hexadecimales
                && !Checksums.sha256("hola!").equals(hash);
    }

    /*
        =================================================================
        CAPA 5) FACHADAS EN EL JDK
        =================================================================

        - java.nio.file.Files: Files.readString(path) esconde abrir el
          archivo (pedir un descriptor al sistema operativo), leer en
          bloques, decodificar el charset y cerrar
          (v8_apis/part2_io_and_network/FilesAndIO, CAPAS 1 y 6). Por
          debajo hay canales, proveedores de sistemas de archivos y
          decodificadores: la fachada te da una línea.
        - java.net.http.HttpClient: send(request, handler) esconde DNS,
          sockets, TLS, HTTP/2 y el pool de conexiones
          (v8_apis/part2_io_and_network/HttpClientUsage, CAPA 1).
        - String.format y los métodos de alto nivel de los streams también
          son, en espíritu, puertas simples a maquinaria compleja.

        Fíjate en el patrón: la fachada cubre el 90% de los casos; para el
        10% restante, el subsistema sigue disponible (FileChannel para
        escrituras con fsync, en FilesAndIO, CAPA 15).
    */

    /*
        =================================================================
        CAPA 6) LA FACHADA COMO LA PUERTA DE UN MÓDULO
        =================================================================

        A escala de proyecto, una fachada suele ser la ÚNICA clase pública
        de un paquete o módulo, y las piezas internas quedan con
        visibilidad de paquete (v2_oop/part1_basics/AccessModifiers): los
        demás módulos SOLO pueden entrar por la puerta.

            pagos/                     (paquete)
              PaymentsFacade           public:   lo que otros módulos usan
              CardProcessor            sin public: interno
              FraudCheck               sin public: interno
              BankClient               sin public: interno

        Así el módulo de pagos puede reorganizar sus piezas internas sin
        que nadie de afuera se entere. En arquitecturas modernas, a esas
        fachadas por caso de uso se les llama SERVICIOS DE APLICACIÓN
        (part4_modern/ServiceLayer; v14_architecture/).

        Cuidado con el exceso opuesto: UNA fachada para todo el sistema
        que crece hasta tener 80 métodos. Mejor una fachada por área o por
        caso de uso (CheckoutFacade, ReturnsFacade).
    */

    /*
        =================================================================
        CAPA 7) FACADE, ADAPTER Y MEDIATOR
        =================================================================

        | Patrón   | Qué hace                                      | Quién conoce a quién               |
        |----------|-----------------------------------------------|------------------------------------|
        | Facade   | simplifica: una interface nueva y sencilla     | la fachada conoce al subsistema;   |
        |          | para usar un subsistema                       | el subsistema NO conoce la fachada |
        | Adapter  | traduce: hace encajar una interface en otra   | el adaptador conoce al adaptado    |
        |          | que ya existe                                 |                                    |
        | Mediator | coordina la comunicación ENTRE piezas que se  | las piezas conocen al mediador y le|
        |          | hablan entre sí (part3_behavioral/Mediator)   | avisan; él decide quién reacciona  |
    */

    /*
        =================================================================
        CAPA 8) ERRORES COMUNES
        =================================================================

        1) Cada cliente coordinando el subsistema por su cuenta: procesos
           repetidos y órdenes equivocados (CAPA 1).

        2) Una fachada que no deshace los pasos cuando algo falla
           (CAPA 3).

        3) Reglas de negocio dentro de la fachada en vez de en las piezas
           (CAPA 3).

        4) Una sola fachada gigante para todo el sistema (CAPA 6).

        5) Fachadas cuya interface expone los tipos internos del
           subsistema: ya no simplifican nada (CAPA 4).

        6) Hacer fachadas sobre algo que ya es simple: una capa más sin
           beneficio (v10_design/part1_basics/BasicPrinciples, CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Facade: una interface simple y de alto nivel para un subsistema de
          varias piezas.
        - Escribe el orden correcto UNA vez, con su compensación si algo
          falla; los clientes solo llaman una operación.
        - Coordina; no contiene las reglas de cada pieza; no es una jaula.
        - También envuelve librerías incómodas con una API a la medida
          (Checksums.sha256).
        - En el JDK: Files, HttpClient.
        - A escala de proyecto: la clase pública de un módulo (servicio de
          aplicación), una por área o caso de uso.
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
