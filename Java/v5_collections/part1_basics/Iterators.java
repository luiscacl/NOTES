package v5_collections.part1_basics;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;

/*
    =====================================================================
    ITERADORES — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v5_collections/: guía 3 de 10  (part1_basics)
    Anterior:  part1_basics/Lists
    Siguiente: part2_sets_and_maps/Sets

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es un iterador y por qué existe
        CAPA 2)  Lo que hace en realidad el for-each
        CAPA 3)  El contrato de Iterator
        CAPA 4)  Quitar elementos mientras recorres
        CAPA 5)  ConcurrentModificationException: por qué existe
        CAPA 6)  La detección no está garantizada: el caso del penúltimo
        CAPA 7)  removeIf: la forma moderna
        CAPA 8)  ListIterator: recorrer en ambos sentidos y modificar
        CAPA 9)  Iterar y modificar un Map
        CAPA 10) Hacer que tu propia clase funcione con for-each
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Iterators {

    public static void main(String[] args) {
        check(manualIterationExample(), "CAPA 2");
        check(contractExample(), "CAPA 3");
        check(iteratorRemoveExample(), "CAPA 4");
        check(concurrentModificationExample(), "CAPA 5");
        check(penultimateTrapExample(), "CAPA 6");
        check(removeIfExample(), "CAPA 7");
        check(listIteratorExample(), "CAPA 8");
        check(mapIterationExample(), "CAPA 9");
        check(customIterableExample(), "CAPA 10");
        System.out.println("Iterators: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN ITERADOR Y POR QUÉ EXISTE
        =================================================================

        Cada colección guarda sus elementos de forma MUY distinta:
        - ArrayList, en un arreglo continuo;
        - LinkedList, en nodos enlazados;
        - HashSet, en "cajones" calculados con el hashCode;
        - TreeSet, en un árbol.

        Si para recorrer cada una tuvieras que saber cómo está hecha por
        dentro, tu código dependería de esos detalles. El ITERADOR resuelve
        eso: es un objeto que sabe recorrer UNA colección concreta, y que
        ofrece la MISMA forma de hacerlo para todas:

            interface Iterator<E> {
                boolean hasNext();   // ¿queda alguno?
                E next();            // dame el siguiente (y avanza)
                void remove();       // quita el último que me diste (opcional)
            }

        Y una colección que se puede recorrer implementa Iterable:

            interface Iterable<E> {
                Iterator<E> iterator();   // dame un iterador nuevo, desde el principio
            }

        Es abstracción pura (v2_oop/part2_pillars/Abstraction): tu código
        dice QUÉ hacer con cada elemento, y cada colección decide CÓMO
        llegar al siguiente. Un iterador de LinkedList recuerda en qué
        nodo va; uno de ArrayList, en qué posición; uno de TreeSet, en qué
        rama del árbol.

        Cada iterador es un recorrido INDEPENDIENTE y de UN SOLO USO: no se
        puede "rebobinar". Para volver a empezar se pide otro con
        iterator().
    */

    /*
        =================================================================
        CAPA 2) LO QUE HACE EN REALIDAD EL FOR-EACH
        =================================================================

        El for-each es una abreviatura que el COMPILADOR traduce a un
        iterador. Esto que escribes:

            for (String name : names) {
                ... usar name ...
            }

        el compilador lo convierte en:

            Iterator<String> it = names.iterator();
            while (it.hasNext()) {
                String name = it.next();
                ... usar name ...
            }

        Por eso el for-each funciona con CUALQUIER cosa que implemente
        Iterable, incluidas tus propias clases (CAPA 10). (Con arreglos,
        que no son Iterable, el compilador lo traduce a un for con índice.)

        Y por eso tiene sus límites: como el iterador está escondido, no
        puedes llamar a su remove() (CAPA 4) ni saber en qué posición vas.
    */
    static boolean manualIterationExample() {
        List<String> names = List.of("Ana", "Luis", "Mia");

        StringBuilder withForEach = new StringBuilder();
        for (String name : names) {
            withForEach.append(name).append(' ');
        }

        StringBuilder withIterator = new StringBuilder();
        Iterator<String> it = names.iterator();        // exactamente lo que genera el compilador
        while (it.hasNext()) {
            String name = it.next();
            withIterator.append(name).append(' ');
        }

        return withForEach.toString().equals(withIterator.toString())
                && withForEach.toString().equals("Ana Luis Mia ");
    }

    /*
        =================================================================
        CAPA 3) EL CONTRATO DE ITERATOR
        =================================================================

        Las reglas que debes respetar al usar un iterador a mano:

        1) hasNext() SOLO pregunta: no avanza. Puedes llamarlo las veces que
           quieras.
        2) next() devuelve el siguiente Y avanza. Llamarlo cuando ya no hay
           más lanza NoSuchElementException. Por eso siempre va precedido
           de hasNext().
        3) remove() quita el ÚLTIMO elemento que devolvió next(). Solo se
           puede llamar UNA vez por cada next(); llamarlo antes del primer
           next() o dos veces seguidas lanza IllegalStateException.
           (Porque no hay "último elemento devuelto" que quitar.)
        4) remove() es OPCIONAL: las colecciones inmutables (List.of)
           lanzan UnsupportedOperationException.

        Un error clásico: llamar next() dos veces en la misma vuelta (por
        ejemplo, una en el if y otra al usar el valor). Cada llamada AVANZA,
        así que te saltas elementos. Guarda el resultado en una variable.
    */
    static boolean contractExample() {
        Iterator<String> it = new ArrayList<>(List.of("x")).iterator();

        boolean removeBeforeNextFails;
        try {
            it.remove();
            removeBeforeNextFails = false;
        } catch (IllegalStateException e) {
            removeBeforeNextFails = true;
        }

        boolean hasNextDoesNotAdvance = it.hasNext() && it.hasNext() && it.hasNext();
        it.next();
        it.remove();

        boolean removeTwiceFails;
        try {
            it.remove();
            removeTwiceFails = false;
        } catch (IllegalStateException e) {
            removeTwiceFails = true;
        }

        boolean nextPastEndFails;
        try {
            it.next();
            nextPastEndFails = false;
        } catch (NoSuchElementException e) {
            nextPastEndFails = true;
        }

        return removeBeforeNextFails && hasNextDoesNotAdvance && removeTwiceFails && nextPastEndFails;
    }

    /*
        =================================================================
        CAPA 4) QUITAR ELEMENTOS MIENTRAS RECORRES
        =================================================================

        Necesidad muy común: recorrer una colección y quitar los elementos
        que cumplan algo (productos vencidos, pedidos cancelados).

        Hacerlo con list.remove(x) dentro de un for-each falla (CAPA 5).
        La forma correcta "clásica" es usar el iterador explícitamente y
        llamar a SU remove():

            Iterator<Product> it = products.iterator();
            while (it.hasNext()) {
                if (it.next().isExpired()) {
                    it.remove();      // el iterador quita y sabe ajustar su posición
                }
            }

        Por qué funciona: el iterador es quien está recorriendo, así que
        cuando quita un elemento ajusta su propia posición y su registro
        interno. Nada lo toma por sorpresa.
    */
    record Product(String name, int daysToExpire) {
        boolean isExpired() {
            return daysToExpire <= 0;
        }
    }

    static boolean iteratorRemoveExample() {
        List<Product> products = new ArrayList<>(List.of(
                new Product("leche", 0), new Product("pan", 2), new Product("yogur", -1), new Product("queso", 10)));

        Iterator<Product> it = products.iterator();
        while (it.hasNext()) {
            Product product = it.next();            // UNA llamada a next() por vuelta, guardada en variable
            if (product.isExpired()) {
                it.remove();
            }
        }

        return products.equals(List.of(new Product("pan", 2), new Product("queso", 10)));
    }

    /*
        =================================================================
        CAPA 5) CONCURRENTMODIFICATIONEXCEPTION: POR QUÉ EXISTE
        =================================================================

        Si modificas la colección por OTRO camino (list.remove, list.add)
        mientras un iterador la recorre, el iterador queda "perdido": su
        posición ya no corresponde a los datos (los elementos se movieron,
        el tamaño cambió). Podría saltarse elementos, repetirlos o leer
        fuera de rango.

        Para detectarlo, las colecciones de java.util llevan un CONTADOR
        de modificaciones estructurales (modCount). Al crearse, el iterador
        anota ese número; en cada next() compara. Si cambió, alguien
        modificó la colección por fuera, y lanza
        ConcurrentModificationException en lugar de seguir con datos
        inconsistentes. A esto se le llama comportamiento "fail-fast"
        (falla rápido).

        Aclaración del nombre: "Concurrent" NO significa que haya varios
        hilos. Pasa con uno solo; "concurrente" se refiere a que la
        modificación ocurrió MIENTRAS el recorrido estaba en curso.
    */
    static boolean concurrentModificationExample() {
        List<String> names = new ArrayList<>(List.of("Ana", "Luis", "Mia"));
        try {
            for (String name : names) {
                if (name.equals("Ana")) {
                    names.remove(name);     // modificación por fuera del iterador
                }
            }
            return false;
        } catch (ConcurrentModificationException e) {
            return true;                    // el iterador lo detectó en el siguiente next()
        }
    }

    /*
        =================================================================
        CAPA 6) LA DETECCIÓN NO ESTÁ GARANTIZADA: EL CASO DEL PENÚLTIMO
        =================================================================

        La documentación de Java advierte que el fail-fast es de "mejor
        esfuerzo": NO garantiza que siempre detecte el problema. Hay un
        caso clásico que lo demuestra.

        Si quitas el PENÚLTIMO elemento dentro de un for-each:
        - hasNext() del iterador de ArrayList pregunta "¿mi posición es
          distinta del tamaño?".
        - Después de visitar "b" (posición 1), la posición pasa a 2. Al
          quitar "b", el tamaño baja de 3 a 2.
        - hasNext() ve posición 2 y tamaño 2: "no hay más". El ciclo
          TERMINA, sin llamar a next(), así que nunca llega a la revisión
          que lanzaría la excepción.

        Resultado: NO hay excepción, y "c" NUNCA SE VISITA. Es peor que la
        excepción: el error pasa en silencio.

        La lección: no confíes en ConcurrentModificationException para
        encontrar este error. La regla es simple: nunca modifiques una
        colección por fuera mientras la recorres.
    */
    static boolean penultimateTrapExample() {
        List<String> letters = new ArrayList<>(List.of("a", "b", "c"));
        List<String> visited = new ArrayList<>();

        for (String letter : letters) {
            visited.add(letter);
            if (letter.equals("b")) {
                letters.remove(letter);     // el penúltimo: el ciclo termina sin avisar
            }
        }

        return visited.equals(List.of("a", "b"))       // "c" nunca se visitó
                && letters.equals(List.of("a", "c"));  // y nadie lanzó ninguna excepción
    }

    /*
        =================================================================
        CAPA 7) REMOVEIF: LA FORMA MODERNA
        =================================================================

        Desde Java 8, toda Collection tiene removeIf(condición): quita
        todos los elementos que cumplan la condición, en una línea.

            products.removeIf(Product::isExpired);
            numbers.removeIf(n -> n < 0);

        (La condición es una lambda o una referencia a método:
        v7_functional/part1_lambdas/Lambdas.)

        Por qué preferirlo, además de ser más corto:
        - No puedes equivocarte con el iterador.
        - En ArrayList es MÁS RÁPIDO: el iterador quita uno por uno, y
          cada remove recorre los elementos siguientes (Lists, CAPA 4): en
          el peor caso O(n²). removeIf primero marca cuáles se van y luego
          compacta el arreglo una sola vez: O(n).
        - Devuelve true si quitó algo.
    */
    static boolean removeIfExample() {
        List<Product> products = new ArrayList<>(List.of(
                new Product("leche", 0), new Product("pan", 2), new Product("yogur", -1)));

        boolean removedSomething = products.removeIf(Product::isExpired);
        boolean removedNothing = products.removeIf(Product::isExpired);   // ya no hay vencidos

        return removedSomething && !removedNothing && products.equals(List.of(new Product("pan", 2)));
    }

    /*
        =================================================================
        CAPA 8) LISTITERATOR: RECORRER EN AMBOS SENTIDOS Y MODIFICAR
        =================================================================

        Las listas ofrecen un iterador más poderoso, ListIterator
        (list.listIterator()), que además de lo normal permite:

            hasPrevious(), previous()   ir hacia atrás
            nextIndex(), previousIndex() saber la posición
            set(x)                      REEMPLAZAR el último elemento devuelto
            add(x)                      INSERTAR en la posición actual

        Sirve para modificar una lista "en el lugar" mientras la recorres,
        sin crear otra: normalizar valores, insertar algo después de cierto
        elemento. (Para reemplazar TODOS con una función existe también
        list.replaceAll(x -> ...).)
    */
    static boolean listIteratorExample() {
        List<String> tags = new ArrayList<>(List.of("java", "Spring", "SQL"));

        ListIterator<String> it = tags.listIterator();
        while (it.hasNext()) {
            String tag = it.next();
            it.set(tag.toLowerCase());          // reemplaza en el lugar
            if (tag.equals("Spring")) {
                it.add("spring-boot");          // inserta justo después
            }
        }

        // Recorrer hacia atrás desde el final.
        StringBuilder backwards = new StringBuilder();
        ListIterator<String> back = tags.listIterator(tags.size());
        while (back.hasPrevious()) {
            backwards.append(back.previous()).append(' ');
        }

        return tags.equals(List.of("java", "spring", "spring-boot", "sql"))
                && backwards.toString().equals("sql spring-boot spring java ");
    }

    /*
        =================================================================
        CAPA 9) ITERAR Y MODIFICAR UN MAP
        =================================================================

        Un Map no es Iterable, pero ofrece tres VISTAS que sí lo son
        (CollectionsOverview, CAPA 7):
            map.keySet()     las claves
            map.values()     los valores
            map.entrySet()   los pares (Map.Entry)

        Son vistas: están conectadas al mapa. Por eso:
        - Quitar a través de ellas quita del mapa:
              map.entrySet().removeIf(entry -> entry.getValue() == 0);
              map.values().removeIf(v -> v == 0);        // lo mismo
          O con el iterador de entrySet() y su remove().
        - entry.setValue(nuevo) cambia el valor EN el mapa, sin buscar la
          clave otra vez.
        - map.remove(clave) dentro de un for-each sobre el mapa da el mismo
          ConcurrentModificationException de las listas.
    */
    static boolean mapIterationExample() {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("teclado", 15);
        stock.put("mouse", 0);
        stock.put("monitor", 4);
        stock.put("cable", 0);

        Iterator<Map.Entry<String, Integer>> it = stock.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            if (entry.getValue() == 0) {
                it.remove();                            // quita el par del mapa
            } else {
                entry.setValue(entry.getValue() * 2);   // cambia el valor en el mapa
            }
        }

        return stock.equals(Map.of("teclado", 30, "monitor", 8));
    }

    /*
        =================================================================
        CAPA 10) HACER QUE TU PROPIA CLASE FUNCIONE CON FOR-EACH
        =================================================================

        Como el for-each solo necesita un Iterable (CAPA 2), cualquier
        clase tuya puede usarse en él: implementa Iterable<T> y devuelve
        un Iterator<T> nuevo en cada llamada a iterator().

        Ejemplo: un rango de números (como 1..5) que NO guarda los números
        en una lista: los va calculando a medida que se piden. Es una
        ventaja de los iteradores: pueden recorrer cosas que ni siquiera
        existen todas juntas en memoria (un rango de un millón de números
        ocupa lo mismo que uno de cinco).

        El iterador es una clase interna (v2_oop/part3_interfaces_and_relationships/
        NestedClasses, CAPA 3) porque necesita los datos del rango que lo
        creó.
    */
    static final class Range implements Iterable<Integer> {
        private final int start;
        private final int endInclusive;

        Range(int start, int endInclusive) {
            this.start = start;
            this.endInclusive = endInclusive;
        }

        @Override
        public Iterator<Integer> iterator() {
            return new RangeIterator();             // uno NUEVO cada vez: cada for-each empieza desde el inicio
        }

        private final class RangeIterator implements Iterator<Integer> {
            private int next = start;

            @Override
            public boolean hasNext() {
                return next <= endInclusive;
            }

            @Override
            public Integer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("El rango terminó en " + endInclusive);
                }
                return next++;                       // calcula el valor: no hay ninguna lista guardada
            }
        }
    }

    static boolean customIterableExample() {
        Range oneToFive = new Range(1, 5);

        int sum = 0;
        for (int n : oneToFive) {                   // tu clase, en un for-each
            sum += n;
        }

        int secondPass = 0;
        for (int n : oneToFive) {                   // un segundo recorrido empieza desde el inicio
            secondPass += n;
        }

        long hugeCount = 0;
        for (int n : new Range(1, 1_000_000)) {     // un millón de números sin guardar ninguno
            hugeCount++;
        }

        return sum == 15 && secondPass == 15 && hugeCount == 1_000_000;
    }

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Quitar o agregar con list.remove/add dentro de un for-each
           (CAPA 5).

        2) Creer que, si no hubo ConcurrentModificationException, no hubo
           error: el caso del penúltimo pasa en silencio (CAPA 6).

        3) Llamar next() más de una vez por vuelta (CAPA 3).

        4) Llamar it.remove() sin next() antes, o dos veces (CAPA 3).

        5) Reusar un iterador que ya terminó, esperando que vuelva a
           empezar (CAPA 1).

        6) Quitar uno por uno con el iterador en un ArrayList grande cuando
           removeIf lo hace en O(n) (CAPA 7).

        7) Buscar la clave otra vez (map.put) en lugar de entry.setValue al
           recorrer un mapa (CAPA 9).

        8) Un iterator() propio que devuelve siempre el MISMO iterador: el
           segundo for-each no recorre nada (CAPA 10).

        9) Creer que "Concurrent" en la excepción significa varios hilos
           (CAPA 5).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Iterator: la forma uniforme de recorrer cualquier colección:
          hasNext(), next(), remove(). Iterable: "dame un iterador".
        - El for-each es un iterador escondido que genera el compilador.
        - hasNext no avanza; next avanza (NoSuchElementException al final);
          remove quita el último devuelto, una vez por next.
        - Para quitar mientras recorres: it.remove() o, mejor, removeIf.
        - Modificar por fuera durante el recorrido: ConcurrentModification-
          Exception (fail-fast), aunque NO siempre se detecta.
        - ListIterator: hacia atrás, set y add en el lugar.
        - Map: recorre entrySet; quita con su iterador o removeIf; cambia
          con setValue.
        - Tu clase en un for-each: implementa Iterable y devuelve un
          iterador nuevo cada vez.
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
