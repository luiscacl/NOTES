package v3_exceptions.part1_concepts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/*
    =====================================================================
    EXCEPCIONES CHECKED VS UNCHECKED — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v3_exceptions/: guía 2 de 5  (part1_concepts)
    Anterior:  part1_concepts/WhatAreExceptions
    Siguiente: part2_in_practice/CustomExceptions

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Dos familias de excepciones
        CAPA 2)  La regla de las checked: "atrápala o decláralo"
        CAPA 3)  throws: declarar lo que un método puede lanzar
        CAPA 4)  Por qué existen las dos familias
        CAPA 5)  Atrapar o propagar: cómo decidir
        CAPA 6)  El problema de las checked con lambdas
        CAPA 7)  Envolver una checked en una unchecked
        CAPA 8)  Checked, herencia e interfaces
        CAPA 9)  El debate: ¿fueron una buena idea?
        CAPA 10) Cómo elegir para tu propio código
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class CheckedVsUnchecked {

    public static void main(String[] args) {
        check(familiesExample(), "CAPA 1");
        check(catchOrDeclareExample(), "CAPA 2");
        check(throwsExample(), "CAPA 3");
        check(handleOrPropagateExample(), "CAPA 5");
        check(lambdaProblemExample(), "CAPA 6");
        check(wrappingExample(), "CAPA 7");
        check(inheritanceExample(), "CAPA 8");
        System.out.println("CheckedVsUnchecked: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) DOS FAMILIAS DE EXCEPCIONES
        =================================================================

        En la jerarquía (WhatAreExceptions, CAPA 8) hay una división
        que cambia cómo se escribe el código:

        - UNCHECKED ("no verificadas"): RuntimeException y todas sus
          hijas, más Error. El compilador NO te obliga a nada con ellas.
              NullPointerException, IllegalArgumentException,
              IllegalStateException, IndexOutOfBoundsException...

        - CHECKED ("verificadas"): todas las demás Exception (las que NO
          heredan de RuntimeException). El COMPILADOR VERIFICA que las
          atiendas.
              IOException (y sus hijas: FileNotFoundException,
              NoSuchFileException...), SQLException,
              InterruptedException, TimeoutException...

        Cómo saber de qué familia es una excepción: ¿hereda de
        RuntimeException? Entonces es unchecked. Si no, checked.
    */
    static boolean isChecked(Throwable t) {
        return t instanceof Exception && !(t instanceof RuntimeException);
    }

    static boolean familiesExample() {
        return isChecked(new IOException())
                && isChecked(new InterruptedException())
                && !isChecked(new IllegalArgumentException())
                && !isChecked(new NullPointerException())
                && !isChecked(new StackOverflowError());   // los Error tampoco son checked
    }

    /*
        =================================================================
        CAPA 2) LA REGLA DE LAS CHECKED: "ATRÁPALA O DECLÁRALO"
        =================================================================

        Si un método llama a algo que puede lanzar una excepción CHECKED,
        el compilador le exige UNA de dos cosas:
        1) ATRAPARLA con try/catch, o
        2) DECLARAR con throws que él también puede lanzarla, pasándole
           la responsabilidad a quien lo llame.

        Si no hace ninguna, NO COMPILA:
            String read() {
                return Files.readString(Path.of("config.txt"));
            }
            // ERROR: unreported exception IOException; must be caught or declared to be thrown

        Por qué: leer un archivo PUEDE fallar por razones que tu programa
        no controla (no existe, no hay permisos, el disco falló). Java
        quiere que pienses en ese caso ANTES de que pase, no cuando un
        usuario lo encuentre.

        Con una UNCHECKED, el compilador no dice nada: Integer.parseInt
        puede lanzar NumberFormatException y puedes llamarlo sin try.

        Dato curioso: el compilador también rechaza ATRAPAR una checked
        que el try no puede lanzar:
            try { int x = 1; } catch (IOException e) { }
            // ERROR: exception IOException is never thrown in body of corresponding try statement
        Con las unchecked no pasa (cualquier código podría lanzarlas).
    */
    static String readOrDefault(Path path, String defaultValue) {
        try {
            return Files.readString(path);          // puede lanzar IOException (checked)
        } catch (IOException e) {                   // opción 1: atraparla
            return defaultValue;
        }
    }

    static boolean catchOrDeclareExample() {
        Path missing = Path.of("este-archivo-no-existe-" + System.nanoTime() + ".txt");
        return readOrDefault(missing, "configuración por defecto").equals("configuración por defecto");
    }

    /*
        =================================================================
        CAPA 3) THROWS: DECLARAR LO QUE UN MÉTODO PUEDE LANZAR
        =================================================================

            String readConfig(Path path) throws IOException { ... }
                                         ^^^^^^^^^^^^^^^^^^
                                         "quien me llame debe estar listo
                                          para un IOException"

        throws (con s) va en la FIRMA del método y DECLARA; throw (sin s)
        va en el cuerpo y LANZA. Se confunden mucho.

        - Se pueden declarar varias: throws IOException, InterruptedException
        - throws es parte del CONTRATO del método, igual que sus
          parámetros y su retorno: quien lo llama sabe qué puede salir mal
          sin leer el código.
        - Declarar una UNCHECKED en throws se permite y a veces se hace
          para documentar (throws IllegalArgumentException), pero no obliga
          a nadie a nada. Lo usual es documentarla en el Javadoc con @throws.
        - Declarar un tipo padre cubre a sus hijas: throws IOException
          cubre NoSuchFileException.
    */
    static String readConfig(Path path) throws IOException {   // opción 2: declararla
        return Files.readString(path).strip();
    }

    static boolean throwsExample() {
        Path missing = Path.of("tampoco-existe-" + System.nanoTime() + ".txt");
        try {
            readConfig(missing);
            return false;
        } catch (NoSuchFileException e) {           // la hija específica que lanzó Files
            return e.getMessage().contains("tampoco-existe");
        } catch (IOException e) {                   // cualquier otro problema de lectura
            return false;
        }
    }

    /*
        =================================================================
        CAPA 4) POR QUÉ EXISTEN LAS DOS FAMILIAS
        =================================================================

        La idea original de Java separa dos tipos de problema muy
        distintos:

        UNCHECKED = ERRORES DE PROGRAMACIÓN (bugs).
        Un null donde no debía, un índice fuera de rango, un argumento
        inválido. No se "manejan": se CORRIGEN en el código. Si el
        compilador obligara a atrapar NullPointerException, habría que
        poner try/catch en casi cada línea del programa, y además no
        habría nada razonable que hacer en el catch.

        CHECKED = CONDICIONES ESPERABLES FUERA DE TU CONTROL.
        El archivo no existe, la red se cayó, el servidor tardó demasiado.
        Tu código está bien escrito y aun así puede pasar. Quien llama
        PUEDE hacer algo útil: reintentar, usar un valor por defecto,
        pedirle al usuario otro archivo. Por eso Java obliga a pensar en
        ello.

        ERROR = LA JVM ESTÁ EN PROBLEMAS (sin memoria, pila llena).
        No es tu lógica ni algo que puedas resolver: no se atrapa.

        | Familia    | Significa                     | Qué hacer              | Ejemplo                     |
        |------------|-------------------------------|------------------------|-----------------------------|
        | Unchecked  | bug en el código              | corregir el código     | NullPointerException        |
        | Checked    | algo externo puede fallar     | decidir cómo responder | IOException                 |
        | Error      | la JVM no puede continuar     | nada (no atrapar)      | OutOfMemoryError            |

        ¿Y por qué Integer.parseInt lanza una UNCHECKED, si el texto puede
        venir mal de fuera? Porque se considera responsabilidad de quien
        llama validar antes (o atraparla si quiere). Los límites no
        siempre son perfectos; es una convención, no una ley física.
    */

    /*
        =================================================================
        CAPA 5) ATRAPAR O PROPAGAR: CÓMO DECIDIR
        =================================================================

        Ante una checked, la pregunta es: "¿puedo hacer algo ÚTIL con este
        error AQUÍ?"

        - SÍ -> atrápala y responde: reintentar, usar un valor por
          defecto, traducirla a un mensaje para el usuario.
        - NO -> declárala con throws y deja que suba hasta quien sí
          pueda decidir.

        Lo que NUNCA se debe hacer es atraparla solo para que el
        compilador deje de quejarse:
            try { ... } catch (IOException e) { }   // la "trago" y sigo como si nada
        Eso es peor que no tener excepciones: el error desaparece sin
        rastro (ErrorHandlingBestPractices).

        Ejemplo: un componente de bajo nivel lee archivos y PROPAGA (no
        sabe qué hacer si falla); el de alto nivel decide la política.
    */
    static List<String> loadLines(Path path) throws IOException {   // bajo nivel: propaga
        return Files.readAllLines(path);
    }

    static List<String> loadUsersOrEmpty(Path path) {                // alto nivel: decide
        try {
            return loadLines(path);
        } catch (IOException e) {
            return List.of();   // política del negocio: sin archivo, sin usuarios (y en código real, se registraría el error)
        }
    }

    static boolean handleOrPropagateExample() {
        Path missing = Path.of("usuarios-" + System.nanoTime() + ".txt");
        return loadUsersOrEmpty(missing).isEmpty();
    }

    /*
        =================================================================
        CAPA 6) EL PROBLEMA DE LAS CHECKED CON LAMBDAS
        =================================================================

        Las lambdas (v7_functional/) implementan interfaces funcionales como
        Consumer o Function, cuyos métodos (accept, apply) NO declaran
        throws. Una lambda no puede lanzar algo que su interface no
        declara. Así que esto no compila:

            paths.forEach(path -> Files.readString(path));
            // ERROR: unreported exception IOException; must be caught or declared to be thrown

        Y ponerle throws al método que contiene el forEach no ayuda: la
        excepción tendría que salir de la LAMBDA, y la lambda es un
        Consumer que no la declara.

        Esta es la queja más común contra las checked en Java moderno: no
        se llevan bien con lambdas ni streams. La solución estándar es
        atraparla DENTRO de la lambda y envolverla en una unchecked
        (CAPA 7).
    */
    static List<String> readAll(List<Path> paths) {
        List<String> contents = new ArrayList<>();
        paths.forEach(path -> {
            try {
                contents.add(Files.readString(path));
            } catch (IOException e) {
                throw new UncheckedIOException(e);   // la checked viaja envuelta en una unchecked
            }
        });
        return contents;
    }

    static boolean lambdaProblemExample() {
        try {
            readAll(List.of(Path.of("no-existe-" + System.nanoTime() + ".txt")));
            return false;
        } catch (UncheckedIOException e) {
            return e.getCause() instanceof NoSuchFileException;   // la original sigue adentro
        }
    }

    /*
        =================================================================
        CAPA 7) ENVOLVER UNA CHECKED EN UNA UNCHECKED
        =================================================================

        Envolver (wrapping) es crear una excepción nueva que lleva a la
        original como CAUSA:
            throw new UncheckedIOException(e);            // para IOException, existe esta
            throw new RuntimeException("contexto", e);    // genérica, para cualquier otra

        Cuándo tiene sentido:
        - Dentro de lambdas y streams (CAPA 6).
        - Cuando la checked es un detalle de implementación que no debe
          "contaminar" capas superiores: que una pantalla tenga que
          manejar SQLException significa que la pantalla sabe que hay una
          base de datos debajo (ErrorHandlingBestPractices, "traducir por
          capa").
        - Cuando de verdad nadie puede recuperarse: un archivo de
          configuración obligatorio que no existe al arrancar.

        REGLA DE ORO al envolver: SIEMPRE pasa la original como causa.
        Si haces throw new RuntimeException(e.getMessage()), pierdes el
        tipo y el stack trace original, y depurar se vuelve adivinar. Con
        la causa, el stack trace muestra un "Caused by:" con todo
        (CustomExceptions).
    */
    static String requiredSetting(Path path) {
        try {
            return Files.readString(path).strip();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la configuración obligatoria: " + path, e);
        }
    }

    static boolean wrappingExample() {
        Path missing = Path.of("app-" + System.nanoTime() + ".properties");
        try {
            requiredSetting(missing);
            return false;
        } catch (IllegalStateException e) {
            return e.getMessage().startsWith("No se pudo leer la configuración obligatoria")   // contexto nuevo
                    && e.getCause() instanceof NoSuchFileException;                            // causa conservada
        }
    }

    /*
        =================================================================
        CAPA 8) CHECKED, HERENCIA E INTERFACES
        =================================================================

        throws es parte del contrato, así que se aplica la regla de
        sustitución (Inheritance, CAPA 10): quien usa el tipo padre debe
        estar preparado para todo lo que una hija pueda lanzar.

        Por eso, al SOBRESCRIBIR o implementar un método:
        - Puedes lanzar las MISMAS checked, MENOS, o subclases de ellas.
        - NO puedes agregar checked nuevas ni más generales:
              interface DataSource { String load() throws IOException; }
              class Bad implements DataSource {
                  public String load() throws Exception { ... }
              }
              // ERROR: load() in Bad cannot implement load() in DataSource
              //        overridden method does not throw Exception
          Quien llame a DataSource.load() solo está preparado para un
          IOException; un Exception cualquiera lo tomaría por sorpresa.
        - Las unchecked no tienen esta restricción.

        Consecuencia útil: una implementación que NO puede fallar (en
        memoria) declara cero excepciones. Quien la use por su tipo
        concreto no tiene que poner try; quien la use por la interface,
        sí, porque otra implementación (desde archivo) podría fallar.
    */
    interface DataSource {
        String load() throws IOException;
    }

    static final class FileSource implements DataSource {
        private final Path path;

        FileSource(Path path) {
            this.path = path;
        }

        @Override
        public String load() throws IOException {      // puede fallar
            return Files.readString(path);
        }
    }

    static final class InMemorySource implements DataSource {
        private final String data;

        InMemorySource(String data) {
            this.data = data;
        }

        @Override
        public String load() {                          // no puede fallar: declara MENOS (nada)
            return data;
        }
    }

    static String loadOrFallback(DataSource source) {
        try {
            return source.load();                       // por la interface: hay que manejar IOException
        } catch (IOException e) {
            return "respaldo";
        }
    }

    static boolean inheritanceExample() {
        InMemorySource memory = new InMemorySource("datos");
        String direct = memory.load();                  // tipo concreto: sin try, no declara nada

        DataSource missingFile = new FileSource(Path.of("fuente-" + System.nanoTime() + ".txt"));

        return direct.equals("datos")
                && loadOrFallback(memory).equals("datos")
                && loadOrFallback(missingFile).equals("respaldo");
    }

    /*
        Detalle relacionado: los bloques static NO pueden lanzar checked
        (no hay nadie que los llame para atraparla). Un error ahí se
        envuelve, y si escapa, la clase queda inutilizable
        (Static, CAPA 7). Los constructores, en cambio, SÍ pueden declarar
        throws.
    */

    /*
        =================================================================
        CAPA 9) EL DEBATE: ¿FUERON UNA BUENA IDEA?
        =================================================================

        Java es casi el único lenguaje popular con excepciones checked.
        C#, Kotlin, Scala, Python y JavaScript las evitaron a propósito.
        Las críticas más comunes:

        1) No funcionan con lambdas y streams (CAPA 6).
        2) Rompen la evolución de APIs: agregar un throws a un método
           público rompe a TODOS los que lo llaman.
        3) Atraviesan capas: un throws SQLException obliga a cada método
           intermedio a declararlo, aunque no le importe.
        4) Fomentan el peor hábito: catch vacío solo para que compile.

        Lo que defienden sus partidarios: obligan a pensar en los fallos
        reales del mundo exterior, y la firma documenta qué puede salir
        mal.

        LA PRÁCTICA ACTUAL en la industria:
        - Las del JDK se respetan y se manejan (IOException,
          InterruptedException).
        - En código de aplicación nuevo, la mayoría de los equipos y
          frameworks (Spring, Hibernate) usan casi siempre UNCHECKED, y
          traducen las checked de librerías en el borde (CAPA 7).
        - Las checked propias se reservan para fallos RECUPERABLES que el
          llamador DEBE atender, y que no viajan dentro de lambdas.
    */

    /*
        =================================================================
        CAPA 10) CÓMO ELEGIR PARA TU PROPIO CÓDIGO
        =================================================================

        Al lanzar una excepción tuya (CustomExceptions), pregúntate:

        1) ¿Es un error de PROGRAMACIÓN (argumento inválido, estado
           incorrecto, null donde no debía)?
               -> UNCHECKED. Usa las del JDK: IllegalArgumentException,
                  IllegalStateException, NullPointerException...

        2) ¿Es una condición de negocio o del entorno que quien llama
           PUEDE y DEBE resolver, y es improbable que pase por una lambda?
               -> CHECKED puede tener sentido.
                  (fondos insuficientes en una transferencia que el usuario
                  puede corregir; un servicio externo no disponible)

        3) ¿Quien llama no puede hacer nada útil?
               -> UNCHECKED. Obligarlo a atraparla solo genera catch vacíos.

        En la duda, unchecked: es más fácil de usar, funciona con lambdas
        y es lo que hace la mayoría del código moderno. Y si eliges checked,
        que sea una decisión consciente, no costumbre.
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Confundir throws (declara, en la firma) con throw (lanza, en el
           cuerpo) (CAPA 3).

        2) Atrapar una checked solo para que compile, con un catch vacío
           (CAPA 5).

        3) Atrapar en un nivel donde no se puede hacer nada útil, en lugar
           de propagar (CAPA 5).

        4) Envolver sin pasar la causa: new RuntimeException(e.getMessage())
           (CAPA 7).

        5) Pelear con las lambdas en lugar de envolver dentro de ellas
           (CAPA 6).

        6) Intentar lanzar checked nuevas al sobrescribir un método
           (CAPA 8).

        7) Atrapar Exception "por si acaso", ocultando también los bugs
           (las unchecked) que deberían salir a la luz.

        8) Crear excepciones checked propias para errores de programación
           (CAPA 10).

        9) Atrapar Error o RuntimeException de forma general para que "no
           se caiga" el programa (CAPA 4).

        10) Hacer que capas altas declaren excepciones de detalles de bajo
            nivel (SQLException en una pantalla) (CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Unchecked: RuntimeException y sus hijas (y Error). El
          compilador no exige nada. Representan bugs: se corrigen.
        - Checked: el resto de Exception. "Atrápala o decláralo con
          throws", o no compila. Representan fallos externos esperables.
        - throws declara en la firma; throw lanza en el cuerpo.
        - Atrapa solo si puedes hacer algo útil; si no, propaga.
        - Las lambdas no pueden lanzar checked: envuélvelas en una
          unchecked (UncheckedIOException), siempre con la causa.
        - Al sobrescribir, se pueden lanzar menos checked, nunca más.
        - Práctica actual: unchecked por defecto; checked solo para fallos
          recuperables que el llamador debe atender.
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
