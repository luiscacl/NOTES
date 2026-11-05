package v8_apis.part1_text_and_time;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ListFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Currency;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/*
    =====================================================================
    FORMATO E IDIOMAS: LOCALE, NUMBERFORMAT Y MENSAJES TRADUCIDOS
    =====================================================================

    Ruta de apis/: guía 3 de 6  (part1_text_and_time)
    Anterior:  part1_text_and_time/RegularExpressions
    Siguiente: part2_io_and_network/FilesAndIO

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: String.format y el Locale en printf
    (v1_fundamentals/part3_text_and_references/Strings, CAPA 10),
    BigDecimal y el redondeo del dinero (v1_fundamentals/part4_data_and_
    input/Numbers, CAPAS 5 a 8), y el formato de fechas
    (DatesAndTimes, CAPA 11).

    Nota: algunos formatos usan espacios ESPECIALES que no se cortan al
    final de un renglón (U+00A0 y U+202F). En el código aparecen como
      y   para que se vean.

    Índice:
        CAPA 1)  Internacionalizar: el mismo dato, escrito distinto
        CAPA 2)  Locale: idioma + región
        CAPA 3)  El Locale por defecto: el peligro escondido
        CAPA 4)  NumberFormat: números, enteros y porcentajes
        CAPA 5)  Dinero: getCurrencyInstance y Currency
        CAPA 6)  Números compactos: 1.2M
        CAPA 7)  Leer números que escribió una persona
        CAPA 8)  DecimalFormat: patrones propios
        CAPA 9)  NumberFormat NO es seguro con hilos
        CAPA 10) Mensajes traducidos: ResourceBundle y MessageFormat
        CAPA 11) Listas: "pan, leche y huevos" (ListFormat)
        CAPA 12) Las reglas profesionales
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class FormattingAndLocale {

    public static void main(String[] args) {
        check(sameNumberExample(), "CAPA 1");
        check(localeExample(), "CAPA 2");
        check(explicitLocaleExample(), "CAPA 3");
        check(numberFormatExample(), "CAPA 4");
        check(currencyExample(), "CAPA 5");
        check(compactExample(), "CAPA 6");
        check(parsingExample(), "CAPA 7");
        check(decimalFormatExample(), "CAPA 8");
        check(messagesExample(), "CAPA 10");
        check(listFormatExample(), "CAPA 11");
        System.out.println("FormattingAndLocale: todos los ejemplos se comportan como se explica.");
    }

    static final Locale MEXICO = Locale.forLanguageTag("es-MX");
    static final Locale SPAIN = Locale.forLanguageTag("es-ES");
    static final Locale FRANCE = Locale.forLanguageTag("fr-FR");

    /*
        =================================================================
        CAPA 1) INTERNACIONALIZAR: EL MISMO DATO, ESCRITO DISTINTO
        =================================================================

        El número 1234567.891 se ESCRIBE distinto según dónde estés:

            Estados Unidos, México   1,234,567.891     (coma para miles, punto decimal)
            España, Alemania         1.234.567,891     (al revés)
            Francia                  1 234 567,891     (espacio fino para miles)

        Lo mismo con el dinero ($1,234.50 vs 1.234,50 €), las fechas
        (03/04 es 3 de abril o 4 de marzo), los textos de la interfaz, el
        orden alfabético (Sorting, CAPA 2) y hasta las mayúsculas (la i
        turca; Strings, CAPA 5).

        Dos palabras que verás en todos lados:
        - INTERNACIONALIZACIÓN (i18n: i + 18 letras + n): preparar el
          programa para que PUEDA mostrarse en cualquier idioma y región,
          sin cambiar el código.
        - LOCALIZACIÓN (l10n): adaptarlo a UNO en concreto (traducir los
          textos, elegir formatos).

        Si un sistema escribe "1.234" para un mexicano, este entiende "uno
        punto dos": un error de mil veces en un precio. Por eso importa.
    */
    static boolean sameNumberExample() {
        double amount = 1234567.891;
        return NumberFormat.getNumberInstance(Locale.US).format(amount).equals("1,234,567.891")
                && NumberFormat.getNumberInstance(MEXICO).format(amount).equals("1,234,567.891")
                && NumberFormat.getNumberInstance(SPAIN).format(amount).equals("1.234.567,891")
                && NumberFormat.getNumberInstance(FRANCE).format(amount).equals("1 234 567,891");
    }

    /*
        =================================================================
        CAPA 2) LOCALE: IDIOMA + REGIÓN
        =================================================================

        Un Locale identifica un IDIOMA y, opcionalmente, una REGIÓN:
            es        español (genérico)
            es-MX     español de México
            es-ES     español de España
            en-US     inglés de Estados Unidos

        Las dos partes importan por separado: el IDIOMA decide las palabras
        (nombres de meses, "y" en las listas); la REGIÓN decide los
        formatos (separadores, moneda). es-MX y es-ES hablan el mismo
        idioma y escriben los números al revés.

        Crearlo:
            Locale.forLanguageTag("es-MX")    con la etiqueta estándar (BCP 47): la más usada,
                                              y la que llega de un navegador o una API
            Locale.of("es", "MX")             idioma y país por separado (Java 19)
            Locale.US, Locale.FRANCE...       constantes para algunos (no hay una para México)
            Locale.ROOT                       "ningún idioma": para formatos técnicos

        (Los constructores new Locale("es", "MX") están deprecados desde
        Java 19.)

        Ojo con la forma de escribirlo: toLanguageTag() da "es-MX" (con
        guion, el estándar) y toString() da "es_MX" (con guion bajo, el
        formato viejo de Java). Para guardar o enviar, usa toLanguageTag.
    */
    static boolean localeExample() {
        Locale fromTag = Locale.forLanguageTag("es-MX");
        Locale fromParts = Locale.of("es", "MX");

        return fromTag.equals(fromParts)
                && fromTag.getLanguage().equals("es") && fromTag.getCountry().equals("MX")
                && fromTag.toLanguageTag().equals("es-MX") && fromTag.toString().equals("es_MX")
                && fromTag.getDisplayName(Locale.forLanguageTag("es")).equals("español (México)");
    }

    /*
        =================================================================
        CAPA 3) EL LOCALE POR DEFECTO: EL PELIGRO ESCONDIDO
        =================================================================

        Locale.getDefault() es el de la computadora donde corre el
        programa: lo toma del sistema operativo. Y MUCHOS métodos lo usan
        sin decírtelo cuando no les pasas uno:
            String.format("%.2f", x), NumberFormat.getInstance(),
            MessageFormat.format(...), toLowerCase(), Scanner,
            DateTimeFormatter.ofPattern("MMMM")...

        El problema: el mismo código da resultados DISTINTOS según la
        máquina. En tu laptop (con Windows en español de España, como la
        máquina donde se probó esta guía) String.format("%.2f", 3.5) da
        "3,50"; en el servidor (un contenedor Linux, normalmente en inglés)
        da "3.50". Si ese texto se guarda en un archivo o se manda a otro
        sistema que espera punto decimal, todo se rompe, y solo en
        producción.

        Reglas:
        1) Pasa SIEMPRE el Locale explícitamente.
           - Para mostrar a una persona: el Locale de ESA persona (de su
             perfil, o del encabezado Accept-Language de su navegador).
           - Para texto técnico (archivos, JSON, logs, claves): Locale.ROOT.
        2) Nunca llames Locale.setDefault en una librería o un servidor:
           cambia el comportamiento de TODO el programa, incluidas
           librerías que no son tuyas.

        El ejemplo da el mismo resultado en cualquier computadora porque
        nunca depende del Locale por defecto.
    */
    static String priceForUser(BigDecimal price, Locale userLocale) {
        return NumberFormat.getCurrencyInstance(userLocale).format(price);   // el Locale del USUARIO
    }

    static String priceForCsv(BigDecimal price) {
        return String.format(Locale.ROOT, "%.2f", price);                    // técnico: siempre igual
    }

    static boolean explicitLocaleExample() {
        BigDecimal price = new BigDecimal("1234.5");
        return priceForUser(price, MEXICO).equals("$1,234.50")
                && priceForUser(price, SPAIN).equals("1.234,50 €")
                && priceForCsv(price).equals("1234.50");
    }

    /*
        =================================================================
        CAPA 4) NUMBERFORMAT: NÚMEROS, ENTEROS Y PORCENTAJES
        =================================================================

        NumberFormat (java.text) formatea números para personas. Se
        obtiene con fábricas que reciben el Locale:

            NumberFormat.getNumberInstance(locale)     1,234.568   (3 decimales por defecto)
            NumberFormat.getIntegerInstance(locale)    1,235       (sin decimales)
            NumberFormat.getPercentInstance(locale)    26%         (multiplica por 100)
            NumberFormat.getCurrencyInstance(locale)   $1,234.57   (CAPA 5)

        Se ajustan con setMinimumFractionDigits, setMaximumFractionDigits,
        setGroupingUsed(false) (sin separador de miles) y setRoundingMode.

        DOS TRAMPAS:
        1) El redondeo por defecto es HALF_EVEN ("del banquero"; Numbers,
           CAPA 7): un .5 va al número PAR más cercano. 2.5 -> "2" y
           3.5 -> "4". Si esperas el redondeo de la escuela, pide
           setRoundingMode(RoundingMode.HALF_UP).
        2) getPercentInstance espera la FRACCIÓN: 0.256 se muestra "26%".
           Si le pasas 25.6 muestra "2,560%".

        Y el detalle regional: en es-ES el porcentaje lleva un espacio
        (26 %), en es-MX no (26%).
    */
    static boolean numberFormatExample() {
        NumberFormat integers = NumberFormat.getIntegerInstance(Locale.US);
        boolean bankersRounding = integers.format(2.5).equals("2") && integers.format(3.5).equals("4");

        NumberFormat twoDecimals = NumberFormat.getNumberInstance(MEXICO);
        twoDecimals.setMinimumFractionDigits(2);
        twoDecimals.setMaximumFractionDigits(2);

        NumberFormat percent = NumberFormat.getPercentInstance(MEXICO);

        return bankersRounding
                && twoDecimals.format(1234.5).equals("1,234.50")
                && percent.format(0.256).equals("26%")
                && percent.format(25.6).equals("2,560%")                     // le pasaron el porcentaje, no la fracción
                && NumberFormat.getPercentInstance(SPAIN).format(0.256).equals("26 %");
    }

    /*
        =================================================================
        CAPA 5) DINERO: GETCURRENCYINSTANCE Y CURRENCY
        =================================================================

        getCurrencyInstance(locale) usa la moneda del PAÍS del Locale y
        sabe dónde va el símbolo y cuántos decimales lleva:

            es-MX   $1,234.50
            en-US   $1,234.50
            es-ES   1.234,50 €
            ja-JP   ￥1,234          (el yen no tiene centavos)

        La clase Currency representa una moneda por su código ISO 4217
        (MXN, USD, EUR, JPY): getDefaultFractionDigits() dice cuántos
        decimales usa (2 para MXN, 0 para JPY) y getDisplayName(locale) su
        nombre ("peso mexicano").

        CUIDADO: el Locale decide el FORMATO, no la MONEDA del dinero. Si
        un cliente de Estados Unidos ve un precio en pesos, NO uses
        getCurrencyInstance(Locale.US) a secas: mostraría "$1,234.50" y
        creería que son dólares. Fija la moneda:
            NumberFormat f = NumberFormat.getCurrencyInstance(Locale.US);
            f.setCurrency(Currency.getInstance("MXN"));   // "MX$1,234.50"

        La forma correcta de modelar dinero: BigDecimal + código de moneda
        (Numbers, CAPA 8), y formatear al MOSTRAR. NumberFormat.format
        acepta un BigDecimal y lo formatea sin pasar por double.
    */
    record Money(BigDecimal amount, Currency currency) {
        String display(Locale userLocale) {
            NumberFormat format = NumberFormat.getCurrencyInstance(userLocale);
            format.setCurrency(currency);               // la moneda del dinero, no la del país del usuario
            format.setMaximumFractionDigits(currency.getDefaultFractionDigits());
            return format.format(amount);
        }
    }

    static boolean currencyExample() {
        Money pesos = new Money(new BigDecimal("1234.50"), Currency.getInstance("MXN"));
        Currency yen = Currency.getInstance("JPY");

        return pesos.display(MEXICO).equals("$1,234.50")
                && pesos.display(Locale.US).equals("MX$1,234.50")          // un estadounidense ve que son pesos
                && yen.getDefaultFractionDigits() == 0
                && Currency.getInstance(MEXICO).getCurrencyCode().equals("MXN")
                && pesos.currency().getDisplayName(MEXICO).equals("peso mexicano");
    }

    /*
        =================================================================
        CAPA 6) NÚMEROS COMPACTOS: 1.2M
        =================================================================

        Java 12 agregó getCompactNumberInstance para mostrar cantidades
        grandes en poco espacio, como en redes sociales o tableros:

            NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT)  1M
            ...(Locale.US, NumberFormat.Style.LONG)                                     1 million

        Por defecto redondea a entero; setMaximumFractionDigits(1) da
        "1.2M". Los textos salen de la base de datos de formatos regionales
        que trae el JDK (CLDR), y no todos los idiomas tienen formas
        compactas tan cortas como el inglés: pruébalo con los Locale que
        vayas a usar.
    */
    static boolean compactExample() {
        NumberFormat shortForm = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        NumberFormat longForm = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.LONG);
        boolean rounded = shortForm.format(1_200_000).equals("1M") && longForm.format(1_200_000).equals("1 million");

        shortForm.setMaximumFractionDigits(1);
        return rounded && shortForm.format(1_200_000).equals("1.2M") && shortForm.format(15_300).equals("15.3K");
    }

    /*
        =================================================================
        CAPA 7) LEER NÚMEROS QUE ESCRIBIÓ UNA PERSONA
        =================================================================

        Double.parseDouble e Integer.parseInt leen el formato TÉCNICO
        (punto decimal, sin separadores). Un usuario de España que escribe
        "1,5" recibe NumberFormatException: For input string: "1,5".

        NumberFormat.parse lee el formato del Locale ("1,5" en es-ES es
        1.5). Pero tiene una trampa grave: es PERMISIVO. Lee desde el
        inicio hasta donde puede y SE CALLA el resto:
            NumberFormat.getNumberInstance(Locale.US).parse("12abc")  ->  12

        Para validar de verdad, usa la versión con ParsePosition y revisa
        que haya consumido TODO el texto: si getIndex() no llegó al final,
        sobró basura.

        (Lanza ParseException, checked, si no puede leer nada. Y ojo: el
        resultado es un Number: Long si el valor es entero, aunque se
        escriba "2.0"; Double si no.)
    */
    static Optional<Number> parseStrict(String text, Locale locale) {
        ParsePosition position = new ParsePosition(0);
        Number parsed = NumberFormat.getNumberInstance(locale).parse(text.strip(), position);
        boolean consumedEverything = position.getIndex() == text.strip().length();
        return parsed != null && consumedEverything ? Optional.of(parsed) : Optional.empty();
    }

    static boolean parsingExample() {
        boolean technicalParserFails;
        try {
            Double.parseDouble("1,5");
            technicalParserFails = false;
        } catch (NumberFormatException e) {
            technicalParserFails = e.getMessage().equals("For input string: \"1,5\"");
        }

        boolean lenient;
        try {
            lenient = NumberFormat.getNumberInstance(Locale.US).parse("12abc").intValue() == 12;   // ¡se calló "abc"!
        } catch (ParseException e) {
            lenient = false;
        }

        return technicalParserFails && lenient
                && parseStrict("1,5", SPAIN).get().doubleValue() == 1.5
                && parseStrict("1,234.5", Locale.US).get().doubleValue() == 1234.5
                && parseStrict("12abc", Locale.US).isEmpty()
                && parseStrict("abc", Locale.US).isEmpty();
    }

    /*
        =================================================================
        CAPA 8) DECIMALFORMAT: PATRONES PROPIOS
        =================================================================

        Cuando necesitas un formato EXACTO (un reporte, un código), crea un
        DecimalFormat con un patrón:

            0      un dígito obligatorio (rellena con 0)
            #      un dígito opcional (no muestra ceros sobrantes)
            ,      separador de miles
            .      separador decimal
            %      multiplica por 100 y agrega %

            "#,##0.00"   1234.5  ->  1,234.50
            "000"        7       ->  007          (folios, códigos)
            "0.#"        2.0     ->  2

        Los símbolos del patrón son siempre "," y ".", pero lo que se
        IMPRIME depende de los DecimalFormatSymbols: pásale los del Locale
        que quieras. Sin ellos, usa los del Locale por defecto (CAPA 3).
    */
    static final DecimalFormatSymbols TECHNICAL = DecimalFormatSymbols.getInstance(Locale.ROOT);

    static boolean decimalFormatExample() {
        return new DecimalFormat("#,##0.00", TECHNICAL).format(1234.5).equals("1,234.50")
                && new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(SPAIN)).format(1234.5).equals("1.234,50")
                && new DecimalFormat("000", TECHNICAL).format(7).equals("007")
                && new DecimalFormat("0.#", TECHNICAL).format(2.0).equals("2");
    }

    /*
        =================================================================
        CAPA 9) NUMBERFORMAT NO ES SEGURO CON HILOS
        =================================================================

        A diferencia de DateTimeFormatter (inmutable; DatesAndTimes,
        CAPA 11), NumberFormat y DecimalFormat son MUTABLES (los setters
        de la CAPA 4) y guardan estado mientras formatean. Si un mismo
        objeto se usa desde varios hilos (un servidor web atiende cada
        petición en un hilo), los resultados se mezclan o salen corruptos
        de vez en cuando: el peor tipo de error.

        Por eso en esta guía NINGÚN NumberFormat es una constante static:
        se crea uno nuevo en cada método, que es barato. Alternativas si
        se usa muchísimo: un ThreadLocal<NumberFormat> (v13_concurrency/), o
        String.format, que crea su propio formateador en cada llamada.
        (Los DecimalFormatSymbols de la CAPA 8 SÍ pueden ser constante:
        aquí solo se leen.)
    */

    /*
        =================================================================
        CAPA 10) MENSAJES TRADUCIDOS: RESOURCEBUNDLE Y MESSAGEFORMAT
        =================================================================

        Los textos de la interfaz no se escriben en el código: se guardan
        en archivos por idioma y el programa elige según el Locale.

        RESOURCEBUNDLE: un conjunto de textos con un nombre base, con una
        versión por Locale. Normalmente son archivos .properties:
            messages.properties          (base: si no hay otro)
            messages_es.properties       español
            messages_es_MX.properties    solo lo que cambia en México
        ResourceBundle.getBundle("messages", locale) busca del más
        específico al más general: es_MX -> es -> base. (Aquí, para que la
        guía sea un solo archivo, se usan clases ListResourceBundle, que
        funcionan igual.)

        TRAMPA del respaldo: si no encuentra el idioma pedido, ANTES de ir
        a la base prueba el Locale POR DEFECTO de la máquina. Un usuario
        francés en un servidor configurado en español recibe español, y
        en otro servidor inglés. Para que el resultado no dependa de la
        máquina, usa ResourceBundle.Control.getNoFallbackControl(...).

        MESSAGEFORMAT: el texto traducido lleva marcadores numerados,
        porque el ORDEN de las palabras cambia entre idiomas:
            es: "{0} tiene {1,number,integer} pedidos"
            en: "{0} has {1,number,integer} orders"
        Nunca armes frases concatenando pedazos traducidos ("tiene" +
        n + "pedidos"): en otro idioma el número va en otro lugar.

        TRAMPA del apóstrofo: en MessageFormat, ' sirve para escapar.
        "l'ordre {0}" se imprime "lordre {0}": el apóstrofo desaparece y
        el marcador NO se reemplaza. Hay que escribir ''.
    */
    public static final class Messages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {{"orders", "{0} has {1,number,integer} orders"}};
        }
    }

    public static final class Messages_es extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {{"orders", "{0} tiene {1,number,integer} pedidos"}};
        }
    }

    static final String BUNDLE = FormattingAndLocale.class.getName() + "$Messages";

    static String ordersMessage(Locale locale, String customer, int orders) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_CLASS));
        return new MessageFormat(bundle.getString("orders"), locale).format(new Object[] {customer, orders});
    }

    static boolean messagesExample() {
        boolean mexican = ordersMessage(MEXICO, "Ana", 1500).equals("Ana tiene 1,500 pedidos");   // es_MX -> es
        boolean spanish = ordersMessage(SPAIN, "Ana", 1500).equals("Ana tiene 1.500 pedidos");    // mismo texto, otro número
        boolean french = ordersMessage(FRANCE, "Ana", 3).equals("Ana has 3 orders");              // sin francés: la base

        boolean apostropheTrap = MessageFormat.format("l'ordre {0}", "x").equals("lordre {0}")
                && MessageFormat.format("l''ordre {0}", "x").equals("l'ordre x");

        return mexican && spanish && french && apostropheTrap;
    }

    /*
        =================================================================
        CAPA 11) LISTAS: "PAN, LECHE Y HUEVOS" (LISTFORMAT)
        =================================================================

        Unir con comas es fácil (String.join, Collectors.joining). Lo
        difícil es la última unión, que depende del idioma:
            español   pan, leche y huevos
            inglés    a, b, and c        (con la "coma de Oxford")
        Ojo con lo que NO hace: en español la "y" se vuelve "e" antes de un
        sonido "i" ("pan e imán") y la "o" se vuelve "u" antes de "o"
        ("siete u ocho"), pero ListFormat no aplica esas reglas (medido en
        JDK 26: "pan y imán"). Si te importan, hay que ajustarlas a mano.

        Java 22 agregó java.text.ListFormat:
            ListFormat.getInstance(locale, ListFormat.Type.STANDARD, ListFormat.Style.FULL)
        Type.OR da "pan, leche o huevos".
    */
    static boolean listFormatExample() {
        List<String> groceries = List.of("pan", "leche", "huevos");
        Locale spanish = Locale.forLanguageTag("es");

        return ListFormat.getInstance(spanish, ListFormat.Type.STANDARD, ListFormat.Style.FULL)
                        .format(groceries).equals("pan, leche y huevos")
                && ListFormat.getInstance(spanish, ListFormat.Type.OR, ListFormat.Style.FULL)
                        .format(groceries).equals("pan, leche o huevos")
                && ListFormat.getInstance(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.FULL)
                        .format(List.of("a", "b", "c")).equals("a, b, and c");
    }

    /*
        =================================================================
        CAPA 12) LAS REGLAS PROFESIONALES
        =================================================================

        1) GUARDA DATOS, NO TEXTO FORMATEADO. En la base de datos y en las
           APIs: números como números (BigDecimal para dinero, más el
           código de moneda), fechas en ISO o Instant. Nunca "$1,234.50"
           ni "15/03/2024".

        2) FORMATEA EN EL BORDE, al mostrar, con el Locale del usuario.
           Si la API la consume un frontend, normalmente el frontend
           formatea: tú mandas datos.

        3) Texto técnico (archivos, CSV, JSON, logs): Locale.ROOT, siempre.

        4) Nunca dependas del Locale por defecto (CAPA 3).

        5) Los textos de la interfaz, en ResourceBundles; frases completas
           con marcadores, nunca pedazos concatenados (CAPA 10).

        6) Prueba con al menos dos Locale con formatos opuestos (es-MX y
           es-ES, por ejemplo): así aparecen los errores de separadores.

        7) Los formatos vienen de la base de datos CLDR del JDK y pueden
           cambiar entre versiones de Java (por ejemplo, el tipo de espacio
           en francés). No compares texto formateado en pruebas de lógica
           de negocio: compara los datos.
    */

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) String.format, NumberFormat o MessageFormat sin Locale: el
           resultado depende de la máquina (CAPA 3).

        2) Locale.setDefault en un servidor o una librería (CAPA 3).

        3) Esperar redondeo HALF_UP de NumberFormat: es HALF_EVEN (CAPA 4).

        4) Pasar 25.6 a getPercentInstance en vez de 0.256 (CAPA 4).

        5) Mostrar pesos con el formato de moneda de otro país sin fijar
           la moneda (CAPA 5).

        6) Leer números de usuarios con parseDouble, o con
           NumberFormat.parse sin revisar que consumió todo (CAPA 7).

        7) Un NumberFormat static compartido entre hilos (CAPA 9).

        8) Concatenar pedazos traducidos; apóstrofos sin duplicar en
           MessageFormat; olvidar el respaldo al Locale por defecto
           (CAPA 10).

        9) Guardar texto ya formateado en la base de datos (CAPA 12).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - i18n: preparar para cualquier idioma; l10n: adaptar a uno.
        - Locale = idioma + región; Locale.forLanguageTag("es-MX");
          toLanguageTag para guardarlo; Locale.ROOT para lo técnico.
        - El Locale por defecto cambia según la máquina: pásalo siempre.
        - NumberFormat: número, entero, porcentaje (fracción), moneda,
          compacto. Redondeo HALF_EVEN. NO es seguro con hilos.
        - Dinero: BigDecimal + Currency; fija la moneda al formatear.
        - Leer números de personas: NumberFormat + ParsePosition, revisando
          que consumió todo.
        - DecimalFormat con patrón + DecimalFormatSymbols del Locale.
        - Textos: ResourceBundle (es_MX -> es -> base, cuidado con el
          respaldo al Locale por defecto) + MessageFormat ('' para el
          apóstrofo).
        - ListFormat (Java 22) para "a, b y c".
        - Guarda datos; formatea al mostrar.
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
