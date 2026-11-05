package v8_apis.part1_text_and_time;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/*
    =====================================================================
    EXPRESIONES REGULARES — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de apis/: guía 2 de 6  (part1_text_and_time)
    Anterior:  part1_text_and_time/DatesAndTimes
    Siguiente: part1_text_and_time/FormattingAndLocale

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: los métodos de String (v1_fundamentals/
    part3_text_and_references/Strings), incluidas las trampas de split y
    replaceAll (CAPAS 5 y 6 de esa guía), que aquí se explican a fondo.

    Índice:
        CAPA 1)  Qué es una expresión regular y para qué sirve
        CAPA 2)  El doble escape: regex dentro de un String de Java
        CAPA 3)  Caracteres y clases de caracteres
        CAPA 4)  Cuantificadores: cuántas veces
        CAPA 5)  Codicioso vs perezoso
        CAPA 6)  Anclas y límites; matches vs find
        CAPA 7)  Grupos: capturar partes
        CAPA 8)  Pattern y Matcher: compilar una vez, usar muchas
        CAPA 9)  Extraer datos: de una línea de log a un record
        CAPA 10) Reemplazar: referencias a grupos y la trampa del $
        CAPA 11) Opciones (flags): mayúsculas, multilínea, Unicode
        CAPA 12) Lookahead: condiciones sin consumir
        CAPA 13) Rendimiento y el backtracking catastrófico (ReDoS)
        CAPA 14) Cuándo NO usar una regex
        CAPA 15) Errores comunes
        RESUMEN FINAL
*/
public class RegularExpressions {

    public static void main(String[] args) {
        check(whatIsItExample(), "CAPA 1");
        check(escapingExample(), "CAPA 2");
        check(characterClassesExample(), "CAPA 3");
        check(quantifiersExample(), "CAPA 4");
        check(greedyVsLazyExample(), "CAPA 5");
        check(anchorsExample(), "CAPA 6");
        check(groupsExample(), "CAPA 7");
        check(patternMatcherExample(), "CAPA 8");
        check(extractExample(), "CAPA 9");
        check(replaceExample(), "CAPA 10");
        check(flagsExample(), "CAPA 11");
        check(lookaheadExample(), "CAPA 12");
        check(safePatternExample(), "CAPA 13");
        System.out.println("RegularExpressions: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UNA EXPRESIÓN REGULAR Y PARA QUÉ SIRVE
        =================================================================

        Una EXPRESIÓN REGULAR (regex) es un pequeño lenguaje para
        describir un PATRÓN de texto. En lugar de buscar un texto exacto
        ("hola"), describes una forma: "tres letras mayúsculas, un guion
        y tres dígitos" se escribe [A-Z]{3}-\d{3}.

        Cuatro usos:
        1) VALIDAR: ¿este texto tiene la forma correcta? (un código postal,
           un SKU, un formato de placa)
        2) BUSCAR: ¿dónde aparece el patrón dentro de un texto más grande?
        3) EXTRAER: sacar partes (la fecha y el nivel de una línea de log)
        4) REEMPLAZAR o DIVIDIR: cambiar o partir donde aparece el patrón

        En Java están en dos lugares:
        - métodos de String: matches, replaceAll, replaceFirst, split
          (cómodos para usos sueltos);
        - java.util.regex: Pattern y Matcher (para todo lo demás, CAPA 8).

        La sintaxis es casi la misma en JavaScript, Python, grep, editores
        y bases de datos: lo que aprendes aquí sirve en todos lados.
    */
    static boolean whatIsItExample() {
        String sku = "[A-Z]{3}-\\d{3}";             // tres mayúsculas, guion, tres dígitos
        return "ABC-123".matches(sku) && !"ABC-12".matches(sku) && !"abc-123".matches(sku);
    }

    /*
        =================================================================
        CAPA 2) EL DOBLE ESCAPE: REGEX DENTRO DE UN STRING DE JAVA
        =================================================================

        En regex, la barra invertida \ da significado especial: \d es "un
        dígito". Pero en un String de Java, \ TAMBIÉN es especial (\n es
        salto de línea; v1_fundamentals/part1_first_steps/PrimitiveTypes).
        El texto pasa por DOS intérpretes: primero el compilador de Java,
        luego el motor de regex.

        Para que al motor le llegue \d, en Java escribes "\\d": el
        compilador convierte \\ en una sola \.

            regex que quieres     en código Java
            \d                    "\\d"
            \.                    "\\."      (un punto literal)
            \\                    "\\\\"     (UNA barra invertida literal)

        Si escribes "\d", el compilador ni siquiera llega a la regex:
            // ERROR: illegal escape character

        Los metacaracteres que tienen significado especial en regex son:
            .  ^  $  *  +  ?  (  )  [  ]  {  }  |  \
        Para usarlos LITERALMENTE, escápalos con \ (en Java, "\\.", "\\+").

        Cuando el texto a buscar viene de AFUERA (lo escribió un usuario),
        no lo escapes a mano: Pattern.quote(texto) lo convierte en un
        patrón literal. Sin eso, un usuario que busca "1+1" estaría
        escribiendo una regex (y uno malicioso podría escribir una que
        cuelgue tu servidor, CAPA 13).
    */
    static boolean escapingExample() {
        boolean dotIsAnyCharacter = "1x5".matches("1.5");          // . = cualquier carácter
        boolean escapedDot = !"1x5".matches("1\\.5") && "1.5".matches("1\\.5");
        String userSearch = "1+1=2";
        boolean quoted = userSearch.matches(Pattern.quote(userSearch))     // literal, sin significado especial
                && !userSearch.matches(userSearch);                         // como regex, + significa "una o más"

        return dotIsAnyCharacter && escapedDot && quoted;
    }

    /*
        =================================================================
        CAPA 3) CARACTERES Y CLASES DE CARACTERES
        =================================================================

        Un carácter normal (a, 7, -) se representa a sí mismo. Para "uno de
        varios", se usan CLASES:

        | Regex       | Significa                                          |
        |-------------|----------------------------------------------------|
        | .           | cualquier carácter (menos salto de línea)          |
        | [abc]       | a, b o c                                           |
        | [a-z]       | un rango: cualquier minúscula de la a a la z       |
        | [a-zA-Z0-9] | varios rangos juntos                               |
        | [^0-9]      | cualquiera que NO sea un dígito (^ dentro de [])   |
        | \d          | un dígito: [0-9]                                   |
        | \w          | carácter de "palabra": [a-zA-Z0-9_]                |
        | \s          | espacio en blanco: espacio, tab, salto de línea    |
        | \D \W \S    | lo contrario de \d \w \s                           |
        | a|b         | a o b (alternativa; con grupos en la CAPA 7)       |

        Ojo con \w: por defecto es solo ASCII, así que NO incluye ñ ni
        letras con acento. "señor".matches("\\w+") es false. Para texto en
        español, activa Unicode (CAPA 11) o usa \p{L} ("cualquier letra
        de cualquier idioma").

        Dentro de [ ], casi todos los metacaracteres pierden su significado:
        [.+] es "un punto o un más". Las excepciones son ^ al inicio,
        - entre dos caracteres, ], \, y en Java también [ (anidar clases)
        y && (intersección: [a-z&&[^x]]).
    */
    static boolean characterClassesExample() {
        boolean vowel = "e".matches("[aeiou]") && !"x".matches("[aeiou]");
        boolean notDigit = "x".matches("[^0-9]") && !"7".matches("[^0-9]");
        boolean wordIsAscii = "hola_1".matches("\\w+") && !"señor".matches("\\w+");
        boolean anyLetter = "señor".matches("\\p{L}+");          // letras de cualquier idioma
        boolean literalInsideBrackets = "+".matches("[.+]");
        return vowel && notDigit && wordIsAscii && anyLetter && literalInsideBrackets;
    }

    /*
        =================================================================
        CAPA 4) CUANTIFICADORES: CUÁNTAS VECES
        =================================================================

        Van DESPUÉS de lo que repiten (un carácter, una clase o un grupo):

        | Cuantificador | Significa            | Ejemplo        | Acepta                 |
        |---------------|----------------------|----------------|------------------------|
        | *             | 0 o más              | ab*c           | ac, abc, abbbc         |
        | +             | 1 o más              | ab+c           | abc, abbbc (no ac)     |
        | ?             | 0 o 1 (opcional)     | colou?r        | color, colour          |
        | {n}           | exactamente n        | \d{5}          | un código postal       |
        | {n,}          | n o más              | \w{8,}         | 8 caracteres o más     |
        | {n,m}         | entre n y m          | \d{2,4}        | 2, 3 o 4 dígitos       |

        Solo afectan a lo INMEDIATAMENTE anterior: ab+ es "a seguida de
        una o más b", no "ab repetido". Para repetir varios caracteres,
        agrúpalos: (ab)+ (CAPA 7).
    */
    static boolean quantifiersExample() {
        boolean optional = "color".matches("colou?r") && "colour".matches("colou?r");
        boolean zipCode = "44100".matches("\\d{5}") && !"4410".matches("\\d{5}");
        boolean phone = "33-1234-5678".matches("\\d{2,3}-\\d{4}-\\d{4}");
        boolean onlyLastLetter = "abbb".matches("ab+") && !"abab".matches("ab+") && "abab".matches("(ab)+");
        return optional && zipCode && phone && onlyLastLetter;
    }

    /*
        =================================================================
        CAPA 5) CODICIOSO VS PEREZOSO
        =================================================================

        Por defecto los cuantificadores son CODICIOSOS (greedy): toman
        TODO lo que pueden y solo "devuelven" caracteres si el resto del
        patrón no encaja.

            texto:  <b>a</b> y <b>b</b>
            <b>.*</b>    ->  "<b>a</b> y <b>b</b>"     (.* se comió hasta el ÚLTIMO </b>)

        Agregando ? después del cuantificador se vuelve PEREZOSO (lazy):
        toma lo MENOS posible.

            <b>.*?</b>   ->  "<b>a</b>"                (se detiene en el PRIMER </b>)

        Esta diferencia causa muchos errores al extraer cosas entre
        delimitadores (comillas, etiquetas, corchetes). Otra opción, a
        menudo más clara y rápida: una clase negada, "todo lo que no sea
        el delimitador": <b>[^<]*</b>.
    */
    static boolean greedyVsLazyExample() {
        String html = "<b>a</b> y <b>b</b>";

        Matcher greedy = Pattern.compile("<b>.*</b>").matcher(html);
        Matcher lazy = Pattern.compile("<b>.*?</b>").matcher(html);
        Matcher negated = Pattern.compile("<b>[^<]*</b>").matcher(html);

        return greedy.find() && greedy.group().equals(html)
                && lazy.find() && lazy.group().equals("<b>a</b>")
                && negated.find() && negated.group().equals("<b>a</b>");
    }

    /*
        =================================================================
        CAPA 6) ANCLAS Y LÍMITES; MATCHES VS FIND
        =================================================================

        Las anclas no consumen caracteres: marcan POSICIONES.
            ^     inicio del texto (o de cada línea, con MULTILINE; CAPA 11)
            $     final del texto
            \b    límite de palabra: entre un \w y algo que no lo es

        Y aquí está la diferencia más importante de la API:
        - matches(): TODO el texto debe encajar con el patrón (como si
          tuviera ^ y $ implícitos). Es para VALIDAR.
        - find(): busca el patrón en CUALQUIER parte del texto. Es para
          BUSCAR. Se puede llamar varias veces para encontrar el siguiente.
        - lookingAt(): debe encajar desde el INICIO, pero no hasta el final.

            "abc123".matches("\\d+")          false: el texto completo no son dígitos
            matcher("\\d+", "abc123").find()  true: hay dígitos dentro

        String.matches es siempre la versión "todo el texto".

        \b sirve para buscar palabras completas: \bcat\b encuentra "cat"
        en "the cat", pero no en "concatenar".
    */
    static boolean anchorsExample() {
        boolean wholeText = !"abc123".matches("\\d+");
        boolean somewhere = Pattern.compile("\\d+").matcher("abc123").find();
        boolean fromStart = Pattern.compile("abc").matcher("abc123").lookingAt();
        boolean wholeWord = Pattern.compile("\\bcat\\b").matcher("the cat sat").find()
                && !Pattern.compile("\\bcat\\b").matcher("concatenar").find();
        return wholeText && somewhere && fromStart && wholeWord;
    }

    /*
        =================================================================
        CAPA 7) GRUPOS: CAPTURAR PARTES
        =================================================================

        Los paréntesis hacen dos cosas:
        1) AGRUPAR, para que un cuantificador o una alternativa se aplique
           a varios caracteres: (ab)+, (jpg|png|gif).
        2) CAPTURAR: recordar qué parte del texto encajó, para leerla
           después.

        Los grupos se numeran por su paréntesis de apertura, de izquierda a
        derecha, empezando en 1. El grupo 0 es TODO lo que encajó.

            (\d+)-(\d+)   sobre "10-20"
            group(0) = "10-20"    group(1) = "10"    group(2) = "20"

        Dos variantes:
        - GRUPOS CON NOMBRE: (?<year>\d{4}). Se leen con group("year"). Más
          claros que contar paréntesis, y no se rompen si agregas otro
          grupo antes.
        - GRUPOS SIN CAPTURA: (?:jpg|png). Agrupan pero no capturan: no
          cambian la numeración ni guardan nada.

        Si un grupo es opcional y no participó, group(...) devuelve null.
    */
    static boolean groupsExample() {
        Matcher range = Pattern.compile("(\\d+)-(\\d+)").matcher("10-20");
        boolean numbered = range.matches() && range.groupCount() == 2
                && range.group(0).equals("10-20") && range.group(2).equals("20");

        Matcher date = Pattern.compile("(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})").matcher("2024-03-15");
        boolean named = date.matches() && date.group("month").equals("03");

        Matcher file = Pattern.compile("(\\w+)\\.(?:jpg|png)").matcher("foto.png");
        boolean nonCapturing = file.matches() && file.groupCount() == 1 && file.group(1).equals("foto");

        Matcher optional = Pattern.compile("(\\d+)(px)?").matcher("12");
        boolean missingGroupIsNull = optional.matches() && optional.group(2) == null;

        return numbered && named && nonCapturing && missingGroupIsNull;
    }

    /*
        =================================================================
        CAPA 8) PATTERN Y MATCHER: COMPILAR UNA VEZ, USAR MUCHAS
        =================================================================

        Antes de usarse, una regex se COMPILA: se analiza y se convierte en
        una estructura interna. Es trabajo. Los métodos de String
        (matches, replaceAll, split) compilan la regex EN CADA LLAMADA;
        dentro de un ciclo de miles de vueltas, eso se nota.

        La forma profesional:
            private static final Pattern SKU = Pattern.compile("[A-Z]{3}-\\d{3}");
            ...
            SKU.matcher(texto).matches()

        - Pattern: la regex compilada. INMUTABLE y segura con hilos:
          guárdala como constante y compártela.
        - Matcher: el "cursor" que aplica un Pattern a UN texto. Guarda
          estado (dónde va, qué grupos encontró): NO es seguro con hilos.
          Se crea uno por texto (es barato).

        Recorrer TODAS las coincidencias:
            Matcher m = PATTERN.matcher(texto);
            while (m.find()) { ... m.group() ... }
        o, desde Java 9, como stream:
            PATTERN.matcher(texto).results().map(MatchResult::group).toList()

        Una regex mal escrita no se detecta al compilar Java, sino al
        ejecutar Pattern.compile: PatternSyntaxException ("Unclosed group
        near index 4"). Otra razón para que sean constantes static final:
        el error aparece al cargar la clase, no a la mitad de una petición.
    */
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    static boolean patternMatcherExample() {
        List<String> withLoop = new ArrayList<>();
        Matcher matcher = NUMBER.matcher("a1 b22 c333");
        while (matcher.find()) {
            withLoop.add(matcher.group());
        }
        List<String> withStream = NUMBER.matcher("a1 b22 c333").results().map(MatchResult::group).toList();

        boolean syntaxError;
        try {
            Pattern.compile("(abc");
            syntaxError = false;
        } catch (PatternSyntaxException e) {
            syntaxError = e.getDescription().equals("Unclosed group");
        }

        return withLoop.equals(List.of("1", "22", "333")) && withStream.equals(withLoop) && syntaxError;
    }

    /*
        =================================================================
        CAPA 9) EXTRAER DATOS: DE UNA LÍNEA DE LOG A UN RECORD
        =================================================================

        Un uso real: convertir líneas de texto con formato fijo en objetos.
        Una línea de log:
            2024-03-15 10:42:07 [ERROR] PaymentService - Tarjeta rechazada

        Con grupos con nombre, cada parte va a un campo. Si la línea no
        tiene el formato, devolvemos Optional vacío (v7_functional/
        part2_streams/OptionalUsage): una línea mal formada es algo normal
        en un log, no un error.

        Fíjate en cómo se lee la regex: cada grupo está separado por el
        texto literal que hay entre los campos (espacios, corchetes, " - ").
    */
    record LogEntry(String date, String time, String level, String source, String message) { }

    private static final Pattern LOG_LINE = Pattern.compile(
            "(?<date>\\d{4}-\\d{2}-\\d{2}) (?<time>\\d{2}:\\d{2}:\\d{2}) "
            + "\\[(?<level>[A-Z]+)] (?<source>\\w+) - (?<message>.*)");

    static Optional<LogEntry> parseLog(String line) {
        Matcher m = LOG_LINE.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new LogEntry(m.group("date"), m.group("time"), m.group("level"),
                m.group("source"), m.group("message")));
    }

    static boolean extractExample() {
        Optional<LogEntry> entry = parseLog("2024-03-15 10:42:07 [ERROR] PaymentService - Tarjeta rechazada");
        return entry.isPresent()
                && entry.get().level().equals("ERROR")
                && entry.get().source().equals("PaymentService")
                && entry.get().message().equals("Tarjeta rechazada")
                && parseLog("línea basura").isEmpty();
    }

    /*
        =================================================================
        CAPA 10) REEMPLAZAR: REFERENCIAS A GRUPOS Y LA TRAMPA DEL $
        =================================================================

        replaceAll(regex, reemplazo) cambia cada coincidencia. En el TEXTO
        de reemplazo se puede usar lo que capturaron los grupos:
            $1, $2...       por número
            ${nombre}       por nombre

            "2024-03-15".replaceAll("(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})", "${d}/${m}/${y}")
            ->  "15/03/2024"

        LA TRAMPA: por eso $ y \ son especiales también en el REEMPLAZO.
        Si el reemplazo es un texto con un $ (un precio, una contraseña),
        falla o hace algo raro:
            "precio".replaceAll("precio", "$10")
            // IndexOutOfBoundsException: No group 1   ($1 = "el grupo 1")
        Cuando el reemplazo viene de datos, envuélvelo con
        Matcher.quoteReplacement(texto). (Y si no necesitas regex en
        absoluto, String.replace es literal en ambos lados.)

        REEMPLAZAR CON UNA FUNCIÓN (Java 9): matcher.replaceAll(m -> ...)
        calcula el reemplazo de cada coincidencia con código. Ejemplo:
        poner en mayúscula la primera letra de cada palabra.
    */
    private static final Pattern ISO_DATE = Pattern.compile("(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})");
    private static final Pattern ALL_BUT_LAST_FOUR = Pattern.compile("\\d(?=\\d{4})");   // CAPA 12
    private static final Pattern WORD_START = Pattern.compile("\\b\\w");

    static boolean replaceExample() {
        String mexican = ISO_DATE.matcher("Entrega: 2024-03-15").replaceAll("${d}/${m}/${y}");

        boolean dollarTrap;
        try {
            "precio".replaceAll("precio", "$10");
            dollarTrap = false;
        } catch (IndexOutOfBoundsException e) {
            dollarTrap = e.getMessage().equals("No group 1");
        }
        String safe = "precio".replaceAll("precio", Matcher.quoteReplacement("$10"));

        String masked = ALL_BUT_LAST_FOUR.matcher("4111111111111111").replaceAll("*");
        String titled = WORD_START.matcher("hola mundo java").replaceAll(m -> m.group().toUpperCase());

        return mexican.equals("Entrega: 15/03/2024") && dollarTrap && safe.equals("$10")
                && masked.equals("************1111") && titled.equals("Hola Mundo Java");
    }

    /*
        =================================================================
        CAPA 11) OPCIONES (FLAGS): MAYÚSCULAS, MULTILÍNEA, UNICODE
        =================================================================

        Pattern.compile(regex, flags) cambia cómo se interpreta. También se
        pueden activar DENTRO de la regex con (?letra):

        | Flag                       | En línea | Efecto                                         |
        |----------------------------|----------|------------------------------------------------|
        | CASE_INSENSITIVE           | (?i)     | ignora mayúsculas/minúsculas                   |
        | MULTILINE                  | (?m)     | ^ y $ marcan inicio y fin de CADA línea        |
        | DOTALL                     | (?s)     | . también acepta saltos de línea               |
        | UNICODE_CHARACTER_CLASS    | (?U)     | \w, \d, \b reconocen ñ, acentos, otros idiomas |
        | COMMENTS                   | (?x)     | ignora espacios y permite # comentarios        |

        Se combinan con |: Pattern.compile(regex, CASE_INSENSITIVE | MULTILINE).

        COMMENTS sirve para que una regex larga se pueda LEER: la escribes
        en varias líneas, con espacios y comentarios. Con un text block
        (Java 15) queda muy claro.
    */
    private static final Pattern MEXICAN_PLATE = Pattern.compile("""
            ^ [A-Z]{3}       # tres letras
            - \\d{2,3}       # dos o tres dígitos
            - [A-Z0-9]{1,2}  # una o dos letras/dígitos
            $""", Pattern.COMMENTS);

    static boolean flagsExample() {
        boolean ignoreCase = Pattern.compile("java", Pattern.CASE_INSENSITIVE).matcher("Me gusta JAVA").find()
                && "JAVA".matches("(?i)java");
        List<String> firstWordOfEachLine = Pattern.compile("^\\w+", Pattern.MULTILINE)
                .matcher("uno dos\ntres cuatro").results().map(MatchResult::group).toList();
        boolean unicode = !"señor".matches("\\w+") && "señor".matches("(?U)\\w+");
        boolean readable = MEXICAN_PLATE.matcher("ABC-12-3").matches() && !MEXICAN_PLATE.matcher("AB-12-3").matches();

        return ignoreCase && firstWordOfEachLine.equals(List.of("uno", "tres")) && unicode && readable;
    }

    /*
        =================================================================
        CAPA 12) LOOKAHEAD: CONDICIONES SIN CONSUMIR
        =================================================================

        Un LOOKAHEAD revisa lo que viene DESPUÉS sin "comérselo" (como las
        anclas, no consume caracteres):
            X(?=Y)     X, solo si después viene Y
            X(?!Y)     X, solo si después NO viene Y
        (Y los lookbehind, hacia atrás: (?<=Y)X y (?<!Y)X.)

        El enmascarado de tarjeta de la CAPA 10 usa uno: \d(?=\d{4}) es
        "un dígito que todavía tenga al menos 4 dígitos después". Los
        últimos 4 no cumplen, así que no se reemplazan.

        Otro uso típico: varias condiciones a la vez sobre el mismo texto,
        como una contraseña que debe tener un dígito Y una mayúscula:
            ^(?=.*\d)(?=.*[A-Z]).{8,}$
        Funciona, pero el usuario solo recibe "contraseña inválida". En
        validaciones de formularios, varias reglas SEPARADAS dan mensajes
        claros ("falta un número") y se leen mejor (v7_functional/
        part1_lambdas/FunctionalInterfaces, CAPA 9: Validator).
    */
    private static final Pattern STRONG_PASSWORD = Pattern.compile("^(?=.*\\d)(?=.*[A-Z]).{8,}$");
    private static final Pattern PRICE_IN_PESOS = Pattern.compile("(?<=\\$)\\d+");   // el número, sin el $

    static boolean lookaheadExample() {
        boolean password = STRONG_PASSWORD.matcher("Segura123").matches()
                && !STRONG_PASSWORD.matcher("segura123").matches()       // sin mayúscula
                && !STRONG_PASSWORD.matcher("Segura").matches();         // sin dígito y corta
        Matcher price = PRICE_IN_PESOS.matcher("Total: $350 MXN");
        boolean lookbehind = price.find() && price.group().equals("350");
        return password && lookbehind;
    }

    /*
        =================================================================
        CAPA 13) RENDIMIENTO Y EL BACKTRACKING CATASTRÓFICO (REDOS)
        =================================================================

        El motor de regex de Java prueba caminos, y cuando uno falla
        RETROCEDE (backtracking) y prueba otro. Normalmente es rapidísimo.
        Pero algunos patrones tienen MUCHÍSIMAS formas de repartir el mismo
        texto entre sus partes, y cuando al final no encajan, el motor
        prueba TODAS antes de rendirse.

        Ejemplo medido en JDK 26 con (.*a){12} sobre "aaa...a!" (no encaja
        por el ! final):
            20 caracteres  ->      48 ms
            25 caracteres  ->     301 ms
            30 caracteres  ->   3,134 ms
            35 caracteres  ->  28,882 ms   (casi 30 segundos)
        El mismo resultado con a{12,} tarda microsegundos.

        Si la regex procesa texto que manda un usuario, un atacante puede
        mandar justo el texto que la hace tardar y dejar tu servidor sin
        hilos libres. Se llama ReDoS (Regular expression Denial of
        Service) y ha tumbado servicios reales.

        Nota: el ejemplo clásico de los libros, (a+)+, YA NO es lento desde
        Java 9: el motor detecta ese caso. Pero no los detecta todos, como
        muestra el ejemplo de arriba.

        Cómo protegerte:
        - Evita cuantificadores anidados o que se solapan sobre lo mismo:
          (.*a){n}, (\w+\s?)+, (x|x?)+.
        - Prefiere clases negadas y específicas ([^,]* en lugar de .*).
        - LIMITA el largo del texto ANTES de aplicar la regex.
        - Nunca uses texto del usuario COMO regex sin Pattern.quote
          (CAPA 2).
    */
    private static final Pattern AT_LEAST_12_A = Pattern.compile("a{12,}");   // la versión segura

    static boolean safePatternExample() {
        String attack = "a".repeat(35) + "!";
        long start = System.nanoTime();
        boolean matches = AT_LEAST_12_A.matcher(attack).matches();
        long millis = (System.nanoTime() - start) / 1_000_000;
        return !matches && millis < 1_000;          // mismo texto que tarda ~30 s con (.*a){12}
    }

    /*
        =================================================================
        CAPA 14) CUÁNDO NO USAR UNA REGEX
        =================================================================

        "Algunas personas, ante un problema, piensan: 'ya sé, usaré
        expresiones regulares'. Ahora tienen dos problemas." (Jamie
        Zawinski). Una regex es difícil de leer y de mantener. Úsala cuando
        de verdad describe un PATRÓN; si no:

        - Texto exacto: contains, startsWith, endsWith, indexOf, equals.
          Más claros y más rápidos.
        - Partir por un carácter fijo: split también usa regex, pero para
          algo como "," está bien; para separadores con metacaracteres,
          Pattern.quote o escapar (Strings, CAPA 6).
        - Formatos con su propia gramática: HTML, XML, JSON, CSV con
          comillas. Usa un parser (Jackson para JSON: part2_io_and_network/
          Serialization). Una regex para HTML siempre falla con algún caso.
        - Fechas y números: DateTimeFormatter (DatesAndTimes, CAPA 11),
          Integer.parseInt, NumberFormat (FormattingAndLocale).
        - Correos electrónicos: la regla oficial es enorme. Para un
          formulario basta algo simple como [^@\s]+@[^@\s]+\.[^@\s]+; la
          verdadera validación es mandar un correo de confirmación.
    */

    /*
        =================================================================
        CAPA 15) ERRORES COMUNES
        =================================================================

        1) "\d" en lugar de "\\d": no compila (CAPA 2).

        2) Olvidar que ., +, ?, $, (, [ son especiales; split(".") y
           replaceAll(".") (CAPA 2).

        3) Usar el texto de un usuario como regex sin Pattern.quote
           (CAPAS 2 y 13).

        4) Esperar que \w acepte ñ o acentos sin (?U) (CAPAS 3 y 11).

        5) ab+ creyendo que repite "ab" (CAPA 4).

        6) .* codicioso al extraer entre delimitadores (CAPA 5).

        7) Confundir matches (todo el texto) con find (en cualquier parte)
           (CAPA 6).

        8) Llamar group() sin haber llamado matches() o find() antes
           (lanza IllegalStateException), o no revisar si devolvieron
           true (CAPA 8).

        9) String.matches/replaceAll dentro de un ciclo grande: compila
           cada vez; usa un Pattern constante (CAPA 8).

        10) Un $ o \ dentro del texto de reemplazo sin quoteReplacement
            (CAPA 10).

        11) Patrones con backtracking catastrófico sobre texto de usuarios
            (CAPA 13).

        12) Regex para HTML, JSON o fechas (CAPA 14).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Una regex describe un patrón: validar, buscar, extraer, reemplazar.
        - En Java, doble escape: "\\d". Literales externos: Pattern.quote.
        - Clases: [abc], [a-z], [^...], \d \w \s (ASCII sin (?U)), \p{L}.
        - Cuantificadores: * + ? {n} {n,} {n,m}; codiciosos por defecto,
          perezosos con ?.
        - Anclas ^ $ \b. matches = todo el texto; find = en cualquier parte.
        - Grupos: (…) numerados, (?<nombre>…), (?:…) sin captura.
        - Pattern constante (inmutable, seguro con hilos); Matcher por texto.
        - Reemplazo con $1 y ${nombre}; quoteReplacement para datos;
          replaceAll(m -> ...) con función.
        - Flags: (?i), (?m), (?s), (?U), (?x) con text blocks.
        - Lookahead (?=…) / lookbehind (?<=…) no consumen.
        - Cuidado con el backtracking catastrófico; limita la entrada.
        - Si no es un patrón, no uses regex.
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
