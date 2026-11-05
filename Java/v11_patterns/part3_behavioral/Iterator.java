package v11_patterns.part3_behavioral;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/*
    =====================================================================
    ITERATOR: RECORRER SIN EXPONER CÓMO ESTÁN GUARDADAS LAS COSAS
    =====================================================================

    Ruta de patterns/: guía 16 de 28  (part3_behavioral)
    Anterior:  part3_behavioral/Interpreter
    Siguiente: part3_behavioral/Mediator

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: MEDIA. El mecanismo ya lo dominas
    (v5_collections/part1_basics/Iterators: hasNext/next, for-each,
    ConcurrentModificationException, tu propio Iterable). Esta guía se
    queda con lo que el PATRÓN agrega en el diseño.

    Índice:
        CAPA 1)  Qué agrega el patrón
        CAPA 2)  Recorrer una estructura propia: un árbol de categorías
        CAPA 3)  Iteradores perezosos: traer páginas solo cuando hacen falta
        CAPA 4)  De iterador a Stream
        CAPA 5)  ¿Devolver List, Iterable o Stream?
        CAPA 6)  Errores comunes
        RESUMEN FINAL
*/
public class Iterator {

    public static void main(String[] args) {
        check(treeIteratorExample(), "CAPA 2");
        check(lazyPagesExample(), "CAPA 3");
        check(streamExample(), "CAPA 4");
        System.out.println("Iterator: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ AGREGA EL PATRÓN
        =================================================================

        ITERATOR: "Proporciona una forma de acceder secuencialmente a los
        elementos de un objeto agregado SIN EXPONER su representación
        interna."

        En colecciones ya lo viste: tu for-each funciona igual sobre un
        ArrayList, un HashSet o un TreeSet, aunque por dentro sean un
        arreglo, cajones o un árbol. Como patrón de DISEÑO, la misma idea
        sirve para TUS estructuras y fuentes de datos:
        - una estructura propia (un árbol de categorías) que quieres
          recorrer con un for-each, sin que nadie sepa que es un árbol
          (CAPA 2);
        - una fuente que entrega los datos POR PARTES (una API con
          paginación, una consulta grande, un archivo enorme), recorrida
          como si fuera una sola secuencia (CAPA 3).

        La regla de diseño: la estructura decide CÓMO se recorre; quien la
        usa solo dice QUÉ hacer con cada elemento.
    */

    /*
        =================================================================
        CAPA 2) RECORRER UNA ESTRUCTURA PROPIA: UN ÁRBOL DE CATEGORÍAS
        =================================================================

        El catálogo tiene un árbol de categorías (un Composite;
        part2_structural/Composite):

            Tecnología
             ├─ Computadoras
             │   └─ Laptops
             └─ Accesorios

        El menú del sitio necesita recorrerlas TODAS, en orden "de arriba
        hacia abajo" (cada categoría seguida de sus subcategorías: un
        recorrido en profundidad). Si cada pantalla que las necesita escribe
        su propia recursión, todas conocen la forma del árbol.

        Con un iterador, el árbol es Iterable<Category>: quien lo usa
        escribe un for-each y nada más. El recorrido vive en UN lugar, con
        una pila propia (ArrayDeque) en vez de recursión, así que aguanta
        cualquier profundidad (Composite, CAPA 8). Si mañana el árbol se
        guarda distinto, solo cambia el iterador.
    */
    record Category(String name, List<Category> children) {
        Category(String name, Category... children) {
            this(name, List.of(children));
        }
    }

    static final class CategoryTree implements Iterable<Category> {
        private final Category root;

        CategoryTree(Category root) {
            this.root = root;
        }

        @Override
        public java.util.Iterator<Category> iterator() {    // uno NUEVO por recorrido (Iterators, CAPA 10)
            Deque<Category> pending = new ArrayDeque<>();
            pending.push(root);
            return new java.util.Iterator<>() {
                @Override
                public boolean hasNext() {
                    return !pending.isEmpty();
                }

                @Override
                public Category next() {
                    if (pending.isEmpty()) {
                        throw new NoSuchElementException();
                    }
                    Category current = pending.pop();
                    List<Category> children = current.children();
                    for (int i = children.size() - 1; i >= 0; i--) {   // al revés, para que salgan en orden
                        pending.push(children.get(i));
                    }
                    return current;
                }
            };
        }
    }

    static final Category CATALOG = new Category("Tecnología",
            new Category("Computadoras", new Category("Laptops")),
            new Category("Accesorios"));

    static boolean treeIteratorExample() {
        List<String> menu = new ArrayList<>();
        for (Category category : new CategoryTree(CATALOG)) {   // nadie aquí sabe que es un árbol
            menu.add(category.name());
        }
        return menu.equals(List.of("Tecnología", "Computadoras", "Laptops", "Accesorios"));
    }

    /*
        =================================================================
        CAPA 3) ITERADORES PEREZOSOS: TRAER PÁGINAS SOLO CUANDO HACEN FALTA
        =================================================================

        Una API de pedidos devuelve los resultados por PÁGINAS (de 2 en 2
        en el ejemplo; en la realidad, de 50 o 100): para tener todos hay
        que pedir la página 1, la 2, la 3... Cada página es una petición
        por la red (v8_apis/part2_io_and_network/HttpClientUsage).

        Si una función "trae todo" a una lista primero, hace TODAS las
        peticiones aunque quien llama solo necesite los primeros
        resultados, y guarda todo en memoria.

        Un iterador PEREZOSO pide la siguiente página solo cuando se acabó
        la actual y alguien pide otro elemento. Así:
        - quien lo usa recorre "todos los pedidos" como una sola secuencia,
          sin saber que hay páginas;
        - si deja de recorrer a la mitad (encontró lo que buscaba), las
          páginas siguientes NUNCA se piden;
        - en memoria solo hay una página a la vez.

        El ejemplo cuenta las peticiones: buscar el primer pedido de más de
        $1,000 se resuelve con 2 peticiones en vez de 4.
    */
    record Order(String id, int total) { }

    static final class OrdersApi {                          // simula una API remota paginada
        private final List<Order> all = List.of(
                new Order("A-1", 300), new Order("A-2", 450), new Order("A-3", 1_200),
                new Order("A-4", 80), new Order("A-5", 990), new Order("A-6", 60),
                new Order("A-7", 2_500), new Order("A-8", 15));
        int requests;

        List<Order> page(int number, int size) {            // cada llamada sería una petición HTTP
            requests++;
            int from = number * size;
            return from >= all.size() ? List.of() : all.subList(from, Math.min(from + size, all.size()));
        }
    }

    static final class PagedOrders implements Iterable<Order> {
        private final OrdersApi api;
        private final int pageSize;

        PagedOrders(OrdersApi api, int pageSize) {
            this.api = api;
            this.pageSize = pageSize;
        }

        @Override
        public java.util.Iterator<Order> iterator() {
            return new java.util.Iterator<>() {
                private int nextPage = 0;
                private java.util.Iterator<Order> current = List.<Order>of().iterator();
                private boolean exhausted = false;

                @Override
                public boolean hasNext() {
                    while (!current.hasNext() && !exhausted) {
                        List<Order> page = api.page(nextPage++, pageSize);   // pide la siguiente SOLO ahora
                        exhausted = page.size() < pageSize;
                        current = page.iterator();
                    }
                    return current.hasNext();
                }

                @Override
                public Order next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return current.next();
                }
            };
        }
    }

    static boolean lazyPagesExample() {
        OrdersApi api = new OrdersApi();
        Order firstBig = null;
        for (Order order : new PagedOrders(api, 2)) {
            if (order.total() > 1_000) {
                firstBig = order;
                break;                                      // dejar de recorrer: no se piden más páginas
            }
        }
        boolean stoppedEarly = firstBig != null && firstBig.id().equals("A-3") && api.requests == 2;

        OrdersApi fullApi = new OrdersApi();
        int count = 0;
        for (Order ignored : new PagedOrders(fullApi, 2)) {
            count++;
        }
        boolean allWhenNeeded = count == 8 && fullApi.requests == 5;   // 4 páginas llenas + 1 vacía que confirma el final

        return stoppedEarly && allWhenNeeded;
    }

    /*
        =================================================================
        CAPA 4) DE ITERADOR A STREAM
        =================================================================

        Hoy quien recibe una secuencia muchas veces quiere usar streams
        (filter, map, findFirst; v7_functional/part2_streams/StreamsBasics).
        Cualquier Iterable se convierte en un Stream que CONSERVA la pereza:

            StreamSupport.stream(iterable.spliterator(), false)

        (spliterator() es la versión del iterador que usan los streams; el
        false significa "no paralelo".) Como los streams también son
        perezosos y se detienen al tener la respuesta (StreamsBasics,
        CAPA 6), el ahorro de peticiones de la CAPA 3 se mantiene.
    */
    static Stream<Order> orders(OrdersApi api) {
        return StreamSupport.stream(new PagedOrders(api, 2).spliterator(), false);
    }

    static boolean streamExample() {
        OrdersApi api = new OrdersApi();
        List<String> firstTwoBig = orders(api)
                .filter(order -> order.total() > 900)
                .map(Order::id)
                .limit(2)
                .toList();
        return firstTwoBig.equals(List.of("A-3", "A-5")) && api.requests == 3;   // se detuvo en la página 3
    }

    /*
        =================================================================
        CAPA 5) ¿DEVOLVER LIST, ITERABLE O STREAM?
        =================================================================

        Cuando un método tuyo entrega "varios elementos", elige según su
        naturaleza:

        | Devuelve         | Cuándo                                                        |
        |------------------|---------------------------------------------------------------|
        | List (inmutable) | pocos elementos, ya calculados, que se pueden recorrer varias |
        |                  | veces, contar, indexar. Es lo más común y lo más simple       |
        | Iterable         | una estructura propia o una fuente perezosa que se puede      |
        |                  | recorrer VARIAS veces (cada for-each pide un iterador nuevo)  |
        | Stream           | una secuencia perezosa pensada para filtrar/transformar, de   |
        |                  | un solo uso (StreamsBasics, CAPA 7)                           |

        Un caso especial importante: si recorrer mantiene un RECURSO abierto
        (un archivo, un cursor de base de datos, una conexión), devuelve un
        Stream que el que llama cierra con try-with-resources, como hace
        Files.lines (v8_apis/part2_io_and_network/FilesAndIO, CAPA 11). Un
        Iterator no tiene forma de avisar "ya terminé, libera el recurso" si
        el que recorre se detiene a la mitad.
    */

    /*
        =================================================================
        CAPA 6) ERRORES COMUNES
        =================================================================

        1) Exponer la estructura interna (la lista de hijos, el arreglo)
           para que cada cliente la recorra a su manera (CAPAS 1 y 2).

        2) Traer TODAS las páginas a una lista cuando quien llama podría
           detenerse antes (CAPA 3).

        3) Un iterator() que devuelve siempre el mismo iterador: el segundo
           recorrido no ve nada (Iterators, CAPA 10).

        4) Un hasNext() que AVANZA o hace trabajo cada vez que se llama sin
           control: debe poder llamarse varias veces sin cambiar el
           resultado (el del ejemplo solo pide una página cuando la actual
           se terminó) (CAPA 3).

        5) Recorridos que mantienen recursos abiertos sin forma de cerrarlos
           (CAPA 5).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Iterator: recorrer elementos sin exponer cómo están guardados; la
          estructura decide el CÓMO.
        - Para tus estructuras: implementa Iterable y el for-each funciona
          (un árbol recorrido con una pila propia).
        - Iteradores perezosos: piden datos (páginas) solo cuando hacen
          falta; detenerse antes ahorra peticiones y memoria.
        - StreamSupport.stream(iterable.spliterator(), false) lo convierte
          en Stream sin perder la pereza.
        - List si son pocos y ya están; Iterable/Stream si son perezosos;
          Stream con try-with-resources si hay un recurso abierto.
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
