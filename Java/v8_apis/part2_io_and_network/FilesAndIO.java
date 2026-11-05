package v8_apis.part2_io_and_network;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/*
    =====================================================================
    ARCHIVOS Y E/S: PATH, FILES Y LO QUE PASA EN EL SISTEMA OPERATIVO
    =====================================================================

    Ruta de apis/: guía 4 de 6  (part2_io_and_network)
    Anterior:  part1_text_and_time/FormattingAndLocale
    Siguiente: part2_io_and_network/HttpClientUsage

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Los ejemplos trabajan en una carpeta TEMPORAL del sistema que se
      crea al empezar y se borra al terminar. Nunca tocan el proyecto.

    Sobre el sistema operativo: los archivos no los maneja Java, los
    maneja el SISTEMA OPERATIVO. Esta guía explica qué pasa debajo usando
    LINUX como referencia (es lo que corre en casi todos los servidores y
    sus conceptos son los clásicos). Cuando Windows se comporta distinto
    de una forma que te afecta, se dice. Las cifras medidas son de la
    máquina donde se probó la guía: Windows 11, JDK 26.

    Índice:
        CAPA 1)  Qué pasa cuando un programa lee un archivo
        CAPA 2)  Descriptores de archivo: por qué SIEMPRE hay que cerrar
        CAPA 3)  Path: un nombre, no un archivo
        CAPA 4)  Rutas relativas y el directorio de trabajo del proceso
        CAPA 5)  Seguridad: rutas que se escapan (path traversal)
        CAPA 6)  Leer y escribir un archivo completo
        CAPA 7)  Bytes y caracteres: el charset
        CAPA 8)  Saltos de línea: \n y \r\n
        CAPA 9)  Streams de bytes y de caracteres
        CAPA 10) Buffers: el costo de cada llamada al sistema
        CAPA 11) Archivos grandes: línea por línea
        CAPA 12) Las excepciones de E/S y la carrera de exists()
        CAPA 13) Directorios: listar, recorrer, buscar y borrar
        CAPA 14) Borrar un archivo que alguien tiene abierto
        CAPA 15) La caché del kernel: escribir no es guardar en disco
        CAPA 16) Escritura atómica: nunca un archivo a medias
        CAPA 17) Metadatos y archivos temporales
        CAPA 18) Errores comunes
        RESUMEN FINAL
*/
public class FilesAndIO {

    public static void main(String[] args) throws IOException {
        Path workDir = Files.createTempDirectory("files-and-io-");   // en la carpeta temporal del sistema
        try {
            check(closeExample(workDir), "CAPA 2");
            check(pathExample(), "CAPA 3");
            check(workingDirectoryExample(), "CAPA 4");
            check(traversalExample(workDir), "CAPA 5");
            check(wholeFileExample(workDir), "CAPA 6");
            check(charsetExample(workDir), "CAPA 7");
            check(lineEndingsExample(workDir), "CAPA 8");
            check(streamsExample(workDir), "CAPA 9");
            check(bufferExample(workDir), "CAPA 10");
            check(largeFileExample(workDir), "CAPA 11");
            check(exceptionsExample(workDir), "CAPA 12");
            check(directoriesExample(workDir), "CAPA 13");
            check(deleteOpenFileExample(workDir), "CAPA 14");
            check(durabilityExample(workDir), "CAPA 15");
            check(atomicWriteExample(workDir), "CAPA 16");
            check(metadataExample(workDir), "CAPA 17");
        } finally {
            deleteRecursively(workDir);                             // limpiar pase lo que pase
        }
        System.out.println("FilesAndIO: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ PASA CUANDO UN PROGRAMA LEE UN ARCHIVO
        =================================================================

        Tu programa NUNCA toca el disco. Cuando corres java Programa, el
        sistema operativo crea un PROCESO: un programa en ejecución con su
        propia memoria, aislado de los demás. Ese proceso vive en el
        "espacio de usuario" y no tiene permiso de hablar con el hardware.

        Quien sí puede es el KERNEL (el núcleo del sistema operativo). Para
        cualquier cosa que salga del proceso (archivos, red, la pantalla),
        el programa le PIDE al kernel que lo haga, con una LLAMADA AL
        SISTEMA (system call, syscall). En Linux, para leer un archivo:

            Tu código Java        Files.readString(path)
                 |
            La JVM (en C/C++)     llama a las funciones del sistema:
                 |
            -----+----------------------------------------- frontera
                 |                                          usuario / kernel
            Kernel de Linux       open("/home/ana/notas.txt")  -> te da un número: 3
                                  read(3, buffer, 8192)        -> copia bytes a tu memoria
                                  read(3, buffer, 8192)        -> ... hasta que no hay más
                                  close(3)                     -> libera el número 3
                 |
            Sistema de archivos   traduce el nombre a bloques del disco
                 |
            Disco (SSD/HDD)

        Puedes VER estas llamadas en Linux con:
            strace -e trace=openat,read,write,close java Programa.java

        Por qué importa entenderlo:
        - Cada llamada al sistema cuesta: cruzar la frontera hacia el
          kernel y regresar es miles de veces más lento que una llamada a
          un método normal (CAPA 10: buffers).
        - Lo que el kernel te presta (el número 3), hay que devolverlo
          (CAPA 2: cerrar).
        - "Escribir" significa entregarle los bytes al kernel, que los
          guarda en el disco CUANDO LE CONVENGA (CAPA 15).
        - El disco y los archivos los comparten TODOS los procesos: otro
          programa puede borrar, cambiar o abrir el archivo mientras tú lo
          usas (CAPAS 12, 14 y 16).

        Y por eso las operaciones de E/S lanzan IOException, una excepción
        CHECKED (v3_exceptions/part1_concepts/CheckedVsUnchecked): dependen
        del mundo exterior (disco lleno, permisos, otro proceso), y Java te
        obliga a decidir qué hacer cuando fallan.
    */

    /*
        =================================================================
        CAPA 2) DESCRIPTORES DE ARCHIVO: POR QUÉ SIEMPRE HAY QUE CERRAR
        =================================================================

        Cuando el kernel abre un archivo para un proceso, guarda la
        información (qué archivo, en qué posición vas, con qué permisos)
        en una tabla PROPIA de ese proceso, y le da al proceso solo el
        ÍNDICE en esa tabla: un número entero llamado DESCRIPTOR DE ARCHIVO
        (file descriptor, fd). En Windows se llama "handle", pero la idea
        es la misma.

            Tabla de descriptores del proceso java (PID 1216):
                0  -> la entrada estándar (teclado)        = System.in
                1  -> la salida estándar (pantalla)        = System.out
                2  -> la salida de errores                 = System.err
                3  -> /home/ana/notas.txt  (posición 8192)
                4  -> un socket hacia api.ejemplo.com      (la red también usa fds)

        (En Linux puedes ver la tabla de un proceso en /proc/<pid>/fd, y
        Java te da su PID con ProcessHandle.current().pid().)

        LA TABLA TIENE UN LÍMITE. En Linux, el límite por proceso se ve con
        ulimit -n y muchas veces es 1024. Un servidor que abre archivos o
        conexiones y no los cierra va acumulando descriptores; cuando llega
        al límite, CUALQUIER intento de abrir algo falla:
            java.io.IOException: Too many open files
        y el servidor deja de funcionar aunque tenga memoria y CPU de sobra.

        ¿Y el recolector de basura? NO sirve para esto: libera MEMORIA
        cuando le conviene (tal vez en minutos), y no sabe que tu objeto
        tiene un descriptor escaso detrás. Por eso todo lo que abre un
        recurso implementa AutoCloseable, y se usa con try-with-resources
        (v3_exceptions/part2_in_practice/TryWithResources): close() se
        llama al salir del bloque, siempre, incluso con excepciones. close()
        termina en la llamada close(fd) del kernel.

        Qué hay que cerrar y qué no:
        - Files.readString, readAllLines, writeString, write: abren y
          cierran solos por dentro. No hay nada que cerrar.
        - Files.lines, Files.list, Files.walk, Files.newBufferedReader,
          newInputStream...: te DEVUELVEN algo con el archivo abierto.
          Tú lo cierras, con try-with-resources.
    */
    static boolean closeExample(Path dir) throws IOException {
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, "uno\ndos\n");      // abre, escribe y cierra por dentro

        boolean[] closed = {false};
        long count;
        try (Stream<String> lines = Files.lines(file).onClose(() -> closed[0] = true)) {
            count = lines.count();
        }                                           // aquí se cierra: el descriptor vuelve al kernel

        return count == 2 && closed[0] && ProcessHandle.current().pid() > 0;
    }

    /*
        =================================================================
        CAPA 3) PATH: UN NOMBRE, NO UN ARCHIVO
        =================================================================

        java.nio.file.Path (Java 7) representa una RUTA: un nombre que
        puede señalar a un archivo o carpeta. Crear un Path NO toca el
        disco: el archivo puede no existir. Es solo texto con estructura
        (las partes separadas por / o \).

            Path.of("docs", "cv.pdf")        docs/cv.pdf  (el separador lo pone Java)
            dir.resolve("cv.pdf")            "entra" a dir: dir/cv.pdf
            path.getFileName()               cv.pdf
            path.getParent()                 docs
            path.normalize()                 quita . y ..: a/b/../c -> a/c
            base.relativize(otro)            cómo llegar de base a otro
            path.toAbsolutePath()            la ruta completa (CAPA 4)

        SEPARADORES: Linux usa / (/home/ana/cv.pdf), Windows usa \
        (C:\Users\Ana\cv.pdf). Con Path.of(partes...) y resolve no escribes
        separadores a mano y el código funciona en los dos. (En Linux todo
        cuelga de una sola raíz, /; en Windows hay una raíz por unidad:
        C:\, D:\.)

        La clase antigua es java.io.File. Funciona, pero sus métodos
        devuelven false en vez de explicar el error (file.delete() ->
        false, ¿por qué?). Para código nuevo, Path + Files. Si una API vieja
        te pide un File: path.toFile(), y al revés, file.toPath().
    */
    static boolean pathExample() {
        Path cv = Path.of("docs", "cv.pdf");
        Path messy = Path.of("a", "b", "..", "c", ".", "d.txt");
        Path home = Path.of("home", "ana");
        Path deep = Path.of("home", "ana", "docs", "cv.pdf");

        return cv.getFileName().equals(Path.of("cv.pdf"))
                && cv.getParent().equals(Path.of("docs"))
                && messy.normalize().equals(Path.of("a", "c", "d.txt"))
                && home.relativize(deep).equals(Path.of("docs", "cv.pdf"))
                && home.resolve("docs").resolve("cv.pdf").equals(deep)
                && !Files.exists(Path.of("este", "archivo", "no", "existe.txt"));   // crear el Path no crea nada
    }

    /*
        =================================================================
        CAPA 4) RUTAS RELATIVAS Y EL DIRECTORIO DE TRABAJO DEL PROCESO
        =================================================================

        Una ruta ABSOLUTA empieza en la raíz (/home/ana/notas.txt). Una
        RELATIVA (notas.txt, datos/clientes.csv) no dice desde dónde. ¿Desde
        dónde la busca el sistema?

        Todo proceso tiene un DIRECTORIO DE TRABAJO actual (current working
        directory, cwd): la carpeta "donde está parado". Lo hereda de quien
        lo lanzó: si en la terminal estás en /home/ana/proyecto y escribes
        java App.java, el cwd del proceso java es /home/ana/proyecto. Las
        rutas relativas se resuelven contra ESA carpeta, no contra donde
        está el .java o el .jar.

        En Java lo ves con System.getProperty("user.dir"), y
        Path.of("x.txt").toAbsolutePath() lo antepone. Java NO permite
        cambiar el cwd del proceso mientras corre.

        Consecuencia práctica: un programa que lee "config.properties"
        funciona en tu IDE (que lo lanza desde la carpeta del proyecto) y
        falla en el servidor (lanzado desde otra carpeta por un servicio).
        Por eso las rutas importantes se reciben por configuración y se
        convierten en absolutas al arrancar; y los recursos que viajan con
        el programa se leen del classpath, no del disco (tools/).
    */
    static boolean workingDirectoryExample() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path relative = Path.of("config.properties");
        return !relative.isAbsolute()
                && relative.toAbsolutePath().equals(cwd.resolve("config.properties"))   // relativa = contra el cwd
                && cwd.isAbsolute();
    }

    /*
        =================================================================
        CAPA 5) SEGURIDAD: RUTAS QUE SE ESCAPAN (PATH TRAVERSAL)
        =================================================================

        Un servidor guarda los archivos que suben los usuarios en
        /srv/uploads y los entrega por nombre: GET /archivo?nombre=foto.png.
        El código "obvio":
            Path file = uploads.resolve(nombreQueMandaElUsuario);
        Si el usuario manda "../../etc/passwd", resolve lo acepta: la ruta
        resultante SALE de uploads y apunta a cualquier archivo que el
        PROCESO tenga permiso de leer. Es uno de los ataques más comunes en
        aplicaciones web ("path traversal").

        La defensa: normalizar (para resolver los ..) y comprobar que el
        resultado SIGUE dentro de la carpeta permitida. Y como segunda
        línea, que el proceso corra con un usuario del sistema con los
        MÍNIMOS permisos: si lo engañan, que no pueda leer nada importante.
    */
    static Path safeResolve(Path baseDir, String userFileName) {
        Path base = baseDir.toAbsolutePath().normalize();
        Path target = base.resolve(userFileName).normalize();
        if (!target.startsWith(base)) {             // startsWith de Path compara por partes, no por texto
            throw new SecurityException("Ruta fuera de la carpeta permitida: " + userFileName);
        }
        return target;
    }

    static boolean traversalExample(Path dir) {
        Path uploads = dir.resolve("uploads");
        boolean normalAllowed = safeResolve(uploads, "foto.png").getFileName().equals(Path.of("foto.png"));

        boolean attackBlocked;
        try {
            safeResolve(uploads, "../../etc/passwd");
            attackBlocked = false;
        } catch (SecurityException e) {
            attackBlocked = true;
        }
        Path naive = uploads.resolve("../../etc/passwd").normalize();     // lo que haría el código "obvio"

        return normalAllowed && attackBlocked && !naive.startsWith(uploads);
    }

    /*
        =================================================================
        CAPA 6) LEER Y ESCRIBIR UN ARCHIVO COMPLETO
        =================================================================

        Para archivos pequeños (configuraciones, plantillas, un JSON), Files
        tiene métodos de una línea que abren, hacen todo y cierran:

            Files.readString(path)              todo el texto (UTF-8)
            Files.readAllLines(path)            una List<String> con las líneas
            Files.readAllBytes(path)            byte[] (imágenes, binarios)
            Files.writeString(path, texto)      escribe texto (UTF-8)
            Files.write(path, lineas)           escribe una línea por elemento
            Files.write(path, bytes)

        Las OPCIONES dicen qué hacer si el archivo ya existe
        (StandardOpenOption):
            (por defecto)         CREATE + TRUNCATE_EXISTING: lo crea o lo SOBRESCRIBE
            APPEND                agrega al final (logs)
            CREATE_NEW            solo si NO existe; si existe, falla (CAPA 12)

        CUIDADO: "todo de una vez" carga el archivo COMPLETO en la memoria
        del proceso (el heap de la JVM; v12_jvm/). Un archivo de 5 GB no cabe:
        OutOfMemoryError. Para archivos grandes, línea por línea (CAPA 11).
    */
    static boolean wholeFileExample(Path dir) throws IOException {
        Path log = dir.resolve("app.log");
        Files.writeString(log, "inicio\n");
        Files.writeString(log, "pedido 1\n", StandardOpenOption.APPEND);    // agrega
        Files.writeString(log, "pedido 2\n", StandardOpenOption.APPEND);
        List<String> lines = Files.readAllLines(log);

        Path config = dir.resolve("config.txt");
        Files.writeString(config, "version=1");
        Files.writeString(config, "version=2");                            // por defecto: sobrescribe

        return lines.equals(List.of("inicio", "pedido 1", "pedido 2"))
                && Files.readString(config).equals("version=2");
    }

    /*
        =================================================================
        CAPA 7) BYTES Y CARACTERES: EL CHARSET
        =================================================================

        Para el sistema operativo, un archivo es SOLO una secuencia de
        BYTES (números del 0 al 255). No existe "un archivo de texto": un
        .txt y un .png son lo mismo para el kernel. El texto aparece cuando
        un programa INTERPRETA esos bytes con una CODIFICACIÓN (charset):
        una tabla que dice qué bytes forman qué carácter.

            "año" en UTF-8        61  C3 B1  6F     (4 bytes: la ñ ocupa 2)
            "año" en ISO-8859-1   61  F1     6F     (3 bytes: la ñ ocupa 1)

        Si escribes con una codificación y lees con otra, sale basura. Dos
        reacciones posibles:
        - Files.readString/readAllLines son ESTRICTOS: si los bytes no son
          UTF-8 válido, lanzan MalformedInputException ("Input length = 1").
        - Files.newBufferedReader también es estricto. Pero un
          InputStreamReader armado a mano (CAPA 9) es PERMISIVO: cambia
          cada byte inválido por el carácter de reemplazo U+FFFD (el rombo
          con ?) y sigue sin avisar. Datos corrompidos en silencio.

        UTF-8 es el estándar de hoy: representa todos los idiomas y emojis.
        Desde Java 18, el charset por defecto de Java es UTF-8 en todos los
        sistemas. Antes dependía del sistema operativo (en Windows era
        Cp1252), y un programa que funcionaba en Linux corrompía acentos
        en Windows.

        Pero el sistema operativo SIGUE teniendo el suyo: en la máquina
        donde se probó esta guía, System.getProperty("native.encoding") es
        Cp1252, y la consola de Windows usa OTRA: la 850 (medido con chcp:
        "Página de códigos activa: 850"). Por eso a veces ves
        "a?o" o "a�o" en la terminal aunque el archivo esté bien: Java
        escribió bytes de UTF-8 y la consola los interpretó con otra tabla.

        Regla: UTF-8 en todos lados, y explícito cuando lees algo que no
        generaste tú (un archivo de un sistema viejo puede venir en
        ISO-8859-1 o Windows-1252: Files.readString(path, charset)).
    */
    static boolean charsetExample(Path dir) throws IOException {
        Path legacy = dir.resolve("legacy.txt");
        Files.writeString(legacy, "año", StandardCharsets.ISO_8859_1);       // un archivo de un sistema viejo

        boolean strictFails;
        try {
            Files.readString(legacy);                                       // lo lee como UTF-8
            strictFails = false;
        } catch (MalformedInputException e) {
            strictFails = e.getMessage().equals("Input length = 1");
        }

        String nioReader;
        try (BufferedReader reader = Files.newBufferedReader(legacy, StandardCharsets.UTF_8)) {
            nioReader = reader.readLine();
        } catch (MalformedInputException e) {
            nioReader = "estricto";                 // Files.newBufferedReader también es estricto
        }
        String permissive;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(legacy), StandardCharsets.UTF_8))) {
            permissive = reader.readLine();         // InputStreamReader: reemplaza en silencio
        }

        return strictFails
                && nioReader.equals("estricto")
                && permissive.charAt(1) == '\uFFFD'                                   // el rombo con ?
                && Files.readString(legacy, StandardCharsets.ISO_8859_1).equals("año")   // con el charset correcto
                && Files.size(legacy) == 3
                && "año".getBytes(StandardCharsets.UTF_8).length == 4;
    }

    /*
        =================================================================
        CAPA 8) SALTOS DE LÍNEA: \n Y \r\n
        =================================================================

        "Fin de línea" también son bytes, y no todos los sistemas usan los
        mismos:
            Linux y macOS    \n       (LF, un byte: 0A)
            Windows          \r\n     (CR LF, dos bytes: 0D 0A)
        System.lineSeparator() da el del sistema actual.

        Qué hace Java:
        - LEER: readAllLines, lines() y BufferedReader.readLine aceptan los
          dos (y \r solo) y te dan las líneas SIN el separador.
        - ESCRIBIR: Files.write(path, lineas) y BufferedWriter.newLine()
          usan el del sistema. writeString escribe exactamente lo que le
          das.

        Por qué importa: un archivo generado en Windows y procesado por un
        script en Linux trae un \r al final de cada línea ("valor\r"), que
        rompe comparaciones y es invisible al imprimir. Para archivos que
        se intercambian entre sistemas, elige UNO (normalmente \n) y
        escríbelo explícito.
    */
    static boolean lineEndingsExample(Path dir) throws IOException {
        Path windowsFile = dir.resolve("windows.txt");
        Files.writeString(windowsFile, "uno\r\ndos\r\n");
        Path linuxFile = dir.resolve("linux.txt");
        Files.writeString(linuxFile, "uno\ndos\n");

        Path platform = dir.resolve("platform.txt");
        Files.write(platform, List.of("a", "b"));   // usa el separador del sistema actual
        long expectedSize = 2 + 2L * System.lineSeparator().length();

        return Files.readAllLines(windowsFile).equals(List.of("uno", "dos"))     // lee los dos igual
                && Files.readAllLines(linuxFile).equals(List.of("uno", "dos"))
                && Files.size(windowsFile) == 10 && Files.size(linuxFile) == 8
                && Files.size(platform) == expectedSize;
    }

    /*
        =================================================================
        CAPA 9) STREAMS DE BYTES Y DE CARACTERES
        =================================================================

        La API clásica de E/S (java.io, Java 1) tiene dos familias. (No
        confundir con los Stream de v7_functional: solo comparten nombre.)

        | Familia                | Lee / escribe | Clases base                 |
        |------------------------|---------------|-----------------------------|
        | Streams de BYTES       | bytes         | InputStream / OutputStream  |
        | Readers / Writers      | caracteres    | Reader / Writer             |

        El PUENTE entre las dos es la codificación (CAPA 7):
            InputStreamReader(inputStream, charset)    bytes -> caracteres
            OutputStreamWriter(outputStream, charset)  caracteres -> bytes

        Se arman en CAPAS, envolviendo uno en otro, y cada capa agrega una
        capacidad (el patrón Decorator; v11_patterns/):

            new BufferedReader(                         + leer por líneas, con buffer (CAPA 10)
                new InputStreamReader(                  + bytes -> caracteres (UTF-8)
                    Files.newInputStream(path),         el archivo, como bytes
                    StandardCharsets.UTF_8))

        Files tiene atajos que arman esas capas por ti:
            Files.newBufferedReader(path)  /  Files.newBufferedWriter(path)
            Files.newInputStream(path)     /  Files.newOutputStream(path)

        Lo mismo sirve para NO archivos: la red (un socket da un
        InputStream; part2_io_and_network/HttpClientUsage), la memoria
        (ByteArrayInputStream) o la salida de otro proceso
        (Process.getInputStream()). Esa es la ventaja de la abstracción: tu
        código que lee un InputStream no sabe ni le importa de dónde vienen
        los bytes.
    */
    static long countBytes(InputStream input) throws IOException {
        long total = 0;
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) {  // -1 = ya no hay más (fin del archivo o de la conexión)
            total += read;
        }
        return total;
    }

    static boolean streamsExample(Path dir) throws IOException {
        Path file = dir.resolve("data.bin");
        Files.write(file, new byte[] {1, 2, 3, 4, 5});

        long fromFile;
        try (InputStream input = Files.newInputStream(file)) {
            fromFile = countBytes(input);
        }
        long fromMemory = countBytes(new ByteArrayInputStream(new byte[] {9, 9, 9}));   // mismo código, otra fuente

        Path text = dir.resolve("text.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(text)) {    // capas armadas por Files (UTF-8)
            writer.write("línea con acento");
            writer.newLine();
        }
        String back;
        try (BufferedReader reader = Files.newBufferedReader(text)) {
            back = reader.readLine();
        }

        return fromFile == 5 && fromMemory == 3 && back.equals("línea con acento");
    }

    /*
        =================================================================
        CAPA 10) BUFFERS: EL COSTO DE CADA LLAMADA AL SISTEMA
        =================================================================

        Un OutputStream "crudo" de archivo hace UNA llamada al sistema
        (write en Linux, WriteFile en Windows) por CADA write(). Si
        escribes byte por byte, son millones de viajes al kernel, y cada
        viaje cuesta: el procesador cambia de modo usuario a modo kernel,
        el kernel valida, copia, actualiza sus estructuras y regresa.

        Un BUFFER es un arreglo en la memoria del proceso donde se JUNTAN
        los datos, y solo cuando se llena (8 KB por defecto en
        BufferedOutputStream y BufferedWriter) se hace UNA llamada al
        sistema con todo. Mismo resultado, miles de veces menos viajes.

        Medido en la máquina de prueba, escribiendo 2,000,000 de bytes uno
        por uno:
            FileOutputStream sin buffer       12,834 ms  (2 millones de llamadas al sistema)
            con BufferedOutputStream              78 ms  (unas 250 llamadas de 8 KB)

        (Leer tiene el mismo problema y la misma solución: BufferedReader,
        BufferedInputStream, o leer en bloques como countBytes de la
        CAPA 9.)

        EL PRECIO del buffer: mientras los datos están en él, NO están en
        el archivo. Se mandan al kernel al llenarse, al llamar flush(), o
        al cerrar (close() hace flush). Si el programa termina sin cerrar
        (o se cae), lo que quedaba en el buffer SE PIERDE. Otra razón para
        try-with-resources.
    */
    static boolean bufferExample(Path dir) throws IOException {
        Path file = dir.resolve("buffered.txt");
        long sizeBeforeFlush;
        long sizeAfterFlush;
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("hola");
            sizeBeforeFlush = Files.size(file);     // sigue en el buffer de NUESTRO proceso: 0 bytes
            writer.flush();                         // ahora sí: una llamada al sistema con todo
            sizeAfterFlush = Files.size(file);
        }

        Path binary = dir.resolve("buffered.bin");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(binary))) {
            for (int i = 0; i < 100_000; i++) {
                out.write(i % 256);                 // 100,000 write(), pero ~13 llamadas al sistema
            }
        }

        return sizeBeforeFlush == 0 && sizeAfterFlush == 4 && Files.size(binary) == 100_000;
    }

    /*
        =================================================================
        CAPA 11) ARCHIVOS GRANDES: LÍNEA POR LÍNEA
        =================================================================

        Para archivos grandes (logs, exportaciones CSV de millones de
        filas), no cargues todo (CAPA 6). Procesa una línea a la vez: la
        memoria usada es la de UNA línea más el buffer, sin importar si el
        archivo pesa 1 MB o 50 GB.

        Dos formas:
            try (Stream<String> lines = Files.lines(path)) {
                lines.filter(...).map(...)...          // perezoso (StreamsBasics, CAPA 4)
            }

            try (BufferedReader reader = Files.newBufferedReader(path)) {
                String line;
                while ((line = reader.readLine()) != null) { ... }   // null = fin del archivo
            }

        Files.lines es el que MÁS se olvida cerrar: el descriptor queda
        abierto hasta que el stream se cierre (CAPA 2). Siempre en un
        try-with-resources.
    */
    static boolean largeFileExample(Path dir) throws IOException {
        Path log = dir.resolve("big.log");
        try (BufferedWriter writer = Files.newBufferedWriter(log)) {
            for (int i = 1; i <= 100_000; i++) {
                writer.write((i % 10 == 0 ? "ERROR " : "INFO ") + "evento " + i);
                writer.newLine();
            }
        }

        long errors;
        try (Stream<String> lines = Files.lines(log)) {
            errors = lines.filter(line -> line.startsWith("ERROR")).count();
        }

        long lastLineNumber = 0;
        try (BufferedReader reader = Files.newBufferedReader(log)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lastLineNumber = Long.parseLong(line.substring(line.lastIndexOf(' ') + 1));
            }
        }

        return errors == 10_000 && lastLineNumber == 100_000;
    }

    /*
        =================================================================
        CAPA 12) LAS EXCEPCIONES DE E/S Y LA CARRERA DE EXISTS()
        =================================================================

        java.nio.file lanza excepciones ESPECÍFICAS (todas son IOException),
        que dicen exactamente qué pasó:

            NoSuchFileException          el archivo o carpeta no existe
            FileAlreadyExistsException   ya existe (con CREATE_NEW, createDirectory)
            DirectoryNotEmptyException   borrar una carpeta con contenido
            AccessDeniedException        el PROCESO no tiene permiso (CAPA 1:
                                         los permisos son del usuario del
                                         sistema que corre el programa)
            FileSystemException          la base, con más detalle del sistema

        LA CARRERA DE EXISTS() (time-of-check to time-of-use, TOCTOU):

            if (Files.exists(path)) {        // 1) revisas
                Files.readString(path);      // 2) usas
            }

        Entre 1 y 2, OTRO PROCESO (u otro hilo) puede borrar el archivo. El
        if no te protege: solo agrega una llamada al sistema y una falsa
        sensación de seguridad. Lo correcto es INTENTAR y manejar la
        excepción, que es la respuesta real del kernel en ese instante.

        Lo mismo al crear: "si no existe, créalo" con exists + write es una
        carrera. CREATE_NEW lo hace en UNA sola operación del kernel
        (en Linux, open con O_CREAT | O_EXCL): crea, o falla si ya existe,
        sin hueco entre medio. Es la base de los "archivos de bloqueo"
        (lock files) que usan muchos programas para que solo una instancia
        corra a la vez.
    */
    static boolean tryAcquireLock(Path lockFile) throws IOException {
        try {
            Files.writeString(lockFile, String.valueOf(ProcessHandle.current().pid()), StandardOpenOption.CREATE_NEW);
            return true;                            // lo creamos nosotros: somos los únicos
        } catch (FileAlreadyExistsException e) {
            return false;                           // otra instancia ya lo tiene
        }
    }

    static boolean exceptionsExample(Path dir) throws IOException {
        boolean missing;
        try {
            Files.readString(dir.resolve("no-existe.txt"));     // intentar, no preguntar antes
            missing = false;
        } catch (NoSuchFileException e) {
            missing = e.getFile().endsWith("no-existe.txt");
        }

        Path lock = dir.resolve("app.lock");
        boolean first = tryAcquireLock(lock);
        boolean second = tryAcquireLock(lock);      // segunda instancia: no puede

        Path folder = Files.createDirectory(dir.resolve("folder"));
        Files.writeString(folder.resolve("x.txt"), "x");
        boolean notEmpty;
        try {
            Files.delete(folder);
            notEmpty = false;
        } catch (DirectoryNotEmptyException e) {
            notEmpty = true;
        }

        return missing && first && !second && notEmpty
                && AccessDeniedException.class.getSuperclass().getSimpleName().equals("FileSystemException");
    }

    /*
        =================================================================
        CAPA 13) DIRECTORIOS: LISTAR, RECORRER, BUSCAR Y BORRAR
        =================================================================

        Para el sistema de archivos (en Linux), una carpeta es un archivo
        especial que contiene una tabla de "nombre -> inodo". El INODO es
        la estructura con los datos reales del archivo (tamaño, permisos,
        dónde están sus bloques en el disco). El nombre NO es parte del
        archivo: es una entrada en la tabla de su carpeta. (Por eso un
        mismo archivo puede tener varios nombres: los "hard links", y por
        eso renombrar dentro del mismo disco es instantáneo, CAPA 16.)

            Files.createDirectories(path)    crea la carpeta y las que falten arriba (mkdir -p)
            Files.list(dir)                  Stream del contenido directo (un nivel)
            Files.walk(dir)                  Stream de TODO, recursivo (incluye a dir)
            Files.walk(dir, profundidad)     con límite de niveles
            Files.find(dir, prof, (ruta, atributos) -> condición)   buscar con filtro
            Files.newDirectoryStream(dir, "*.java")                 filtro con glob

        list, walk y find tienen la carpeta ABIERTA: se cierran con
        try-with-resources (CAPA 2).

        BORRAR UNA CARPETA: el sistema solo borra carpetas VACÍAS
        (DirectoryNotEmptyException). Hay que borrar primero el contenido,
        de lo más profundo hacia arriba: walk + orden inverso (los hijos
        tienen rutas más largas que sus padres, así que al revés de
        alfabético salen antes). Es lo que hace deleteRecursively al final
        de esta guía.
    */
    static boolean directoriesExample(Path dir) throws IOException {
        Path project = dir.resolve("project");
        Files.createDirectories(project.resolve("src").resolve("app"));    // crea las tres de una vez
        Files.writeString(project.resolve("src").resolve("app").resolve("Main.java"), "class Main {}");
        Files.writeString(project.resolve("src").resolve("app").resolve("Util.java"), "class Util {}");
        Files.writeString(project.resolve("README.md"), "# Proyecto");

        long directChildren;
        try (Stream<Path> children = Files.list(project)) {
            directChildren = children.count();       // README.md y src
        }

        List<Path> javaFiles;
        try (Stream<Path> found = Files.find(project, 10,
                (path, attributes) -> attributes.isRegularFile() && path.toString().endsWith(".java"))) {
            javaFiles = found.map(project::relativize).sorted().toList();
        }

        deleteRecursively(project);

        return directChildren == 2
                && javaFiles.equals(List.of(Path.of("src", "app", "Main.java"), Path.of("src", "app", "Util.java")))
                && !Files.exists(project);
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> all = Files.walk(root)) {
            all.sorted(Comparator.reverseOrder())   // hijos antes que padres
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);   // Lambdas, CAPA 10
                        }
                    });
        }
    }

    /*
        =================================================================
        CAPA 14) BORRAR UN ARCHIVO QUE ALGUIEN TIENE ABIERTO
        =================================================================

        ¿Qué pasa si un proceso borra un archivo que otro tiene abierto?

        En LINUX, "borrar" (la llamada unlink) solo quita el NOMBRE de la
        tabla de la carpeta (CAPA 13). El inodo y sus datos siguen vivos
        mientras algún proceso tenga un descriptor abierto hacia él; el
        espacio en disco se libera cuando se cierra el ÚLTIMO. El proceso
        que lo tenía abierto puede seguir leyendo sin enterarse.

        Esto explica un misterio clásico de servidores: el disco aparece
        LLENO (df), pero sumando los archivos (du) no cuadra. Alguien borró
        un log enorme que un proceso todavía tenía abierto: el nombre ya no
        está, pero el espacio sigue ocupado hasta reiniciar ese proceso. En
        Linux, lsof +L1 lista esos archivos "borrados pero abiertos".

        En WINDOWS la regla depende de cómo se abrió el archivo: un
        programa puede abrirlo prohibiendo que otros lo borren (por eso a
        veces Windows dice "el archivo está en uso por otro proceso"). Java
        abre los archivos PERMITIENDO el borrado, así que, como muestra el
        ejemplo, Files.delete funciona aunque un stream de Java lo tenga
        abierto. Con archivos abiertos por OTROS programas, puedes recibir
        un FileSystemException.
    */
    static boolean deleteOpenFileExample(Path dir) throws IOException {
        Path file = dir.resolve("in-use.txt");
        Files.writeString(file, "a\nb\n");

        boolean deletedWhileOpen;
        try (Stream<String> lines = Files.lines(file)) {   // descriptor abierto
            Files.delete(file);                            // otro "proceso" lo borra
            deletedWhileOpen = !Files.exists(file);        // el NOMBRE ya no está
            lines.count();
        }
        return deletedWhileOpen;
    }

    /*
        =================================================================
        CAPA 15) LA CACHÉ DEL KERNEL: ESCRIBIR NO ES GUARDAR EN DISCO
        =================================================================

        Cuando write() regresa (o Files.writeString termina), ¿los datos
        ya están en el disco? NO necesariamente.

        El kernel guarda los datos en una caché en MEMORIA RAM (en Linux,
        la "page cache") y los escribe al disco más tarde, cuando le
        conviene (en Linux, típicamente en segundos; las páginas "sucias"
        se escriben en lotes). Lo hace porque el disco es miles de veces
        más lento que la memoria. Y LEER también usa esa caché: por eso
        abrir un archivo por segunda vez es mucho más rápido.

            tu proceso  --write-->  page cache (RAM del kernel)  --más tarde-->  disco
                                    ^ aquí regresa write()

        Si el PROCESO se cae, no pasa nada: los datos ya son del kernel y
        llegarán al disco. Si se va la LUZ o se cae el SISTEMA OPERATIVO
        antes de que el kernel los escriba, SE PIERDEN.

        Para casi todo, eso está bien. Cuando NO puedes perder datos
        (bases de datos, pagos, archivos de transacciones), pides
        explícitamente que se escriban YA:
            fileChannel.force(true)     // en Linux: la llamada fsync
        force no regresa hasta que el dispositivo confirma. Es LENTO (puede
        tardar milisegundos): por eso las bases de datos agrupan varias
        transacciones por cada fsync.

        (Hay dos niveles más de caché que Java no controla: la del propio
        disco y la de su controlador; los discos de servidor están hechos
        para respetar fsync.)
    */
    static void writeDurably(Path file, String content) throws IOException {
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(StandardCharsets.UTF_8.encode(content));
            channel.force(true);                    // no regresa hasta que el disco confirma (fsync)
        }
    }

    static boolean durabilityExample(Path dir) throws IOException {
        Path payment = dir.resolve("payment.txt");
        writeDurably(payment, "pago 123: aprobado");
        return Files.readString(payment).equals("pago 123: aprobado");
    }

    /*
        =================================================================
        CAPA 16) ESCRITURA ATÓMICA: NUNCA UN ARCHIVO A MEDIAS
        =================================================================

        Escribir un archivo no es instantáneo. Si el proceso se cae (o
        alguien lo mata, o se va la luz) A LA MITAD de reescribir
        config.json, queda un archivo CORTADO: ni el viejo ni el nuevo. Y
        otro proceso que lo lea mientras se escribe ve la mitad.

        La técnica estándar (la usan editores, gestores de paquetes, bases
        de datos):
        1) Escribe el contenido completo en un archivo TEMPORAL en la MISMA
           carpeta.
        2) (Si necesitas durabilidad) force para que esté en el disco
           (CAPA 15).
        3) RENOMBRA el temporal al nombre final con
           Files.move(temp, final, ATOMIC_MOVE, REPLACE_EXISTING).

        Por qué funciona: renombrar dentro del mismo sistema de archivos
        solo cambia la entrada "nombre -> inodo" de la carpeta (CAPA 13),
        y en Linux la llamada rename es ATÓMICA: cualquier proceso que abra
        el archivo ve el viejo COMPLETO o el nuevo COMPLETO, nunca una
        mezcla. Si el programa se cae en el paso 1, el original está
        intacto (y queda un temporal que se puede limpiar).

        Por qué en la MISMA carpeta: entre discos distintos, "mover" es
        copiar y borrar, que no puede ser atómico; ATOMIC_MOVE lanza
        AtomicMoveNotSupportedException en ese caso, en lugar de fingir.
    */
    static void writeAtomically(Path target, String content) throws IOException {
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), target.getFileName() + "-", ".tmp");
        try {
            writeDurably(temp, content);            // 1 y 2: completo y en disco
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);   // 3
        } finally {
            Files.deleteIfExists(temp);             // si algo falló antes del move, no dejar basura
        }
    }

    static boolean atomicWriteExample(Path dir) throws IOException {
        Path config = dir.resolve("config.json");
        writeAtomically(config, "{\"version\": 1}");
        writeAtomically(config, "{\"version\": 2}");

        long leftovers;
        try (Stream<Path> files = Files.list(dir)) {
            leftovers = files.filter(path -> path.getFileName().toString().endsWith(".tmp")).count();
        }
        return Files.readString(config).equals("{\"version\": 2}") && leftovers == 0;
    }

    /*
        =================================================================
        CAPA 17) METADATOS Y ARCHIVOS TEMPORALES
        =================================================================

        Los datos SOBRE un archivo (en Linux, guardados en su inodo):
            Files.size(path)                     tamaño en bytes
            Files.getLastModifiedTime(path)      última modificación
            Files.isDirectory / isRegularFile    qué es
            Files.isReadable / isWritable        ¿el PROCESO puede? (CAPA 12: es
                                                 una pregunta con carrera; úsala
                                                 para mensajes, no para decidir)
            Files.readAttributes(path, BasicFileAttributes.class)   varios en una llamada

        ARCHIVOS TEMPORALES: el sistema tiene una carpeta para ellos (en
        Linux /tmp; en Windows, la carpeta Temp del usuario). Java la
        expone en System.getProperty("java.io.tmpdir").
            Files.createTempFile("reporte-", ".csv")      nombre único y seguro
            Files.createTempDirectory("trabajo-")         (así empieza esta guía)
        Los nombres son aleatorios para que dos procesos no choquen ni un
        atacante pueda adivinarlos.

        BORRARLOS es tu responsabilidad. file.toFile().deleteOnExit() los
        borra al terminar la JVM, pero solo si termina NORMALMENTE: si el
        proceso muere por kill -9 (en Linux) o se cae la máquina, se quedan.
        Es mejor borrarlos en un finally, como el main de esta guía.
    */
    static boolean metadataExample(Path dir) throws IOException {
        Path report = Files.createTempFile(dir, "report-", ".csv");
        try {
            Files.writeString(report, "id,total\n1,350\n");
            return Files.size(report) == 15
                    && Files.isRegularFile(report) && !Files.isDirectory(report)
                    && report.getFileName().toString().startsWith("report-")
                    && Files.getLastModifiedTime(report).toMillis() > 0
                    && Path.of(System.getProperty("java.io.tmpdir")).isAbsolute();
        } finally {
            Files.delete(report);                   // tu responsabilidad, no la del sistema
        }
    }

    /*
        =================================================================
        CAPA 18) ERRORES COMUNES
        =================================================================

        1) No cerrar Files.lines, list, walk, readers o streams: se acaban
           los descriptores del proceso ("Too many open files") (CAPA 2).

        2) Rutas relativas que dependen de desde dónde se lanzó el proceso
           (CAPA 4).

        3) Usar el nombre que manda un usuario en resolve sin comprobar que
           no se sale de la carpeta (CAPA 5).

        4) readAllLines/readString con archivos enormes (CAPAS 6 y 11).

        5) No fijar el charset al leer archivos de otros sistemas, o
           confiar en un Reader permisivo que corrompe en silencio
           (CAPA 7).

        6) Comparar líneas de archivos de Windows en Linux con el \r al
           final (CAPA 8).

        7) Escribir byte por byte sin buffer, o con buffer y sin cerrar ni
           hacer flush (CAPA 10).

        8) if (Files.exists(...)) antes de usar: una carrera con otros
           procesos; intenta y maneja la excepción (CAPA 12).

        9) Creer que al terminar write() los datos están en el disco
           (CAPA 15).

        10) Reescribir un archivo importante directamente en lugar de
            temporal + rename atómico (CAPA 16).

        11) Usar java.io.File y sus métodos que devuelven false sin
            explicar el error (CAPA 3).

        12) Dejar archivos temporales confiando en deleteOnExit (CAPA 17).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Tu proceso no toca el disco: pide al kernel con llamadas al
          sistema (open, read, write, close). Cada una cuesta.
        - Abrir te presta un descriptor de archivo; la tabla tiene límite:
          cierra SIEMPRE con try-with-resources (el GC no sirve para esto).
        - Path es un nombre, no un archivo; Path.of + resolve sin escribir
          separadores. Las relativas van contra el directorio de trabajo
          del proceso.
        - Valida que las rutas de usuarios no se escapen (normalize +
          startsWith).
        - Un archivo son bytes; el texto requiere charset: UTF-8 explícito.
          readString es estricto; los Reader puente pueden ser permisivos.
        - \n en Linux, \r\n en Windows; readAllLines acepta los dos.
        - Bytes (InputStream/OutputStream) y caracteres (Reader/Writer), en
          capas.
        - Buffers: muchas operaciones en pocas llamadas al sistema (12.8 s
          -> 78 ms); flush o close para que lleguen.
        - Archivos grandes: línea por línea.
        - Intenta y maneja NoSuchFileException etc.; CREATE_NEW es
          crear-si-no-existe atómico.
        - Borrar quita el nombre; el archivo vive mientras esté abierto
          (Linux).
        - write llega a la caché del kernel; force (fsync) para el disco.
        - Escritura atómica: temporal en la misma carpeta + ATOMIC_MOVE.
        - Temporales: createTempFile y bórralos tú.
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
