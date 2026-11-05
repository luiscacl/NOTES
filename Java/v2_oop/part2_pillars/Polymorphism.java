package v2_oop.part2_pillars;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/*
    =====================================================================
    POLIMORFISMO EN JAVA — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 6 de 14  (part2_pillars)
    Anterior:  part2_pillars/Inheritance
    Siguiente: part2_pillars/Abstraction

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 0)  Vocabulario mínimo
        CAPA 1)  Qué es el polimorfismo
        CAPA 2)  El problema: decidir con if/switch según el tipo
        CAPA 3)  La solución: un contrato, muchas formas
        CAPA 4)  La referencia no es el objeto: tipo estático y dinámico
        CAPA 5)  Upcasting: dónde ocurre de verdad
        CAPA 6)  Cómo decide Java qué método ejecutar
        CAPA 7)  Downcasting e instanceof
        CAPA 8)  Polimorfismo en diseño profesional
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Polymorphism {

    public static void main(String[] args) {
        check(same(withoutPolymorphismExample(), "90"), "CAPA 2");
        check(same(polymorphicCollectionExample(), "800"), "CAPA 3");
        check(referenceVsObjectExample(), "CAPA 4");
        check(upcastingExample(), "CAPA 5");
        check(dispatchExample(), "CAPA 6");
        check(downcastingExample(), "CAPA 7");
        check(professionalDesignExample(), "CAPA 8");
        System.out.println("Polymorphism: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 0) VOCABULARIO MÍNIMO
        =================================================================

        - REFERENCIA: lo que guarda una variable de objeto. No es el
          objeto: es la "dirección" donde vive el objeto en memoria.

        - TIPO ESTÁTICO (o declarado): el tipo que escribiste a la
          IZQUIERDA al declarar la variable. Lo conoce el compilador.

        - TIPO DINÁMICO (o real): la clase del objeto que se creó con
          new, a la DERECHA. Solo se conoce con certeza al ejecutar.

              Discount discount = new PercentageDiscount(10);
              ^^^^^^^^                ^^^^^^^^^^^^^^^^^^
              tipo estático           tipo dinámico

        - UPCASTING: ver un objeto con un tipo MÁS GENERAL que el suyo.
        - DOWNCASTING: volver a verlo con un tipo MÁS ESPECÍFICO.
    */

    /*
        =================================================================
        CAPA 1) QUÉ ES EL POLIMORFISMO
        =================================================================

        "Poli" = muchas, "morfo" = formas.
        Polimorfismo es que UNA MISMA instrucción produzca resultados
        distintos según el objeto que la recibe, sin que quien da la
        instrucción tenga que saber qué objeto es.

        Ejemplo de la vida real: el botón de "play".
        Lo presionas igual para una canción, un video o un podcast.
        Tú das UNA instrucción; cada cosa sabe cómo reproducirse.
        El botón no tiene un "si es canción haz esto, si es video haz
        aquello": cada objeto aporta su propio comportamiento.

        En Java hay tres formas de polimorfismo:

        1) DE SUBTIPOS (el importante, y el tema de esta guía):
           una variable de un tipo general (interface o clase padre)
           apunta a objetos de distintas clases, y cada uno responde con
           su propia versión del método. Se decide al EJECUTAR.

        2) DE SOBRECARGA: varios métodos con el mismo nombre y distintos
           parámetros. Se decide al COMPILAR (CAPA 6 explica por qué esto
           importa).

        3) PARAMÉTRICO (genéricos): List<String>, List<Integer>... el
           mismo código sirve para muchos tipos. Tiene su propia guía en
           el roadmap.

        Cuando alguien dice "polimorfismo" sin más, casi siempre se
        refiere al primero.
    */

    /*
        =================================================================
        CAPA 2) EL PROBLEMA: DECIDIR CON IF/SWITCH SEGÚN EL TIPO
        =================================================================

        Una tienda en línea aplica descuentos. Sin polimorfismo, el
        código pregunta "¿de qué tipo es?" y decide:
    */
    static BigDecimal applyDiscountWithoutPolymorphism(String type, BigDecimal subtotal, BigDecimal value) {
        return switch (type) {
            case "PERCENT" -> subtotal.subtract(subtotal.multiply(value).movePointLeft(2));
            case "FIXED" -> subtotal.subtract(value).max(BigDecimal.ZERO);
            case "NONE" -> subtotal;
            default -> throw new IllegalArgumentException("Tipo de descuento desconocido: " + type);
        };
    }

    static BigDecimal withoutPolymorphismExample() {
        return applyDiscountWithoutPolymorphism("PERCENT", new BigDecimal("100"), new BigDecimal("10"));
    }

    /*
        Problemas:

        1) Este switch no vive solo. Pronto habrá otro para mostrar la
           etiqueta del descuento, otro para validarlo, otro para el
           reporte... Cada tipo nuevo obliga a encontrar y modificar
           TODOS esos switch. Olvidar uno es un error silencioso.

        2) "value" significa cosas distintas según el tipo: un porcentaje
           o una cantidad. Nada impide pasar 5000 como porcentaje.

        3) Los tipos son textos. "PERCNT" compila y truena al ejecutar.

        Cada switch que pregunta "¿qué tipo eres?" es una decisión que el
        OBJETO debería tomar por sí mismo. La refactorización se llama
        literalmente "Reemplazar condicional con polimorfismo".
    */

    /*
        =================================================================
        CAPA 3) LA SOLUCIÓN: UN CONTRATO, MUCHAS FORMAS
        =================================================================

        1) Se define el contrato común: todo descuento sabe aplicarse a
           un subtotal y sabe describirse.
    */
    interface Discount {
        BigDecimal apply(BigDecimal subtotal);

        String label();
    }

    /*
        2) Cada forma de descuento es una clase. Cada una tiene SUS datos
           (con su significado claro) y SU lógica. El switch desapareció:
           cada rama ahora es una clase.
    */
    static final class PercentageDiscount implements Discount {
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

        @Override
        public String label() {
            return percent + "% de descuento";
        }

        // Propio de esta clase: NO está en el contrato (se usa en las CAPAS 6 y 7).
        int getPercent() {
            return percent;
        }
    }

    static final class FixedAmountDiscount implements Discount {
        private final BigDecimal amount;

        FixedAmountDiscount(BigDecimal amount) {
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("El descuento debe ser mayor que cero");
            }
            this.amount = amount;
        }

        @Override
        public BigDecimal apply(BigDecimal subtotal) {
            return subtotal.subtract(amount).max(BigDecimal.ZERO); // nunca deja un total negativo
        }

        @Override
        public String label() {
            return "$" + amount + " de descuento";
        }
    }

    /*
        3) Una forma NUEVA, agregada sin tocar nada de lo anterior.
           Además ENVUELVE a otro descuento: "aplica X solo si la compra
           llega a un mínimo". Como recibe un Discount, sirve con
           CUALQUIER descuento, incluso con los que todavía no existen.
    */
    static final class MinimumPurchaseDiscount implements Discount {
        private final BigDecimal minimum;
        private final Discount discount;

        MinimumPurchaseDiscount(BigDecimal minimum, Discount discount) {
            this.minimum = Objects.requireNonNull(minimum, "minimum");
            this.discount = Objects.requireNonNull(discount, "discount");
        }

        @Override
        public BigDecimal apply(BigDecimal subtotal) {
            return subtotal.compareTo(minimum) >= 0 ? discount.apply(subtotal) : subtotal;
        }

        @Override
        public String label() {
            return discount.label() + " en compras desde $" + minimum;
        }
    }

    /*
        4) El código cliente. Recibe una lista de descuentos de clases
           DISTINTAS y los trata a todos igual. Una sola línea,
           discount.apply(subtotal), ejecuta una lógica distinta en cada
           vuelta del ciclo. Eso es el polimorfismo.
    */
    static Discount bestDiscount(List<Discount> candidates, BigDecimal subtotal) {
        Discount best = candidates.get(0);
        for (Discount candidate : candidates) {
            if (candidate.apply(subtotal).compareTo(best.apply(subtotal)) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    static BigDecimal polymorphicCollectionExample() {
        BigDecimal subtotal = new BigDecimal("1000");
        List<Discount> coupons = List.of(
                new PercentageDiscount(10),                                                  // 900
                new FixedAmountDiscount(new BigDecimal("150")),                              // 850
                new MinimumPurchaseDiscount(new BigDecimal("500"), new PercentageDiscount(20)) // 800
        );
        return bestDiscount(coupons, subtotal).apply(subtotal); // 800
    }

    /*
        bestDiscount no tiene ni un if sobre el TIPO de descuento. Si
        mañana se crea "2x1" o "descuento por cumpleaños", bestDiscount
        funciona con ellos sin modificarse.
    */

    /*
        =================================================================
        CAPA 4) LA REFERENCIA NO ES EL OBJETO: TIPO ESTÁTICO Y DINÁMICO
        =================================================================

        Esta es la idea que, una vez entendida, hace que todo el
        polimorfismo tenga sentido.

        Cuando escribes:

            Discount discount = new PercentageDiscount(10);

        pasan DOS cosas separadas:
        - A la DERECHA, new crea UN objeto PercentageDiscount en memoria.
          Ese objeto es PercentageDiscount desde que nace hasta que
          muere. Nada lo puede convertir en otra clase.
        - A la IZQUIERDA, se crea una variable que guarda una REFERENCIA
          a ese objeto, y que lo "ve" como Discount.

        El tipo de la izquierda es como unos LENTES: decide qué parte del
        objeto puedes ver y usar, pero no cambia al objeto. Varias
        variables con lentes distintos pueden apuntar al MISMO objeto.

        Tú eliges los lentes como parte del diseño. Son válidos todos los
        tipos que el objeto realmente ES: su clase, cualquier clase padre,
        cualquier interface que implemente, y Object.
    */
    static boolean referenceVsObjectExample() {
        PercentageDiscount specific = new PercentageDiscount(10);
        Discount general = specific;      // misma referencia, lentes más generales
        Object mostGeneral = specific;    // misma referencia, lentes lo más generales posible

        // == entre objetos compara REFERENCIAS: true = apuntan al MISMO objeto.
        boolean oneSingleObject = general == specific && mostGeneral == specific;

        // Aunque la variable sea Object, el objeto sigue siendo PercentageDiscount.
        boolean realClassUnchanged = mostGeneral.getClass() == PercentageDiscount.class;

        // Y se comporta como lo que es, sin importar los lentes.
        boolean behavesAsItself = same(general.apply(new BigDecimal("100")), "90");

        return oneSingleObject && realClassUnchanged && behavesAsItself;
    }

    /*
        Las dos preguntas que Java hace en dos momentos distintos:

        | Momento      | Mira                | Pregunta                          |
        |--------------|---------------------|-----------------------------------|
        | Al COMPILAR  | el tipo ESTÁTICO    | ¿Este método EXISTE en ese tipo?  |
        | Al EJECUTAR  | el tipo DINÁMICO    | ¿QUÉ versión del método corre?    |

            general.apply(...)        // compila: apply está en Discount
                                      // corre: el apply de PercentageDiscount
            general.getPercent()      // NO compila: Discount no tiene getPercent,
                                      // aunque el objeto real sí lo tenga
    */

    /*
        =================================================================
        CAPA 5) UPCASTING: DÓNDE OCURRE DE VERDAD
        =================================================================

        Upcasting es tratar un objeto con un tipo más general:
            PercentageDiscount -> Discount -> Object

        Es AUTOMÁTICO (no se escribe ningún cast) y SIEMPRE es seguro,
        porque todo PercentageDiscount ES un Discount, sin excepción.
        Lo único que se "pierde" es la vista de lo específico: con lentes
        de Discount no ves getPercent().

        En código real casi nunca se escribe "Discount d = new ...".
        El upcasting ocurre en tres lugares, y ahí está todo su valor:

        1) PARÁMETROS: bestDiscount(List<Discount> ...) acepta cualquier
           descuento. Ya lo viste en la CAPA 3.

        2) COLECCIONES: una List<Discount> guarda clases distintas juntas.

        3) VALORES DE RETORNO: un método promete devolver un Discount y
           decide por dentro CUÁL. Quien llama no sabe ni necesita saber
           qué clase recibió. Esto es lo más poderoso de los tres:
    */
    static Discount discountForCoupon(String couponCode) {
        return switch (couponCode) {
            case "BIENVENIDA" -> new PercentageDiscount(10);
            case "MENOS150" -> new FixedAmountDiscount(new BigDecimal("150"));
            case "VERANO" -> new MinimumPurchaseDiscount(new BigDecimal("500"), new PercentageDiscount(20));
            default -> new NoDiscount(); // CAPA 8
        };
    }

    /*
        "¡Pero eso es un switch por tipo, lo que la CAPA 2 dijo que
        estaba mal!" Diferencia clave: este switch decide QUÉ OBJETO
        CREAR, y existe en UN solo lugar. El de la CAPA 2 decidía QUÉ
        HACER y se repetía en todo el sistema. Centralizar la creación
        está bien; esparcir la decisión de comportamiento no.
        (A un método que decide qué objeto crear se le llama "fábrica";
        el patrón Factory tiene su guía en el roadmap.)
    */
    static boolean upcastingExample() {
        BigDecimal subtotal = new BigDecimal("1000");

        // Ninguna de estas líneas sabe qué clase concreta recibió.
        Discount welcome = discountForCoupon("BIENVENIDA");
        Discount summer = discountForCoupon("VERANO");
        Discount unknown = discountForCoupon("NO-EXISTE");

        return same(welcome.apply(subtotal), "900")
                && same(summer.apply(subtotal), "800")
                && same(unknown.apply(subtotal), "1000");
    }

    /*
        Regla profesional para elegir el tipo de la izquierda:
        usa el tipo MÁS GENERAL que todavía te dé lo que necesitas.

            List<String> names = new ArrayList<>();   // bien: solo necesitas "una lista"
            ArrayList<String> names = new ArrayList<>(); // innecesariamente específico

        En parámetros y retornos esto es todavía más importante: un
        parámetro List acepta cualquier lista; uno ArrayList obliga a
        todos a usar esa implementación. Esto se conoce como
        "programar contra interfaces, no contra implementaciones".

        Usa el tipo concreto SOLO cuando necesitas algo que solo él
        tiene, como getPercent().
    */

    /*
        =================================================================
        CAPA 6) CÓMO DECIDE JAVA QUÉ MÉTODO EJECUTAR
        =================================================================

        Una llamada como objeto.metodo(argumentos) se resuelve en DOS
        pasos, en dos momentos distintos:

        PASO 1, AL COMPILAR: se elige la FIRMA.
            El compilador mira el tipo ESTÁTICO del objeto y de los
            ARGUMENTOS, y escoge cuál de los métodos con ese nombre
            (si hay sobrecargas) se va a llamar. Esta decisión queda
            fija en el programa compilado.

        PASO 2, AL EJECUTAR: se elige la IMPLEMENTACIÓN.
            La JVM mira la clase REAL del objeto que recibe la llamada y
            ejecuta la versión de esa clase para la firma elegida en el
            paso 1. Esto es el "despacho dinámico". (Internamente, cada
            clase tiene una tabla de métodos, y la JVM busca ahí la
            versión que corresponde a la clase real.)

        Consecuencia que confunde incluso a gente con experiencia:

            - El OBJETO que recibe la llamada se evalúa al EJECUTAR
              (sobrescritura = polimórfica).
            - La SOBRECARGA se elige al COMPILAR, según el tipo estático de
              los argumentos (sobrecarga = NO polimórfica).

        Java solo hace despacho dinámico sobre UNA cosa: el objeto a la
        izquierda del punto. Por eso se dice que es de "despacho simple".

        Míralo fallar:
    */
    static final class DiscountDescriber {
        // Dos SOBRECARGAS: mismo nombre, distinto tipo de parámetro.
        String describe(Discount discount) {
            return "descuento genérico";
        }

        String describe(PercentageDiscount discount) {
            return "descuento de " + discount.getPercent() + "%";
        }
    }

    static boolean dispatchExample() {
        DiscountDescriber describer = new DiscountDescriber();
        Discount discount = new PercentageDiscount(15); // tipo estático Discount, real PercentageDiscount

        // Sobrecarga: el compilador ve "Discount" y elige describe(Discount).
        // No importa que el objeto real sea PercentageDiscount.
        boolean overloadUsesStaticType = describer.describe(discount).equals("descuento genérico");

        // Sobrescritura: label() se elige según el objeto real.
        boolean overrideUsesRealObject = discount.label().equals("15% de descuento");

        return overloadUsesStaticType && overrideUsesRealObject;
    }

    /*
        La lección de diseño: si el comportamiento depende del TIPO de un
        objeto, ponlo COMO MÉTODO DEL OBJETO (label() dentro de cada
        descuento), no en sobrecargas de una clase externa. El objeto
        siempre sabe lo que es; el compilador no.

        Lo que NUNCA es polimórfico (detalle en Inheritance.java, CAPA 6):
        - métodos static      -> los decide la clase escrita
        - campos              -> los decide el tipo de la variable
        - métodos private     -> no se heredan, no se sobrescriben
        - constructores       -> no se heredan
    */

    /*
        =================================================================
        CAPA 7) DOWNCASTING E INSTANCEOF
        =================================================================

        Downcasting es el camino de regreso: tienes un objeto visto como
        Discount y necesitas verlo como PercentageDiscount para usar
        getPercent().

            PercentageDiscount p = (PercentageDiscount) discount;
                                   ^^^^^^^^^^^^^^^^^^^^
                                   cast: "(TipoDeseado) expresión"

        Lo que hace y lo que NO hace el cast:
        - NO crea un objeto nuevo, NO convierte nada, NO cambia el objeto.
        - Solo te da otra referencia, con lentes más específicos, al
          MISMO objeto.
        - Le dice al compilador: "confía en mí, el objeto real es de
          este tipo". Y la JVM lo VERIFICA al ejecutar.

        Por eso el downcasting, a diferencia del upcasting, PUEDE FALLAR.
        Si el objeto real no es de ese tipo, la JVM lanza
        ClassCastException:
    */
    static boolean castFailsWithWrongType() {
        Discount discount = new FixedAmountDiscount(new BigDecimal("150"));
        try {
            PercentageDiscount percentage = (PercentageDiscount) discount; // compila, pero el objeto es Fixed
            return percentage == null; // nunca llega aquí
        } catch (ClassCastException e) {
            return true;
        }
    }

    /*
        Hay casts que el compilador ya sabe que son imposibles, y esos ni
        siquiera compilan:

            String text = (String) discount;   // ERROR de compilación

        String es final y no implementa Discount: NINGÚN objeto puede ser
        las dos cosas a la vez. El compilador solo deja pasar los casts
        que PODRÍAN funcionar, y la JVM revisa los demás al ejecutar.

        La forma segura: preguntar antes con instanceof.

        FORMA CLÁSICA (antes de Java 16): preguntar y luego castear.
    */
    static int percentOrZeroClassic(Discount discount) {
        if (discount instanceof PercentageDiscount) {
            PercentageDiscount percentage = (PercentageDiscount) discount;
            return percentage.getPercent();
        }
        return 0;
    }

    /*
        FORMA MODERNA (Java 16+, "pattern matching for instanceof"):
        pregunta y, si es cierto, deja la variable ya casteada. Es la
        que debes usar hoy: no repites el tipo y no existe la
        posibilidad de castear al tipo equivocado.
    */
    static int percentOrZero(Discount discount) {
        if (discount instanceof PercentageDiscount percentage) {
            return percentage.getPercent();
        }
        return 0;
    }

    static boolean downcastingExample() {
        Discount percentage = new PercentageDiscount(25);
        Discount fixed = new FixedAmountDiscount(new BigDecimal("100"));

        return castFailsWithWrongType()
                && percentOrZeroClassic(percentage) == 25
                && percentOrZero(percentage) == 25
                && percentOrZero(fixed) == 0;
        // Nota: null instanceof Cualquiera es siempre false, así que
        // instanceof también te protege de null.
    }

    /*
        Cuándo el downcasting es una señal de alarma:
        si escribes una cadena de "if instanceof A ... else if instanceof
        B ... else if instanceof C", acabas de reinventar el switch de la
        CAPA 2. Ese comportamiento debería ser un método del contrato.

        Cuándo es aceptable:
        - Preguntar por una CAPACIDAD OPCIONAL que no todos tienen
          (como Refundable en Abstraction.java, CAPA 8).
        - En equals(Object), donde el contrato de Object te obliga a
          recibir Object.
        - Con jerarquías sealed y switch con patrones (Java 21+), donde
          el compilador verifica que cubriste todos los casos. Ese tema
          tiene su propia guía en el roadmap.
    */

    /*
        =================================================================
        CAPA 8) POLIMORFISMO EN DISEÑO PROFESIONAL
        =================================================================

        Casi todos los patrones de diseño son formas de aprovechar el
        polimorfismo. Los que ya aparecieron en esta guía:

        1) STRATEGY: un algoritmo intercambiable detrás de una interface.
           Cada Discount es una estrategia de descuento.

        2) DECORATOR: un objeto que envuelve a otro del MISMO tipo y le
           agrega algo. MinimumPurchaseDiscount envuelve un Discount.

        3) NULL OBJECT: en lugar de usar null para "no hay", se usa un
           objeto que cumple el contrato sin hacer nada. Así el código
           cliente nunca tiene que escribir "if (discount != null)".
    */
    static final class NoDiscount implements Discount {
        @Override
        public BigDecimal apply(BigDecimal subtotal) {
            return subtotal;
        }

        @Override
        public String label() {
            return "sin descuento";
        }
    }

    /*
        4) INYECCIÓN DE DEPENDENCIAS: la clase que USA el comportamiento
           lo recibe desde fuera, por el constructor, como interface.
           Checkout no sabe qué descuento tiene; quien lo crea decide.
           Por eso se puede probar con cualquier descuento, o con
           NoDiscount, sin cambiar Checkout.
    */
    static final class Checkout {
        private final Discount discount;

        Checkout(Discount discount) {
            this.discount = Objects.requireNonNull(discount, "discount"); // gracias a NoDiscount, null no tiene excusa
        }

        BigDecimal total(BigDecimal subtotal) {
            return discount.apply(subtotal).setScale(2, RoundingMode.HALF_UP);
        }

        String summary(BigDecimal subtotal) {
            return "Total: $" + total(subtotal) + " (" + discount.label() + ")";
        }
    }

    static boolean professionalDesignExample() {
        BigDecimal subtotal = new BigDecimal("300");

        Checkout regular = new Checkout(new NoDiscount());
        Checkout summerSale = new Checkout(discountForCoupon("VERANO")); // 300 < 500: no aplica

        return regular.summary(subtotal).equals("Total: $300.00 (sin descuento)")
                && summerSale.summary(subtotal).equals("Total: $300.00 (20% de descuento en compras desde $500)");
    }

    /*
        5) PRINCIPIO ABIERTO/CERRADO (la "O" de SOLID): agregar un tipo
           nuevo de descuento no modifica Checkout, bestDiscount ni
           DiscountDescriber. El polimorfismo es el mecanismo que lo hace
           posible.

        La señal de que estás aprovechando bien el polimorfismo:
        las clases que USAN tus objetos casi no tienen if/switch sobre
        tipos, y agregar una variante nueva es agregar UNA clase.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) "La referencia es el objeto."
           No. La variable guarda una referencia; varias variables pueden
           apuntar al mismo objeto (CAPA 4).

        2) "El tipo de la izquierda cambia al objeto."
           No. Solo cambia qué puedes ver. El objeto nunca cambia de
           clase (CAPA 4).

        3) "El cast convierte el objeto."
           No. Solo cambia los lentes, y la JVM verifica que sean
           correctos (CAPA 7).

        4) "Upcasting y downcasting son igual de seguros."
           Upcasting: automático y siempre seguro.
           Downcasting: explícito y puede lanzar ClassCastException.

        5) "La sobrecarga también es polimórfica."
           No. Las sobrecargas se eligen al compilar, con el tipo
           estático de los argumentos (CAPA 6).

        6) Cadenas de instanceof para decidir el comportamiento.
           Es el switch por tipo disfrazado. El comportamiento debe ser
           un método del contrato (CAPAS 2 y 7).

        7) Declarar variables y parámetros con el tipo concreto sin
           necesidad. ArrayList en vez de List amarra el código a una
           implementación (CAPA 5).

        8) Usar null para "no hay comportamiento".
           Obliga a revisar null en todos lados. Usa un Null Object
           (CAPA 8).

        9) Confundir polimorfismo con ocultamiento de campos.
           Los campos nunca son polimórficos (CAPA 6 e Inheritance.java).

        10) Pensar que el polimorfismo solo sirve dentro de la jerarquía.
            Su valor real está en las clases que CONSUMEN la abstracción:
            Checkout, bestDiscount... que funcionan con tipos que aún no
            existen.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Polimorfismo = una misma llamada, distinto comportamiento según
          el objeto real, sin que quien llama sepa cuál es.
        - Tipo estático (izquierda): qué puedes llamar. Lo revisa el
          compilador.
        - Tipo dinámico (derecha): qué código corre. Lo decide la JVM.
        - Upcasting: automático, seguro; ocurre en parámetros,
          colecciones y retornos.
        - Downcasting: explícito, puede fallar; usa instanceof con
          pattern matching, y úsalo poco.
        - Sobrescritura es polimórfica; sobrecarga, static y campos no.
        - Reemplaza los switch por tipo con métodos en el objeto.
        - Strategy, Decorator, Null Object e inyección de dependencias son
          polimorfismo aplicado.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    // Compara cantidades sin importar decimales: 800 y 800.00 son lo mismo.
    private static boolean same(BigDecimal actual, String expected) {
        return actual.compareTo(new BigDecimal(expected)) == 0;
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
