package v2_oop.part4_advanced_types;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/*
    =====================================================================
    ENUMS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 11 de 14  (part4_advanced_types)
    Anterior:  part3_interfaces_and_relationships/NestedClasses
    Siguiente: part4_advanced_types/Records

    Requisito: ClassesAndObjects, Static y Interfaces.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  El problema: constantes int y String
        CAPA 2)  El enum básico y lo que Java genera por ti
        CAPA 3)  Un enum es una clase: campos, constructor y métodos
        CAPA 4)  Comportamiento distinto por constante
        CAPA 5)  switch con enums y exhaustividad
        CAPA 6)  Enums que implementan interfaces
        CAPA 7)  EnumSet y EnumMap
        CAPA 8)  Buscar constantes: valueOf y búsquedas propias
        CAPA 9)  ordinal(): por qué no debes depender de él
        CAPA 10) Singleton con enum
        CAPA 11) Cuándo usar un enum (y cuándo no)
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class Enums {

    public static void main(String[] args) {
        check(intConstantsProblemExample(), "CAPA 1");
        check(basicEnumExample(), "CAPA 2");
        check(enumWithStateExample(), "CAPA 3");
        check(constantSpecificExample(), "CAPA 4");
        check(switchExample(), "CAPA 5");
        check(interfaceExample(), "CAPA 6");
        check(enumCollectionsExample(), "CAPA 7");
        check(lookupExample(), "CAPA 8");
        check(ordinalExample(), "CAPA 9");
        check(singletonExample(), "CAPA 10");
        System.out.println("Enums: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: CONSTANTES INT Y STRING
        =================================================================

        Muchos datos solo pueden tomar un conjunto FIJO de valores: los
        días de la semana, los palos de una baraja, los estados de un
        pedido (pendiente, pagado, enviado, entregado, cancelado).

        Antes de Java 5 se representaban con constantes int:
    */
    static final class LegacyOrderStatus {
        static final int PENDING = 0;
        static final int PAID = 1;
        static final int SHIPPED = 2;

        private LegacyOrderStatus() {
        }
    }

    static final class LegacyShippingSpeed {
        static final int STANDARD = 0;
        static final int EXPRESS = 1;

        private LegacyShippingSpeed() {
        }
    }

    static String describeLegacyStatus(int status) {
        if (status == LegacyOrderStatus.PENDING) {
            return "Pendiente";
        } else if (status == LegacyOrderStatus.PAID) {
            return "Pagado";
        }
        return "Desconocido";
    }

    static boolean intConstantsProblemExample() {
        // Todo esto COMPILA, y todo está mal:
        String mixedTypes = describeLegacyStatus(LegacyShippingSpeed.EXPRESS); // una velocidad usada como estado
        String outOfRange = describeLegacyStatus(42);                          // un estado que no existe

        return mixedTypes.equals("Pagado") && outOfRange.equals("Desconocido");
    }

    /*
        Los problemas:
        1) No hay seguridad de tipos: el método recibe "un int", así que
           acepta CUALQUIER int: una velocidad de envío, un 42, un -1.
        2) Al imprimirlo o verlo en el depurador solo ves "1", no "PAID".
        3) No hay forma de recorrer "todos los estados posibles".
        4) No puedes asociar datos ni comportamiento a cada valor.
        Con constantes String es igual de malo, y además "PAGADO" vs
        "Pagado" son distintos.

        Un ENUM resuelve todo esto: es un TIPO propio cuyos únicos
        valores posibles son los que tú enumeras.
    */

    /*
        =================================================================
        CAPA 2) EL ENUM BÁSICO Y LO QUE JAVA GENERA POR TI
        =================================================================

        Sintaxis mínima (como tu enum Words en Practice/Main.java):

            enum Weekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY }

        Lo que REALMENTE es, por dentro:
        - Weekday es una CLASE que hereda de java.lang.Enum.
        - Cada constante (MONDAY...) es un OBJETO de esa clase, creado
          una sola vez cuando se carga la clase, y guardado en un campo
          public static final.
        - No se pueden crear más: new Weekday() no compila. Las
          constantes que escribiste son TODAS las instancias que
          existirán jamás.

        Lo que Java genera automáticamente:
            Weekday.values()           -> arreglo con todas las constantes, en orden
            Weekday.valueOf("MONDAY")  -> la constante con ese nombre exacto (CAPA 8)
            day.name()                 -> "MONDAY"
            day.ordinal()              -> su posición: 0, 1, 2... (CAPA 9)
            day.toString()             -> igual que name(), salvo que lo sobrescribas
            day.compareTo(otro)        -> compara por orden de declaración
            + equals, hashCode, y es Comparable y Serializable

        Como cada constante existe una sola vez, se comparan con ==
        (además es más seguro que equals: nunca lanza NullPointerException).

        Convención: constantes en MAYÚSCULAS, como cualquier constante.
    */
    enum Weekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY }

    static boolean isLongMeetingDay(Weekday day) {
        return day == Weekday.MONDAY || day == Weekday.FRIDAY;   // solo acepta Weekday: ni un 42 ni una velocidad
    }

    static boolean basicEnumExample() {
        Weekday[] all = Weekday.values();

        return all.length == 5
                && all[0] == Weekday.MONDAY
                && Weekday.FRIDAY.name().equals("FRIDAY")
                && Weekday.MONDAY.compareTo(Weekday.FRIDAY) < 0     // MONDAY se declaró antes
                && isLongMeetingDay(Weekday.FRIDAY)
                && Weekday.MONDAY instanceof Enum<?>;               // hereda de java.lang.Enum
    }

    /*
        =================================================================
        CAPA 3) UN ENUM ES UNA CLASE: CAMPOS, CONSTRUCTOR Y MÉTODOS
        =================================================================

        Como un enum es una clase, puede tener todo lo que tiene una
        clase: campos, constructores y métodos. Cada constante se crea
        llamando al constructor con los argumentos entre paréntesis.

        Reglas:
        - Las constantes van PRIMERO, separadas por comas, terminadas en
          punto y coma si después viene más código.
        - El constructor es SIEMPRE private (aunque no lo escribas): solo
          el propio enum crea sus constantes.
        - Los campos deben ser final: las constantes son compartidas por
          todo el programa, así que un enum mutable es estado global
          (Static, CAPA 10).

        Ejemplo: los estados de un pedido, cada uno con su etiqueta para
        mostrar al usuario y las reglas de a qué estado puede pasar.
        Esto convierte al enum en una MÁQUINA DE ESTADOS: el tipo mismo
        sabe qué transiciones son válidas.
    */
    enum OrderStatus {
        PENDING("Pendiente de pago"),
        PAID("Pagado"),
        SHIPPED("Enviado"),
        DELIVERED("Entregado"),
        CANCELLED("Cancelado");

        private final String label;

        OrderStatus(String label) {   // implícitamente private
            this.label = label;
        }

        String label() {
            return label;
        }

        boolean isFinal() {
            return this == DELIVERED || this == CANCELLED;
        }

        boolean canMoveTo(OrderStatus next) {
            return allowedNext().contains(next);
        }

        Set<OrderStatus> allowedNext() {
            return switch (this) {   // switch sobre el propio enum (CAPA 5)
                case PENDING -> EnumSet.of(PAID, CANCELLED);
                case PAID -> EnumSet.of(SHIPPED, CANCELLED);
                case SHIPPED -> EnumSet.of(DELIVERED);
                case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
            };
        }

        // Búsqueda por etiqueta (CAPA 8).
        private static final Map<String, OrderStatus> BY_LABEL = new HashMap<>();

        static {
            for (OrderStatus status : values()) {
                BY_LABEL.put(status.label, status);
            }
        }

        static OrderStatus fromLabel(String label) {
            OrderStatus status = BY_LABEL.get(label);
            if (status == null) {
                throw new IllegalArgumentException("No existe un estado con la etiqueta: " + label);
            }
            return status;
        }
    }

    // Un pedido que usa el enum para proteger sus reglas.
    static final class Order {
        private OrderStatus status = OrderStatus.PENDING;

        void moveTo(OrderStatus next) {
            Objects.requireNonNull(next, "next");
            if (!status.canMoveTo(next)) {
                throw new IllegalStateException("No se puede pasar de " + status.label() + " a " + next.label());
            }
            status = next;
        }

        OrderStatus getStatus() {
            return status;
        }
    }

    static boolean enumWithStateExample() {
        Order order = new Order();
        order.moveTo(OrderStatus.PAID);
        order.moveTo(OrderStatus.SHIPPED);

        boolean invalidRejected;
        try {
            order.moveTo(OrderStatus.CANCELLED);   // un pedido enviado ya no se cancela
            invalidRejected = false;
        } catch (IllegalStateException e) {
            invalidRejected = true;
        }

        return order.getStatus() == OrderStatus.SHIPPED
                && order.getStatus().label().equals("Enviado")
                && invalidRejected
                && OrderStatus.CANCELLED.isFinal();
    }

    /*
        Un detalle técnico de inicialización: las constantes se crean
        ANTES que los demás campos static del enum (son los primeros
        campos static de la clase). Por eso el constructor de un enum
        NO puede usar campos static que no sean constantes:

            private static int created = 0;
            OrderStatus(String label) { created++; }
            // ERROR: illegal reference to static field from initializer

        Cuando la constante PENDING se construye, "created" todavía no
        existe. Por eso BY_LABEL se llena en un bloque static DESPUÉS de
        las constantes, y no en el constructor.
    */

    /*
        =================================================================
        CAPA 4) COMPORTAMIENTO DISTINTO POR CONSTANTE
        =================================================================

        A veces cada constante necesita su PROPIA versión de un método.
        Una opción es un switch dentro del método (como allowedNext). La
        otra: declarar el método abstract en el enum y que CADA constante
        tenga su propio cuerpo:

            CONSTANTE { método con su implementación },

        Cada constante se convierte en una pequeña clase anónima que
        hereda del enum (NestedClasses, CAPA 6). Si agregas una
        constante nueva y olvidas implementar el método, no compila.

        Ejemplo: velocidades de envío, cada una con su fórmula de costo.
    */
    interface ShippingCostRule {
        BigDecimal cost(double weightKg);
    }

    enum ShippingSpeed implements ShippingCostRule {
        STANDARD {
            @Override
            public BigDecimal cost(double weightKg) {
                return new BigDecimal("50").add(BigDecimal.valueOf(weightKg).multiply(BigDecimal.TEN));
            }
        },
        EXPRESS {
            @Override
            public BigDecimal cost(double weightKg) {
                return new BigDecimal("150").add(BigDecimal.valueOf(weightKg).multiply(new BigDecimal("25")));
            }
        },
        PICKUP_IN_STORE {
            @Override
            public BigDecimal cost(double weightKg) {
                return BigDecimal.ZERO;
            }
        };
        // cost(...) es abstracto porque viene de la interface y el enum no lo implementa:
        // cada constante está OBLIGADA a hacerlo.
    }

    static boolean constantSpecificExample() {
        return ShippingSpeed.STANDARD.cost(2).compareTo(new BigDecimal("70")) == 0     // 50 + 2 x 10
                && ShippingSpeed.EXPRESS.cost(2).compareTo(new BigDecimal("200")) == 0 // 150 + 2 x 25
                && ShippingSpeed.PICKUP_IN_STORE.cost(30).signum() == 0;
    }

    /*
        ¿switch dentro del método o cuerpo por constante?
        - Cuerpo por constante: cuando el comportamiento es parte
          esencial de cada constante y hay varias líneas por caso.
          Es el patrón Strategy en forma de enum.
        - switch: cuando es una decisión pequeña, o cuando el
          comportamiento NO le pertenece al enum (CAPA 5).
    */

    /*
        =================================================================
        CAPA 5) SWITCH CON ENUMS Y EXHAUSTIVIDAD
        =================================================================

        Los enums y el switch se llevan muy bien:
        - En los case se escribe solo el nombre: case EXPRESS, no
          case ShippingSpeed.EXPRESS (desde Java 21 ambos se permiten).
        - Con un switch EXPRESIÓN (el que devuelve un valor con ->), el
          compilador verifica que cubriste TODAS las constantes. No
          necesitas default.

        Esa verificación es la gran ventaja: si mañana se agrega
        ShippingSpeed.SAME_DAY, todo switch expresión que no la contemple
        deja de compilar:
            // ERROR: the switch expression does not cover all possible input values
        El compilador te lleva de la mano a cada lugar que hay que
        actualizar. Si hubieras puesto default, el caso nuevo caería ahí
        en silencio.
    */
    static int estimatedDays(ShippingSpeed speed) {
        return switch (speed) {
            case STANDARD -> 5;
            case EXPRESS -> 1;
            case PICKUP_IN_STORE -> 0;
            // sin default a propósito: así el compilador revisa que no falte ninguna
        };
    }

    static boolean switchExample() {
        int total = 0;
        for (ShippingSpeed speed : ShippingSpeed.values()) {
            total += estimatedDays(speed);
        }
        return total == 6 && estimatedDays(ShippingSpeed.EXPRESS) == 1;
    }

    /*
        estimatedDays vive FUERA del enum a propósito: los días de
        entrega dependen de la paquetería que contrate cada tienda, no
        son una propiedad de la velocidad. Cuando el comportamiento le
        pertenece a otro, un switch externo es mejor que meterlo al enum.

        Cuidado: un switch SENTENCIA sobre un enum (con case X: o con
        case X ->) no exige cubrir todos los casos; solo el switch
        EXPRESIÓN (el que devuelve un valor) lo exige. Prefiere el switch
        expresión.
    */

    /*
        =================================================================
        CAPA 6) ENUMS QUE IMPLEMENTAN INTERFACES
        =================================================================

        Un enum NO puede extender otra clase (ya extiende de Enum), pero
        SÍ puede implementar interfaces. ShippingSpeed implementa
        ShippingCostRule.

        Esto tiene una ventaja de diseño importante: el código que usa
        la regla de costo depende de la INTERFACE, no del enum. Así, el
        conjunto fijo de constantes cubre los casos comunes, pero se
        pueden agregar reglas especiales (una promoción) como clases
        normales, sin tocar el enum. (Effective Java lo llama "enum
        extensible mediante interfaces".)
    */
    static final class FreeShippingAbove implements ShippingCostRule {
        private final double maxFreeWeightKg;

        FreeShippingAbove(double maxFreeWeightKg) {
            this.maxFreeWeightKg = maxFreeWeightKg;
        }

        @Override
        public BigDecimal cost(double weightKg) {
            return weightKg <= maxFreeWeightKg ? BigDecimal.ZERO : ShippingSpeed.STANDARD.cost(weightKg);
        }
    }

    static BigDecimal cheapest(List<ShippingCostRule> options, double weightKg) {
        BigDecimal best = null;
        for (ShippingCostRule option : options) {
            BigDecimal cost = option.cost(weightKg);
            if (best == null || cost.compareTo(best) < 0) {
                best = cost;
            }
        }
        return best;
    }

    static boolean interfaceExample() {
        // Constantes del enum y una clase normal, mezcladas bajo la misma interface.
        List<ShippingCostRule> options = List.of(ShippingSpeed.STANDARD, ShippingSpeed.EXPRESS, new FreeShippingAbove(5));
        return cheapest(options, 3).signum() == 0
                && cheapest(options, 8).compareTo(new BigDecimal("130")) == 0;   // 50 + 8 x 10
    }

    /*
        =================================================================
        CAPA 7) ENUMSET Y ENUMMAP
        =================================================================

        Java trae dos colecciones hechas especialmente para enums. Son las
        que debes usar siempre que las claves o elementos sean de un enum:

        ENUMSET: un conjunto de constantes.
        Por dentro es un solo número donde cada bit representa una
        constante (bit 0 = PENDING, bit 1 = PAID...). Es muchísimo más
        rápido y ligero que un HashSet.
            EnumSet.of(PAID, SHIPPED)            las indicadas
            EnumSet.range(PENDING, SHIPPED)      de una a otra, en orden
            EnumSet.allOf(OrderStatus.class)     todas
            EnumSet.noneOf(OrderStatus.class)    vacío
            EnumSet.complementOf(otroSet)        todas las que NO están

        ENUMMAP: un mapa cuyas claves son constantes.
        Por dentro es un arreglo indexado por la posición de cada
        constante. Más rápido que HashMap, y siempre se recorre en el
        orden en que se declararon las constantes.
    */
    static Map<OrderStatus, Integer> countByStatus(List<OrderStatus> statuses) {
        Map<OrderStatus, Integer> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : statuses) {
            counts.put(status, counts.getOrDefault(status, 0) + 1);
        }
        return counts;
    }

    static boolean enumCollectionsExample() {
        Set<OrderStatus> active = EnumSet.range(OrderStatus.PENDING, OrderStatus.SHIPPED);
        Set<OrderStatus> closed = EnumSet.complementOf(EnumSet.copyOf(active));

        Map<OrderStatus, Integer> counts = countByStatus(List.of(
                OrderStatus.SHIPPED, OrderStatus.PENDING, OrderStatus.SHIPPED, OrderStatus.DELIVERED));

        // EnumMap se recorre en orden de declaración, no en el orden en que se insertó.
        List<OrderStatus> keysInOrder = new ArrayList<>(counts.keySet());

        return active.equals(EnumSet.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.SHIPPED))
                && closed.equals(EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED))
                && counts.get(OrderStatus.SHIPPED) == 2
                && keysInOrder.equals(List.of(OrderStatus.PENDING, OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    /*
        =================================================================
        CAPA 8) BUSCAR CONSTANTES: VALUEOF Y BÚSQUEDAS PROPIAS
        =================================================================

        Convertir texto en una constante es muy común (leer de un
        archivo, de una petición web, de una base de datos).

        valueOf(String):
        - Busca por el NOMBRE EXACTO de la constante ("PAID").
        - Distingue mayúsculas: valueOf("paid") falla.
        - Si no existe, lanza IllegalArgumentException.
        - Si recibe null, lanza NullPointerException.

        Para buscar por OTRO dato (la etiqueta "Pagado"), se arma un mapa
        una sola vez en un bloque static, después de las constantes, como
        OrderStatus.fromLabel en la CAPA 3. Recorrer values() en cada
        búsqueda también funciona, pero values() crea un arreglo NUEVO en
        cada llamada.
    */
    static boolean lookupExample() {
        boolean byName = OrderStatus.valueOf("PAID") == OrderStatus.PAID;
        boolean byLabel = OrderStatus.fromLabel("Entregado") == OrderStatus.DELIVERED;

        boolean caseSensitive;
        try {
            OrderStatus.valueOf("paid");
            caseSensitive = false;
        } catch (IllegalArgumentException e) {
            caseSensitive = true;
        }

        return byName && byLabel && caseSensitive;
    }

    /*
        =================================================================
        CAPA 9) ORDINAL(): POR QUÉ NO DEBES DEPENDER DE ÉL
        =================================================================

        ordinal() devuelve la posición de la constante en la declaración:
        PENDING = 0, PAID = 1, SHIPPED = 2...

        Es tentador guardarlo en una base de datos o usarlo como índice
        de un arreglo. NO LO HAGAS. Si alguien agrega una constante en
        medio (por ejemplo, PACKED entre PAID y SHIPPED), todos los
        ordinales posteriores se recorren:
            antes: SHIPPED = 2      después: SHIPPED = 3
        y todos los pedidos guardados con "2" ahora dirían PACKED. Un
        error silencioso que corrompe datos.

        Qué hacer en su lugar:
        - Para guardar: el name() o un código propio en un campo.
        - Para indexar datos por constante: EnumMap (CAPA 7).
        - Para orden: compareTo, que sí es su propósito.

        ordinal() existe para uso interno de EnumSet y EnumMap.
    */
    enum PaymentMethod {
        CARD("TC"),
        CASH("EF"),
        TRANSFER("TR");

        private final String code;   // código ESTABLE para guardar: no cambia si reordenas

        PaymentMethod(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    static boolean ordinalExample() {
        return PaymentMethod.TRANSFER.ordinal() == 2       // depende del orden: frágil
                && PaymentMethod.TRANSFER.code().equals("TR"); // no depende del orden: estable
    }

    /*
        =================================================================
        CAPA 10) SINGLETON CON ENUM
        =================================================================

        Un SINGLETON es una clase de la que solo debe existir UN objeto.
        La forma más segura de hacerlo en Java es un enum con una sola
        constante (Effective Java):

            enum Algo { INSTANCE; ...métodos... }

        Por qué es mejor que una clase con constructor private:
        - La JVM garantiza una sola instancia, incluso con varios hilos.
        - No se puede romper con reflection ni al deserializar (con una
          clase normal, ambas cosas pueden crear una segunda instancia).
        - Es una línea.
    */
    enum InvoiceFormat {
        INSTANCE;

        private static final String PREFIX = "FAC-";

        String format(int number) {
            return PREFIX + String.format("%06d", number);
        }
    }

    static boolean singletonExample() {
        return InvoiceFormat.INSTANCE.format(42).equals("FAC-000042")
                && InvoiceFormat.valueOf("INSTANCE") == InvoiceFormat.INSTANCE;
    }

    /*
        Advertencia: que el singleton con enum sea la MEJOR forma de
        hacer un singleton no significa que debas hacer singletons. Un
        singleton con estado mutable es una variable global, y llamarlo
        desde cualquier lado es una dependencia oculta (Static, CAPA 10).
        Úsalo para cosas SIN estado mutable, como este formateador, y
        prefiere inyectar dependencias (v11_patterns/part1_creational/Singleton).
    */

    /*
        =================================================================
        CAPA 11) CUÁNDO USAR UN ENUM (Y CUÁNDO NO)
        =================================================================

        Usa un enum cuando:
        - el conjunto de valores es FIJO y CONOCIDO al compilar
          (estados, tipos, días, roles, monedas que soporta el sistema);
        - quieres que el compilador rechace valores inválidos;
        - cada valor tiene datos o comportamiento asociado.

        No uses un enum cuando:
        - los valores cambian sin recompilar (categorías de productos que
          administra el usuario, países de una tabla): eso son DATOS, van
          en una base de datos o configuración;
        - cada "caso" necesita datos DISTINTOS entre sí (un pago con
          tarjeta tiene número; uno en efectivo tiene cambio). Ahí lo
          adecuado es una jerarquía sealed de records
          (SealedAndPatternMatching): un enum da los mismos campos a
          todas sus constantes.

        Comparación rápida:
        | Necesitas                                      | Usa                    |
        |------------------------------------------------|------------------------|
        | Un valor de un conjunto fijo                   | enum                   |
        | Varios valores de un conjunto fijo a la vez    | EnumSet                |
        | Datos asociados a cada valor de un conjunto fijo| EnumMap               |
        | Variantes con datos distintos cada una         | sealed + records       |
        | Valores que administra el usuario              | datos (base de datos)  |
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Seguir usando constantes int o String para valores fijos
           (CAPA 1).

        2) Comparar constantes con equals por costumbre. == es lo correcto
           y nunca lanza NullPointerException (CAPA 2).

        3) Campos no final en un enum: estado global mutable (CAPA 3).

        4) Usar campos static dentro del constructor del enum (CAPA 3).

        5) Poner default en un switch expresión sobre un enum: pierdes el
           aviso del compilador cuando se agrega una constante (CAPA 5).

        6) Usar HashSet/HashMap con enums en lugar de EnumSet/EnumMap
           (CAPA 7).

        7) Usar valueOf con texto de usuario sin manejar la excepción, o
           esperando que ignore mayúsculas (CAPA 8).

        8) Guardar ordinal() en archivos o bases de datos, o usarlo como
           índice (CAPA 9).

        9) Meter al enum lógica que no le pertenece (CAPA 5).

        10) Usar un enum para datos que el usuario administra, o para
            variantes que necesitan datos distintos (CAPA 11).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Un enum es un tipo con un conjunto FIJO de instancias: las
          constantes. Es una clase que hereda de java.lang.Enum.
        - Se compara con ==. Trae values(), valueOf(), name(), ordinal(),
          compareTo().
        - Puede tener campos (final), constructor (siempre private) y
          métodos. Ideal para máquinas de estados.
        - Cada constante puede tener su propio cuerpo de un método
          abstracto.
        - switch expresión sobre un enum: sin default, y el compilador
          revisa que estén todas.
        - Puede implementar interfaces (no extender clases).
        - EnumSet y EnumMap: las colecciones correctas para enums.
        - valueOf busca por nombre exacto; para otros datos, un mapa
          estático.
        - Nunca dependas de ordinal() para guardar o indexar.
        - Singleton con enum: la forma más segura, para cosas sin estado.
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
