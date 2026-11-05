package v10_design.part2_solid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/*
    =====================================================================
    PRINCIPIO DE SUSTITUCIÓN DE LISKOV (LA L DE SOLID)
    =====================================================================

    Ruta de design/: guía 4 de 10  (part2_solid)
    Anterior:  part2_solid/OpenClosed
    Siguiente: part2_solid/InterfaceSegregation

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: herencia y el ejemplo Rectángulo/Cuadrado
    (v2_oop/part2_pillars/Inheritance, CAPA 10), polimorfismo e
    interfaces.

    Índice:
        CAPA 1)  Qué dice el principio
        CAPA 2)  Por qué importa: sin Liskov, el polimorfismo no sirve
        CAPA 3)  El cuadrado, otra vez: el problema era la mutabilidad
        CAPA 4)  El contrato: lo que un tipo promete
        CAPA 5)  Regla 1: no exigir más (precondiciones)
        CAPA 6)  Regla 2: no prometer menos (postcondiciones)
        CAPA 7)  Regla 3: no lanzar sorpresas (excepciones)
        CAPA 8)  Regla 4: respetar los invariantes
        CAPA 9)  Lo que el compilador revisa y lo que no
        CAPA 10) Liskov en el JDK: operaciones "opcionales"
        CAPA 11) Pruebas de contrato: detectar violaciones
        CAPA 12) Cómo rediseñar
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class LiskovSubstitution {

    public static void main(String[] args) {
        check(instanceofSmellExample(), "CAPA 2");
        check(immutableShapesExample(), "CAPA 3");
        check(preconditionExample(), "CAPA 5");
        check(postconditionExample(), "CAPA 6");
        check(exceptionsExample(), "CAPA 7");
        check(invariantExample(), "CAPA 8");
        check(jdkOptionalOperationsExample(), "CAPA 10");
        check(contractTestsExample(), "CAPA 11");
        check(redesignExample(), "CAPA 12");
        System.out.println("LiskovSubstitution: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ DICE EL PRINCIPIO
        =================================================================

        Barbara Liskov (premio Turing 2008) lo planteó en 1987. En
        palabras simples:

            Si S es un subtipo de T, cualquier código que funcione con un T
            debe seguir funcionando, SIN SABERLO, si le das un S.

        "Subtipo" incluye una clase que hereda (extends) y una clase que
        implementa una interface (implements). Y "funcionar" no es solo
        compilar: es COMPORTARSE como el código espera.

        La consecuencia práctica: "ES UN" en el mundo real no basta para
        heredar. Un cuadrado ES UN rectángulo en geometría, pero en tu
        código un Square no puede reemplazar a un Rectangle con setters
        (Inheritance, CAPA 10). Lo que importa no es qué SON las cosas, sino
        qué PROMETEN hacer. La pregunta correcta es:
            "¿El subtipo puede cumplir TODO lo que promete el tipo base?"
    */

    /*
        =================================================================
        CAPA 2) POR QUÉ IMPORTA: SIN LISKOV, EL POLIMORFISMO NO SIRVE
        =================================================================

        Todo el valor del polimorfismo (y del principio Abierto/Cerrado,
        part2_solid/OpenClosed) es que el código trabaja con la INTERFACE y
        no le importa la implementación concreta. Eso solo es posible si
        TODAS las implementaciones cumplen lo que la interface promete.

        Cuando un subtipo no cumple, quien lo usa empieza a defenderse:

            if (notifier instanceof SmsNotifier && message.length() > 160) {
                ...caso especial...
            }

        Ese instanceof es el síntoma clásico de una violación de Liskov. Y
        destruye el diseño: el código "cerrado" ahora conoce una clase
        concreta y hay que modificarlo cada vez que aparece otro subtipo
        especial. Liskov es lo que hace que OCP funcione.
    */
    interface Notifier {
        /** Envía el mensaje completo al destinatario. */
        String send(String to, String message);
    }

    static final class EmailNotifier implements Notifier {
        @Override
        public String send(String to, String message) {
            return "correo a " + to + ": " + message;
        }
    }

    static final class SmsNotifier implements Notifier {
        @Override
        public String send(String to, String message) {
            return "sms a " + to + ": " + message.substring(0, Math.min(message.length(), 20));   // corta en silencio
        }
    }

    // El cliente, que debería funcionar con CUALQUIER Notifier, tuvo que aprender sobre uno.
    static String notifyOrderShipped(Notifier notifier, String to) {
        String message = "Tu pedido 1234 salió hoy y llega el jueves";
        if (notifier instanceof SmsNotifier) {              // ERROR de diseño: el síntoma
            message = "Pedido 1234 enviado";
        }
        return notifier.send(to, message);
    }

    static boolean instanceofSmellExample() {
        String fullMessage = "Tu pedido 1234 salió hoy y llega el jueves";
        boolean smsBreaksPromise = !new SmsNotifier().send("555", fullMessage).endsWith(fullMessage);   // no envió completo
        return smsBreaksPromise && notifyOrderShipped(new SmsNotifier(), "555").endsWith("Pedido 1234 enviado");
    }

    /*
        =================================================================
        CAPA 3) EL CUADRADO, OTRA VEZ: EL PROBLEMA ERA LA MUTABILIDAD
        =================================================================

        En Inheritance, CAPA 10, Square heredaba de un Rectangle con
        setWidth y setHeight. Rectangle prometía, sin decirlo, que ancho y
        alto se cambian POR SEPARADO. Square no lo puede cumplir: si cambia
        uno, debe cambiar el otro. Por eso resizeTo5x4 daba 16 en vez de 20.

        El conflicto NO es "cuadrado vs rectángulo"; es la promesa de los
        SETTERS. Si las figuras son INMUTABLES (no se cambian, se crean
        nuevas), la promesa desaparece y la jerarquía funciona: un cuadrado
        de lado 4 se comporta exactamente como un rectángulo de 4 x 4 en
        todo lo que se le pueda preguntar (área, perímetro).

        Es un patrón general: muchas violaciones de Liskov aparecen con la
        MUTACIÓN. Los objetos inmutables (records) tienen contratos más
        simples y es mucho más difícil romperlos.
    */
    interface Shape {
        double area();

        double perimeter();
    }

    record RectangleShape(double width, double height) implements Shape {
        @Override
        public double area() {
            return width * height;
        }

        @Override
        public double perimeter() {
            return 2 * (width + height);
        }
    }

    record SquareShape(double side) implements Shape {
        @Override
        public double area() {
            return side * side;
        }

        @Override
        public double perimeter() {
            return 4 * side;
        }
    }

    static double totalArea(List<Shape> shapes) {           // funciona con cualquier Shape
        return shapes.stream().mapToDouble(Shape::area).sum();
    }

    static boolean immutableShapesExample() {
        Shape square = new SquareShape(4);
        Shape sameAsRectangle = new RectangleShape(4, 4);
        return square.area() == sameAsRectangle.area() && square.perimeter() == sameAsRectangle.perimeter()
                && totalArea(List.of(square, new RectangleShape(5, 4))) == 36;
    }

    /*
        =================================================================
        CAPA 4) EL CONTRATO: LO QUE UN TIPO PROMETE
        =================================================================

        Para saber si un subtipo "cumple", hay que saber QUÉ promete el tipo
        base. Bertrand Meyer lo formalizó como DISEÑO POR CONTRATO; cada
        método tiene:

        - PRECONDICIONES: lo que el método EXIGE para funcionar ("el monto
          debe ser positivo").
        - POSTCONDICIONES: lo que GARANTIZA al terminar ("devuelve un
          precio entre 0 y el original", "la lista queda ordenada").
        - INVARIANTES: lo que es SIEMPRE cierto del objeto ("el saldo nunca
          es negativo").
        - Y las EXCEPCIONES que puede lanzar, y cuándo.

        En Java ese contrato está, de más a menos formal, en: la firma del
        método (tipos y throws), el Javadoc ("@throws IllegalArgumentException
        si amount <= 0"), los nombres, y las pruebas.

        Las reglas de Liskov dicen qué puede hacer un subtipo con ese
        contrato. La versión corta: un subtipo puede ser MÁS GENEROSO, nunca
        MÁS EXIGENTE ni MENOS CONFIABLE.
            - Puede exigir IGUAL o MENOS               (CAPA 5)
            - Debe garantizar IGUAL o MÁS              (CAPA 6)
            - No puede lanzar errores nuevos           (CAPA 7)
            - Debe conservar los invariantes           (CAPA 8)
    */

    /*
        =================================================================
        CAPA 5) REGLA 1: NO EXIGIR MÁS (PRECONDICIONES)
        =================================================================

        Un subtipo NO puede tener precondiciones MÁS FUERTES que su tipo
        base. Quien llama solo conoce las del tipo base y las cumple; si el
        subtipo exige más, falla con entradas perfectamente válidas.

        Ejemplo: ShippingCalculator promete calcular el envío de CUALQUIER
        paquete (peso >= 0). Una implementación para motocicleta solo
        acepta hasta 5 kg y lanza excepción con más. El carrito, que usa
        "un ShippingCalculator", no tiene por qué saberlo y explota.

        Lo contrario SÍ está permitido: un subtipo puede ser más generoso
        (aceptar más casos que el base).

        Cómo arreglarlo: si de verdad hay calculadoras con límite, el
        límite debe ser parte del CONTRATO base, visible para todos (por
        ejemplo, un método supports(peso) que el carrito consulta, o
        devolver Optional.empty() cuando no aplica), no una sorpresa de una
        implementación.
    */
    interface ShippingCalculator {
        /** Costo de enviar un paquete de cualquier peso (gramos >= 0). */
        BigDecimal cost(int grams);
    }

    static final class GroundShipping implements ShippingCalculator {
        @Override
        public BigDecimal cost(int grams) {
            return new BigDecimal("99").add(BigDecimal.valueOf(grams / 1000 * 10L));
        }
    }

    static final class MotorcycleShipping implements ShippingCalculator {
        @Override
        public BigDecimal cost(int grams) {
            if (grams > 5_000) {                            // precondición MÁS FUERTE: viola Liskov
                throw new IllegalArgumentException("La moto no lleva más de 5 kg");
            }
            return new BigDecimal("49");
        }
    }

    static BigDecimal shippingForCart(ShippingCalculator calculator, int cartGrams) {
        return calculator.cost(cartGrams);                  // confía en el contrato del tipo base
    }

    static boolean preconditionExample() {
        boolean groundWorks = shippingForCart(new GroundShipping(), 8_000).compareTo(new BigDecimal("179")) == 0;
        boolean motorcycleBreaksCaller;
        try {
            shippingForCart(new MotorcycleShipping(), 8_000);   // entrada válida según el contrato
            motorcycleBreaksCaller = false;
        } catch (IllegalArgumentException e) {
            motorcycleBreaksCaller = true;
        }
        return groundWorks && motorcycleBreaksCaller;
    }

    /*
        =================================================================
        CAPA 6) REGLA 2: NO PROMETER MENOS (POSTCONDICIONES)
        =================================================================

        Un subtipo NO puede tener postcondiciones MÁS DÉBILES. Si el tipo
        base garantiza algo sobre el resultado, todos los subtipos deben
        garantizarlo, porque quien llama CONSTRUYE sobre esa garantía.

        Ejemplo: DiscountPolicy promete "el precio con descuento está entre
        0 y el precio original". El checkout suma esos precios sin
        revisarlos. Una política de liquidación mal hecha devuelve un
        precio NEGATIVO para productos baratos, y el total del pedido baja:
        la tienda le termina debiendo dinero al cliente.

        Lo contrario sí está permitido: un subtipo puede garantizar MÁS
        (por ejemplo, además redondear a centavos).
    */
    interface DiscountPolicy {
        /** Devuelve un precio entre 0 y price (inclusive). */
        BigDecimal apply(BigDecimal price);
    }

    static final class TenPercentOff implements DiscountPolicy {
        @Override
        public BigDecimal apply(BigDecimal price) {
            return price.multiply(new BigDecimal("0.90"));
        }
    }

    static final class BrokenClearance implements DiscountPolicy {
        @Override
        public BigDecimal apply(BigDecimal price) {
            return price.subtract(new BigDecimal("100"));   // con precios menores a 100: ¡negativo!
        }
    }

    static final class Clearance implements DiscountPolicy {
        @Override
        public BigDecimal apply(BigDecimal price) {
            return price.subtract(new BigDecimal("100")).max(BigDecimal.ZERO);   // cumple la promesa
        }
    }

    static BigDecimal checkoutTotal(List<BigDecimal> prices, DiscountPolicy policy) {
        return prices.stream().map(policy::apply).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static boolean postconditionExample() {
        List<BigDecimal> cart = List.of(new BigDecimal("500"), new BigDecimal("30"));
        return checkoutTotal(cart, new TenPercentOff()).compareTo(new BigDecimal("477")) == 0
                && checkoutTotal(cart, new BrokenClearance()).compareTo(new BigDecimal("330")) == 0   // 400 + (-70)
                && checkoutTotal(cart, new Clearance()).compareTo(new BigDecimal("400")) == 0;        // 400 + 0
    }

    /*
        =================================================================
        CAPA 7) REGLA 3: NO LANZAR SORPRESAS (EXCEPCIONES)
        =================================================================

        Un subtipo no puede lanzar excepciones que el contrato del tipo base
        no anuncia. Para las checked, Java lo impide (CAPA 9). Para las
        unchecked, no: es responsabilidad tuya.

        El caso más típico: un subtipo que "no puede" hacer algo que la
        interface promete y lo resuelve con:
            throw new UnsupportedOperationException();
        Ejemplo clásico de enseñanza: Bird tiene fly(); Penguin hereda de
        Bird y lanza excepción en fly(). Todo código que recorra "aves" y
        las haga volar explota con el pingüino.

        Igual de malo es el método que, en vez de lanzar, NO HACE NADA (un
        override vacío): el pingüino "vuela" sin moverse, y el error es
        silencioso.

        La pregunta: si el subtipo no puede cumplir un método de la
        interface, ¿por qué es ese subtipo? La solución está en la CAPA 12.
    */
    static class Bird {
        int altitude;

        void fly() {
            altitude += 100;
        }
    }

    static final class Sparrow extends Bird { }

    static final class Penguin extends Bird {
        @Override
        void fly() {
            throw new UnsupportedOperationException("Los pingüinos no vuelan");   // viola el contrato de Bird
        }
    }

    static int launchAll(List<Bird> birds) {
        for (Bird bird : birds) {
            bird.fly();
        }
        return birds.stream().mapToInt(bird -> bird.altitude).sum();
    }

    static boolean exceptionsExample() {
        boolean sparrowsFine = launchAll(List.of(new Sparrow(), new Sparrow())) == 200;
        boolean penguinBreaksLoop;
        try {
            launchAll(List.of(new Sparrow(), new Penguin()));
            penguinBreaksLoop = false;
        } catch (UnsupportedOperationException e) {
            penguinBreaksLoop = true;
        }
        return sparrowsFine && penguinBreaksLoop;
    }

    /*
        =================================================================
        CAPA 8) REGLA 4: RESPETAR LOS INVARIANTES
        =================================================================

        Lo que el tipo base garantiza que es SIEMPRE cierto, también debe
        serlo en el subtipo. Si BankAccount garantiza "el saldo nunca es
        negativo", todo el sistema lo asume: reportes, cálculos de interés,
        límites. Un subtipo que permite sobregiro rompe a todos sin que
        ninguno haya cambiado.

        Un caso particular muy importante: si el tipo base es INMUTABLE o
        promete que algo no cambia, un subtipo no puede agregar formas de
        cambiarlo (la "restricción de historia"). Por eso las clases
        inmutables deben ser final (v2_oop/part2_pillars/Encapsulation,
        CAPA 7): un subtipo mutable rompería a quien confía en la
        inmutabilidad (por ejemplo, usándolo como clave de un mapa).

        Ojo: el sobregiro en sí NO está mal. Lo malo es esconderlo detrás
        del tipo que prometía lo contrario. Si existe, va en un tipo con un
        contrato que lo diga (CAPA 12).
    */
    static class BankAccount {
        protected BigDecimal balance = BigDecimal.ZERO;

        void deposit(BigDecimal amount) {
            balance = balance.add(amount);
        }

        /** Invariante: después de withdraw, el saldo sigue siendo >= 0. */
        void withdraw(BigDecimal amount) {
            if (amount.compareTo(balance) > 0) {
                throw new IllegalStateException("Fondos insuficientes");
            }
            balance = balance.subtract(amount);
        }

        BigDecimal balance() {
            return balance;
        }
    }

    static final class OverdraftAccount extends BankAccount {
        @Override
        void withdraw(BigDecimal amount) {
            balance = balance.subtract(amount);             // rompe el invariante: el saldo puede ser negativo
        }
    }

    // Código que confía en el invariante de BankAccount.
    static BigDecimal monthlyInterest(BankAccount account) {
        return account.balance().multiply(new BigDecimal("0.01"));   // supone saldo >= 0
    }

    static boolean invariantExample() {
        BankAccount account = new OverdraftAccount();
        account.deposit(new BigDecimal("100"));
        account.withdraw(new BigDecimal("300"));
        return account.balance().signum() < 0                         // "imposible" para BankAccount
                && monthlyInterest(account).signum() < 0;             // ¡el banco cobra interés negativo!
    }

    /*
        =================================================================
        CAPA 9) LO QUE EL COMPILADOR REVISA Y LO QUE NO
        =================================================================

        Java impone automáticamente la parte "de forma" de Liskov al
        sobrescribir un método:

        1) El tipo de retorno puede ser el MISMO o MÁS ESPECÍFICO (retorno
           covariante): si el padre devuelve Object, el hijo puede devolver
           String. Es una garantía más fuerte, así que está permitido.

        2) No puede lanzar excepciones CHECKED nuevas o más amplias:
               // ERROR: load() in Sub cannot override load() in Base
               //   overridden method does not throw IOException

        3) No puede REDUCIR la visibilidad (de protected a privado de
           paquete, o de public a protected):
               // ERROR: m() in Sub2 cannot override m() in Base
               //   attempting to assign weaker access privileges; was protected

        Las tres son la misma idea: el hijo puede ofrecer igual o más, nunca
        menos.

        Lo que el compilador NO puede revisar es el COMPORTAMIENTO: que no
        exijas más (CAPA 5), que cumplas lo prometido (CAPA 6), que no
        lances unchecked sorpresa (CAPA 7), que respetes invariantes
        (CAPA 8). Para eso están la documentación y las pruebas de
        contrato (CAPA 11).
    */

    /*
        =================================================================
        CAPA 10) LISKOV EN EL JDK: OPERACIONES "OPCIONALES"
        =================================================================

        El JDK tiene casos famosos donde se tomó una decisión discutible:

        1) List.add en listas inmutables. La interface List dice que add
           es una "operación opcional": una implementación puede lanzar
           UnsupportedOperationException. List.of, Collections.
           unmodifiableList y (para add) Arrays.asList lo hacen
           (v5_collections/part4_tools/ImmutableCollections). Técnicamente
           cumple el contrato, porque el contrato lo AVISA. Pero el costo
           es que nadie que recibe "una List" puede confiar en que add
           funcione: el contrato quedó tan débil que no promete casi nada.
           Fue una decisión para no duplicar todas las interfaces de
           colecciones (una de lectura y otra de escritura), y es la
           motivación de part2_solid/InterfaceSegregation.

        2) Stack hereda de Vector (v5_collections/part3_queues/
           QueuesAndStacks, CAPA 6): una "pila" que deja insertar en medio.

        3) java.sql.Timestamp hereda de java.util.Date, y su propia
           documentación advierte que no se debe usar como un Date porque
           equals no es simétrico entre los dos (v2_oop/part4_advanced_types/
           ObjectContract, CAPA 5).

        Lección: incluso los mejores diseñadores fallan aquí, y una vez
        publicado, un error de Liskov en una API pública queda para
        siempre.
    */
    static int addDefaults(List<String> tags) {             // "recibo una List, le agrego cosas"
        tags.add("nuevo");
        return tags.size();
    }

    static boolean jdkOptionalOperationsExample() {
        boolean worksWithArrayList = addDefaults(new ArrayList<>(List.of("a"))) == 2;
        boolean failsWithListOf;
        try {
            addDefaults(List.of("a"));                      // también es "una List"
            failsWithListOf = false;
        } catch (UnsupportedOperationException e) {
            failsWithListOf = true;
        }
        return worksWithArrayList && failsWithListOf;
    }

    /*
        =================================================================
        CAPA 11) PRUEBAS DE CONTRATO: DETECTAR VIOLACIONES
        =================================================================

        Como el compilador no puede verificar el comportamiento, la técnica
        profesional es escribir las pruebas UNA vez, contra la INTERFACE,
        y correrlas para CADA implementación. A esto se le llama "pruebas
        de contrato".

        Si una implementación nueva no pasa las pruebas del contrato, viola
        Liskov, y te enteras al probarla, no en producción.

        El ejemplo verifica la promesa de DiscountPolicy (CAPA 6) contra las
        tres implementaciones, con precios típicos y extremos (0, menor que
        el descuento, grande). La prueba no sabe nada de cada clase:
        recibe una forma de crearla (un Supplier; v7_functional/
        part1_lambdas/FunctionalInterfaces). En testing/ esto se hace con
        JUnit, la herramienta estándar de pruebas en Java.

        Pistas para encontrar violaciones sin pruebas, al leer código:
        - instanceof o getClass() antes de llamar a un método (CAPA 2);
        - overrides que lanzan UnsupportedOperationException o están vacíos
          (CAPA 7);
        - Javadoc con "excepto en la subclase X..." o "no usar con...";
        - un override que valida MÁS que el método del padre (CAPA 5).
    */
    static boolean honorsDiscountContract(Supplier<DiscountPolicy> factory) {
        DiscountPolicy policy = factory.get();
        for (BigDecimal price : List.of(BigDecimal.ZERO, new BigDecimal("30"), new BigDecimal("100"), new BigDecimal("5000"))) {
            BigDecimal result = policy.apply(price);
            boolean withinRange = result.signum() >= 0 && result.compareTo(price) <= 0;
            if (!withinRange) {
                return false;
            }
        }
        return true;
    }

    static boolean contractTestsExample() {
        return honorsDiscountContract(TenPercentOff::new)
                && honorsDiscountContract(Clearance::new)
                && !honorsDiscountContract(BrokenClearance::new);   // la prueba de contrato la atrapa
    }

    /*
        =================================================================
        CAPA 12) CÓMO REDISEÑAR
        =================================================================

        Cuando un subtipo no puede cumplir el contrato, el problema casi
        nunca está en el subtipo: está en la JERARQUÍA. Opciones:

        1) Dividir la abstracción según la CAPACIDAD real. No todas las aves
           vuelan, así que "volar" no es parte de Bird: es otra interface,
           Flyer, que implementan solo las que vuelan. El código que hace
           volar pide un Flyer, y el compilador impide pasarle un pingüino.
           (Es exactamente part2_solid/InterfaceSegregation.)

        2) Hacer explícito lo que varía en el contrato: si hay envíos con
           límite de peso, el contrato base lo dice (supports(gramos)), y
           todos lo respetan (CAPA 5).

        3) Usar COMPOSICIÓN en vez de herencia: Stack debió TENER un Vector,
           no SER un Vector (v2_oop/part3_interfaces_and_relationships/
           CompositionAndAggregation, CAPA 6).

        4) Hacer los tipos inmutables cuando la violación viene de la
           mutación (CAPA 3).

        5) Si dos cosas solo comparten código pero no comportamiento
           prometido, no deben ser padre e hijo: comparte el código con un
           método o una clase auxiliar.
    */
    interface Animal {
        String name();
    }

    interface Flyer {
        int fly();                                          // solo lo promete quien de verdad vuela
    }

    record Eagle(String name) implements Animal, Flyer {
        @Override
        public int fly() {
            return 300;
        }
    }

    record Emperor(String name) implements Animal { }      // pingüino: un Animal, NO un Flyer

    static int totalAltitude(List<Flyer> flyers) {          // el compilador no deja pasar pingüinos
        return flyers.stream().mapToInt(Flyer::fly).sum();
    }

    static boolean redesignExample() {
        List<Animal> zoo = List.of(new Eagle("águila"), new Emperor("pingüino"));
        List<Flyer> flyers = zoo.stream()
                .filter(Flyer.class::isInstance)            // la capacidad se pregunta en UN lugar, con el tipo correcto
                .map(Flyer.class::cast)
                .toList();
        return totalAltitude(flyers) == 300 && zoo.size() == 2;
    }

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) Heredar porque "en la vida real ES UN", sin revisar si cumple
           el comportamiento prometido (CAPA 1).

        2) Subtipos que exigen más que el tipo base (CAPA 5).

        3) Subtipos que garantizan menos: resultados fuera de rango, null
           donde se prometía un valor (CAPA 6).

        4) Overrides que lanzan UnsupportedOperationException o no hacen
           nada (CAPA 7).

        5) Subtipos que rompen invariantes, o subclases mutables de clases
           inmutables (CAPA 8).

        6) Resolver violaciones con instanceof en los clientes en lugar de
           arreglar la jerarquía (CAPAS 2 y 12).

        7) Contratos que solo existen en la cabeza del autor: documéntalos
           (Javadoc) y pruébalos para cada implementación (CAPAS 4 y 11).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Liskov: un subtipo debe poder reemplazar a su tipo base sin que
          el código que lo usa lo note. Importa el comportamiento, no la
          taxonomía.
        - Sin Liskov, el polimorfismo y OCP no funcionan: aparecen los
          instanceof defensivos.
        - El contrato: precondiciones, postcondiciones, invariantes,
          excepciones.
        - Un subtipo puede exigir igual o menos, garantizar igual o más, no
          lanzar sorpresas y conservar los invariantes.
        - El compilador revisa retornos, excepciones checked y visibilidad;
          el comportamiento lo revisan las pruebas de contrato.
        - Muchas violaciones vienen de la mutación: la inmutabilidad ayuda.
        - Se rediseña dividiendo por capacidades, haciendo explícito el
          contrato o usando composición.
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
