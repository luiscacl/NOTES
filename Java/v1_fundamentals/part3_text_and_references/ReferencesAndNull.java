package v1_fundamentals.part3_text_and_references;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
    =====================================================================
    REFERENCIAS Y NULL — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 10 de 14  (part3_text_and_references)
    Anterior:  part3_text_and_references/Strings
    Siguiente: part3_text_and_references/Wrappers

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Dos familias de tipos: primitivos y referencias
        CAPA 2)  Copiar una referencia NO copia el objeto
        CAPA 3)  Copiar de verdad: copia superficial y profunda
        CAPA 4)  == compara referencias; equals compara contenido
        CAPA 5)  Qué pasa con un objeto que nadie referencia
        CAPA 6)  Qué es null
        CAPA 7)  NullPointerException y cómo leer su mensaje
        CAPA 8)  De dónde sale null
        CAPA 9)  Defenderse de null
        CAPA 10) null, vacío y en blanco no son lo mismo
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class ReferencesAndNull {

    public static void main(String[] args) {
        check(primitiveVsReferenceExample(), "CAPA 1");
        check(aliasingExample(), "CAPA 2");
        check(copyExample(), "CAPA 3");
        check(identityVsEqualityExample(), "CAPA 4");
        check(whatIsNullExample(), "CAPA 6");
        check(nullPointerExample(), "CAPA 7");
        check(whereNullComesFromExample(), "CAPA 8");
        check(nullDefensesExample(), "CAPA 9");
        check(nullEmptyBlankExample(), "CAPA 10");
        System.out.println("ReferencesAndNull: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) DOS FAMILIAS DE TIPOS: PRIMITIVOS Y REFERENCIAS
        =================================================================

        Todo tipo en Java pertenece a una de dos familias, y entender la
        diferencia cambia por completo cómo razonas sobre copias,
        cambios y métodos (como decía tu antigua JavaDataStructuresGuide):

        - PRIMITIVOS (los 8 de PrimitiveTypes): la variable guarda el
          VALOR directamente.
        - REFERENCIAS (todo lo demás: String, arreglos, List, Map, tus
          clases): la variable NO guarda el objeto. Guarda una
          REFERENCIA: la DIRECCIÓN en memoria donde vive el objeto.

        Cómo se ve en memoria:

            int count = 3;                  PILA (stack)           MONTÓN (heap)
            Account ana = new Account(...); ┌──────────────┐
                                            │ count │  3   │
                                            │ ana   │  ●───┼──────► [Account: "Ana", 500]
                                            └──────────────┘

        - Las variables locales viven en la PILA; los objetos, en el
          MONTÓN (heap). new crea el objeto en el heap y devuelve su
          dirección.
        - Por qué Java separa así: copiar un int es copiar 4 bytes; copiar
          un objeto grande en cada asignación sería lentísimo. Pasar la
          dirección es igual de barato sin importar el tamaño del objeto.

        Si vienes de JavaScript: es exactamente igual. Los números y
        booleanos se copian; los objetos y arreglos se comparten por
        referencia. Java solo lo hace más explícito.
    */
    static final class Account {
        private String owner;
        private int balance;

        Account(String owner, int balance) {
            this.owner = owner;
            this.balance = balance;
        }

        void rename(String newOwner) {
            this.owner = newOwner;
        }

        void deposit(int amount) {
            this.balance += amount;
        }

        String getOwner() {
            return owner;
        }

        int getBalance() {
            return balance;
        }
    }

    static boolean primitiveVsReferenceExample() {
        int a = 10;
        int b = a;          // copia el VALOR
        b = 99;

        return a == 10 && b == 99;   // a no cambió: cada variable tiene su propio valor
    }

    /*
        =================================================================
        CAPA 2) COPIAR UNA REFERENCIA NO COPIA EL OBJETO
        =================================================================

        Al asignar una variable de referencia a otra, se copia la
        DIRECCIÓN, no el objeto. Ahora hay DOS variables apuntando al
        MISMO objeto. A esto se le llama ALIASING (dos "alias" para lo
        mismo).

            Account first = new Account("Ana", 500);
            Account second = first;          // copia la dirección

            first  ●───┐
                       ├──► [Account: "Ana", 500]     UN solo objeto
            second ●───┘

        Consecuencia: un cambio hecho A TRAVÉS de una variable se ve
        desde la otra, porque es el mismo objeto. (Es el ejemplo de tu
        TypingGuide con PersonaRef, y el de los arreglos arr1/arr2.)

        Esto explica también los métodos: cuando pasas un objeto a un
        método, el parámetro es otro alias del mismo objeto (Methods,
        CAPA 4).
    */
    static boolean aliasingExample() {
        Account first = new Account("Ana", 500);
        Account second = first;           // NO crea otra cuenta
        second.rename("María");
        second.deposit(100);

        int[] arr1 = {1, 2, 3};
        int[] arr2 = arr1;                 // los arreglos también son objetos
        arr2[0] = 999;

        return first.getOwner().equals("María")   // el cambio hecho por second se ve en first
                && first.getBalance() == 600
                && arr1[0] == 999;
    }

    /*
        =================================================================
        CAPA 3) COPIAR DE VERDAD: COPIA SUPERFICIAL Y PROFUNDA
        =================================================================

        Si quieres un objeto INDEPENDIENTE, hay que pedirlo explícitamente:
        - arreglos: Arrays.copyOf(arr, arr.length) o arr.clone()
        - listas:   new ArrayList<>(original) o List.copyOf(original)
        - tus clases: un constructor de copia (ObjectContract, CAPA 11)

        PERO cuidado: esas copias son SUPERFICIALES (shallow). Copian el
        contenedor, pero si los elementos son objetos, la copia guarda
        las MISMAS referencias a esos objetos. Tienes dos listas
        distintas... que apuntan a las mismas cuentas.

            original ──► [ ● , ● ]
                           │   │
                           ▼   ▼
                         Ana  Luis        <- las cuentas son compartidas
                           ▲   ▲
                           │   │
            copia    ──► [ ● , ● ]

        - Agregar o quitar elementos en la copia NO afecta al original.
        - Modificar un ELEMENTO (renombrar una cuenta) SÍ se ve en las dos.

        Una COPIA PROFUNDA (deep) copia también cada elemento. Hay que
        escribirla a mano. Es una de las razones por las que se prefieren
        objetos INMUTABLES (records, String): si nadie puede modificarlos,
        compartirlos es seguro y la copia superficial basta.
    */
    static boolean copyExample() {
        int[] original = {5, 6, 7};
        int[] independent = Arrays.copyOf(original, original.length);
        independent[0] = 12345;
        boolean arrayCopyIsIndependent = original[0] == 5;

        List<Account> accounts = new ArrayList<>(List.of(new Account("Ana", 100), new Account("Luis", 200)));
        List<Account> shallowCopy = new ArrayList<>(accounts);

        shallowCopy.add(new Account("Mia", 300));
        boolean containersIndependent = accounts.size() == 2;          // agregar no afecta al original

        shallowCopy.get(0).rename("Ana María");
        boolean elementsShared = accounts.get(0).getOwner().equals("Ana María");   // los elementos sí se comparten

        List<Account> deepCopy = new ArrayList<>();
        for (Account account : accounts) {
            deepCopy.add(new Account(account.getOwner(), account.getBalance()));   // un objeto nuevo por elemento
        }
        deepCopy.get(1).rename("Otro");
        boolean deepIsIndependent = accounts.get(1).getOwner().equals("Luis");

        return arrayCopyIsIndependent && containersIndependent && elementsShared && deepIsIndependent;
    }

    /*
        =================================================================
        CAPA 4) == COMPARA REFERENCIAS; EQUALS COMPARA CONTENIDO
        =================================================================

        Con referencias, == pregunta "¿son la MISMA dirección?" (el mismo
        objeto). equals pregunta "¿representan lo mismo?", según lo que
        defina cada clase.

        Los ejemplos de tu Base.java:
        - person3 = person1  -> misma dirección -> == true
        - "Hola" == new String("Hola") -> objetos distintos -> == false,
          aunque equals diga true.

        Las reglas completas de equals (y por qué hay que escribirlo
        junto con hashCode) están en ObjectContract; el caso especial
        de String y su pool, en Strings, CAPA 3.
    */
    static boolean identityVsEqualityExample() {
        Account person1 = new Account("Luis", 25);
        Account person2 = new Account("Luis", 25);   // mismos datos, OTRO objeto
        Account person3 = person1;                   // mismo objeto

        String s1 = "Hola";
        String s3 = new String("Hola");

        return person1 == person3
                && person1 != person2                 // Account no sobrescribe equals: compara identidad
                && !person1.equals(person2)
                && s1 != s3 && s1.equals(s3);
    }

    /*
        =================================================================
        CAPA 5) QUÉ PASA CON UN OBJETO QUE NADIE REFERENCIA
        =================================================================

        Un objeto vive en el heap MIENTRAS alguien tenga su dirección. Si
        reasignas la única variable que lo apuntaba, el objeto queda
        INALCANZABLE: ningún código puede volver a usarlo.

            Account temp = new Account("Ana", 0);
            temp = new Account("Luis", 0);     // la cuenta de Ana ya no tiene quién la apunte

        El RECOLECTOR DE BASURA (HowJavaWorks, CAPA 10) detecta esos
        objetos y libera su memoria en algún momento. No hay "delete" en
        Java, y no hace falta asignar null a las variables "para
        liberar": al salir del método, las variables locales desaparecen
        solas.

        La otra cara: un objeto que SIGUE referenciado nunca se libera,
        aunque ya no lo necesites. Una lista static a la que solo se le
        agregan cosas, o un caché que nunca se limpia, crecen hasta
        agotar la memoria: es una FUGA DE MEMORIA, incluso con recolector
        (v12_jvm/part1_memory/GarbageCollector).
    */

    /*
        =================================================================
        CAPA 6) QUÉ ES NULL
        =================================================================

        null es el valor de una referencia que NO apunta a ningún objeto.
        Es "la dirección vacía".

        - Solo existe para tipos de REFERENCIA. Un primitivo nunca es
          null: int x = null; no compila (para eso existen los wrappers:
          v1_fundamentals/Wrappers).
        - Es el valor POR DEFECTO de los campos de referencia y de las
          posiciones de un arreglo de objetos (tu NullExample.java).
        - Concatenar null con un String produce el texto "null".
        - null instanceof CualquierCosa es siempre false.
        - null == null es true.

        Su inventor, Tony Hoare, lo llamó en 2009 "mi error de mil
        millones de dólares", por la cantidad de fallas que ha causado en
        todos los lenguajes. No es malo en sí: el problema es usar una
        referencia sin saber si puede ser null.

        Si vienes de JavaScript: JS tiene null Y undefined. Java solo
        tiene null; y lo que en JS sería "undefined" (una variable local
        sin valor) en Java ni siquiera compila (Variables, CAPA 3).
    */
    static final class Defaults {
        int count;          // 0
        double amount;      // 0.0
        String name;        // null
        Account account;    // null
    }

    static boolean whatIsNullExample() {
        Defaults defaults = new Defaults();
        Account[] slots = new Account[3];   // tres posiciones, ninguna cuenta todavía
        String nothing = null;

        return defaults.count == 0 && defaults.amount == 0.0
                && defaults.name == null && defaults.account == null
                && slots[0] == null
                && ("texto = " + nothing).equals("texto = null")
                && !(nothing instanceof String);   // ni siquiera un String null "es" un String
    }

    /*
        =================================================================
        CAPA 7) NULLPOINTEREXCEPTION Y CÓMO LEER SU MENSAJE
        =================================================================

        Usar una referencia null como si tuviera un objeto lanza
        NullPointerException (NPE). Pasa al:
        - llamar un método:            text.length()
        - leer o escribir un campo:    account.balance
        - acceder a un arreglo null:   numbers[0] o numbers.length
        - convertir un wrapper null a primitivo (unboxing): int x = nullInteger;
        - hacer switch o synchronized sobre null, o throw null

        Por qué falla ahí y no antes: el compilador no sabe si una
        referencia será null al ejecutar; solo lo descubre la JVM cuando
        intenta seguir la dirección y no hay nada.

        MENSAJES ÚTILES (activados por defecto desde Java 15): la JVM dice
        exactamente QUÉ era null.
            Cannot invoke "...Address.city()" because the return value of
            "...Customer.address()" is null
        Se lee: "intenté llamar city(), pero lo que devolvió address()
        era null". En una cadena a().b().c() te dice cuál eslabón falló.

        Detalle: para variables LOCALES el mensaje dice "<localN>" (N es el
        número de la variable dentro del método, por ejemplo "<local5>") en
        lugar del nombre, porque por defecto el compilador no guarda los
        nombres de las variables locales. Compilando con javac -g (lo que
        hacen los IDE y Maven al depurar) aparece el nombre real:
            Cannot invoke "String.length()" because "text" is null
    */
    record Address(String city) { }

    record Customer(String name, Address address) { }

    static boolean nullPointerExample() {
        Customer withoutAddress = new Customer("Ana", null);

        String message;
        try {
            withoutAddress.address().city();
            message = "";
        } catch (NullPointerException e) {
            message = e.getMessage();
        }

        // El mensaje señala exactamente el eslabón que era null.
        return message.contains("because the return value of")
                && message.contains("Customer.address()")
                && message.contains("is null");
    }

    /*
        =================================================================
        CAPA 8) DE DÓNDE SALE NULL
        =================================================================

        Saber de dónde puede venir es la mitad de la defensa:

        1) Campos que nadie inicializó (CAPA 6).
        2) Posiciones de arreglos de objetos sin llenar.
        3) Map.get de una clave que no existe devuelve null (de tu
           JavaDataStructuresGuide). Y si lo guardas en un int, el
           unboxing lanza NPE.
        4) Métodos que devuelven null para "no encontrado" (muchas APIs
           viejas).
        5) Datos de fuera: JSON con campos ausentes, columnas vacías en
           una base de datos, parámetros opcionales de una petición web.
    */
    static boolean whereNullComesFromExample() {
        Map<String, Integer> ages = new HashMap<>();
        ages.put("Luis", 25);

        Integer missing = ages.get("Pedro");          // no existe: null
        boolean mapReturnsNull = missing == null;

        boolean unboxingFails;
        try {
            int dangerous = ages.get("Pedro");        // null -> int: NPE
            unboxingFails = dangerous < 0;
        } catch (NullPointerException e) {
            unboxingFails = true;
        }

        return mapReturnsNull && unboxingFails && ages.getOrDefault("Pedro", 0) == 0;
    }

    /*
        =================================================================
        CAPA 9) DEFENDERSE DE NULL
        =================================================================

        No se trata de llenar el código de "if (x != null)". Se trata de
        DECIDIR dónde puede existir null, y cerrarle la puerta en el resto.

        1) FALLA RÁPIDO EN LA FRONTERA. Valida con Objects.requireNonNull
           al recibir datos (constructores, métodos públicos). Si algo
           llega null, falla ahí, con un mensaje claro, y no 500 líneas
           después (Encapsulation, CAPA 4).

        2) NO DEVUELVAS null:
           - para "no hay resultados", una colección VACÍA (Methods, CAPA 10);
           - para "puede no existir", Optional (v7_functional/part2_streams/OptionalUsage);
           - para "no hay comportamiento", un Null Object (Polymorphism, CAPA 8).

        3) USA LAS HERRAMIENTAS null-safe del JDK:
           Objects.equals(a, b)                   compara aunque alguno sea null
           Objects.requireNonNullElse(x, def)     x, o def si x es null
           Objects.toString(x, "def")             texto, o "def" si x es null
           "literal".equals(variable)             nunca lanza NPE (Strings, CAPA 3)
           map.getOrDefault(clave, def)           en lugar de get + revisar null

        4) LAS COLECCIONES MODERNAS RECHAZAN null: List.of, Set.of y
           Map.of lanzan NPE si les pasas null. Es a propósito: un null
           dentro de una colección es una bomba de tiempo.

        5) MARCA LA INTENCIÓN. En proyectos reales se usan anotaciones
           como @Nullable y @NonNull (por ejemplo, de JSpecify) para que
           el IDE avise si usas algo que puede ser null sin revisarlo.

        Si vienes de JavaScript: Java no tiene ?. (encadenamiento
        opcional) ni ?? (valor por defecto). Sus equivalentes son Optional
        y Objects.requireNonNullElse.
    */
    static final class Invoice {
        private final String customerName;
        private final String notes;

        Invoice(String customerName, String notes) {
            this.customerName = Objects.requireNonNull(customerName, "customerName");   // obligatorio
            this.notes = Objects.requireNonNullElse(notes, "");                         // opcional: nunca null por dentro
        }

        String getCustomerName() {
            return customerName;
        }

        String getNotes() {
            return notes;
        }
    }

    static boolean nullDefensesExample() {
        boolean failsFastWithMessage;
        try {
            new Invoice(null, "x");
            failsFastWithMessage = false;
        } catch (NullPointerException e) {
            failsFastWithMessage = e.getMessage().equals("customerName");   // dice EXACTAMENTE qué faltó
        }

        Invoice invoice = new Invoice("Ana", null);
        boolean optionalNormalized = invoice.getNotes().isEmpty();   // nunca null hacia adentro

        boolean nullSafeHelpers = Objects.equals(null, null)
                && !Objects.equals("a", null)
                && Objects.toString(null, "sin dato").equals("sin dato");

        boolean modernCollectionsReject;
        try {
            List.of("a", null);
            modernCollectionsReject = false;
        } catch (NullPointerException e) {
            modernCollectionsReject = true;
        }

        return failsFastWithMessage && optionalNormalized && nullSafeHelpers
                && modernCollectionsReject && invoice.getCustomerName().equals("Ana");
    }

    /*
        =================================================================
        CAPA 10) NULL, VACÍO Y EN BLANCO NO SON LO MISMO
        =================================================================

        Tres situaciones distintas que a veces se confunden:

            String a = null;     // no hay texto (no se sabe, no se dio)
            String b = "";       // hay texto, y está vacío
            String c = "   ";    // hay texto, solo con espacios

        | Valor   | == null | isEmpty()  | isBlank()  |
        |---------|---------|------------|------------|
        | null    | true    | NPE        | NPE        |
        | ""      | false   | true       | true       |
        | "   "   | false   | false      | true       |

        Lo mismo con colecciones: una lista null ("no hay lista") no es una
        lista vacía ("hay lista, sin elementos"). Tener ambos estados
        obliga a revisar los dos en todos lados. Por eso la regla
        profesional: elige UNA representación (casi siempre vacío) y
        conviértela al recibir datos, como hace Invoice con sus notas.
    */
    static boolean isMissing(String value) {
        return value == null || value.isBlank();   // el orden importa: null se revisa primero (cortocircuito)
    }

    static boolean nullEmptyBlankExample() {
        return isMissing(null) && isMissing("") && isMissing("   ") && !isMissing("Ana");
    }

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Creer que asignar un objeto a otra variable lo copia (CAPA 2).

        2) Creer que new ArrayList<>(lista) copia también los objetos de
           adentro (CAPA 3).

        3) Comparar objetos con == esperando comparar contenido (CAPA 4).

        4) Asignar null a variables "para liberar memoria", o guardar
           objetos en colecciones que nunca se limpian (CAPA 5).

        5) Guardar Map.get(...) en un int sin revisar null (CAPA 8).

        6) Llenar el código de if (x != null) en lugar de validar en la
           frontera y no devolver null (CAPA 9).

        7) Devolver null en lugar de colecciones vacías (CAPA 9).

        8) Revisar isEmpty() antes que null: value.isEmpty() || value == null
           lanza NPE (CAPA 10).

        9) Leer solo "NullPointerException" e ignorar el resto del mensaje,
           que dice exactamente qué era null (CAPA 7).

        10) Mezclar null y vacío para significar lo mismo (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Primitivos guardan el valor; referencias guardan la dirección de
          un objeto en el heap.
        - Asignar una referencia comparte el objeto (aliasing); los cambios
          se ven desde todas las variables.
        - Copias: Arrays.copyOf, new ArrayList<>(...): superficiales. Los
          objetos inmutables evitan la necesidad de copias profundas.
        - == compara direcciones; equals, contenido.
        - Un objeto sin referencias lo libera el recolector; uno que sigue
          referenciado nunca.
        - null: la referencia vacía. Solo en tipos de referencia. Valor por
          defecto de campos y arreglos.
        - NPE al usar null; el mensaje dice exactamente qué era null.
        - Defensa: requireNonNull en la frontera, no devolver null,
          helpers de Objects, colecciones que rechazan null.
        - null, "" y "   " son distintos; elige una representación.
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
