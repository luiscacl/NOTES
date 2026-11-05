package v1_fundamentals.part4_data_and_input;

import java.util.List;

/*
    =====================================================================
    ARREGLOS (ARRAYS) — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 12 de 14  (part4_data_and_input)
    Anterior:  part3_text_and_references/Wrappers
    Siguiente: part4_data_and_input/Numbers

    Nota: esta clase se llama Arrays, igual que la clase utilitaria
    java.util.Arrays. Por eso, dentro de este archivo, la utilitaria se
    escribe con su nombre completo: java.util.Arrays.sort(...). En tu
    código normal la importarías y escribirías Arrays.sort(...).

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es un arreglo y por qué su tamaño es fijo
        CAPA 2)  Crear arreglos
        CAPA 3)  Leer y escribir posiciones
        CAPA 4)  "Agrandar" un arreglo: por qué existe ArrayList
        CAPA 5)  La clase utilitaria java.util.Arrays
        CAPA 6)  binarySearch: buscar rápido en un arreglo ordenado
        CAPA 7)  Arrays.asList y sus dos trampas
        CAPA 8)  Copiar arreglos
        CAPA 9)  Matrices: arreglos de arreglos
        CAPA 10) Covarianza: un error que solo aparece al ejecutar
        CAPA 11) Arreglo o List: cómo elegir
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class Arrays {

    public static void main(String[] args) {
        check(fixedSizeExample(), "CAPA 1");
        check(creationExample(), "CAPA 2");
        check(accessExample(), "CAPA 3");
        check(growingExample(), "CAPA 4");
        check(utilitiesExample(), "CAPA 5");
        check(binarySearchExample(), "CAPA 6");
        check(asListExample(), "CAPA 7");
        check(copyExample(), "CAPA 8");
        check(matrixExample(), "CAPA 9");
        check(covarianceExample(), "CAPA 10");
        System.out.println("Arrays: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN ARREGLO Y POR QUÉ SU TAMAÑO ES FIJO
        =================================================================

        Un ARREGLO (array) guarda VARIOS valores del MISMO tipo, uno
        detrás de otro, bajo un solo nombre. Cada valor ocupa una
        POSICIÓN numerada desde 0.

            int[] scores = {90, 85, 70};
            "int[]" se lee "arreglo de int" (como decía tu antigua TypingGuide)

            posición:   0    1    2
                      [ 90 | 85 | 70 ]     length = 3

        Sus tres características, y el POR QUÉ de cada una:

        1) TAMAÑO FIJO: se decide al crearlo y no cambia nunca.
        2) UN SOLO TIPO: todos los elementos son del tipo declarado.
        3) ACCESO INSTANTÁNEO a cualquier posición, sin importar el tamaño.

        Las tres vienen de lo mismo: los elementos se guardan JUNTOS en un
        bloque CONTINUO de memoria, y todos ocupan lo mismo (4 bytes cada
        int). Para encontrar la posición i, la JVM no busca: CALCULA la
        dirección:
            dirección del elemento i = inicio del arreglo + i x tamaño del elemento
        Una multiplicación y una suma, igual para la posición 0 que para la
        1 000 000. Por eso es tan rápido... y por eso no puede crecer: la
        memoria justo después del bloque puede estar ocupada por otra cosa.

        Un arreglo es un OBJETO (vive en el heap y la variable guarda su
        referencia; ReferencesAndNull), aunque sea de primitivos.

        Si vienes de JavaScript: en JS un arreglo crece solo y puede
        mezclar tipos. Eso en Java es una List (v5_collections/Lists), no un
        arreglo.
    */
    static boolean fixedSizeExample() {
        int[] scores = {90, 85, 70};
        Object asObject = scores;   // un arreglo es un objeto

        return scores.length == 3 && scores[0] == 90 && scores[2] == 70 && asObject instanceof int[];
    }

    /*
        =================================================================
        CAPA 2) CREAR ARREGLOS
        =================================================================

        Tres formas:

        1) Con valores conocidos (solo al declarar):
               int[] primes = {2, 3, 5, 7};

        2) Con un tamaño, y se llena después:
               String[] names = new String[3];
           Cada posición arranca con el valor por defecto del tipo
           (0, 0.0, false, '\u0000' o null), como los campos de una clase.

        3) Con new y valores, cuando NO estás declarando (por ejemplo, al
           pasarlo a un método o reasignar):
               primes = new int[] {11, 13};
           La forma corta {...} solo se permite en la declaración:
               primes = {11, 13};   // ERROR: illegal start of expression

        length: el tamaño del arreglo. Es un CAMPO, no un método: va SIN
        paréntesis. Y cada tipo usa su propio nombre, una fuente constante
        de confusión:
            arreglo.length          (campo)
            texto.length()          (método de String)
            lista.size()            (método de List)
        Escribir arreglo.length() no compila (cannot find symbol).

        Java también acepta int scores[] (estilo de C), pero el estilo de
        Java es int[] scores: el tipo completo queda junto.
    */
    static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    static boolean creationExample() {
        int[] primes = {2, 3, 5, 7};

        String[] names = new String[3];
        boolean[] flags = new boolean[2];
        double[] prices = new double[2];

        primes = new int[] {11, 13};               // reasignar: hace falta new int[]
        int inlineSum = sum(new int[] {1, 2, 3});  // pasarlo a un método: también

        return names[0] == null && !flags[1] && prices[0] == 0.0
                && primes.length == 2 && inlineSum == 6;
    }

    /*
        =================================================================
        CAPA 3) LEER Y ESCRIBIR POSICIONES
        =================================================================

            arreglo[i]          lee la posición i
            arreglo[i] = valor  escribe en la posición i

        Las posiciones válidas van de 0 a length - 1. Cualquier otra
        lanza ArrayIndexOutOfBoundsException (Loops, CAPA 5). Java
        revisa CADA acceso: es una de las razones por las que es un
        lenguaje "seguro en memoria" (en C, leer fuera del arreglo lee
        memoria ajena sin avisar, y es la causa de muchos ataques
        informáticos).

        Recorrerlo: for clásico si necesitas la posición, for-each si no
        (Loops, CAPAS 4 y 6). Los patrones de suma, máximo y búsqueda de
        tu antigua JavaDataStructuresGuide están en Loops, CAPA 10; aquí la
        búsqueda lineal, que devuelve la POSICIÓN o -1:
    */
    static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return -1;
    }

    static boolean accessExample() {
        int[] nums = {1, 2, 3, 4};
        nums[1] = 99;                              // escribir

        boolean outOfBounds;
        try {
            nums[nums.length] = 5;                 // la posición 4 no existe
            outOfBounds = false;
        } catch (ArrayIndexOutOfBoundsException e) {
            outOfBounds = true;
        }

        return nums[1] == 99 && nums[nums.length - 1] == 4      // el último: length - 1
                && indexOf(nums, 3) == 2 && indexOf(nums, 42) == -1
                && outOfBounds;
    }

    /*
        =================================================================
        CAPA 4) "AGRANDAR" UN ARREGLO: POR QUÉ EXISTE ARRAYLIST
        =================================================================

        Un arreglo no puede crecer (CAPA 1). Si necesitas un elemento
        más, la única forma es CREAR UN ARREGLO NUEVO más grande y copiar
        todo:
            int[] bigger = java.util.Arrays.copyOf(original, original.length + 1);

        Si haces eso cada vez que agregas un elemento, agregar n elementos
        cuesta copiar 1 + 2 + 3 + ... + n: el mismo problema cuadrático
        de concatenar Strings (Strings, CAPA 9).

        ArrayList resuelve exactamente esto: por dentro TIENE un arreglo,
        pero con espacio de sobra; cuando se llena, crea uno nuevo de 1.5
        veces el tamaño y copia. Así, copiar es raro y agregar es rápido en
        promedio (v5_collections/Lists). Por eso en código normal, cuando el
        tamaño cambia, se usa List y no arreglos.
    */
    static int[] append(int[] original, int value) {
        int[] bigger = java.util.Arrays.copyOf(original, original.length + 1);   // nuevo arreglo, una posición más
        bigger[original.length] = value;
        return bigger;
    }

    static boolean growingExample() {
        int[] numbers = {1, 2};
        int[] grown = append(numbers, 3);

        return numbers.length == 2                // el original NO cambió de tamaño
                && grown.length == 3 && grown[2] == 3
                && grown != numbers;              // es otro objeto
    }

    /*
        =================================================================
        CAPA 5) LA CLASE UTILITARIA JAVA.UTIL.ARRAYS
        =================================================================

        Los arreglos casi no tienen métodos propios (solo length y clone).
        Todo lo útil está en la clase utilitaria java.util.Arrays
        (Static, CAPA 5):

        | Método                         | Hace                                          |
        |--------------------------------|-----------------------------------------------|
        | toString(arr)                  | "[1, 2, 3]" para imprimir/depurar             |
        | sort(arr)                      | ordena (de menor a mayor)                     |
        | fill(arr, valor)               | pone el mismo valor en todas las posiciones   |
        | equals(a, b)                   | true si tienen el mismo contenido             |
        | copyOf(arr, nuevoTamaño)       | copia, recortando o rellenando con defaults   |
        | copyOfRange(arr, desde, hasta) | copia un rango (hasta excluido)               |
        | binarySearch(arr, x)           | busca en un arreglo ORDENADO (CAPA 6)         |
        | asList(...)                    | lo ve como List (CAPA 7)                      |
        | stream(arr)                    | lo convierte en stream (v7_functional/)           |

        Dos trampas que esta tabla resuelve:
        1) Imprimir un arreglo directamente NO muestra su contenido:
           muestra algo como "[I@1b6d3586" (el toString de Object:
           [I significa "arreglo de int"). Usa Arrays.toString.
        2) a.equals(b) en un arreglo compara IDENTIDAD (si es el mismo
           objeto), no contenido, porque los arreglos no sobrescriben
           equals. Usa Arrays.equals(a, b).

        Sobre sort: con primitivos usa un algoritmo muy rápido (Dual-Pivot
        Quicksort). Solo ordena de menor a mayor; para otro orden con
        objetos se le pasa un Comparator (ObjectContract, CAPA 10).
    */
    static boolean utilitiesExample() {
        int[] numbers = {7, 2, 9, 1, 5};
        int[] sameContent = {7, 2, 9, 1, 5};

        boolean printingDirectlyIsUseless = String.valueOf(numbers).startsWith("[I@");
        boolean toStringWorks = java.util.Arrays.toString(numbers).equals("[7, 2, 9, 1, 5]");

        boolean equalsMethodIsIdentity = !numbers.equals(sameContent);              // compara si es el mismo objeto
        boolean arraysEqualsComparesContent = java.util.Arrays.equals(numbers, sameContent);

        java.util.Arrays.sort(numbers);
        boolean sorted = java.util.Arrays.equals(numbers, new int[] {1, 2, 5, 7, 9});

        int[] filled = new int[4];
        java.util.Arrays.fill(filled, 8);

        int[] middle = java.util.Arrays.copyOfRange(numbers, 1, 4);   // posiciones 1, 2 y 3

        String[] words = {"pera", "Manzana", "uva"};
        java.util.Arrays.sort(words);                                 // orden de Unicode: mayúsculas primero

        return printingDirectlyIsUseless && toStringWorks
                && equalsMethodIsIdentity && arraysEqualsComparesContent
                && sorted
                && java.util.Arrays.toString(filled).equals("[8, 8, 8, 8]")
                && java.util.Arrays.equals(middle, new int[] {2, 5, 7})
                && words[0].equals("Manzana");
    }

    /*
        =================================================================
        CAPA 6) BINARYSEARCH: BUSCAR RÁPIDO EN UN ARREGLO ORDENADO
        =================================================================

        La búsqueda lineal (CAPA 3) revisa uno por uno: con un millón de
        elementos, hasta un millón de comparaciones.

        Si el arreglo está ORDENADO hay algo mucho mejor: la BÚSQUEDA
        BINARIA. Mira el elemento del centro; si lo que buscas es menor,
        descarta toda la mitad derecha, si es mayor, la izquierda. Repite
        con la mitad que queda. Cada paso descarta la MITAD:
            1 000 000 -> 500 000 -> 250 000 -> ... -> 1
        Un millón de elementos se resuelve en unos 20 pasos.
        (Se implementa a mano en algorithms/BinarySearch.)

        Arrays.binarySearch(arr, x):
        - Si lo encuentra, devuelve su posición.
        - Si NO, devuelve un número NEGATIVO que además dice dónde
          debería insertarse para mantener el orden:
              resultado = -(puntoDeInserción) - 1
          (El -1 existe porque, si no, "insertar en la posición 0" daría
          -0, que es lo mismo que 0, y se confundiría con "encontrado en
          la posición 0".)
        - REQUISITO: el arreglo DEBE estar ordenado. Si no lo está, el
          resultado es basura, sin ningún error.
    */
    static boolean binarySearchExample() {
        int[] sorted = {1, 3, 5, 7, 9};

        int found = java.util.Arrays.binarySearch(sorted, 7);
        int missing = java.util.Arrays.binarySearch(sorted, 4);   // iría en la posición 2
        int insertionPoint = -missing - 1;

        return found == 3 && missing == -3 && insertionPoint == 2;
    }

    /*
        =================================================================
        CAPA 7) ARRAYS.ASLIST Y SUS DOS TRAMPAS
        =================================================================

        Arrays.asList(...) te da una List a partir de un arreglo o de una
        lista de valores (tu antiguo CheatSheet lo usaba con contains). Es útil,
        pero tiene dos trampas que conviene conocer:

        TRAMPA 1: NO es una copia, es una VISTA del arreglo.
        - Cambiar un elemento de la lista (set) cambia el arreglo, y
          viceversa: comparten la misma memoria.
        - Su tamaño es FIJO (es el del arreglo): add y remove lanzan
          UnsupportedOperationException.
        Si quieres una lista independiente que pueda crecer:
            new ArrayList<>(Arrays.asList(...))   o   new ArrayList<>(List.of(...))

        TRAMPA 2: con un arreglo de PRIMITIVOS no hace lo que esperas.
        asList(new int[] {1, 2, 3}) NO da una lista de 3 números: da una
        lista con UN elemento, que es el arreglo completo. Motivo: asList
        recibe objetos (es genérico, y los genéricos no aceptan
        primitivos; Wrappers, CAPA 1), y el único objeto ahí es el int[].
        Con Integer[] sí funciona.
    */
    static boolean asListExample() {
        String[] backing = {"a", "b", "c"};
        List<String> view = java.util.Arrays.asList(backing);

        view.set(0, "X");                          // escribe en el arreglo
        boolean writesThrough = backing[0].equals("X");

        boolean fixedSize;
        try {
            view.add("d");
            fixedSize = false;
        } catch (UnsupportedOperationException e) {
            fixedSize = true;
        }

        int[] primitives = {1, 2, 3};
        boolean primitiveTrap = java.util.Arrays.asList(primitives).size() == 1;   // ¡un solo elemento: el arreglo!

        Integer[] boxed = {1, 2, 3};
        boolean boxedWorks = java.util.Arrays.asList(boxed).size() == 3;

        return writesThrough && fixedSize && primitiveTrap && boxedWorks;
    }

    /*
        =================================================================
        CAPA 8) COPIAR ARREGLOS
        =================================================================

        Un arreglo es un objeto: arr2 = arr1 NO copia, comparte
        (ReferencesAndNull, CAPA 2). Para una copia real hay varias formas:

            arr.clone()                              copia completa (la más simple)
            Arrays.copyOf(arr, n)                    copia completa, o recorta/rellena a n
            Arrays.copyOfRange(arr, desde, hasta)    una parte
            System.arraycopy(origen, desdeO, destino, desdeD, cuántos)
                                                     copia dentro de un arreglo ya existente;
                                                     la más rápida, la que usan Arrays.copyOf
                                                     y copyOfRange

        Todas son SUPERFICIALES: en un arreglo de objetos se copian las
        referencias, no los objetos (ReferencesAndNull, CAPA 3).
    */
    static boolean copyExample() {
        int[] original = {1, 2, 3};

        int[] cloned = original.clone();
        cloned[0] = 100;

        int[] padded = java.util.Arrays.copyOf(original, 5);      // rellena con 0
        int[] truncated = java.util.Arrays.copyOf(original, 2);   // recorta

        int[] destination = new int[5];
        System.arraycopy(original, 0, destination, 1, 3);         // copia los 3 a partir de la posición 1

        return original[0] == 1
                && java.util.Arrays.equals(padded, new int[] {1, 2, 3, 0, 0})
                && java.util.Arrays.equals(truncated, new int[] {1, 2})
                && java.util.Arrays.equals(destination, new int[] {0, 1, 2, 3, 0});
    }

    /*
        =================================================================
        CAPA 9) MATRICES: ARREGLOS DE ARREGLOS
        =================================================================

        Java no tiene matrices "de verdad": una matriz es un ARREGLO DE
        ARREGLOS. int[][] se lee "arreglo de arreglos de int".

            int[][] grid = {
                {1, 2, 3},      <- grid[0], es un int[]
                {4, 5, 6}       <- grid[1]
            };
            grid[1][2] es la fila 1, columna 2: el 6

        - grid.length       = número de FILAS
        - grid[i].length    = número de COLUMNAS de la fila i
        - new int[3][4]     crea 3 filas de 4 columnas, todo en 0

        Como cada fila es un arreglo independiente:
        1) Las filas pueden tener DISTINTO tamaño ("jagged", irregulares),
           como en tu antigua JavaDataStructuresGuide.
        2) Puedes crear solo las filas y llenar las columnas después:
           new int[3][] crea 3 filas en null.
        3) Arrays.toString no muestra los números de una matriz: usa
           Arrays.deepToString.

        Para recorrerla: dos ciclos anidados, filas afuera y columnas
        adentro (Loops, CAPA 8). Usa siempre grid[row].length (no el
        tamaño de la primera fila) para que funcione con filas
        irregulares.
    */
    static int[][] transpose(int[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        int[][] result = new int[cols][rows];          // las filas se vuelven columnas
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                result[col][row] = matrix[row][col];
            }
        }
        return result;
    }

    static boolean matrixExample() {
        int[][] grid = {
                {1, 2, 3},
                {4, 5, 6}
        };

        int total = 0;
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                total += grid[row][col];
            }
        }

        int[][] jagged = {{10, 20}, {30, 40, 50}, {60}};
        int[][] rowsOnly = new int[3][];

        return grid[1][2] == 6 && grid.length == 2 && grid[0].length == 3
                && total == 21
                && java.util.Arrays.deepToString(transpose(grid)).equals("[[1, 4], [2, 5], [3, 6]]")
                && jagged[1].length == 3 && jagged[2].length == 1
                && rowsOnly[0] == null;
    }

    /*
        =================================================================
        CAPA 10) COVARIANZA: UN ERROR QUE SOLO APARECE AL EJECUTAR
        =================================================================

        Como String "ES UN" Object, Java permite tratar un String[] como
        un Object[]. Esto se llama que los arreglos son COVARIANTES.

        El problema: a través de la vista Object[] el compilador te deja
        meter cualquier objeto... pero el arreglo REAL sigue siendo de
        String. La JVM lo revisa al ejecutar y lanza ArrayStoreException.

            Object[] objects = new String[2];
            objects[0] = 42;          // compila, pero al ejecutar: ArrayStoreException

        Es un error de diseño histórico de Java (de antes de los
        genéricos). Los genéricos lo corrigieron: List<String> NO se puede
        tratar como List<Object>, y el error aparece al COMPILAR, que es
        mucho mejor (v4_generics/AdvancedGenerics). Es una razón más para
        preferir List en código normal.
    */
    static boolean covarianceExample() {
        Object[] objects = new String[2];   // permitido: String[] "es un" Object[]
        objects[0] = "texto";               // bien: es un String

        boolean storeRejected;
        try {
            objects[1] = Integer.valueOf(42);   // compila... y falla al ejecutar
            storeRejected = false;
        } catch (ArrayStoreException e) {
            storeRejected = e.getMessage().equals("java.lang.Integer");
        }

        return storeRejected;
    }

    /*
        =================================================================
        CAPA 11) ARREGLO O LIST: CÓMO ELEGIR
        =================================================================

        | Usa ARREGLO cuando                          | Usa LIST cuando                       |
        |---------------------------------------------|---------------------------------------|
        | El tamaño es fijo y conocido                | El tamaño cambia (agregar/quitar)     |
        | Son muchos primitivos y el rendimiento o la | Quieres los métodos de colección      |
        |   memoria importan (int[] vs List<Integer>) |   (contains, remove, sort, streams)   |
        | Matrices, tableros, buffers de bytes        | Código de negocio en general          |
        | Algoritmos y entrevistas                    | Lo devuelves en una API               |
        | Una API lo exige (main(String[] args),      |                                       |
        |   varargs, que son arreglos: Methods, CAPA 7)|                                      |

        En código profesional, la mayoría de las veces se usa List. Los
        arreglos aparecen en algoritmos, en código de bajo nivel y donde
        una API los exige.
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Acceder a la posición length (o usar <= length en un for)
           (CAPA 3).

        2) Escribir length() o size() en un arreglo (CAPA 2).

        3) Asignar {1, 2} a una variable ya declarada sin new int[]
           (CAPA 2).

        4) Esperar que un arreglo crezca (CAPA 4).

        5) Imprimir un arreglo directamente y ver "[I@..." (CAPA 5).

        6) Comparar arreglos con == o con a.equals(b) (CAPA 5).

        7) Usar binarySearch en un arreglo sin ordenar (CAPA 6).

        8) Hacer add a una lista de Arrays.asList, o usar asList con un
           int[] (CAPA 7).

        9) Creer que arr2 = arr1 copia el arreglo (CAPA 8).

        10) Usar Arrays.toString con una matriz en lugar de deepToString
            (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Arreglo: tamaño fijo, un solo tipo, posiciones desde 0. Todo
          viene de guardarse en un bloque continuo: acceso instantáneo,
          pero no puede crecer. Es un objeto.
        - Crear: {valores} al declarar, new Tipo[n] (valores por defecto)
          o new Tipo[] {valores}. length es un campo.
        - Posición inválida: ArrayIndexOutOfBoundsException.
        - Para crecer hay que copiar a uno nuevo: por eso existe ArrayList.
        - java.util.Arrays: toString, sort, fill, equals, copyOf,
          binarySearch (ordenado; negativo = punto de inserción).
        - asList: vista de tamaño fijo; no sirve con int[].
        - Copias: clone, copyOf, copyOfRange, System.arraycopy; todas
          superficiales.
        - Matriz = arreglo de arreglos; filas pueden ser irregulares;
          deepToString.
        - Los arreglos son covariantes: ArrayStoreException al ejecutar.
        - En código normal, prefiere List.
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
