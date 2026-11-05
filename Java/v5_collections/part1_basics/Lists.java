package v5_collections.part1_basics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.List;

/*
    =====================================================================
    LISTAS: ARRAYLIST Y LINKEDLIST — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v5_collections/: guía 2 de 10  (part1_basics)
    Anterior:  part1_basics/CollectionsOverview
    Siguiente: part1_basics/Iterators

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es una List
        CAPA 2)  ArrayList por dentro: un arreglo que crece solo
        CAPA 3)  Las operaciones de una lista
        CAPA 4)  Por qué insertar al inicio es lento en ArrayList
        CAPA 5)  LinkedList por dentro: nodos enlazados
        CAPA 6)  ArrayList vs LinkedList: la respuesta real
        CAPA 7)  Recorrer una lista (y la trampa con LinkedList)
        CAPA 8)  subList: una vista, no una copia
        CAPA 9)  Listas de objetos
        CAPA 10) Igualdad, conversiones y ordenamiento
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Lists {

    public static void main(String[] args) {
        check(listBasicsExample(), "CAPA 1");
        check(arrayListGrowthExample(), "CAPA 2");
        check(operationsExample(), "CAPA 3");
        check(shiftingExample(), "CAPA 4");
        check(linkedListExample(), "CAPA 5");
        check(iterationTrapExample(), "CAPA 7");
        check(subListExample(), "CAPA 8");
        check(listOfObjectsExample(), "CAPA 9");
        check(equalityAndConversionExample(), "CAPA 10");
        System.out.println("Lists: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UNA LIST
        =================================================================

        List es la interface de las SECUENCIAS (CollectionsOverview,
        CAPA 3). Tres características la definen:
        1) ORDEN: los elementos quedan en el orden en que los pusiste, y
           así se recorren.
        2) POSICIONES: cada elemento tiene un índice desde 0, como en un
           arreglo. Puedes pedir, cambiar, insertar o quitar en una
           posición.
        3) REPETIDOS: el mismo valor puede aparecer varias veces.

        A diferencia de un arreglo, CRECE Y SE ENCOGE sola, y tiene
        métodos para todo.

        Dos detalles de tipos (Wrappers y GenericsBasics):
        - List<int> no existe: se usa List<Integer>, y el autoboxing
          convierte solo al agregar y al leer.
        - Se declara con la interface: List<String> names = new ArrayList<>();

        Implementaciones principales:
        - ArrayList   -> la que vas a usar el 95 % de las veces
        - LinkedList  -> casi nunca (CAPA 6 explica por qué)
        - List.of(...) -> lista INMUTABLE (part4_tools/ImmutableCollections)
    */
    static boolean listBasicsExample() {
        List<Integer> numbers = new ArrayList<>();
        numbers.add(10);
        numbers.add(10);                    // repetidos permitidos
        numbers.add(5);
        int first = numbers.get(0);         // unboxing automático de Integer a int

        return numbers.size() == 3 && first == 10 && numbers.get(2) == 5;
    }

    /*
        =================================================================
        CAPA 2) ARRAYLIST POR DENTRO: UN ARREGLO QUE CRECE SOLO
        =================================================================

        Entender cómo funciona por dentro explica TODO su comportamiento.

        Un ArrayList guarda sus elementos en un ARREGLO normal
        (v1_fundamentals/.../Arrays), pero más grande de lo necesario. Lleva
        dos números:
        - SIZE: cuántos elementos hay de verdad (lo que devuelve size()).
        - CAPACITY: cuántos caben en el arreglo interno antes de llenarse.

            size = 3, capacity = 10
            [ "a" | "b" | "c" |     |     |     |     |     |     |     ]

        Al hacer add al final:
        - Si hay espacio, pone el elemento en la siguiente posición:
          instantáneo.
        - Si está lleno, crea un arreglo NUEVO de 1.5 veces el tamaño,
          COPIA todo y luego agrega. Esto es lento, pero pasa RARA vez:
          cada vez que crece, tarda más en volver a llenarse.

        Por eso agregar al final es "O(1) amortizado": casi siempre
        instantáneo, y las copias ocasionales, repartidas entre todos los
        add, cuestan muy poco en promedio. Es el arreglo que "crece solo"
        del que hablaba v1_fundamentals/.../Arrays, CAPA 4.

        Y por eso get(i) es instantáneo: es un acceso a un arreglo, que
        CALCULA la dirección de la posición i (Arrays, CAPA 1).

        Optimización: si sabes de antemano cuántos elementos vas a
        agregar, dale esa capacidad inicial y te ahorras todas las copias:
            new ArrayList<>(10_000)
        (Ojo: eso NO crea 10 000 elementos; size sigue siendo 0. Solo
        reserva espacio.)
    */
    static boolean arrayListGrowthExample() {
        List<Integer> growing = new ArrayList<>();          // capacidad inicial pequeña: crecerá varias veces
        List<Integer> presized = new ArrayList<>(10_000);   // espacio reservado: no copiará nunca

        for (int i = 0; i < 10_000; i++) {
            growing.add(i);
            presized.add(i);
        }

        return growing.size() == 10_000 && growing.equals(presized)
                && new ArrayList<Integer>(500).isEmpty();      // capacidad no es tamaño
    }

    /*
        =================================================================
        CAPA 3) LAS OPERACIONES DE UNA LISTA
        =================================================================

        | Método                 | Hace                                            | Costo en ArrayList |
        |------------------------|-------------------------------------------------|--------------------|
        | add(x)                 | agrega al final                                 | O(1) amortizado    |
        | add(i, x)              | inserta en la posición i (recorre los demás)    | O(n)               |
        | get(i)                 | el elemento en la posición i                    | O(1)               |
        | set(i, x)              | reemplaza el de la posición i; DEVUELVE el viejo| O(1)               |
        | remove(int i)          | quita la posición i; devuelve lo quitado        | O(n)               |
        | remove(Object x)       | quita la primera aparición de x; devuelve boolean| O(n)              |
        | indexOf(x)             | primera posición de x, o -1                     | O(n)               |
        | contains(x)            | ¿está x?                                        | O(n)               |
        | size(), isEmpty()      | tamaño                                          | O(1)               |
        | clear()                | vacía la lista                                  | O(n)               |

        Recuerda la trampa de Wrappers (CAPA 6), que tu antiguo CheatSheet marcaba:
        en una List<Integer>, remove(1) quita la POSICIÓN 1; para quitar
        el NÚMERO 1 se escribe remove(Integer.valueOf(1)).

        Posiciones fuera de rango lanzan IndexOutOfBoundsException.

        La secuencia de tu antigua JavaDataStructuresGuide, paso por paso:
    */
    static boolean operationsExample() {
        List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30));

        int replaced = numbers.set(1, 99);   // [10, 99, 30]; set devuelve el valor que había (20)
        numbers.remove(0);                   // [99, 30]: remove(int) quita por POSICIÓN
        numbers.add(1, 777);                 // [99, 777, 30]: los siguientes se recorren a la derecha

        List<Integer> values = new ArrayList<>(List.of(5, 3, 1, 4));
        values.remove(Integer.valueOf(3));   // quita el VALOR 3, no la posición 3

        boolean outOfRange;
        try {
            numbers.get(3);
            outOfRange = false;
        } catch (IndexOutOfBoundsException e) {
            outOfRange = true;
        }

        return replaced == 20
                && numbers.equals(List.of(99, 777, 30))
                && numbers.indexOf(30) == 2 && numbers.indexOf(5) == -1
                && values.equals(List.of(5, 1, 4))
                && outOfRange;
    }

    /*
        =================================================================
        CAPA 4) POR QUÉ INSERTAR AL INICIO ES LENTO EN ARRAYLIST
        =================================================================

        En un arreglo no hay "huecos": los elementos están pegados desde la
        posición 0. Así que para insertar en la posición 0, el ArrayList
        tiene que MOVER todos los demás una posición a la derecha para
        abrir espacio. Y al quitar la posición 0, moverlos todos a la
        izquierda para cerrar el hueco.

            add(0, "x") en [a, b, c, d]:
                [ a | b | c | d |   ]
                   ↘   ↘   ↘   ↘          4 movimientos
                [ x | a | b | c | d ]

        Con n elementos son n movimientos: O(n). Hacerlo una vez no
        importa. Hacerlo dentro de un ciclo (por ejemplo, usar una lista
        como fila, quitando siempre el primero) da O(n²): con 100 000
        elementos son unos 5 000 MILLONES de movimientos.

        La regla: en ArrayList, agrega y quita AL FINAL. Si necesitas
        sacar siempre del inicio (una fila), usa ArrayDeque
        (part3_queues/QueuesAndStacks), que lo hace en O(1).
    */
    static boolean shiftingExample() {
        List<String> letters = new ArrayList<>(List.of("a", "b", "c", "d"));
        letters.add(0, "x");                                   // todos se recorren a la derecha
        boolean shiftedRight = letters.equals(List.of("x", "a", "b", "c", "d"));

        letters.remove(0);                                     // todos se recorren a la izquierda
        return shiftedRight && letters.equals(List.of("a", "b", "c", "d"));
    }

    /*
        =================================================================
        CAPA 5) LINKEDLIST POR DENTRO: NODOS ENLAZADOS
        =================================================================

        Una LinkedList NO usa un arreglo. Cada elemento vive en un NODO
        aparte, que guarda el valor y dos referencias: al nodo anterior y
        al siguiente. La lista solo sabe dónde están el primero y el
        último.

            first                                         last
              ↓                                             ↓
            [ a ] ⇄ [ b ] ⇄ [ c ] ⇄ [ d ]
           cada nodo: valor + referencia al anterior + referencia al siguiente

        Consecuencias, todas por esa estructura:
        - Agregar o quitar en los EXTREMOS es O(1): solo se cambian un par
          de referencias; nada se mueve.
        - get(i) es O(n): no hay forma de calcular dónde está el nodo i;
          hay que caminar desde un extremo, nodo por nodo.
        - Insertar en medio es O(1) SOLO si ya estás parado en ese nodo
          (con un iterador). Llegar hasta ahí cuesta O(n).
        - Cada elemento ocupa mucho más memoria: además del valor, un
          objeto nodo con dos referencias (unas 3-4 veces más que en un
          ArrayList).

        LinkedList implementa List Y Deque, así que tiene addFirst,
        removeFirst, addLast, removeLast...
    */
    static boolean linkedListExample() {
        LinkedList<String> line = new LinkedList<>(List.of("b", "c"));
        line.addFirst("a");              // O(1): solo cambia la referencia del primero
        line.addLast("d");               // O(1)
        String removed = line.removeFirst();

        return removed.equals("a") && line.equals(List.of("b", "c", "d")) && line.getLast().equals("d");
    }

    /*
        =================================================================
        CAPA 6) ARRAYLIST VS LINKEDLIST: LA RESPUESTA REAL
        =================================================================

        En los libros de teoría se lee: "LinkedList es mejor para insertar
        y quitar". En la práctica, ArrayList gana casi SIEMPRE, incluso en
        muchos casos donde la teoría favorece a LinkedList. Por qué:

        1) MEMORIA CONTINUA: los procesadores modernos leen la memoria en
           bloques y la guardan en una memoria muy rápida (caché). Un
           arreglo está todo junto: leer un elemento trae los siguientes
           "gratis". Los nodos de una LinkedList están dispersos por la
           memoria: cada salto al siguiente nodo puede ser lento.
        2) MOVER ELEMENTOS ES MUY RÁPIDO: el "O(n)" de ArrayList al
           insertar es una copia de memoria en bloque (System.arraycopy),
           que el procesador hace a velocidad enorme. El "O(n)" de
           LinkedList para LLEGAR a la posición es caminar nodo por nodo.
        3) MENOS MEMORIA y menos trabajo para el recolector de basura.

        Hasta el autor de LinkedList (Joshua Bloch) dijo públicamente que
        nunca la usa.

        Regla práctica:
        - Lista            -> ArrayList
        - Fila o pila      -> ArrayDeque (no LinkedList)
        - LinkedList       -> casi nunca; solo si mides y de verdad gana
    */

    /*
        =================================================================
        CAPA 7) RECORRER UNA LISTA (Y LA TRAMPA CON LINKEDLIST)
        =================================================================

        Formas de recorrer:
            for (int i = 0; i < list.size(); i++) { list.get(i) }   con posición
            for (String s : list) { }                               sin posición (preferida)
            Iterator<String> it = list.iterator(); ...               para quitar al recorrer (Iterators)
            list.forEach(s -> ...)                                   con una lambda (v7_functional/)

        LA TRAMPA: el for con get(i) sobre una LinkedList. Cada get(i)
        camina hasta i desde el extremo más cercano (el inicio o el final).
        Recorrer n elementos así cuesta del orden de n²/4 pasos: O(n²). Con
        100 000 elementos son unos 2 500 millones de pasos, contra 100 000
        con un for-each.

        El for-each no tiene ese problema: usa un ITERADOR, que recuerda
        en qué nodo va y avanza al siguiente en O(1). Por eso, si no
        necesitas la posición, usa siempre for-each: es correcto y rápido
        con cualquier lista.
    */
    static long sumWithIndex(List<Integer> list) {
        long sum = 0;
        for (int i = 0; i < list.size(); i++) {
            sum += list.get(i);          // con LinkedList, cada get(i) camina desde un extremo
        }
        return sum;
    }

    static long sumWithForEach(List<Integer> list) {
        long sum = 0;
        for (int value : list) {          // el iterador avanza nodo a nodo: O(n) total
            sum += value;
        }
        return sum;
    }

    static boolean iterationTrapExample() {
        List<Integer> linked = new LinkedList<>();
        for (int i = 1; i <= 2_000; i++) {
            linked.add(i);
        }
        // Mismo resultado; con miles de elementos, la versión con índice hace millones de pasos de más.
        return sumWithIndex(linked) == 2_001_000 && sumWithForEach(linked) == 2_001_000;
    }

    /*
        =================================================================
        CAPA 8) SUBLIST: UNA VISTA, NO UNA COPIA
        =================================================================

        list.subList(desde, hasta) devuelve las posiciones [desde, hasta)
        (hasta excluido, como substring). Pero NO es una lista nueva: es
        una VISTA sobre la original, como Arrays.asList
        (v1_fundamentals/.../Arrays, CAPA 7).

        - Cambiar algo a través de la vista cambia la original.
        - Tiene un uso muy elegante: borrar un rango de una sola vez.
              list.subList(1, 3).clear();   // borra las posiciones 1 y 2
        - PELIGRO: si después cambias el TAMAÑO de la lista original (add,
          remove), la vista queda inválida y usarla lanza
          ConcurrentModificationException.

        Si quieres una lista independiente, cópiala:
            new ArrayList<>(list.subList(desde, hasta))
    */
    static boolean subListExample() {
        List<String> letters = new ArrayList<>(List.of("a", "b", "c", "d", "e"));

        letters.subList(1, 3).clear();                                // borra "b" y "c" de la original
        boolean rangeRemoved = letters.equals(List.of("a", "d", "e"));

        List<String> view = letters.subList(0, 2);
        view.set(0, "A");                                              // escribe en la original
        boolean writesThrough = letters.get(0).equals("A");

        List<String> copy = new ArrayList<>(letters.subList(0, 2));   // independiente

        letters.add("f");                                              // la original cambió de tamaño...
        boolean viewInvalidated;
        try {
            view.size();                                               // ...y la vista ya no sirve
            viewInvalidated = false;
        } catch (ConcurrentModificationException e) {
            viewInvalidated = true;
        }

        return rangeRemoved && writesThrough && viewInvalidated && copy.equals(List.of("A", "d"));
    }

    /*
        =================================================================
        CAPA 9) LISTAS DE OBJETOS
        =================================================================

        Como decía tu OOP.java, las listas aparecen en todos lados al
        modelar con objetos: un pedido TIENE una lista de artículos, una
        escuela una lista de maestros. Lo que hay que recordar:

        1) La lista guarda REFERENCIAS a los objetos, no copias
           (v1_fundamentals/.../ReferencesAndNull). Modificar un objeto que
           está en la lista se ve desde la lista.

        2) contains, indexOf y remove(Object) usan EQUALS. Con una clase
           sin equals, contains(new Producto(...)) siempre da false. Con un
           record funciona (v2_oop/part4_advanced_types/ObjectContract).

        3) Si un objeto TIENE una lista como parte de su estado, no la
           expongas tal cual: devuelve una copia o una vista de solo
           lectura (v2_oop/part2_pillars/Encapsulation, CAPA 8).
    */
    record Item(String name, int quantity) { }

    static final class Order {
        private final List<Item> items = new ArrayList<>();

        void add(Item item) {
            items.add(item);
        }

        boolean contains(Item item) {
            return items.contains(item);              // usa equals: funciona porque Item es un record
        }

        List<Item> getItems() {
            return Collections.unmodifiableList(items);   // nadie de fuera puede modificar la lista interna
        }

        int totalUnits() {
            int total = 0;
            for (Item item : items) {
                total += item.quantity();
            }
            return total;
        }
    }

    static boolean listOfObjectsExample() {
        Order order = new Order();
        order.add(new Item("teclado", 1));
        order.add(new Item("cable", 3));

        boolean findsEqualObject = order.contains(new Item("cable", 3));   // otro objeto, mismos datos

        boolean protectedList;
        try {
            order.getItems().clear();
            protectedList = false;
        } catch (UnsupportedOperationException e) {
            protectedList = true;
        }

        return findsEqualObject && protectedList && order.totalUnits() == 4;
    }

    /*
        =================================================================
        CAPA 10) IGUALDAD, CONVERSIONES Y ORDENAMIENTO
        =================================================================

        IGUALDAD: dos listas son iguales (equals) si tienen los MISMOS
        elementos en el MISMO orden, sin importar la implementación. Un
        ArrayList y una LinkedList con [1, 2, 3] son iguales. Es el
        contrato de List.

        CONVERSIONES:
            List.of(a, b, c)                    lista inmutable
            new ArrayList<>(List.of(a, b, c))   lista modificable con esos valores
            Arrays.asList(arreglo)              vista de tamaño fijo (ojo: Arrays, CAPA 7)
            list.toArray(String[]::new)         lista -> arreglo

        ORDENAR (part4_tools/Sorting lo trata a fondo):
            list.sort(null)                            orden natural
            list.sort(Comparator.reverseOrder())       al revés
            Collections.sort(list)                     equivalente a list.sort(null)
        Y la advertencia de tu antiguo CheatSheet, que es un bug real documentado:
        NUNCA compares con (a, b) -> b - a. Con números extremos la resta
        se desborda y el orden sale mal; usa Integer.compare
        (ObjectContract, CAPA 9).
    */
    static boolean equalityAndConversionExample() {
        List<Integer> array = new ArrayList<>(List.of(1, 2, 3));
        List<Integer> linked = new LinkedList<>(List.of(1, 2, 3));
        boolean equalAcrossImplementations = array.equals(linked);

        String[] asArray = List.of("x", "y").toArray(String[]::new);
        List<String> fromArray = new ArrayList<>(Arrays.asList(asArray));
        fromArray.add("z");                                            // modificable: es una copia

        List<Integer> sorted = new ArrayList<>(List.of(5, 3, Integer.MIN_VALUE, 4));
        sorted.sort((a, b) -> Integer.compare(b, a));                  // descendente, sin restas
        boolean descending = sorted.equals(List.of(5, 4, 3, Integer.MIN_VALUE));

        return equalAcrossImplementations && fromArray.equals(List.of("x", "y", "z")) && descending;
    }

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Escribir List<int> en lugar de List<Integer> (CAPA 1).

        2) Confundir capacidad con tamaño: new ArrayList<>(10) está vacío
           (CAPA 2).

        3) list.remove(1) en una List<Integer> esperando quitar el número
           1 (CAPA 3).

        4) Usar un ArrayList como fila, quitando siempre el primero: O(n²)
           (CAPA 4). Usa ArrayDeque.

        5) Elegir LinkedList "porque insertar es O(1)" (CAPA 6).

        6) Recorrer una LinkedList con for e índice: O(n²) (CAPA 7).

        7) Guardar un subList y seguir modificando la original (CAPA 8).

        8) contains con objetos sin equals (CAPA 9).

        9) Devolver la lista interna de un objeto tal cual (CAPA 9).

        10) Ordenar con una resta en el Comparator (CAPA 10).

        11) Quitar elementos dentro de un for-each: ver part1_basics/Iterators.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - List: orden, posiciones desde 0, repetidos. Se declara con la
          interface.
        - ArrayList: un arreglo que crece 1.5 veces cuando se llena.
          get/set y add al final: rápidos. Insertar o quitar al inicio o en
          medio: O(n), porque hay que mover elementos.
        - LinkedList: nodos enlazados. Extremos O(1), get(i) O(n), más
          memoria. En la práctica casi siempre gana ArrayList.
        - Recorre con for-each; nunca for con get(i) sobre LinkedList.
        - subList es una vista: sirve para borrar rangos; cópiala si la vas
          a guardar.
        - Con objetos: guarda referencias, contains usa equals; no expongas
          la lista interna.
        - Dos listas son iguales si tienen los mismos elementos en el mismo
          orden. Ordena con Integer.compare, nunca con restas.
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
