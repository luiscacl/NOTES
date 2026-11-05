package v3_exceptions.part2_in_practice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
    =====================================================================
    BUENAS PRÁCTICAS PARA MANEJAR ERRORES — NIVEL PROFESIONAL
    =====================================================================

    Ruta de v3_exceptions/: guía 5 de 5  (part2_in_practice)
    Anterior:  part2_in_practice/TryWithResources
    Siguiente: v4_generics/GenericsBasics (última de v3_exceptions/)

    Esta guía junta todo lo de v3_exceptions/ en las reglas que siguen los
    equipos profesionales. Cada práctica tiene la forma INCORRECTA y la
    CORRECTA, en código real, para ver la diferencia y no solo leerla.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Nunca te tragues una excepción
        CAPA 2)  Atrapa lo específico, no Exception
        CAPA 3)  Falla rápido, cerca de la causa
        CAPA 4)  No uses excepciones para el flujo normal
        CAPA 5)  Errores esperados: valida y reúne, no lances uno por uno
        CAPA 6)  Deja los objetos en un estado válido aunque falles
        CAPA 7)  Registra (log) el error una sola vez, en el lugar correcto
        CAPA 8)  Traduce las excepciones en los límites de cada capa
        CAPA 9)  InterruptedException: no la ignores
        CAPA 10) Documenta lo que puede fallar
        CAPA 11) Lista de decisiones
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class ErrorHandlingBestPractices {

    public static void main(String[] args) {
        check(dontSwallowExample(), "CAPA 1");
        check(catchSpecificExample(), "CAPA 2");
        check(failFastExample(), "CAPA 3");
        check(noControlFlowExample(), "CAPA 4");
        check(collectErrorsExample(), "CAPA 5");
        check(failureAtomicityExample(), "CAPA 6");
        check(logOnceExample(), "CAPA 7");
        check(translateAtBoundaryExample(), "CAPA 8");
        check(interruptedExample(), "CAPA 9");
        System.out.println("ErrorHandlingBestPractices: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) NUNCA TE TRAGUES UNA EXCEPCIÓN
        =================================================================

        "Tragarse" una excepción es atraparla y no hacer nada:
            try { ... } catch (Exception e) { }

        Es el peor error posible con excepciones, peor que no atraparla:
        - Si no la atrapas, el programa se detiene y te dice exactamente
          qué pasó y dónde (WhatAreExceptions, CAPA 10).
        - Si te la tragas, el programa SIGUE con datos incorrectos, sin
          ningún rastro. El síntoma aparece mucho después, en otro lugar,
          y no hay forma de saber de dónde vino. Volvemos al problema de
          los códigos de error ignorados (WhatAreExceptions, CAPA 1).

        Ante una excepción, haz UNA de estas:
        1) Manejarla de verdad (reintentar, valor por defecto JUSTIFICADO
           y registrado, avisar al usuario).
        2) Propagarla (no atraparla, o declararla con throws).
        3) Traducirla a otra, con la causa (CAPA 8).

        Si de verdad debes ignorarla (casos muy raros), escribe POR QUÉ en
        un comentario y nombra la variable "ignored".
    */
    static int discountSwallowing(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            // tragada: nadie se entera de que el descuento venía mal
        }
        return 0;
    }

    static int discountOrFail(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Descuento inválido: '" + text + "'", e);   // traduce con causa
        }
    }

    static boolean dontSwallowExample() {
        // "15%" no es un número: con la versión que traga, el cliente pierde su descuento sin que nadie lo sepa.
        boolean silentlyWrong = discountSwallowing("15%") == 0;

        boolean failsLoudly;
        try {
            discountOrFail("15%");
            failsLoudly = false;
        } catch (IllegalArgumentException e) {
            failsLoudly = e.getMessage().equals("Descuento inválido: '15%'") && e.getCause() instanceof NumberFormatException;
        }

        return silentlyWrong && failsLoudly;
    }

    /*
        =================================================================
        CAPA 2) ATRAPA LO ESPECÍFICO, NO EXCEPTION
        =================================================================

        catch (Exception e) atrapa TODO, incluidos tus BUGS
        (NullPointerException, IndexOutOfBounds...). Si lo pusiste
        pensando en "el texto no es un número", ahora también ocultas un
        null que nunca debió llegar ahí, y lo tratas como si fuera un dato
        mal escrito.

        Regla: atrapa el tipo MÁS ESPECÍFICO que sepas manejar. Lo que no
        esperabas debe salir a la luz.

        La excepción a la regla: en el nivel MÁS ALTO del programa (main,
        o el manejador central de un servidor) sí se atrapa Exception,
        para registrar cualquier error inesperado y responder algo
        razonable en lugar de que todo se caiga (CAPA 8). Ahí es correcto
        porque no se oculta: se registra y se reporta.
    */
    static int parseQuantityCatchingAll(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {              // también atrapa el NPE de text.trim() con null
            return 1;
        }
    }

    static int parseQuantity(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {  // solo el problema que sí sabe manejar
            return 1;
        }
    }

    static boolean catchSpecificExample() {
        boolean bugHidden = parseQuantityCatchingAll(null) == 1;   // un null (bug) tratado como "dato mal escrito"

        boolean bugRevealed;
        try {
            parseQuantity(null);
            bugRevealed = false;
        } catch (NullPointerException e) {
            bugRevealed = true;              // el bug sale a la luz, como debe ser
        }

        return bugHidden && bugRevealed && parseQuantity("abc") == 1;
    }

    /*
        =================================================================
        CAPA 3) FALLA RÁPIDO, CERCA DE LA CAUSA
        =================================================================

        Un dato inválido debe detectarse EN CUANTO ENTRA, no cuando por
        fin se usa. Si un pedido acepta una cantidad negativa al crearse,
        el error aparecerá horas después al facturar, en otra clase, y
        habrá que rastrear de dónde salió ese -3.

        Validar en la frontera (constructores, métodos públicos) hace que
        la excepción apunte a la línea que CAUSÓ el problema. Es la misma
        idea de Encapsulation (CAPA 4), Objects.requireNonNull
        (ReferencesAndNull, CAPA 9) y las cláusulas de guarda
        (Conditionals, CAPA 4).
    */
    record OrderLine(String sku, int quantity) {
        OrderLine {
            Objects.requireNonNull(sku, "sku");
            if (quantity <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser positiva: " + quantity);   // falla AL CREARSE
            }
        }
    }

    static boolean failFastExample() {
        try {
            new OrderLine("TEC-01", -3);
            return false;
        } catch (IllegalArgumentException e) {
            // La excepción señala la creación de la línea, no un cálculo de factura horas después.
            return e.getStackTrace()[0].getMethodName().equals("<init>");   // <init> = el constructor
        }
    }

    /*
        =================================================================
        CAPA 4) NO USES EXCEPCIONES PARA EL FLUJO NORMAL
        =================================================================

        Las excepciones son para lo EXCEPCIONAL. Usarlas como parte del
        flujo normal es un error de diseño (Effective Java: "usa
        excepciones solo para condiciones excepcionales"):

        - Es CONFUSO: quien lee espera que un catch maneje un problema, no
          el final normal de un ciclo.
        - Es LENTO: crear una excepción captura toda la pila de llamadas
          (el stack trace), algo muchísimo más caro que un if.
        - OCULTA BUGS: si el ciclo termina "cuando salta una excepción",
          otra excepción real del mismo tipo dentro del ciclo se confunde
          con el final normal.

        El ejemplo clásico: recorrer un arreglo hasta que "truene".
    */
    static int sumUntilItBreaks(int[] values) {
        int sum = 0;
        int i = 0;
        try {
            while (true) {
                sum += values[i++];          // termina cuando lanza ArrayIndexOutOfBounds
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return sum;                      // "fin normal" disfrazado de error
        }
    }

    static int sum(int[] values) {
        int sum = 0;
        for (int value : values) {           // el final del ciclo es parte del ciclo
            sum += value;
        }
        return sum;
    }

    static boolean noControlFlowExample() {
        int[] values = {10, 20, 30};
        // Dan lo mismo, pero solo la segunda dice lo que hace, y no puede confundir un bug con el final.
        return sumUntilItBreaks(values) == 60 && sum(values) == 60;
    }

    /*
        Variante del mismo error: preguntar con una excepción lo que se
        puede preguntar con un método. Si existe hasNextInt(), isEmpty(),
        containsKey() o getOrDefault(), úsalo en lugar de intentar y
        atrapar.
        (Atrapar NumberFormatException al convertir texto del usuario es
        aceptable: no hay un método estándar "isInteger" y el texto
        inválido sí es una situación excepcional para parseInt.)
    */

    /*
        =================================================================
        CAPA 5) ERRORES ESPERADOS: VALIDA Y REÚNE, NO LANCES UNO POR UNO
        =================================================================

        Que un usuario llene mal un formulario NO es excepcional: pasa
        todo el tiempo. Si validas lanzando una excepción en el primer
        error, el usuario corrige el correo, envía, descubre que la
        contraseña también estaba mal, corrige, envía, descubre la edad...

        Para errores ESPERADOS de entrada, valida TODO y devuelve la lista
        completa de problemas. Es un tipo de resultado, no una excepción
        (CustomExceptions, CAPA 9).

        Las excepciones se reservan para cuando alguien se SALTA la
        validación: si el código llega a crear un usuario con datos
        inválidos, eso sí es un bug, y el constructor debe fallar
        (CAPA 3). Las dos defensas conviven.
    */
    record Registration(String email, String password, int age) { }

    static List<String> validate(Registration form) {
        List<String> errors = new ArrayList<>();
        if (form.email() == null || !form.email().contains("@")) {
            errors.add("El correo no es válido");
        }
        if (form.password() == null || form.password().length() < 8) {
            errors.add("La contraseña debe tener al menos 8 caracteres");
        }
        if (form.age() < 18) {
            errors.add("Debes ser mayor de edad");
        }
        return errors;   // vacía = todo bien
    }

    static boolean collectErrorsExample() {
        List<String> errors = validate(new Registration("ana-correo", "123", 15));
        return errors.size() == 3                                     // los tres problemas a la vez
                && validate(new Registration("ana@correo.com", "segura123", 30)).isEmpty();
    }

    /*
        =================================================================
        CAPA 6) DEJA LOS OBJETOS EN UN ESTADO VÁLIDO AUNQUE FALLES
        =================================================================

        Si un método lanza una excepción a la MITAD de modificar un
        objeto, el objeto puede quedar a medias: ni como estaba, ni como
        debía quedar. Quien atrape la excepción e intente seguir usándolo
        trabajará con datos inconsistentes.

        La propiedad deseable se llama ATOMICIDAD ANTE FALLOS: si el
        método falla, el objeto queda EXACTAMENTE como estaba antes
        (Effective Java). Las técnicas, de la más simple a la más
        elaborada:
        1) VALIDAR TODO ANTES de modificar nada.
        2) Calcular el resultado en variables locales o en una copia, y
           aplicarlo al final, cuando ya no puede fallar.
        3) Objetos inmutables: si nunca cambian, nunca quedan a medias.
        4) Deshacer lo hecho si algo falla (como el rollback de la
           transacción en TryWithResources, CAPA 8).
    */
    static final class Cart {
        private static final int MAX_ITEMS = 5;
        private final List<String> items = new ArrayList<>();

        // MAL: agrega uno por uno y falla a la mitad.
        void addAllUnsafe(List<String> newItems) {
            for (String item : newItems) {
                if (items.size() >= MAX_ITEMS) {
                    throw new IllegalStateException("El carrito admite máximo " + MAX_ITEMS + " artículos");
                }
                items.add(item);
            }
        }

        // BIEN: valida todo primero; si falla, el carrito no cambia.
        void addAll(List<String> newItems) {
            if (items.size() + newItems.size() > MAX_ITEMS) {
                throw new IllegalStateException("El carrito admite máximo " + MAX_ITEMS + " artículos");
            }
            items.addAll(newItems);
        }

        int size() {
            return items.size();
        }
    }

    static boolean failureAtomicityExample() {
        List<String> tooMany = List.of("a", "b", "c", "d");

        Cart unsafe = new Cart();
        unsafe.addAllUnsafe(List.of("x", "y"));
        try {
            unsafe.addAllUnsafe(tooMany);
        } catch (IllegalStateException ignored) {
            // se espera el error; lo que importa es en qué estado quedó el carrito
        }

        Cart safe = new Cart();
        safe.addAll(List.of("x", "y"));
        try {
            safe.addAll(tooMany);
        } catch (IllegalStateException ignored) {
            // ídem
        }

        return unsafe.size() == 5     // ¡quedó a medias: 3 de los 4 nuevos entraron!
                && safe.size() == 2;  // quedó exactamente como estaba
    }

    /*
        =================================================================
        CAPA 7) REGISTRA (LOG) EL ERROR UNA SOLA VEZ, EN EL LUGAR CORRECTO
        =================================================================

        En una aplicación real, los errores se REGISTRAN en un log
        (tools/Logging) para poder investigarlos después.

        El antipatrón "registrar y relanzar": cada capa atrapa la
        excepción, la escribe en el log, y la vuelve a lanzar.
            catch (Exception e) { log.error("falló", e); throw e; }
        Resultado: el MISMO error aparece tres, cinco, diez veces en el
        log. Parece que hubo muchos errores, los registros se vuelven
        ruido, y cuesta encontrar el que importa.

        La regla: REGISTRA EN EL LUGAR DONDE SE MANEJA (donde termina el
        viaje de la excepción), normalmente en el nivel más alto. Las
        capas intermedias o propagan, o traducen con causa (CAPA 8), pero
        no registran. Y al registrar, pasa el objeto excepción completo
        (no solo getMessage()), para conservar el stack trace y las causas.
    */
    static final class FakeLog {
        final List<String> entries = new ArrayList<>();

        void error(String message, Throwable error) {
            entries.add(message + " -> " + error.getClass().getSimpleName());
        }
    }

    static void repositoryNoisy(FakeLog log) {
        try {
            throw new IllegalStateException("Base de datos no disponible");
        } catch (IllegalStateException e) {
            log.error("repositorio falló", e);   // registra...
            throw e;                             // ...y relanza
        }
    }

    static void serviceNoisy(FakeLog log) {
        try {
            repositoryNoisy(log);
        } catch (IllegalStateException e) {
            log.error("servicio falló", e);      // registra OTRA vez
            throw e;
        }
    }

    static void repositoryQuiet() {
        throw new IllegalStateException("Base de datos no disponible");   // solo propaga
    }

    static void serviceQuiet() {
        repositoryQuiet();                       // ni registra ni atrapa: no tiene nada que decidir
    }

    static boolean logOnceExample() {
        FakeLog noisy = new FakeLog();
        try {
            serviceNoisy(noisy);
        } catch (IllegalStateException e) {
            noisy.error("petición falló", e);    // el nivel alto también registra
        }

        FakeLog quiet = new FakeLog();
        try {
            serviceQuiet();
        } catch (IllegalStateException e) {
            quiet.error("petición falló", e);    // UN solo registro, donde se maneja
        }

        return noisy.entries.size() == 3 && quiet.entries.size() == 1;
    }

    /*
        =================================================================
        CAPA 8) TRADUCE LAS EXCEPCIONES EN LOS LÍMITES DE CADA CAPA
        =================================================================

        Una aplicación se organiza en CAPAS (v14_architecture/): la que
        muestra cosas al usuario, la de reglas de negocio, la que habla
        con la base de datos o la red. Cada capa debe hablar su propio
        idioma:

        - Si la pantalla recibe un SQLException o un SocketTimeoutException,
          SABE que hay una base de datos o una red debajo. Cambiar de
          base de datos rompería la pantalla.
        - Por eso, en el LÍMITE de cada capa, las excepciones de abajo se
          TRADUCEN a las de la capa actual, conservando la causa
          (CustomExceptions, CAPA 6).

        Y en el nivel más alto hay UN manejador central que convierte
        cualquier excepción en una respuesta para el usuario: un mensaje
        claro para los errores conocidos, y uno genérico ("algo salió mal,
        intenta más tarde") para lo inesperado, que además se registra.
        Así, ningún detalle técnico llega al usuario, y no hay que repetir
        try/catch en cada pantalla. (Es lo que hacen los frameworks web,
        como @ControllerAdvice en Spring.)
    */
    static class OrderException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        OrderException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Capa de datos: habla de "conexiones" y "tablas".
    static String loadOrderFromDatabase(int orderId) {
        throw new IllegalStateException("Conexión rechazada por el servidor de base de datos");
    }

    // Capa de negocio: traduce a su idioma, con la causa.
    static String findOrder(int orderId) {
        try {
            return loadOrderFromDatabase(orderId);
        } catch (IllegalStateException e) {
            throw new OrderException("No se pudo consultar el pedido " + orderId, e);
        }
    }

    // Manejador central: un solo lugar decide qué ve el usuario.
    static String respondToUser(int orderId, FakeLog log) {
        try {
            return findOrder(orderId);
        } catch (OrderException e) {
            log.error("Error conocido al consultar pedido", e);
            return "No pudimos mostrar tu pedido. Intenta de nuevo en unos minutos.";
        } catch (RuntimeException e) {
            log.error("Error inesperado", e);   // aquí SÍ se atrapa en general: es el nivel más alto
            return "Algo salió mal. Ya lo estamos revisando.";
        }
    }

    static boolean translateAtBoundaryExample() {
        FakeLog log = new FakeLog();
        String shown = respondToUser(1042, log);

        return shown.equals("No pudimos mostrar tu pedido. Intenta de nuevo en unos minutos.")
                && !shown.contains("Conexión")      // el detalle técnico no llega al usuario
                && log.entries.size() == 1;
    }

    /*
        =================================================================
        CAPA 9) INTERRUPTEDEXCEPTION: NO LA IGNORES
        =================================================================

        Métodos como Thread.sleep o los que esperan a otros hilos lanzan
        InterruptedException (checked). Significa: "alguien pidió que este
        hilo se detenga" (por ejemplo, porque la aplicación se está
        apagando).

        Al lanzarla, Java BORRA la "marca de interrupción" del hilo. Si te
        la tragas, esa petición de detenerse se pierde, y el hilo sigue
        trabajando como si nada: la aplicación no se apaga.

        Si no puedes propagarla, la forma correcta es RESTAURAR la marca:
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // vuelve a marcar el hilo
                return;                               // y termina lo antes posible
            }
        Los detalles son de v13_concurrency/ThreadBasics; la regla vale ya.
    */
    static boolean sleepRespectingInterruption() {
        try {
            Thread.sleep(10);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // restaura la marca para quien siga
            return false;
        }
    }

    static boolean interruptedExample() {
        Thread.currentThread().interrupt();                         // alguien pidió detener este hilo
        boolean completed = sleepRespectingInterruption();          // sleep lanza de inmediato
        boolean flagRestored = Thread.interrupted();                // la marca sigue ahí (y esta llamada la limpia)
        return !completed && flagRestored;
    }

    /*
        =================================================================
        CAPA 10) DOCUMENTA LO QUE PUEDE FALLAR
        =================================================================

        - Las checked ya quedan documentadas en throws.
        - Las UNCHECKED que quien llama debería conocer, documéntalas en
          el Javadoc con @throws (HowJavaWorks, CAPA 8):

            /**
             * Retira dinero de la cuenta.
             *
             * @param amount cantidad a retirar; debe ser positiva
             * @throws IllegalArgumentException si amount no es positiva
             * @throws InsufficientFundsException si el saldo no alcanza
             * /

        Quien use tu método sabe qué puede pasar sin leer su código. Y
        documentar te obliga a pensar si de verdad son los errores
        correctos.
    */

    /*
        =================================================================
        CAPA 11) LISTA DE DECISIONES
        =================================================================

        Al escribir código que puede fallar:

        | Pregunta                                                  | Respuesta                                  |
        |-----------------------------------------------------------|--------------------------------------------|
        | ¿Es un bug (null, índice, argumento inválido)?            | unchecked del JDK; no la atrapes: corrígelo|
        | ¿Es un dato del usuario que suele venir mal?              | valida y reúne los errores (CAPA 5)        |
        | ¿Es un resultado normal del negocio (pago rechazado)?     | tipo de resultado sealed, o excepción propia con datos |
        | ¿Es un fallo externo (archivo, red, base de datos)?       | checked del JDK; tradúcela en el límite    |
        | ¿Puedo hacer algo útil con ella aquí?                     | sí: manéjala; no: propágala                |
        | ¿Voy a lanzar otra a partir de esta?                      | siempre con la causa                       |
        | ¿Abrí un recurso?                                         | try-with-resources                         |
        | ¿Dónde la registro?                                       | una vez, donde se maneja                   |
        | ¿Qué ve el usuario?                                       | un mensaje claro, nunca el detalle técnico |
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) catch vacío (CAPA 1).
        2) catch (Exception e) en medio del código, ocultando bugs (CAPA 2).
        3) Validar tarde, lejos de donde entró el dato (CAPA 3).
        4) Terminar ciclos o decidir el flujo con excepciones (CAPA 4).
        5) Lanzar una excepción por el primer error de un formulario
           (CAPA 5).
        6) Dejar objetos a medio modificar al fallar (CAPA 6).
        7) Registrar y relanzar en cada capa (CAPA 7).
        8) Registrar solo getMessage() y perder el stack trace (CAPA 7).
        9) Dejar que excepciones técnicas crucen capas o lleguen al
           usuario (CAPA 8).
        10) Tragarse InterruptedException (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Nunca te tragues una excepción: manéjala, propágala o tradúcela.
        - Atrapa lo específico; Exception solo en el nivel más alto.
        - Falla rápido: valida donde entra el dato.
        - Excepciones solo para lo excepcional, nunca para el flujo normal.
        - Errores esperados de entrada: valida todo y devuelve la lista.
        - Si fallas, deja el objeto como estaba (valida antes de modificar).
        - Registra una vez, donde se maneja, con la excepción completa.
        - Traduce en los límites de cada capa (con causa) y ten un
          manejador central que decida qué ve el usuario.
        - InterruptedException: restaura la marca con interrupt().
        - Documenta con @throws lo que quien llama debe saber.
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
