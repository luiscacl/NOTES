package v2_oop.part1_basics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
    =====================================================================
    CLASES Y OBJETOS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 1 de 14  (part1_basics)
    Anterior:  v1_fundamentals/part4_data_and_input/InputAndOutput (última de v1_fundamentals/)
    Siguiente: part1_basics/Static

    Esta es la PRIMERA guía de la carpeta v2_oop/. Todo lo demás
    (encapsulación, herencia, polimorfismo, abstracción) se construye
    sobre lo que se explica aquí.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es la programación orientada a objetos
        CAPA 2)  Clase vs objeto
        CAPA 3)  Campos (atributos) y sus valores por defecto
        CAPA 4)  Métodos de instancia: comportamiento + estado
        CAPA 5)  Constructores
        CAPA 6)  Sobrecarga de constructores y this(...)
        CAPA 7)  Los cuatro usos de this
        CAPA 8)  Orden de inicialización de un objeto
        CAPA 9)  toString: cómo se ve un objeto como texto
        CAPA 10) Arrays y listas de objetos
        CAPA 11) Cómo diseñar una clase (y hacia dónde seguir)
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class ClassesAndObjects {

    public static void main(String[] args) {
        check(proceduralVsObjectExample(), "CAPA 1");
        check(classVsObjectExample(), "CAPA 2");
        check(fieldDefaultsExample(), "CAPA 3");
        check(instanceMethodsExample(), "CAPA 4");
        check(constructorExample(), "CAPA 5");
        check(constructorOverloadingExample(), "CAPA 6");
        check(thisExample(), "CAPA 7");
        check(initializationOrderExample(), "CAPA 8");
        check(toStringExample(), "CAPA 9");
        check(collectionsOfObjectsExample(), "CAPA 10");
        System.out.println("ClassesAndObjects: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES LA PROGRAMACIÓN ORIENTADA A OBJETOS
        =================================================================

        Hay dos formas principales de organizar un programa:

        - PROCEDURAL: piensas en DATOS sueltos (variables, arreglos) y en
          FUNCIONES separadas que los procesan paso a paso.

        - ORIENTADA A OBJETOS (POO, u OOP en inglés): piensas en COSAS
          del problema (un libro, un socio, un préstamo). Cada cosa
          guarda SUS datos y sabe hacer SUS acciones. Datos y
          comportamiento viajan juntos.

        Un OBJETO = ESTADO (los datos que guarda) + COMPORTAMIENTO (lo
        que sabe hacer con esos datos).

        Ejemplo: llevar el progreso de lectura de varios libros.
        Primero, al estilo procedural, con arreglos "paralelos": la
        posición i de cada arreglo corresponde al mismo libro.
    */
    static int progressPercentProcedural(int[] totalPages, int[] currentPages, int bookIndex) {
        return currentPages[bookIndex] * 100 / totalPages[bookIndex];
    }

    /*
        Problemas de esta forma:
        - Nada garantiza que los arreglos tengan el mismo tamaño ni que
          la posición 3 de uno sea el mismo libro que la posición 3 del
          otro. Si alguien ordena un arreglo y el otro no, los datos se
          mezclan en silencio.
        - Cualquier función puede escribir currentPages[i] = 9999.
        - Las reglas (no leer más páginas de las que tiene el libro)
          viven lejos de los datos, en funciones sueltas que hay que
          acordarse de llamar.

        La versión orientada a objetos junta todo lo de UN libro en UN
        lugar. La clase Book completa está en la CAPA 4; aquí solo se usa.
    */
    static boolean proceduralVsObjectExample() {
        int[] totalPages = {250, 400};
        int[] currentPages = {100, 50};
        int procedural = progressPercentProcedural(totalPages, currentPages, 0);

        Book book = new Book("Effective Java", "Joshua Bloch", 250);
        book.readPages(100);

        return procedural == 40 && book.progressPercent() == 40;
    }

    /*
        Si vienes de JavaScript:
        - En JS puedes crear un objeto al vuelo: { title: "X", pages: 250 }
          y agregarle propiedades cuando quieras.
        - En Java, la forma de cada objeto la define su CLASE, antes de
          que exista ningún objeto. Un Book siempre tiene exactamente los
          campos que declara la clase Book: ni más, ni menos, y con tipos
          fijos. El compilador lo revisa todo.
    */

    /*
        =================================================================
        CAPA 2) CLASE VS OBJETO
        =================================================================

        - CLASE: el MOLDE o plano. Describe qué datos tendrá cada objeto
          y qué sabrá hacer. Se escribe UNA vez. Por sí sola no guarda
          datos de ningún libro concreto.

        - OBJETO (o INSTANCIA): una cosa concreta creada con ese molde.
          Puede haber miles, y cada uno tiene SUS PROPIOS valores.

        Analogía: el plano de una casa es la clase. Cada casa construida
        con ese plano es un objeto. Todas tienen la misma distribución,
        pero si pintas una de azul, las demás no cambian.

        Los objetos se crean con new:

            Book book = new Book("Clean Code", "Robert C. Martin", 464);
                        ^^^ reserva memoria para un objeto nuevo y llama al
                            constructor (CAPA 5) para dejarlo listo

        La variable book no ES el objeto: guarda una REFERENCIA (una
        dirección) hacia él. Ese detalle se estudia a fondo en
        v1_fundamentals/ReferencesAndNull.
    */
    static boolean classVsObjectExample() {
        Book cleanCode = new Book("Clean Code", "Robert C. Martin", 464);
        Book refactoring = new Book("Refactoring", "Martin Fowler", 448);

        // Mismo molde, estados independientes: leer uno no afecta al otro.
        cleanCode.readPages(200);

        return cleanCode.getCurrentPage() == 200 && refactoring.getCurrentPage() == 0;
    }

    /*
        =================================================================
        CAPA 3) CAMPOS (ATRIBUTOS) Y SUS VALORES POR DEFECTO
        =================================================================

        Un CAMPO es una variable declarada dentro de la clase (fuera de
        cualquier método). Cada objeto tiene su propia copia de cada
        campo: es su ESTADO.

        Dato técnico importante: los campos SIEMPRE tienen un valor,
        aunque no les asignes ninguno. Java los inicializa con:

            números (int, long, double...)  -> 0 / 0.0
            boolean                         -> false
            char                            -> '\u0000' (carácter nulo)
            cualquier objeto (String, Book) -> null

        Esta clase no inicializa nada, para comprobarlo:
    */
    static final class FieldDefaults {
        int number;
        double decimal;
        boolean flag;
        char letter;
        String text;
        Book book;
    }

    static boolean fieldDefaultsExample() {
        FieldDefaults defaults = new FieldDefaults();
        return defaults.number == 0
                && defaults.decimal == 0.0
                && !defaults.flag
                && defaults.letter == '\u0000'
                && defaults.text == null
                && defaults.book == null;
    }

    /*
        Las VARIABLES LOCALES (las declaradas dentro de un método) NO
        reciben valor por defecto. Usarlas sin asignarles algo no compila:

            int pages;
            System.out.println(pages);   // ERROR: variable pages might not have been initialized

        Por qué la diferencia: el compilador puede seguir el flujo de un
        método y ver si asignaste la variable. Con los campos no puede
        (otro método o el constructor pudo asignarlos), así que Java
        garantiza un valor inicial.

        Cuidado: que un campo de objeto valga null por defecto es la
        fuente número uno de NullPointerException. Por eso los
        constructores (CAPA 5) deben asignar todos los campos que
        importan.
    */

    /*
        =================================================================
        CAPA 4) MÉTODOS DE INSTANCIA: COMPORTAMIENTO + ESTADO
        =================================================================

        Un MÉTODO DE INSTANCIA es una acción que se ejecuta SOBRE un
        objeto concreto y puede leer y cambiar SU estado.

            book.readPages(100);
            ^^^^ el objeto sobre el que se ejecuta

        Dentro del método, los campos (currentPage, pages) son los de ESE
        objeto. Si llamas readPages sobre otro libro, trabaja con los
        campos del otro.

        Tipos de métodos según lo que hacen:
        - COMANDOS: cambian el estado y normalmente devuelven void.
              readPages(int)
        - CONSULTAS: no cambian nada y devuelven un valor.
              progressPercent(), isFinished(), getTitle()
        Separar comandos de consultas hace el código fácil de predecir:
        preguntar algo nunca debería cambiar nada.

        La clase Book completa. (private significa "solo el código de
        esta clase puede tocar este campo"; se estudia en
        AccessModifiers y Encapsulation.)
    */
    static final class Book {
        private final String title;
        private final String author;
        private final int pages;
        private int currentPage;   // sin asignar: empieza en 0 (CAPA 3)

        // Constructor principal (CAPAS 5 y 6).
        Book(String title, String author, int pages) {
            if (pages <= 0) {
                throw new IllegalArgumentException("Un libro debe tener al menos una página");
            }
            this.title = requireText(title, "title");
            this.author = requireText(author, "author");
            this.pages = pages;
        }

        // Constructor sobrecargado (CAPA 6): delega en el principal.
        Book(String title, int pages) {
            this(title, "Anónimo", pages);
        }

        // COMANDO: cambia el estado. Nunca deja pasar del final del libro.
        void readPages(int count) {
            if (count <= 0) {
                throw new IllegalArgumentException("Hay que leer al menos una página");
            }
            currentPage = Math.min(pages, currentPage + count);
        }

        // CONSULTAS: calculan con el estado, no lo cambian.
        int progressPercent() {
            return currentPage * 100 / pages;
        }

        boolean isFinished() {
            return currentPage == pages;
        }

        String getTitle() {
            return title;
        }

        String getAuthor() {
            return author;
        }

        int getPages() {
            return pages;
        }

        int getCurrentPage() {
            return currentPage;
        }

        @Override // CAPA 9
        public String toString() {
            return "Book[" + title + " de " + author + ", " + progressPercent() + "% leído]";
        }
    }

    static boolean instanceMethodsExample() {
        Book book = new Book("Effective Java", "Joshua Bloch", 250);

        book.readPages(100);
        boolean partial = book.progressPercent() == 40 && !book.isFinished();

        book.readPages(1000); // más de lo que queda: se queda en la última página
        boolean finished = book.isFinished() && book.getCurrentPage() == 250;

        return partial && finished;
    }

    /*
        =================================================================
        CAPA 5) CONSTRUCTORES
        =================================================================

        Un CONSTRUCTOR es un bloque especial que se ejecuta UNA vez, al
        crear el objeto con new. Su trabajo: dejar el objeto listo y
        VÁLIDO para usarse.

        Reglas técnicas:
        1) Se llama EXACTAMENTE igual que la clase.
        2) NO tiene tipo de retorno, ni siquiera void. Si le pones void,
           deja de ser constructor y se vuelve un método normal que
           casualmente se llama igual que la clase (un error clásico).
        3) Si la clase NO declara ningún constructor, Java genera uno
           vacío sin parámetros ("constructor por defecto"). Por eso
           new FieldDefaults() funciona en la CAPA 3.
        4) En cuanto declaras CUALQUIER constructor, Java ya no genera
           el vacío:
               new Book();   // ERROR: Book no tiene constructor sin parámetros

        El problema de nombres que resuelve this:
        en el constructor de Book, el parámetro se llama title y el campo
        también. Dentro del constructor, "title" a secas es el
        PARÁMETRO (el nombre más cercano gana; a esto se le llama
        "sombrear" el campo). Para hablar del campo se escribe this.title.

            this.title = title;
            ^^^^^^^^^^   ^^^^^
            el campo     el parámetro

        Si escribieras title = title, asignarías el parámetro a sí mismo
        y el campo se quedaría en null.

        El constructor también es el lugar para VALIDAR: si los datos no
        tienen sentido, se lanza una excepción y el objeto nunca llega a
        existir. (Esto se profundiza en Encapsulation, CAPA 4.)
    */
    static boolean constructorExample() {
        Book valid = new Book("Clean Code", "Robert C. Martin", 464);
        boolean fieldsAssigned = valid.getTitle().equals("Clean Code") && valid.getPages() == 464;

        boolean invalidRejected;
        try {
            new Book("Libro vacío", "Nadie", 0);
            invalidRejected = false;
        } catch (IllegalArgumentException e) {
            invalidRejected = true;
        }

        return fieldsAssigned && invalidRejected;
    }

    /*
        =================================================================
        CAPA 6) SOBRECARGA DE CONSTRUCTORES Y THIS(...)
        =================================================================

        Una clase puede tener VARIOS constructores, siempre que cada uno
        reciba parámetros distintos (en cantidad, tipo u orden). Java
        elige cuál ejecutar según los argumentos que pases a new.
        Sirve para ofrecer formas cómodas de crear objetos con valores
        por defecto razonables.

        Book tiene dos:
            Book(String title, String author, int pages)   <- principal
            Book(String title, int pages)                   <- autor "Anónimo"

        La regla profesional: UN constructor principal hace todo el
        trabajo (validar y asignar), y los demás DELEGAN en él con
        this(...). Así la validación existe una sola vez.

            Book(String title, int pages) {
                this(title, "Anónimo", pages);   // llama al otro constructor
            }

        Reglas de this(...):
        - Llama a OTRO constructor de la MISMA clase.
        - Un constructor no puede llamar a this(...) y a super(...) a la
          vez: o delega en un hermano o en el padre (ver Inheritance).
        - Desde Java 25 puede ir después de código que no use el objeto
          (por ejemplo, validar argumentos). Antes tenía que ser
          obligatoriamente la primera línea.
    */
    static boolean constructorOverloadingExample() {
        Book anonymous = new Book("Lazarillo de Tormes", 96);
        return anonymous.getAuthor().equals("Anónimo") && anonymous.getPages() == 96;
    }

    /*
        Criterio de diseño: no crees constructores que produzcan objetos
        sin sentido. Un Book() sin parámetros que pusiera "Título
        desconocido" y 0 páginas crearía un libro que no existe. Si no
        hay un valor por defecto REAL, no ofrezcas ese constructor.

        Cuando hay muchos parámetros opcionales, los constructores
        sobrecargados se vuelven confusos (¿el tercer int era páginas o
        año?). Para eso existen las fábricas estáticas y el patrón
        Builder (v11_patterns/part1_creational/FactoryMethod y v11_patterns/part1_creational/Builder).
    */

    /*
        =================================================================
        CAPA 7) LOS CUATRO USOS DE THIS
        =================================================================

        this es una referencia al objeto sobre el que se está ejecutando
        el código en ese momento: "yo mismo".

        1) Distinguir un campo de un parámetro con el mismo nombre.
               this.title = title;                     (CAPA 5)

        2) Llamar a otro constructor de la misma clase.
               this(title, "Anónimo", pages);          (CAPA 6)

        3) Pasarse a sí mismo como argumento a otro objeto.
           Un socio pide un préstamo; el préstamo necesita saber QUÉ
           socio lo pidió, y ese socio es "yo".

        4) Devolverse a sí mismo para encadenar llamadas ("interfaz
           fluida"): lista.add(a).add(b).add(c)
    */
    static final class Member {
        private final String name;
        private final List<Loan> loans = new ArrayList<>();

        Member(String name) {
            this.name = requireText(name, "name");
        }

        Loan borrow(Book book) {
            Loan loan = new Loan(this, book);   // uso 3: me paso a mí mismo
            loans.add(loan);
            return loan;
        }

        String getName() {
            return name;
        }

        int loanCount() {
            return loans.size();
        }
    }

    static final class Loan {
        private final Member member;
        private final Book book;

        Loan(Member member, Book book) {
            this.member = Objects.requireNonNull(member, "member");
            this.book = Objects.requireNonNull(book, "book");
        }

        Member getMember() {
            return member;
        }

        Book getBook() {
            return book;
        }
    }

    static final class ReadingList {
        private final List<Book> books = new ArrayList<>();

        ReadingList add(Book book) {
            books.add(Objects.requireNonNull(book, "book"));
            return this;   // uso 4: devuelvo el mismo objeto para seguir encadenando
        }

        int totalPages() {
            int total = 0;
            for (Book book : books) {
                total += book.getPages();
            }
            return total;
        }
    }

    static boolean thisExample() {
        Member luis = new Member("Luis");
        Book book = new Book("Effective Java", "Joshua Bloch", 416);
        Loan loan = luis.borrow(book);

        // El préstamo guarda una referencia al MISMO socio que lo creó.
        boolean loanKnowsMember = loan.getMember() == luis && luis.loanCount() == 1;

        int pages = new ReadingList()
                .add(new Book("Clean Code", "Robert C. Martin", 464))
                .add(new Book("Refactoring", "Martin Fowler", 448))
                .totalPages();

        return loanKnowsMember && pages == 912;
    }

    /*
        Detalle técnico: this NO existe dentro de un método static,
        porque un método static no se ejecuta sobre ningún objeto
        (Static lo explica).
    */

    /*
        =================================================================
        CAPA 8) ORDEN DE INICIALIZACIÓN DE UN OBJETO
        =================================================================

        Además del constructor, hay otras dos formas de dar valores
        iniciales a un objeto:

        - INICIALIZADOR DE CAMPO: asignar en la misma declaración.
              private final List<Loan> loans = new ArrayList<>();

        - BLOQUE DE INICIALIZACIÓN DE INSTANCIA: un bloque { } suelto
          dentro de la clase. Se ejecuta en cada new, antes del cuerpo
          del constructor.

        Cuando haces new, Java sigue SIEMPRE este orden:
        1) Reserva memoria y pone TODOS los campos en su valor por
           defecto (0, false, null).
        2) Ejecuta el constructor del padre (super; ver Inheritance).
        3) Ejecuta inicializadores de campo y bloques de instancia, en el
           orden en que aparecen escritos en la clase, de arriba abajo.
        4) Ejecuta el resto del cuerpo del constructor.

        Esta clase anota cada paso en una lista para verlo:
    */
    static final class InitializationOrder {
        private final List<String> steps = new ArrayList<>();          // va primero: los demás lo usan
        private final String fieldInitializer = record("inicializador de campo");

        {
            record("bloque de instancia");
        }

        InitializationOrder() {
            record("cuerpo del constructor");
        }

        private String record(String step) {
            steps.add(step);
            return step;
        }

        List<String> getSteps() {
            return List.copyOf(steps);
        }

        String getFieldInitializer() {
            return fieldInitializer;
        }
    }

    static boolean initializationOrderExample() {
        InitializationOrder order = new InitializationOrder();
        return order.getSteps().equals(List.of("inicializador de campo", "bloque de instancia", "cuerpo del constructor"))
                && order.getFieldInitializer().equals("inicializador de campo");
    }

    /*
        El orden textual importa: si "fieldInitializer" estuviera escrito
        ARRIBA de "steps", record() encontraría steps todavía en null
        (paso 1) y lanzaría NullPointerException.

        En la práctica: los bloques de instancia casi nunca se usan.
        Inicializa en la declaración lo que no depende de parámetros
        (como la lista vacía de loans) y todo lo demás en el
        constructor. Es lo más fácil de leer.
        (El orden de lo static se explica en Static.)
    */

    /*
        =================================================================
        CAPA 9) TOSTRING: CÓMO SE VE UN OBJETO COMO TEXTO
        =================================================================

        Todo objeto tiene un método toString() que lo convierte en texto
        (lo hereda de Object; ver Inheritance, CAPA 7). Java lo llama
        AUTOMÁTICAMENTE cuando:
        - concatenas un objeto con un String: "Libro: " + book
        - lo pasas a System.out.println(book) o a String.valueOf(book)
        - un logger o el depurador lo muestran

        Si no lo sobrescribes, obtienes algo como:
            v2_oop.part1_basics.ClassesAndObjects$FieldDefaults@1b6d3586
            (nombre completo de la clase + @ + un número en hexadecimal)
        que no dice nada útil.

        Al sobrescribirlo, decides cómo se ve. Book lo hace en la CAPA 4.

        Buenas prácticas:
        - Incluye lo necesario para reconocer el objeto al depurar.
        - NUNCA incluyas datos sensibles (contraseñas, tarjetas completas):
          los toString terminan en logs.
        - No lo uses como formato "oficial" para mostrar al usuario o
          para guardar datos: es para humanos depurando. Si necesitas un
          formato estable, crea un método específico.
        - Los records (Records) generan uno automáticamente.
    */
    static boolean toStringExample() {
        Book book = new Book("Clean Code", "Robert C. Martin", 464);
        book.readPages(116);

        String overridden = "Leyendo: " + book; // concatenar llama a toString() sin escribirlo
        String inherited = new FieldDefaults().toString();

        return overridden.equals("Leyendo: Book[Clean Code de Robert C. Martin, 25% leído]")
                && inherited.contains("$FieldDefaults@");
    }

    /*
        =================================================================
        CAPA 10) ARRAYS Y LISTAS DE OBJETOS
        =================================================================

        Un arreglo de objetos NO guarda objetos: guarda REFERENCIAS. Al
        crearlo, cada posición vale null (CAPA 3), y hay que llenarlo.

            Book[] shelf = new Book[3];   // tres posiciones, las tres en null
                                          // no se creó ningún Book

        Llamar un método sobre una posición vacía lanza
        NullPointerException.

        En código real se prefiere List<Book>: crece sola, no tiene
        posiciones vacías y tiene métodos útiles. (Las listas se estudian
        a fondo en v5_collections/Lists.)

        El valor real de tener objetos en una colección: el ciclo le
        pide a CADA objeto que haga algo con SUS datos, sin manejar
        arreglos paralelos como en la CAPA 1.
    */
    static List<String> unfinishedTitles(List<Book> books) {
        List<String> titles = new ArrayList<>();
        for (Book book : books) {
            if (!book.isFinished()) {
                titles.add(book.getTitle());
            }
        }
        return titles;
    }

    static boolean collectionsOfObjectsExample() {
        Book[] shelf = new Book[3];
        shelf[0] = new Book("Clean Code", "Robert C. Martin", 464);
        boolean emptySlotsAreNull = shelf[1] == null && shelf[2] == null;

        boolean nullSlotFails;
        try {
            shelf[1].getTitle();
            nullSlotFails = false;
        } catch (NullPointerException e) {
            nullSlotFails = true;
        }

        Book finished = new Book("El Principito", "Antoine de Saint-Exupéry", 96);
        finished.readPages(96);
        List<Book> library = List.of(shelf[0], finished, new Book("Refactoring", "Martin Fowler", 448));

        return emptySlotsAreNull
                && nullSlotFails
                && unfinishedTitles(library).equals(List.of("Clean Code", "Refactoring"));
    }

    /*
        =================================================================
        CAPA 11) CÓMO DISEÑAR UNA CLASE (Y HACIA DÓNDE SEGUIR)
        =================================================================

        La POO no es "hacer clases por hacer". La meta es modelar el
        problema de forma que cada pieza tenga UNA responsabilidad clara
        y proteja sus propias reglas.

        Preguntas para diseñar cualquier clase:
        1) ¿Qué COSA del problema representa? Si no puedes nombrarla con
           un sustantivo del negocio (Libro, Préstamo), sospecha.
        2) ¿Qué ESTADO necesita guardar? Solo lo suyo. Lo que se puede
           calcular, se calcula (progressPercent no es un campo).
        3) ¿Qué COMPORTAMIENTO le corresponde? Las acciones que usan sus
           datos van DENTRO de la clase (readPages), no en funciones
           sueltas afuera.
        4) ¿Qué datos pueden cambiar y cuáles no? Lo que no cambia, final.
        5) ¿Con qué estado nace? El constructor debe exigir todo lo
           necesario para que el objeto sea válido desde el primer
           momento.
        6) ¿Qué debe quedar privado? Casi todo el estado.
        7) ¿Se relaciona con otras clases? ¿Es un "ES UN" (herencia) o un
           "TIENE UN" (composición, como Loan que tiene un Member y un
           Book)?

        Cada pregunta tiene su guía:

        | Pregunta / idea                              | Guía                          |
        |----------------------------------------------|-------------------------------|
        | Cosas de la clase y no del objeto            | Static                    |
        | Quién puede ver cada cosa                    | AccessModifiers     |
        | Proteger el estado y sus reglas              | Encapsulation             |
        | "ES UN": reutilizar y especializar           | Inheritance               |
        | Una llamada, muchos comportamientos          | Polymorphism              |
        | Separar el QUÉ del CÓMO                      | Abstraction               |
        | Contratos y sus capacidades modernas         | Interfaces                |
        | "TIENE UN": objetos dentro de objetos        | CompositionAndAggregation    |
        | Clases dentro de clases                      | NestedClasses            |
        | Un número fijo de valores (días, estados)    | Enums                     |
        | Clases que solo cargan datos                 | Records                   |
        | equals, hashCode, comparar objetos           | ObjectContract          |
        | Jerarquías cerradas y switch por tipo        | SealedAndPatternMatching    |
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Confundir la clase con el objeto.
           La clase es el molde; los datos viven en cada objeto (CAPA 2).

        2) Ponerle void a un constructor.
           void Book(...) ya no es constructor: es un método. new Book(...)
           no lo ejecuta (CAPA 5).

        3) Escribir title = title en el constructor.
           Se asigna el parámetro a sí mismo; falta this. (CAPA 5).

        4) Esperar que exista el constructor vacío después de declarar
           otro. Java solo lo genera si no declaraste ninguno (CAPA 5).

        5) Duplicar la validación en cada constructor sobrecargado.
           Uno principal; los demás delegan con this(...) (CAPA 6).

        6) Constructores que crean objetos sin sentido ("Título
           desconocido", 0 páginas) (CAPA 6).

        7) Olvidar que los campos de objeto empiezan en null y que los
           arreglos de objetos nacen llenos de null (CAPAS 3 y 10).

        8) Guardar lo que se puede calcular. Dos fuentes del mismo dato
           terminan contradiciéndose (CAPA 11).

        9) Clases que solo guardan datos mientras la lógica vive en
           funciones sueltas afuera. Eso es programación procedural con
           sintaxis de clases (CAPA 1; se profundiza en Encapsulation,
           CAPA 5: "modelo anémico").

        10) toString con datos sensibles o usado como formato oficial
            (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - POO = organizar el programa en objetos que juntan ESTADO y
          COMPORTAMIENTO.
        - Clase = molde; objeto = instancia concreta con su propio estado.
        - Campos: el estado. Tienen valor por defecto (0, false, null);
          las variables locales no.
        - Métodos de instancia: trabajan sobre el estado de SU objeto.
          Comandos cambian, consultas preguntan.
        - Constructor: mismo nombre que la clase, sin retorno, deja el
          objeto válido. this.campo = parámetro.
        - Sobrecarga: varios constructores; uno principal y los demás
          delegan con this(...).
        - this: el objeto actual. Distingue campos, llama constructores,
          se pasa como argumento y permite encadenar.
        - Orden: valores por defecto -> padre -> inicializadores y
          bloques en orden escrito -> cuerpo del constructor.
        - toString: texto para depurar; sobrescríbelo sin datos sensibles.
        - Colecciones de objetos guardan referencias; prefiere List a
          arreglos.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " no puede ser null ni vacío");
        }
        return value;
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
