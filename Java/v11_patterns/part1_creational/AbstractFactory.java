package v11_patterns.part1_creational;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/*
    =====================================================================
    ABSTRACT FACTORY: CREAR FAMILIAS DE OBJETOS QUE COMBINAN ENTRE SÍ
    =====================================================================

    Ruta de patterns/: guía 2 de 28  (part1_creational)
    Anterior:  part1_creational/FactoryMethod
    Siguiente: part1_creational/Builder

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: MEDIA. No lo escribirás seguido, pero aparece en
    librerías y frameworks (CAPA 8), y es la solución correcta cuando el
    problema de la CAPA 2 aparece.

    Índice:
        CAPA 1)  Qué es una "familia" de objetos
        CAPA 2)  El problema: combinaciones que no deberían existir
        CAPA 3)  La solución: una fábrica por familia
        CAPA 4)  La estructura, con sus nombres
        CAPA 5)  Elegir la familia una sola vez
        CAPA 6)  Fácil agregar familias, difícil agregar productos
        CAPA 7)  La versión moderna: un record con la familia ya armada
        CAPA 8)  Abstract Factory en el JDK
        CAPA 9)  Factory Method, Abstract Factory y Builder
        CAPA 10) Cuándo usarlo y cuándo no
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class AbstractFactory {

    public static void main(String[] args) {
        check(mixedFamilyBugExample(), "CAPA 2");
        check(abstractFactoryExample(), "CAPA 3");
        check(selectionExample(), "CAPA 5");
        check(recordFamilyExample(), "CAPA 7");
        check(jdkExample(), "CAPA 8");
        System.out.println("AbstractFactory: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UNA "FAMILIA" DE OBJETOS
        =================================================================

        Una tienda en línea vende en México y en Colombia. Para cada país
        necesita VARIOS objetos que trabajan juntos:
        - una calculadora de IMPUESTOS (IVA de 16% en México, 19% en
          Colombia);
        - un formateador de FACTURAS (cada país tiene su formato y su
          numeración legal);
        - una PASARELA DE PAGOS (los medios que se usan en cada país).

        Esos tres objetos forman una FAMILIA: cada uno tiene una versión por
        país, y deben usarse JUNTOS los de la misma familia. Una factura
        mexicana con el IVA colombiano es un error legal y contable.

        Otros ejemplos de familias:
        - los componentes visuales de un tema (botones, menús y ventanas
          del modo claro o del modo oscuro);
        - el driver de una base de datos (la conexión, las consultas y los
          resultados de PostgreSQL o de MySQL);
        - los objetos de un sistema de archivos (rutas y búsquedas del
          disco local o de un archivo .zip; CAPA 8).
    */
    interface TaxCalculator {
        BigDecimal taxFor(BigDecimal amount);
    }

    interface InvoiceFormatter {
        String format(String customer, BigDecimal amount, BigDecimal tax);
    }

    interface PaymentGateway {
        String charge(BigDecimal total);
    }

    // Familia MÉXICO
    static final class MexicanTax implements TaxCalculator {
        @Override
        public BigDecimal taxFor(BigDecimal amount) {
            return amount.multiply(new BigDecimal("0.16")).setScale(2, RoundingMode.HALF_UP);
        }
    }

    static final class MexicanInvoice implements InvoiceFormatter {
        private final AtomicInteger folio = new AtomicInteger();

        @Override
        public String format(String customer, BigDecimal amount, BigDecimal tax) {
            return "MX-" + folio.incrementAndGet() + " | " + customer + " | subtotal " + amount + " | IVA " + tax;
        }
    }

    static final class MexicanPayments implements PaymentGateway {
        @Override
        public String charge(BigDecimal total) {
            return "cobro en MXN por " + total;
        }
    }

    // Familia COLOMBIA
    static final class ColombianTax implements TaxCalculator {
        @Override
        public BigDecimal taxFor(BigDecimal amount) {
            return amount.multiply(new BigDecimal("0.19")).setScale(2, RoundingMode.HALF_UP);
        }
    }

    static final class ColombianInvoice implements InvoiceFormatter {
        private final AtomicInteger folio = new AtomicInteger();

        @Override
        public String format(String customer, BigDecimal amount, BigDecimal tax) {
            return "CO-" + folio.incrementAndGet() + " | " + customer + " | base " + amount + " | IVA " + tax;
        }
    }

    static final class ColombianPayments implements PaymentGateway {
        @Override
        public String charge(BigDecimal total) {
            return "cobro en COP por " + total;
        }
    }

    /*
        =================================================================
        CAPA 2) EL PROBLEMA: COMBINACIONES QUE NO DEBERÍAN EXISTIR
        =================================================================

        La forma ingenua: cada parte del código elige su objeto por su
        cuenta, con un if por país:

            TaxCalculator tax = country.equals("MX") ? new MexicanTax() : new ColombianTax();
            ...en otro archivo...
            InvoiceFormatter invoice = region.equals("CO") ? new ColombianInvoice() : new MexicanInvoice();

        Problemas:
        1) El if por país se repite en cada lugar que crea uno de esos
           objetos (la cirugía de escopeta; v10_design/part2_solid/
           OpenClosed, CAPA 3). Agregar Perú es buscarlos todos.
        2) NADA garantiza que las piezas sean de la misma familia. Basta
           que dos partes lean el país de lugares distintos (uno de la
           cuenta del cliente, otro de la dirección de envío) para armar
           una factura mexicana con IVA colombiano. Compila, funciona... y
           está mal.

        El ejemplo reproduce ese error: el checkout recibe las piezas por
        separado, y alguien le pasa una combinación imposible.
    */
    static String checkoutWithLoosePieces(TaxCalculator tax, InvoiceFormatter invoice, PaymentGateway payments,
                                          String customer, BigDecimal amount) {
        BigDecimal taxAmount = tax.taxFor(amount);
        return invoice.format(customer, amount, taxAmount) + " || " + payments.charge(amount.add(taxAmount));
    }

    static boolean mixedFamilyBugExample() {
        String mixed = checkoutWithLoosePieces(new ColombianTax(), new MexicanInvoice(), new MexicanPayments(),
                "Ana", new BigDecimal("1000"));
        return mixed.startsWith("MX-1") && mixed.contains("IVA 190.00");   // factura mexicana con IVA de 19%: un error legal
    }

    /*
        =================================================================
        CAPA 3) LA SOLUCIÓN: UNA FÁBRICA POR FAMILIA
        =================================================================

        ABSTRACT FACTORY: "Proporciona una interface para crear FAMILIAS de
        objetos relacionados sin especificar sus clases concretas."

        - Una interface, CountryFactory, con un método de creación por cada
          tipo de objeto de la familia: createTaxCalculator(),
          createInvoiceFormatter(), createPaymentGateway().
        - Una implementación por familia: MexicoFactory crea SOLO objetos
          mexicanos; ColombiaFactory, solo colombianos.
        - El código que los usa (Checkout) recibe UNA fábrica y le pide
          todo a ella.

        Consecuencias:
        - Las combinaciones imposibles de la CAPA 2 desaparecen: Checkout
          no puede recibir piezas sueltas, solo una familia completa.
        - Checkout no conoce ninguna clase concreta ni ningún país.
        - El if por país desaparece del código: se decide una sola vez
          cuál fábrica usar (CAPA 5).
    */
    interface CountryFactory {
        TaxCalculator createTaxCalculator();

        InvoiceFormatter createInvoiceFormatter();

        PaymentGateway createPaymentGateway();
    }

    static final class MexicoFactory implements CountryFactory {
        @Override
        public TaxCalculator createTaxCalculator() {
            return new MexicanTax();
        }

        @Override
        public InvoiceFormatter createInvoiceFormatter() {
            return new MexicanInvoice();
        }

        @Override
        public PaymentGateway createPaymentGateway() {
            return new MexicanPayments();
        }
    }

    static final class ColombiaFactory implements CountryFactory {
        @Override
        public TaxCalculator createTaxCalculator() {
            return new ColombianTax();
        }

        @Override
        public InvoiceFormatter createInvoiceFormatter() {
            return new ColombianInvoice();
        }

        @Override
        public PaymentGateway createPaymentGateway() {
            return new ColombianPayments();
        }
    }

    static final class Checkout {                           // el CLIENTE: no conoce ningún país
        private final TaxCalculator tax;
        private final InvoiceFormatter invoices;
        private final PaymentGateway payments;

        Checkout(CountryFactory factory) {                  // recibe UNA familia, no piezas sueltas
            this.tax = factory.createTaxCalculator();
            this.invoices = factory.createInvoiceFormatter();
            this.payments = factory.createPaymentGateway();
        }

        String pay(String customer, BigDecimal amount) {
            BigDecimal taxAmount = tax.taxFor(amount);
            return invoices.format(customer, amount, taxAmount) + " || " + payments.charge(amount.add(taxAmount));
        }
    }

    static boolean abstractFactoryExample() {
        BigDecimal amount = new BigDecimal("1000");
        String mexico = new Checkout(new MexicoFactory()).pay("Ana", amount);
        String colombia = new Checkout(new ColombiaFactory()).pay("Luis", amount);
        return mexico.equals("MX-1 | Ana | subtotal 1000 | IVA 160.00 || cobro en MXN por 1160.00")
                && colombia.equals("CO-1 | Luis | base 1000 | IVA 190.00 || cobro en COP por 1190.00");
    }

    /*
        =================================================================
        CAPA 4) LA ESTRUCTURA, CON SUS NOMBRES
        =================================================================

            AbstractFactory   (CountryFactory)     declara un método de creación
                                                   por cada producto de la familia
            ConcreteFactory   (MexicoFactory)      crea la familia de un país
            AbstractProduct   (TaxCalculator...)   la interface de cada producto
            ConcreteProduct   (MexicanTax...)      cada versión concreta
            Client            (Checkout)           usa solo las interfaces

                                  CountryFactory
                       createTax / createInvoice / createPayment
                          ^                         ^
                 MexicoFactory               ColombiaFactory
              MexicanTax, MexicanInvoice,  ColombianTax, ColombianInvoice,
              MexicanPayments              ColombianPayments

        Relación con la guía anterior: cada método de una Abstract Factory
        es, en el fondo, un Factory Method (part1_creational/FactoryMethod).
        La diferencia es de propósito: Factory Method varía QUÉ se crea en
        UN paso de un proceso; Abstract Factory agrupa VARIAS creaciones
        que deben ser coherentes entre sí.
    */

    /*
        =================================================================
        CAPA 5) ELEGIR LA FAMILIA UNA SOLA VEZ
        =================================================================

        En algún lugar hay que decidir qué fábrica usar (por configuración,
        por el país de la tienda, por el dominio web). Ese lugar es UNO: el
        composition root (v10_design/part3_responsibilities/
        DependencyInjection, CAPA 4), normalmente con un registro por
        código de país (v10_design/part2_solid/OpenClosed, CAPA 5).

        Una vez elegida, la fábrica se inyecta, y ningún otro lugar del
        sistema vuelve a preguntar por el país. Un país desconocido falla
        en ese momento, con un mensaje claro, en lugar de caer en un
        "default" que mezcle familias.
    */
    static final Map<String, CountryFactory> FACTORIES = Map.of(
            "MX", new MexicoFactory(),
            "CO", new ColombiaFactory());

    static CountryFactory factoryFor(String countryCode) {
        CountryFactory factory = FACTORIES.get(countryCode);
        if (factory == null) {
            throw new IllegalArgumentException("País sin configuración: " + countryCode);
        }
        return factory;
    }

    static boolean selectionExample() {
        String storeCountry = "CO";                         // vendría de la configuración
        Checkout checkout = new Checkout(factoryFor(storeCountry));

        boolean unknownFails;
        try {
            factoryFor("PE");
            unknownFails = false;
        } catch (IllegalArgumentException e) {
            unknownFails = e.getMessage().equals("País sin configuración: PE");
        }
        return checkout.pay("Mia", new BigDecimal("100")).startsWith("CO-1") && unknownFails;
    }

    /*
        =================================================================
        CAPA 6) FÁCIL AGREGAR FAMILIAS, DIFÍCIL AGREGAR PRODUCTOS
        =================================================================

        Abstract Factory tiene una asimetría que hay que conocer antes de
        elegirlo:

        - Agregar una FAMILIA (Perú) es fácil y no toca nada existente: una
          PeruFactory nueva con sus tres productos, y una línea en el
          registro. Abierto/Cerrado de libro.

        - Agregar un PRODUCTO a la familia (por ejemplo, reglas de envío por
          país) es caro: hay que agregar un método a la interface
          CountryFactory, y TODAS las fábricas existentes dejan de compilar
          hasta implementarlo.

        Es el mismo dilema del "problema de la expresión"
        (v2_oop/part4_advanced_types/SealedAndPatternMatching, CAPA 10):
        este diseño hace fácil un eje de cambio y difícil el otro.
        Mitigaciones: un método default en la interface si hay un
        comportamiento razonable para casi todas, o aceptar el cambio
        sabiendo que el compilador te lleva a cada fábrica.

        Por eso el patrón es bueno cuando las FAMILIAS crecen (más países,
        más temas, más bases de datos) y los TIPOS de producto son
        estables.
    */

    /*
        =================================================================
        CAPA 7) LA VERSIÓN MODERNA: UN RECORD CON LA FAMILIA YA ARMADA
        =================================================================

        La fábrica clásica CREA objetos nuevos cada vez que se le piden.
        Eso importa si los productos tienen ESTADO propio (el formateador
        de facturas lleva su folio) o si se crean con datos del momento.

        Pero muchas veces los productos son objetos SIN estado que basta con
        crear una vez. Entonces la "familia" puede ser simplemente un RECORD
        que agrupa las tres piezas ya creadas, construido una vez por país:

            record Region(TaxCalculator tax, InvoiceFormatter invoices, PaymentGateway payments)

        Se conserva lo importante (el cliente recibe UNA familia, nunca
        piezas sueltas) con menos clases. Si después algún producto necesita
        crearse nuevo cada vez, el campo puede ser un Supplier (la fábrica
        como función; part1_creational/FactoryMethod, CAPA 8).
    */
    record Region(TaxCalculator tax, InvoiceFormatter invoices, PaymentGateway payments) {
        static Region mexico() {
            return new Region(new MexicanTax(), new MexicanInvoice(), new MexicanPayments());
        }

        static Region colombia() {
            return new Region(new ColombianTax(), new ColombianInvoice(), new ColombianPayments());
        }

        String pay(String customer, BigDecimal amount) {
            BigDecimal taxAmount = tax.taxFor(amount);
            return invoices.format(customer, amount, taxAmount) + " || " + payments.charge(amount.add(taxAmount));
        }
    }

    static boolean recordFamilyExample() {
        BigDecimal amount = new BigDecimal("1000");
        return Region.mexico().pay("Ana", amount).equals(new Checkout(new MexicoFactory()).pay("Ana", amount))
                && Region.colombia().pay("Luis", amount).equals(new Checkout(new ColombiaFactory()).pay("Luis", amount));
    }

    /*
        =================================================================
        CAPA 8) ABSTRACT FACTORY EN EL JDK
        =================================================================

        - java.nio.file.FileSystem: es una fábrica de una FAMILIA de objetos
          que deben ser del mismo sistema de archivos: getPath(...) crea
          rutas, getPathMatcher(...) crea buscadores, newWatchService() crea
          un vigilante de cambios. FileSystems.getDefault() da la familia
          del disco del sistema operativo (con su separador, / o \), y
          FileSystems.newFileSystem(archivoZip) da la familia de un .zip,
          con la que puedes manejar su contenido como si fueran carpetas.
          Un Path de un zip no se mezcla con otro del disco: cada uno
          pertenece a SU FileSystem.

        - La conexión de JDBC (la API de Java para bases de datos):
          connection.createStatement() y prepareStatement(...) crean objetos
          de la familia del driver de ESA base de datos (PostgreSQL, MySQL).
          Tu código usa las interfaces de java.sql y nunca las clases del
          driver.

        - javax.xml.parsers.DocumentBuilderFactory.newInstance(): elige la
          implementación del lector de XML disponible.

        El ejemplo pide al FileSystem del sistema operativo una ruta: el
        objeto que crea respeta las reglas de SU familia (el separador).
    */
    static boolean jdkExample() {
        FileSystem system = FileSystems.getDefault();        // la familia del sistema operativo
        Path path = system.getPath("docs", "factura.pdf");    // creado POR la fábrica
        return path.getFileSystem() == system
                && path.toString().equals("docs" + system.getSeparator() + "factura.pdf");
    }

    /*
        =================================================================
        CAPA 9) FACTORY METHOD, ABSTRACT FACTORY Y BUILDER
        =================================================================

        Los tres patrones creacionales que más se confunden:

        | Patrón           | Responde a                                  | Ejemplo                      |
        |------------------|---------------------------------------------|------------------------------|
        | Factory Method   | ¿QUÉ objeto crear en este paso de un        | cada campaña crea su         |
        |                  | proceso? (lo deciden las subclases)         | mensaje                      |
        | Abstract Factory | ¿cómo crear VARIOS objetos que deben ser    | impuestos + facturas + pagos |
        |                  | de la misma familia?                        | del mismo país               |
        | Builder          | ¿cómo armar UN objeto complicado, paso a    | un pedido con muchos campos  |
        |                  | paso? (part1_creational/Builder)            | opcionales                   |
    */

    /*
        =================================================================
        CAPA 10) CUÁNDO USARLO Y CUÁNDO NO
        =================================================================

        ÚSALO cuando:
        - Hay VARIOS tipos de objetos que siempre deben usarse JUNTOS, con
          una versión por "familia" (país, tema, proveedor, base de datos).
        - Mezclar familias sería un error real (legal, visual, técnico).
        - Las familias crecen y los tipos de producto son estables
          (CAPA 6).

        NO lo uses cuando:
        - Solo hay UN tipo de objeto que varía: Factory Method, una fábrica
          simple o inyectar la implementación.
        - Hay una sola familia y no se espera otra (YAGNI;
          v10_design/part1_basics/BasicPrinciples, CAPA 8).
        - Lo que varía son solo valores (tasas, textos): configuración, no
          clases (v10_design/part2_solid/OpenClosed, CAPA 10). Si la única
          diferencia entre países fuera la tasa de IVA, una tabla bastaría.
        - Los productos no tienen estado: muchas veces alcanza con el
          record de la CAPA 7.
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Elegir cada pieza por separado con ifs por país: combinaciones
           imposibles y cirugía de escopeta (CAPA 2).

        2) Pasar las piezas sueltas al cliente en vez de la familia
           (CAPA 2).

        3) Decidir la familia en varios lugares, o con un default que
           mezcla (CAPA 5).

        4) Usarlo cuando los TIPOS de producto cambian seguido: cada
           producto nuevo rompe todas las fábricas (CAPA 6).

        5) Una fábrica de clases para lo que solo son valores de
           configuración (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Familia: varios objetos que deben usarse juntos, con una versión
          por país/tema/proveedor.
        - Sin el patrón: ifs repetidos y combinaciones imposibles.
        - Abstract Factory: una interface con un método por producto; una
          implementación por familia; el cliente recibe UNA familia.
        - La familia se elige una vez (registro en el composition root).
        - Fácil agregar familias; caro agregar tipos de producto.
        - Versión moderna: un record con la familia armada, si los
          productos no tienen estado.
        - En el JDK: FileSystem, la Connection de JDBC.
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
