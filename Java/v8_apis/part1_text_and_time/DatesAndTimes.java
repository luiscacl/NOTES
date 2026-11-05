package v8_apis.part1_text_and_time;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/*
    =====================================================================
    FECHAS Y HORAS CON JAVA.TIME — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de apis/: guía 1 de 6  (part1_text_and_time)
    Anterior:  v7_functional/part2_streams/OptionalUsage
    Siguiente: part1_text_and_time/RegularExpressions

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Ningún ejemplo usa la fecha de HOY (now()): los resultados serían
      distintos cada día. La CAPA 12 explica cómo se hace eso bien.

    Índice:
        CAPA 1)  Por qué el tiempo es difícil, y la API vieja
        CAPA 2)  El mapa de java.time: qué clase para qué
        CAPA 3)  LocalDate: fechas de calendario
        CAPA 4)  Inmutables: el error de ignorar el resultado
        CAPA 5)  Comparar y medir entre fechas
        CAPA 6)  LocalTime y LocalDateTime
        CAPA 7)  Period y Duration: dos formas de medir
        CAPA 8)  Instant: el tiempo de la máquina
        CAPA 9)  Zonas horarias: ZoneId, ZonedDateTime y el horario de verano
        CAPA 10) Qué guardar y dónde: las reglas profesionales
        CAPA 11) Formatear y parsear
        CAPA 12) now() y Clock: código con tiempo que se puede probar
        CAPA 13) Convivir con Date y Calendar
        CAPA 14) Errores comunes
        RESUMEN FINAL
*/
public class DatesAndTimes {

    public static void main(String[] args) {
        check(legacyApiExample(), "CAPA 1");
        check(localDateExample(), "CAPA 3");
        check(immutabilityExample(), "CAPA 4");
        check(compareExample(), "CAPA 5");
        check(timeExample(), "CAPA 6");
        check(periodAndDurationExample(), "CAPA 7");
        check(instantExample(), "CAPA 8");
        check(zonesExample(), "CAPA 9");
        check(formattingExample(), "CAPA 11");
        check(clockExample(), "CAPA 12");
        check(interopExample(), "CAPA 13");
        System.out.println("DatesAndTimes: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) POR QUÉ EL TIEMPO ES DIFÍCIL, Y LA API VIEJA
        =================================================================

        Fechas y horas parecen simples hasta que las programas:
        - Los meses tienen 28, 29, 30 o 31 días; los años bisiestos.
        - Las ZONAS HORARIAS: las 10:00 en CDMX son las 18:00 en Madrid.
        - El HORARIO DE VERANO: en muchos países hay un día de 23 horas y
          otro de 25, y horas que no existen o que ocurren dos veces.
        - Los gobiernos CAMBIAN esas reglas (México quitó el horario de
          verano en 2022).
        - "Un mes después del 31 de enero" no tiene una respuesta obvia.

        Java 1 tenía java.util.Date y Calendar, famosas por estar mal
        diseñadas:
        - MUTABLES: pasar una fecha a un método permitía que te la
          cambiaran (v2_oop/part2_pillars/Encapsulation, CAPA 8).
        - Meses desde CERO: Calendar.JANUARY vale 0, así que "mes 12" es
          enero del año siguiente.
        - Date guardaba el año desde 1900: new Date(2024, 0, 1) es el año
          3924.
        - Date no es una fecha: es un instante (milisegundos), y su
          toString usa la zona de la computadora.
        - SimpleDateFormat, para formatear, NO es segura con hilos: compartida
          entre hilos produce fechas corruptas.

        Java 8 trajo java.time: clases inmutables, meses del 1 al 12, una
        clase distinta para cada concepto y nombres claros. Para código
        nuevo, SIEMPRE java.time. Date y Calendar solo para hablar con
        código viejo (CAPA 13).
    */
    static boolean legacyApiExample() {
        boolean zeroBasedMonths = Calendar.JANUARY == 0;                  // la trampa de la API vieja
        boolean newApiIsHuman = Month.JANUARY.getValue() == 1
                && LocalDate.of(2024, 1, 15).getMonthValue() == 1;       // java.time: enero es 1
        return zeroBasedMonths && newApiIsHuman;
    }

    /*
        =================================================================
        CAPA 2) EL MAPA DE JAVA.TIME: QUÉ CLASE PARA QUÉ
        =================================================================

        La idea central: hay dos formas de pensar el tiempo.
        - Tiempo HUMANO: "el 15 de marzo", "a las 9:00", como en un
          calendario o un reloj de pared. Depende de dónde estés.
        - Tiempo de MÁQUINA: un punto exacto en la línea del tiempo,
          igual en todo el mundo ("este evento ocurrió en el instante X").

        | Clase           | Ejemplo                               | Para                                       |
        |-----------------|---------------------------------------|--------------------------------------------|
        | LocalDate       | 2024-03-15                            | cumpleaños, feriados, vencimientos         |
        | LocalTime       | 09:30                                 | horario de apertura, alarma diaria         |
        | LocalDateTime   | 2024-03-15T09:30                      | fecha y hora "de pared", SIN zona          |
        | ZonedDateTime   | 2024-03-15T09:30-06:00[America/Mexico_City] | una cita en un lugar concreto        |
        | OffsetDateTime  | 2024-03-15T09:30-06:00                | con desfase fijo, sin reglas (APIs, BD)    |
        | Instant         | 2024-03-15T15:30:00Z                  | marcas de tiempo: logs, eventos, BD        |
        | Duration        | PT2H15M                               | cantidad de tiempo EXACTA (horas, segundos)|
        | Period          | P1Y2M3D                               | cantidad de CALENDARIO (años, meses, días) |
        | ZoneId          | America/Mexico_City                   | una zona con sus reglas (horario de verano)|
        | ZoneOffset      | -06:00                                | un desfase fijo respecto a UTC             |
        | Year, YearMonth, MonthDay | 2024, 2024-03, --03-15      | partes sueltas (vencimiento de tarjeta)    |
        | DayOfWeek, Month| MONDAY, MARCH                         | enums con métodos útiles                   |

        "Local" significa SIN ZONA: un LocalDateTime 2024-03-15T09:30 no
        dice en qué lugar del mundo, así que NO es un instante. No se
        puede saber cuántas horas faltan para él sin decir dónde.
    */

    /*
        =================================================================
        CAPA 3) LOCALDATE: FECHAS DE CALENDARIO
        =================================================================

        Crear:
            LocalDate.of(2024, 3, 15)            o LocalDate.of(2024, Month.MARCH, 15)
            LocalDate.parse("2024-03-15")        formato ISO 8601: año-mes-día
            LocalDate.now()                      hoy (CAPA 12)

        Validan: LocalDate.of(2024, 2, 30) lanza DateTimeException. No hay
        fechas imposibles como en la API vieja, que las "acomodaba".

        Moverse:
            plusDays(n), plusWeeks(n), plusMonths(n), plusYears(n)   y sus minus
            withDayOfMonth(1)                    el día 1 del mismo mes
            with(TemporalAdjusters.lastDayOfMonth())        último día del mes
            with(TemporalAdjusters.next(DayOfWeek.MONDAY))  el próximo lunes

        Preguntar:
            getDayOfWeek(), getMonth(), lengthOfMonth(), isLeapYear()

        EL CASO DIFÍCIL: sumar meses a fin de mes. 31 de enero + 1 mes:
        febrero no tiene 31. java.time AJUSTA al último día válido: 29 de
        febrero en 2024 (bisiesto), 28 en 2023. Consecuencia: sumar 1 mes
        dos veces NO es lo mismo que sumar 2 meses (31 ene -> 29 feb ->
        29 mar, pero 31 ene + 2 meses = 31 mar). Para cobros mensuales
        calcula SIEMPRE desde la fecha original (inicio.plusMonths(n)), no
        desde la anterior.
    */
    static boolean localDateExample() {
        LocalDate jan31 = LocalDate.of(2024, 1, 31);

        boolean clampsToEndOfMonth = jan31.plusMonths(1).equals(LocalDate.of(2024, 2, 29))
                && LocalDate.of(2023, 1, 31).plusMonths(1).equals(LocalDate.of(2023, 2, 28));
        boolean stepByStepDrifts = jan31.plusMonths(1).plusMonths(1).equals(LocalDate.of(2024, 3, 29))
                && jan31.plusMonths(2).equals(LocalDate.of(2024, 3, 31));          // desde el original: correcto

        LocalDate march1 = LocalDate.parse("2024-03-01");
        boolean adjusters = march1.with(TemporalAdjusters.lastDayOfMonth()).getDayOfMonth() == 31
                && march1.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).equals(LocalDate.of(2024, 3, 4));

        boolean validates;
        try {
            LocalDate.of(2024, 2, 30);
            validates = false;
        } catch (DateTimeException e) {
            validates = true;
        }

        return clampsToEndOfMonth && stepByStepDrifts && adjusters && validates
                && march1.getDayOfWeek() == DayOfWeek.FRIDAY && march1.isLeapYear();
    }

    /*
        =================================================================
        CAPA 4) INMUTABLES: EL ERROR DE IGNORAR EL RESULTADO
        =================================================================

        TODAS las clases de java.time son inmutables (como String;
        v1_fundamentals/part3_text_and_references/Strings). plusDays no
        cambia la fecha: DEVUELVE una nueva.

            LocalDate due = LocalDate.of(2024, 3, 1);
            due.plusDays(30);              // ERROR de lógica: el resultado se tira
            due = due.plusDays(30);        // correcto

        Es el error más común al empezar, y el compilador no avisa.

        A cambio de eso, la inmutabilidad da todo lo bueno de la CAPA 1 de
        ImmutableCollections: puedes compartir fechas, usarlas como clave
        de un mapa y entre hilos, sin copias defensivas. Y
        DateTimeFormatter también es inmutable: una constante compartida,
        a diferencia de SimpleDateFormat.
    */
    static boolean immutabilityExample() {
        LocalDate due = LocalDate.of(2024, 3, 1);
        due.plusDays(30);                               // no cambia nada
        boolean unchanged = due.equals(LocalDate.of(2024, 3, 1));

        LocalDate realDue = due.plusDays(30);           // el resultado es otra fecha
        return unchanged && realDue.equals(LocalDate.of(2024, 3, 31));
    }

    /*
        =================================================================
        CAPA 5) COMPARAR Y MEDIR ENTRE FECHAS
        =================================================================

        Comparar:
            a.isBefore(b), a.isAfter(b), a.isEqual(b)
            a.compareTo(b)       son Comparable: se ordenan y sirven en TreeSet
            a.equals(b)

        Medir:
            ChronoUnit.DAYS.between(a, b)       días entre dos fechas (b excluido)
            ChronoUnit.MONTHS.between(a, b)     meses COMPLETOS
            Period.between(a, b)                años, meses y días (CAPA 7)
            a.datesUntil(b)                     Stream<LocalDate> de a hasta b (b excluido; Java 9)

        Tip: el día final se EXCLUYE (como en IntStream.range). Para "del 1
        al 5 inclusive" usa between(a, b.plusDays(1)).
    */
    static boolean compareExample() {
        LocalDate checkIn = LocalDate.of(2024, 1, 1);
        LocalDate checkOut = LocalDate.of(2024, 3, 1);

        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);          // enero 31 + febrero 29
        List<DayOfWeek> firstWeek = LocalDate.of(2024, 3, 4)
                .datesUntil(LocalDate.of(2024, 3, 9))
                .map(LocalDate::getDayOfWeek)
                .toList();

        return checkIn.isBefore(checkOut) && nights == 60
                && ChronoUnit.MONTHS.between(checkIn, checkOut) == 2
                && firstWeek.equals(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
    }

    /*
        =================================================================
        CAPA 6) LOCALTIME Y LOCALDATETIME
        =================================================================

        LocalTime es una hora del día, sin fecha: "abrimos a las 9:00".
        Al sumar, DA LA VUELTA a la medianoche sin cambiar de día (no tiene
        día que cambiar): 23:00 + 2 horas = 01:00.

        LocalDateTime es fecha + hora, todavía sin zona. Se arma juntando:
            date.atTime(9, 30)          LocalDate -> LocalDateTime
            dateTime.toLocalDate()      y de regreso
            date.atStartOfDay()         las 00:00 de ese día

        truncatedTo(ChronoUnit.MINUTES) quita segundos y nanos (útil al
        comparar o guardar).

        Cuándo usar LocalDateTime: lo que pasa a una "hora de pared" sin
        importar dónde ("todos los días a las 8:00 se envía el reporte" en
        la zona de cada usuario). NO para "cuándo ocurrió algo": eso es
        Instant (CAPAS 8 y 10).
    */
    static boolean timeExample() {
        LocalTime opening = LocalTime.of(9, 0);
        LocalTime closing = LocalTime.of(18, 30);
        boolean isOpen = !LocalTime.of(12, 15).isBefore(opening) && LocalTime.of(12, 15).isBefore(closing);
        boolean wrapsAround = LocalTime.of(23, 0).plusHours(2).equals(LocalTime.of(1, 0));

        LocalDateTime meeting = LocalDate.of(2024, 3, 15).atTime(9, 30);
        LocalDateTime withSeconds = LocalDateTime.of(2024, 3, 15, 9, 30, 42);

        return isOpen && wrapsAround
                && meeting.toLocalDate().equals(LocalDate.of(2024, 3, 15))
                && withSeconds.truncatedTo(ChronoUnit.MINUTES).equals(meeting);
    }

    /*
        =================================================================
        CAPA 7) PERIOD Y DURATION: DOS FORMAS DE MEDIR
        =================================================================

        PERIOD: tiempo de CALENDARIO en años, meses y días. "1 mes" no
        tiene un número fijo de días; se interpreta según la fecha.
            Period.of(1, 2, 3)  -> P1Y2M3D
            Period.between(nacimiento, hoy).getYears()    la EDAD correcta

        DURATION: tiempo EXACTO en segundos y nanosegundos. "2 horas" son
        siempre 7,200 segundos.
            Duration.ofMinutes(135) -> PT2H15M
            Duration.between(inicio, fin)                 cuánto tardó algo

        (Los textos P1Y2M3D y PT2H15M son el formato ISO 8601 para
        cantidades: P = periodo, T = separa la parte de horas.)

        ¿Importa cuál uses? SÍ, el día que cambia el horario de verano. En
        Nueva York, el 10 de marzo de 2024 a las 2:00 el reloj saltó a las
        3:00: ese día tuvo 23 horas.
            sábado 12:00 + Period.ofDays(1)     = domingo 12:00   ("mismo horario, mañana")
            sábado 12:00 + Duration.ofHours(24) = domingo 13:00   ("24 horas exactas")
        Las dos son correctas: responden preguntas distintas. Una alarma
        diaria quiere Period; un temporizador de 24 horas quiere Duration.

        Calcular EDAD: Period.between(nacimiento, hoy).getYears(). No
        dividas días entre 365: los bisiestos hacen que falle cerca del
        cumpleaños.
    */
    static int age(LocalDate birthDate, LocalDate today) {
        return Period.between(birthDate, today).getYears();
    }

    static boolean periodAndDurationExample() {
        ZoneId newYork = ZoneId.of("America/New_York");
        ZonedDateTime saturdayNoon = ZonedDateTime.of(2024, 3, 9, 12, 0, 0, 0, newYork);

        ZonedDateTime sameWallTime = saturdayNoon.plus(Period.ofDays(1));
        ZonedDateTime exactly24Hours = saturdayNoon.plus(Duration.ofHours(24));
        Duration dstDayLength = Duration.between(
                LocalDate.of(2024, 3, 10).atStartOfDay(newYork),
                LocalDate.of(2024, 3, 11).atStartOfDay(newYork));

        return sameWallTime.getHour() == 12 && exactly24Hours.getHour() == 13
                && dstDayLength.equals(Duration.ofHours(23))                  // un día de 23 horas
                && age(LocalDate.of(1995, 8, 20), LocalDate.of(2024, 8, 19)) == 28   // un día antes del cumpleaños
                && age(LocalDate.of(1995, 8, 20), LocalDate.of(2024, 8, 20)) == 29
                && Duration.ofMinutes(135).toString().equals("PT2H15M")
                && Period.of(1, 2, 3).toString().equals("P1Y2M3D");
    }

    /*
        =================================================================
        CAPA 8) INSTANT: EL TIEMPO DE LA MÁQUINA
        =================================================================

        Un Instant es un punto EXACTO en la línea del tiempo: segundos (y
        nanosegundos) desde el 1 de enero de 1970 a las 00:00 UTC (la
        "época Unix", epoch). Es el mismo valor en todo el mundo: cuando
        un servidor en México y otro en Japón registran el mismo evento,
        guardan el mismo Instant.

            Instant.now()                       ahora (CAPA 12)
            Instant.ofEpochMilli(ms)            desde milisegundos (lo que da
                                                System.currentTimeMillis())
            instant.toEpochMilli()
            Instant.parse("2024-03-15T15:30:00Z")    la Z significa UTC

        Lo que NO tiene: año, mes ni día "humanos", porque dependen de la
        zona (cuando en CDMX es 14 de marzo a las 22:00, en Madrid ya es 15).
        Para mostrarlo a una persona, conviértelo a su zona:
            instant.atZone(ZoneId.of("America/Mexico_City"))

        Para qué: logs, fechas de creación y modificación, eventos,
        expiración de tokens, todo lo que "ocurrió en un momento".
    */
    static boolean instantExample() {
        Instant event = Instant.parse("2024-03-15T03:30:00Z");

        LocalDate dayInMexico = event.atZone(ZoneId.of("America/Mexico_City")).toLocalDate();
        LocalDate dayInMadrid = event.atZone(ZoneId.of("Europe/Madrid")).toLocalDate();

        return Instant.ofEpochSecond(0).toString().equals("1970-01-01T00:00:00Z")
                && Instant.ofEpochMilli(event.toEpochMilli()).equals(event)
                && dayInMexico.equals(LocalDate.of(2024, 3, 14))     // mismo instante...
                && dayInMadrid.equals(LocalDate.of(2024, 3, 15));    // ...distinto día según dónde
    }

    /*
        =================================================================
        CAPA 9) ZONAS HORARIAS: ZONEID, ZONEDDATETIME Y EL HORARIO DE VERANO
        =================================================================

        ZoneOffset es un desfase FIJO respecto a UTC: -06:00.
        ZoneId es una ZONA con nombre ("America/Mexico_City",
        "Europe/Madrid") y con sus REGLAS: cuándo cambia de horario, y cómo
        cambiaron a lo largo de la historia. Java trae una base de datos de
        zonas (la de IANA) que se actualiza con el JDK.

        Usa siempre ZoneId con nombre de ciudad, no abreviaturas como
        "CST" (significa cosas distintas en distintos países) ni offsets
        fijos para personas (el offset cambia en verano).

        ZonedDateTime = LocalDateTime + ZoneId. Es un instante concreto Y
        sabe mostrarse en la hora local. Dos conversiones distintas:
        - withZoneSameInstant(otraZona): el MISMO momento, visto desde
          otra zona. "Mi junta es a las 10:00 en CDMX, ¿qué hora es en
          Madrid?" -> 18:00.
        - withZoneSameLocal(otraZona): la misma HORA DE PARED en otra
          zona, es decir, OTRO momento. Casi nunca es lo que quieres.

        Horas que no existen y que se repiten:
        - El día que el reloj se adelanta (2:00 -> 3:00), las 2:30 NO
          EXISTEN. ZonedDateTime.of las mueve hacia adelante: 3:30.
        - El día que se atrasa, una hora ocurre DOS veces. Java elige la
          primera (withLaterOffsetAtOverlap() elige la segunda).
        Por eso no programes tareas a las 2:30 de la madrugada.
    */
    static boolean zonesExample() {
        ZonedDateTime meetingInMexico = ZonedDateTime.of(2024, 7, 15, 10, 0, 0, 0, ZoneId.of("America/Mexico_City"));
        ZonedDateTime sameMomentInMadrid = meetingInMexico.withZoneSameInstant(ZoneId.of("Europe/Madrid"));
        ZonedDateTime sameWallClockInMadrid = meetingInMexico.withZoneSameLocal(ZoneId.of("Europe/Madrid"));

        ZonedDateTime nonExistent = ZonedDateTime.of(LocalDateTime.of(2024, 3, 10, 2, 30), ZoneId.of("America/New_York"));

        return sameMomentInMadrid.getHour() == 18
                && sameMomentInMadrid.toInstant().equals(meetingInMexico.toInstant())      // el mismo instante
                && sameWallClockInMadrid.getHour() == 10
                && !sameWallClockInMadrid.toInstant().equals(meetingInMexico.toInstant())  // otro instante
                && nonExistent.toLocalTime().equals(LocalTime.of(3, 30))                   // 2:30 no existe ese día
                && meetingInMexico.getOffset().equals(ZoneOffset.ofHours(-6));
    }

    /*
        =================================================================
        CAPA 10) QUÉ GUARDAR Y DÓNDE: LAS REGLAS PROFESIONALES
        =================================================================

        1) "CUÁNDO OCURRIÓ" (creado, pagado, enviado, logs): Instant, o en
           UTC en la base de datos (timestamp with time zone). Nunca la hora
           local del servidor: si mueves el servidor o cambia el horario de
           verano, los datos quedan desordenados.

        2) Convierte a la zona del USUARIO solo al MOSTRAR. La zona es un
           dato del usuario (su perfil o su navegador), no del servidor.

        3) Fechas SIN hora (cumpleaños, feriados, fecha de factura):
           LocalDate. Un cumpleaños no "ocurre" en un instante; es el mismo
           día en cualquier zona.

        4) Eventos FUTUROS a una hora local ("la cita es el 15 de junio a
           las 10:00 en la clínica de Monterrey"): guarda LocalDateTime +
           ZoneId por separado. Si guardas el Instant y el gobierno cambia
           las reglas de horario antes de esa fecha, la cita queda a otra
           hora.

        5) Nunca dependas de la zona por defecto de la computadora
           (ZoneId.systemDefault(), LocalDate.now() sin argumentos) en
           servidores: inyecta la zona o el Clock (CAPA 12).

        6) Duraciones medidas (cuánto tardó): System.nanoTime() o
           Duration; nunca restando horas de reloj.
    */

    /*
        =================================================================
        CAPA 11) FORMATEAR Y PARSEAR
        =================================================================

        Por defecto, toString() y parse() usan ISO 8601 (2024-03-15,
        2024-03-15T09:30). Es el formato para guardar e intercambiar datos
        (APIs, JSON, archivos): sin ambigüedad y se ordena bien como texto.

        Para mostrar a personas, DateTimeFormatter:
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                    .withLocale(Locale.forLanguageTag("es-MX"))   "domingo, 3 de marzo de 2024"

        Letras del patrón más usadas:
            yyyy año    MM mes (03)    MMM mes corto (mar)    MMMM mes (marzo)
            dd día      EEEE día de la semana                 HH hora 0-23
            hh hora 1-12  mm MINUTOS    ss segundos    a  a.m./p.m.
            'texto'     texto literal

        TRAMPAS de mayúsculas y minúsculas:
        - MM es MES, mm es MINUTOS. "dd/mm/yyyy" pone los minutos.
        - yyyy es el año; YYYY es el "año de la semana" (según las reglas
          de semana del Locale, no necesariamente las ISO). Son iguales casi todo el año... menos en los últimos días
          de diciembre: el 30 de diciembre de 2024 con YYYY sale 2025.
          Este error ha tumbado sistemas reales cada fin de año.

        Parsear valida: "2024-02-30" lanza DateTimeParseException ("Invalid
        date 'FEBRUARY 30'"); un texto con otro formato también. Cuando
        el texto viene de un usuario, atrápala y responde con un mensaje
        claro (v3_exceptions/part2_in_practice/ErrorHandlingBestPractices).

        Los formatters son inmutables y seguros con hilos: declara los que
        uses como constantes static final.
    */
    static final DateTimeFormatter MEXICAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter SPANISH_LONG =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX"));

    static boolean formattingExample() {
        LocalDate date = LocalDate.of(2024, 3, 3);
        LocalDate lastWeek = LocalDate.of(2024, 12, 30);

        boolean iso = date.toString().equals("2024-03-03");
        boolean mexican = date.format(MEXICAN_DATE).equals("03/03/2024");
        boolean spanish = date.format(SPANISH_LONG).equals("3 de marzo de 2024");
        boolean full = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                .withLocale(Locale.forLanguageTag("es-MX"))).equals("domingo, 3 de marzo de 2024");
        boolean weekYearTrap = lastWeek.format(DateTimeFormatter.ofPattern("YYYY-MM-dd")).equals("2025-12-30");

        boolean parsed = LocalDate.parse("31/12/2024", MEXICAN_DATE).equals(LocalDate.of(2024, 12, 31));
        boolean rejectsInvalid;
        try {
            LocalDate.parse("2024-02-30");
            rejectsInvalid = false;
        } catch (DateTimeParseException e) {
            rejectsInvalid = e.getMessage().endsWith("Invalid date 'FEBRUARY 30'");
        }

        return iso && mexican && spanish && full && weekYearTrap && parsed && rejectsInvalid;
    }

    /*
        =================================================================
        CAPA 12) NOW() Y CLOCK: CÓDIGO CON TIEMPO QUE SE PUEDE PROBAR
        =================================================================

        LocalDate.now(), Instant.now() leen el reloj de la computadora. Un
        método que los llama por dentro es imposible de probar: "¿la
        suscripción vence mañana?" da un resultado distinto cada día, y
        no puedes probar qué pasa el 29 de febrero sin esperar a que
        llegue.

        La solución profesional: recibir un java.time.Clock (inyección de
        dependencias, v10_design/). Todas las clases tienen now(clock):
            LocalDate.now(clock), Instant.now(clock)

        - En producción: Clock.system(ZoneId.of("America/Mexico_City")) o
          Clock.systemUTC().
        - En pruebas: Clock.fixed(instante, zona): el tiempo "se congela"
          en el momento que elijas. Clock.offset(...) lo adelanta.

        Así la clase no depende del reloj real ni de la zona del servidor
        (regla 5 de la CAPA 10).
    */
    record Subscription(String customer, LocalDate expiresOn) { }

    static final class SubscriptionService {
        private final Clock clock;

        SubscriptionService(Clock clock) {
            this.clock = clock;                         // el tiempo viene de afuera
        }

        boolean isActive(Subscription subscription) {
            return !LocalDate.now(clock).isAfter(subscription.expiresOn());
        }

        boolean expiresWithinDays(Subscription subscription, int days) {
            LocalDate today = LocalDate.now(clock);
            return isActive(subscription) && ChronoUnit.DAYS.between(today, subscription.expiresOn()) <= days;
        }
    }

    static boolean clockExample() {
        ZoneId mexico = ZoneId.of("America/Mexico_City");
        Clock leapDay = Clock.fixed(Instant.parse("2024-02-29T18:00:00Z"), mexico);   // 12:00 en CDMX
        SubscriptionService service = new SubscriptionService(leapDay);

        Subscription endsToday = new Subscription("Ana", LocalDate.of(2024, 2, 29));
        Subscription endsSoon = new Subscription("Luis", LocalDate.of(2024, 3, 3));
        Subscription ended = new Subscription("Mia", LocalDate.of(2024, 2, 28));

        SubscriptionService nextWeek = new SubscriptionService(Clock.offset(leapDay, Duration.ofDays(7)));

        return service.isActive(endsToday) && service.expiresWithinDays(endsSoon, 7)
                && !service.isActive(ended)
                && !nextWeek.isActive(endsSoon);        // una semana después ya venció
    }

    /*
        =================================================================
        CAPA 13) CONVIVIR CON DATE Y CALENDAR
        =================================================================

        Librerías viejas (y algunas APIs de JDBC) todavía piden o devuelven
        java.util.Date. Convierte en el BORDE y trabaja con java.time
        dentro de tu código:

            Instant instant = legacyDate.toInstant();
            Date legacy = Date.from(instant);
            ZonedDateTime z = gregorianCalendar.toZonedDateTime();

        Para bases de datos, JDBC 4.2 acepta LocalDate, LocalTime,
        LocalDateTime, OffsetTime y OffsetDateTime directamente: no hace
        falta pasar por java.sql.Date. Instant NO está en esa lista (depende
        del driver; el de PostgreSQL lo rechaza): conviértelo antes con
        instant.atOffset(ZoneOffset.UTC).
    */
    static boolean interopExample() {
        Instant instant = Instant.parse("2024-03-15T15:30:00Z");
        Date legacy = Date.from(instant);                   // hacia la API vieja
        Instant back = legacy.toInstant();                  // y de regreso

        YearMonth cardExpiry = YearMonth.of(2027, 8);       // una parte suelta: vencimiento de tarjeta
        return back.equals(instant) && legacy.getTime() == instant.toEpochMilli()
                && cardExpiry.atEndOfMonth().equals(LocalDate.of(2027, 8, 31));
    }

    /*
        =================================================================
        CAPA 14) ERRORES COMUNES
        =================================================================

        1) Usar Date, Calendar o SimpleDateFormat en código nuevo (CAPA 1).

        2) Llamar plusDays y no guardar el resultado (CAPA 4).

        3) Sumar meses paso a paso desde la fecha anterior en lugar de
           desde la original (CAPA 3).

        4) Calcular edades dividiendo días entre 365 (CAPA 7).

        5) Duration donde se quería Period, o al revés, en días con cambio
           de horario (CAPA 7).

        6) Guardar "cuándo ocurrió" como LocalDateTime o en la hora local
           del servidor (CAPAS 8 y 10).

        7) Usar abreviaturas de zona ("CST") u offsets fijos para personas
           (CAPA 9).

        8) withZoneSameLocal cuando se quería withZoneSameInstant (CAPA 9).

        9) "mm" por "MM" o "YYYY" por "yyyy" en un patrón (CAPA 11).

        10) LocalDate.now() escondido dentro de la lógica: no se puede
            probar; inyecta un Clock (CAPA 12).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - java.time: inmutable, meses 1-12, una clase por concepto. Nunca
          Date/Calendar en código nuevo.
        - LocalDate (calendario), LocalTime, LocalDateTime (sin zona),
          ZonedDateTime (con zona y reglas), Instant (momento exacto UTC).
        - Period = años/meses/días de calendario; Duration = tiempo exacto.
          Difieren en días con cambio de horario.
        - Fin de mes: se ajusta al último día válido; calcula desde la
          fecha original.
        - Zonas por nombre de ciudad; withZoneSameInstant para convertir.
        - Guarda Instant/UTC; muestra en la zona del usuario; LocalDate
          para fechas sin hora; LocalDateTime + ZoneId para citas futuras.
        - ISO para datos; DateTimeFormatter con Locale para personas;
          cuidado con mm/MM y yyyy/YYYY.
        - Inyecta Clock para poder probar.
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
