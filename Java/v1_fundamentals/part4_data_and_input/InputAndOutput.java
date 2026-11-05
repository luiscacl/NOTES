package v1_fundamentals.part4_data_and_input;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;

/*
    =====================================================================
    ENTRADA Y SALIDA POR CONSOLA — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 14 de 14  (part4_data_and_input)
    Anterior:  part4_data_and_input/Numbers
    Siguiente: — (es la última de v1_fundamentals/; sigue v2_oop/part1_basics/ClassesAndObjects)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Una particularidad de esta guía: los ejemplos NO leen del teclado ni
    escriben en la consola de verdad, sino de un texto y hacia memoria.
    No es un truco para poder comprobarlos: es la técnica profesional que
    explica la CAPA 10, y la razón más importante de toda la guía.

    Índice:
        CAPA 1)  Entrada, salida y error estándar
        CAPA 2)  Escribir: print, println y printf
        CAPA 3)  Leer con Scanner: tokens y líneas
        CAPA 4)  La trampa de nextLine después de nextInt
        CAPA 5)  Validar lo que escribe el usuario
        CAPA 6)  Scanner y el idioma: 3.5 o 3,5
        CAPA 7)  Leer mucho y rápido: BufferedReader
        CAPA 8)  Argumentos de main y códigos de salida
        CAPA 9)  Console, IO (Java 25) y los acentos en la terminal
        CAPA 10) Separar la entrada/salida de la lógica
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class InputAndOutput {

    public static void main(String[] args) {
        check(standardStreamsExample(), "CAPA 1");
        check(printExample(), "CAPA 2");
        check(scannerExample(), "CAPA 3");
        check(nextLineTrapExample(), "CAPA 4");
        check(validationExample(), "CAPA 5");
        check(localeExample(), "CAPA 6");
        check(bufferedReaderExample(), "CAPA 7");
        check(argumentsExample(), "CAPA 8");
        check(separationExample(), "CAPA 10");
        System.out.println("InputAndOutput: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) ENTRADA, SALIDA Y ERROR ESTÁNDAR
        =================================================================

        Todo programa de consola, en cualquier lenguaje, recibe del
        sistema operativo tres "canales" (streams) ya abiertos:

        | Canal              | En Java     | Normalmente es       |
        |--------------------|-------------|----------------------|
        | entrada estándar   | System.in   | el teclado           |
        | salida estándar    | System.out  | la pantalla          |
        | error estándar     | System.err  | también la pantalla  |

        Un STREAM es un flujo de bytes: no "un teclado" ni "una pantalla",
        sino cualquier cosa de la que se lee o a la que se escribe. Por
        eso el sistema operativo puede CONECTARLOS a otra cosa sin que el
        programa lo sepa:
            java Programa < datos.txt           System.in lee del archivo
            java Programa > resultado.txt       System.out escribe al archivo
            java Programa 2> errores.txt        System.err escribe a otro archivo
            java Uno | java Dos                 la salida de Uno es la entrada de Dos

        POR QUÉ EXISTE UN CANAL DE ERROR SEPARADO: si rediriges la salida
        a un archivo (> resultado.txt), quieres que el archivo contenga
        SOLO los resultados, y que los mensajes de error se sigan viendo
        en pantalla. Por eso los errores van a System.err, nunca a
        System.out.

        Los tres son variables static de la clase System, de tipo
        PrintStream (out, err) e InputStream (in).
    */
    static boolean standardStreamsExample() {
        return System.out instanceof PrintStream && System.err instanceof PrintStream && System.in != null;
    }

    /*
        =================================================================
        CAPA 2) ESCRIBIR: PRINT, PRINTLN Y PRINTF
        =================================================================

            print(x)              escribe x, sin salto de línea
            println(x)            escribe x y un salto de línea
            println()             solo un salto de línea
            printf(plantilla, ...)  escribe con formato (Strings, CAPA 10)

        Todos aceptan cualquier tipo: println(5), println(true),
        println(objeto) (que usa su toString; ClassesAndObjects, CAPA 9).
        Es la sobrecarga en acción (Methods, CAPA 5).

        SALTO DE LÍNEA: Windows usa dos caracteres (\r\n) y Linux/macOS
        uno (\n). println usa el del sistema donde corre. En printf, %n
        también; \n escribe siempre solo \n. Por eso en printf se prefiere
        %n.

        Cualquier PrintStream funciona igual, no solo System.out. Aquí se
        escribe en MEMORIA (un ByteArrayOutputStream) para poder revisar
        exactamente qué se imprimió. Es la base de la CAPA 10.
    */
    static String captureOutput(String name, int age) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        out.print("Hola, ");
        out.println(name);
        out.printf(Locale.ROOT, "Edad: %d, promedio: %.1f%n", age, 9.25);

        return buffer.toString(StandardCharsets.UTF_8);
    }

    static boolean printExample() {
        String printed = captureOutput("Ana", 30);
        String newline = System.lineSeparator();   // \r\n en Windows, \n en Linux y macOS
        return printed.equals("Hola, Ana" + newline + "Edad: 30, promedio: 9.3" + newline);
    }

    /*
        =================================================================
        CAPA 3) LEER CON SCANNER: TOKENS Y LÍNEAS
        =================================================================

        System.in solo entrega BYTES. Scanner los convierte en datos
        útiles. Se crea sobre una fuente:
            Scanner keyboard = new Scanner(System.in);   // el teclado
            Scanner fromText = new Scanner("25 Ana");    // un texto (para pruebas)

        Scanner trabaja con TOKENS: pedazos de texto separados por
        espacios, tabuladores o saltos de línea.

            nextInt(), nextDouble(), nextBoolean()  lee el siguiente token como ese tipo
            next()                                  lee el siguiente token como texto
            nextLine()                              lee TODO hasta el fin de la línea
            hasNextInt(), hasNext(), hasNextLine()  ¿el siguiente es de ese tipo? (no lo consume)

        Diferencia clave entre next() y nextLine():
        - next() da UNA palabra ("Ana" de "Ana López").
        - nextLine() da la línea COMPLETA ("Ana López").

        Mientras espera que el usuario escriba, nextInt() y compañía
        DETIENEN el programa (se dice que "bloquean").
    */
    static boolean scannerExample() {
        Scanner input = new Scanner("25 Ana López\nGuadalajara\n");

        int age = input.nextInt();           // token "25"
        String firstWord = input.next();     // token "Ana"
        String rest = input.nextLine();      // lo que queda de la línea: " López"
        String city = input.nextLine();      // la línea siguiente completa

        return age == 25 && firstWord.equals("Ana") && rest.equals(" López") && city.equals("Guadalajara")
                && !input.hasNextLine();     // ya no queda nada
    }

    /*
        =================================================================
        CAPA 4) LA TRAMPA DE NEXTLINE DESPUÉS DE NEXTINT
        =================================================================

        El error más común con Scanner. El usuario escribe:
            25 [Enter]
            Ana López [Enter]
        Y el programa hace:
            int age = input.nextInt();      // lee 25
            String name = input.nextLine(); // ¡devuelve ""!

        Por qué: nextInt() lee el token "25" y SE DETIENE ahí; el salto de
        línea del Enter sigue sin leerse. nextLine() lee "hasta el fin de
        la línea", y la línea actual ya terminó justo ahí: devuelve el
        texto vacío entre el 25 y el salto. El nombre queda sin leer.

        Soluciones:
        1) Después de nextInt(), un nextLine() extra para "consumir" el
           salto de línea pendiente.
        2) Mejor: leer SIEMPRE líneas completas con nextLine() y
           convertir tú (Integer.parseInt(line.trim())). Así nunca mezclas
           tokens con líneas.
    */
    static boolean nextLineTrapExample() {
        Scanner trap = new Scanner("25\nAna López\n");
        int age = trap.nextInt();
        String name = trap.nextLine();             // vacío: solo consumió el salto pendiente

        Scanner fixed = new Scanner("25\nAna López\n");
        int fixedAge = Integer.parseInt(fixed.nextLine().trim());   // siempre líneas completas
        String fixedName = fixed.nextLine();

        return age == 25 && name.isEmpty() && fixedAge == 25 && fixedName.equals("Ana López");
    }

    /*
        =================================================================
        CAPA 5) VALIDAR LO QUE ESCRIBE EL USUARIO
        =================================================================

        Regla de oro: TODO lo que escribe un usuario puede venir mal.
        Letras donde iban números, números fuera de rango, nada.

        Lo que pasa si no validas:
        - nextInt() con "abc"  -> InputMismatchException
        - nextInt() sin datos  -> NoSuchElementException (se acabó la entrada)

        LA TRAMPA DEL CICLO INFINITO: cuando nextInt() falla, NO consume el
        token inválido. Si en un ciclo atrapas la excepción y vuelves a
        llamar nextInt(), encuentra otra vez "abc", falla otra vez... para
        siempre. Hay que DESCARTAR el token malo con next() (o la línea
        con nextLine()).

        La forma limpia: preguntar ANTES con hasNextInt() y descartar lo
        que no sirva. O leer líneas y validar con parseInt en un try
        (ConversionsAndCasting, CAPA 10).

        Y no basta con que sea un número: valida también el RANGO.
    */
    static int readAge(Scanner input, PrintStream out) {
        while (true) {
            out.print("Edad: ");
            if (!input.hasNextInt()) {
                String invalid = input.next();               // DESCARTA el token malo, o se repetiría
                out.println("'" + invalid + "' no es un número.");
                continue;
            }
            int age = input.nextInt();
            if (age < 0 || age > 120) {
                out.println("La edad debe estar entre 0 y 120.");
                continue;
            }
            return age;
        }
    }

    static boolean validationExample() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        int age = readAge(new Scanner("abc -5 30"), out);   // dos intentos inválidos, luego uno bueno
        String conversation = buffer.toString(StandardCharsets.UTF_8);

        boolean mismatch;
        try {
            new Scanner("abc").nextInt();
            mismatch = false;
        } catch (InputMismatchException e) {
            mismatch = true;
        }

        boolean endOfInput;
        try {
            new Scanner("").nextInt();
            endOfInput = false;
        } catch (NoSuchElementException e) {
            endOfInput = true;
        }

        return age == 30
                && conversation.contains("'abc' no es un número.")
                && conversation.contains("entre 0 y 120")
                && mismatch && endOfInput;
    }

    /*
        =================================================================
        CAPA 6) SCANNER Y EL IDIOMA: 3.5 O 3,5
        =================================================================

        nextDouble() interpreta el número según el IDIOMA (Locale) de la
        computadora. En una configurada en español de España, el
        separador decimal es la COMA: espera "3,5", y "3.5" lanza
        InputMismatchException. En México o Estados Unidos es al revés.

        Esto no es teórico: tu computadora tiene Locale es_ES. Un
        programa que hace new Scanner(System.in).nextDouble() en tu
        máquina rechazará "3.5".

        Por qué Scanner hace esto: está pensado para leer lo que escribe
        una PERSONA, y una persona escribe los números como se usan en su
        país.

        Qué hacer:
        - Para datos de MÁQUINAS (archivos, otros programas), fija el
          formato: scanner.useLocale(Locale.ROOT) -> siempre punto.
        - Para PERSONAS, decide conscientemente qué idioma esperas, o lee
          texto y conviértelo tú.
        (Double.parseDouble no depende del idioma: siempre espera punto.)
    */
    static boolean localeExample() {
        Scanner spain = new Scanner("3.5 3,5").useLocale(Locale.forLanguageTag("es-ES"));
        boolean pointRejected;
        try {
            spain.nextDouble();
            pointRejected = false;
        } catch (InputMismatchException e) {
            pointRejected = true;
            spain.next();                    // descartar el token inválido (CAPA 5)
        }
        double commaAccepted = spain.nextDouble();

        double machineFormat = new Scanner("3.5").useLocale(Locale.ROOT).nextDouble();

        return pointRejected && commaAccepted == 3.5 && machineFormat == 3.5
                && Double.parseDouble("3.5") == 3.5;
    }

    /*
        =================================================================
        CAPA 7) LEER MUCHO Y RÁPIDO: BUFFEREDREADER
        =================================================================

        Scanner es cómodo pero lento: usa expresiones regulares para
        reconocer cada token. Con unas pocas líneas no importa; con cientos
        de miles (archivos grandes, problemas de programación competitiva)
        sí.

        BufferedReader lee LÍNEAS, rápido, y nada más. Tú conviertes:
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();     // null cuando ya no hay más
            int value = Integer.parseInt(line.trim());

        Diferencias:
        - readLine() devuelve null al final de la entrada (en lugar de
          lanzar una excepción como Scanner).
        - Lanza IOException, una excepción "checked" que hay que manejar
          (v3_exceptions/CheckedVsUnchecked).
        - Se cierra con try-with-resources (v3_exceptions/TryWithResources),
          igual que cualquier recurso.

        Detalle: NO cierres un Scanner o BufferedReader construido sobre
        System.in si después vas a seguir leyendo del teclado: cerrarlo
        cierra también System.in, y ya no se puede volver a abrir.
    */
    static int sumOfLines(BufferedReader reader) throws IOException {
        int sum = 0;
        String line;
        while ((line = reader.readLine()) != null) {   // lee hasta que no haya más líneas
            if (!line.isBlank()) {
                sum += Integer.parseInt(line.trim());
            }
        }
        return sum;
    }

    static boolean bufferedReaderExample() {
        try (BufferedReader reader = new BufferedReader(new StringReader("10\n20\n\n30\n"))) {
            return sumOfLines(reader) == 60;
        } catch (IOException e) {
            return false;
        }
    }

    /*
        =================================================================
        CAPA 8) ARGUMENTOS DE MAIN Y CÓDIGOS DE SALIDA
        =================================================================

        La otra forma de darle datos a un programa es al lanzarlo:
            java Saludo --nombre Ana --veces 3
        llega a main como args = ["--nombre", "Ana", "--veces", "3"]
        (HowJavaWorks, CAPA 6). Las palabras se separan por espacios;
        para un argumento con espacios, la terminal usa comillas:
        "Ana López" llega como UN solo argumento.

        Son siempre Strings: hay que convertirlos y validarlos igual que
        lo que escribe un usuario.

        CÓDIGO DE SALIDA: al terminar, un programa le devuelve al sistema
        operativo un número. 0 significa "todo bien"; cualquier otro,
        "algo falló". Los scripts y las herramientas de automatización lo
        usan para saber si continuar. Se fija con System.exit(código).
        Úsalo SOLO en main: System.exit apaga la JVM entera, así que en
        cualquier otro lugar mataría el programa sin dejar que nada se
        limpie (y en un servidor, mataría el servidor).
    */
    record Options(String name, int times) { }

    static Options parseOptions(String[] args) {
        String name = "mundo";
        int times = 1;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--nombre" -> name = args[++i];
                case "--veces" -> times = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("Opción desconocida: " + args[i]);
            }
        }
        return new Options(name, times);
    }

    static boolean argumentsExample() {
        Options defaults = parseOptions(new String[] {});
        Options custom = parseOptions(new String[] {"--nombre", "Ana López", "--veces", "3"});

        boolean unknownRejected;
        try {
            parseOptions(new String[] {"--color"});
            unknownRejected = false;
        } catch (IllegalArgumentException e) {
            unknownRejected = true;
        }

        return defaults.equals(new Options("mundo", 1))
                && custom.equals(new Options("Ana López", 3))
                && unknownRejected;
    }

    /*
        =================================================================
        CAPA 9) CONSOLE, IO (JAVA 25) Y LOS ACENTOS EN LA TERMINAL
        =================================================================

        CONSOLE: System.console() da acceso a la terminal real. Su uso
        estrella es readPassword(): lee una contraseña SIN mostrarla en
        pantalla mientras se escribe. Pero devuelve null cuando no hay una
        terminal interactiva (si rediriges la entrada o la salida, o en
        algunos IDE), así que siempre hay que revisar null.

        IO (Java 25): una clase en java.lang (no hay que importarla)
        pensada para principiantes y programas pequeños:
            IO.println("Hola");
            String name = IO.readln("¿Cómo te llamas? ");   // pregunta y lee una línea
        Es la que usan los archivos compactos (HowJavaWorks, CAPA 6).

        ACENTOS QUE SALEN MAL: si ves "Ã±" en lugar de "ñ", el programa y
        la terminal están usando CODIFICACIONES distintas (formas de
        convertir caracteres a bytes). En Windows la consola suele usar
        una codificación antigua (Cp1252 o Cp850) en lugar de UTF-8.
        Java la detecta en la propiedad stdout.encoding. Si hay problemas:
        cambiar la terminal a UTF-8 (chcp 65001 en Windows) o usar una
        terminal moderna (Windows Terminal).
    */

    /*
        =================================================================
        CAPA 10) SEPARAR LA ENTRADA/SALIDA DE LA LÓGICA
        =================================================================

        La lección más importante de la guía.

        El error de principiante es escribir la lógica mezclada con
        System.in y System.out:

            static void tipCalculator() {
                Scanner in = new Scanner(System.in);
                System.out.print("Total: ");
                double total = in.nextDouble();
                ... cálculo ...
                System.out.println("Propina: " + tip);
            }

        Problemas:
        - Para probarlo, alguien tiene que sentarse a escribir números
          cada vez.
        - No se puede reutilizar desde una página web o una app móvil:
          está pegado a la consola.

        La forma profesional, en DOS niveles:
        1) La LÓGICA es una función pura (Methods, CAPA 10): recibe
           valores y devuelve valores. Nada de Scanner ni System.out.
        2) La INTERACCIÓN recibe la entrada y la salida como PARÁMETROS
           (un Scanner y un PrintStream). main le pasa System.in y
           System.out; una prueba le pasa un texto y un buffer en memoria.

        Es la misma idea de "recibir las dependencias desde fuera" de
        Polymorphism (CAPA 8) y Static (CAPA 10, con el reloj).
        Y es exactamente lo que hacen todos los ejemplos de esta guía.
    */

    // Nivel 1: lógica pura. Se prueba sola, sin consola.
    static double tipFor(double total, int percent) {
        return Math.round(total * percent) / 100.0;   // redondeado a centavos
    }

    // Nivel 2: interacción, con entrada y salida inyectadas.
    static void runTipCalculator(Scanner in, PrintStream out) {
        out.print("Total de la cuenta: ");
        double total = in.useLocale(Locale.ROOT).nextDouble();
        out.print("Porcentaje de propina: ");
        int percent = in.nextInt();
        out.printf(Locale.ROOT, "Propina: %.2f%n", tipFor(total, percent));
    }

    // En un programa real, main solo conecta las piezas con la consola de verdad:
    //     public static void main(String[] args) {
    //         runTipCalculator(new Scanner(System.in), System.out);
    //     }
    // (En comentario solo porque esta clase ya tiene su main.)

    static boolean separationExample() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        runTipCalculator(new Scanner("850.50\n15\n"), out);   // un "usuario" simulado

        List<String> lines = new ArrayList<>(buffer.toString(StandardCharsets.UTF_8).lines().toList());
        return tipFor(850.50, 15) == 127.58
                && lines.get(lines.size() - 1).endsWith("Propina: 127.58");
    }

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Mandar mensajes de error a System.out en lugar de System.err
           (CAPA 1).

        2) Usar \n en printf en lugar de %n (CAPA 2).

        3) nextLine() justo después de nextInt() (CAPA 4).

        4) No validar lo que escribe el usuario, o validar solo el tipo y
           no el rango (CAPA 5).

        5) Atrapar InputMismatchException en un ciclo sin descartar el
           token inválido: ciclo infinito (CAPA 5).

        6) Leer decimales con Scanner sin pensar en el idioma (CAPA 6).

        7) Usar Scanner para entradas enormes (CAPA 7).

        8) Cerrar un Scanner sobre System.in y querer volver a leer del
           teclado (CAPA 7).

        9) Llamar System.exit fuera de main (CAPA 8).

        10) Mezclar la lógica con System.in y System.out: imposible de
            probar y de reutilizar (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Tres canales: System.in, System.out, System.err. El sistema
          operativo puede redirigirlos. Errores a System.err.
        - print, println, printf (%n para saltos de línea).
        - Scanner: tokens (nextInt, next) o líneas (nextLine); hasNextX
          para preguntar sin consumir.
        - nextLine tras nextInt devuelve "": mejor leer siempre líneas.
        - Valida tipo y rango; descarta los tokens inválidos.
        - nextDouble depende del Locale (tu máquina: es_ES, coma decimal).
        - BufferedReader para leer mucho y rápido (null al final).
        - args son Strings; System.exit(código) solo en main.
        - Console.readPassword, IO de Java 25, codificación de la terminal.
        - Separa la lógica (funciones puras) de la interacción (Scanner y
          PrintStream recibidos como parámetros).
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
