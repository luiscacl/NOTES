package v3_exceptions.part2_in_practice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/*
    =====================================================================
    TRY-WITH-RESOURCES — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v3_exceptions/: guía 4 de 5  (part2_in_practice)
    Anterior:  part2_in_practice/CustomExceptions
    Siguiente: part2_in_practice/ErrorHandlingBestPractices

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es un recurso y por qué hay que cerrarlo
        CAPA 2)  La forma antigua: try/finally y sus problemas
        CAPA 3)  try-with-resources
        CAPA 4)  Varios recursos: se cierran en orden inverso
        CAPA 5)  Excepciones suprimidas: ningún error se pierde
        CAPA 6)  Con catch y finally: qué se ejecuta primero
        CAPA 7)  Si un recurso no se puede abrir
        CAPA 8)  Tus propios recursos: AutoCloseable y Closeable
        CAPA 9)  Qué cerrar y qué no
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class TryWithResources {

    public static void main(String[] args) {
        check(realFileExample(), "CAPA 3");
        check(reverseOrderExample(), "CAPA 4");
        check(suppressedExample(), "CAPA 5");
        check(catchFinallyOrderExample(), "CAPA 6");
        check(failedOpenExample(), "CAPA 7");
        check(transactionExample(), "CAPA 8");
        System.out.println("TryWithResources: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN RECURSO Y POR QUÉ HAY QUE CERRARLO
        =================================================================

        Un RECURSO es algo que tu programa le pide prestado al sistema
        operativo o a otro programa, y que hay que DEVOLVER explícitamente
        al terminar:
        - un archivo abierto
        - una conexión de red o a una base de datos
        - un bloqueo (lock) entre hilos
        - un Scanner o BufferedReader sobre un archivo

        Por qué hay que cerrarlos, aunque Java tenga recolector de basura:
        1) El recolector libera MEMORIA, no recursos del sistema operativo.
           Y no hay ninguna garantía de CUÁNDO pasará (y finalize, que
           antes "intentaba" cerrar, está obsoleto: ObjectContract,
           CAPA 11).
        2) Los recursos son LIMITADOS: un proceso solo puede tener cierta
           cantidad de archivos abiertos, y una base de datos solo acepta
           cierto número de conexiones. Un programa que no cierra se queda
           sin ellos ("Too many open files") después de horas funcionando.
        3) Cerrar también TERMINA el trabajo: un archivo que se escribe con
           un buffer no guarda los últimos datos en disco hasta que se
           cierra (o se vacía con flush). Sin close, el archivo queda
           incompleto.
        4) Mientras está abierto, puede BLOQUEAR a otros: en Windows, un
           archivo abierto no se puede borrar ni mover.

        Para ver el orden en que pasan las cosas, esta guía usa un recurso
        de prueba que anota cada acción en una lista:
    */
    static final class TrackedResource implements AutoCloseable {
        private final String name;
        private final List<String> log;
        private final boolean failOnClose;

        TrackedResource(String name, List<String> log, boolean failOnOpen, boolean failOnClose) {
            if (failOnOpen) {
                log.add("no se pudo abrir " + name);
                throw new IllegalStateException("No se pudo abrir " + name);
            }
            this.name = name;
            this.log = log;
            this.failOnClose = failOnClose;
            log.add("abre " + name);
        }

        void use() {
            log.add("usa " + name);
        }

        @Override
        public void close() {
            log.add("cierra " + name);
            if (failOnClose) {
                throw new IllegalStateException("Falló al cerrar " + name);
            }
        }
    }

    static TrackedResource open(String name, List<String> log) {
        return new TrackedResource(name, log, false, false);
    }

    /*
        =================================================================
        CAPA 2) LA FORMA ANTIGUA: TRY/FINALLY Y SUS PROBLEMAS
        =================================================================

        Antes de Java 7, el patrón era cerrar en un finally (que se
        ejecuta pase lo que pase: WhatAreExceptions, CAPA 6):

            Resource r = null;
            try {
                r = new Resource();
                ... trabajar ...
            } finally {
                if (r != null) {
                    r.close();
                }
            }

        Funciona, pero tiene tres problemas:
        1) Es LARGO y repetitivo, y hay que acordarse del if (r != null).
        2) Con varios recursos se vuelve una pirámide de try/finally
           anidados, porque cada close puede fallar.
        3) EL PEOR: si el trabajo lanza una excepción y luego close()
           TAMBIÉN lanza otra, la del close REEMPLAZA a la original. El
           error real desaparece y solo ves "falló al cerrar".
    */
    static void oldStyle(List<String> log) {
        TrackedResource resource = null;
        try {
            resource = new TrackedResource("archivo", log, false, true);
            resource.use();
            throw new IllegalArgumentException("Error real en el trabajo");
        } finally {
            if (resource != null) {
                resource.close();   // lanza... y la excepción del trabajo se PIERDE
            }
        }
    }

    /*
        =================================================================
        CAPA 3) TRY-WITH-RESOURCES
        =================================================================

        Desde Java 7:

            try (Resource r = new Resource()) {
                ... trabajar con r ...
            }   // aquí Java llama a r.close() automáticamente

        - El recurso se declara entre los PARÉNTESIS del try.
        - Al salir del bloque, Java llama a close(), SIEMPRE: si terminó
          bien, si lanzó una excepción, si hubo un return.
        - Solo se permite con objetos que implementen AutoCloseable
          (CAPA 8). Prácticamente todo lo que se debe cerrar en el JDK lo
          implementa.
        - La variable solo existe dentro del try y es implícitamente final.
        - Desde Java 9 puedes usar una variable que ya existía (si es
          efectivamente final): try (resource) { ... }

        Resuelve los tres problemas de la CAPA 2: es corto, maneja varios
        recursos (CAPA 4) y no pierde ninguna excepción (CAPA 5).

        Un ejemplo real con un archivo temporal: escribir y luego leer.
    */
    static List<String> writeAndReadBack(List<String> lines) throws IOException {
        Path file = Files.createTempFile("guia-", ".txt");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }   // close() vacía el buffer al disco: sin esto, el archivo podría quedar vacío

            List<String> read = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    read.add(line);
                }
            }
            return read;
        } finally {
            Files.deleteIfExists(file);   // limpieza del archivo temporal
        }
    }

    static boolean realFileExample() {
        List<String> log = new ArrayList<>();
        TrackedResource existing = open("conexión", log);
        try (existing) {                  // Java 9+: una variable que ya existía
            existing.use();
        }

        try {
            return writeAndReadBack(List.of("primera línea", "segunda línea"))
                    .equals(List.of("primera línea", "segunda línea"))
                    && log.equals(List.of("abre conexión", "usa conexión", "cierra conexión"));
        } catch (IOException e) {
            return false;
        }
    }

    /*
        =================================================================
        CAPA 4) VARIOS RECURSOS: SE CIERRAN EN ORDEN INVERSO
        =================================================================

        Se pueden declarar varios, separados por punto y coma:

            try (Connection connection = open();
                 Statement statement = connection.createStatement()) {
                ...
            }

        Se ABREN en el orden escrito y se CIERRAN en orden INVERSO: el
        último que se abrió es el primero que se cierra.

        Por qué: los recursos de abajo suelen DEPENDER de los de arriba
        (la consulta se creó a partir de la conexión; el lector se creó
        sobre el archivo). Cerrar la conexión antes que la consulta
        dejaría a la consulta usando algo que ya no existe. Cerrando al
        revés, cada recurso se cierra mientras lo que necesita sigue vivo.
        Es la misma lógica de la pila de llamadas (Methods, CAPA 9).
    */
    static boolean reverseOrderExample() {
        List<String> log = new ArrayList<>();
        try (TrackedResource connection = open("conexión", log);
             TrackedResource statement = open("consulta", log)) {
            connection.use();
            statement.use();
        }

        return log.equals(List.of(
                "abre conexión", "abre consulta",
                "usa conexión", "usa consulta",
                "cierra consulta", "cierra conexión"));   // al revés de como se abrieron
    }

    /*
        =================================================================
        CAPA 5) EXCEPCIONES SUPRIMIDAS: NINGÚN ERROR SE PIERDE
        =================================================================

        El mismo escenario del problema 3 de la CAPA 2: el trabajo falla y
        luego close() también falla. try-with-resources lo resuelve así:

        - La excepción que se PROPAGA es la ORIGINAL (la del trabajo), que
          es la que explica qué salió mal.
        - La de close() no se pierde: se agrega a la original como
          excepción SUPRIMIDA. Se consulta con getSuppressed(), y aparece
          en el stack trace como "Suppressed: ...".

        Así ves las dos, con la importante primero. (Puedes agregar
        suprimidas a mano con addSuppressed, pero casi nunca hace falta.)
    */
    static boolean suppressedExample() {
        // Forma antigua: la excepción real se pierde.
        Exception fromOldStyle = null;
        try {
            oldStyle(new ArrayList<>());
        } catch (RuntimeException e) {
            fromOldStyle = e;
        }
        boolean oldStyleLosesOriginal = fromOldStyle instanceof IllegalStateException
                && fromOldStyle.getMessage().equals("Falló al cerrar archivo");

        // try-with-resources: la original llega, y la del close va adentro como suprimida.
        Exception fromNewStyle = null;
        try (TrackedResource resource = new TrackedResource("archivo", new ArrayList<>(), false, true)) {
            resource.use();
            throw new IllegalArgumentException("Error real en el trabajo");
        } catch (RuntimeException e) {
            fromNewStyle = e;
        }
        boolean newStyleKeepsBoth = fromNewStyle instanceof IllegalArgumentException
                && fromNewStyle.getMessage().equals("Error real en el trabajo")
                && fromNewStyle.getSuppressed().length == 1
                && fromNewStyle.getSuppressed()[0].getMessage().equals("Falló al cerrar archivo");

        return oldStyleLosesOriginal && newStyleKeepsBoth;
    }

    /*
        =================================================================
        CAPA 6) CON CATCH Y FINALLY: QUÉ SE EJECUTA PRIMERO
        =================================================================

        Un try-with-resources puede tener catch y finally, como cualquier
        try. El orden es:
            1) el cuerpo del try
            2) se CIERRAN los recursos
            3) el catch (si hubo excepción)
            4) el finally

        Es decir: cuando llegas al catch, los recursos YA están cerrados.
        Por qué: el catch debe poder manejar también una excepción lanzada
        por close(), y para eso el cierre tiene que haber ocurrido antes.
        Consecuencia práctica: dentro del catch no puedes seguir usando el
        recurso (para eso ni siquiera existe la variable: su alcance es
        solo el cuerpo del try).
    */
    static boolean catchFinallyOrderExample() {
        List<String> log = new ArrayList<>();
        try (TrackedResource resource = open("archivo", log)) {
            resource.use();
            throw new IllegalStateException("falla");
        } catch (IllegalStateException e) {
            log.add("catch");
        } finally {
            log.add("finally");
        }

        return log.equals(List.of("abre archivo", "usa archivo", "cierra archivo", "catch", "finally"));
    }

    /*
        =================================================================
        CAPA 7) SI UN RECURSO NO SE PUEDE ABRIR
        =================================================================

        Si la CREACIÓN de un recurso lanza una excepción (el archivo no
        existe, la conexión fue rechazada):
        - ese recurso NUNCA existió, así que no hay nada que cerrar;
        - los recursos declarados ANTES que él sí se abrieron, y Java los
          cierra igual;
        - el cuerpo del try no se ejecuta.

        Es justo lo que con la forma antigua había que programar a mano con
        try/finally anidados.
    */
    static boolean failedOpenExample() {
        List<String> log = new ArrayList<>();
        boolean propagated;
        try (TrackedResource first = open("conexión", log);
             TrackedResource second = new TrackedResource("consulta", log, true, false)) {
            first.use();          // nunca se ejecuta
            second.use();
            propagated = false;
        } catch (IllegalStateException e) {
            propagated = e.getMessage().equals("No se pudo abrir consulta");
        }

        return propagated && log.equals(List.of("abre conexión", "no se pudo abrir consulta", "cierra conexión"));
    }

    /*
        =================================================================
        CAPA 8) TUS PROPIOS RECURSOS: AUTOCLOSEABLE Y CLOSEABLE
        =================================================================

        Para que una clase tuya funcione en try-with-resources, implementa
        una de estas interfaces:

        | Interface     | Paquete   | close() puede lanzar | Para                          |
        |---------------|-----------|----------------------|-------------------------------|
        | AutoCloseable | java.lang | Exception            | cualquier recurso (Java 7)    |
        | Closeable     | java.io   | IOException          | recursos de E/S (es su hija)  |

        Al implementar close(), puedes declarar MENOS excepciones que la
        interface (CheckedVsUnchecked, CAPA 8). Si tu close no puede
        fallar con checked, no declares throws: así quien la use no tendrá
        que atrapar nada extra.

        Buenas prácticas para close():
        - Que se pueda llamar dos veces sin error (Closeable lo exige; con
          AutoCloseable es muy recomendable).
        - Que libere todo lo que pueda aunque una parte falle.

        try-with-resources no es solo para archivos: sirve para cualquier
        cosa que tenga un "inicio" y un "fin" obligatorio. Un ejemplo
        profesional: una TRANSACCIÓN que se revierte sola (rollback) si
        no se confirmó (commit). Si el código falla a la mitad, close()
        deshace todo; nadie puede olvidar el rollback.
    */
    static final class Transaction implements AutoCloseable {
        private final List<String> log;
        private boolean committed;
        private boolean closed;

        Transaction(List<String> log) {
            this.log = log;
            log.add("BEGIN");
        }

        void execute(String operation) {
            log.add(operation);
        }

        void commit() {
            committed = true;
            log.add("COMMIT");
        }

        @Override
        public void close() {                // sin throws: no puede fallar con checked
            if (closed) {
                return;                      // llamar dos veces no hace nada
            }
            closed = true;
            if (!committed) {
                log.add("ROLLBACK");         // si nadie confirmó, se deshace todo
            }
        }
    }

    static void transfer(List<String> log, boolean failInTheMiddle) {
        try (Transaction tx = new Transaction(log)) {
            tx.execute("restar 500 de la cuenta A");
            if (failInTheMiddle) {
                throw new IllegalStateException("Se cayó la conexión a mitad de la transferencia");
            }
            tx.execute("sumar 500 a la cuenta B");
            tx.commit();
        }
    }

    static boolean transactionExample() {
        List<String> ok = new ArrayList<>();
        transfer(ok, false);

        List<String> failed = new ArrayList<>();
        try {
            transfer(failed, true);
        } catch (IllegalStateException e) {
            // esperado: el dinero no quedó a medias
        }

        return ok.equals(List.of("BEGIN", "restar 500 de la cuenta A", "sumar 500 a la cuenta B", "COMMIT"))
                && failed.equals(List.of("BEGIN", "restar 500 de la cuenta A", "ROLLBACK"));
    }

    /*
        =================================================================
        CAPA 9) QUÉ CERRAR Y QUÉ NO
        =================================================================

        La regla: CIERRA LO QUE TÚ ABRISTE, en el mismo método donde lo
        abriste. Quien crea el recurso es su "dueño" y es responsable de
        cerrarlo.

        - Si un método RECIBE un recurso como parámetro, normalmente NO lo
          cierra: el que lo creó todavía puede querer usarlo.
        - Si un método DEVUELVE un recurso abierto, la responsabilidad de
          cerrarlo pasa a quien lo recibe (y debe quedar claro en el
          nombre o la documentación).
        - NO cierres System.in, System.out ni System.err: los abrió la JVM,
          y cerrarlos los inutiliza para todo el programa
          (InputAndOutput, CAPA 7).
        - Un objeto que solo usa memoria (una List, un StringBuilder) no es
          un recurso: no tiene close() y no hace falta nada.

        En proyectos reales, frameworks como Spring abren y cierran por ti
        las conexiones a bases de datos, pero el principio es el mismo.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) No cerrar un recurso "porque el recolector de basura lo hará"
           (CAPA 1).

        2) Cerrar en un try/finally manual, perdiendo la excepción
           original si close() falla (CAPA 2).

        3) Olvidar que un writer con buffer necesita cerrarse (o flush)
           para guardar los datos (CAPA 3).

        4) Abrir el recurso ANTES del try y declararlo con otra variable:
           si algo falla entre la apertura y el try, nunca se cierra.
           Créalo dentro de los paréntesis del try.

        5) Ignorar las excepciones suprimidas al investigar un error
           (CAPA 5).

        6) Intentar usar el recurso dentro del catch: ya está cerrado
           (CAPA 6).

        7) close() que lanza checked sin necesidad, o que falla si se llama
           dos veces (CAPA 8).

        8) Cerrar recursos que no te pertenecen, incluido System.in
           (CAPA 9).

        9) Olvidar el rollback de una operación a medias: un AutoCloseable
           lo garantiza (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Recurso: algo prestado por el sistema (archivo, conexión,
          bloqueo). Hay que cerrarlo; el recolector no lo hace.
        - try (Recurso r = ...) { } llama a close() siempre, al salir.
        - Varios recursos: se abren en orden y se cierran al revés.
        - Si el trabajo y el close fallan, se propaga la original y la del
          close queda en getSuppressed(). Nada se pierde.
        - Orden: cuerpo, cierre, catch, finally.
        - Si un recurso no se puede abrir, los anteriores se cierran igual.
        - Implementa AutoCloseable (o Closeable) para tus recursos;
          close() sin throws si puede, e idempotente.
        - Cierra lo que tú abriste; no cierres lo que recibes ni System.in.
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
