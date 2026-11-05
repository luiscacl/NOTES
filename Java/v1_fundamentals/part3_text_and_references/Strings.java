package v1_fundamentals.part3_text_and_references;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/*
    =====================================================================
    STRINGS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 9 de 14  (part3_text_and_references)
    Anterior:  part2_control_flow/Methods
    Siguiente: part3_text_and_references/ReferencesAndNull

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es un String
        CAPA 2)  Inmutable: por qué un String nunca cambia
        CAPA 3)  El String pool y por qué NUNCA se compara con ==
        CAPA 4)  Buscar y extraer: índices y substring
        CAPA 5)  Transformar: mayúsculas, espacios, reemplazos
        CAPA 6)  Dividir y unir: split, join, repeat
        CAPA 7)  Comparar y ordenar texto
        CAPA 8)  Recorrer un String carácter por carácter
        CAPA 9)  StringBuilder: construir texto eficientemente
        CAPA 10) Formatear: format, formatted y printf
        CAPA 11) Text blocks: texto de varias líneas
        CAPA 12) Unicode: cuando un "carácter" no es un char
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class Strings {

    public static void main(String[] args) {
        check(stringBasicsExample(), "CAPA 1");
        check(immutabilityExample(), "CAPA 2");
        check(stringPoolExample(), "CAPA 3");
        check(searchExample(), "CAPA 4");
        check(transformExample(), "CAPA 5");
        check(splitJoinExample(), "CAPA 6");
        check(compareExample(), "CAPA 7");
        check(traverseExample(), "CAPA 8");
        check(stringBuilderExample(), "CAPA 9");
        check(formatExample(), "CAPA 10");
        check(textBlockExample(), "CAPA 11");
        check(unicodeExample(), "CAPA 12");
        System.out.println("Strings: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN STRING
        =================================================================

        Un String es una SECUENCIA DE CARACTERES: texto. Se escribe entre
        comillas DOBLES:
            String name = "Luis";
            char initial = 'L';        // un char: comillas simples, UN carácter

        String NO es un tipo primitivo: es una CLASE (java.lang.String),
        y cada texto es un OBJETO con métodos (name.length(),
        name.toUpperCase()...). Pero es una clase tan usada que Java le
        da un trato especial que ninguna otra clase tiene:
        - se puede crear con un literal ("Luis") sin new,
        - el operador + la concatena (Operators, CAPA 4),
        - switch acepta Strings (Conditionals, CAPA 10).

        Por dentro guarda sus caracteres en un arreglo, y cada posición se
        numera desde 0, igual que en los arreglos:

            "Luis"
             L u i s
             0 1 2 3      length() = 4, la última posición es 3
    */
    static boolean stringBasicsExample() {
        String name = "Luis";
        return name.length() == 4
                && name.charAt(0) == 'L'
                && name.charAt(name.length() - 1) == 's'   // el último está en length() - 1
                && "".isEmpty();                            // el String vacío existe y tiene longitud 0
    }

    /*
        =================================================================
        CAPA 2) INMUTABLE: POR QUÉ UN STRING NUNCA CAMBIA
        =================================================================

        Un String NO SE PUEDE MODIFICAR después de creado. Ningún método
        lo cambia: toUpperCase, replace, trim... todos DEVUELVEN UN STRING
        NUEVO y dejan el original intacto.

        El error clásico que esto provoca:
            String name = "luis";
            name.toUpperCase();          // crea "LUIS"... y lo tira: nadie lo guardó
            // name sigue siendo "luis"
        Lo correcto es guardar el resultado:
            name = name.toUpperCase();

        ¿Por qué Java hizo los String inmutables? Porque se comparten
        MUCHÍSIMO, y la inmutabilidad hace seguro compartirlos:
        1) SEGURIDAD: un nombre de archivo o una URL validados no pueden
           ser alterados después por otro código que tenga la referencia.
        2) CLAVES DE MAPAS: si un String usado como clave de un HashMap
           cambiara, el mapa lo perdería (ObjectContract, CAPA 8). Como
           no cambia, es la clave perfecta.
        3) HILOS: varios hilos pueden leer el mismo String sin
           sincronizar nada.
        4) EFICIENCIA: permite el String pool (CAPA 3) y que el hashCode
           se calcule una sola vez y se guarde.
        (Es la misma idea de Encapsulation, CAPA 7: lo inmutable no se
        puede romper.)

        El costo: "modificar" texto muchas veces crea muchos objetos.
        Para eso existe StringBuilder (CAPA 9).
    */
    static boolean immutabilityExample() {
        String name = "luis";
        name.toUpperCase();                   // el resultado se pierde
        boolean unchanged = name.equals("luis");

        String upper = name.toUpperCase();    // guardado en otra variable
        name = name.trim();                   // o reasignado a la misma

        return unchanged && upper.equals("LUIS") && name.equals("luis");
    }

    /*
        =================================================================
        CAPA 3) EL STRING POOL Y POR QUÉ NUNCA SE COMPARA CON ==
        =================================================================

        Como los String son inmutables, Java puede ahorrar memoria: todos
        los LITERALES iguales del programa se guardan UNA sola vez en una
        zona especial, el STRING POOL. Si escribes "Hola" en diez lugares,
        las diez variables apuntan al MISMO objeto.

        Esto provoca una trampa peligrosa con ==, que compara si dos
        variables apuntan al MISMO OBJETO (no si tienen el mismo texto):
        - Dos literales "Hola" -> mismo objeto del pool -> == da true.
        - Un "Hola" creado con new, leído de un archivo, de un teclado o
          armado con + al ejecutar -> objeto NUEVO -> == da false, aunque
          el texto sea idéntico.

        El resultado: código con == que "funciona" en las pruebas (donde
        usas literales) y falla en producción (donde el texto viene de
        fuera). Por eso la regla de tu antiguo CheatSheet:
            NUNCA == para comparar Strings. SIEMPRE equals.

        Detalles:
        - Concatenar CONSTANTES ("Ho" + "la") se resuelve al compilar, así
          que el resultado sí va al pool. Concatenar una VARIABLE se hace
          al ejecutar y crea un objeto nuevo.
        - intern() busca el texto en el pool y devuelve esa versión. Casi
          nunca lo necesitas.
        - Para comparar sin importar mayúsculas: equalsIgnoreCase.
        - Truco contra null: "literal".equals(variable) nunca lanza
          NullPointerException, aunque variable sea null.
    */
    static boolean stringPoolExample() {
        String literalA = "Hola";
        String literalB = "Hola";
        String created = new String("Hola");
        String part = "Ho";
        String builtAtRuntime = part + "la";

        boolean literalsShareObject = literalA == literalB;              // true: mismo objeto del pool
        boolean newIsDifferentObject = literalA != created;              // otro objeto, mismo texto
        boolean runtimeIsDifferentObject = literalA != builtAtRuntime;   // armado al ejecutar
        boolean constantFoldedIntoPool = literalA == "Ho" + "la";        // resuelto al compilar

        boolean equalsComparesText = literalA.equals(created) && literalA.equals(builtAtRuntime);

        String userInput = null;
        boolean nullSafe = !"admin".equals(userInput);                  // no lanza NPE

        return literalsShareObject && newIsDifferentObject && runtimeIsDifferentObject
                && constantFoldedIntoPool && equalsComparesText && nullSafe
                && created.intern() == literalA
                && "HOLA".equalsIgnoreCase(literalA);
    }

    /*
        =================================================================
        CAPA 4) BUSCAR Y EXTRAER: ÍNDICES Y SUBSTRING
        =================================================================

        | Método                     | Devuelve                                      |
        |----------------------------|-----------------------------------------------|
        | charAt(i)                  | el carácter en la posición i                  |
        | indexOf("x")               | la PRIMERA posición donde aparece, o -1       |
        | lastIndexOf("x")           | la ÚLTIMA posición, o -1                      |
        | contains("x")              | true si aparece en cualquier parte            |
        | startsWith / endsWith      | true si empieza / termina con eso             |
        | substring(inicio)          | desde inicio hasta el final                   |
        | substring(inicio, fin)     | desde inicio HASTA fin, SIN incluir fin       |

        -1 significa "no está". Por qué -1 y no otro número: es el único
        valor que nunca puede ser una posición válida.

        substring(inicio, fin) INCLUYE inicio y EXCLUYE fin. Por qué ese
        diseño, que confunde al principio:
        - fin - inicio = cuántos caracteres obtienes (substring(2, 6) da 4).
        - substring(0, n) da los primeros n caracteres.
        - Partes consecutivas encajan sin repetir: substring(0, 3) +
          substring(3) es el texto completo.
        Es la misma convención de los ciclos for (desde 0, menor que
        length): Loops, CAPA 5.

        Posiciones fuera de rango lanzan StringIndexOutOfBoundsException.
    */
    static String domainOf(String email) {
        int at = email.indexOf('@');
        if (at == -1) {
            throw new IllegalArgumentException("Correo sin @: " + email);
        }
        return email.substring(at + 1);
    }

    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');     // el ÚLTIMO punto: "reporte.final.pdf" -> pdf
        return dot == -1 ? "" : fileName.substring(dot + 1);
    }

    static boolean searchExample() {
        String padded = "  Luis  ";
        return padded.substring(2, 6).equals("Luis")               // de tu Base.java
                && "Hola Mundo".indexOf("Mundo") == 5
                && "Hola Mundo".indexOf("xyz") == -1
                && "Hola Mundo".contains("la M")
                && domainOf("ana@empresa.com").equals("empresa.com")
                && extensionOf("reporte.final.pdf").equals("pdf")
                && extensionOf("LEEME").isEmpty()
                && ("Java".substring(0, 2) + "Java".substring(2)).equals("Java");
    }

    /*
        =================================================================
        CAPA 5) TRANSFORMAR: MAYÚSCULAS, ESPACIOS, REEMPLAZOS
        =================================================================

        MAYÚSCULAS Y MINÚSCULAS: toUpperCase() / toLowerCase()
        Trampa poco conocida: usan el IDIOMA de la computadora donde
        corre el programa. En turco, la I mayúscula en minúscula es "ı"
        (sin punto). Un programa que hace "TITLE".toLowerCase() para
        comparar comandos falla en una computadora configurada en turco.
        Para texto que NO es para humanos (comandos, claves, códigos),
        usa toLowerCase(Locale.ROOT): mismo resultado en todo el mundo.

        ESPACIOS:
        - trim(): quita espacios y caracteres de control del inicio y del
          final (solo los "viejos", códigos 0 a 32).
        - strip() (Java 11): igual, pero entiende TODOS los espacios de
          Unicode. Prefiérelo.
        - isEmpty(): true solo si la longitud es 0.
        - isBlank() (Java 11): true si está vacío O solo tiene espacios.
          Es lo que normalmente quieres para validar entradas.

        REEMPLAZOS:
        - replace(a, b): reemplaza TEXTO literal. Todas las apariciones.
        - replaceAll(a, b): el primer argumento es una EXPRESIÓN REGULAR
          (v8_apis/part1_text_and_time/RegularExpressions). En regex, el punto significa
          "cualquier carácter", así que replaceAll(".", "-") reemplaza
          TODO. Si no necesitas regex, usa replace.
    */
    static boolean transformExample() {
        Locale turkish = Locale.forLanguageTag("tr");
        boolean turkishTrap = "TITLE".toLowerCase(turkish).equals("tıtle");   // la i sin punto
        boolean rootIsSafe = "TITLE".toLowerCase(Locale.ROOT).equals("title");

        String withUnicodeSpaces = " hola ";            // "espacio eme" de Unicode
        boolean trimMisses = withUnicodeSpaces.trim().length() == 6;
        boolean stripWorks = withUnicodeSpaces.strip().equals("hola");

        boolean emptyVsBlank = !"   ".isEmpty() && "   ".isBlank();

        boolean replaceIsLiteral = "1.5.0".replace(".", "-").equals("1-5-0");
        boolean replaceAllIsRegex = "1.5.0".replaceAll(".", "-").equals("-----");

        return turkishTrap && rootIsSafe && trimMisses && stripWorks && emptyVsBlank
                && replaceIsLiteral && replaceAllIsRegex
                && "  Luis  ".replace("Luis", "Carlos").equals("  Carlos  ");
    }

    /*
        =================================================================
        CAPA 6) DIVIDIR Y UNIR: SPLIT, JOIN, REPEAT
        =================================================================

        split(separador) parte un texto en un arreglo de Strings. Es la
        forma más común de leer datos como "manzana,pera,uvas".

        DOS TRAMPAS de split:
        1) El separador es una EXPRESIÓN REGULAR, como en replaceAll.
           split(".") no parte por puntos: el punto significa "cualquier
           carácter", todo se vuelve separador y el resultado es un
           arreglo VACÍO. Hay que escaparlo: split("\\.").
           (Los caracteres especiales de regex: . | * + ? ( ) [ ] { } ^ $ \)
        2) Las cadenas vacías del FINAL se descartan: "a,b,,".split(",")
           da ["a", "b"], no 4 elementos. Si te importan (columnas vacías
           en un CSV), usa split(",", -1).

        Para unir, al revés:
        - String.join(", ", lista)   une con un separador entre cada uno.
        - "-".repeat(10)             repite un texto (Java 11).
    */
    static boolean splitJoinExample() {
        String[] fruits = "manzana,pera,uvas".split(",");
        String[] versionWrong = "1.5.0".split(".");      // ¡arreglo vacío!
        String[] versionRight = "1.5.0".split("\\.");
        String[] trailingDropped = "a,b,,".split(",");
        String[] trailingKept = "a,b,,".split(",", -1);

        String joined = String.join(", ", List.of("Ana", "Luis", "Mia"));
        String line = "-".repeat(5);

        return Arrays.equals(fruits, new String[] {"manzana", "pera", "uvas"})
                && versionWrong.length == 0
                && versionRight.length == 3
                && trailingDropped.length == 2
                && trailingKept.length == 4
                && joined.equals("Ana, Luis, Mia")
                && line.equals("-----");
    }

    /*
        =================================================================
        CAPA 7) COMPARAR Y ORDENAR TEXTO
        =================================================================

        equals dice si son iguales. Para ORDENAR, compareTo dice cuál va
        primero (negativo: este va antes; 0: iguales; positivo: después;
        ObjectContract, CAPA 9).

        compareTo compara carácter por carácter según su código UNICODE
        (PrimitiveTypes, CAPA 9). Eso NO es el orden alfabético que
        espera una persona:
        - Las MAYÚSCULAS van antes que TODAS las minúsculas ('Z' es 90,
          'a' es 97): "Zebra" va antes que "apple".
        - Las letras con ACENTO van después de la z ('á' es 225): "árbol"
          queda después de "avión".
        compareToIgnoreCase arregla las mayúsculas, pero no los acentos.

        Para ordenar texto PARA HUMANOS en un idioma, se usa un COLLATOR:
        conoce las reglas de ese idioma (en español, "árbol" va antes que
        "avión"). Es lo que usarías para ordenar una lista de nombres que
        verá un usuario.
    */
    static boolean compareExample() {
        boolean uppercaseFirst = "Zebra".compareTo("apple") < 0;
        boolean ignoreCaseFixesIt = "Zebra".compareToIgnoreCase("apple") > 0;

        List<String> words = new ArrayList<>(List.of("avión", "árbol", "barco"));
        List<String> byUnicode = new ArrayList<>(words);
        byUnicode.sort(null);                                           // orden de compareTo

        List<String> bySpanish = new ArrayList<>(words);
        bySpanish.sort(Collator.getInstance(Locale.forLanguageTag("es")));   // orden del idioma

        return uppercaseFirst && ignoreCaseFixesIt
                && byUnicode.equals(List.of("avión", "barco", "árbol"))     // "árbol" al final: incorrecto para humanos
                && bySpanish.equals(List.of("árbol", "avión", "barco"));
    }

    /*
        =================================================================
        CAPA 8) RECORRER UN STRING CARÁCTER POR CARÁCTER
        =================================================================

        Dos formas, las dos muy usadas en algoritmos:
            for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); }
            for (char c : text.toCharArray()) { ... }
        La primera da también la posición; la segunda es más corta, pero
        copia el texto en un arreglo nuevo.

        La clase Character tiene métodos para preguntar por un char:
            Character.isLetter(c)     Character.isDigit(c)
            Character.isWhitespace(c) Character.isUpperCase(c)
            Character.toLowerCase(c)  Character.toUpperCase(c)

        Ejemplos clásicos de tu antigua JavaDataStructuresGuide: contar vocales y
        verificar un palíndromo (se lee igual al revés, con dos punteros:
        Loops, CAPA 4).
    */
    static int countVowels(String word) {
        int count = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            if ("aeiouáéíóú".indexOf(c) != -1) {   // indexOf como "¿está en este conjunto?"
                count++;
            }
        }
        return count;
    }

    static boolean isPalindrome(String text) {
        String letters = text.toLowerCase(Locale.ROOT).replace(" ", "");
        for (int left = 0, right = letters.length() - 1; left < right; left++, right--) {
            if (letters.charAt(left) != letters.charAt(right)) {
                return false;
            }
        }
        return true;
    }

    static boolean traverseExample() {
        int digits = 0;
        for (char c : "Pedido 2026-11".toCharArray()) {
            if (Character.isDigit(c)) {
                digits++;
            }
        }
        return countVowels("documentation") == 6
                && isPalindrome("Anita lava la tina")
                && !isPalindrome("Java")
                && digits == 6;
    }

    /*
        =================================================================
        CAPA 9) STRINGBUILDER: CONSTRUIR TEXTO EFICIENTEMENTE
        =================================================================

        El problema (CAPA 2): un String no cambia, así que en
            String result = "";
            for (...) { result += piece; }
        cada vuelta crea un String NUEVO, COPIANDO todo el texto anterior
        más el pedazo nuevo. Con 10 000 vueltas de 1 carácter se copian
        en total unos 50 MILLONES de caracteres (1 + 2 + 3 + ... + 10 000).
        El trabajo crece con el CUADRADO del tamaño: el doble de vueltas,
        cuatro veces más lento.

        STRINGBUILDER es un texto MODIFICABLE: guarda los caracteres en un
        arreglo con espacio de sobra y agrega al final sin copiar nada.
        Cuando se llena, crece al doble (de vez en cuando, no en cada
        operación). Al terminar, toString() crea el String final.

        Métodos principales (casi todos devuelven el MISMO StringBuilder,
        así que se pueden encadenar):
            append(x)          agrega al final (cualquier tipo)
            insert(pos, x)     inserta en una posición
            delete(ini, fin)   borra un rango (fin excluido, como substring)
            reverse()          invierte
            setCharAt(i, c)    cambia un carácter
            length(), charAt(i), toString()

        Cuándo usarlo: al construir texto en un CICLO o en muchos pasos.
        Fuera de ciclos, + está bien: en una sola expresión como
        "Hola " + name + "!", el compilador ya lo optimiza.

        (StringBuffer es su versión antigua, sincronizada para varios
        hilos y por eso más lenta. No lo uses en código nuevo.)
    */
    static String reverseWithConcatenation(String text) {
        String reversed = "";
        for (int i = text.length() - 1; i >= 0; i--) {
            reversed += text.charAt(i);   // crea un String nuevo en cada vuelta
        }
        return reversed;
    }

    static String csvLine(List<String> fields) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(fields.get(i));
        }
        return line.toString();
    }

    static boolean stringBuilderExample() {
        StringBuilder sb = new StringBuilder("Hola Java");
        sb.insert(5, "Mundo ");            // "Hola Mundo Java"
        String afterInsert = sb.toString();
        sb.delete(5, 11);                  // "Hola Java"
        sb.reverse();                      // "avaJ aloH"

        String chained = new StringBuilder().append("Total: ").append(3).append(" artículos").toString();

        return afterInsert.equals("Hola Mundo Java")
                && sb.toString().equals("avaJ aloH")
                && chained.equals("Total: 3 artículos")
                && reverseWithConcatenation("java").equals(new StringBuilder("java").reverse().toString())
                && csvLine(List.of("Ana", "30", "GDL")).equals("Ana,30,GDL");
    }

    /*
        =================================================================
        CAPA 10) FORMATEAR: FORMAT, FORMATTED Y PRINTF
        =================================================================

        Concatenar con + para armar mensajes con números se vuelve
        ilegible. String.format usa una PLANTILLA con marcadores:

            String.format("Hola %s, tienes %d años", name, age)
            "Hola %s, tienes %d años".formatted(name, age)     // Java 15, igual
            System.out.printf("Hola %s%n", name)                // imprime directo (de tu Base.java)

        Marcadores principales:
            %s       texto (cualquier objeto, usa su toString)
            %d       entero
            %f       decimal          %.2f  con 2 decimales (redondeados)
            %,d      con separador de miles
            %05d     rellena con ceros hasta 5 dígitos
            %8s      ocupa 8 espacios, alineado a la derecha
            %-8s     ocupa 8 espacios, alineado a la izquierda
            %%       un signo % literal
            %n       salto de línea correcto para el sistema operativo

        LOCALE: el separador decimal y el de miles dependen del idioma de
        la computadora. En España, 3.5 se escribe "3,50"; en México,
        "3.50". Si el texto es para guardarse o para otra máquina (CSV,
        JSON, logs), fija el idioma: String.format(Locale.ROOT, ...).
    */
    static boolean formatExample() {
        String row = String.format(Locale.ROOT, "%-8s|%6.2f|%,d|%05d", "Teclado", 3.14159, 1234567, 42);
        String greeting = "Hola %s, tienes %d años".formatted("Ana", 30);
        String percent = String.format(Locale.ROOT, "%d%%", 15);

        String spain = String.format(Locale.forLanguageTag("es-ES"), "%.2f", 3.5);
        String mexico = String.format(Locale.forLanguageTag("es-MX"), "%.2f", 3.5);

        return row.equals("Teclado |  3.14|1,234,567|00042")
                && greeting.equals("Hola Ana, tienes 30 años")
                && percent.equals("15%")
                && spain.equals("3,50")
                && mexico.equals("3.50");
    }

    /*
        =================================================================
        CAPA 11) TEXT BLOCKS: TEXTO DE VARIAS LÍNEAS
        =================================================================

        Antes, un JSON o una consulta SQL dentro del código era una
        pesadilla de comillas escapadas y "\n" + ... Desde Java 15 existen
        los TEXT BLOCKS, entre triples comillas:

            String json = """
                    {
                      "name": "Ana"
                    }
                    """;

        Reglas:
        - Después de las """ de apertura va un salto de línea obligatorio.
        - La sangría COMÚN a todas las líneas (incluida la línea de las """
          de cierre) se elimina automáticamente. Así puedes indentar el
          bloque junto con tu código sin meter espacios en el texto.
        - Si las """ de cierre van en su propia línea, el texto termina
          con un salto de línea.
        - Las comillas dobles no necesitan escaparse.
        - Una \ al final de una línea la une con la siguiente (para
          líneas largas que no quieres partir en el resultado).
    */
    static boolean textBlockExample() {
        String json = """
                {
                  "name": "Ana"
                }
                """;

        String oneLine = """
                Esta frase es larga, \
                pero queda en una línea.""";

        return json.equals("{\n  \"name\": \"Ana\"\n}\n")
                && oneLine.equals("Esta frase es larga, pero queda en una línea.");
    }

    /*
        =================================================================
        CAPA 12) UNICODE: CUANDO UN "CARÁCTER" NO ES UN CHAR
        =================================================================

        length() cuenta unidades char (de 16 bits), no los "caracteres"
        que ve una persona. Casi siempre coinciden, pero no siempre:

        1) EMOJIS y otros símbolos fuera de los primeros 65 536 de Unicode
           ocupan DOS char (PrimitiveTypes, CAPA 9). "😀".length() es 2.
           Para contar símbolos reales: codePointCount.

        2) La MISMA letra puede escribirse de dos formas: "é" como un solo
           código (U+00E9), o como "e" seguida de un acento que se combina
           (U+0301). En pantalla se ven idénticas, pero equals dice que
           son distintas y tienen distinta longitud. Pasa con texto que
           viene de distintos sistemas (macOS suele usar la segunda forma
           en nombres de archivo). La solución es NORMALIZAR ambos textos
           antes de comparar.

        Por qué importa: validar que un nombre "tenga máximo 20
        caracteres", cortar un texto con substring a la mitad de un emoji,
        o buscar "José" y no encontrarlo aunque esté ahí.
    */
    static boolean unicodeExample() {
        String emoji = "😀";
        boolean emojiIsTwoChars = emoji.length() == 2 && emoji.codePointCount(0, emoji.length()) == 1;

        String composed = "José";        // é como un solo código
        String decomposed = "José";     // e + acento combinado
        boolean lookSameButDiffer = !composed.equals(decomposed) && composed.length() == 4 && decomposed.length() == 5;
        boolean normalizedEqual = Normalizer.normalize(decomposed, Normalizer.Form.NFC).equals(composed);

        return emojiIsTwoChars && lookSameButDiffer && normalizedEqual;
    }

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) Comparar Strings con ==. Funciona con literales y falla con
           texto que viene de fuera (CAPA 3).

        2) Llamar toUpperCase(), trim(), replace()... sin guardar el
           resultado (CAPA 2).

        3) Olvidar que substring excluye el índice final, o no revisar el
           -1 de indexOf (CAPA 4).

        4) toLowerCase() sin Locale para comparar texto técnico (CAPA 5).

        5) Usar isEmpty() para validar entradas donde "   " también es
           inválido: usa isBlank() (CAPA 5).

        6) Usar replaceAll o split con un "." u otro carácter especial sin
           escaparlo (CAPAS 5 y 6).

        7) Perder columnas vacías al final con split (CAPA 6).

        8) Ordenar texto para personas con compareTo (CAPA 7).

        9) Concatenar con += dentro de un ciclo (CAPA 9).

        10) Formatear números para guardar o enviar sin fijar el Locale
            (CAPA 10).

        11) Asumir que length() cuenta caracteres visibles (CAPA 12).

        12) Llamar métodos sobre un String null: NullPointerException.
            Usa "literal".equals(variable) o revisa null antes
            (v1_fundamentals/part3_text_and_references/ReferencesAndNull).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - String: objeto inmutable con trato especial (literales, +,
          switch). Posiciones desde 0.
        - Inmutable: los métodos devuelven un String nuevo; guarda el
          resultado. Por eso es seguro compartirlo y usarlo como clave.
        - String pool: los literales se comparten. Compara SIEMPRE con
          equals (o equalsIgnoreCase), nunca con ==.
        - indexOf devuelve -1 si no está; substring(ini, fin) excluye fin.
        - strip > trim; isBlank para validar; Locale.ROOT para texto
          técnico; replace (literal) vs replaceAll (regex).
        - split usa regex (escapa el punto) y descarta vacíos al final;
          join y repeat para unir.
        - compareTo ordena por Unicode; Collator, por idioma.
        - StringBuilder para construir texto en ciclos.
        - String.format / formatted con marcadores; fija el Locale si el
          texto no es para humanos.
        - Text blocks """ para texto de varias líneas.
        - length() cuenta char, no símbolos: emojis y acentos combinados.
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
