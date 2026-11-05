package v10_design.part3_responsibilities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
    =====================================================================
    GRASP: A QUIÉN LE TOCA CADA RESPONSABILIDAD
    =====================================================================

    Ruta de design/: guía 8 de 10  (part3_responsibilities)
    Anterior:  part3_responsibilities/DependencyInjection
    Siguiente: part4_clean_code/CleanCode

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es GRASP y qué problema resuelve
        CAPA 2)  Information Expert: lo hace quien tiene la información
        CAPA 3)  Creator: quién debe crear a quién
        CAPA 4)  Controller: quién recibe las peticiones del exterior
        CAPA 5)  Low Coupling y High Cohesion: los jueces
        CAPA 6)  Polymorphism: variar sin preguntar el tipo
        CAPA 7)  Pure Fabrication: inventar una clase que el negocio no tiene
        CAPA 8)  Indirection: un intermediario para desacoplar
        CAPA 9)  Protected Variations: el principio paraguas
        CAPA 10) Todos juntos: diseñar "registrar una venta"
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class GRASP {

    public static void main(String[] args) {
        check(informationExpertExample(), "CAPA 2");
        check(creatorExample(), "CAPA 3");
        check(controllerExample(), "CAPA 4");
        check(pureFabricationExample(), "CAPA 7");
        check(indirectionExample(), "CAPA 8");
        System.out.println("GRASP: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES GRASP Y QUÉ PROBLEMA RESUELVE
        =================================================================

        Ya sabes QUÉ hace un buen diseño: alta cohesión, bajo acoplamiento,
        SOLID. Pero frente a un problema concreto ("calcular el total de
        una venta", "guardar un pedido"), la pregunta del día a día es más
        simple y más difícil:

            ¿EN QUÉ CLASE pongo este código?

        GRASP ("General Responsibility Assignment Software Patterns",
        patrones generales para asignar responsabilidades) son 9 guías para
        responder esa pregunta. Las reunió Craig Larman en su libro
        "Applying UML and Patterns", muy usado para enseñar diseño
        orientado a objetos.

        Una RESPONSABILIDAD es una obligación de una clase, de dos tipos:
        - de HACER: calcular algo, crear un objeto, coordinar a otros;
        - de CONOCER: sus propios datos, objetos relacionados, cosas que
          puede calcular.

        Los 9 patrones:
            Information Expert   Creator         Controller
            Low Coupling         High Cohesion   Polymorphism
            Pure Fabrication     Indirection     Protected Variations

        Varios ya los conoces con otro nombre; esta guía lo señala y se
        concentra en los que son nuevos: Expert, Creator, Controller, Pure
        Fabrication e Indirection.
    */

    /*
        =================================================================
        CAPA 2) INFORMATION EXPERT: LO HACE QUIEN TIENE LA INFORMACIÓN
        =================================================================

        El patrón más usado, y el primero que hay que preguntarse:

            Asigna la responsabilidad a la clase que TIENE LA INFORMACIÓN
            necesaria para cumplirla.

        ¿Quién calcula el total de una venta? La venta, porque conoce sus
        líneas. ¿Quién calcula el subtotal de una línea? La línea, porque
        conoce la cantidad y el producto. ¿Quién conoce el precio? El
        producto. Cada uno hace la parte que puede hacer con SUS datos, y
        el trabajo se reparte solo:

            Sale.total()  ->  suma  SaleLine.subtotal()  ->  cantidad * Product.price()

        El error opuesto es una clase externa que pide los datos de todos
        con getters y hace el cálculo ella (un "calculador" que lo sabe
        todo). Funciona, pero:
        - acopla esa clase con la estructura interna de Sale, SaleLine y
          Product (si cambia cómo se guarda una línea, se rompe);
        - deja a Sale como una bolsa de datos sin comportamiento (un
          "modelo anémico");
        - la regla queda lejos de los datos que usa.

        Es Tell, Don't Ask (v2_oop/part2_pillars/Encapsulation, CAPA 5)
        visto desde el diseño: se le PIDE a cada objeto que haga lo suyo.
    */
    record Product(String sku, String name, BigDecimal price) { }

    static final class SaleLine {
        private final Product product;
        private final int quantity;

        SaleLine(Product product, int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Cantidad inválida: " + quantity);
            }
            this.product = product;
            this.quantity = quantity;
        }

        BigDecimal subtotal() {                             // EXPERT: conoce cantidad y producto
            return product.price().multiply(BigDecimal.valueOf(quantity));
        }

        Product product() {
            return product;
        }

        int quantity() {
            return quantity;
        }
    }

    static final class Sale {
        private final List<SaleLine> lines = new ArrayList<>();

        void addLine(Product product, int quantity) {       // CAPA 3: la venta crea sus líneas
            lines.add(new SaleLine(product, quantity));
        }

        BigDecimal total() {                                // EXPERT: conoce sus líneas
            return lines.stream().map(SaleLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        List<SaleLine> lines() {
            return List.copyOf(lines);
        }
    }

    // Lo contrario: un "calculador" que lo pregunta todo y lo calcula afuera.
    static BigDecimal externalTotal(Sale sale) {
        BigDecimal total = BigDecimal.ZERO;
        for (SaleLine line : sale.lines()) {                // conoce la estructura interna de Sale...
            total = total.add(line.product().price()        // ...de SaleLine...
                    .multiply(BigDecimal.valueOf(line.quantity())));   // ...y de Product
        }
        return total;
    }

    static final Product KEYBOARD = new Product("KBD-1", "teclado", new BigDecimal("500"));
    static final Product MOUSE = new Product("MOU-1", "mouse", new BigDecimal("250"));

    static boolean informationExpertExample() {
        Sale sale = new Sale();
        sale.addLine(KEYBOARD, 2);
        sale.addLine(MOUSE, 1);
        return sale.total().compareTo(new BigDecimal("1250")) == 0
                && externalTotal(sale).compareTo(sale.total()) == 0;   // mismo resultado, más acoplado
    }

    /*
        =================================================================
        CAPA 3) CREATOR: QUIÉN DEBE CREAR A QUIÉN
        =================================================================

        Crear objetos es una responsabilidad como cualquier otra, y crea
        acoplamiento (quien hace new depende de la clase concreta). ¿Quién
        debe hacer new SaleLine(...)? Creator dice: la clase B crea objetos
        de A si (mientras más se cumplan, mejor):
        - B CONTIENE o agrega objetos de A (la venta contiene sus líneas);
        - B REGISTRA objetos de A;
        - B USA de cerca objetos de A;
        - B tiene los DATOS para inicializar A.

        Por eso Sale.addLine(product, quantity) crea la SaleLine, en lugar
        de que el código de afuera haga new SaleLine y la pase. Ventajas:
        - quien usa la venta no necesita conocer SaleLine;
        - la venta controla sus propias reglas al crear: no permitir dos
          líneas del mismo producto (las combina), un máximo de líneas,
          etc. Nadie puede meter una línea que la venta no aprobó.

        (Si crear un objeto es complicado, o hay que decidir entre varias
        clases, se usa una fábrica: v11_patterns/. Y los objetos con
        dependencias de servicios no se crean así, se inyectan:
        DependencyInjection.)
    */
    static final class Cart {
        private final Map<String, SaleLine> linesBySku = new HashMap<>();

        void add(Product product, int quantity) {            // CREATOR: el carrito crea y combina sus líneas
            if (quantity <= 0) {                            // valida lo que RECIBE, no solo el resultado combinado
                throw new IllegalArgumentException("Cantidad inválida: " + quantity);
            }
            SaleLine existing = linesBySku.get(product.sku());
            int newQuantity = existing == null ? quantity : existing.quantity() + quantity;
            linesBySku.put(product.sku(), new SaleLine(product, newQuantity));
        }

        int lineCount() {
            return linesBySku.size();
        }

        int quantityOf(String sku) {
            SaleLine line = linesBySku.get(sku);
            return line == null ? 0 : line.quantity();
        }
    }

    static boolean creatorExample() {
        Cart cart = new Cart();
        cart.add(KEYBOARD, 1);
        cart.add(KEYBOARD, 2);                              // el creador aplica su regla: combina
        cart.add(MOUSE, 1);

        boolean validatesOnCreation;
        try {
            cart.add(MOUSE, 0);
            validatesOnCreation = false;
        } catch (IllegalArgumentException e) {
            validatesOnCreation = true;
        }
        return cart.lineCount() == 2 && cart.quantityOf("KBD-1") == 3 && validatesOnCreation;
    }

    /*
        =================================================================
        CAPA 4) CONTROLLER: QUIÉN RECIBE LAS PETICIONES DEL EXTERIOR
        =================================================================

        Las peticiones llegan de AFUERA: un botón en la pantalla, una
        petición HTTP, un mensaje de otra aplicación, un comando de la
        terminal. ¿Qué objeto las recibe primero?

        Controller dice: el primer objeto DESPUÉS de la interfaz (que no
        sea la pantalla ni el código HTTP) que recibe y coordina una
        operación del sistema. Normalmente uno por CASO DE USO
        ("registrar venta", "cancelar pedido").

        Su trabajo es delgado:
        1) recibir la petición en el formato de afuera (textos, ids);
        2) convertirla en objetos del dominio (buscar el producto por su
           código);
        3) DELEGAR el trabajo a los objetos del dominio (Information
           Expert, Creator...);
        4) devolver un resultado simple.

        Por qué no poner esto en la pantalla o en el código HTTP:
        - la misma operación se puede disparar desde la web, desde una app
          móvil o desde una prueba, sin repetir lógica;
        - la lógica se prueba sin servidor web ni pantalla.

        El antipatrón: el CONTROLADOR INFLADO, que hace él mismo todo el
        trabajo (calcula, valida, guarda) en lugar de delegar. Es el
        "servicio que hace todo" de part2_solid/SingleResponsibility,
        CAPA 6.

        (En Spring, las clases @RestController reciben HTTP y llaman a este
        tipo de objeto; v15_spring/.)
    */
    record SaleReceipt(String saleId, BigDecimal total) { }

    static final class RegisterSaleController {            // CONTROLLER del caso de uso "registrar venta"
        private final ProductCatalog catalog;
        private final SaleRepository sales;

        RegisterSaleController(ProductCatalog catalog, SaleRepository sales) {
            this.catalog = catalog;
            this.sales = sales;
        }

        /** Recibe lo que llega de afuera: códigos de producto y cantidades como texto. */
        SaleReceipt register(Map<String, String> quantitiesBySku) {
            Sale sale = new Sale();
            quantitiesBySku.forEach((sku, quantity) -> {
                Product product = catalog.find(sku)
                        .orElseThrow(() -> new IllegalArgumentException("Producto desconocido: " + sku));
                sale.addLine(product, Integer.parseInt(quantity));    // delega en el dominio
            });
            String id = sales.save(sale);                   // delega el guardado (CAPA 7)
            return new SaleReceipt(id, sale.total());       // y no calcula nada él mismo
        }
    }

    interface ProductCatalog {
        Optional<Product> find(String sku);
    }

    static boolean controllerExample() {
        ProductCatalog catalog = sku -> Optional.ofNullable(Map.of("KBD-1", KEYBOARD, "MOU-1", MOUSE).get(sku));
        RegisterSaleController controller = new RegisterSaleController(catalog, new InMemorySaleRepository());

        SaleReceipt receipt = controller.register(Map.of("KBD-1", "2"));   // como llegaría de un formulario

        boolean unknownRejected;
        try {
            controller.register(Map.of("XXX-9", "1"));
            unknownRejected = false;
        } catch (IllegalArgumentException e) {
            unknownRejected = e.getMessage().equals("Producto desconocido: XXX-9");
        }
        return receipt.total().compareTo(new BigDecimal("1000")) == 0 && receipt.saleId().equals("V-1") && unknownRejected;
    }

    /*
        =================================================================
        CAPA 5) LOW COUPLING Y HIGH COHESION: LOS JUECES
        =================================================================

        Estos dos ya los conoces a fondo (part1_basics/BasicPrinciples,
        CAPAS 2 y 3). En GRASP tienen un papel especial: son los criterios
        para EVALUAR las alternativas que proponen los otros patrones.

        Por ejemplo, Information Expert diría que una venta se guarde a sí
        misma en la base de datos (tiene los datos). Pero eso acoplaría
        Sale con la base de datos y mezclaría negocio con persistencia: los
        jueces dicen que no, y entra Pure Fabrication (CAPA 7).

        Ante dos diseños posibles, gana el que deja menos acoplamiento y
        más cohesión.
    */

    /*
        =================================================================
        CAPA 6) POLYMORPHISM: VARIAR SIN PREGUNTAR EL TIPO
        =================================================================

        Cuando el comportamiento varía según el tipo, asigna la
        responsabilidad a los tipos, con polimorfismo, en vez de decidir
        con if o switch sobre el tipo.

        Es exactamente part2_solid/OpenClosed, CAPA 4 (un método de pago
        por clase) y v2_oop/part2_pillars/Polymorphism, CAPAS 2 y 3. No hay
        nada nuevo aquí: GRASP le da nombre como patrón de asignación.
    */

    /*
        =================================================================
        CAPA 7) PURE FABRICATION: INVENTAR UNA CLASE QUE EL NEGOCIO NO TIENE
        =================================================================

        Hasta ahora las clases salían del NEGOCIO: venta, producto, línea.
        Pero a veces asignar una responsabilidad a una clase del negocio
        empeora el diseño. ¿Quién guarda una venta en la base de datos?
        - Information Expert: la venta (tiene los datos) -> sale.save().
        - Pero así Sale conoce SQL o la base de datos: baja su cohesión
          (negocio + persistencia) y sube su acoplamiento (depende de la
          tecnología; part2_solid/DependencyInversion).

        PURE FABRICATION dice: INVENTA una clase que no representa nada del
        negocio, solo para tener una responsabilidad bien puesta. Nadie en
        la tienda habla de un "repositorio de ventas", pero SaleRepository
        es la mejor forma de mantener a Sale limpia.

        Son fabricaciones muy comunes:
            SaleRepository, PasswordHasher, InvoiceNumberGenerator,
            EmailSender, ReportExporter, PriceFormatter...

        Señal de que hace falta una: el Expert natural quedaría mezclando
        su negocio con algo técnico o con algo que no le corresponde.
    */
    interface SaleRepository {                              // PURE FABRICATION: no existe en el negocio
        String save(Sale sale);

        Optional<BigDecimal> totalOf(String id);
    }

    static final class InMemorySaleRepository implements SaleRepository {
        private final Map<String, BigDecimal> totals = new HashMap<>();

        @Override
        public String save(Sale sale) {
            String id = "V-" + (totals.size() + 1);
            totals.put(id, sale.total());
            return id;
        }

        @Override
        public Optional<BigDecimal> totalOf(String id) {
            return Optional.ofNullable(totals.get(id));
        }
    }

    static boolean pureFabricationExample() {
        Sale sale = new Sale();                             // Sale no sabe nada de guardarse
        sale.addLine(MOUSE, 4);
        SaleRepository repository = new InMemorySaleRepository();
        String id = repository.save(sale);
        return repository.totalOf(id).orElseThrow().compareTo(new BigDecimal("1000")) == 0;
    }

    /*
        =================================================================
        CAPA 8) INDIRECTION: UN INTERMEDIARIO PARA DESACOPLAR
        =================================================================

        Cuando dos piezas no deberían conocerse directamente (porque una
        cambia mucho, o es de un tercero, o tiene una forma incómoda), pon
        un INTERMEDIARIO entre ellas.

        El caso típico: un servicio EXTERNO con su propia forma de hacer
        las cosas. Por ejemplo, la librería de un proveedor de impuestos
        que trabaja con montos en CENTAVOS (long), códigos de país propios
        y métodos con nombres raros. Si tu código de negocio la usa
        directamente, se llena de conversiones, y cambiar de proveedor
        obliga a tocarlo todo.

        Con un intermediario (un ADAPTADOR; v11_patterns/, Adapter):
        - tu negocio habla con TaxCalculator, una interface tuya, en tus
          términos (BigDecimal, tus códigos);
        - el adaptador traduce a la forma del proveedor y de regreso;
        - cambiar de proveedor es escribir otro adaptador.

        Interfaces, adaptadores, fachadas y el Controller son formas de
        indirección. Hay una frase famosa: "todo problema en computación se
        resuelve con otro nivel de indirección"... y su complemento: "...
        excepto el de tener demasiados niveles de indirección". Úsala
        donde protege de algo real (CAPA 9).
    */
    static final class ExternalTaxApi {                     // de un tercero: no la puedes cambiar
        long computeTaxInCents(long amountInCents, String isoCountry3) {   // centavos y códigos de 3 letras
            return switch (isoCountry3) {
                case "MEX" -> amountInCents * 16 / 100;
                case "COL" -> amountInCents * 19 / 100;
                default -> throw new IllegalArgumentException("País no soportado: " + isoCountry3);
            };
        }
    }

    interface TaxCalculator {                               // en TUS términos
        BigDecimal taxFor(BigDecimal amount, String country);
    }

    static final class ExternalTaxAdapter implements TaxCalculator {   // INDIRECTION: traduce
        private static final Map<String, String> COUNTRY_CODES = Map.of("MX", "MEX", "CO", "COL");
        private final ExternalTaxApi api;

        ExternalTaxAdapter(ExternalTaxApi api) {
            this.api = api;
        }

        @Override
        public BigDecimal taxFor(BigDecimal amount, String country) {
            long cents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            long taxCents = api.computeTaxInCents(cents, COUNTRY_CODES.get(country));
            return BigDecimal.valueOf(taxCents, 2);         // de centavos a pesos con 2 decimales
        }
    }

    static boolean indirectionExample() {
        TaxCalculator taxes = new ExternalTaxAdapter(new ExternalTaxApi());   // el negocio solo ve TaxCalculator
        return taxes.taxFor(new BigDecimal("1250.00"), "MX").compareTo(new BigDecimal("200.00")) == 0
                && taxes.taxFor(new BigDecimal("100"), "CO").compareTo(new BigDecimal("19.00")) == 0;
    }

    /*
        =================================================================
        CAPA 9) PROTECTED VARIATIONS: EL PRINCIPIO PARAGUAS
        =================================================================

        Identifica los puntos donde se espera VARIACIÓN o INESTABILIDAD
        (proveedores externos, reglas que cambian, tecnologías, formatos)
        y rodéalos con una INTERFACE ESTABLE, para que esa variación no
        afecte al resto.

        Es el principio detrás de casi todo lo anterior:
        - encapsulación: protege del cambio en la representación interna;
        - Open/Closed y Polymorphism: protegen de las variantes nuevas;
        - Dependency Inversion e Indirection: protegen de los detalles y
          los terceros;
        - incluso poner datos en configuración (part2_solid/OpenClosed,
          CAPA 10) protege del cambio de valores.

        Y su límite es el mismo que el del cierre estratégico
        (part2_solid/OpenClosed, CAPA 8): protege donde la variación es
        PROBABLE. Rodear de interfaces lo que nunca cambia es costo sin
        beneficio (BasicPrinciples, CAPA 8).
    */

    /*
        =================================================================
        CAPA 10) TODOS JUNTOS: DISEÑAR "REGISTRAR UNA VENTA"
        =================================================================

        Así se usan en la práctica: para cada responsabilidad que aparece
        en el caso de uso, se pregunta qué patrón aplica.

        | Responsabilidad                                  | Clase                    | Patrón que decide        |
        |--------------------------------------------------|--------------------------|--------------------------|
        | Recibir "registrar venta" desde afuera           | RegisterSaleController   | Controller               |
        | Encontrar el producto por su código              | ProductCatalog           | Pure Fabrication +       |
        |                                                  |                          | Protected Variations     |
        | Crear las líneas de la venta                     | Sale                     | Creator                  |
        | Calcular el subtotal de una línea                | SaleLine                 | Information Expert       |
        | Calcular el total de la venta                    | Sale                     | Information Expert       |
        | Guardar la venta                                 | SaleRepository           | Pure Fabrication         |
        | Calcular impuestos con el proveedor externo      | ExternalTaxAdapter       | Indirection +            |
        |                                                  |                          | Protected Variations     |
        | Cobrar con distintos métodos de pago             | PaymentMethod            | Polymorphism             |
        |                                                  | (OpenClosed, CAPA 4)     |                          |
        | Revisar el diseño completo                       | (todas)                  | Low Coupling,            |
        |                                                  |                          | High Cohesion            |

        Ninguna clase hace todo, cada una hace lo que le corresponde, y la
        razón de cada decisión tiene nombre. Eso es lo más valioso de
        GRASP: te da un vocabulario para explicar y discutir un diseño.
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Clases "calculadoras" que piden los datos de todos con getters,
           dejando a los objetos del negocio sin comportamiento (CAPA 2).

        2) Crear las partes de un objeto desde afuera (new SaleLine en el
           controlador), saltándose sus reglas (CAPA 3).

        3) Lógica de negocio en la pantalla o en el código HTTP; o un
           controlador inflado que hace todo (CAPA 4).

        4) Aplicar Information Expert a ciegas: entidades que se guardan
           solas en la base de datos (CAPAS 5 y 7).

        5) Usar directamente las clases de un proveedor externo en todo el
           negocio (CAPA 8).

        6) Indirección en todas partes, "por si acaso" (CAPAS 8 y 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - GRASP: 9 guías para decidir en qué clase va cada responsabilidad
          (de hacer o de conocer).
        - Information Expert: lo hace quien tiene la información.
        - Creator: crea quien contiene, registra, usa de cerca o tiene los
          datos para inicializar.
        - Controller: un objeto por caso de uso recibe lo de afuera,
          convierte y delega; delgado.
        - Low Coupling y High Cohesion: los jueces de toda decisión.
        - Polymorphism: variantes por tipo, sin if.
        - Pure Fabrication: clases inventadas (repositorios, generadores)
          para no ensuciar al negocio.
        - Indirection: intermediarios (adaptadores) para desacoplar.
        - Protected Variations: interfaces estables alrededor de lo que
          probablemente cambie.
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
