package v1_fundamentals.part1_first_steps;

import java.util.List;

/*
    =====================================================================
    CÓMO FUNCIONA JAVA — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 1 de 14  (part1_first_steps)
    Anterior:  — (es la primera)
    Siguiente: part1_first_steps/PrimitiveTypes

    Esta es la PRIMERA guía de todo el proyecto. No necesitas saber nada
    de Java para leerla.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué pasa desde que escribes código hasta que corre
        CAPA 2)  Por qué existe el bytecode: "escribe una vez, corre en todos lados"
        CAPA 3)  JDK, JRE y JVM: qué es cada cosa
        CAPA 4)  Compilar y ejecutar: los comandos
        CAPA 5)  Anatomía de un archivo Java
        CAPA 6)  main: la puerta de entrada
        CAPA 7)  Paquetes e import
        CAPA 8)  Comentarios y Javadoc
        CAPA 9)  Los tres tipos de errores
        CAPA 10) Qué hace la JVM mientras tu programa corre
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class HowJavaWorks {

    public static void main(String[] args) {
        check(runningJvmExample(), "CAPA 2");
        check(packageMatchesFolderExample(), "CAPA 5");
        check(mainArgumentsExample(), "CAPA 6");
        check(importsExample(), "CAPA 7");
        check(javadocExample(), "CAPA 8");
        check(errorTypesExample(), "CAPA 9");
        check(jvmServicesExample(), "CAPA 10");
        System.out.println("HowJavaWorks: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ PASA DESDE QUE ESCRIBES CÓDIGO HASTA QUE CORRE
        =================================================================

        Una computadora solo entiende CÓDIGO MÁQUINA: instrucciones
        binarias muy simples ("suma estos dos registros", "salta a esta
        dirección"), y cada tipo de procesador (Intel, ARM de un celular
        o de una Mac) tiene las suyas. Nadie escribe programas así: se
        escriben en un lenguaje legible y algo los traduce.

        Hay dos estrategias clásicas de traducción:
        - COMPILAR: traducir TODO el programa antes de ejecutarlo
          (C, C++, Go). Es rápido al correr, pero el resultado solo sirve
          para un sistema operativo y procesador.
        - INTERPRETAR: traducir y ejecutar línea por línea mientras
          corre (los primeros JavaScript, Python). Corre donde haya un
          intérprete, pero es más lento.

        Java usa una estrategia de DOS PASOS que combina lo mejor de las
        dos:

            1. Escribes      Hola.java        código fuente (texto legible)
                                 │
                                 │  javac  (el compilador de Java)
                                 ▼
            2. Se compila a  Hola.class       BYTECODE: instrucciones para
                                 │            una máquina "imaginaria"
                                 │  java   (arranca la JVM)
                                 ▼
            3. La JVM lo     código máquina   del procesador real donde
               ejecuta                        está corriendo

        - El paso 1→2 pasa UNA vez, en tu computadora, y es cuando Java
          revisa los TIPOS y la sintaxis (CAPA 9). Por eso muchos errores
          que en JavaScript aparecen al ejecutar, en Java aparecen antes
          de que el programa siquiera arranque.
        - El paso 2→3 pasa CADA vez que se ejecuta, en la máquina donde
          corre.

        Si vienes de JavaScript: el motor de JavaScript (V8, en Chrome y
        Node) recibe directamente el texto fuente. Java nunca distribuye
        el texto fuente: distribuye bytecode ya revisado por el compilador.
    */

    /*
        =================================================================
        CAPA 2) POR QUÉ EXISTE EL BYTECODE: "ESCRIBE UNA VEZ, CORRE EN TODOS LADOS"
        =================================================================

        El bytecode NO es código máquina de ningún procesador real. Es el
        "idioma" de la JVM (Java Virtual Machine, máquina virtual de
        Java): un procesador imaginario, igual en todas partes.

        Lo que sí es distinto en cada sistema es la JVM. Existe una JVM
        para Windows, otra para Linux, otra para macOS, para ARM, para
        x86... Cada una sabe traducir el MISMO bytecode al código máquina
        de SU procesador.

        Resultado: compilas una sola vez, y el MISMO archivo .class corre
        sin cambios en cualquier sistema con una JVM. Ese fue el lema de
        Java desde 1995: "Write once, run anywhere".

        Por qué importó tanto: antes había que compilar y probar el
        programa por separado para cada sistema operativo. Con Java, un
        servidor en Linux y una laptop con Windows ejecutan exactamente
        el mismo archivo.

        Tu programa puede preguntarle a la JVM en qué está corriendo.
        Este mismo bytecode daría otros valores en otra computadora,
        sin recompilar nada:
    */
    static boolean runningJvmExample() {
        int javaVersion = Runtime.version().feature();          // 26 en esta máquina
        String operatingSystem = System.getProperty("os.name");  // "Windows 11", "Linux", "Mac OS X"...
        String jvmName = System.getProperty("java.vm.name");     // la implementación de la JVM

        return javaVersion >= 25 && !operatingSystem.isBlank() && !jvmName.isBlank();
    }

    /*
        =================================================================
        CAPA 3) JDK, JRE Y JVM: QUÉ ES CADA COSA
        =================================================================

        Son tres cosas metidas una dentro de otra:

            ┌─────────────────────────── JDK ───────────────────────────┐
            │  herramientas para DESARROLLAR: javac, javadoc, jar,      │
            │  jshell, javap, jdb (depurador)...                        │
            │   ┌──────────────────────── JRE ─────────────────────┐    │
            │   │  lo necesario para EJECUTAR: la JVM + la         │    │
            │   │  biblioteca estándar (String, List, LocalDate...)│    │
            │   │   ┌──────────── JVM ────────────┐                │    │
            │   │   │  ejecuta el bytecode        │                │    │
            │   │   └─────────────────────────────┘                │    │
            │   └──────────────────────────────────────────────────┘    │
            └───────────────────────────────────────────────────────────┘

        - JVM (Java Virtual Machine): el programa que EJECUTA bytecode.
        - JRE (Java Runtime Environment): JVM + biblioteca estándar. Es lo
          mínimo para CORRER programas Java.
        - JDK (Java Development Kit): JRE + herramientas para ESCRIBIR y
          compilar programas. Es lo que instalas como programador.

        Desde Java 11, Oracle ya no publica un JRE suelto: se instala el JDK
        (o se empaqueta solo lo necesario con la herramienta jlink). Otros
        proveedores, como Eclipse Temurin, sí publican JREs (las imágenes de
        Docker usan uno: v15_spring/.../packaging/PackagingAndDocker).

        VERSIONES
        Sale una versión nueva cada 6 meses (marzo y septiembre). Cada
        2 años, una es LTS (Long-Term Support): recibe correcciones por
        muchos años, y es la que usan las empresas.
            LTS: 8, 11, 17, 21, 25
        Este proyecto usa JDK 26 (no LTS, pero incluye todo lo de 25).
        Cuando una guía dice "desde Java 21", significa que en versiones
        anteriores eso no compila.

        Hay varios proveedores del JDK (Oracle, Eclipse Temurin, Amazon
        Corretto, Microsoft...). Todos se construyen desde el mismo código
        abierto, OpenJDK, y se comportan igual.
    */

    /*
        =================================================================
        CAPA 4) COMPILAR Y EJECUTAR: LOS COMANDOS
        =================================================================

        Todos se escriben en una terminal, desde la carpeta raíz del
        proyecto (la que contiene a v1_fundamentals/).

        FORMA CLÁSICA: compilar y luego ejecutar (dos pasos)
            javac -d out v1_fundamentals/part1_first_steps/HowJavaWorks.java
            java -cp out v1_fundamentals.part1_first_steps.HowJavaWorks

        - javac crea los .class. -d out dice "ponlos en la carpeta out".
        - java arranca la JVM. -cp out ("classpath") le dice dónde buscar
          los .class. Luego va el nombre COMPLETO de la clase (con su
          paquete y con puntos, no con / ni .java).

        FORMA RÁPIDA: ejecutar el código fuente directamente (Java 11+)
            java v1_fundamentals/part1_first_steps/HowJavaWorks.java

        La JVM compila en memoria y ejecuta, sin dejar .class. Es la que
        usamos en este proyecto. Desde Java 22, si el archivo usa clases
        de otros archivos del mismo árbol de carpetas, también las compila
        (así funciona AccessModifiers con sus subpaquetes).

        Por qué existen las dos: la forma rápida es ideal para aprender y
        para scripts. En proyectos reales, con cientos de archivos y
        librerías externas, se compila con una herramienta de construcción
        (Maven o Gradle, en tools/) que genera un archivo .jar.

        OTRAS HERRAMIENTAS DEL JDK QUE VALE LA PENA CONOCER
            jshell                 consola interactiva: escribes una línea
                                   de Java y ves el resultado al instante.
                                   Ideal para probar algo pequeño.
            java -jar app.jar      ejecuta una aplicación empaquetada.
            javap -c Clase.class   muestra el bytecode de una clase
                                   (v12_jvm/part2_classes/ClassLoadingAndBytecode).
            javadoc                genera la documentación HTML (CAPA 8).
    */

    /*
        =================================================================
        CAPA 5) ANATOMÍA DE UN ARCHIVO JAVA
        =================================================================

        Este mismo archivo es el ejemplo. Un archivo .java tiene, en este
        orden:

            package v1_fundamentals.part1_first_steps;   1) a qué paquete pertenece (opcional)
            import java.util.List;                       2) qué nombres usa de otros paquetes
            public class HowJavaWorks { ... }        3) una o más clases (o interfaces, enums, records)

        Dentro de la clase van sus MIEMBROS: campos, constructores y
        métodos. Y dentro de los métodos van las SENTENCIAS (las
        instrucciones).

        Reglas de sintaxis que aplican a TODO Java:

        1) En Java, todo el código vive dentro de una clase. No existen
           funciones sueltas como en JavaScript. (Los archivos compactos
           de Java 25, CAPA 6, parecen una excepción, pero el compilador
           crea una clase por ti.)

        2) Cada sentencia termina en punto y coma ; y es OBLIGATORIO. En
           JavaScript a veces se puede omitir; en Java es un error de
           compilación.

        3) Las llaves { } delimitan BLOQUES: el cuerpo de una clase, de un
           método, de un if. Lo que se declara dentro de un bloque solo
           existe dentro de él (v1_fundamentals/Variables, "alcance").

        4) Java DISTINGUE MAYÚSCULAS: total, Total y TOTAL son tres
           nombres distintos. String es un tipo; string no existe.

        5) Los espacios, tabuladores y saltos de línea no importan para el
           compilador; la sangría es para los humanos. (Se usan 4 espacios
           por nivel.)

        6) Un archivo puede tener varias clases, pero solo UNA public, y
           esa debe llamarse EXACTAMENTE igual que el archivo
           (AccessModifiers, CAPA 8).

        7) La línea package debe coincidir con las CARPETAS:
               archivo:  v1_fundamentals/part1_first_steps/HowJavaWorks.java
               paquete:  v1_fundamentals.part1_first_steps
           Por qué: así la JVM sabe en qué carpeta buscar cada clase a
           partir de su nombre completo, sin recorrer todo el disco.

        La JVM conoce cada clase por su NOMBRE COMPLETO (paquete + clase):
    */
    static boolean packageMatchesFolderExample() {
        String fullName = HowJavaWorks.class.getName();
        String packageName = HowJavaWorks.class.getPackageName();

        return fullName.equals("v1_fundamentals.part1_first_steps.HowJavaWorks")
                && packageName.equals("v1_fundamentals.part1_first_steps");
    }

    /*
        PALABRAS RESERVADAS
        Hay palabras que el lenguaje usa y que no puedes usar como nombre
        de nada: class, public, static, void, int, if, for, while, return,
        new, this, true, false, null, etc. (unas 50).
        Y hay palabras "contextuales" que solo son especiales en ciertos
        lugares, para no romper código viejo que ya las usaba como
        nombres: var, record, sealed, permits, yield, when, module...
        Por eso int var = 5; todavía compila (aunque no deberías hacerlo).
    */

    /*
        =================================================================
        CAPA 6) MAIN: LA PUERTA DE ENTRADA
        =================================================================

        Cuando ejecutas java Clase, la JVM busca un método llamado main
        en esa clase y empieza por ahí. La firma clásica, que verás en el
        99 % del código existente:

            public static void main(String[] args)

        Por qué cada palabra:
            public    -> la JVM está FUERA de tu clase; necesita poder verlo.
            static    -> al arrancar no existe ningún objeto todavía; la JVM
                         lo llama sin crear uno (Static, CAPA 9).
            void      -> no devuelve nada a la JVM. (Para indicar si el
                         programa terminó bien o mal se usa un "código de
                         salida": System.exit(0) = éxito, otro número = error.)
            main      -> el nombre que la JVM busca. No es palabra reservada;
                         es una convención que la JVM respeta.
            String[] args -> las palabras que escribas después del nombre al
                         ejecutar: java Saludo Ana Luis  ->  args = ["Ana", "Luis"]

        Variantes también válidas: String... args, o cualquier nombre de
        parámetro (String[] argumentos).

        DESDE JAVA 25: MAIN SIMPLIFICADO
        Para que aprender Java no empiece con 5 palabras que todavía no
        entiendes, Java 25 permite:
        - main sin public y sin static:        void main() { ... }
          (la JVM crea el objeto por ti con su constructor sin parámetros)
        - sin el parámetro args si no lo usas
        - "archivos compactos": un archivo SIN declarar clase, solo con
          métodos. El compilador crea la clase automáticamente:
              void main() {
                  IO.println("Hola");   // IO: atajo de Java 25 para la consola
              }
        Tu Ejercicios.java usa esta forma.

        Si una clase tiene varios main, la JVM elige primero el que
        recibe String[] (sea static o no) y, si no hay, el que no recibe
        nada.

        BUENA PRÁCTICA: main debe ser CORTO. Su trabajo es arrancar:
        leer los argumentos, crear los objetos principales y delegar.
        (Es lo que tu Base.java explicaba: "en lugar de poner toda la
        lógica dentro de main, se delega".) Toda la lógica en main no se
        puede reutilizar ni probar por partes.

        Ejemplo de main que delega: la lógica de armar el saludo vive en
        un método aparte, que se puede probar sin ejecutar el programa.
    */
    static String greeting(String[] args) {
        if (args.length == 0) {
            return "Hola, mundo";   // sin argumentos: un valor por defecto
        }
        return "Hola, " + String.join(" y ", args);
    }

    // Así se vería el main de un programa de saludo: corto, solo arranca y delega.
    //     public static void main(String[] args) {
    //         System.out.println(greeting(args));
    //     }
    // (En comentario solo porque esta clase ya tiene su propio main arriba.)

    static boolean mainArgumentsExample() {
        return greeting(new String[] {}).equals("Hola, mundo")
                && greeting(new String[] {"Ana", "Luis"}).equals("Hola, Ana y Luis");
    }

    /*
        =================================================================
        CAPA 7) PAQUETES E IMPORT
        =================================================================

        PAQUETES
        La biblioteca estándar de Java tiene miles de clases, y tu
        proyecto tendrá cientos. Los paquetes las organizan en grupos y,
        sobre todo, evitan choques de nombres: puede haber una clase Date
        en java.util y otra en java.sql, y no se confunden porque su
        NOMBRE COMPLETO es distinto.
        (Los paquetes también controlan el acceso: AccessModifiers.)

        IMPORT
        Escribir siempre el nombre completo sería tedioso:
            java.util.List<String> names = java.util.List.of("Ana");
        import le dice al compilador: "cuando escriba List, me refiero a
        java.util.List".
            import java.util.List;           // un nombre (la forma recomendada)
            import java.util.*;              // todos los del paquete ("comodín")
            import static java.lang.Math.max;// un miembro static (Static, CAPA 8)

        Lo que import NO hace (diferencia clave con JavaScript):
        en JavaScript, import CARGA y EJECUTA otro módulo. En Java, import
        solo es un ATAJO DE NOMBRES para el compilador: no carga nada, no
        ejecuta nada, no copia código y no afecta la velocidad. Por eso
        tampoco cuesta nada importar algo que al final no usas (aunque es
        desorden y conviene borrarlo).

        Lo que se importa solo:
        - todo java.lang (String, Math, Integer, System, Object...),
        - las clases del MISMO paquete.

        Por qué se prefiere importar clase por clase y no con *:
        - Se ve de un vistazo de dónde viene cada nombre.
        - Si dos paquetes importados con * tienen una clase con el mismo
          nombre y la USAS, el compilador no sabe cuál quieres y marca error
          ("reference to X is ambiguous").

        Cuando de verdad necesitas dos clases con el mismo nombre en un
        archivo, importas una y escribes la otra con su nombre completo:
    */
    static boolean importsExample() {
        List<String> imported = List.of("Ana");                      // List viene del import de arriba
        java.util.List<String> fullyQualified = java.util.List.of("Ana"); // mismo tipo, nombre completo

        // Dos clases llamadas Date, en paquetes distintos, en el mismo método:
        java.util.Date utilDate = new java.util.Date(0);
        java.sql.Date sqlDate = java.sql.Date.valueOf("2026-11-03");

        return imported.equals(fullyQualified)
                && !utilDate.getClass().getName().equals(sqlDate.getClass().getName());
        // (Ambas Date son clases antiguas; para fechas se usa java.time: v8_apis/part1_text_and_time/DatesAndTimes.)
    }

    /*
        =================================================================
        CAPA 8) COMENTARIOS Y JAVADOC
        =================================================================

        Tres tipos de comentarios. El compilador los ignora por completo;
        no llegan al bytecode.

            // comentario de una línea

            /* comentario de varias líneas
               (como los de estas guías) * /     <- sin el espacio; aquí está
                                                    separado para no cerrar
                                                    este comentario

            /** comentario de DOCUMENTACIÓN (Javadoc) * /

        JAVADOC
        Un comentario que empieza con /** justo antes de una clase o
        método es especial: la herramienta javadoc lo convierte en páginas
        HTML, y los editores (IntelliJ, VS Code) lo muestran al pasar el
        ratón sobre el método. Toda la documentación oficial de Java
        (docs.oracle.com) está generada así.

        Etiquetas más usadas:
            @param nombre   qué significa cada parámetro
            @return         qué devuelve
            @throws Tipo    qué excepción puede lanzar y cuándo

        Abajo un método documentado como se haría en un proyecto real.
    */

    /**
     * Calcula el promedio de un conjunto de calificaciones.
     *
     * @param grades calificaciones a promediar; no puede estar vacío
     * @return el promedio, con decimales
     * @throws IllegalArgumentException si {@code grades} está vacío
     */
    static double average(int[] grades) {
        if (grades.length == 0) {
            throw new IllegalArgumentException("No hay calificaciones para promediar");
        }
        int sum = 0;
        for (int grade : grades) {
            sum += grade;
        }
        return (double) sum / grades.length;
    }

    static boolean javadocExample() {
        return average(new int[] {7, 8}) == 7.5;
    }

    /*
        QUÉ COMENTAR (y qué no)
        Un buen comentario explica el POR QUÉ, porque el QUÉ ya lo dice el
        código.

            i++;   // incrementa i                          <- inútil: repite el código
            i++;   // saltamos el encabezado del archivo   <- útil: explica la intención

        Si sientes que un bloque necesita un comentario para entenderse,
        primero intenta hacerlo más claro: un mejor nombre de variable o
        sacarlo a un método con nombre descriptivo (v10_design/part4_clean_code/CleanCode).
        Estas guías comentan muchísimo porque su propósito es ENSEÑAR; el
        código de un proyecto real comenta mucho menos.
    */

    /*
        =================================================================
        CAPA 9) LOS TRES TIPOS DE ERRORES
        =================================================================

        Distinguirlos es lo primero que hay que aprender para depurar,
        porque cada uno se encuentra y se arregla de forma distinta.

        1) ERRORES DE COMPILACIÓN
           javac encuentra algo que no respeta las reglas del lenguaje.
           El programa NO se genera; no hay nada que ejecutar.
               int total = "diez";     // ERROR: incompatible types: String cannot be converted to int
               System.out.println("x") // ERROR: ';' expected
           Son los MEJORES errores: aparecen inmediatamente, con la línea
           exacta. Esta es la gran ventaja del tipado estático de Java
           frente a JavaScript: toda una familia de errores (usar texto
           donde va un número, llamar un método que no existe) se detecta
           ANTES de ejecutar.

        2) ERRORES DE EJECUCIÓN (EXCEPCIONES)
           El programa compiló bien, pero al correr pasa algo imposible:
           dividir un entero entre cero, acceder a la posición 10 de un
           arreglo de 3, usar un objeto que es null. La JVM lanza una
           EXCEPCIÓN y, si nadie la maneja, el programa se detiene y
           muestra un "stack trace": la lista de métodos que se estaban
           ejecutando, con la línea exacta (v3_exceptions/).

        3) ERRORES LÓGICOS
           Compila, corre sin excepciones... y da un resultado
           EQUIVOCADO. Nadie te avisa. Son los más peligrosos; por eso
           existen las pruebas automatizadas (testing/).

        Los tres, con código real:
    */
    static boolean errorTypesExample() {
        // 2) Error de ejecución: dividir un entero entre cero.
        int zero = 0;
        boolean runtimeErrorDetected;
        try {
            int result = 10 / zero;
            runtimeErrorDetected = result < 0;   // nunca llega aquí
        } catch (ArithmeticException e) {
            runtimeErrorDetected = e.getMessage().equals("/ by zero");
        }

        // 3) Error lógico: se quería el promedio de 7 y 8 (7.5), pero
        //    dividir dos int descarta los decimales (v1_fundamentals/Operators).
        int wrongAverage = (7 + 8) / 2;          // 7: compila, corre, y está mal
        double rightAverage = (7 + 8) / 2.0;     // 7.5

        return runtimeErrorDetected && wrongAverage == 7 && rightAverage == 7.5;
    }

    /*
        =================================================================
        CAPA 10) QUÉ HACE LA JVM MIENTRAS TU PROGRAMA CORRE
        =================================================================

        La JVM no solo "traduce". Mientras tu programa corre, trabaja
        constantemente. Una vista general (cada punto tiene su guía en
        v12_jvm/):

        1) CARGA DE CLASES: las clases no se cargan todas al inicio, sino
           la PRIMERA vez que se usan. Por eso un programa Java arranca
           rápido aunque la biblioteca estándar tenga miles de clases.

        2) VERIFICACIÓN: antes de ejecutar un .class, la JVM revisa que el
           bytecode sea válido y seguro (que no salte a memoria ajena, por
           ejemplo). Un .class manipulado se rechaza.

        3) INTÉRPRETE + COMPILADOR JIT: al principio la JVM INTERPRETA el
           bytecode. Mientras tanto mide qué métodos se ejecutan muchas
           veces ("código caliente") y esos los compila a código máquina
           optimizado sobre la marcha (Just-In-Time). Por eso un programa
           Java se vuelve más rápido después de unos segundos corriendo,
           y puede igualar a C++ en servidores de larga duración.

        4) RECOLECTOR DE BASURA (Garbage Collector): en Java creas objetos
           con new pero NUNCA los liberas a mano. La JVM detecta los
           objetos que ya nadie usa y libera su memoria. Esto elimina toda
           una familia de errores graves de C/C++ (liberar dos veces, usar
           memoria ya liberada). JavaScript hace lo mismo.

        5) MEMORIA: la JVM administra la memoria en dos zonas principales:
           la PILA (stack), donde viven las variables locales de cada
           método en ejecución, y el MONTÓN (heap), donde viven todos los
           objetos (v12_jvm/part1_memory/StackAndHeapMemory).

        Tu programa puede consultar algunos de estos datos:
    */
    static boolean jvmServicesExample() {
        Runtime runtime = Runtime.getRuntime();

        long maxHeapBytes = runtime.maxMemory();           // límite de memoria para objetos
        int processors = runtime.availableProcessors();    // núcleos que la JVM puede usar

        // Crear muchos objetos sin liberarlos a mano: el recolector de basura se encarga.
        long totalLength = 0;
        for (int i = 0; i < 100_000; i++) {
            String temporary = "objeto " + i;   // cada vuelta crea un String nuevo que nadie guarda
            totalLength += temporary.length();
        }

        return maxHeapBytes > 0 && processors > 0 && totalLength > 0;
    }

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Olvidar el punto y coma, o escribir string/system en minúscula.
           Java distingue mayúsculas (CAPA 5).

        2) Que el nombre del archivo no coincida con su clase public, o
           que la línea package no coincida con las carpetas (CAPA 5).

        3) Ejecutar desde la carpeta equivocada o con el nombre incompleto
           de la clase: java HowJavaWorks en lugar de
           java v1_fundamentals.part1_first_steps.HowJavaWorks
           (error "Could not find or load main class") (CAPA 4).

        4) Confundir JDK, JRE y JVM, o tener instalado un Java más viejo
           que el que exige el código (al ejecutar: UnsupportedClassVersionError
           "...compiled by a more recent version of the Java Runtime...";
           al compilar: errores con sintaxis o clases nuevas) (CAPA 3).

        5) Escribir mal la firma de main: Main con mayúscula, o static
           void main(String args) sin los corchetes (CAPA 6).

        6) Meter toda la lógica dentro de main (CAPA 6).

        7) Creer que import carga o ejecuta código, como en JavaScript
           (CAPA 7).

        8) Comentarios que repiten lo que hace el código en vez de explicar
           por qué (CAPA 8).

        9) Leer solo la primera línea de un error. El mensaje del
           compilador dice archivo, línea y causa; un stack trace dice la
           cadena completa de llamadas (CAPA 9).

        10) Ignorar los errores lógicos porque "compila y no truena"
            (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Código fuente (.java) -> javac -> bytecode (.class) -> JVM ->
          código máquina del procesador real.
        - El bytecode es el mismo en todos lados; cada sistema tiene su
          JVM. Por eso Java corre en cualquier parte sin recompilar.
        - JVM ejecuta; JRE = JVM + biblioteca; JDK = JRE + herramientas.
          LTS: 8, 11, 17, 21, 25.
        - javac + java (dos pasos) o java Archivo.java (directo).
        - Todo vive en clases; ; obligatorio; { } son bloques; distingue
          mayúsculas; package = carpetas; una clase public por archivo.
        - main: public static void main(String[] args). Desde Java 25,
          también void main() y archivos compactos. Mantenlo corto.
        - import es un atajo de nombres, no carga código. java.lang se
          importa solo.
        - Tres comentarios: // una línea, /* varias * / y /** Javadoc * /.
          Comenta el porqué.
        - Errores: de compilación (los mejores), de ejecución
          (excepciones) y lógicos (los peores).
        - La JVM carga clases cuando se usan, verifica, interpreta y
          compila con JIT, y libera memoria con el recolector de basura.
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
