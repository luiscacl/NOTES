package v2_oop.part4_advanced_types;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
    =====================================================================
    RECORDS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 12 de 14  (part4_advanced_types)
    Anterior:  part4_advanced_types/Enums
    Siguiente: part4_advanced_types/ObjectContract

    Requisito: ClassesAndObjects, Encapsulation (inmutabilidad,
    copias defensivas) y Interfaces.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  El problema: clases de datos llenas de código repetitivo
        CAPA 2)  El record: sintaxis y lo que Java genera
        CAPA 3)  Constructor compacto: validar y normalizar
        CAPA 4)  Otros constructores, métodos y miembros static
        CAPA 5)  Lo que un record NO puede hacer
        CAPA 6)  Inmutabilidad superficial: el hueco de las colecciones
        CAPA 7)  Records con arreglos: la trampa
        CAPA 8)  "Modificar" un record: métodos with
        CAPA 9)  equals y hashCode: records como claves de mapas
        CAPA 10) Records con interfaces, genéricos y como valor de retorno
        CAPA 11) Cuándo usar un record y cuándo una clase
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class Records {

    public static void main(String[] args) {
        check(boilerplateExample(), "CAPA 1");
        check(generatedMembersExample(), "CAPA 2");
        check(compactConstructorExample(), "CAPA 3");
        check(extraMembersExample(), "CAPA 4");
        check(shallowImmutabilityExample(), "CAPA 6");
        check(arrayTrapExample(), "CAPA 7");
        check(witherExample(), "CAPA 8");
        check(mapKeyExample(), "CAPA 9");
        check(interfacesAndGenericsExample(), "CAPA 10");
        System.out.println("Records: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: CLASES DE DATOS LLENAS DE CÓDIGO REPETITIVO
        =================================================================

        Muchas clases existen solo para CARGAR DATOS: una cantidad de
        dinero, un rango de fechas, una coordenada, la respuesta de una
        API. Para que una de esas clases esté BIEN hecha (inmutable y
        comparable por su contenido) necesitas escribir a mano:
        - campos private final
        - un constructor que los asigne
        - un getter por campo
        - equals, para que dos objetos con los mismos datos sean iguales
        - hashCode, coherente con equals (para HashMap y HashSet)
        - toString, para depurar

        Así se ve una cantidad de dinero hecha a mano. Fíjate cuánto
        código hay, y que NADA de él es lógica de negocio:
    */
    static final class ManualMoney {
        private final BigDecimal amount;
        private final String currency;

        ManualMoney(BigDecimal amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }

        BigDecimal getAmount() {
            return amount;
        }

        String getCurrency() {
            return currency;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ManualMoney that)) {
                return false;
            }
            return amount.equals(that.amount) && currency.equals(that.currency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(amount, currency);
        }

        @Override
        public String toString() {
            return "ManualMoney[amount=" + amount + ", currency=" + currency + "]";
        }
    }

    /*
        Y cada vez que agregues un campo, tienes que acordarte de
        actualizar el constructor, equals, hashCode y toString. Olvidar
        uno en equals produce errores muy difíciles de encontrar.

        Lo mismo, como record (sin validaciones todavía; esas llegan en
        la CAPA 3):
    */
    record SimpleMoney(BigDecimal amount, String currency) { }

    static boolean boilerplateExample() {
        ManualMoney manualA = new ManualMoney(new BigDecimal("10"), "MXN");
        ManualMoney manualB = new ManualMoney(new BigDecimal("10"), "MXN");
        SimpleMoney recordA = new SimpleMoney(new BigDecimal("10"), "MXN");
        SimpleMoney recordB = new SimpleMoney(new BigDecimal("10"), "MXN");

        // Mismo comportamiento: iguales por contenido, mismo hashCode, toString útil.
        return manualA.equals(manualB) && manualA.hashCode() == manualB.hashCode()
                && recordA.equals(recordB) && recordA.hashCode() == recordB.hashCode()
                && recordA.toString().equals("SimpleMoney[amount=10, currency=MXN]");
    }

    /*
        =================================================================
        CAPA 2) EL RECORD: SINTAXIS Y LO QUE JAVA GENERA
        =================================================================

        Un RECORD (Java 16+) es una clase pensada para ser un portador
        de datos INMUTABLE. Se declara con sus COMPONENTES entre
        paréntesis:

            record SimpleMoney(BigDecimal amount, String currency) { }
                               ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                               los componentes: el "estado" del record

        Con esa línea, Java genera:
        1) Un campo private final por componente.
        2) El CONSTRUCTOR CANÓNICO: recibe todos los componentes en orden
           y los asigna.
        3) Un método de acceso por componente, CON EL MISMO NOMBRE (sin
           "get"): money.amount(), money.currency().
        4) equals: dos records son iguales si son del mismo tipo y TODOS
           sus componentes son iguales.
        5) hashCode coherente con ese equals.
        6) toString con el formato NombreRecord[comp1=valor, comp2=valor].

        Y además:
        - La clase es implícitamente FINAL: nadie puede heredar de ella.
        - Hereda de java.lang.Record (y por eso no puede extender otra).
        - Un record anidado es implícitamente static.

        La idea de fondo: en un record, el ESTADO es la API. Lo que
        declaras en los componentes es exactamente lo que el record es,
        guarda y expone. Nada oculto.
    */
    static boolean generatedMembersExample() {
        SimpleMoney price = new SimpleMoney(new BigDecimal("250"), "MXN");

        boolean accessors = price.amount().compareTo(new BigDecimal("250")) == 0 && price.currency().equals("MXN");
        boolean isRecord = price instanceof Record;
        boolean notSameObjectButEqual = price != new SimpleMoney(new BigDecimal("250"), "MXN")
                && price.equals(new SimpleMoney(new BigDecimal("250"), "MXN"));

        return accessors && isRecord && notSameObjectButEqual;
    }

    /*
        =================================================================
        CAPA 3) CONSTRUCTOR COMPACTO: VALIDAR Y NORMALIZAR
        =================================================================

        Un record sin validar acepta cualquier cosa: SimpleMoney(null, "")
        es válido. Para proteger las invariantes (Encapsulation, CAPA 4)
        existe el CONSTRUCTOR COMPACTO:

            record Money(BigDecimal amount, String currency) {
                Money {            // sin paréntesis ni parámetros
                    ...validar y normalizar...
                }
            }

        Cómo funciona:
        - Recibe los parámetros del constructor canónico, con los mismos
          nombres que los componentes.
        - Puedes validarlos y REASIGNARLOS (amount = amount.setScale(2)).
        - NO asignas los campos: Java lo hace solo, al final, con el valor
          que tengan los parámetros en ese momento.
              this.amount = amount;   // ERROR dentro del compacto:
                                      // cannot assign a value to final variable amount

        Un detalle importante con BigDecimal: equals considera la escala,
        así que 10 y 10.00 NO son iguales para equals. Normalizar la
        escala en el constructor compacto hace que el equals generado
        funcione como se espera para dinero.
    */
    record Money(BigDecimal amount, String currency) {

        Money {
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(currency, "currency");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("El monto no puede ser negativo");
            }
            if (!currency.matches("[A-Za-z]{3}")) {
                throw new IllegalArgumentException("La moneda debe ser un código de 3 letras: " + currency);
            }
            amount = amount.setScale(2, RoundingMode.HALF_UP);   // normalizar
            currency = currency.toUpperCase();                    // normalizar
        }

        // CAPA 4: método de negocio. No modifica: devuelve un Money nuevo.
        Money plus(Money other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("No se pueden sumar " + currency + " y " + other.currency);
            }
            return new Money(amount.add(other.amount), currency);
        }

        // CAPA 4: fábrica estática.
        static Money of(String amount, String currency) {
            return new Money(new BigDecimal(amount), currency);
        }

        // CAPA 4: constante static (los campos static SÍ se permiten).
        static final Money ZERO_MXN = new Money(BigDecimal.ZERO, "MXN");
    }

    static boolean compactConstructorExample() {
        // Normalizado: "mxn" -> "MXN", 10 -> 10.00. Por eso son iguales.
        boolean normalized = Money.of("10", "mxn").equals(Money.of("10.00", "MXN"));

        boolean validated;
        try {
            Money.of("-5", "MXN");
            validated = false;
        } catch (IllegalArgumentException e) {
            validated = true;
        }

        return normalized && validated;
    }

    /*
        Si necesitas algo que el compacto no permite, también puedes
        escribir el constructor canónico COMPLETO (con parámetros y
        asignando this.campo = ...). Casi nunca hace falta: el compacto
        es la forma idiomática.
    */

    /*
        =================================================================
        CAPA 4) OTROS CONSTRUCTORES, MÉTODOS Y MIEMBROS STATIC
        =================================================================

        Un record puede tener casi todo lo que tiene una clase:
        - Métodos de instancia (plus en Money): un record NO es solo
          datos; la lógica que usa esos datos va DENTRO (evita el modelo
          anémico, Encapsulation, CAPA 5).
        - Métodos y campos static (of, ZERO_MXN).
        - Constructores adicionales, con una condición: deben delegar en
          otro constructor con this(...), para que la validación del
          compacto SIEMPRE se ejecute. (Desde Java 25 el this(...) puede ir
          precedido de código que no use el objeto, por ejemplo para validar
          los argumentos; v2_oop/part2_pillars/Inheritance, Regla 5.)
        - Implementar interfaces (CAPA 10).
        - Clases y records anidados.

        Ejemplo: un rango de fechas (como la estancia de hotel de
        Encapsulation, ahora como record), con un constructor adicional
        "a partir de un número de noches".
    */
    record DateRange(LocalDate start, LocalDate end) {

        DateRange {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("El final debe ser después del inicio");
            }
        }

        // Constructor adicional: delega en el canónico, así que también se valida.
        DateRange(LocalDate start, int nights) {
            this(start, start.plusDays(nights));
        }

        long nights() {
            return ChronoUnit.DAYS.between(start, end);
        }

        boolean overlaps(DateRange other) {
            return start.isBefore(other.end) && other.start.isBefore(end);
        }

        // CAPA 8: "modificar" devolviendo uno nuevo.
        DateRange withEnd(LocalDate newEnd) {
            return new DateRange(start, newEnd);
        }
    }

    static boolean extraMembersExample() {
        DateRange december = new DateRange(LocalDate.of(2026, 12, 20), 5);
        DateRange christmas = new DateRange(LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 26));

        boolean money = Money.of("100", "MXN").plus(Money.of("50.5", "MXN")).equals(Money.of("150.50", "MXN"))
                && Money.ZERO_MXN.amount().signum() == 0;

        return december.end().equals(LocalDate.of(2026, 12, 25))
                && december.nights() == 5
                && december.overlaps(christmas)
                && money;
    }

    /*
        =================================================================
        CAPA 5) LO QUE UN RECORD NO PUEDE HACER
        =================================================================

        Todas estas restricciones existen para GARANTIZAR que el estado
        del record sea exactamente sus componentes, y nada más. Las
        líneas están comentadas porque no compilan:

        1) No puede tener campos de instancia extra:
               record Money(BigDecimal amount, String currency) {
                   private int timesUsed;
                   // ERROR: field declaration must be static
               }
           Si pudiera, equals y toString ignorarían ese campo y el record
           tendría estado oculto.

        2) No puede extender otra clase (ya extiende de Record):
               record Money(...) extends Base { }   // ERROR

        3) Nadie puede heredar de un record (es final):
               class SpecialMoney extends Money { }   // ERROR

        4) Sus campos son final: no hay forma de cambiarlos después de
           construido.

        5) No puede declararse abstract.

        Si necesitas cualquiera de esas cosas, no quieres un record:
        quieres una clase normal (CAPA 11).
    */

    /*
        =================================================================
        CAPA 6) INMUTABILIDAD SUPERFICIAL: EL HUECO DE LAS COLECCIONES
        =================================================================

        Los campos de un record son final, pero final solo impide
        REASIGNAR el campo (Encapsulation, CAPA 7). Si un componente es
        una lista mutable, el record guarda la MISMA lista que le pasaron,
        y quien la tenga puede seguir modificándola por fuera.

        A eso se le llama inmutabilidad SUPERFICIAL: el record no puede
        cambiar a qué lista apunta, pero la lista sí puede cambiar.

        La solución es la misma copia defensiva de siempre, en el
        constructor compacto: List.copyOf crea una copia INMUTABLE.
    */
    record LeakyShoppingList(String owner, List<String> items) { }   // MAL

    record ShoppingList(String owner, List<String> items) {          // BIEN
        ShoppingList {
            Objects.requireNonNull(owner, "owner");
            items = List.copyOf(items);   // copia inmutable: ni el original ni quien lea items() la cambia
        }
    }

    static boolean shallowImmutabilityExample() {
        List<String> original = new ArrayList<>(List.of("Leche", "Pan"));

        LeakyShoppingList leaky = new LeakyShoppingList("Ana", original);
        ShoppingList safe = new ShoppingList("Ana", original);

        original.add("Intruso");

        boolean leakyChanged = leaky.items().size() == 3;   // cambió por fuera
        boolean safeUnchanged = safe.items().size() == 2;   // no cambió

        boolean safeIsReadOnly;
        try {
            safe.items().add("Otro");
            safeIsReadOnly = false;
        } catch (UnsupportedOperationException e) {
            safeIsReadOnly = true;
        }

        return leakyChanged && safeUnchanged && safeIsReadOnly;
    }

    /*
        Regla: si un componente es de un tipo mutable (List, Set, Map,
        arreglos, objetos con setters), cópialo en el constructor
        compacto. Si es inmutable (String, BigDecimal, LocalDate, otro
        record bien hecho), no hace falta.
    */

    /*
        =================================================================
        CAPA 7) RECORDS CON ARREGLOS: LA TRAMPA
        =================================================================

        Los arreglos tienen dos problemas dentro de un record:
        - El equals generado compara los componentes con equals, y el
          equals de un arreglo compara REFERENCIAS, no contenido. Dos
          records con arreglos de igual contenido NO son iguales.
        - El toString de un arreglo es algo como [I@1b6d3586.
        Además, un arreglo siempre es mutable.
    */
    record ScoresWithArray(int[] values) { }

    record Scores(List<Integer> values) {
        Scores {
            values = List.copyOf(values);
        }
    }

    static boolean arrayTrapExample() {
        ScoresWithArray arrayA = new ScoresWithArray(new int[] {9, 8, 10});
        ScoresWithArray arrayB = new ScoresWithArray(new int[] {9, 8, 10});
        Scores listA = new Scores(List.of(9, 8, 10));
        Scores listB = new Scores(List.of(9, 8, 10));

        return !arrayA.equals(arrayB)                     // mismo contenido, NO iguales
                && arrayA.toString().contains("[I@")      // toString inútil
                && listA.equals(listB)                    // con List: iguales
                && listA.toString().equals("Scores[values=[9, 8, 10]]");
    }

    /*
        Solución: usa List en lugar de arreglos en los componentes. Si de
        verdad necesitas un arreglo (por rendimiento), tendrás que
        sobrescribir equals, hashCode y toString con Arrays.equals,
        Arrays.hashCode y Arrays.toString, y copiar el arreglo al recibirlo
        y al devolverlo. En ese punto, el record ya no te ahorra trabajo.
    */

    /*
        =================================================================
        CAPA 8) "MODIFICAR" UN RECORD: MÉTODOS WITH
        =================================================================

        Un record no se puede modificar. Cuando necesitas "el mismo, pero
        con un dato distinto", se crea uno NUEVO. La convención es un
        método withComponente(nuevoValor), como DateRange.withEnd.

        Es el mismo estilo de String, BigDecimal y LocalDate: sus
        métodos (toUpperCase, add, plusDays) nunca cambian el objeto,
        devuelven otro.

        (Java está trabajando en una sintaxis para esto sin escribir el
        método a mano; mientras no sea parte estable del lenguaje, el
        método with es la forma estándar.)
    */
    static boolean witherExample() {
        DateRange original = new DateRange(LocalDate.of(2026, 12, 20), 3);
        DateRange extended = original.withEnd(LocalDate.of(2026, 12, 28));

        boolean withValidates;
        try {
            original.withEnd(LocalDate.of(2026, 12, 1));   // antes del inicio: el compacto lo rechaza
            withValidates = false;
        } catch (IllegalArgumentException e) {
            withValidates = true;
        }

        return original.nights() == 3 && extended.nights() == 8 && withValidates;
    }

    /*
        =================================================================
        CAPA 9) EQUALS Y HASHCODE: RECORDS COMO CLAVES DE MAPAS
        =================================================================

        Un HashMap encuentra una clave usando hashCode y equals. Si una
        clase no los sobrescribe, usa los de Object, que comparan
        IDENTIDAD: dos objetos con los mismos datos son claves distintas,
        y nunca encuentras lo que guardaste a menos que uses el MISMO
        objeto.

        Los records traen equals y hashCode por contenido, así que son
        perfectos como claves compuestas: "el stock del producto X en el
        almacén Y".
    */
    static final class BrokenStockKey {   // sin equals/hashCode
        private final String sku;
        private final String warehouse;

        BrokenStockKey(String sku, String warehouse) {
            this.sku = sku;
            this.warehouse = warehouse;
        }

        String sku() {
            return sku;
        }

        String warehouse() {
            return warehouse;
        }
    }

    record StockKey(String sku, String warehouse) { }

    static boolean mapKeyExample() {
        Map<BrokenStockKey, Integer> brokenStock = new HashMap<>();
        brokenStock.put(new BrokenStockKey("TEC-01", "GDL"), 15);

        Map<StockKey, Integer> stock = new HashMap<>();
        stock.put(new StockKey("TEC-01", "GDL"), 15);

        // Se busca con un objeto NUEVO con los mismos datos, como pasa en la vida real.
        boolean brokenLookupFails = brokenStock.get(new BrokenStockKey("TEC-01", "GDL")) == null;
        boolean recordLookupWorks = stock.get(new StockKey("TEC-01", "GDL")) == 15;

        return brokenLookupFails && recordLookupWorks;
    }

    /*
        Puedes sobrescribir equals, hashCode o toString en un record si
        lo necesitas (por ejemplo, para ocultar un dato sensible en
        toString), pero entonces eres responsable de respetar su
        contrato (ObjectContract). Y si sobrescribes un método de
        acceso, debe seguir siendo public y devolver el mismo tipo.
    */

    /*
        =================================================================
        CAPA 10) RECORDS CON INTERFACES, GENÉRICOS Y COMO VALOR DE RETORNO
        =================================================================

        1) INTERFACES: un record puede implementar interfaces. Así
           varios records pueden compartir un contrato. Esto es la base
           de las jerarquías sealed (SealedAndPatternMatching).
    */
    interface Shape {
        double area();
    }

    record Circle(double radius) implements Shape {
        @Override
        public double area() {
            return Math.PI * radius * radius;
        }
    }

    record Rectangle(double width, double height) implements Shape {
        @Override
        public double area() {
            return width * height;
        }
    }

    /*
        2) GENÉRICOS: un record puede tener parámetros de tipo
           (v4_generics/GenericsBasics).

        3) VARIOS VALORES DE RETORNO: un método Java devuelve UN solo
           valor. Cuando necesitas devolver dos o tres datos relacionados,
           un record pequeño es la forma limpia (antes se usaban arreglos
           u objetos Map, perdiendo nombres y tipos).
    */
    record Pair<A, B>(A first, B second) { }

    record MinMax(int min, int max) { }

    static MinMax minMax(List<Integer> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("La lista no puede estar vacía");
        }
        int min = values.get(0);
        int max = values.get(0);
        for (int value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return new MinMax(min, max);
    }

    static boolean interfacesAndGenericsExample() {
        List<Shape> shapes = List.of(new Circle(1), new Rectangle(2, 3));
        double totalArea = 0;
        for (Shape shape : shapes) {
            totalArea += shape.area();
        }

        Pair<String, Integer> pair = new Pair<>("Teclado", 3);
        MinMax range = minMax(List.of(7, 2, 9, 4));

        return Math.abs(totalArea - (Math.PI + 6)) < 1e-9
                && pair.first().equals("Teclado") && pair.second() == 3
                && range.min() == 2 && range.max() == 9;
    }

    /*
        Y desde Java 21, los records se pueden "desarmar" en patrones:
            if (shape instanceof Rectangle(double w, double h)) { ... w * h ... }
        Se explica a fondo en SealedAndPatternMatching.
    */

    /*
        =================================================================
        CAPA 11) CUÁNDO USAR UN RECORD Y CUÁNDO UNA CLASE
        =================================================================

        La pregunta clave: ¿dos objetos con los mismos datos son "el
        mismo"?

        - SÍ -> es un VALOR. Dos billetes de 100 pesos valen lo mismo; dos
          rangos 20-25 de diciembre son el mismo rango. Usa RECORD.
        - NO -> es una ENTIDAD con identidad propia. Dos clientes llamados
          "Ana" son personas distintas; un pedido cambia de estado pero
          sigue siendo el mismo pedido. Usa CLASE.

        | Usa RECORD para                              | Usa CLASE para                              |
        |----------------------------------------------|---------------------------------------------|
        | Valores: dinero, rangos, coordenadas         | Entidades con identidad (Cliente, Pedido)   |
        | DTOs: datos que entran o salen de una API    | Objetos con estado que cambia               |
        | Claves compuestas de mapas                   | Objetos que necesitan herencia              |
        | Devolver varios valores                      | Ocultar cómo se guardan los datos           |
        | Resultados intermedios (records locales)     | Entidades JPA (requieren clases mutables)   |
        | Variantes de una jerarquía sealed            |                                             |

        Sobre "ocultar cómo se guardan los datos": en un record, los
        componentes SON la API pública. Si mañana quieres guardar la
        temperatura en Kelvin en lugar de Celsius (Encapsulation, CAPA 9),
        cambiar el componente rompe a todos los que llamaban a celsius().
        Una clase te deja cambiar el interior sin tocar su cara pública.
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Escribir a mano clases de solo datos con equals/hashCode/
           toString cuando un record basta (CAPA 1).

        2) Llamar getAmount() en un record. Los métodos de acceso se
           llaman igual que el componente: amount() (CAPA 2).

        3) Records sin validación: aceptan null y valores absurdos. Usa el
           constructor compacto (CAPA 3).

        4) Intentar asignar this.campo dentro del constructor compacto
           (CAPA 3).

        5) Constructores adicionales que no delegan en el canónico, o
           esperar que exista uno sin parámetros (CAPA 4).

        6) Intentar agregar campos de instancia o heredar (CAPA 5).

        7) Componentes List/Set/Map sin copia defensiva: el record parece
           inmutable pero no lo es (CAPA 6).

        8) Arreglos como componentes: equals y toString no funcionan como
           esperas (CAPA 7).

        9) No normalizar BigDecimal: 10 y 10.00 no son iguales para equals
           (CAPA 3).

        10) Usar records para entidades con identidad o estado que cambia
            (CAPA 11).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - record = clase final e inmutable cuyo estado son sus
          componentes. Java genera campos, constructor canónico, métodos
          de acceso (sin get), equals, hashCode y toString.
        - Constructor compacto: valida y normaliza; Java asigna los campos
          al final.
        - Puede tener métodos, miembros static, constructores adicionales
          (delegando con this(...)) e implementar interfaces.
        - No puede: campos de instancia extra, extender, ser heredado.
        - Inmutabilidad superficial: copia las colecciones en el compacto.
        - Evita arreglos como componentes.
        - "Modificar" = método with que devuelve uno nuevo.
        - Ideal para valores, DTOs, claves de mapas y varios valores de
          retorno. Para entidades con identidad, usa una clase.
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
