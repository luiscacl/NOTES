package v11_patterns.part4_modern;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/*
    =====================================================================
    SPECIFICATION: UNA REGLA DEL NEGOCIO CON NOMBRE, ESCRITA UNA VEZ
    =====================================================================

    Ruta de patterns/: guía 28 de 28  (part4_modern)
    Anterior:  part4_modern/ServiceLayer
    Siguiente: v12_jvm/part1_memory/StackAndHeapMemory  (última de patterns/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Lo que ya se vio y aquí solo se usa:
    - componer Predicate con and/or y armar reglas como un árbol de
      records (part3_behavioral/Interpreter, CAPAS 2-5). Aquí la pregunta
      es otra: cómo usar UNA regla del negocio en todos los lugares que la
      necesitan, incluida la base de datos.

    Importancia: MEDIA. La idea (reglas con nombre y combinables) se usa
    todo el tiempo; la versión completa (traducir a consultas) aparece en
    pantallas de búsqueda con muchos filtros.

    Índice:
        CAPA 1)  El problema: la misma regla copiada en varios lugares
        CAPA 2)  La regla como objeto con nombre
        CAPA 3)  Combinar reglas en el idioma del negocio
        CAPA 4)  Reglas con parámetros (y el tiempo como parámetro)
        CAPA 5)  Una regla para memoria Y para la base de datos
        CAPA 6)  Filtros que elige el usuario, sin inyección SQL
        CAPA 7)  Specification en Spring
        CAPA 8)  Cuándo NO usarla
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Specification {

    public static void main(String[] args) {
        check(sameRuleEverywhereExample(), "CAPA 2");
        check(combinedExample(), "CAPA 3");
        check(parameterizedExample(), "CAPA 4");
        check(memoryAndSqlExample(), "CAPA 5");
        check(userFiltersExample(), "CAPA 6");
        System.out.println("Specification: todos los ejemplos se comportan como se explica.");
    }

    record Customer(String name, String city, boolean active, LocalDate overdueSince) {
        // overdueSince: desde cuándo debe dinero; null si está al corriente
    }

    static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    static final List<Customer> CUSTOMERS = List.of(
            new Customer("Ana", "MTY", true, null),
            new Customer("Luis", "MTY", true, LocalDate.of(2026, 9, 1)),    // debe hace 64 días
            new Customer("Mia", "GDL", true, LocalDate.of(2026, 10, 20)),   // debe hace 15 días
            new Customer("Eve", "MTY", false, LocalDate.of(2026, 1, 10)));  // inactiva

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: LA MISMA REGLA COPIADA EN VARIOS LUGARES
        =================================================================

        El negocio define: "un cliente es MOROSO si está activo y debe
        dinero desde hace más de 30 días". Esa regla se necesita en:

        1) VALIDAR: un moroso no puede hacer pedidos nuevos a crédito;
        2) SELECCIONAR: la lista de cobranza son todos los morosos;
        3) CONSULTAR: la pantalla de cobranza los busca en la base de datos.

        Lo normal es que cada lugar la escriba a su manera: uno pone "> 30",
        otro ">= 30", otro olvida "está activo". Los números de cobranza no
        cuadran con los pedidos rechazados, y nadie sabe cuál es la regla
        "de verdad".

        El patrón SPECIFICATION (Eric Evans y Martin Fowler): la regla es
        UN OBJETO con NOMBRE que responde "¿este objeto la cumple?". Todos
        los lugares usan ese objeto.
    */

    /*
        =================================================================
        CAPA 2) LA REGLA COMO OBJETO CON NOMBRE
        =================================================================

        En la forma clásica es una interface con isSatisfiedBy(T). En Java
        esa interface ya existe: Predicate<T> (v7_functional/part1_lambdas/
        FunctionalInterfaces). Lo que agrega el patrón es el NOMBRE y el
        LUGAR: las reglas viven juntas en una clase del dominio
        (CustomerRules) y tienen nombres del negocio.

        Con eso, validar y seleccionar usan la MISMA regla: si el negocio
        cambia "30 días" por "45 días", se cambia en un solo lugar y las
        dos cosas siguen cuadrando.
    */
    static final class CustomerRules {
        private CustomerRules() {
        }

        static Predicate<Customer> active() {
            return Customer::active;
        }

        static Predicate<Customer> owesMoreThanDays(int days, LocalDate today) {     // CAPA 4
            return customer -> customer.overdueSince() != null
                    && customer.overdueSince().isBefore(today.minusDays(days));
        }

        static Predicate<Customer> inCity(String city) {
            return customer -> customer.city().equals(city);
        }

        /** "Moroso": la regla del negocio, con su nombre, en un solo lugar. */
        static Predicate<Customer> delinquent(LocalDate today) {
            return active().and(owesMoreThanDays(30, today));
        }
    }

    static boolean canOrderOnCredit(Customer customer) {                  // 1) validar
        return CustomerRules.delinquent(TODAY).negate().test(customer);
    }

    static List<Customer> collectionList(List<Customer> customers) {      // 2) seleccionar
        return customers.stream().filter(CustomerRules.delinquent(TODAY)).toList();
    }

    static boolean sameRuleEverywhereExample() {
        List<Customer> toCollect = collectionList(CUSTOMERS);
        List<Customer> rejected = CUSTOMERS.stream().filter(customer -> !canOrderOnCredit(customer)).toList();
        return toCollect.equals(rejected)                                 // siempre cuadran: es la misma regla
                && toCollect.stream().map(Customer::name).toList().equals(List.of("Luis"));
    }

    /*
        =================================================================
        CAPA 3) COMBINAR REGLAS EN EL IDIOMA DEL NEGOCIO
        =================================================================

        Las reglas pequeñas se combinan con and, or y negate (o
        Predicate.not) para formar otras, y el código se lee como el
        negocio lo dice:

            "morosos de Monterrey"      delinquent(today).and(inCity("MTY"))
            "activos al corriente"      active().and(not(owesMoreThanDays(0, today)))

        Cada pieza se prueba sola, y las combinaciones no necesitan
        pruebas nuevas para sus partes.
    */
    static boolean combinedExample() {
        Predicate<Customer> delinquentInMonterrey = CustomerRules.delinquent(TODAY).and(CustomerRules.inCity("MTY"));
        Predicate<Customer> activeUpToDate = CustomerRules.active()
                .and(Predicate.not(CustomerRules.owesMoreThanDays(0, TODAY)));

        return names(delinquentInMonterrey).equals(List.of("Luis"))
                && names(activeUpToDate).equals(List.of("Ana"));
    }

    /*
        =================================================================
        CAPA 4) REGLAS CON PARÁMETROS (Y EL TIEMPO COMO PARÁMETRO)
        =================================================================

        owesMoreThanDays(days, today) es una FÁBRICA de reglas: cada
        llamada crea una regla con sus valores. Así no hay una regla por
        número de días.

        Fíjate en "today": la regla NO llama a LocalDate.now() por dentro.
        Si lo hiciera, el resultado dependería del día en que corre la
        prueba, y "moroso" cambiaría solo a medianoche sin que nadie lo
        note en las pruebas. El tiempo se recibe como parámetro (o con un
        Clock; v10_design/part2_solid/DependencyInversion, CAPA 9).
    */
    static boolean parameterizedExample() {
        Predicate<Customer> moreThan10 = CustomerRules.owesMoreThanDays(10, TODAY);
        Predicate<Customer> moreThan60 = CustomerRules.owesMoreThanDays(60, TODAY);
        LocalDate twoMonthsLater = TODAY.plusMonths(2);

        return names(moreThan10).equals(List.of("Luis", "Mia", "Eve"))
                && names(moreThan60).equals(List.of("Luis", "Eve"))
                && names(CustomerRules.delinquent(twoMonthsLater)).equals(List.of("Luis", "Mia"));   // Mia ya pasa de 30 días
    }

    /*
        =================================================================
        CAPA 5) UNA REGLA PARA MEMORIA Y PARA LA BASE DE DATOS
        =================================================================

        Un Predicate solo sirve EN MEMORIA: para usarlo con un millón de
        clientes habría que cargarlos todos y filtrar en Java, justo lo que
        el repositorio no debe hacer (part4_modern/Repository, CAPA 8). La
        base de datos tiene que recibir la condición como parte de la
        consulta (el WHERE de SQL), y un Predicate es una caja cerrada: no
        se puede leer qué condición tiene adentro (Interpreter, CAPA 5).

        La versión completa del patrón: la regla es un DATO (un árbol de
        records, como en Interpreter) que sabe hacer DOS cosas:
        - test(customer): evaluarse en memoria (validar un objeto);
        - toSql(): traducirse a la condición del WHERE, para que la base de
          datos filtre y devuelva solo los que cumplen.

        El repositorio ofrece findAll(CustomerSpec spec, int limit): recibe
        la regla y la manda a la base de datos. Las dos traducciones están
        JUNTAS en el mismo tipo, así que agregar un tipo de regla obliga
        (por el switch sobre sealed) a escribir las dos: no pueden
        separarse por olvido.

        Pero "juntas" no garantiza "iguales": hay que traducir con cuidado
        el NULL. En Java, test pide overdueSince != null. En SQL, si la
        columna es NULL, "overdue_since < ?" no es falso sino DESCONOCIDO, y
        NOT(desconocido) sigue siendo desconocido: la fila no sale. Así,
        Not(OverdueBefore) incluiría en memoria a un cliente sin adeudo y lo
        excluiría en la base de datos. Por eso toSql escribe la misma
        condición que test: "overdue_since IS NOT NULL AND ...". Regla
        general: toda columna que admite NULL necesita su IS NULL / IS NOT
        NULL explícito en la traducción.

        Los VALORES nunca se pegan en el texto del SQL: van como
        parámetros "?" aparte (CAPA 6 dice por qué).
    */
    record SqlCondition(String where, List<Object> parameters) {
        SqlCondition {
            parameters = List.copyOf(parameters);
        }
    }

    sealed interface CustomerSpec {
        record IsActive() implements CustomerSpec {
        }

        record OverdueBefore(LocalDate cutoff) implements CustomerSpec {
        }

        record InCity(String city) implements CustomerSpec {
        }

        record And(CustomerSpec left, CustomerSpec right) implements CustomerSpec {
        }

        record Not(CustomerSpec spec) implements CustomerSpec {
        }

        static CustomerSpec delinquent(LocalDate today) {                 // la misma regla de la CAPA 2
            return new And(new IsActive(), new OverdueBefore(today.minusDays(30)));
        }

        default CustomerSpec and(CustomerSpec other) {
            return new And(this, other);
        }

        /** Evaluar en memoria. */
        default boolean test(Customer customer) {
            return switch (this) {
                case IsActive _ -> customer.active();
                case OverdueBefore(LocalDate cutoff) ->
                        customer.overdueSince() != null && customer.overdueSince().isBefore(cutoff);
                case InCity(String city) -> customer.city().equals(city);
                case And(CustomerSpec left, CustomerSpec right) -> left.test(customer) && right.test(customer);
                case Not(CustomerSpec spec) -> !spec.test(customer);
            };
        }

        /** Traducir a la condición del WHERE, con los valores como parámetros. */
        default SqlCondition toSql() {
            return switch (this) {
                case IsActive _ -> new SqlCondition("active = ?", List.of(true));
                case OverdueBefore(LocalDate cutoff) -> new SqlCondition("(overdue_since IS NOT NULL AND overdue_since < ?)",
                        List.of(cutoff));
                case InCity(String city) -> new SqlCondition("city = ?", List.of(city));
                case And(CustomerSpec left, CustomerSpec right) -> {
                    SqlCondition l = left.toSql();
                    SqlCondition r = right.toSql();
                    List<Object> parameters = new ArrayList<>(l.parameters());
                    parameters.addAll(r.parameters());
                    yield new SqlCondition("(" + l.where() + " AND " + r.where() + ")", parameters);
                }
                case Not(CustomerSpec spec) -> {
                    SqlCondition inner = spec.toSql();
                    yield new SqlCondition("NOT (" + inner.where() + ")", inner.parameters());
                }
            };
        }
    }

    static boolean memoryAndSqlExample() {
        CustomerSpec delinquent = CustomerSpec.delinquent(TODAY);
        SqlCondition sql = delinquent.toSql();

        List<Customer> inMemory = CUSTOMERS.stream().filter(delinquent::test).toList();
        List<Customer> withPredicate = collectionList(CUSTOMERS);

        return sql.where().equals("(active = ? AND (overdue_since IS NOT NULL AND overdue_since < ?))")
                && sql.parameters().equals(List.of(true, LocalDate.of(2026, 10, 5)))
                && inMemory.equals(withPredicate);                        // la misma regla que la CAPA 2
    }

    /*
        =================================================================
        CAPA 6) FILTROS QUE ELIGE EL USUARIO, SIN INYECCIÓN SQL
        =================================================================

        Donde más se usa: la pantalla de búsqueda con filtros opcionales
        ("ciudad", "solo activos", "solo morosos"). El usuario elige
        cualquier combinación; escribir un método del repositorio por cada
        combinación es imposible (con 3 filtros ya son 8).

        Con especificaciones: se arma la regla con los filtros que llegaron
        y se manda UNA consulta.

        Y la seguridad: el texto que escribe el usuario NUNCA se pega en el
        SQL. Si alguien busca la ciudad

            x' OR '1'='1

        y se pegara en "city = '" + city + "'", la condición quedaría
        "city = 'x' OR '1'='1'", que es verdadera para TODAS las filas: el
        usuario vería clientes que no debía. Es la INYECCIÓN SQL, uno de los
        ataques más comunes de la historia. Con parámetros "?", la base de
        datos recibe el texto aparte y lo trata como un VALOR, nunca como
        parte de la consulta. (En Java, el JDBC lo hace con
        PreparedStatement, que se verá con bases de datos.)
    */
    static CustomerSpec searchFilters(String city, boolean onlyDelinquent) {
        CustomerSpec spec = new CustomerSpec.IsActive();
        if (city != null) {
            spec = spec.and(new CustomerSpec.InCity(city));
        }
        if (onlyDelinquent) {
            spec = spec.and(new CustomerSpec.OverdueBefore(TODAY.minusDays(30)));
        }
        return spec;
    }

    static boolean userFiltersExample() {
        String attack = "x' OR '1'='1";
        SqlCondition sql = searchFilters(attack, false).toSql();

        return sql.where().equals("(active = ? AND city = ?)")           // el texto del usuario NO está en el SQL
                && sql.parameters().equals(List.of(true, attack))         // va aparte, como valor
                && searchFilters(null, true).toSql().where().equals("(active = ? AND (overdue_since IS NOT NULL AND overdue_since < ?))");
    }

    /*
        =================================================================
        CAPA 7) SPECIFICATION EN SPRING
        =================================================================

        Spring Data (v15_spring/) trae este patrón listo: una interface
        Specification<T> cuyas reglas se combinan con and, or y not, y que
        Spring traduce a la consulta de la base de datos. El repositorio la
        recibe en findAll(spec, page). Es exactamente la CAPA 5 sin
        escribir la traducción a mano.

        Lo de esta guía sigue valiendo: reglas con nombre del negocio en un
        solo lugar (no armadas sueltas en cada controller), y siempre con
        límite o página.
    */

    /*
        =================================================================
        CAPA 8) CUÁNDO NO USARLA
        =================================================================

        - Consultas FIJAS: si la app solo busca "por correo" y "activos",
          dos métodos con nombre en el repositorio (findByEmail,
          findActive; part4_modern/Repository, CAPA 2) son más claros que
          un sistema de reglas.
        - Reglas que solo se evalúan en memoria: Predicate con nombre
          (CAPA 2) basta. El árbol con toSql (CAPA 5) solo vale cuando la
          regla TAMBIÉN tiene que ir a la base de datos.
        - Un "lenguaje de consultas" genérico (cualquier campo, cualquier
          operador): eso ya no es una regla del negocio, es reinventar SQL
          y exponer la tabla entera al cliente.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) La misma regla escrita distinto en validación, selección y
           consulta (CAPA 1).

        2) Lambdas sueltas sin nombre en cada lugar, en vez de reglas con
           nombre en un solo lugar (CAPA 2).

        3) LocalDate.now() dentro de la regla: pruebas que dependen del día
           (CAPA 4).

        4) Filtrar con un Predicate en memoria después de cargar toda la
           tabla (CAPA 5).

        5) Pegar el texto del usuario en el SQL: inyección SQL (CAPA 6).

        6) Usar especificaciones para consultas fijas, o convertirlas en un
           lenguaje de consultas genérico (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Specification: una regla del negocio como objeto con nombre,
          escrita una vez y usada para validar, seleccionar y consultar.
        - En memoria, Predicate con nombre basta; se combinan con and, or,
          negate.
        - Reglas con parámetros; el tiempo se recibe, no se consulta.
        - Si la regla debe llegar a la base de datos: un árbol (sealed +
          records) que se evalúa en memoria Y se traduce a SQL, siempre con
          parámetros "?".
        - Brilla en búsquedas con filtros opcionales; para consultas fijas,
          métodos con nombre en el repositorio.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static List<String> names(Predicate<Customer> rule) {
        return CUSTOMERS.stream().filter(rule).map(Customer::name).toList();
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
