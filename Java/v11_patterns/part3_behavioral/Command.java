package v11_patterns.part3_behavioral;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/*
    =====================================================================
    COMMAND: CONVERTIR UNA ACCIÓN EN UN OBJETO
    =====================================================================

    Ruta de patterns/: guía 14 de 28  (part3_behavioral)
    Anterior:  part3_behavioral/ChainOfResponsibility
    Siguiente: part3_behavioral/Interpreter

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: MEDIA-ALTA. Deshacer/rehacer, colas de trabajos, tareas
    para otros hilos (Runnable, Callable) y el registro de "qué se hizo"
    se construyen con esta idea.

    Índice:
        CAPA 1)  El problema: acciones que solo existen mientras se ejecutan
        CAPA 2)  La solución: la acción como objeto
        CAPA 3)  Deshacer y rehacer
        CAPA 4)  Qué guardar para poder deshacer
        CAPA 5)  Ejecutar después: colas y otros hilos
        CAPA 6)  Comandos como datos: records y un despachador
        CAPA 7)  Comandos simples con lambdas
        CAPA 8)  Command, Strategy y Memento
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Command {

    public static void main(String[] args) throws Exception {
        check(commandExample(), "CAPA 2");
        check(undoRedoExample(), "CAPA 3");
        check(deferredExample(), "CAPA 5");
        check(commandsAsDataExample(), "CAPA 6");
        check(lambdaCommandsExample(), "CAPA 7");
        System.out.println("Command: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: ACCIONES QUE SOLO EXISTEN MIENTRAS SE EJECUTAN
        =================================================================

        En el panel de administración de la tienda, cuando alguien cambia
        un precio, el código llama directo: catalog.setPrice("KBD-1", 450).
        La acción ocurre y desaparece: solo existió mientras corría ese
        método.

        Eso impide cosas que el negocio pide todo el tiempo:
        - DESHACER: "me equivoqué de precio, regrésalo".
        - HACERLO DESPUÉS: "aplica estos cambios de precio a medianoche", o
          "procesa esta importación en segundo plano".
        - REGISTRAR qué se hizo, quién y cuándo (auditoría).
        - REINTENTAR algo que falló.
        - Tratar igual acciones distintas: una cola que procesa "lo que
          sea" sin conocer cada tipo de acción.

        Para todo eso, la acción necesita existir como algo que se pueda
        GUARDAR, PASAR y EJECUTAR cuando convenga: un objeto.
    */
    static final class Catalog {                            // RECEIVER: el que sabe hacer el trabajo real
        private final Map<String, BigDecimal> prices = new HashMap<>(Map.of(
                "KBD-1", new BigDecimal("500"), "MOU-1", new BigDecimal("250")));
        private final Map<String, Integer> stock = new HashMap<>(Map.of("KBD-1", 10, "MOU-1", 4));

        BigDecimal price(String sku) {
            return prices.get(sku);
        }

        void setPrice(String sku, BigDecimal price) {
            prices.put(sku, price);
        }

        int stock(String sku) {
            return stock.get(sku);
        }

        void adjustStock(String sku, int delta) {
            stock.merge(sku, delta, Integer::sum);
        }
    }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: LA ACCIÓN COMO OBJETO
        =================================================================

        COMMAND: "Encapsula una petición como un objeto, lo que permite
        parametrizar clientes con distintas peticiones, encolarlas,
        registrarlas y soportar operaciones que se pueden deshacer."

        - Una interface con un método para ejecutar: execute().
        - Cada acción es una clase que guarda TODO lo necesario para
          hacerla: a quién afecta (el Catalog) y con qué datos (el SKU, el
          precio nuevo).
        - Quien ejecuta los comandos (el "invocador": el panel, una cola)
          solo conoce la interface: no sabe si es un cambio de precio o un
          ajuste de inventario.

        Piezas del libro: COMMAND (la interface), CONCRETE COMMAND
        (ChangePrice), RECEIVER (Catalog, quien hace el trabajo real),
        INVOKER (quien ejecuta y guarda los comandos), CLIENT (quien crea
        los comandos).
    */
    interface AdminCommand {                                // COMMAND
        void execute();

        void undo();

        String description();
    }

    static final class ChangePrice implements AdminCommand {   // CONCRETE COMMAND
        private final Catalog catalog;
        private final String sku;
        private final BigDecimal newPrice;
        private BigDecimal previousPrice;                   // lo que hace falta para deshacer (CAPA 4)

        ChangePrice(Catalog catalog, String sku, BigDecimal newPrice) {
            this.catalog = catalog;
            this.sku = sku;
            this.newPrice = newPrice;
        }

        @Override
        public void execute() {
            previousPrice = catalog.price(sku);
            catalog.setPrice(sku, newPrice);
        }

        @Override
        public void undo() {
            catalog.setPrice(sku, previousPrice);
        }

        @Override
        public String description() {
            return "precio de " + sku + " a " + newPrice;
        }
    }

    static final class AdjustStock implements AdminCommand {
        private final Catalog catalog;
        private final String sku;
        private final int delta;

        AdjustStock(Catalog catalog, String sku, int delta) {
            this.catalog = catalog;
            this.sku = sku;
            this.delta = delta;
        }

        @Override
        public void execute() {
            catalog.adjustStock(sku, delta);
        }

        @Override
        public void undo() {
            catalog.adjustStock(sku, -delta);               // deshacer = la operación inversa
        }

        @Override
        public String description() {
            return "existencias de " + sku + (delta >= 0 ? " +" : " ") + delta;
        }
    }

    static boolean commandExample() {
        Catalog catalog = new Catalog();
        List<AdminCommand> batch = List.of(                 // acciones distintas, tratadas igual
                new ChangePrice(catalog, "KBD-1", new BigDecimal("450")),
                new AdjustStock(catalog, "MOU-1", 6));
        List<String> audit = new ArrayList<>();
        for (AdminCommand command : batch) {
            command.execute();
            audit.add(command.description());              // registrar es trivial: cada acción se describe
        }
        return catalog.price("KBD-1").compareTo(new BigDecimal("450")) == 0 && catalog.stock("MOU-1") == 10
                && audit.equals(List.of("precio de KBD-1 a 450", "existencias de MOU-1 +6"));
    }

    /*
        =================================================================
        CAPA 3) DESHACER Y REHACER
        =================================================================

        Con acciones como objetos, deshacer/rehacer es la idea de las dos
        pilas que viste en v5_collections/part3_queues/QueuesAndStacks,
        CAPA 8, pero ahora GENÉRICA para cualquier comando:
        - ejecutar: execute(), y el comando va a la pila de "deshacer";
          la pila de "rehacer" se vacía (una acción nueva invalida el
          futuro);
        - deshacer: sacar de "deshacer", llamar undo(), meter en "rehacer";
        - rehacer: sacar de "rehacer", llamar execute() otra vez, meter en
          "deshacer".

        El historial no sabe nada de precios ni de inventario: cualquier
        comando nuevo (cambiar una descripción, activar una promoción)
        funciona con deshacer/rehacer sin tocarlo.
    */
    static final class CommandHistory {                     // INVOKER
        private final Deque<AdminCommand> undoStack = new ArrayDeque<>();
        private final Deque<AdminCommand> redoStack = new ArrayDeque<>();

        void run(AdminCommand command) {
            command.execute();
            undoStack.push(command);
            redoStack.clear();
        }

        boolean undo() {
            if (undoStack.isEmpty()) {
                return false;
            }
            AdminCommand command = undoStack.pop();
            command.undo();
            redoStack.push(command);
            return true;
        }

        boolean redo() {
            if (redoStack.isEmpty()) {
                return false;
            }
            AdminCommand command = redoStack.pop();
            command.execute();
            undoStack.push(command);
            return true;
        }
    }

    static boolean undoRedoExample() {
        Catalog catalog = new Catalog();
        CommandHistory history = new CommandHistory();

        history.run(new ChangePrice(catalog, "KBD-1", new BigDecimal("450")));
        history.run(new AdjustStock(catalog, "KBD-1", -3));
        history.undo();                                     // regresa las 3 piezas
        history.undo();                                     // regresa el precio
        boolean bothUndone = catalog.price("KBD-1").compareTo(new BigDecimal("500")) == 0 && catalog.stock("KBD-1") == 10;

        history.redo();                                     // vuelve a aplicar el precio
        boolean redone = catalog.price("KBD-1").compareTo(new BigDecimal("450")) == 0;

        history.run(new AdjustStock(catalog, "KBD-1", 5));  // acción nueva: ya no se puede rehacer el -3
        boolean futureCleared = !history.redo();

        return bothUndone && redone && futureCleared && catalog.stock("KBD-1") == 15;
    }

    /*
        =================================================================
        CAPA 4) QUÉ GUARDAR PARA PODER DESHACER
        =================================================================

        Hay dos formas de deshacer, y cada comando elige la que le sirve:

        1) LA OPERACIÓN INVERSA: si sumar 6 se deshace restando 6
           (AdjustStock), el comando solo necesita sus propios datos.

        2) GUARDAR EL ESTADO ANTERIOR: un cambio de precio no tiene
           "inversa" sin saber el precio de antes. Por eso ChangePrice
           guarda previousPrice al ejecutar. Si el estado anterior es
           grande, se guarda una "foto" del objeto (part3_behavioral/
           Memento).

        Cuidado con el orden: deshacer funciona si los comandos se
        deshacen en orden INVERSO (por eso es una pila). Deshacer uno del
        medio, ignorando los que vinieron después, puede dejar datos
        inconsistentes.

        Y una advertencia del mundo real: no todo se puede deshacer. Un
        correo enviado o un pago cobrado no se "des-ejecutan"; a lo mucho
        se compensan con otra acción (un reembolso; part2_structural/
        Facade, CAPA 3).
    */

    /*
        =================================================================
        CAPA 5) EJECUTAR DESPUÉS: COLAS Y OTROS HILOS
        =================================================================

        Como un comando es un objeto, crearlo y ejecutarlo pueden pasar en
        momentos y lugares distintos:
        - en una COLA que se procesa después (los cambios de precio que se
          aplican a medianoche; una cola FIFO: QueuesAndStacks);
        - en OTRO HILO.

        El JDK está construido sobre esta idea: Runnable (una acción sin
        resultado) y Callable (una acción con resultado) SON comandos. Se
        los entregas a un ExecutorService, que los ejecuta en sus hilos
        cuando puede, y te devuelve un Future para recoger el resultado
        después (v13_concurrency/).

        El ejemplo: comandos acumulados en una cola y procesados después, y
        un Callable que otro hilo ejecuta.
    */
    static boolean deferredExample() throws Exception {
        Catalog catalog = new Catalog();
        Deque<AdminCommand> midnightQueue = new ArrayDeque<>();
        midnightQueue.offer(new ChangePrice(catalog, "KBD-1", new BigDecimal("399")));   // se ENCOLA, no se ejecuta
        midnightQueue.offer(new ChangePrice(catalog, "MOU-1", new BigDecimal("199")));
        boolean nothingChangedYet = catalog.price("KBD-1").compareTo(new BigDecimal("500")) == 0;

        while (!midnightQueue.isEmpty()) {                 // "a medianoche"
            midnightQueue.poll().execute();
        }
        boolean appliedLater = catalog.price("MOU-1").compareTo(new BigDecimal("199")) == 0;

        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Future<String> report = worker.submit(() -> "reporte generado en " + Thread.currentThread().getName());
            String result = report.get();                   // esperar el resultado del comando
            return nothingChangedYet && appliedLater && !result.contains("main");   // corrió en otro hilo
        }
    }

    /*
        =================================================================
        CAPA 6) COMANDOS COMO DATOS: RECORDS Y UN DESPACHADOR
        =================================================================

        La versión clásica pone los datos Y la lógica en cada comando. En
        aplicaciones modernas (sobre todo cuando los comandos llegan por la
        red o se guardan), se separan:
        - el COMANDO es solo DATOS: un record inmutable que describe la
          intención ("cambiar precio de KBD-1 a 450"), fácil de
          serializar, registrar o mandar por una cola (v8_apis/
          part2_io_and_network/Serialization);
        - un DESPACHADOR (handler) recibe el comando y hace el trabajo. Con
          una jerarquía sealed y un switch, el compilador verifica que todo
          tipo de comando tenga su manejo (v2_oop/part4_advanced_types/
          SealedAndPatternMatching).

        Como los comandos son datos, la lista de comandos ejecutados es un
        REGISTRO COMPLETO de lo que se pidió. El ejemplo reconstruye el
        catálogo volviendo a aplicar esa lista desde el principio. Ojo con el
        nombre: guardar y reaplicar COMANDOS (pedidos que podían rechazarse)
        se llama "command sourcing", y es frágil (reaplicar vuelve a validar
        y a ejecutar efectos). El EVENT SOURCING guarda EVENTOS: hechos ya
        aceptados, en pasado (PriceSet, Restocked: Observer, CAPA 3), y el
        estado se reconstruye reaplicándolos (v14_architecture/).
    */
    sealed interface CatalogCommand permits SetPrice, Restock, Discontinue { }

    record SetPrice(String sku, BigDecimal price) implements CatalogCommand { }

    record Restock(String sku, int units) implements CatalogCommand { }

    record Discontinue(String sku) implements CatalogCommand { }

    static final class CatalogCommandHandler {
        final List<CatalogCommand> log = new ArrayList<>();

        void handle(Catalog catalog, CatalogCommand command) {
            switch (command) {                              // sin default: un comando nuevo no compila hasta manejarse
                case SetPrice c -> catalog.setPrice(c.sku(), c.price());
                case Restock c -> catalog.adjustStock(c.sku(), c.units());
                case Discontinue c -> catalog.adjustStock(c.sku(), -catalog.stock(c.sku()));
            }
            log.add(command);                               // el registro son los propios comandos
        }
    }

    static boolean commandsAsDataExample() {
        Catalog live = new Catalog();
        CatalogCommandHandler handler = new CatalogCommandHandler();
        handler.handle(live, new SetPrice("KBD-1", new BigDecimal("450")));
        handler.handle(live, new Restock("KBD-1", 5));
        handler.handle(live, new Discontinue("MOU-1"));

        Catalog rebuilt = new Catalog();                    // reconstruir desde el registro
        CatalogCommandHandler replayer = new CatalogCommandHandler();
        handler.log.forEach(command -> replayer.handle(rebuilt, command));

        return rebuilt.price("KBD-1").equals(live.price("KBD-1"))
                && rebuilt.stock("KBD-1") == live.stock("KBD-1") && rebuilt.stock("KBD-1") == 15
                && rebuilt.stock("MOU-1") == 0
                && handler.log.get(0).equals(new SetPrice("KBD-1", new BigDecimal("450")));   // un comando es un valor
    }

    /*
        =================================================================
        CAPA 7) COMANDOS SIMPLES CON LAMBDAS
        =================================================================

        Si un comando solo necesita EJECUTARSE (sin deshacer, sin
        describirse), una lambda o una referencia a método basta: Runnable
        es la interface de comando del JDK.

            List<Runnable> nightlyTasks = List.of(
                    () -> catalog.adjustStock("KBD-1", 20),
                    report::regenerate);

        Para deshacer, describir o serializar, hace falta más que una
        función: una clase o un record (CAPAS 2 y 6).
    */
    static boolean lambdaCommandsExample() {
        Catalog catalog = new Catalog();
        List<String> done = new ArrayList<>();
        List<Runnable> nightlyTasks = List.of(
                () -> catalog.adjustStock("KBD-1", 20),
                () -> done.add("caché de precios limpiada"));
        nightlyTasks.forEach(Runnable::run);
        return catalog.stock("KBD-1") == 30 && done.size() == 1;
    }

    /*
        =================================================================
        CAPA 8) COMMAND, STRATEGY Y MEMENTO
        =================================================================

        | Patrón   | El objeto representa...                  | Pregunta que responde            |
        |----------|------------------------------------------|----------------------------------|
        | Command  | UNA ACCIÓN concreta, con sus datos,      | ¿QUÉ hay que hacer (y cuándo)?   |
        |          | lista para ejecutarse, guardarse o        |                                  |
        |          | deshacerse                               |                                  |
        | Strategy | UNA FORMA de hacer algo (un algoritmo)   | ¿CÓMO se hace?                   |
        |          | (part3_behavioral/Strategy)              |                                  |
        | Memento  | UNA FOTO del estado de un objeto         | ¿cómo REGRESO a como estaba?     |
        |          | (part3_behavioral/Memento)               | (útil para el undo de Command)   |
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Lógica de negocio repartida en los manejadores de la interfaz
           (botones, endpoints) en vez de en comandos: no se puede deshacer,
           encolar ni registrar de forma uniforme (CAPA 1).

        2) Comandos que no guardan lo necesario para deshacer (el precio
           anterior) (CAPA 4).

        3) Deshacer fuera de orden, o prometer "deshacer" en acciones que no
           se pueden revertir (un correo enviado) (CAPA 4).

        4) Comandos encolados que guardan objetos MUTABLES que cambian antes
           de ejecutarse: al ejecutarse, usan datos distintos a los de
           cuando se crearon. Por eso los comandos como datos son records
           (CAPA 6).

        5) Un switch con default sobre los tipos de comando: un comando
           nuevo se ignora en silencio (CAPA 6).

        6) Clases de comando para acciones que solo necesitan ejecutarse una
           vez: una lambda basta (CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Command: una acción convertida en objeto (qué hacer + datos), para
          guardarla, encolarla, registrarla, deshacerla o reintentarla.
        - Piezas: Command, ConcreteCommand, Receiver (hace el trabajo),
          Invoker (ejecuta y guarda), Client (crea).
        - Deshacer/rehacer genérico con dos pilas; deshacer con la operación
          inversa o guardando el estado anterior.
        - Ejecutar después: colas y otros hilos; Runnable y Callable son
          comandos del JDK.
        - Moderno: comandos como records (datos) + un despachador con sealed
          y switch; el registro de comandos permite reconstruir el estado.
        - Sin deshacer ni descripción: una lambda.
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
