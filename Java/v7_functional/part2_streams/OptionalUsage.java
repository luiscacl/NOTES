package v7_functional.part2_streams;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

/*
    =====================================================================
    OPTIONAL: CUÁNDO SÍ Y CUÁNDO NO — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de functional/: guía 6 de 6  (part2_streams)
    Anterior:  part2_streams/AdvancedStreams
    Siguiente: v8_apis/part1_text_and_time/DatesAndTimes  (última de functional/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Nota de nombres: la guía se llama OptionalUsage y no Optional para
    no tapar a java.util.Optional, que es la clase que se estudia.

    Índice:
        CAPA 1)  El problema: null no se ve en la firma
        CAPA 2)  Qué es un Optional
        CAPA 3)  Crearlo: of, ofNullable, empty
        CAPA 4)  Sacar el valor: las formas seguras y la peligrosa
        CAPA 5)  orElse vs orElseGet: la diferencia que causa errores
        CAPA 6)  Transformar sin ifs: map, flatMap, filter
        CAPA 7)  Actuar y combinar: ifPresent, ifPresentOrElse, or, stream
        CAPA 8)  OptionalInt, OptionalLong, OptionalDouble
        CAPA 9)  Dónde SÍ usarlo
        CAPA 10) Dónde NO usarlo
        CAPA 11) ¿Optional, null o excepción?
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class OptionalUsage {

    public static void main(String[] args) {
        check(problemExample(), "CAPA 1");
        check(createExample(), "CAPA 3");
        check(extractExample(), "CAPA 4");
        check(orElseVsOrElseGetExample(), "CAPA 5");
        check(transformExample(), "CAPA 6");
        check(actExample(), "CAPA 7");
        check(primitiveExample(), "CAPA 8");
        check(whereNotExample(), "CAPA 10");
        check(optionalNullOrExceptionExample(), "CAPA 11");
        System.out.println("OptionalUsage: todos los ejemplos se comportan como se explica.");
    }

    // Datos de ejemplo: usuarios con dirección opcional.
    record Address(String street, String city) { }

    record User(String id, String name, Address address) { }   // address puede ser null: datos "de afuera"

    static final Map<String, User> USERS = Map.of(
            "u1", new User("u1", "Ana", new Address("Reforma 10", "CDMX")),
            "u2", new User("u2", "Luis", null));

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: NULL NO SE VE EN LA FIRMA
        =================================================================

        Un método que busca algo puede no encontrarlo. La forma antigua es
        devolver null:

            User findUser(String id)     // ¿puede devolver null? La firma no lo dice.

        Quien lo llama tiene que ADIVINAR o leer la documentación. Si
        olvida revisar, el NullPointerException explota más adelante,
        lejos de la búsqueda, cuando alguien usa el resultado
        (v1_fundamentals/part3_text_and_references/ReferencesAndNull).
        Tony Hoare, quien inventó la referencia nula en 1965, la llamó
        "mi error de mil millones de dólares".

        Con Optional, la ausencia queda escrita en el TIPO:

            Optional<User> findUser(String id)   // "puede que no haya usuario"

        El compilador no te deja usar el User directamente: tienes que
        decidir qué pasa si no está (CAPA 4). La pregunta "¿y si no
        existe?" se responde donde se debe, al momento de llamar.
    */
    static User findUserOrNull(String id) {
        return USERS.get(id);                       // null si no existe: la firma no lo avisa
    }

    static Optional<User> findUser(String id) {
        return Optional.ofNullable(USERS.get(id));   // la firma lo avisa
    }

    static boolean problemExample() {
        boolean nullExplodesLater;
        try {
            User user = findUserOrNull("u9");       // aquí no pasa nada...
            nullExplodesLater = user.name().isEmpty();   // ...explota aquí, al usarlo
        } catch (NullPointerException e) {
            nullExplodesLater = true;
        }

        String name = findUser("u9").map(User::name).orElse("invitado");   // obligado a decidir
        return nullExplodesLater && name.equals("invitado");
    }

    /*
        =================================================================
        CAPA 2) QUÉ ES UN OPTIONAL
        =================================================================

        Un Optional<T> es una CAJA que tiene un valor o está vacía. Nada
        más:
            Optional[Ana]      con valor
            Optional.empty     vacía

        Propiedades:
        - Es INMUTABLE: no se le mete ni se le saca nada después de
          crearlo.
        - Es una "clase basada en valor" (value-based): dos Optional con
          el mismo contenido son iguales con equals; no compares con ==,
          y no uses synchronized sobre ellos.
        - NO es Serializable (CAPA 10).

        Para qué se diseñó, según Brian Goetz (arquitecto del lenguaje):
        como TIPO DE RETORNO de métodos donde "no hay resultado" es una
        respuesta normal, y para encadenar operaciones sobre ese
        resultado. NO como reemplazo general de null en todo el código
        (CAPA 10).
    */

    /*
        =================================================================
        CAPA 3) CREARLO: OF, OFNULLABLE, EMPTY
        =================================================================

            Optional.of(valor)          con valor; si valor es null, lanza
                                        NullPointerException
            Optional.ofNullable(valor)  con valor, o vacío si es null
            Optional.empty()            vacío

        Cuál usar:
        - of cuando SABES que no es null: si lo es, es un error y quieres
          enterarte ahí.
        - ofNullable para envolver algo que viene de una API que usa null
          (map.get, código antiguo).
        - empty para decir "no hay" explícitamente.

        REGLA ABSOLUTA: un método que devuelve Optional NUNCA devuelve
        null. Un Optional null combina lo peor de los dos mundos: quien
        llama confía en que no es null y recibe un NullPointerException.
    */
    static boolean createExample() {
        Optional<String> present = Optional.of("Ana");
        Optional<String> fromNull = Optional.ofNullable(null);
        Optional<String> none = Optional.empty();

        boolean ofRejectsNull;
        try {
            Optional.of(null);
            ofRejectsNull = false;
        } catch (NullPointerException e) {
            ofRejectsNull = true;
        }

        return present.isPresent() && fromNull.isEmpty() && none.equals(fromNull) && ofRejectsNull;
    }

    /*
        =================================================================
        CAPA 4) SACAR EL VALOR: LAS FORMAS SEGURAS Y LA PELIGROSA
        =================================================================

        | Método                      | Si hay valor | Si está vacío                       |
        |-----------------------------|--------------|-------------------------------------|
        | orElse(otro)                | el valor     | otro                                |
        | orElseGet(supplier)         | el valor     | lo que dé el supplier (CAPA 5)      |
        | orElseThrow(supplier)       | el valor     | lanza TU excepción                  |
        | orElseThrow()               | el valor     | NoSuchElementException              |
        | get()                       | el valor     | NoSuchElementException ("No value present") |

        get() y orElseThrow() sin argumentos hacen EXACTAMENTE lo mismo.
        orElseThrow() se agregó en Java 10 porque get() se usaba sin
        pensar: el nombre "get" suena seguro. Las guías de estilo
        (y los analizadores de código) desaconsejan get(); si de verdad
        "no puede estar vacío", escribe orElseThrow(): el nombre avisa.

        El ANTIPATRÓN más común:
            if (opt.isPresent()) {
                use(opt.get());
            }
        Es el mismo "if (x != null)" de siempre, con más ceremonia. Usa
        ifPresent, map u orElse (CAPAS 6 y 7).

        orElseThrow(supplier) es la forma correcta cuando la ausencia ES
        un error en ese punto: "el usuario de esta sesión debe existir".
        La excepción lleva un mensaje claro en lugar del genérico
        (v3_exceptions/part2_in_practice/CustomExceptions).
    */
    static final class UserNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UserNotFoundException(String id) {
            super("No existe el usuario " + id);
        }
    }

    static User requireUser(String id) {
        return findUser(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    static boolean extractExample() {
        String withDefault = findUser("u9").map(User::name).orElse("invitado");
        String found = findUser("u1").map(User::name).orElse("invitado");

        boolean getFails;
        try {
            findUser("u9").get();                   // la peligrosa
            getFails = false;
        } catch (NoSuchElementException e) {
            getFails = e.getMessage().equals("No value present");
        }

        boolean customException;
        try {
            requireUser("u9");
            customException = false;
        } catch (UserNotFoundException e) {
            customException = e.getMessage().equals("No existe el usuario u9");
        }

        return withDefault.equals("invitado") && found.equals("Ana") && getFails && customException;
    }

    /*
        =================================================================
        CAPA 5) ORELSE VS ORELSEGET: LA DIFERENCIA QUE CAUSA ERRORES
        =================================================================

        Parecen iguales, pero:
        - orElse(valor): el valor se calcula SIEMPRE, antes de llamar al
          método (es un argumento normal), haya o no contenido.
        - orElseGet(supplier): el supplier se ejecuta SOLO si está vacío
          (evaluación perezosa; part1_lambdas/FunctionalInterfaces, CAPA 3).

        Con un valor fijo ("invitado", 0) da igual: usa orElse, que se lee
        más simple. Con algo CARO o con EFECTOS, orElse es un error:

            findUser(id).orElse(createGuestUser())
            // createGuestUser() se ejecuta SIEMPRE: aunque el usuario
            // exista, se crea (y quizá se guarda en la base de datos) un
            // invitado que nadie usa.

            findUser(id).orElseGet(() -> createGuestUser())   // solo si falta
    */
    static int guestsCreated = 0;

    static User createGuestUser() {
        guestsCreated++;                            // imagina un INSERT en la base de datos
        return new User("guest", "invitado", null);
    }

    static boolean orElseVsOrElseGetExample() {
        guestsCreated = 0;
        User eager = findUser("u1").orElse(createGuestUser());          // Ana existe... y aun así se crea un invitado
        boolean createdAnyway = guestsCreated == 1;

        User lazy = findUser("u1").orElseGet(OptionalUsage::createGuestUser); // Ana existe: no se crea nada
        boolean notCreated = guestsCreated == 1;

        return eager.name().equals("Ana") && lazy.name().equals("Ana") && createdAnyway && notCreated;
    }

    /*
        =================================================================
        CAPA 6) TRANSFORMAR SIN IFS: MAP, FLATMAP, FILTER
        =================================================================

        Optional tiene las mismas ideas que un stream de 0 o 1 elementos
        (part2_streams/StreamsBasics):

            map(f)       si hay valor, lo transforma; si f devuelve null,
                         queda vacío; si estaba vacío, sigue vacío
            filter(p)    si hay valor y NO cumple, queda vacío
            flatMap(f)   como map, pero para funciones que YA devuelven
                         Optional (evita Optional<Optional<T>>)

        Con eso, cadenas de "si existe esto, y si existe aquello..." se
        escriben sin ningún if:

            Antes:
                User user = findUserOrNull(id);
                if (user != null) {
                    Address address = user.address();
                    if (address != null) {
                        return address.city();
                    }
                }
                return "sin ciudad";

            Con Optional:
                return findUser(id)
                        .map(User::address)          // address null -> vacío
                        .map(Address::city)
                        .orElse("sin ciudad");

        Cualquier eslabón vacío deja vacío todo el resto, y al final
        decides UNA vez qué hacer.
    */
    static Optional<Address> findAddress(String userId) {
        return findUser(userId).map(User::address);  // devuelve Optional: útil con flatMap
    }

    static String cityOf(String userId) {
        return findUser(userId)
                .map(User::address)
                .map(Address::city)
                .orElse("sin ciudad");
    }

    static boolean transformExample() {
        Optional<Optional<Address>> nested = Optional.of("u1").map(OptionalUsage::findAddress);
        Optional<Address> flat = Optional.of("u1").flatMap(OptionalUsage::findAddress);   // sin doble caja

        Optional<User> capitalUser = findUser("u1")
                .filter(user -> user.address() != null && user.address().city().equals("CDMX"));

        return cityOf("u1").equals("CDMX")
                && cityOf("u2").equals("sin ciudad")        // Luis existe, pero sin dirección
                && cityOf("u9").equals("sin ciudad")        // no existe
                && nested.get().isPresent() && flat.isPresent()
                && capitalUser.isPresent()
                && findUser("u2").filter(user -> user.address() != null).isEmpty();
    }

    /*
        =================================================================
        CAPA 7) ACTUAR Y COMBINAR: IFPRESENT, IFPRESENTORELSE, OR, STREAM
        =================================================================

            ifPresent(acción)                   ejecuta la acción solo si hay valor
            ifPresentOrElse(acción, siVacío)    una u otra (Java 9)
            or(() -> otroOptional)              si está vacío, prueba OTRA fuente
                                                que también devuelve Optional (Java 9)
            stream()                            Stream de 0 o 1 elementos (Java 9)

        or() sirve para "buscar en varios lugares en orden": primero en la
        caché, si no en la base de datos, si no en un servicio externo. Cada
        búsqueda solo se hace si la anterior no encontró nada.

        stream() sirve para quedarse solo con los resultados presentes de
        una lista de búsquedas:
            ids.stream().map(this::findUser).flatMap(Optional::stream).toList()
        Los vacíos producen un stream vacío y desaparecen al aplanar
        (AdvancedStreams, CAPA 8).
    */
    static boolean actExample() {
        List<String> log = new ArrayList<>();
        findUser("u1").ifPresent(user -> log.add("bienvenida " + user.name()));
        findUser("u9").ifPresentOrElse(
                user -> log.add("bienvenida " + user.name()),
                () -> log.add("usuario no encontrado"));

        Map<String, String> cache = Map.of("u7", "Mia");
        String fromAnywhere = Optional.ofNullable(cache.get("u1"))
                .or(() -> findUser("u1").map(User::name))   // segunda fuente, solo si la primera falla
                .orElse("desconocido");

        List<String> existingNames = Stream.of("u1", "u9", "u2")
                .map(OptionalUsage::findUser)
                .flatMap(Optional::stream)    // los vacíos desaparecen
                .map(User::name)
                .toList();

        return log.equals(List.of("bienvenida Ana", "usuario no encontrado"))
                && fromAnywhere.equals("Ana")
                && existingNames.equals(List.of("Ana", "Luis"));
    }

    /*
        =================================================================
        CAPA 8) OPTIONALINT, OPTIONALLONG, OPTIONALDOUBLE
        =================================================================

        Las versiones primitivas, que devuelven los streams primitivos
        (AdvancedStreams, CAPA 9): IntStream.max() da un OptionalInt,
        average() un OptionalDouble. Existen para evitar el boxing.

        Diferencias con Optional<T>:
        - El método para sacar el valor es getAsInt(), getAsLong(),
          getAsDouble(); también tienen orElse, orElseGet, orElseThrow,
          ifPresent e ifPresentOrElse.
        - NO tienen map, filter ni flatMap. Si necesitas transformar, o lo
          resuelves antes en el stream, o lo conviertes.

        No los uses como tipo de retorno de tus métodos solo por
        "ahorrar": son incómodos. Úsalos cuando ya vienen de un stream.
    */
    static boolean primitiveExample() {
        OptionalInt highest = Stream.of(3, 9, 4).mapToInt(Integer::intValue).max();
        OptionalInt none = Stream.<Integer>empty().mapToInt(Integer::intValue).max();

        return highest.getAsInt() == 9 && none.orElse(-1) == -1 && none.isEmpty();
    }

    /*
        =================================================================
        CAPA 9) DÓNDE SÍ USARLO
        =================================================================

        1) TIPO DE RETORNO de un método que puede no tener resultado, y
           donde eso es NORMAL: buscar por id, el primero que cumpla algo,
           leer una configuración opcional, parsear un texto que puede no
           ser válido.
               Optional<User> findById(String id)
               Optional<Coupon> bestCouponFor(Cart cart)
           Spring Data, por ejemplo, define findById con Optional
           (v15_spring/).

        2) El resultado de los streams: findFirst, findAny, min, max,
           reduce sin identidad. No lo elegiste tú: úsalo bien.

        3) Una cadena de "si existe..." en una sola expresión (CAPA 6).

        Y cuando lo recibes, consúmelo PRONTO: decide en el lugar con
        orElse, orElseThrow, map o ifPresent. No lo pases de método en
        método.
    */

    /*
        =================================================================
        CAPA 10) DÓNDE NO USARLO
        =================================================================

        1) COMO CAMPO de una clase. Optional no es Serializable (los
           frameworks que guardan o envían objetos fallan), gasta un objeto
           extra por campo, y la clase ya CONTROLA su estado: guarda el
           campo como null por dentro y ofrece un GETTER que devuelva
           Optional:
               private final String nickname;            // puede ser null
               Optional<String> nickname() { return Optional.ofNullable(nickname); }

        2) COMO PARÁMETRO. Obliga a quien llama a envolver
           (process(Optional.empty()), process(Optional.of(x))), y el
           parámetro aún puede llegar null. Mejor: dos métodos (o una
           sobrecarga), o un parámetro que acepte null documentado, o un
           objeto de configuración.

        3) PARA COLECCIONES. Optional<List<Order>> es redundante: una
           lista VACÍA ya significa "no hay". Devuelve List.of() (o un
           Map, Set... vacíos), nunca null y nunca Optional.

        4) DENTRO DE COLECCIONES: List<Optional<T>> o valores
           Optional en un Map. Filtra los vacíos (CAPA 7) o no los metas.

        5) Optional<Optional<T>>: usa flatMap (CAPA 6).

        6) En código muy caliente (un ciclo de millones de vueltas): cada
           Optional es un objeto. Rara vez importa, pero existe.

        7) Para "acortar" un if (x != null): Optional.ofNullable(x)
           .ifPresent(...) donde x es una variable local tuya solo agrega
           un objeto. Un if es más claro.
    */
    record Profile(String name, String nickname) {     // nickname puede ser null: NO Optional como campo
        Optional<String> nicknameIfAny() {    // el getter sí devuelve Optional
            return Optional.ofNullable(nickname);
        }
    }

    static List<String> ordersOf(String userId) {
        return userId.equals("u1") ? List.of("pedido-1") : List.of();   // sin pedidos: lista vacía, no Optional
    }

    static boolean whereNotExample() {
        boolean notSerializable = !Serializable.class.isAssignableFrom(Optional.class);
        Profile profile = new Profile("Ana", null);

        return notSerializable
                && profile.nicknameIfAny().orElse(profile.name()).equals("Ana")
                && ordersOf("u2").isEmpty() && ordersOf("u1").size() == 1;
    }

    /*
        =================================================================
        CAPA 11) ¿OPTIONAL, NULL O EXCEPCIÓN?
        =================================================================

        Tres formas de decir "no hay resultado". Cómo elegir:

        | Situación                                              | Usa               |
        |--------------------------------------------------------|-------------------|
        | "No hay" es una respuesta NORMAL que quien llama debe  | Optional          |
        | manejar (buscar, el primero que...)                    |                   |
        | "No hay" es un ERROR: no debería pasar si todo está    | excepción         |
        | bien (el id viene de una sesión válida, un archivo     | (orElseThrow o    |
        | obligatorio falta)                                     |  lanzarla)        |
        | Una colección sin elementos                            | colección vacía   |
        | Detalle INTERNO de una clase (un campo privado, una    | null, bien        |
        | variable local) que nadie de afuera ve                 | controlado        |

        Un mismo dato puede necesitar las dos según el caso de uso:
        findUser(id) devuelve Optional (buscar es normal), y requireUser(id)
        lanza excepción (en ese punto, no encontrarlo es un error). Las dos
        conviven: requireUser se escribe con findUser + orElseThrow
        (CAPA 4).
    */
    static boolean optionalNullOrExceptionExample() {
        boolean searchIsNormal = findUser("u9").isEmpty();      // buscar y no encontrar: normal
        boolean requireIsError;
        try {
            requireUser("u9");                                  // exigir y no encontrar: error
            requireIsError = false;
        } catch (UserNotFoundException e) {
            requireIsError = true;
        }
        return searchIsNormal && requireIsError;
    }

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Devolver null desde un método que devuelve Optional (CAPA 3).

        2) Optional.of con algo que puede ser null (CAPA 3).

        3) get() sin estar seguro, o isPresent() + get() como un if de null
           con más pasos (CAPA 4).

        4) orElse con algo caro o con efectos: se ejecuta siempre (CAPA 5).

        5) map con una función que devuelve Optional: Optional<Optional<T>>;
           era flatMap (CAPA 6).

        6) Optional como campo, parámetro, o envolviendo colecciones
           (CAPA 10).

        7) Comparar Optional con ==, o sincronizar sobre ellos (CAPA 2).

        8) Usar Optional cuando la ausencia es un error que debería ser
           una excepción, o al revés (CAPA 11).

        9) Pasar el Optional de método en método sin decidir nunca qué
           hacer con el vacío (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Optional<T> es una caja con 0 o 1 valor que pone la posible
          ausencia en el TIPO de retorno.
        - Crear: of (no null), ofNullable, empty. Nunca devolver null en
          su lugar.
        - Sacar: orElse (valor fijo), orElseGet (caro o con efectos),
          orElseThrow(supplier) (ausencia = error). Evita get().
        - Transformar: map, flatMap (si la función ya da Optional), filter.
        - Actuar: ifPresent, ifPresentOrElse; or para otra fuente; stream()
          para filtrar vacíos en un stream.
        - OptionalInt/Long/Double: getAsInt..., sin map ni filter.
        - Sí: tipo de retorno de búsquedas. No: campos, parámetros,
          colecciones, Optional dentro de Optional.
        - Ausencia normal -> Optional; ausencia = error -> excepción;
          colección -> vacía.

        Con esto termina functional/: lambdas, interfaces funcionales,
        referencias a métodos, streams y Optional.
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
