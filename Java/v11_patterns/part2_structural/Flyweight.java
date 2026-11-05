package v11_patterns.part2_structural;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
    =====================================================================
    FLYWEIGHT: COMPARTIR OBJETOS IDÉNTICOS PARA AHORRAR MEMORIA
    =====================================================================

    Ruta de patterns/: guía 11 de 28  (part2_structural)
    Anterior:  part2_structural/Facade
    Siguiente: part2_structural/Proxy

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: BAJA para escribirlo (solo con millones de objetos y
    después de medir); MEDIA para entenderlo, porque el JDK lo aplica por
    ti en lugares que usas todos los días (CAPA 4).

    Índice:
        CAPA 1)  El problema: millones de copias de lo mismo
        CAPA 2)  La solución: compartir la parte que se repite
        CAPA 3)  La regla de oro: lo compartido debe ser inmutable
        CAPA 4)  El JDK ya lo hace por ti
        CAPA 5)  Cuándo escribirlo: solo después de medir
        CAPA 6)  Errores comunes
        RESUMEN FINAL
*/
public class Flyweight {

    public static void main(String[] args) {
        check(sharingExample(), "CAPA 2");
        check(jdkExample(), "CAPA 4");
        System.out.println("Flyweight: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: MILLONES DE COPIAS DE LO MISMO
        =================================================================

        Un almacén procesa el historial de un millón de movimientos de
        inventario. Cada movimiento tiene datos PROPIOS (el producto, la
        cantidad) y datos de su CATEGORÍA (nombre, clave fiscal,
        descripción). Pero solo hay 3 categorías.

        Si cada movimiento guarda su PROPIA copia de la categoría, hay un
        millón de objetos Category con exactamente el mismo contenido
        repetido. Cada objeto en el heap ocupa memoria (sus campos más un
        encabezado que la JVM agrega a todo objeto), y los Strings de
        adentro, más.

        Medido en la máquina de prueba (JDK 26), un millón de movimientos:
            cada uno con su propia copia de la categoría    ~177 MB
            compartiendo las 3 categorías                    ~28 MB

        Seis veces menos memoria, para guardar EXACTAMENTE la misma
        información. Y menos memoria también significa menos trabajo para
        el recolector de basura (v12_jvm/).
    */

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: COMPARTIR LA PARTE QUE SE REPITE
        =================================================================

        FLYWEIGHT ("peso mosca"): "Usa objetos compartidos para soportar
        eficientemente grandes cantidades de objetos de grano fino."

        Se separa el estado en dos:
        - INTRÍNSECO: lo que se repite y no depende del contexto (la
          categoría). Se guarda UNA vez por valor distinto y se COMPARTE.
        - EXTRÍNSECO: lo propio de cada uso (el producto, la cantidad). Se
          queda en cada objeto, o se pasa como parámetro.

        Una FÁBRICA con caché entrega siempre la misma instancia para el
        mismo valor: si ya existe, la devuelve; si no, la crea y la guarda.
        Con computeIfAbsent de un mapa (v5_collections/
        part2_sets_and_maps/Maps, CAPA 9) es una línea. Si varios hilos la
        usan a la vez, el mapa debe ser seguro con hilos:
        ConcurrentHashMap (v13_concurrency/).

        El ejemplo crea un millón de movimientos y comprueba que solo
        existen 3 objetos de categoría, compartidos por todos.
    */
    record Category(String name, String taxCode, String description) { }   // estado INTRÍNSECO

    record StockMovement(String sku, int quantity, Category category) { }  // estado EXTRÍNSECO + referencia compartida

    static final class CategoryFactory {                    // FLYWEIGHT FACTORY
        private final Map<String, Category> cache = new ConcurrentHashMap<>();

        Category get(String name) {
            return cache.computeIfAbsent(name, key -> new Category(key, "IVA16", "Categoría de productos " + key));
        }

        int distinctInstances() {
            return cache.size();
        }
    }

    static boolean sharingExample() {
        CategoryFactory categories = new CategoryFactory();
        String[] names = {"periféricos", "pantallas", "accesorios"};

        List<StockMovement> movements = new ArrayList<>(1_000_000);
        for (int i = 0; i < 1_000_000; i++) {
            movements.add(new StockMovement("SKU-" + (i % 100), 1, categories.get(names[i % 3])));
        }

        return movements.size() == 1_000_000
                && categories.distinctInstances() == 3      // un millón de usos, tres objetos
                && movements.get(0).category() == movements.get(3).category();   // literalmente el MISMO objeto
    }

    /*
        =================================================================
        CAPA 3) LA REGLA DE ORO: LO COMPARTIDO DEBE SER INMUTABLE
        =================================================================

        Si un millón de movimientos apuntan al MISMO objeto Category, y ese
        objeto se pudiera modificar, cambiar la descripción en UN
        movimiento la cambiaría en TODOS. Por eso el estado intrínseco
        compartido debe ser INMUTABLE: un record, como en la CAPA 2
        (part1_creational/Prototype, CAPA 4 y v5_collections/part4_tools/
        ImmutableCollections, CAPA 1).

        Y una consecuencia: al compartir instancias, == empieza a "funcionar"
        para comparar (dos categorías iguales son el mismo objeto). No
        dependas de eso fuera de la fábrica: compara con equals, que
        funciona siempre (v2_oop/part4_advanced_types/ObjectContract,
        CAPA 2). La CAPA 4 muestra por qué.
    */

    /*
        =================================================================
        CAPA 4) EL JDK YA LO HACE POR TI
        =================================================================

        Varias cosas que usas a diario son flyweights que el JDK administra:

        - LA CACHÉ DE Integer: Integer.valueOf (y el autoboxing) devuelven
          SIEMPRE el mismo objeto para los valores de -128 a 127
          (v1_fundamentals/part3_text_and_references/Wrappers, CAPA 3). Lo
          mismo Long, Short, Byte, Character (hasta 127) y Boolean (TRUE y
          FALSE).
        - EL POOL DE STRING: los textos literales iguales del código son el
          mismo objeto (v1_fundamentals/part3_text_and_references/Strings,
          CAPA 3).
        - LAS CONSTANTES DE UN ENUM: una instancia por constante, compartida
          por todo el programa.
        - LAS COLECCIONES VACÍAS INMUTABLES: List.of() devuelve siempre la
          misma lista vacía.

        Y la trampa de la CAPA 3, en vivo: Integer 127 == 127 es true
        (están en la caché), pero 128 == 128 es false (cada uno es un
        objeto nuevo). Por eso con objetos SIEMPRE se compara con equals.
        (La JVM puede además deduplicar los Strings repetidos en el heap
        con -XX:+UseStringDeduplication: nació en G1 y desde JDK 18 funciona
        en todos los recolectores principales; v12_jvm/.)
    */
    static boolean jdkExample() {
        Integer cachedA = 127;
        Integer cachedB = 127;
        Integer notCachedA = 128;
        Integer notCachedB = 128;
        String literalA = "teclado";
        String literalB = "teclado";
        String built = new String("teclado");               // fuera del pool: otro objeto

        return cachedA == cachedB                           // flyweight del JDK
                && notCachedA != notCachedB                 // fuera del rango: objetos distintos...
                && notCachedA.equals(notCachedB)            // ...pero iguales con equals
                && literalA == literalB && literalA != built && literalA.equals(built)
                && List.of() == List.of();                  // la lista vacía, compartida
    }

    /*
        =================================================================
        CAPA 5) CUÁNDO ESCRIBIRLO: SOLO DESPUÉS DE MEDIR
        =================================================================

        Escribe un flyweight SOLO si se cumplen las tres:
        1) Hay MUCHOS objetos (cientos de miles o millones) en memoria al
           mismo tiempo.
        2) Una parte grande de su estado SE REPITE (pocos valores
           distintos).
        3) MEDISTE que la memoria es un problema real (con un perfilador
           que muestre qué ocupa el heap; v12_jvm/), no lo supusiste.

        Si no, es complejidad sin beneficio (v10_design/part1_basics/
        BasicPrinciples, CAPA 7): para mil objetos el ahorro es de unos
        cuantos KB.

        Un aviso sobre medir: medir memoria en Java engaña con facilidad.
        Al preparar esta guía, la primera medición dio 0 MB para la versión
        con copias: la JVM detectó que la lista ya no se usaba después y la
        recolectó ANTES de medir. Para medir de verdad se usan herramientas
        hechas para eso (v12_jvm/, JMH y perfiladores), no restas de
        Runtime.totalMemory a mano.
    */

    /*
        =================================================================
        CAPA 6) ERRORES COMUNES
        =================================================================

        1) Compartir objetos MUTABLES: un cambio en un uso aparece en todos
           (CAPA 3).

        2) Una fábrica con un HashMap usada desde varios hilos: necesita
           ConcurrentHashMap (CAPA 2).

        3) Comparar con == porque "son el mismo objeto": funciona hasta que
           no (Integer 128) (CAPAS 3 y 4).

        4) Aplicarlo sin haber medido un problema de memoria (CAPA 5).

        5) Una caché de flyweights que crece sin límite con valores que no
           se repiten (cada valor nuevo se queda para siempre): eso no
           ahorra memoria, la consume.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Flyweight: compartir el estado que se repite (intrínseco) entre
          muchos objetos; lo propio (extrínseco) se queda en cada uno.
        - Una fábrica con caché (computeIfAbsent, ConcurrentHashMap) entrega
          siempre la misma instancia por valor.
        - Lo compartido DEBE ser inmutable; compara con equals.
        - Medido: un millón de objetos, ~177 MB con copias vs ~28 MB
          compartiendo.
        - El JDK lo hace por ti: caché de Integer (-128 a 127), pool de
          String, enums, List.of().
        - Escríbelo solo con millones de objetos repetidos y después de
          medir.
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
