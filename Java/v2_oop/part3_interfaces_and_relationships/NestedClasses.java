package v2_oop.part3_interfaces_and_relationships;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/*
    =====================================================================
    CLASES ANIDADAS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 10 de 14  (part3_interfaces_and_relationships)
    Anterior:  part3_interfaces_and_relationships/CompositionAndAggregation
    Siguiente: part4_advanced_types/Enums

    Requisito: ClassesAndObjects, Static y Interfaces.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es una clase anidada y los 4 tipos
        CAPA 2)  Clase anidada static
        CAPA 3)  Clase interna (inner): ligada a un objeto externo
        CAPA 4)  El costo oculto de las clases internas
        CAPA 5)  Clase local: declarada dentro de un método
        CAPA 6)  Clase anónima: se declara y se usa en el mismo lugar
        CAPA 7)  Clase anónima vs lambda
        CAPA 8)  Acceso entre la clase externa y sus anidadas
        CAPA 9)  Cuándo usar cada tipo
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class NestedClasses {

    public static void main(String[] args) {
        check(staticNestedExample(), "CAPA 2");
        check(innerClassExample(), "CAPA 3");
        check(hiddenReferenceExample(), "CAPA 4");
        check(localClassExample(), "CAPA 5");
        check(anonymousClassExample(), "CAPA 6");
        check(anonymousVsLambdaExample(), "CAPA 7");
        System.out.println("NestedClasses: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UNA CLASE ANIDADA Y LOS 4 TIPOS
        =================================================================

        Una CLASE ANIDADA es una clase declarada DENTRO de otra. La de
        afuera se llama clase EXTERNA (o contenedora).

        Para qué sirve:
        - Agrupar un tipo que solo tiene sentido junto a otro
          (Playlist.Song, Map.Entry).
        - Esconder detalles: una anidada private no existe para nadie
          fuera de su clase externa (como OrderLine en
          CompositionAndAggregation).
        - Dejar el código cerca de donde se usa.

        Ya las has usado en TODAS las guías: cada clase de ejemplo
        (Book, Order, Discount...) es una clase anidada static dentro de
        la clase de la guía.

        Hay cuatro tipos:

        | Tipo            | Dónde se declara           | ¿Necesita un objeto externo? | Uso típico                 |
        |-----------------|----------------------------|------------------------------|----------------------------|
        | anidada static  | dentro de la clase         | No                           | tipos auxiliares, Builder  |
        | interna (inner) | dentro de la clase, sin static | Sí, siempre               | iteradores, vistas         |
        | local           | dentro de un método        | Sí, si el método no es static| ayuda de un solo método    |
        | anónima         | en una expresión new       | Igual que la local           | implementación de un uso   |

        Los tres últimos tipos juntos se llaman "clases internas" (inner
        classes) en la especificación de Java, porque todos pueden estar
        ligados a un objeto externo. La anidada static no.

        El ejemplo de toda la guía: una playlist de música.
    */

    /*
        =================================================================
        CAPA 2) CLASE ANIDADA STATIC
        =================================================================

        Es una clase normal que simplemente vive DENTRO del "espacio de
        nombres" de otra. No está ligada a ningún objeto de la clase
        externa: se crea sola, igual que cualquier clase.

            Playlist.Song song = new Playlist.Song("Clocks", "Coldplay", 307);

        Se usa cuando el tipo:
        - está íntimamente relacionado con la clase externa, y
        - NO necesita acceder a los datos de un objeto externo concreto.

        Dos usos clásicos en la Playlist de abajo:
        - Song: una canción de la playlist. Tiene sentido como
          "Playlist.Song".
        - Builder: arma una Playlist paso a paso (el patrón completo está
          en v11_patterns/part1_creational/Builder). Puede llamar al constructor private de
          Playlist porque está anidada dentro de ella (CAPA 8).
    */
    static final class Playlist implements Iterable<Playlist.Song> {
        private final String name;
        private final List<Song> songs;

        // private: la única forma de crear una Playlist es con su Builder.
        private Playlist(Builder builder) {
            this.name = builder.name;
            this.songs = List.copyOf(builder.songs);
        }

        static Builder named(String name) {
            return new Builder(name);
        }

        String getName() {
            return name;
        }

        int size() {
            return songs.size();
        }

        int totalSeconds() {
            int total = 0;
            for (Song song : songs) {
                total += song.seconds();
            }
            return total;
        }

        // Iterable: permite usar la Playlist en un for-each. Devuelve una clase INTERNA (CAPA 3).
        @Override
        public Iterator<Song> iterator() {
            return new SongIterator();
        }

        // ---------- ANIDADA STATIC: no necesita una Playlist para existir ----------
        static final class Song {
            private final String title;
            private final String artist;
            private final int seconds;

            Song(String title, String artist, int seconds) {
                this.title = Objects.requireNonNull(title, "title");
                this.artist = Objects.requireNonNull(artist, "artist");
                if (seconds <= 0) {
                    throw new IllegalArgumentException("La duración debe ser positiva");
                }
                this.seconds = seconds;
            }

            String title() {
                return title;
            }

            String artist() {
                return artist;
            }

            int seconds() {
                return seconds;
            }
        }

        // ---------- ANIDADA STATIC: el Builder ----------
        static final class Builder {
            private final String name;
            private final List<Song> songs = new ArrayList<>();

            private Builder(String name) {
                this.name = Objects.requireNonNull(name, "name");
            }

            Builder add(String title, String artist, int seconds) {
                songs.add(new Song(title, artist, seconds));
                return this;
            }

            Playlist build() {
                if (songs.isEmpty()) {
                    throw new IllegalStateException("Una playlist necesita al menos una canción");
                }
                return new Playlist(this);   // constructor private de la externa: permitido (CAPA 8)
            }
        }

        // ---------- INTERNA (sin static): cada una pertenece a UNA Playlist (CAPA 3) ----------
        final class SongIterator implements Iterator<Song> {
            private int position;

            @Override
            public boolean hasNext() {
                return position < songs.size();   // "songs" es el campo de SU Playlist
            }

            @Override
            public Song next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No hay más canciones en " + name);
                }
                return songs.get(position++);
            }

            // Playlist.this = el objeto externo al que está ligado este iterador (CAPA 3).
            Playlist owner() {
                return Playlist.this;
            }
        }
    }

    static boolean staticNestedExample() {
        // La anidada static se crea sola, sin ninguna Playlist.
        Playlist.Song single = new Playlist.Song("Clocks", "Coldplay", 307);

        Playlist playlist = Playlist.named("Para programar")
                .add("Clocks", "Coldplay", 307)
                .add("Intro", "The xx", 128)
                .build();

        return single.title().equals("Clocks")
                && playlist.size() == 2
                && playlist.totalSeconds() == 435;
    }

    /*
        =================================================================
        CAPA 3) CLASE INTERNA (INNER): LIGADA A UN OBJETO EXTERNO
        =================================================================

        Una clase anidada SIN static. Cada objeto de la clase interna
        está atado a UN objeto de la clase externa, y puede usar
        directamente sus campos y métodos, incluso los private.

        SongIterator lee "songs" y "name" sin recibirlos: son los de la
        Playlist que lo creó. Dos iteradores de dos playlists distintas
        recorren canciones distintas.

        Por eso NO se puede crear sin un objeto externo:
        - Dentro de un método de instancia de Playlist basta con
              new SongIterator()          (se liga a this)
        - Desde fuera, la sintaxis (rara, y señal de mal diseño si la
          ves seguido) es:
              playlist.new SongIterator()
              new Playlist.SongIterator() // ERROR: an enclosing instance that contains
                                          // Playlist.SongIterator is required
                                          // (no hay ninguna Playlist a la cual ligarlo)

        Playlist.this
        Dentro de la clase interna, "this" es el iterador. Para hablar
        del objeto externo se escribe NombreExterna.this. Es necesario
        también cuando la interna tiene un campo con el mismo nombre que
        uno de la externa: name sería el de la interna, Playlist.this.name
        el de la externa.

        Este es el uso clásico de las clases internas: un ITERADOR
        (o una "vista") que necesita los datos privados de un objeto
        concreto. Así lo hace ArrayList con su iterador. Iterator e
        Iterable se estudian en v5_collections/Iterators.
    */
    static boolean innerClassExample() {
        Playlist rock = Playlist.named("Rock").add("Yellow", "Coldplay", 266).add("Creep", "Radiohead", 238).build();
        Playlist chill = Playlist.named("Chill").add("Intro", "The xx", 128).build();

        // for-each llama a iterator(), que crea un SongIterator ligado a "rock".
        List<String> titles = new ArrayList<>();
        for (Playlist.Song song : rock) {
            titles.add(song.title());
        }

        // Creación desde fuera: hay que decir A QUÉ playlist pertenece.
        Playlist.SongIterator chillIterator = chill.new SongIterator();

        return titles.equals(List.of("Yellow", "Creep"))
                && chillIterator.next().title().equals("Intro")
                && !chillIterator.hasNext();
    }

    /*
        =================================================================
        CAPA 4) EL COSTO OCULTO DE LAS CLASES INTERNAS
        =================================================================

        ¿Cómo sabe SongIterator a qué Playlist pertenece? El compilador
        le agrega un CAMPO OCULTO con una referencia al objeto externo, y
        un parámetro oculto en su constructor para llenarlo. Playlist.this
        lee ese campo.

        Consecuencias:
        1) Cada objeto interno pesa un poco más (una referencia extra).
        2) MIENTRAS EXISTA el objeto interno, el objeto externo NO puede
           liberarse de memoria, aunque nadie más lo use. Si guardas un
           iterador (o un listener interno) en una lista que vive mucho
           tiempo, mantienes viva a toda la Playlist con todas sus
           canciones. Es una fuga de memoria clásica y difícil de ver
           (v12_jvm/part1_memory/GarbageCollector).
        3) El objeto interno no se puede crear sin el externo, lo que
           complica reutilizarlo o probarlo solo.
    */
    static boolean hiddenReferenceExample() {
        Playlist playlist = Playlist.named("Mix").add("Clocks", "Coldplay", 307).build();
        Playlist.SongIterator iterator = playlist.iterator() instanceof Playlist.SongIterator it ? it : null;

        // El iterador guarda una referencia al MISMO objeto Playlist (el campo oculto).
        return iterator != null && iterator.owner() == playlist;
    }

    /*
        LA REGLA (Effective Java, "prefiere clases anidadas static"):
        si la clase anidada NO necesita acceder al objeto externo, hazla
        static. Song y Builder no lo necesitan: son static. SongIterator
        sí lo necesita (recorre las canciones de SU playlist): es interna.

        Olvidar el static es el error más común con clases anidadas, y
        no da ningún aviso: todo funciona, pero cada objeto NECESITA un
        objeto externo para crearse. Si además la clase interna usa ese
        objeto externo, lo guarda en un campo oculto (this$0) y lo mantiene
        vivo mientras ella viva. (Desde JDK 18, javac ya no guarda ese campo
        si la interna nunca usa al externo; medido con javap.)
    */

    /*
        =================================================================
        CAPA 5) CLASE LOCAL: DECLARADA DENTRO DE UN MÉTODO
        =================================================================

        Una clase declarada DENTRO del cuerpo de un método. Solo existe
        dentro de ese método: fuera de él no se puede nombrar.

        Puede usar:
        - los campos de la clase externa (si el método no es static),
        - las variables locales y parámetros del método, SIEMPRE QUE
          SEAN EFECTIVAMENTE FINALES (nunca se reasignan).

        Por qué la restricción: la clase local guarda una COPIA del valor
        de la variable. Si la variable pudiera cambiar después, la copia
        y el original dirían cosas distintas. Java lo evita prohibiéndolo.

            int minSeconds = 200;
            minSeconds++;                      // ahora ya no es efectivamente final
            class Filter { ... minSeconds ... } // ERROR: local variables referenced from
                                               // an inner class must be final or effectively final
    */
    static List<String> titlesAtLeast(Playlist playlist, int minSeconds) {
        // Clase local: usa el parámetro minSeconds (efectivamente final: nunca se reasigna).
        class DurationFilter {
            boolean accepts(Playlist.Song song) {
                return song.seconds() >= minSeconds;
            }
        }

        DurationFilter filter = new DurationFilter();
        List<String> titles = new ArrayList<>();
        for (Playlist.Song song : playlist) {
            if (filter.accepts(song)) {
                titles.add(song.title());
            }
        }
        return titles;
    }

    /*
        Desde Java 16 también se pueden declarar RECORDS, ENUMS e
        INTERFACES locales. Esos son implícitamente static: NO pueden usar
        variables del método ni campos de instancia. Son útiles para
        darle nombre a un dato intermedio dentro de un método.
        (Los records se estudian en Records.)
    */
    static String longestSong(Playlist playlist) {
        record Candidate(String title, int seconds) { }   // record local

        Candidate best = null;
        for (Playlist.Song song : playlist) {
            if (best == null || song.seconds() > best.seconds()) {
                best = new Candidate(song.title(), song.seconds());
            }
        }
        return best == null ? "" : best.title();
    }

    static boolean localClassExample() {
        Playlist playlist = Playlist.named("Variado")
                .add("Clocks", "Coldplay", 307)
                .add("Intro", "The xx", 128)
                .add("Creep", "Radiohead", 238)
                .build();

        return titlesAtLeast(playlist, 200).equals(List.of("Clocks", "Creep"))
                && longestSong(playlist).equals("Clocks");
    }

    /*
        En la práctica, las clases locales son raras: casi siempre una
        lambda (si es una sola acción) o un método private hacen lo mismo
        con menos código. Úsalas cuando la ayuda tiene VARIOS métodos o
        estado y solo tiene sentido dentro de ese método.
    */

    /*
        =================================================================
        CAPA 6) CLASE ANÓNIMA: SE DECLARA Y SE USA EN EL MISMO LUGAR
        =================================================================

        Una clase SIN NOMBRE que se declara y se instancia en la misma
        expresión. Sirve para crear UNA implementación de una interface
        (o una subclase) que solo se usa en ese punto.

            Interfaz variable = new Interfaz() {
                // cuerpo de la clase: campos y métodos
            };

        Reglas:
        - Implementa UNA interface o extiende UNA clase, no ambas ni varias.
        - No tiene constructor (no tiene nombre para declararlo). Si
          extiende una clase, los argumentos van al constructor del padre:
          new Base(arg) { ... }
        - Puede tener campos y varios métodos.
        - Captura variables igual que la clase local (efectivamente finales).

        Ejemplo: un reproductor avisa a un "listener" cada vez que suena
        o se salta una canción. La interface tiene DOS métodos, así que no
        se puede implementar con una lambda: aquí la clase anónima es la
        herramienta correcta.
    */
    interface PlaybackListener {
        void onPlay(Playlist.Song song);

        void onSkip(Playlist.Song song);
    }

    // Reproduce la playlist; salta las canciones más cortas que el mínimo.
    static void play(Playlist playlist, int minSeconds, PlaybackListener listener) {
        for (Playlist.Song song : playlist) {
            if (song.seconds() < minSeconds) {
                listener.onSkip(song);
            } else {
                listener.onPlay(song);
            }
        }
    }

    static boolean anonymousClassExample() {
        Playlist playlist = Playlist.named("Mix")
                .add("Clocks", "Coldplay", 307)
                .add("Intro", "The xx", 128)
                .add("Creep", "Radiohead", 238)
                .build();

        // Clase anónima con ESTADO propio (dos contadores) y dos métodos.
        PlaybackListener stats = new PlaybackListener() {
            private int played;
            private int skipped;

            @Override
            public void onPlay(Playlist.Song song) {
                played++;
            }

            @Override
            public void onSkip(Playlist.Song song) {
                skipped++;
            }

            @Override
            public String toString() {
                return played + " reproducidas, " + skipped + " saltadas";
            }
        };

        play(playlist, 200, stats);

        return stats.toString().equals("2 reproducidas, 1 saltadas")
                && stats.getClass().isAnonymousClass();
    }

    /*
        Detalle: el compilador sí le pone un nombre interno. Al compilar
        verás archivos como NestedClasses$1.class (la primera anónima),
        y las anidadas con nombre como NestedClasses$Playlist$Song.class.
    */

    /*
        =================================================================
        CAPA 7) CLASE ANÓNIMA VS LAMBDA
        =================================================================

        Antes de Java 8, las clases anónimas eran LA forma de pasar
        comportamiento (un Comparator, un listener). Desde Java 8, si la
        interface es FUNCIONAL (un solo método abstracto; ver
        Interfaces, CAPA 8), una lambda hace lo mismo en una línea:
    */
    static boolean anonymousVsLambdaExample() {
        Comparator<Playlist.Song> byDurationAnonymous = new Comparator<>() {
            @Override
            public int compare(Playlist.Song a, Playlist.Song b) {
                return Integer.compare(a.seconds(), b.seconds());
            }
        };

        Comparator<Playlist.Song> byDurationLambda = (a, b) -> Integer.compare(a.seconds(), b.seconds());

        List<Playlist.Song> songs1 = new ArrayList<>(List.of(
                new Playlist.Song("Clocks", "Coldplay", 307), new Playlist.Song("Intro", "The xx", 128)));
        List<Playlist.Song> songs2 = new ArrayList<>(songs1);
        songs1.sort(byDurationAnonymous);
        songs2.sort(byDurationLambda);

        return songs1.get(0).title().equals("Intro") && songs2.get(0).title().equals("Intro");
    }

    /*
        No son equivalentes. Diferencias técnicas:

        | Aspecto                    | Clase anónima                     | Lambda                               |
        |----------------------------|-----------------------------------|--------------------------------------|
        | Funciona con               | cualquier interface o clase       | solo interfaces funcionales          |
        | Puede tener estado propio  | Sí (campos)                       | No                                   |
        | Varios métodos             | Sí                                | No (es UN método)                    |
        | Qué significa this         | la propia clase anónima           | el objeto que la contiene            |
        | Crea una clase nueva       | Sí, un .class por cada una        | No (se genera al ejecutar, más ligero)|
        | Referencia oculta al objeto externo | Solo si lo usa (JDK 18+; antes, siempre) | Solo si usa this o sus campos |

        Regla práctica:
        - Interface funcional, sin estado -> LAMBDA.
        - Varios métodos, estado propio, o extender una clase -> CLASE
          ANÓNIMA (como el PlaybackListener de la CAPA 6).
        - Si la clase anónima crece o se usa en varios lugares -> dale
          nombre: anidada static o clase normal.
    */

    /*
        =================================================================
        CAPA 8) ACCESO ENTRE LA CLASE EXTERNA Y SUS ANIDADAS
        =================================================================

        La clase externa y TODAS sus anidadas (de cualquier tipo y nivel)
        forman una sola "familia" que comparte acceso a sus private:
        - Builder llama al constructor private de Playlist.
        - Playlist podría leer los campos private de Song.
        - SongIterator lee el campo private songs de su Playlist.

        Por eso, en las guías anteriores, varios comentarios decían "aquí
        compilaría solo porque la clase está anidada": la clase de la guía
        ve los private de sus clases de ejemplo.

        Detalle técnico: desde Java 11, la JVM entiende directamente estas
        "familias" (se llaman nestmates). Antes, el compilador tenía que
        generar métodos ocultos de acceso para cada private usado entre
        ellas.

        Modificadores de acceso: una clase anidada puede ser private, de
        paquete, protected o public, como cualquier miembro (las de nivel
        superior solo public o de paquete; ver AccessModifiers,
        CAPA 8). Una anidada private, como OrderLine en
        CompositionAndAggregation, es invisible fuera de su externa.

        Y desde Java 16, las clases internas también pueden declarar
        miembros static (antes estaba prohibido).
    */

    /*
        =================================================================
        CAPA 9) CUÁNDO USAR CADA TIPO
        =================================================================

        1) ¿El tipo se usa fuera de esta clase, en varios lugares?
               -> clase de NIVEL SUPERIOR, en su propio archivo.

        2) ¿Solo tiene sentido junto a esta clase?
               -> ANIDADA. Y entonces:

           2a) ¿Necesita los datos de un objeto externo concreto?
                   No -> anidada STATIC (la opción por defecto).
                   Sí -> INTERNA (iteradores, vistas sobre los datos del
                         objeto).

        3) ¿Solo se usa dentro de un método?
               -> LOCAL si tiene varios métodos o estado;
                  método private si es solo lógica;
                  record local si es solo un dato intermedio.

        4) ¿Es una implementación de un solo uso?
               -> LAMBDA si la interface es funcional;
                  ANÓNIMA si tiene varios métodos o estado.

        Ejemplos reales del JDK:
            Map.Entry                 anidada static (interface anidada)
            ArrayList.Itr             interna (el iterador de ArrayList)
            AbstractMap.SimpleEntry   anidada static
            Thread.State              enum anidado (siempre static)
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Olvidar static en una anidada que no usa el objeto externo:
           obliga a tener uno para crearla, y si la interna lo usa, lo
           retiene con una referencia oculta que puede causar fugas de
           memoria (CAPA 4).

        2) Intentar crear una clase interna sin objeto externo:
           new Playlist.SongIterator() no compila (CAPA 3).

        3) Confundir this y Externa.this dentro de una clase interna
           (CAPA 3).

        4) Usar en una clase local o anónima una variable del método que
           se reasigna. No compila: debe ser efectivamente final (CAPA 5).

        5) Clases anónimas enormes en medio de un método. Si pasa de unas
           pocas líneas, dale nombre (CAPA 7).

        6) Usar una clase anónima para una interface funcional donde una
           lambda basta (CAPA 7).

        7) Esperar que this dentro de una lambda sea la lambda. Es el
           objeto que la contiene (CAPA 7).

        8) Guardar listeners o iteradores internos en estructuras que
           viven mucho tiempo (cachés, listas estáticas): mantienen vivo
           al objeto externo (CAPA 4).

        9) Anidar por anidar. Si el tipo se usa en varias partes del
           sistema, merece su propio archivo (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Anidada static: un tipo que vive en el espacio de nombres de
          otro; no necesita objeto externo. La opción por defecto.
        - Interna (sin static): ligada a un objeto externo; ve sus campos
          y lo referencia con Externa.this. Se crea con objeto.new Interna().
          Cuesta una referencia oculta que puede retener memoria.
        - Local: dentro de un método; captura variables efectivamente
          finales. Records, enums e interfaces locales son static.
        - Anónima: sin nombre, se declara y crea en una expresión; puede
          tener estado y varios métodos.
        - Lambda en lugar de anónima cuando la interface es funcional y no
          hay estado.
        - La externa y sus anidadas comparten acceso a sus private.
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
