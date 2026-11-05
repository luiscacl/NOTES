package v11_patterns.part3_behavioral;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/*
    =====================================================================
    TEMPLATE METHOD: UN PROCESO FIJO CON PASOS QUE VARÍAN
    =====================================================================

    Ruta de patterns/: guía 22 de 28  (part3_behavioral)
    Anterior:  part3_behavioral/Strategy
    Siguiente: part3_behavioral/Visitor

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: MEDIA. Lo verás constantemente en el JDK y en los
    frameworks (clases base que "extiendes" llenando algunos métodos), y
    conviene entenderlo; en código propio nuevo, muchas veces se prefiere
    la versión con composición (CAPA 5).

    Índice:
        CAPA 1)  El problema: procesos iguales con un paso distinto
        CAPA 2)  La solución: el proceso en la clase base, los pasos en las subclases
        CAPA 3)  Pasos obligatorios, hooks opcionales y el método final
        CAPA 4)  Los costos de resolverlo con herencia
        CAPA 5)  La alternativa con composición
        CAPA 6)  Template Method en el JDK
        CAPA 7)  Template Method, Strategy y Factory Method
        CAPA 8)  Errores comunes
        RESUMEN FINAL
*/
public class TemplateMethod {

    public static void main(String[] args) {
        check(templateExample(), "CAPA 2");
        check(hooksExample(), "CAPA 3");
        check(compositionExample(), "CAPA 5");
        check(jdkTemplateExample(), "CAPA 6");
        System.out.println("TemplateMethod: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: PROCESOS IGUALES CON UN PASO DISTINTO
        =================================================================

        La tienda importa existencias de sus proveedores. Cada proveedor
        manda su archivo en un formato distinto:
            proveedor A (CSV):          KBD-1,25
            proveedor B (con barras):   KBD-1|25|bodega-norte

        Pero el PROCESO de importar es siempre el mismo:
        1) convertir cada línea en una fila (sku, cantidad);  <- lo ÚNICO que cambia
        2) validar cada fila (cantidad positiva, sku con formato);
        3) guardar las válidas;
        4) armar un reporte: cuántas se importaron y cuáles se rechazaron.

        Si cada proveedor tiene su propio importador completo, los pasos 2,
        3 y 4 se copian en todos. Cuando cambia la validación (ahora el sku
        debe tener 3 letras), hay que encontrar y cambiar cada copia, y
        alguna se olvida (v10_design/part1_basics/BasicPrinciples, CAPA 5).
    */
    record StockRow(String sku, int quantity) { }

    record ImportReport(int imported, List<String> rejected) { }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: EL PROCESO EN LA CLASE BASE, LOS PASOS EN LAS SUBCLASES
        =================================================================

        TEMPLATE METHOD: "Define el esqueleto de un algoritmo en un método,
        delegando algunos pasos a las subclases. Permite que las subclases
        redefinan ciertos pasos sin cambiar la estructura del algoritmo."

        - La clase base (abstracta) tiene el MÉTODO PLANTILLA, importFrom:
          el proceso completo, en orden, escrito UNA vez.
        - Los pasos que varían son métodos abstractos (parseLine) que cada
          subclase implementa.
        - Cada proveedor es una subclase pequeña que SOLO dice cómo leer su
          formato.

        Fíjate en quién llama a quién: la clase base llama a los métodos de
        la subclase, no al revés. A esto se le llama el "principio de
        Hollywood": "no nos llames, nosotros te llamamos". Es exactamente
        cómo funcionan los frameworks: tú llenas los métodos, el framework
        decide cuándo se ejecutan.
    */
    abstract static class StockImporter {
        final List<StockRow> saved = new ArrayList<>();

        /** EL MÉTODO PLANTILLA: el proceso, fijo y final. */
        final ImportReport importFrom(List<String> lines) {
            List<String> rejected = new ArrayList<>();
            int imported = 0;
            for (String line : lines) {
                StockRow row;
                try {
                    row = parseLine(line);                  // paso que varía: lo da la subclase
                } catch (RuntimeException e) {
                    rejected.add(line + " (formato)");
                    continue;
                }
                String problem = validate(row);             // paso común (con un hook; CAPA 3)
                if (problem != null) {
                    rejected.add(line + " (" + problem + ")");
                    continue;
                }
                saved.add(row);
                imported++;
            }
            afterImport(imported);                          // hook opcional (CAPA 3)
            return new ImportReport(imported, rejected);
        }

        /** Paso OBLIGATORIO: cada formato lo implementa. */
        protected abstract StockRow parseLine(String line);

        private String validate(StockRow row) {
            if (!row.sku().matches("[A-Z]{3}-\\d+")) {
                return "sku inválido";
            }
            if (row.quantity() <= 0) {
                return "cantidad inválida";
            }
            return extraValidation(row);
        }

        /** HOOK: por defecto no agrega reglas; una subclase PUEDE sobrescribirlo. */
        protected String extraValidation(StockRow row) {
            return null;
        }

        /** HOOK: por defecto no hace nada al terminar. */
        protected void afterImport(int imported) {
        }
    }

    static final class CsvImporter extends StockImporter {
        @Override
        protected StockRow parseLine(String line) {
            String[] parts = line.split(",");
            return new StockRow(parts[0].strip(), Integer.parseInt(parts[1].strip()));
        }
    }

    static final class PipeImporter extends StockImporter {
        @Override
        protected StockRow parseLine(String line) {
            String[] parts = line.split("\\|");             // | es especial en regex: se escapa
            return new StockRow(parts[0], Integer.parseInt(parts[1]));   // la bodega se ignora
        }
    }

    static boolean templateExample() {
        ImportReport fromA = new CsvImporter().importFrom(List.of("KBD-1, 25", "MOU-2,0", "malformada"));
        ImportReport fromB = new PipeImporter().importFrom(List.of("KBD-1|25|bodega-norte", "kb-9|3|sur"));

        return fromA.imported() == 1
                && fromA.rejected().equals(List.of("MOU-2,0 (cantidad inválida)", "malformada (formato)"))
                && fromB.imported() == 1
                && fromB.rejected().equals(List.of("kb-9|3|sur (sku inválido)"));   // la MISMA validación para ambos
    }

    /*
        =================================================================
        CAPA 3) PASOS OBLIGATORIOS, HOOKS OPCIONALES Y EL MÉTODO FINAL
        =================================================================

        Tres tipos de métodos en la clase base, cada uno con una intención:

        1) El MÉTODO PLANTILLA es final: las subclases NO pueden cambiar el
           proceso (saltarse la validación, cambiar el orden). Si no fuera
           final, una subclase podría sobrescribirlo y el proceso común ya
           no estaría garantizado.

        2) Los pasos ABSTRACTOS son obligatorios: el compilador obliga a cada
           subclase a implementarlos.

        3) Los HOOKS ("ganchos") tienen una implementación por defecto
           (normalmente vacía o neutra); una subclase los sobrescribe SOLO
           si necesita algo extra en ese punto. Así no hay que implementar
           veinte métodos vacíos para cambiar uno.

        Los pasos son protected: son puntos de extensión para las
        subclases, no parte de lo que usan los clientes del importador.

        El ejemplo: un proveedor especial que, además, rechaza cantidades
        mayores a 1,000 (un error típico de su sistema) y avisa al terminar.
        Solo sobrescribe los dos hooks que necesita.
    */
    static final class StrictSupplierImporter extends StockImporter {
        final List<String> notifications = new ArrayList<>();

        @Override
        protected StockRow parseLine(String line) {
            String[] parts = line.split(",");
            return new StockRow(parts[0], Integer.parseInt(parts[1]));
        }

        @Override
        protected String extraValidation(StockRow row) {    // hook: una regla extra solo para este proveedor
            return row.quantity() > 1_000 ? "cantidad sospechosa" : null;
        }

        @Override
        protected void afterImport(int imported) {          // hook: avisar al terminar
            notifications.add("Proveedor estricto: " + imported + " filas");
        }
    }

    static boolean hooksExample() {
        StrictSupplierImporter importer = new StrictSupplierImporter();
        ImportReport report = importer.importFrom(List.of("KBD-1,25", "MOU-2,50000"));
        return report.imported() == 1
                && report.rejected().equals(List.of("MOU-2,50000 (cantidad sospechosa)"))
                && importer.notifications.equals(List.of("Proveedor estricto: 1 filas"));
    }

    /*
        =================================================================
        CAPA 4) LOS COSTOS DE RESOLVERLO CON HERENCIA
        =================================================================

        Template Method usa HERENCIA, y hereda sus problemas
        (v2_oop/part2_pillars/Inheritance, CAPA 10):

        - La variación queda FIJA al escribir la clase: no se puede elegir
          el formato al ejecutar sin crear la subclase correspondiente.
        - COMBINACIONES: si varían dos cosas independientes (el formato Y
          dónde se guarda), aparecen subclases por cada combinación
          (CsvToDatabaseImporter, CsvToFileImporter...; part2_structural/
          Bridge, CAPA 1).
        - CLASE BASE FRÁGIL: la subclase depende de detalles de la base
          (qué hook se llama, en qué momento). Un cambio en la base puede
          romper subclases que nadie revisó.
        - Para PROBAR un paso hay que crear una subclase.
    */

    /*
        =================================================================
        CAPA 5) LA ALTERNATIVA CON COMPOSICIÓN
        =================================================================

        La misma idea, sin herencia: el proceso vive en una clase NORMAL
        (final), y los pasos que varían se le PASAN como objetos o
        funciones, es decir, como estrategias (part3_behavioral/Strategy):

            new Importer(parser, reglaExtra)

        - El parser es una Function<String, StockRow>: puede ser una lambda,
          una referencia a método o una clase.
        - Los "hooks" son parámetros con un valor por defecto.
        - Se combina al ejecutar, sin una subclase por combinación.
        - Cada pieza se prueba sola.

        El ejemplo arma los mismos importadores de las CAPAS 2 y 3 sin
        ninguna subclase, y verifica que dan los mismos resultados.

        Cuándo sigue siendo mejor la herencia: cuando un FRAMEWORK o
        librería te da una clase base para extender (CAPA 6), o cuando los
        pasos están tan relacionados entre sí que conviene tenerlos juntos
        en una clase.
    */
    static final class Importer {
        private final Function<String, StockRow> parser;
        private final Predicate<StockRow> extraRule;        // "hook" como parámetro
        final List<StockRow> saved = new ArrayList<>();

        Importer(Function<String, StockRow> parser, Predicate<StockRow> extraRule) {
            this.parser = parser;
            this.extraRule = extraRule;
        }

        Importer(Function<String, StockRow> parser) {
            this(parser, row -> true);                      // el hook por defecto: sin reglas extra
        }

        int importFrom(List<String> lines) {                // el mismo proceso, en una clase normal
            int imported = 0;
            for (String line : lines) {
                try {
                    StockRow row = parser.apply(line);
                    if (row.sku().matches("[A-Z]{3}-\\d+") && row.quantity() > 0 && extraRule.test(row)) {
                        saved.add(row);
                        imported++;
                    }
                } catch (RuntimeException e) {
                    // formato inválido: la fila se omite (en el real, se reportaría como en la CAPA 2)
                }
            }
            return imported;
        }
    }

    static StockRow parseCsv(String line) {
        String[] parts = line.split(",");
        return new StockRow(parts[0].strip(), Integer.parseInt(parts[1].strip()));
    }

    static boolean compositionExample() {
        Importer csv = new Importer(TemplateMethod::parseCsv);
        Importer strict = new Importer(TemplateMethod::parseCsv, row -> row.quantity() <= 1_000);   // "hook" pasado

        return csv.importFrom(List.of("KBD-1, 25", "MOU-2,0", "malformada"))
                == new CsvImporter().importFrom(List.of("KBD-1, 25", "MOU-2,0", "malformada")).imported()
                && strict.importFrom(List.of("KBD-1,25", "MOU-2,50000")) == 1;
    }

    /*
        =================================================================
        CAPA 6) TEMPLATE METHOD EN EL JDK
        =================================================================

        - java.util.AbstractList: implementas SOLO get(i) y size(), y
          heredas una List completa: iterator, contains, indexOf, equals,
          hashCode, subList, stream... Todos están escritos en la clase
          base en términos de get y size. Es Template Method a gran escala,
          y la forma más rápida de crear una vista de lista sobre tus datos.
          (Lo mismo AbstractMap, AbstractSet, AbstractCollection.)
        - java.io.InputStream: la subclase implementa read() (un byte), y la
          clase base da read(byte[]), readAllBytes(), transferTo()... en
          términos de él (una subclase eficiente sobrescribe también la
          versión por bloques; v8_apis/part2_io_and_network/FilesAndIO,
          CAPA 10).
        - En los frameworks web: una clase base recibe la petición y llama a
          TU método según el tipo (doGet, doPost).

        El ejemplo crea una lista de solo lectura con los números de folio
        del 1 al N sin guardarlos: dos métodos, y es una List completa.
    */
    static final class FolioRange extends AbstractList<String> {
        private final int count;

        FolioRange(int count) {
            this.count = count;
        }

        @Override
        public String get(int index) {                      // paso 1 que la base necesita
            return "F-" + (index + 1);
        }

        @Override
        public int size() {                                 // paso 2
            return count;
        }
    }

    static boolean jdkTemplateExample() {
        List<String> folios = new FolioRange(1_000_000);    // un millón de folios, sin guardar ninguno
        return folios.contains("F-500000")                  // contains: heredado, usa get y size
                && folios.indexOf("F-3") == 2
                && folios.subList(0, 2).equals(List.of("F-1", "F-2"))
                && folios.stream().skip(999_998).toList().equals(List.of("F-999999", "F-1000000"));
    }

    /*
        =================================================================
        CAPA 7) TEMPLATE METHOD, STRATEGY Y FACTORY METHOD
        =================================================================

        | Patrón          | Qué varía                                  | Cómo                              |
        |-----------------|--------------------------------------------|-----------------------------------|
        | Template Method | algunos PASOS de un proceso fijo           | subclases (herencia)              |
        | Strategy        | el algoritmo COMPLETO                      | un objeto que se pasa (composición)|
        | Factory Method  | QUÉ objeto se crea en un paso del proceso  | subclases: es un Template Method  |
        |                 | (part1_creational/FactoryMethod, CAPA 6)   | cuyo paso es crear                |

        La CAPA 5 muestra que un Template Method siempre se puede convertir
        en un proceso que recibe Strategies para sus pasos.
    */

    /*
        =================================================================
        CAPA 8) ERRORES COMUNES
        =================================================================

        1) Copiar el proceso completo en cada variante (CAPA 1).

        2) No hacer final el método plantilla: una subclase cambia el
           proceso y se salta pasos (CAPA 3).

        3) Pasos que deberían ser hooks declarados abstractos: cada subclase
           implementa métodos vacíos (CAPA 3).

        4) Jerarquías profundas o con una subclase por combinación de
           variaciones (CAPA 4).

        5) Usar herencia en código propio cuando pasar los pasos como
           funciones era más simple (CAPA 5).

        6) Escribir una List desde cero en lugar de extender AbstractList
           (CAPA 6).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Template Method: el proceso fijo en un método final de la clase
          base; los pasos que varían, en métodos que las subclases
          implementan. "No nos llames, nosotros te llamamos".
        - Pasos abstractos (obligatorios) y hooks con valor por defecto
          (opcionales), protected.
        - Costos de la herencia: fijo al escribir, combinaciones, clase base
          frágil.
        - Alternativa moderna: un proceso que recibe sus pasos como
          funciones/estrategias.
        - En el JDK: AbstractList (get + size = una List completa),
          InputStream, y las clases base de los frameworks.
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
