package v11_patterns.part1_creational;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
    =====================================================================
    PROTOTYPE: CREAR OBJETOS COPIANDO UNA PLANTILLA
    =====================================================================

    Ruta de patterns/: guía 4 de 28  (part1_creational)
    Anterior:  part1_creational/Builder
    Siguiente: part1_creational/Singleton

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: BAJA como patrón formal; MEDIA la idea que hay detrás.
    Lo que sí usarás: copiar objetos correctamente (superficial vs
    profunda) y tener plantillas preconfiguradas de las que salen
    variaciones. Esta guía se queda solo con eso.

    Índice:
        CAPA 1)  La idea: copiar en vez de construir desde cero
        CAPA 2)  Cómo se copia hoy: constructor o fábrica de copia
        CAPA 3)  Copia superficial y copia profunda
        CAPA 4)  Con objetos inmutables, copiar es casi gratis
        CAPA 5)  Registro de plantillas
        CAPA 6)  Cuándo usarlo y cuándo no
        CAPA 7)  Errores comunes
        RESUMEN FINAL
*/
public class Prototype {

    public static void main(String[] args) {
        check(copyConstructorExample(), "CAPA 2");
        check(shallowVsDeepExample(), "CAPA 3");
        check(immutableCopyExample(), "CAPA 4");
        check(templateRegistryExample(), "CAPA 5");
        System.out.println("Prototype: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) LA IDEA: COPIAR EN VEZ DE CONSTRUIR DESDE CERO
        =================================================================

        PROTOTYPE: "Crea objetos nuevos copiando un objeto existente (el
        prototipo), en lugar de construirlos desde cero."

        Cuándo tiene sentido copiar:
        - El objeto se CONFIGURA en muchos pasos, y quieres variantes que
          comparten casi toda la configuración: una campaña de correo base
          (remitente, plantilla, horario, segmento) y variantes que solo
          cambian el asunto.
        - Crearlo es CARO (se arma leyendo archivos, consultando una base
          de datos o haciendo cálculos), y las variantes solo difieren en
          poco.
        - Necesitas una "foto" de un objeto para modificarla sin tocar el
          original (un borrador que el usuario edita y puede descartar).

        En Java moderno, la idea se aplica con las herramientas de las
        capas siguientes. (El mecanismo original del lenguaje para esto,
        clone() y la interface Cloneable, existe pero no se usa en código
        nuevo por estar mal diseñado; v2_oop/part4_advanced_types/
        ObjectContract, CAPA 11.)
    */

    /*
        =================================================================
        CAPA 2) CÓMO SE COPIA HOY: CONSTRUCTOR O FÁBRICA DE COPIA
        =================================================================

        La forma estándar de copiar un objeto de una clase MUTABLE:

        - CONSTRUCTOR DE COPIA: un constructor que recibe otro objeto de la
          misma clase y copia sus campos:
              Campaign(Campaign original) { ... }
        - FÁBRICA DE COPIA: un método estático con nombre:
              static Campaign copyOf(Campaign original)

        Ventajas: es código normal y explícito; la clase decide EXACTAMENTE
        qué se copia y cómo (CAPA 3); funciona con campos final; y se lee
        solo. Es lo mismo que hace el JDK: new ArrayList<>(otraLista),
        new HashMap<>(otroMapa), List.copyOf(...).

        El ejemplo: una campaña base, y una variante que solo cambia el
        asunto. La original no se toca.
    */
    static final class Campaign {
        private String subject;
        private final String sender;
        private final String segment;
        private final List<String> tags;

        Campaign(String subject, String sender, String segment, List<String> tags) {
            this.subject = subject;
            this.sender = sender;
            this.segment = segment;
            this.tags = new ArrayList<>(tags);
        }

        Campaign(Campaign original) {                       // CONSTRUCTOR DE COPIA
            this.subject = original.subject;
            this.sender = original.sender;
            this.segment = original.segment;
            this.tags = new ArrayList<>(original.tags);     // la lista también se copia (CAPA 3)
        }

        void changeSubject(String subject) {
            this.subject = subject;
        }

        void addTag(String tag) {
            tags.add(tag);
        }

        String subject() {
            return subject;
        }

        String sender() {
            return sender;
        }

        List<String> tags() {
            return List.copyOf(tags);
        }
    }

    static boolean copyConstructorExample() {
        Campaign base = new Campaign("Ofertas de la semana", "ofertas@tienda.com", "clientes-activos", List.of("semanal"));
        Campaign variant = new Campaign(base);               // copia de la plantilla
        variant.changeSubject("Últimas horas de ofertas");

        return base.subject().equals("Ofertas de la semana")                 // la original intacta
                && variant.subject().equals("Últimas horas de ofertas")
                && variant.sender().equals(base.sender());                    // lo demás, heredado de la plantilla
    }

    /*
        =================================================================
        CAPA 3) COPIA SUPERFICIAL Y COPIA PROFUNDA
        =================================================================

        Lo importante al copiar es QUÉ se copia de los campos que son
        objetos (v1_fundamentals/part3_text_and_references/
        ReferencesAndNull):

        - COPIA SUPERFICIAL (shallow): se copian las REFERENCIAS. La copia y
          el original apuntan a la MISMA lista. Si una agrega una etiqueta,
          la otra también la ve. Es el error más común al copiar.
        - COPIA PROFUNDA (deep): se copian también los objetos MUTABLES de
          adentro (una lista nueva con los mismos elementos). Copia y
          original quedan independientes.

        La regla práctica:
        - Campos INMUTABLES (String, números, LocalDate, records, List.of):
          basta copiar la referencia. Nadie los puede cambiar, así que
          compartirlos es seguro (CAPA 4).
        - Campos MUTABLES (ArrayList, HashMap, objetos con setters): hay
          que copiarlos también, o la "copia" no es independiente.

        El ejemplo muestra el error: una copia que comparte la lista de
        etiquetas, y cómo una etiqueta agregada a la variante aparece en la
        campaña original.
    */
    static final class ShallowCampaign {
        final String subject;
        final List<String> tags;

        ShallowCampaign(String subject, List<String> tags) {
            this.subject = subject;
            this.tags = tags;
        }

        ShallowCampaign copyWithSubject(String newSubject) {
            return new ShallowCampaign(newSubject, this.tags);   // ERROR de diseño: comparte la lista mutable
        }
    }

    static boolean shallowVsDeepExample() {
        ShallowCampaign base = new ShallowCampaign("Ofertas", new ArrayList<>(List.of("semanal")));
        ShallowCampaign variant = base.copyWithSubject("Últimas horas");
        variant.tags.add("urgente");                        // solo a la variante...
        boolean shallowLeaks = base.tags.contains("urgente");   // ...y apareció en la original

        Campaign deepBase = new Campaign("Ofertas", "ofertas@tienda.com", "todos", List.of("semanal"));
        Campaign deepVariant = new Campaign(deepBase);      // el constructor de copia copia la lista
        deepVariant.addTag("urgente");
        boolean deepIsIndependent = !deepBase.tags().contains("urgente");

        return shallowLeaks && deepIsIndependent;
    }

    /*
        =================================================================
        CAPA 4) CON OBJETOS INMUTABLES, COPIAR ES CASI GRATIS
        =================================================================

        Si el objeto es INMUTABLE (un record con componentes inmutables),
        la discusión de la CAPA 3 desaparece:
        - No hace falta copiar para "proteger" el original: nadie lo puede
          cambiar.
        - Para obtener una variante, se crea un objeto NUEVO con el campo
          distinto, con métodos "with" (v2_oop/part4_advanced_types/
          Records, CAPA 8). Las partes que no cambian se COMPARTEN entre el
          original y la variante, y es seguro, porque son inmutables.

        Por eso, en Java moderno, el "prototipo" más común es simplemente
        un record base del que se derivan variantes con with. Es la forma
        más simple y la que menos errores produce.
    */
    record EmailTemplate(String subject, String sender, String segment, List<String> tags, int sendHour) {
        EmailTemplate {
            tags = List.copyOf(tags);                       // inmutable desde que nace
        }

        EmailTemplate withSubject(String newSubject) {
            return new EmailTemplate(newSubject, sender, segment, tags, sendHour);
        }

        EmailTemplate withSendHour(int newHour) {
            return new EmailTemplate(subject, sender, segment, tags, newHour);
        }
    }

    static boolean immutableCopyExample() {
        EmailTemplate base = new EmailTemplate("Ofertas", "ofertas@tienda.com", "activos", List.of("semanal"), 9);
        EmailTemplate evening = base.withSubject("Ofertas de la tarde").withSendHour(18);

        return base.subject().equals("Ofertas") && base.sendHour() == 9   // el original nunca cambia
                && evening.sendHour() == 18
                && evening.tags() == base.tags();           // comparten la lista: es inmutable, no hay riesgo
    }

    /*
        =================================================================
        CAPA 5) REGISTRO DE PLANTILLAS
        =================================================================

        La forma práctica del patrón: un REGISTRO de prototipos ya
        configurados, guardados por nombre. Cuando se necesita uno, se
        obtiene una COPIA de la plantilla y se ajusta lo que cambia.

        Usos reales:
        - Plantillas de documentos o correos que el negocio administra
          ("bienvenida", "recordatorio de pago").
        - Configuraciones de productos ("laptop básica", "laptop gamer")
          de las que el cliente parte para personalizar.
        - Datos de prueba: un pedido "típico" del que cada prueba deriva
          el caso que necesita (testing/).

        Con records, "obtener una copia" es devolver la misma plantilla:
        como es inmutable, las variantes se crean con with y la plantilla
        del registro nunca se altera. Con clases mutables, el registro
        debe devolver una COPIA PROFUNDA (CAPA 3); si devolviera la misma
        instancia, el primer usuario que la modifique cambiaría la
        plantilla para todos los demás.
    */
    static final class TemplateRegistry {
        private final Map<String, EmailTemplate> templates = Map.of(
                "bienvenida", new EmailTemplate("Bienvenido a la tienda", "hola@tienda.com", "nuevos", List.of("onboarding"), 10),
                "recordatorio", new EmailTemplate("Tu carrito te espera", "ventas@tienda.com", "carritos", List.of("recuperacion"), 19));

        EmailTemplate get(String name) {
            EmailTemplate template = templates.get(name);
            if (template == null) {
                throw new IllegalArgumentException("Plantilla desconocida: " + name);
            }
            return template;                                // inmutable: compartirla es seguro
        }
    }

    static boolean templateRegistryExample() {
        TemplateRegistry registry = new TemplateRegistry();
        EmailTemplate forAna = registry.get("recordatorio").withSubject("Ana, tu carrito te espera");
        EmailTemplate forLuis = registry.get("recordatorio").withSendHour(8);

        return forAna.sender().equals("ventas@tienda.com")                       // lo heredado de la plantilla
                && forLuis.subject().equals("Tu carrito te espera")              // la otra variante no se enteró
                && registry.get("recordatorio").subject().equals("Tu carrito te espera")   // la plantilla, intacta
                && registry.get("recordatorio").sendHour() == 19;
    }

    /*
        =================================================================
        CAPA 6) CUÁNDO USARLO Y CUÁNDO NO
        =================================================================

        ÚSALO cuando:
        - Necesitas variantes de un objeto con mucha configuración en común.
        - Construir el objeto desde cero es caro y las variantes difieren
          poco.
        - Necesitas una copia de trabajo que se pueda modificar o descartar
          sin afectar al original.

        Cómo, en orden de preferencia:
        1) Records inmutables con métodos with (CAPA 4).
        2) Constructor o fábrica de copia, con copia profunda de lo mutable
           (CAPAS 2 y 3).
        3) Un registro de plantillas si hay varias con nombre (CAPA 5).

        NO hace falta cuando:
        - Crear el objeto es simple y barato: new, o un Builder
          (part1_creational/Builder), que también se puede reutilizar como
          plantilla (Builder, CAPA 5).
        - El objeto es inmutable y no necesitas variantes: compártelo tal
          cual.
    */

    /*
        =================================================================
        CAPA 7) ERRORES COMUNES
        =================================================================

        1) Copias superficiales de objetos con colecciones o campos
           mutables: la copia y el original quedan conectados (CAPA 3).

        2) Un registro que entrega la MISMA instancia mutable de la
           plantilla: el primero que la cambia la cambia para todos
           (CAPA 5).

        3) Copiar a fondo lo que es inmutable: trabajo inútil (CAPA 4).

        4) Usar clone() y Cloneable en código nuevo (CAPA 1).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Prototype: crear copiando una plantilla en vez de construir desde
          cero; útil para variantes y objetos caros de armar.
        - Hoy se copia con constructores o fábricas de copia (como
          new ArrayList<>(otra)).
        - Superficial = comparte los objetos de adentro; profunda = los
          copia. Copia a fondo solo lo MUTABLE.
        - Con records inmutables y with, copiar es casi gratis y seguro.
        - Registro de plantillas: obtener y ajustar; nunca entregar la
          plantilla mutable original.
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
