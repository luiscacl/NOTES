package v11_patterns.part3_behavioral;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/*
    =====================================================================
    MEMENTO: GUARDAR UNA "FOTO" DEL ESTADO PARA REGRESAR A ELLA
    =====================================================================

    Ruta de patterns/: guía 18 de 28  (part3_behavioral)
    Anterior:  part3_behavioral/Mediator
    Siguiente: part3_behavioral/Observer

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: BAJA como patrón formal. La idea (guardar el estado para
    poder volver) aparece en deshacer, cancelar ediciones y borradores; y
    con objetos inmutables casi no hay que hacer nada (CAPA 3).

    Índice:
        CAPA 1)  El problema: volver atrás sin exponer el interior
        CAPA 2)  La solución: una foto que solo el dueño sabe leer
        CAPA 3)  Con objetos inmutables, el objeto ES la foto
        CAPA 4)  Lo que cuesta guardar fotos
        CAPA 5)  Memento o Command para deshacer
        CAPA 6)  Errores comunes
        RESUMEN FINAL
*/
public class Memento {

    public static void main(String[] args) {
        check(mementoExample(), "CAPA 2");
        check(immutableHistoryExample(), "CAPA 3");
        check(boundedHistoryExample(), "CAPA 4");
        System.out.println("Memento: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: VOLVER ATRÁS SIN EXPONER EL INTERIOR
        =================================================================

        En el panel de administración, alguien edita un producto: cambia el
        nombre, el precio, las etiquetas... y al final presiona CANCELAR.
        Todo debe volver exactamente a como estaba al abrir el formulario.
        O presiona DESHACER y debe regresar al estado anterior a su último
        cambio.

        Para eso hay que GUARDAR el estado en algún momento y RESTAURARLO
        después. El problema es quién lo guarda:
        - Si el historial (quien administra el deshacer) lee los campos
          del producto con getters y luego los vuelve a escribir con
          setters, depende de TODA la estructura interna del producto (y
          obliga a tener setters para todo; v2_oop/part2_pillars/
          Encapsulation, CAPA 6). Cada campo nuevo rompe el historial.

        Lo que se quiere: que el PRODUCTO sepa sacar una foto de sí mismo y
        restaurarse desde ella, y que el historial solo GUARDE fotos sin
        saber qué tienen adentro.
    */

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: UNA FOTO QUE SOLO EL DUEÑO SABE LEER
        =================================================================

        MEMENTO: "Sin violar el encapsulamiento, captura y externaliza el
        estado interno de un objeto para que pueda restaurarse más tarde."

        Tres papeles:
        - ORIGINATOR (ProductEditor): el objeto con estado. Sabe crear una
          foto de sí mismo (snapshot()) y restaurarse desde una
          (restore(foto)).
        - MEMENTO (Snapshot): la foto. Inmutable. Para los de afuera es
          OPACA: la pueden guardar y devolver, pero no leer ni modificar.
        - CARETAKER (el historial): guarda las fotos (en una pila, para
          deshacer en orden inverso) sin saber qué contienen.

        En Java, la foto se hace opaca con un tipo anidado cuyos campos y
        constructor son privados: código de OTROS archivos solo ve un
        objeto Snapshot sin métodos útiles. (En este archivo, como todo
        está anidado en la misma clase, Java permite el acceso; en un
        proyecto real, el editor y el historial estarían en clases
        separadas; v2_oop/part1_basics/AccessModifiers.)

        Importante: la foto debe COPIAR lo mutable (la lista de etiquetas).
        Si guardara la misma lista, los cambios posteriores alterarían la
        foto (part1_creational/Prototype, CAPA 3).
    */
    static final class ProductEditor {                      // ORIGINATOR
        private String name;
        private BigDecimal price;
        private final List<String> tags = new ArrayList<>();

        ProductEditor(String name, BigDecimal price) {
            this.name = name;
            this.price = price;
        }

        void rename(String name) {
            this.name = name;
        }

        void reprice(BigDecimal price) {
            this.price = price;
        }

        void tag(String tag) {
            tags.add(tag);
        }

        String summary() {
            return name + " $" + price + " " + tags;
        }

        Snapshot snapshot() {
            return new Snapshot(name, price, List.copyOf(tags));   // copia de lo mutable
        }

        void restore(Snapshot snapshot) {
            this.name = snapshot.name;
            this.price = snapshot.price;
            this.tags.clear();
            this.tags.addAll(snapshot.tags);
        }

        static final class Snapshot {                       // MEMENTO: inmutable y opaco para los de afuera
            private final String name;
            private final BigDecimal price;
            private final List<String> tags;

            private Snapshot(String name, BigDecimal price, List<String> tags) {
                this.name = name;
                this.price = price;
                this.tags = tags;
            }
        }
    }

    static final class EditHistory {                        // CARETAKER: guarda fotos sin leerlas
        private final Deque<ProductEditor.Snapshot> snapshots = new ArrayDeque<>();

        void save(ProductEditor.Snapshot snapshot) {
            snapshots.push(snapshot);
        }

        ProductEditor.Snapshot undo() {
            return snapshots.pop();
        }
    }

    static boolean mementoExample() {
        ProductEditor editor = new ProductEditor("Teclado", new BigDecimal("500"));
        EditHistory history = new EditHistory();
        ProductEditor.Snapshot whenOpened = editor.snapshot();   // al abrir el formulario

        history.save(editor.snapshot());
        editor.rename("Teclado mecánico");
        history.save(editor.snapshot());
        editor.reprice(new BigDecimal("899"));
        editor.tag("gamer");

        editor.restore(history.undo());                     // DESHACER: antes del cambio de precio
        boolean undone = editor.summary().equals("Teclado mecánico $500 []");

        editor.tag("oferta");
        editor.restore(whenOpened);                         // CANCELAR: todo como al abrir
        return undone && editor.summary().equals("Teclado $500 []");
    }

    /*
        =================================================================
        CAPA 3) CON OBJETOS INMUTABLES, EL OBJETO ES LA FOTO
        =================================================================

        Si el objeto que se edita es INMUTABLE (un record; cada "cambio"
        crea una versión nueva con métodos with; v2_oop/
        part4_advanced_types/Records, CAPA 8), el patrón casi desaparece:
        - no hace falta un tipo Snapshot: cada versión YA es una foto que
          nadie puede alterar;
        - el historial es simplemente una pila de versiones anteriores;
        - deshacer es tomar la versión anterior.

        Y como las versiones comparten lo que no cambió (las listas
        inmutables no se copian; part1_creational/Prototype, CAPA 4),
        guardar muchas versiones es barato. Es la forma preferida hoy, y
        es también como funcionan muchas aplicaciones web modernas para
        deshacer.
    */
    record ProductForm(String name, BigDecimal price, List<String> tags) {
        ProductForm {
            tags = List.copyOf(tags);
        }

        ProductForm withName(String newName) {
            return new ProductForm(newName, price, tags);
        }

        ProductForm withPrice(BigDecimal newPrice) {
            return new ProductForm(name, newPrice, tags);
        }
    }

    static final class FormHistory {
        private final Deque<ProductForm> previous = new ArrayDeque<>();
        private ProductForm current;

        FormHistory(ProductForm initial) {
            this.current = initial;
        }

        void apply(ProductForm next) {
            previous.push(current);                         // la versión actual ES la foto
            current = next;
        }

        void undo() {
            current = previous.pop();
        }

        ProductForm current() {
            return current;
        }
    }

    static boolean immutableHistoryExample() {
        FormHistory form = new FormHistory(new ProductForm("Mouse", new BigDecimal("250"), List.of("oficina")));
        form.apply(form.current().withName("Mouse inalámbrico"));
        form.apply(form.current().withPrice(new BigDecimal("399")));
        form.undo();
        return form.current().name().equals("Mouse inalámbrico")
                && form.current().price().compareTo(new BigDecimal("250")) == 0;
    }

    /*
        =================================================================
        CAPA 4) LO QUE CUESTA GUARDAR FOTOS
        =================================================================

        Cada foto ocupa memoria. Con objetos pequeños no importa; con
        objetos grandes (un documento entero, una imagen) y un historial
        sin límite, la memoria crece con cada cambio.

        Medidas habituales:
        - LIMITAR el historial (las últimas 50 acciones): al pasar el
          límite, se descarta la foto más vieja. Una ArrayDeque sirve para
          las dos puntas (v5_collections/part3_queues/QueuesAndStacks,
          CAPA 10).
        - Guardar solo lo que CAMBIÓ, no todo el objeto: eso ya es guardar
          la operación y su inversa, es decir, Command (CAPA 5).
        - Con records inmutables, el costo baja solo, porque se comparten
          las partes iguales (CAPA 3).
    */
    static final class BoundedHistory {
        private final Deque<ProductForm> previous = new ArrayDeque<>();
        private final int limit;

        BoundedHistory(int limit) {
            this.limit = limit;
        }

        void save(ProductForm form) {
            previous.push(form);
            if (previous.size() > limit) {
                previous.removeLast();                      // se olvida la foto más vieja
            }
        }

        int size() {
            return previous.size();
        }
    }

    static boolean boundedHistoryExample() {
        BoundedHistory history = new BoundedHistory(3);
        for (int i = 0; i < 10; i++) {
            history.save(new ProductForm("versión " + i, BigDecimal.ONE, List.of()));
        }
        return history.size() == 3;                         // 10 cambios, solo las últimas 3 fotos
    }

    /*
        =================================================================
        CAPA 5) MEMENTO O COMMAND PARA DESHACER
        =================================================================

        Las dos formas de deshacer que existen (part3_behavioral/Command,
        CAPA 4):

        | Memento (guardar el estado)               | Command (guardar la operación)            |
        |-------------------------------------------|-------------------------------------------|
        | Simple: no hay que pensar la "inversa" de | Cada operación necesita su inversa        |
        | cada operación                            | (o guardar lo que cambió)                 |
        | Más memoria: una foto completa por paso   | Menos memoria: solo lo que cambió         |
        | Ideal para objetos pequeños o inmutables, | Ideal para objetos grandes, o cuando       |
        | o "cancelar la edición completa"          | además hay que registrar o reintentar     |

        Se combinan bien: un comando puede guardar un memento del objeto
        antes de ejecutarse y usarlo para su undo.
    */

    /*
        =================================================================
        CAPA 6) ERRORES COMUNES
        =================================================================

        1) Que el historial lea y escriba los campos del objeto: depende de
           toda su estructura interna (CAPA 1).

        2) Fotos que comparten objetos mutables con el original: dejan de
           ser fotos (CAPA 2).

        3) Fotos que exponen el estado con getters o setters públicos: se
           rompe el encapsulamiento que el patrón quería proteger (CAPA 2).

        4) Historiales sin límite con objetos grandes (CAPA 4).

        5) Escribir todo el patrón cuando el objeto podría ser un record
           inmutable (CAPA 3).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Memento: el objeto (originator) saca una foto de su estado
          (memento) que otro (caretaker) guarda sin leerla, y luego se
          restaura desde ella.
        - La foto es inmutable, opaca para los de afuera, y copia lo
          mutable.
        - Con records inmutables, cada versión ya es la foto: el historial
          es una pila de versiones.
        - Limita el historial si las fotos son grandes.
        - Memento guarda estado; Command guarda operaciones; se combinan.
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
