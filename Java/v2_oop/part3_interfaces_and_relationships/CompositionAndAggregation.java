package v2_oop.part3_interfaces_and_relationships;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/*
    =====================================================================
    COMPOSICIÓN Y AGREGACIÓN — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 9 de 14  (part3_interfaces_and_relationships)
    Anterior:  part3_interfaces_and_relationships/Interfaces
    Siguiente: part3_interfaces_and_relationships/NestedClasses

    Requisito: ClassesAndObjects y Encapsulation.
    Complementa a Inheritance: allá se ve la relación "ES UN"; aquí,
    todas las relaciones "TIENE UN" y "USA UN".

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Cómo se relacionan los objetos: las 4 relaciones
        CAPA 2)  Dependencia: "usa un" (por un momento)
        CAPA 3)  Asociación: "conoce a"
        CAPA 4)  Agregación: "tiene", pero las partes viven solas
        CAPA 5)  Composición: "es dueño de", las partes viven y mueren con él
        CAPA 6)  Delegación y composición sobre herencia
        CAPA 7)  Multiplicidad y relaciones bidireccionales
        CAPA 8)  Ley de Deméter: no hables con extraños
        CAPA 9)  Cómo decidir qué relación usar
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class CompositionAndAggregation {

    public static void main(String[] args) {
        check(dependencyExample(), "CAPA 2");
        check(associationExample(), "CAPA 3");
        check(aggregationExample(), "CAPA 4");
        check(compositionExample(), "CAPA 5");
        check(delegationExample(), "CAPA 6");
        check(bidirectionalExample(), "CAPA 7");
        check(demeterExample(), "CAPA 8");
        System.out.println("CompositionAndAggregation: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) CÓMO SE RELACIONAN LOS OBJETOS: LAS 4 RELACIONES
        =================================================================

        Ningún objeto trabaja solo. Un pedido tiene un cliente, líneas y
        productos; un recibo necesita el pedido para imprimirse. Hay
        cuatro formas de relacionarse, de la más débil a la más fuerte:

        | Relación     | Frase      | En código                         | ¿La parte vive sin el otro? |
        |--------------|------------|-----------------------------------|-----------------------------|
        | Dependencia  | USA UN     | parámetro o variable local        | Sí, ni siquiera se guarda   |
        | Asociación   | CONOCE A   | campo con un objeto de fuera      | Sí, son independientes      |
        | Agregación   | TIENE      | campo con colección de fuera      | Sí, y se puede compartir    |
        | Composición  | ES DUEÑO DE| el dueño CREA y ESCONDE sus partes| No, existe solo dentro del dueño |

        Las dos preguntas que las distinguen:
        1) ¿La parte tiene sentido sin el todo?
        2) ¿La parte se puede compartir con otros?

        Ejemplos de la vida real:
        - Un cajero USA una calculadora un momento             -> dependencia
        - Un pedido CONOCE a su cliente                         -> asociación
        - Una categoría de la tienda TIENE productos; el mismo
          producto está en varias categorías y sigue existiendo
          si borras la categoría                                -> agregación
        - Un pedido ES DUEÑO de sus líneas: "2 teclados" no
          significa nada fuera de su pedido                     -> composición
        - (Analogía clásica: una casa y sus cuartos. Un cuarto no
          existe fuera de su casa.)

        En diagramas UML se dibujan así:
            dependencia  - - - - ->       (flecha punteada)
            asociación   ---------->      (línea)
            agregación   <>---------      (rombo vacío del lado del todo)
            composición  <#>--------      (rombo relleno del lado del todo)

        Las clases de la tienda que se usan en todas las capas:
    */
    static final class Product {
        private final String sku;
        private final String name;
        private final BigDecimal price;

        Product(String sku, String name, BigDecimal price) {
            this.sku = requireText(sku, "sku");
            this.name = requireText(name, "name");
            this.price = Objects.requireNonNull(price, "price");
            if (price.signum() <= 0) {
                throw new IllegalArgumentException("El precio debe ser mayor que cero");
            }
        }

        String getSku() {
            return sku;
        }

        String getName() {
            return name;
        }

        BigDecimal getPrice() {
            return price;
        }
    }

    static final class Address {
        private final String street;
        private final String city;

        Address(String street, String city) {
            this.street = requireText(street, "street");
            this.city = requireText(city, "city");
        }

        String getStreet() {
            return street;
        }

        String getCity() {
            return city;
        }
    }

    static final class Customer {
        private final String name;
        private final Address shippingAddress;

        Customer(String name, Address shippingAddress) {
            this.name = requireText(name, "name");
            this.shippingAddress = Objects.requireNonNull(shippingAddress, "shippingAddress");
        }

        String getName() {
            return name;
        }

        Address getShippingAddress() {
            return shippingAddress;
        }

        // CAPA 8: el cliente responde por su dirección; nadie navega dentro de ella.
        String shippingCity() {
            return shippingAddress.getCity();
        }
    }

    /*
        El pedido usa TRES relaciones a la vez:
        - ASOCIACIÓN con Customer: lo recibe de fuera; el cliente existe
          antes y después del pedido, y tiene muchos pedidos.
        - COMPOSICIÓN con OrderLine: el pedido CREA sus líneas y nadie de
          fuera puede crearlas, guardarlas ni modificarlas.
        - ASOCIACIÓN de cada línea con Product: la línea apunta a un
          producto del catálogo, que existe por su cuenta.
        - Y DELEGA el cálculo del descuento en una DiscountPolicy (CAPA 6).
    */
    static final class Order {
        private final Customer customer;                          // asociación
        private final List<OrderLine> lines = new ArrayList<>();  // composición
        private DiscountPolicy discountPolicy = new NoDiscount(); // composición + delegación (CAPA 6)

        Order(Customer customer) {
            this.customer = Objects.requireNonNull(customer, "customer");
        }

        /*
            El dueño CREA la parte. Quien llama solo dice QUÉ quiere
            agregar; nunca ve ni toca un objeto OrderLine.
        */
        void addLine(Product product, int quantity) {
            for (OrderLine line : lines) {
                if (line.product.getSku().equals(product.getSku())) {
                    line.increase(quantity);   // regla del dueño: un producto, una sola línea
                    return;
                }
            }
            lines.add(new OrderLine(product, quantity));
        }

        int lineCount() {
            return lines.size();
        }

        // Se exponen DATOS de las partes, no las partes mismas.
        List<String> lineSummaries() {
            List<String> summaries = new ArrayList<>();
            for (OrderLine line : lines) {
                summaries.add(line.quantity + " x " + line.product.getName());
            }
            return summaries;
        }

        BigDecimal subtotal() {
            BigDecimal subtotal = BigDecimal.ZERO;
            for (OrderLine line : lines) {
                subtotal = subtotal.add(line.amount());
            }
            return subtotal;
        }

        BigDecimal total() {
            return discountPolicy.apply(subtotal());   // delegación
        }

        void changeDiscountPolicy(DiscountPolicy newPolicy) {
            this.discountPolicy = Objects.requireNonNull(newPolicy, "newPolicy");
        }

        Customer getCustomer() {
            return customer;
        }

        // CAPA 8: el pedido responde, en vez de que el cliente de Order navegue por dentro.
        String shippingCity() {
            return customer.shippingCity();
        }

        /*
            La PARTE de la composición: private y anidada dentro del dueño.
            Fuera de Order, el tipo OrderLine ni siquiera se puede nombrar.
        */
        private static final class OrderLine {
            private final Product product;   // asociación: el producto vive fuera
            private int quantity;

            private OrderLine(Product product, int quantity) {
                this.product = Objects.requireNonNull(product, "product");
                increase(quantity);
            }

            private void increase(int amount) {
                if (amount <= 0) {
                    throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
                }
                quantity += amount;
            }

            private BigDecimal amount() {
                return product.getPrice().multiply(BigDecimal.valueOf(quantity));
            }
        }
    }

    /*
        =================================================================
        CAPA 2) DEPENDENCIA: "USA UN" (POR UN MOMENTO)
        =================================================================

        La relación más débil: un objeto usa a otro SOLO durante un
        método. Lo recibe como parámetro (o lo crea como variable local),
        trabaja con él y lo olvida. No lo guarda en ningún campo.

        Aun así es una relación real: si Order cambia, ReceiptFormatter
        puede tener que cambiar. Por eso conviene depender de lo mínimo
        (una interface o los métodos públicos justos).
    */
    static final class ReceiptFormatter {
        // Recibe el pedido, lo usa, y no se queda con él.
        String format(Order order) {
            return order.getCustomer().getName() + " | " + order.lineCount() + " producto(s) | $" + order.total();
        }
    }

    static boolean dependencyExample() {
        Order order = new Order(sampleCustomer());
        order.addLine(new Product("TEC-01", "Teclado", new BigDecimal("800")), 1);

        return new ReceiptFormatter().format(order).equals("Ana | 1 producto(s) | $800");
    }

    /*
        =================================================================
        CAPA 3) ASOCIACIÓN: "CONOCE A"
        =================================================================

        Un objeto GUARDA una referencia a otro en un campo, para usarlo a
        lo largo de su vida. Pero no es su dueño: el otro objeto se creó
        fuera, existe por su cuenta y lo pueden conocer muchos más.

        Order conoce a su Customer: el cliente se crea una vez y lo
        conocen todos sus pedidos. Que un pedido se cancele no afecta al
        cliente.
    */
    static boolean associationExample() {
        Customer ana = sampleCustomer();
        Order first = new Order(ana);
        Order second = new Order(ana);

        // Dos pedidos conocen al MISMO cliente (misma referencia, no copias).
        return first.getCustomer() == ana && second.getCustomer() == ana;
    }

    /*
        =================================================================
        CAPA 4) AGREGACIÓN: "TIENE", PERO LAS PARTES VIVEN SOLAS
        =================================================================

        Un objeto agrupa a otros (normalmente una colección), pero esos
        otros:
        - se crearon FUERA y se los PASARON,
        - pueden estar en varios grupos a la vez,
        - siguen existiendo si el grupo desaparece.

        Ejemplo: una categoría de la tienda agrupa productos. El mismo
        teclado está en "Computación" y en "Ofertas". Si se elimina la
        categoría "Ofertas", el teclado sigue en venta.

        Mejora sobre el School de tu OOP.java: la categoría COPIA la lista
        que recibe y devuelve una vista de solo lectura. Así nadie de
        fuera puede alterar el grupo sin pasar por sus métodos
        (Encapsulation, CAPA 8). Lo que se comparte son los PRODUCTOS, no
        la LISTA.
    */
    static final class Category {
        private final String name;
        private final List<Product> products;

        Category(String name, List<Product> initialProducts) {
            this.name = requireText(name, "name");
            this.products = new ArrayList<>(initialProducts);   // copia de la lista, mismos productos
        }

        void add(Product product) {
            products.add(Objects.requireNonNull(product, "product"));
        }

        boolean contains(Product product) {
            return products.contains(product);
        }

        List<Product> getProducts() {
            return Collections.unmodifiableList(products);
        }

        String getName() {
            return name;
        }
    }

    static boolean aggregationExample() {
        Product keyboard = new Product("TEC-01", "Teclado", new BigDecimal("800"));
        Product mouse = new Product("MOU-01", "Mouse", new BigDecimal("300"));

        Category computing = new Category("Computación", List.of(keyboard, mouse));
        Category deals = new Category("Ofertas", List.of(keyboard));

        // El MISMO objeto teclado está en dos grupos.
        boolean shared = computing.contains(keyboard) && deals.contains(keyboard)
                && computing.getProducts().get(0) == deals.getProducts().get(0);

        // "Eliminar" la categoría de ofertas: el teclado sigue vivo y vendiéndose.
        deals = null;
        Order order = new Order(sampleCustomer());
        order.addLine(keyboard, 1);
        boolean partSurvives = computing.contains(keyboard) && order.lineCount() == 1;

        return shared && partSurvives && deals == null;
    }

    /*
        =================================================================
        CAPA 5) COMPOSICIÓN: "ES DUEÑO DE"
        =================================================================

        La relación más fuerte. El todo es DUEÑO de sus partes:
        - el todo CREA sus partes (addLine hace new OrderLine);
        - las partes NO se comparten con nadie;
        - las partes no tienen sentido fuera del todo;
        - cuando el todo desaparece, sus partes desaparecen con él.

        Cómo se garantiza en Java:
        1) La clase de la parte es private y anidada en el dueño, o al
           menos de paquete. Así nadie de fuera puede crear una.
        2) El dueño NUNCA devuelve referencias a sus partes; devuelve
           datos (lineSummaries) o copias.
        3) Todas las reglas que involucran a varias partes viven en el
           dueño: "un producto aparece en una sola línea" la aplica Order
           al sumar a la línea existente.

        Detalle técnico: Java no tiene "destructores". Las partes
        "mueren con el todo" porque nadie más tiene referencias a ellas:
        cuando el pedido deja de usarse, el recolector de basura libera
        el pedido y sus líneas juntos (v12_jvm/part1_memory/
        GarbageCollector). Por eso es tan importante no filtrar
        referencias: una referencia filtrada mantendría viva a una parte
        que ya no debería existir.
    */
    static boolean compositionExample() {
        Product keyboard = new Product("TEC-01", "Teclado", new BigDecimal("800"));
        Product mouse = new Product("MOU-01", "Mouse", new BigDecimal("300"));

        Order order = new Order(sampleCustomer());
        order.addLine(keyboard, 1);
        order.addLine(mouse, 2);
        order.addLine(keyboard, 1);   // mismo producto: el dueño lo suma a su línea existente

        // new Order.OrderLine(keyboard, 1);
        // Desde cualquier otro archivo esa línea NO compila: OrderLine es private dentro
        // de Order, así que solo Order puede crear sus partes. (Aquí compilaría solo
        // porque todas estas clases están anidadas en la misma clase de la guía.)

        return order.lineCount() == 2
                && order.lineSummaries().equals(List.of("2 x Teclado", "2 x Mouse"))
                && order.subtotal().compareTo(new BigDecimal("2200")) == 0;   // 1600 + 600
    }

    /*
        =================================================================
        CAPA 6) DELEGACIÓN Y COMPOSICIÓN SOBRE HERENCIA
        =================================================================

        DELEGAR es que un objeto le pase parte de su trabajo a otro que
        tiene guardado. Order no sabe calcular descuentos: se lo pide a
        su DiscountPolicy.

        Esto permite cambiar comportamiento SIN herencia. Compara:

        CON HERENCIA, cada combinación es una clase:
            Order
            ├── VipOrder
            ├── BlackFridayOrder
            └── VipBlackFridayOrder     // ¿y si llega "cupón + VIP"?
        Con 3 tipos de descuento y 2 tipos de envío ya son 6 subclases;
        con 4 y 3, doce. Esto se llama "explosión de subclases". Y un
        pedido no puede cambiar de clase después de creado.

        CON COMPOSICIÓN, el comportamiento es una pieza intercambiable:
            Order + DiscountPolicy
        Un solo Order, cualquier política, y se puede cambiar en tiempo
        de ejecución (el cliente aplica un cupón después de armar su
        carrito).

        Las otras ventajas de composición sobre herencia (no depender del
        interior de otra clase, no heredar métodos que no quieres) están
        en Inheritance, CAPAS 10 y 11.
    */
    interface DiscountPolicy {
        BigDecimal apply(BigDecimal subtotal);
    }

    static final class NoDiscount implements DiscountPolicy {
        @Override
        public BigDecimal apply(BigDecimal subtotal) {
            return subtotal;
        }
    }

    static final class PercentageDiscount implements DiscountPolicy {
        private final int percent;

        PercentageDiscount(int percent) {
            if (percent < 1 || percent > 100) {
                throw new IllegalArgumentException("El porcentaje debe estar entre 1 y 100");
            }
            this.percent = percent;
        }

        @Override
        public BigDecimal apply(BigDecimal subtotal) {
            return subtotal.subtract(subtotal.multiply(BigDecimal.valueOf(percent)).movePointLeft(2));
        }
    }

    static boolean delegationExample() {
        Order order = new Order(sampleCustomer());
        order.addLine(new Product("MON-01", "Monitor", new BigDecimal("4000")), 1);
        boolean fullPrice = order.total().compareTo(new BigDecimal("4000")) == 0;

        // El cliente aplica un cupón: el MISMO pedido cambia de comportamiento.
        order.changeDiscountPolicy(new PercentageDiscount(15));
        boolean discounted = order.total().compareTo(new BigDecimal("3400")) == 0;

        return fullPrice && discounted;
    }

    /*
        Esta forma de diseño (un comportamiento intercambiable guardado
        en un campo, detrás de una interface) es el patrón STRATEGY
        (v11_patterns/part3_behavioral/Strategy). Envolver un objeto para agregarle algo es el
        patrón DECORATOR, y también es composición (v11_patterns/part2_structural/Decorator).
    */

    /*
        =================================================================
        CAPA 7) MULTIPLICIDAD Y RELACIONES BIDIRECCIONALES
        =================================================================

        MULTIPLICIDAD: cuántos objetos hay de cada lado.
            1 a 1   un Customer tiene una dirección de envío   -> un campo
            1 a N   un Order tiene muchas líneas               -> una lista
            N a M   un Product está en muchas Category, y una
                    Category tiene muchos Product              -> listas en
                                                                  uno o ambos lados

        DIRECCIÓN: quién conoce a quién.
        - UNIDIRECCIONAL: Order conoce a Customer, pero Customer no
          guarda sus pedidos. Simple: un solo lado que mantener.
        - BIDIRECCIONAL: cada lado tiene una referencia al otro. A veces
          es necesario, pero trae dos problemas:

        PROBLEMA 1: mantener los dos lados sincronizados.
        Si agregas un pedido a la lista del cliente pero olvidas poner el
        cliente en el pedido, el modelo queda contradictorio. Regla: UN
        solo método, en UN solo lado, actualiza los dos.

        PROBLEMA 2: recursión infinita.
        Si toString (o equals, o hashCode) de cada lado incluye al otro,
        se llaman entre sí para siempre hasta que se acaba la pila de
        llamadas:
    */
    static final class NaiveCustomer {
        private final String name;
        private final List<NaiveOrder> orders = new ArrayList<>();

        NaiveCustomer(String name) {
            this.name = name;
        }

        // Un solo método mantiene los DOS lados: crea el pedido Y lo registra.
        NaiveOrder placeOrder(String folio) {
            NaiveOrder order = new NaiveOrder(folio, this);
            orders.add(order);
            return order;
        }

        @Override
        public String toString() {
            return "Customer[" + name + ", orders=" + orders + "]";   // incluye los pedidos...
        }
    }

    static final class NaiveOrder {
        private final String folio;
        private final NaiveCustomer customer;

        private NaiveOrder(String folio, NaiveCustomer customer) {
            this.folio = folio;
            this.customer = customer;
        }

        NaiveCustomer getCustomer() {
            return customer;
        }

        @Override
        public String toString() {
            return "Order[" + folio + ", customer=" + customer + "]";  // ...que incluyen al cliente...
        }
    }

    static boolean bidirectionalExample() {
        NaiveCustomer luis = new NaiveCustomer("Luis");
        NaiveOrder order = luis.placeOrder("P-001");
        boolean bothSidesInSync = order.getCustomer() == luis && luis.orders.contains(order);

        boolean infiniteRecursion;
        try {
            luis.toString();   // Customer -> Order -> Customer -> Order -> ...
            infiniteRecursion = false;
        } catch (StackOverflowError e) {
            infiniteRecursion = true;
        }

        return bothSidesInSync && infiniteRecursion;
    }

    /*
        Cómo se evita: en toString/equals/hashCode, uno de los lados
        muestra solo un dato simple del otro (el nombre o el folio), no
        el objeto completo. Y la mejor prevención: prefiere relaciones
        UNIDIRECCIONALES; agrega la otra dirección solo cuando un caso de
        uso real la necesite. (Este problema es muy común con JPA en
        v15_spring/: entidades bidireccionales con toString generado.)
    */

    /*
        =================================================================
        CAPA 8) LEY DE DEMÉTER: NO HABLES CON EXTRAÑOS
        =================================================================

        Cuando los objetos se componen unos dentro de otros, aparece la
        tentación de "navegar" por dentro:

            order.getCustomer().getShippingAddress().getCity()

        A esto se le llama "choque de trenes" (train wreck). El código que
        lo escribe ahora depende de TRES estructuras: Order, Customer y
        Address. Si mañana un cliente puede tener varias direcciones, hay
        que cambiar cada lugar del sistema que navegó así.

        La Ley de Deméter dice que un método solo debería hablar con:
        - su propio objeto (this),
        - sus campos,
        - sus parámetros,
        - los objetos que él mismo crea.
        No con "los amigos de sus amigos".

        La solución: que cada objeto RESPONDA lo que se le pregunta,
        delegando hacia dentro. Order.shippingCity() le pregunta a
        Customer, y Customer.shippingCity() le pregunta a Address. Cada
        uno conoce solo a su vecino inmediato.
    */
    static String cityByNavigating(Order order) {
        return order.getCustomer().getShippingAddress().getCity();   // conoce 3 estructuras
    }

    static String cityByAsking(Order order) {
        return order.shippingCity();                                  // conoce solo Order
    }

    static boolean demeterExample() {
        Order order = new Order(sampleCustomer());
        return cityByNavigating(order).equals("Guadalajara") && cityByAsking(order).equals("Guadalajara");
    }

    /*
        Matiz importante: Deméter es sobre no depender de la ESTRUCTURA
        interna de otros objetos, no sobre contar puntos. Esto NO la viola:
            List.of(1, 2, 3).stream().map(...).toList()
        porque cada llamada devuelve un objeto nuevo pensado para seguir
        encadenando (interfaz fluida), no un pedazo del interior de otro.
    */

    /*
        =================================================================
        CAPA 9) CÓMO DECIDIR QUÉ RELACIÓN USAR
        =================================================================

        Hazte estas preguntas, en orden:

        1) ¿Solo lo necesito durante un método?
               Sí -> DEPENDENCIA (parámetro). No lo guardes.

        2) ¿Lo necesito guardar, pero existe por su cuenta y otros también
           lo usan?
               Sí -> ASOCIACIÓN (un objeto) o AGREGACIÓN (una colección).
                     Recíbelo de fuera; no lo crees tú.

        3) ¿Es una parte que no tiene sentido fuera de mí y que nadie más
           debe tocar?
               Sí -> COMPOSICIÓN. Créala tú, escóndela, y aplica tú las
                     reglas que involucran a varias partes.

        4) ¿Es un comportamiento que puede variar?
               Sí -> COMPOSICIÓN + DELEGACIÓN detrás de una interface
                     (CAPA 6), no una subclase.

        5) ¿De verdad la relación es "ES UN" permanente y cumple Liskov?
               Solo entonces -> HERENCIA (Inheritance, CAPA 11).

        En la duda entre agregación y composición, la pregunta decisiva:
        "¿Qué pasa con la parte si el todo desaparece?". Si la respuesta
        es "nada, sigue existiendo", es agregación.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Usar herencia para algo que es "TIENE UN" (Coche extends Motor).

        2) En una agregación, guardar la lista que te pasan sin copiarla.
           Quien la pasó puede alterar tu grupo sin pasar por tus reglas
           (CAPA 4; era el caso del School original).

        3) En una composición, devolver referencias a las partes
           (getLines() con la lista real). Ya no eres dueño de nada
           (CAPA 5).

        4) Dejar que otros creen las partes de una composición. Si
           cualquiera puede hacer new OrderLine, el pedido no controla
           sus reglas (CAPA 5).

        5) Poner reglas que involucran varias partes fuera del dueño
           (CAPA 5).

        6) Crear una subclase por cada variante de comportamiento en lugar
           de componer una estrategia (CAPA 6).

        7) Relaciones bidireccionales sin un método que mantenga ambos
           lados (CAPA 7).

        8) toString, equals o hashCode que recorren una relación
           bidireccional: StackOverflowError (CAPA 7).

        9) Choques de trenes: a.getB().getC().getD() (CAPA 8).

        10) Guardar como campo algo que solo se usa en un método.
            Convierte una dependencia en una asociación sin necesidad
            (CAPA 2).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Cuatro relaciones, de débil a fuerte: dependencia (usa),
          asociación (conoce), agregación (tiene, partes independientes y
          compartibles), composición (es dueño, partes privadas que viven
          y mueren con él).
        - Agregación: recibe las partes de fuera, copia la colección,
          comparte los objetos.
        - Composición: crea las partes, nunca las expone, aplica las
          reglas que las involucran.
        - Delegación: el objeto pasa trabajo a otro que guarda; permite
          cambiar comportamiento sin herencia (Strategy).
        - Relaciones bidireccionales: un solo método mantiene ambos lados;
          cuidado con toString/equals/hashCode. Prefiere unidireccionales.
        - Ley de Deméter: pregunta a tu vecino, no navegues por dentro.
        - Composición sobre herencia, salvo un "ES UN" verdadero.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static Customer sampleCustomer() {
        return new Customer("Ana", new Address("Av. Juárez 100", "Guadalajara"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " no puede ser null ni vacío");
        }
        return value;
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
