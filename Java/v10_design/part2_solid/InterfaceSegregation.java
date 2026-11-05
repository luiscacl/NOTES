package v10_design.part2_solid;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/*
    =====================================================================
    PRINCIPIO DE SEGREGACIÓN DE INTERFACES (LA I DE SOLID)
    =====================================================================

    Ruta de design/: guía 5 de 10  (part2_solid)
    Anterior:  part2_solid/LiskovSubstitution
    Siguiente: part2_solid/DependencyInversion

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: interfaces, implementar varias a la vez y
    métodos default (v2_oop/part3_interfaces_and_relationships/
    Interfaces).

    Índice:
        CAPA 1)  Qué dice el principio
        CAPA 2)  El primer costo: implementar lo que no puedes cumplir
        CAPA 3)  El segundo costo: depender de lo que no usas
        CAPA 4)  La solución: interfaces de rol
        CAPA 5)  Las interfaces las define quien las USA
        CAPA 6)  Separar lectura de escritura
        CAPA 7)  Pide lo mínimo en tus parámetros
        CAPA 8)  Interfaces pequeñas envejecen mejor
        CAPA 9)  El error contrario: atomizar
        CAPA 10) Cómo se relaciona con S y L
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class InterfaceSegregation {

    public static void main(String[] args) {
        check(fatInterfaceExample(), "CAPA 2");
        check(testDoubleExample(), "CAPA 3");
        check(roleInterfacesExample(), "CAPA 4");
        check(clientOwnedExample(), "CAPA 5");
        check(readWriteExample(), "CAPA 6");
        check(minimalParametersExample(), "CAPA 7");
        System.out.println("InterfaceSegregation: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ DICE EL PRINCIPIO
        =================================================================

            "Ningún cliente debe ser forzado a depender de métodos que no
             usa." (Robert C. Martin)

        Un "cliente" es el código que USA una interface: quien la recibe
        como parámetro o la guarda como campo. Y también, del otro lado,
        quien la IMPLEMENTA.

        En la práctica: prefiere varias interfaces PEQUEÑAS y enfocadas a
        una GRANDE que lo mezcla todo. Cada cliente depende solo de la
        parte que necesita.

        El principio nació de un caso real: en un sistema de impresoras de
        Xerox, una sola clase Job concentraba los métodos de imprimir,
        engrapar, enviar fax... Cualquier cambio para una función obligaba
        a recompilar y volver a desplegar TODO lo que usaba Job, aunque no
        usara esa función. Dividirla en interfaces por uso resolvió el
        problema.
    */

    /*
        =================================================================
        CAPA 2) EL PRIMER COSTO: IMPLEMENTAR LO QUE NO PUEDES CUMPLIR
        =================================================================

        Una interface "gorda" para dispositivos de oficina:

            interface OfficeDevice {
                String print(String document);
                String scan(String document);
                String fax(String document, String number);
            }

        La multifuncional cara implementa todo. Pero la impresora sencilla
        NO escanea ni manda fax, y está OBLIGADA a implementar esos métodos.
        Sus únicas opciones son malas:
        - lanzar UnsupportedOperationException: viola Liskov
          (part2_solid/LiskovSubstitution, CAPA 7);
        - no hacer nada o devolver algo falso: el error es silencioso.

        Y quien recibe "un OfficeDevice" no puede saber, por el tipo, si
        scan() va a funcionar.
    */
    interface OfficeDevice {
        String print(String document);

        String scan(String document);

        String fax(String document, String number);
    }

    static final class MultifunctionPrinter implements OfficeDevice {
        @Override
        public String print(String document) {
            return "impreso: " + document;
        }

        @Override
        public String scan(String document) {
            return "escaneado: " + document;
        }

        @Override
        public String fax(String document, String number) {
            return "fax a " + number + ": " + document;
        }
    }

    static final class BasicPrinter implements OfficeDevice {
        @Override
        public String print(String document) {
            return "impreso: " + document;
        }

        @Override
        public String scan(String document) {
            throw new UnsupportedOperationException("Esta impresora no escanea");   // obligado por la interface
        }

        @Override
        public String fax(String document, String number) {
            return "";                                      // peor aún: finge que lo hizo
        }
    }

    static boolean fatInterfaceExample() {
        OfficeDevice device = new BasicPrinter();
        boolean scanExplodes;
        try {
            device.scan("contrato.pdf");                    // el tipo decía que se podía
            scanExplodes = false;
        } catch (UnsupportedOperationException e) {
            scanExplodes = true;
        }
        boolean faxLiesSilently = device.fax("contrato.pdf", "555").isEmpty();
        return scanExplodes && faxLiesSilently && device.print("a").equals("impreso: a");
    }

    /*
        =================================================================
        CAPA 3) EL SEGUNDO COSTO: DEPENDER DE LO QUE NO USAS
        =================================================================

        El otro lado del problema son los clientes. Si una clase que solo
        necesita BUSCAR pedidos recibe una interface con 8 métodos (buscar,
        guardar, borrar, reportes, exportar...):

        1) ACOPLAMIENTO innecesario: cada cambio en cualquiera de los 8
           métodos (una firma nueva en exportar) afecta, recompila y
           obliga a revisar al cliente que solo buscaba.

        2) LECTURA: al ver el parámetro, no sabes qué usa la clase de
           verdad. ¿Borra pedidos? ¿Genera reportes? Hay que leer todo el
           código para saberlo.

        3) PRUEBAS: para probar ese cliente necesitas una versión falsa del
           repositorio (un "doble de prueba", testing/), y tienes que
           implementar LOS 8 métodos, aunque la prueba use uno. Las pruebas
           se llenan de métodos vacíos o que lanzan excepción.

        El ejemplo muestra el doble de prueba para una interface gorda,
        contra el de una interface de rol que pide solo lo que se usa.
    */
    record Order(String id, String customer, BigDecimal total) { }

    interface OrderRepository {                             // gorda: todo lo que tenga que ver con pedidos
        Optional<Order> findById(String id);

        List<Order> findByCustomer(String customer);

        void save(Order order);

        void delete(String id);

        BigDecimal monthlySales(int year, int month);

        String exportCsv();
    }

    // Un cliente que SOLO busca, pero depende de los 6 métodos.
    static String orderSummaryFat(OrderRepository repository, String id) {
        return repository.findById(id).map(order -> order.id() + " $" + order.total()).orElse("no existe");
    }

    // El doble de prueba: 6 métodos para probar una búsqueda.
    static final class FakeFatRepository implements OrderRepository {
        @Override
        public Optional<Order> findById(String id) {
            return id.equals("A-1") ? Optional.of(new Order("A-1", "ana", new BigDecimal("350"))) : Optional.empty();
        }

        @Override
        public List<Order> findByCustomer(String customer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Order order) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BigDecimal monthlySales(int year, int month) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String exportCsv() {
            throw new UnsupportedOperationException();
        }
    }

    // Interface de rol: lo que este cliente usa, y nada más.
    interface OrderFinder {
        Optional<Order> findById(String id);
    }

    static String orderSummary(OrderFinder finder, String id) {
        return finder.findById(id).map(order -> order.id() + " $" + order.total()).orElse("no existe");
    }

    static boolean testDoubleExample() {
        OrderFinder fake = id -> id.equals("A-1")             // el doble de prueba: UNA lambda
                ? Optional.of(new Order("A-1", "ana", new BigDecimal("350"))) : Optional.empty();

        return orderSummaryFat(new FakeFatRepository(), "A-1").equals(orderSummary(fake, "A-1"))
                && orderSummary(fake, "B-9").equals("no existe");
    }

    /*
        =================================================================
        CAPA 4) LA SOLUCIÓN: INTERFACES DE ROL
        =================================================================

        Se divide la interface gorda en INTERFACES DE ROL: cada una
        describe UNA capacidad que algún cliente necesita.

            interface Printer  { String print(String document); }
            interface Scanner  { String scan(String document); }
            interface FaxSender { String fax(String document, String number); }

        Cada dispositivo implementa SOLO lo que puede hacer, y una clase
        puede implementar varias a la vez:

            BasicPrinter implements Printer
            MultifunctionPrinter implements Printer, Scanner, FaxSender

        Ahora:
        - Nadie implementa métodos que no puede cumplir.
        - El código que escanea pide un Scanner, y el COMPILADOR impide
          pasarle una impresora básica. El error que antes explotaba al
          ejecutar (CAPA 2) ahora no compila.
        - Si un cliente necesita dos capacidades a la vez, puede pedirlas
          en dos parámetros, o definir una interface que extienda ambas
          (interface ScanAndPrint extends Scanner, Printer).
    */
    interface Printer {
        String print(String document);
    }

    interface DocumentScanner {
        String scan(String document);
    }

    interface FaxSender {
        String fax(String document, String number);
    }

    static final class SimplePrinter implements Printer {
        @Override
        public String print(String document) {
            return "impreso: " + document;
        }
    }

    static final class OfficeMultifunction implements Printer, DocumentScanner, FaxSender {
        @Override
        public String print(String document) {
            return "impreso: " + document;
        }

        @Override
        public String scan(String document) {
            return "escaneado: " + document;
        }

        @Override
        public String fax(String document, String number) {
            return "fax a " + number + ": " + document;
        }
    }

    // Cada cliente pide la capacidad exacta que usa.
    static String digitize(DocumentScanner scanner, String document) {
        return scanner.scan(document);
    }

    static List<String> printAll(Printer printer, List<String> documents) {
        return documents.stream().map(printer::print).toList();
    }

    static boolean roleInterfacesExample() {
        OfficeMultifunction office = new OfficeMultifunction();
        return printAll(new SimplePrinter(), List.of("a", "b")).equals(List.of("impreso: a", "impreso: b"))
                && printAll(office, List.of("c")).equals(List.of("impreso: c"))      // la multifuncional ES un Printer
                && digitize(office, "contrato").equals("escaneado: contrato");
    }

    /*
        =================================================================
        CAPA 5) LAS INTERFACES LAS DEFINE QUIEN LAS USA
        =================================================================

        Un cambio de punto de vista importante: una interface no describe
        "todo lo que sabe hacer la clase que la implementa". Describe "lo
        que NECESITA el cliente que la usa".

        Por eso, al diseñar, se empieza por los CLIENTES:
        - El servicio de reportes necesita: ventas del mes.
        - La pantalla de detalle necesita: buscar por id.
        - El proceso de compra necesita: guardar.

        Con eso salen tres interfaces de rol. Y UNA sola clase (la que de
        verdad habla con la base de datos) puede implementar las tres. No
        se trata de tener tres implementaciones: se trata de que cada
        cliente VEA solo su parte.

        Ventajas:
        - Cada cliente se lee y se prueba con lo mínimo (CAPA 3).
        - Si el día de mañana los reportes salen de otro sistema (un
          almacén de datos aparte), solo cambia la implementación de
          SalesReporting; nadie más se entera.
    */
    interface OrderSaver {
        void save(Order order);
    }

    interface SalesReporting {
        BigDecimal salesOf(String customer);
    }

    // UNA implementación real que cumple los tres roles.
    static final class InMemoryOrders implements OrderFinder, OrderSaver, SalesReporting {
        private final Map<String, Order> orders = new HashMap<>();

        @Override
        public Optional<Order> findById(String id) {
            return Optional.ofNullable(orders.get(id));
        }

        @Override
        public void save(Order order) {
            orders.put(order.id(), order);
        }

        @Override
        public BigDecimal salesOf(String customer) {
            return orders.values().stream()
                    .filter(order -> order.customer().equals(customer))
                    .map(Order::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    // Cada cliente ve SOLO su rol.
    static final class Checkout {
        private final OrderSaver saver;

        Checkout(OrderSaver saver) {
            this.saver = saver;
        }

        void place(Order order) {
            saver.save(order);
        }
    }

    static final class CustomerReport {
        private final SalesReporting reporting;

        CustomerReport(SalesReporting reporting) {
            this.reporting = reporting;
        }

        String lineFor(String customer) {
            return customer + ": $" + reporting.salesOf(customer);
        }
    }

    static boolean clientOwnedExample() {
        InMemoryOrders storage = new InMemoryOrders();      // un objeto...
        Checkout checkout = new Checkout(storage);          // ...visto como OrderSaver
        CustomerReport report = new CustomerReport(storage);   // ...visto como SalesReporting

        checkout.place(new Order("A-1", "ana", new BigDecimal("300")));
        checkout.place(new Order("A-2", "ana", new BigDecimal("50")));

        return report.lineFor("ana").equals("ana: $350") && orderSummary(storage, "A-2").equals("A-2 $50");
    }

    /*
        =================================================================
        CAPA 6) SEPARAR LECTURA DE ESCRITURA
        =================================================================

        La división más útil y más común: separar lo que LEE de lo que
        MODIFICA. La mayoría de los clientes solo lee; muy pocos deben
        poder cambiar.

        Es justo lo que le falta a List en el JDK (part2_solid/
        LiskovSubstitution, CAPA 10): como solo hay una interface con
        lectura y escritura, nadie puede pedir por el TIPO "una lista que
        solo voy a leer", y las listas inmutables tienen que lanzar
        excepciones en add.

        En tus propias clases sí puedes hacerlo bien:
            interface InventoryView { int stockOf(String sku); }     // leer
            interface Inventory extends InventoryView {              // leer + escribir
                void receive(String sku, int units);
            }

        Al catálogo de la tienda (que solo muestra existencias) le das un
        InventoryView: el compilador le impide modificar. Al proceso de
        recepción de mercancía, un Inventory. Es la misma idea que las
        vistas de solo lectura de ImmutableCollections, pero garantizada
        por el tipo en lugar de por una excepción al ejecutar.

        (A gran escala, separar los modelos de lectura y escritura se llama
        CQRS; v14_architecture/.)
    */
    interface InventoryView {
        int stockOf(String sku);
    }

    interface Inventory extends InventoryView {
        void receive(String sku, int units);
    }

    static final class Warehouse implements Inventory {
        private final Map<String, Integer> stock = new HashMap<>();

        @Override
        public int stockOf(String sku) {
            return stock.getOrDefault(sku, 0);
        }

        @Override
        public void receive(String sku, int units) {
            stock.merge(sku, units, Integer::sum);
        }
    }

    static String catalogBadge(InventoryView view, String sku) {    // solo PUEDE leer
        return view.stockOf(sku) > 0 ? "Disponible" : "Agotado";
        // ERROR si se intenta view.receive(sku, 5): InventoryView no tiene ese método
    }

    static boolean readWriteExample() {
        Warehouse warehouse = new Warehouse();
        String before = catalogBadge(warehouse, "KBD-1");
        warehouse.receive("KBD-1", 10);                     // solo quien tiene un Inventory escribe
        return before.equals("Agotado") && catalogBadge(warehouse, "KBD-1").equals("Disponible");
    }

    /*
        =================================================================
        CAPA 7) PIDE LO MÍNIMO EN TUS PARÁMETROS
        =================================================================

        El principio también aplica a los tipos que ya existen: un método
        debe pedir el tipo MÁS GENERAL que le alcance, no el más concreto
        que tengas a la mano.

            int total(ArrayList<Integer> values)   // solo acepta ArrayList
            int total(List<Integer> values)        // cualquier lista
            int total(Collection<Integer> values)  // listas, sets, colas...
            int total(Iterable<Integer> values)    // cualquier cosa recorrible

        Si solo vas a recorrer, Iterable o Collection bastan. Si necesitas
        acceso por posición, List. Pedir de más acopla tu método a una
        implementación sin ninguna razón (v4_generics/AdvancedGenerics: con
        el comodín ? extends se gana todavía más flexibilidad).

        Y lo mismo con objetos grandes: si un método solo necesita "la hora
        actual", que reciba un Clock o un Supplier<Instant>, no el objeto
        de configuración completo de la aplicación.

        El JDK está lleno de interfaces diminutas por esta razón, y por eso
        encajan con todo: Runnable, Comparable, AutoCloseable, Iterable,
        Supplier... (v7_functional/part1_lambdas/FunctionalInterfaces).
    */
    static int totalUnits(Collection<Integer> units) {      // pide solo "algo con elementos"
        int sum = 0;
        for (int value : units) {
            sum += value;
        }
        return sum;
    }

    static String stamp(Supplier<String> clock) {           // pide solo "algo que dé la hora"
        return "generado " + clock.get();
    }

    static boolean minimalParametersExample() {
        return totalUnits(List.of(1, 2, 3)) == 6
                && totalUnits(Set.of(10, 20)) == 30
                && totalUnits(new ArrayDeque<>(List.of(5))) == 5   // lista, set o cola: todos sirven
                && stamp(() -> "10:00").equals("generado 10:00");
    }

    /*
        =================================================================
        CAPA 8) INTERFACES PEQUEÑAS ENVEJECEN MEJOR
        =================================================================

        Agregar un método ABSTRACTO a una interface rompe a TODAS las
        clases que la implementan: dejan de compilar hasta que lo
        implementen. Cuanto más grande la interface y más implementaciones
        tiene, más caro es cada cambio.

        Con interfaces de rol pequeñas:
        - Los cambios afectan solo a las implementaciones de ESE rol.
        - Una capacidad nueva suele ser una interface NUEVA, sin tocar las
          que existen (part2_solid/OpenClosed).

        Y si de verdad hay que ampliar una interface que ya tiene muchas
        implementaciones, los métodos default (v2_oop/
        part3_interfaces_and_relationships/Interfaces, CAPA 5) permiten
        agregar comportamiento sin romperlas. Así evolucionó el propio JDK
        (List.sort e Iterable.forEach llegaron en Java 8 como métodos default).
    */

    /*
        =================================================================
        CAPA 9) EL ERROR CONTRARIO: ATOMIZAR
        =================================================================

        Llevado al extremo, el principio produce una interface por método
        en TODO el código: OrderIdGetter, OrderTotalGetter,
        OrderCustomerGetter... Eso fragmenta lo que va junto y obliga a
        recibir cinco parámetros para algo que es una sola cosa.

        Lo que importa no es el TAMAÑO de la interface, sino que sea
        COHESIVA para sus clientes:
        - Iterator tiene hasNext() y next(): siempre se usan juntos. No
          tiene sentido separarlos.
        - Una interface de repositorio con findById y findAll para el
          mismo cliente de lectura está bien junta.

        La regla: divide cuando hay CLIENTES DISTINTOS que usan partes
        distintas, o implementaciones que no pueden cumplir una parte. Si
        todos los clientes usan todo, déjala junta.
    */

    /*
        =================================================================
        CAPA 10) CÓMO SE RELACIONA CON S Y L
        =================================================================

        Los principios de SOLID se refuerzan entre sí:

        - Con la S (SingleResponsibility): una interface gorda casi siempre
          mezcla roles de ACTORES distintos (los reportes los pide finanzas,
          el guardado lo pide el proceso de compra). Dividirla por cliente
          es aplicar la S a las interfaces.

        - Con la L (LiskovSubstitution): las interfaces gordas FUERZAN
          violaciones de Liskov: implementaciones que no pueden cumplir
          una parte y lanzan excepciones (CAPA 2). Con interfaces de rol,
          cada implementación promete solo lo que cumple.

        - Con la D (part2_solid/DependencyInversion, la siguiente): las
          interfaces pequeñas definidas por el cliente son justo las
          abstracciones de las que el código de alto nivel debe depender.
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Interfaces "de todo" (Repository, Service, Manager) que crecen
           con cada necesidad de cualquier cliente (CAPAS 2 y 3).

        2) Implementaciones con métodos que lanzan
           UnsupportedOperationException o no hacen nada (CAPA 2).

        3) Diseñar la interface desde la clase que la implementa ("todo lo
           que sabe hacer") en vez de desde quien la usa (CAPA 5).

        4) Dar acceso de escritura a clientes que solo leen (CAPA 6).

        5) Parámetros más concretos de lo necesario: ArrayList en vez de
           List o Collection (CAPA 7).

        6) Agregar métodos abstractos a interfaces con muchas
           implementaciones (CAPA 8).

        7) Una interface por método sin que haya clientes distintos
           (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Ningún cliente debe depender de métodos que no usa.
        - Interfaces gordas: implementaciones que no pueden cumplir
          (violan Liskov) y clientes acoplados de más, difíciles de leer y
          de probar.
        - Interfaces de ROL, definidas desde el cliente; una clase puede
          implementar varias.
        - Separa lectura de escritura: el tipo garantiza quién puede
          modificar.
        - Pide en los parámetros el tipo más general que te alcance.
        - Interfaces pequeñas cambian con menos impacto.
        - Divide cuando hay clientes distintos; si todos usan todo, déjala
          junta.
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
