package v11_patterns.part2_structural;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

/*
    =====================================================================
    COMPOSITE: TRATAR IGUAL A UNO Y A UN GRUPO (ESTRUCTURAS DE ÁRBOL)
    =====================================================================

    Ruta de patterns/: guía 8 de 28  (part2_structural)
    Anterior:  part2_structural/Bridge
    Siguiente: part2_structural/Decorator

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: MEDIA-ALTA. Cada vez que algo puede CONTENER cosas de su
    mismo tipo (carpetas, menús, categorías, paquetes, organigramas),
    aparece este patrón.

    Índice:
        CAPA 1)  El problema: cosas que contienen cosas
        CAPA 2)  La solución: una interface para la hoja y para el grupo
        CAPA 3)  La estructura y la recursión
        CAPA 4)  Más operaciones sobre el árbol
        CAPA 5)  La versión moderna: sealed + records + switch
        CAPA 6)  Dónde van add y remove
        CAPA 7)  Árboles, no ciclos: la recursión y la pila del hilo
        CAPA 8)  Recorrer sin recursión: para árboles muy profundos
        CAPA 9)  Dónde aparece
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class Composite {

    public static void main(String[] args) {
        check(instanceofProblemExample(), "CAPA 1");
        check(compositeExample(), "CAPA 2");
        check(treeOperationsExample(), "CAPA 4");
        check(sealedExample(), "CAPA 5");
        check(cycleExample(), "CAPA 7");
        check(iterativeExample(), "CAPA 8");
        System.out.println("Composite: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: COSAS QUE CONTIENEN COSAS
        =================================================================

        La tienda vende productos sueltos y PAQUETES. Un paquete contiene
        productos... y también puede contener otros paquetes:

            "Kit home office"  (10% de descuento)
             ├─ teclado            500
             ├─ mouse              250
             └─ "Kit de cables"   (sin descuento)
                 ├─ cable HDMI     150
                 └─ cable USB-C    100

        ¿Cuánto cuesta el kit? Hay que sumar lo de adentro, y si adentro
        hay otro kit, sumar lo de ADENTRO de ese, y así. Es una estructura
        de ÁRBOL: cada nodo es una HOJA (un producto) o un GRUPO (un
        paquete con hijos).

        Sin un diseño para esto, el código que recorre el árbol pregunta
        a cada rato "¿esto es un producto o un paquete?":
            if (item instanceof Product p) ... else if (item instanceof Bundle b) ...
        y esa pregunta se repite en el precio, en el peso, en contar
        productos, en buscar... (el switch por tipo repetido;
        v10_design/part2_solid/OpenClosed, CAPA 3).
    */
    record PlainProduct(String name, BigDecimal price) { }

    record PlainBundle(String name, List<Object> items, BigDecimal discount) { }   // Object: puede ser cualquiera de los dos

    static BigDecimal priceWithInstanceof(Object item) {
        if (item instanceof PlainProduct product) {
            return product.price();
        } else if (item instanceof PlainBundle bundle) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Object child : bundle.items()) {
                sum = sum.add(priceWithInstanceof(child));  // y este if se repite en cada operación
            }
            return sum.subtract(sum.multiply(bundle.discount()));
        }
        throw new IllegalArgumentException("Tipo desconocido: " + item);   // nada impide meter otra cosa
    }

    static boolean instanceofProblemExample() {
        PlainBundle cables = new PlainBundle("Kit de cables",
                List.of(new PlainProduct("HDMI", new BigDecimal("150")), new PlainProduct("USB-C", new BigDecimal("100"))), BigDecimal.ZERO);
        PlainBundle office = new PlainBundle("Kit home office",
                List.of(new PlainProduct("teclado", new BigDecimal("500")), new PlainProduct("mouse", new BigDecimal("250")), cables),
                new BigDecimal("0.10"));
        return priceWithInstanceof(office).compareTo(new BigDecimal("900")) == 0;   // (500+250+250) - 10%
    }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: UNA INTERFACE PARA LA HOJA Y PARA EL GRUPO
        =================================================================

        COMPOSITE: "Compone objetos en estructuras de árbol para
        representar jerarquías parte-todo. Permite a los clientes tratar
        igual a los objetos individuales y a las composiciones de objetos."

        - Una interface común, CatalogItem, con las operaciones que tienen
          sentido para los dos: price(), weightGrams().
        - La HOJA (Product) las responde con sus propios datos.
        - El GRUPO (Bundle) las responde PREGUNTANDO A SUS HIJOS y
          combinando las respuestas. Sus hijos son CatalogItem: pueden ser
          productos o bundles, y al bundle no le importa.

        El cliente (el carrito, el reporte) llama item.price() y listo: no
        sabe ni pregunta si es un producto suelto o un paquete con diez
        niveles adentro. Cada objeto sabe calcular lo suyo (Information
        Expert; v10_design/part3_responsibilities/GRASP, CAPA 2).
    */
    interface CatalogItem {                                 // COMPONENT
        String name();

        BigDecimal price();

        int weightGrams();
    }

    record Product(String name, BigDecimal price, int weightGrams) implements CatalogItem { }   // LEAF

    record Bundle(String name, List<CatalogItem> items, BigDecimal discount) implements CatalogItem {   // COMPOSITE
        Bundle {
            items = List.copyOf(items);                     // inmutable (CAPA 7)
        }

        @Override
        public BigDecimal price() {
            BigDecimal sum = items.stream().map(CatalogItem::price).reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.subtract(sum.multiply(discount)).setScale(2, RoundingMode.HALF_UP);
        }

        @Override
        public int weightGrams() {
            return items.stream().mapToInt(CatalogItem::weightGrams).sum();   // la recursión, escondida en la llamada
        }
    }

    static final Bundle CABLES = new Bundle("Kit de cables", List.of(
            new Product("cable HDMI", new BigDecimal("150"), 80),
            new Product("cable USB-C", new BigDecimal("100"), 50)), BigDecimal.ZERO);

    static final Bundle HOME_OFFICE = new Bundle("Kit home office", List.of(
            new Product("teclado", new BigDecimal("500"), 900),
            new Product("mouse", new BigDecimal("250"), 120),
            CABLES), new BigDecimal("0.10"));

    static final Product HEADPHONES = new Product("audífonos", new BigDecimal("80"), 200);

    static BigDecimal cartTotal(List<CatalogItem> cart) {    // el cliente: trata igual a todos
        return cart.stream().map(CatalogItem::price).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static boolean compositeExample() {
        List<CatalogItem> cart = List.of(HEADPHONES, HOME_OFFICE);   // un producto y un paquete, mezclados
        return HOME_OFFICE.price().compareTo(new BigDecimal("900.00")) == 0
                && HOME_OFFICE.weightGrams() == 1_150
                && cartTotal(cart).compareTo(new BigDecimal("980.00")) == 0;
    }

    /*
        =================================================================
        CAPA 3) LA ESTRUCTURA Y LA RECURSIÓN
        =================================================================

            Component  (CatalogItem)   la interface común
            Leaf       (Product)       no tiene hijos; responde con sus datos
            Composite  (Bundle)        tiene hijos (Components); responde
                                       combinando lo que responden ellos

        El cálculo del precio de HOME_OFFICE, paso a paso:

            HOME_OFFICE.price()
              ├─ teclado.price()      -> 500
              ├─ mouse.price()        -> 250
              └─ CABLES.price()
                   ├─ HDMI.price()    -> 150
                   └─ USB-C.price()   -> 100
                   = 250 (sin descuento)
              = (500 + 250 + 250) - 10% = 900

        Es RECURSIÓN (v1_fundamentals/part2_control_flow/Methods): un
        método que, para responder, llama al mismo método en objetos más
        pequeños, hasta llegar a las hojas, que responden sin llamar a
        nadie. Pero fíjate que nadie escribió un if: el polimorfismo decide
        qué versión de price() corre en cada nodo.
    */

    /*
        =================================================================
        CAPA 4) MÁS OPERACIONES SOBRE EL ÁRBOL
        =================================================================

        Además de "calcular un total", con un árbol se hacen siempre las
        mismas operaciones:
        - CONTAR hojas (cuántos productos hay en total);
        - APLANAR: obtener todas las hojas en una lista (para el picking del
          almacén, que necesita los productos físicos, no los paquetes);
        - BUSCAR si algo está en el árbol;
        - medir la PROFUNDIDAD (cuántos niveles).

        "Aplanar" es justo lo que hace flatMap (v7_functional/part2_streams/
        AdvancedStreams, CAPA 8), aplicado de forma recursiva: cada nodo se
        convierte en un stream de sus hojas.
    */
    static Stream<Product> leaves(CatalogItem item) {
        if (item instanceof Bundle bundle) {
            return bundle.items().stream().flatMap(Composite::leaves);   // cada hijo, aplanado
        }
        return Stream.of((Product) item);
    }

    static int depth(CatalogItem item) {
        if (item instanceof Bundle bundle) {
            return 1 + bundle.items().stream().mapToInt(Composite::depth).max().orElse(0);
        }
        return 0;
    }

    static boolean treeOperationsExample() {
        List<String> pickingList = leaves(HOME_OFFICE).map(Product::name).toList();
        return pickingList.equals(List.of("teclado", "mouse", "cable HDMI", "cable USB-C"))
                && leaves(HOME_OFFICE).count() == 4
                && leaves(HOME_OFFICE).anyMatch(product -> product.name().equals("cable USB-C"))
                && depth(HOME_OFFICE) == 2 && depth(HEADPHONES) == 0;
    }

    /*
        Nota: leaves y depth preguntan "¿es un Bundle?". Con solo dos tipos
        posibles y en un lugar controlado, es aceptable, pero el compilador
        no te avisa si un día aparece un tercer tipo. La CAPA 5 lo resuelve.
    */

    /*
        =================================================================
        CAPA 5) LA VERSIÓN MODERNA: SEALED + RECORDS + SWITCH
        =================================================================

        Java 21 permite otra forma de escribir el mismo árbol: una interface
        SEALED con exactamente dos variantes (records), y las operaciones
        como funciones con un switch EXHAUSTIVO (v2_oop/
        part4_advanced_types/SealedAndPatternMatching):

            sealed interface Node permits FileNode, FolderNode
            static long size(Node node) {
                return switch (node) {
                    case FileNode file -> file.bytes();
                    case FolderNode folder -> sumar size(...) de sus hijos;
                };
            }

        Es el árbol del SISTEMA DE ARCHIVOS: una carpeta contiene archivos y
        otras carpetas (en Linux, cada carpeta es una tabla de nombre ->
        inodo; v8_apis/part2_io_and_network/FilesAndIO, CAPA 13).

        Cuál elegir (es el "problema de la expresión" otra vez;
        SealedAndPatternMatching, CAPA 10):
        - Composite clásico (CAPA 2): fácil agregar TIPOS de nodo; cada
          operación nueva toca todas las clases.
        - sealed + switch: fácil agregar OPERACIONES (tamaño, listar,
          buscar, exportar...); un tipo de nodo nuevo hace que el compilador
          te señale cada switch. Y el switch ya no necesita if ni casts
          como los de la CAPA 4.
    */
    sealed interface Node permits FileNode, FolderNode { }

    record FileNode(String name, long bytes) implements Node { }

    record FolderNode(String name, List<Node> children) implements Node {
        FolderNode {
            children = List.copyOf(children);
        }
    }

    static long size(Node node) {
        return switch (node) {
            case FileNode file -> file.bytes();
            case FolderNode folder -> folder.children().stream().mapToLong(Composite::size).sum();
        };
    }

    static List<String> paths(Node node, String parent) {   // otra operación, sin tocar los records
        String path = parent + "/" + switch (node) {
            case FileNode file -> file.name();
            case FolderNode folder -> folder.name();
        };
        return switch (node) {
            case FileNode file -> List.of(path);
            case FolderNode folder -> folder.children().stream().flatMap(child -> paths(child, path).stream()).toList();
        };
    }

    static boolean sealedExample() {
        Node home = new FolderNode("home", List.of(
                new FileNode("notas.txt", 1_200),
                new FolderNode("fotos", List.of(new FileNode("playa.jpg", 2_000_000), new FileNode("perro.jpg", 1_500_000))),
                new FolderNode("vacia", List.of())));
        return size(home) == 3_501_200
                && paths(home, "").equals(List.of("/home/notas.txt", "/home/fotos/playa.jpg", "/home/fotos/perro.jpg"));
    }

    /*
        =================================================================
        CAPA 6) DÓNDE VAN ADD Y REMOVE
        =================================================================

        Una decisión clásica: ¿los métodos para agregar y quitar hijos van
        en la interface común, o solo en el grupo?

        - En la interface común ("transparencia"): el cliente puede tratar
          todo igual, pero una hoja tendría add()... que no puede cumplir
          (lanzaría excepción: viola Liskov; v10_design/part2_solid/
          LiskovSubstitution, CAPA 7).
        - Solo en el grupo ("seguridad"): el compilador impide agregar
          hijos a una hoja.

        La recomendación actual es la segunda, y todavía mejor: que los
        nodos sean INMUTABLES y el árbol se arme de abajo hacia arriba al
        crearlos (como en esta guía: los hijos se pasan en el constructor).
        Si hay que "modificar" un árbol inmutable, se crea uno nuevo con el
        cambio (y las ramas que no cambian se comparten; part1_creational/
        Prototype, CAPA 4).
    */

    /*
        =================================================================
        CAPA 7) ÁRBOLES, NO CICLOS: LA RECURSIÓN Y LA PILA DEL HILO
        =================================================================

        El patrón supone un ÁRBOL: ningún nodo se contiene a sí mismo, ni
        directa ni indirectamente. Si un grupo MUTABLE termina conteniéndose
        (por un error, al armar paquetes desde datos de una base de datos),
        el cálculo recursivo nunca llega a una hoja: se llama a sí mismo
        sin fin.

        Qué pasa ahí por dentro: cada llamada a un método ocupa un espacio
        (un "marco") en la PILA del hilo, una zona de memoria que el sistema
        operativo reserva para cada hilo con un tamaño FIJO (del orden de
        1 MB por defecto en Linux de 64 bits; se ajusta con la opción -Xss
        de la JVM). Una recursión sin fin llena esa pila, y la JVM lanza
        StackOverflowError (un Error, no una Exception: v3_exceptions/
        part1_concepts/WhatAreExceptions).

        Cómo evitarlo:
        - Nodos INMUTABLES armados de abajo hacia arriba (CAPA 6): para meter
          un paquete dentro de sí mismo tendría que existir antes de
          crearse. Es imposible por construcción.
        - Si los nodos son mutables, validar al agregar que el nuevo hijo
          no contenga ya al padre.

        El ejemplo arma un ciclo con un grupo mutable y muestra el
        StackOverflowError (atraparlo así es solo para la demostración).
    */
    static final class MutableBundle implements CatalogItem {
        private final List<CatalogItem> items = new ArrayList<>();

        void add(CatalogItem item) {
            items.add(item);                                // ERROR de diseño: no impide ciclos
        }

        @Override
        public String name() {
            return "paquete mutable";
        }

        @Override
        public BigDecimal price() {
            return items.stream().map(CatalogItem::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public int weightGrams() {
            return items.stream().mapToInt(CatalogItem::weightGrams).sum();
        }
    }

    static boolean cycleExample() {
        MutableBundle bundle = new MutableBundle();
        bundle.add(HEADPHONES);
        bundle.add(bundle);                                 // se contiene a sí mismo
        try {
            bundle.price();
            return false;
        } catch (StackOverflowError e) {
            return true;                                    // la pila del hilo se llenó
        }
    }

    /*
        =================================================================
        CAPA 8) RECORRER SIN RECURSIÓN: PARA ÁRBOLES MUY PROFUNDOS
        =================================================================

        Por la misma razón (la pila del hilo tiene tamaño fijo), un árbol
        válido pero PROFUNDO también puede causar StackOverflowError con
        recursión. ¿Qué tan profundo? Medido en la máquina de prueba, con la
        pila por defecto, la función size de la CAPA 5:
            con streams adentro (como está escrita)    ~1,200 a 2,700 niveles
            con un for en lugar del stream             ~5,500 a 6,300 niveles
        (varía entre ejecuciones según cómo optimice la JVM). Los streams
        gastan más pila porque cada nivel apila también los marcos de sus
        clases internas. Para un catálogo o un menú sobra; para datos
        generados o cadenas muy largas (un historial enlazado, un árbol
        que crece sin control), no.

        La solución es recorrerlo con una PILA PROPIA en el heap, que crece
        lo que haga falta: un ArrayDeque usado como pila
        (v5_collections/part3_queues/QueuesAndStacks, CAPA 5):
        - meter la raíz;
        - mientras haya algo: sacar un nodo; si es hoja, procesarla; si es
          grupo, meter sus hijos.

        Para árboles normales (un catálogo, un menú, unas carpetas), la
        recursión es más clara y basta. El ejemplo verifica que los dos
        recorridos dan el mismo resultado, y que el iterativo aguanta una
        cadena de 100,000 niveles.
    */
    static long sizeIterative(Node root) {
        long total = 0;
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            switch (node) {
                case FileNode file -> total += file.bytes();
                case FolderNode folder -> folder.children().forEach(pending::push);
            }
        }
        return total;
    }

    static boolean iterativeExample() {
        Node deep = new FileNode("fondo.txt", 7);
        for (int level = 0; level < 100_000; level++) {
            deep = new FolderNode("nivel" + level, List.of(deep));   // 100,000 carpetas, una dentro de otra
        }
        Node normal = new FolderNode("raiz", List.of(new FileNode("a", 10), new FolderNode("b", List.of(new FileNode("c", 5)))));
        return sizeIterative(normal) == size(normal) && sizeIterative(deep) == 7;
    }

    /*
        =================================================================
        CAPA 9) DÓNDE APARECE
        =================================================================

        - SISTEMAS DE ARCHIVOS: carpetas y archivos (CAPA 5). Files.walk
          recorre ese árbol por ti (FilesAndIO, CAPA 13).
        - DOCUMENTOS JSON y XML: un objeto JSON contiene valores, que pueden
          ser otros objetos o arreglos (v8_apis/part2_io_and_network/
          Serialization, CAPA 11). Las librerías de JSON los representan
          como un árbol de nodos.
        - INTERFACES GRÁFICAS: una ventana contiene paneles, que contienen
          botones y otros paneles; dibujar la ventana es dibujar a sus
          hijos, recursivamente. Lo mismo el HTML de una página web.
        - MENÚS, CATEGORÍAS de productos, ORGANIGRAMAS, permisos agrupados
          en roles que contienen otros roles.
        - EXPRESIONES: (2 + 3) * 4 es un árbol de operaciones
          (part3_behavioral/Interpreter).
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Recorrer árboles con instanceof repartidos en cada operación
           (CAPA 1).

        2) Poner add/remove en la interface común: hojas que no pueden
           cumplirlos (CAPA 6).

        3) Grupos mutables que permiten ciclos: StackOverflowError
           (CAPA 7).

        4) Recursión en árboles generados que pueden ser muy profundos: usa
           una pila propia (CAPA 8).

        5) Exponer la lista interna de hijos de un grupo: cualquiera la
           modifica (usa List.copyOf o una vista; v5_collections/
           part4_tools/ImmutableCollections).

        6) Forzar Composite donde no hay "parte-todo": si un grupo no se
           comporta como un elemento (no tiene sentido pedirle precio o
           tamaño), no es Composite.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Composite: hojas y grupos comparten una interface; el grupo
          responde combinando lo que responden sus hijos (recursión).
        - El cliente trata igual a uno y a un grupo de cualquier
          profundidad.
        - Component / Leaf / Composite.
        - Operaciones típicas: totales, contar, aplanar (flatMap), buscar,
          profundidad.
        - Versión moderna: sealed + records + switch exhaustivo; fácil
          agregar operaciones.
        - add/remove solo en el grupo; mejor aún, nodos inmutables armados
          de abajo hacia arriba (sin ciclos posibles).
        - Recursión = marcos en la pila del hilo, de tamaño fijo: ciclos o
          árboles enormes causan StackOverflowError; con una pila propia
          (ArrayDeque) no.
        - Aparece en archivos, JSON/XML, interfaces gráficas, menús,
          categorías y expresiones.
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
