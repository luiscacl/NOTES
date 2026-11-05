package v2_oop.part1_basics;

import java.math.BigDecimal;

import v2_oop.part1_basics.modifiers.store.Warehouse;
import v2_oop.part1_basics.modifiers.store.Product;
import v2_oop.part1_basics.modifiers.sales.ImportedProduct;
import v2_oop.part1_basics.modifiers.sales.Sale;

/*
    =====================================================================
    MODIFICADORES DE ACCESO (Y FINAL) — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 3 de 14  (part1_basics)
    Anterior:  part1_basics/Static
    Siguiente: part2_pillars/Encapsulation

    Requisito: ClassesAndObjects y Static.

    Esta guía es especial: el acceso solo se puede demostrar DE VERDAD
    entre paquetes distintos. Por eso usa clases que viven en dos
    subpaquetes, cada una en su propio archivo:

        v2_oop/part1_basics/
        ├── AccessModifiers.java        <- esta guía (paquete v2_oop.part1_basics)
        └── modifiers/
            ├── store/                        (paquete v2_oop.part1_basics.modifiers.store)
            │   ├── Product.java              usa los 4 niveles de acceso
            │   ├── Warehouse.java               mismo paquete que Product
            │   └── MovementLog.java clase SIN public
            └── sales/                        (paquete v2_oop.part1_basics.modifiers.sales)
                ├── Sale.java                 otro paquete, NO hereda
                └── ImportedProduct.java     otro paquete, SÍ hereda

    Lee esta guía y abre cada archivo cuando se mencione. Las líneas que
    NO compilan están comentadas y marcadas con ERROR: aquí sí son
    errores reales, porque esta clase está en otro paquete.

    Para ejecutarla (desde la raíz del proyecto):
        java v2_oop/part1_basics/AccessModifiers.java
    (Java 22+ encuentra y compila solo las clases de los subpaquetes.)

    Índice:
        CAPA 1)  Qué controlan los modificadores de acceso
        CAPA 2)  Paquetes: la frontera del acceso
        CAPA 3)  Los cuatro niveles
        CAPA 4)  public: la API que prometes
        CAPA 5)  private: el interior de la clase
        CAPA 6)  Acceso de paquete: el interior del paquete
        CAPA 7)  protected: el acuerdo con las hijas
        CAPA 8)  Acceso en clases y constructores
        CAPA 9)  Cómo elegir el nivel correcto
        CAPA 10) final: lo que no puede cambiar
        CAPA 11) Mapa de TODOS los modificadores de Java
        CAPA 12) Cómo leer cualquier declaración
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class AccessModifiers {

    public static void main(String[] args) {
        check(publicApiExample(), "CAPA 4");
        check(packageAccessExample(), "CAPA 6");
        check(protectedExample(), "CAPA 7");
        check(finalExample(), "CAPA 10");
        System.out.println("AccessModifiers: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ CONTROLAN LOS MODIFICADORES DE ACCESO
        =================================================================

        Un modificador de acceso es una palabra delante de una
        declaración que decide DESDE DÓNDE se puede usar.

            public    String getSku()
            private   int stock;
            protected BigDecimal tax()

        Analogía: un edificio de oficinas.
        - La recepción está abierta a cualquiera           -> public
        - Los pasillos internos, solo para los empleados
          del edificio                                     -> de paquete
        - Ciertas salas, para empleados y para los
          contratistas de la empresa matriz                -> protected
        - Tu escritorio con llave, solo para ti            -> private

        ¿Para qué sirve restringir? Lo que NADIE de fuera puede usar,
        lo puedes cambiar cuando quieras sin romper nada. Lo que es
        public, otros ya dependen de ello y cambiarlo los rompe.
        Restringir acceso = conservar tu libertad de cambiar.

        Importante: el acceso lo revisa el COMPILADOR. Usar algo sin
        acceso no es un error al ejecutar: el programa ni siquiera compila.
    */

    /*
        =================================================================
        CAPA 2) PAQUETES: LA FRONTERA DEL ACCESO
        =================================================================

        Un PAQUETE es un grupo de clases relacionadas. Se declara en la
        primera línea del archivo y DEBE coincidir con las carpetas:

            archivo: v2_oop/part1_basics/modifiers/store/Product.java
            primera línea: package v2_oop.part1_basics.modifiers.store;

        Para usar una clase de otro paquete, se importa:
            import v2_oop.part1_basics.modifiers.store.Product;

        Convenciones:
        - Nombres en minúsculas, separados por puntos.
        - En empresas se usa el dominio al revés para que sea único en el
          mundo: com.empresa.proyecto.modulo
        - Las clases de java.lang (String, Math, Object) se importan
          solas. Las clases del mismo paquete no necesitan import.

        Detalle técnico clave: para Java, v2_oop.part1_basics.modifiers.store
        y v2_oop.part1_basics.modifiers.sales son paquetes COMPLETAMENTE
        DISTINTOS.
        Que uno "parezca estar dentro" del otro por las carpetas no da
        ningún acceso especial. Tampoco v2_oop.part1_basics (el paquete de esta
        guía) tiene acceso especial a sus subpaquetes.
    */

    /*
        =================================================================
        CAPA 3) LOS CUATRO NIVELES
        =================================================================

        De más cerrado a más abierto:

        | Modificador        | Misma clase | Mismo paquete | Hija en otro paquete | Cualquiera |
        |--------------------|-------------|---------------|----------------------|------------|
        | private            | Sí          | No            | No                   | No         |
        | (sin modificador)  | Sí          | Sí            | No                   | No         |
        | protected          | Sí          | Sí            | Sí (con una regla)   | No         |
        | public             | Sí          | Sí            | Sí                   | Sí         |

        El nivel sin modificador se llama "de paquete" (package-private
        o default). No existe la palabra "default" para esto: se logra
        NO escribiendo nada.

        Fíjate en un detalle que confunde: protected es MÁS abierto que
        el acceso de paquete, no menos. protected = todo el paquete +
        las hijas de fuera.
    */

    /*
        =================================================================
        CAPA 4) PUBLIC: LA API QUE PROMETES
        =================================================================

        public = cualquier clase, de cualquier paquete, lo puede usar.

        Lo public de una clase es su API (su "contrato con el mundo"):
        una promesa. Una vez que otros lo usan, cambiarlo o quitarlo los
        rompe. Por eso se publica lo MÍNIMO necesario.

        Desde aquí (paquete v2_oop.part1_basics) Product solo muestra lo public:
        el constructor, getSku, getStock, finalPrice y VAT_RATE.
    */
    static boolean publicApiExample() {
        Product libro = new Product("LIB-001", new BigDecimal("100"), 10);

        boolean readable = libro.getSku().equals("LIB-001") && libro.getStock() == 10;
        boolean price = libro.finalPrice().compareTo(new BigDecimal("116.00")) == 0; // 100 + 16 % de IVA

        // Sale también está en otro paquete y solo usa lo public de Product.
        boolean sale = new Sale(libro, 3).total().compareTo(new BigDecimal("348.00")) == 0;

        return readable && price && sale;
    }

    /*
        =================================================================
        CAPA 5) PRIVATE: EL INTERIOR DE LA CLASE
        =================================================================

        private = solo el código escrito DENTRO de esa clase lo puede usar.
        Ni otras clases del paquete, ni las hijas.

        Desde aquí, ninguna de estas líneas compila:

            libro.stock = -5;            // ERROR: stock has private access in Product
            libro.basePrice;            // ERROR
            Product.validateStock(3);    // ERROR

        Lo que debes saber de private:
        - Es el nivel POR DEFECTO para todo campo. Las razones están en
          Encapsulation.
        - Es por CLASE, no por objeto: dentro de Product se puede leer
          el campo private de OTRO Product (Encapsulation, CAPA 3).
        - Los campos private de un padre SÍ existen dentro del objeto
          hijo; lo que la hija no tiene es acceso directo
          (Inheritance, CAPA 3).
        - Excepción técnica: una clase y sus clases anidadas comparten
          acceso a sus private. Por eso, en las guías anteriores, los
          ejemplos anidados podían tocar private "desde fuera". En
          archivos separados, como aquí, no.
    */

    /*
        =================================================================
        CAPA 6) ACCESO DE PAQUETE: EL INTERIOR DEL PAQUETE
        =================================================================

        Sin modificador = solo las clases del MISMO paquete lo pueden
        usar.

        Es el nivel ideal para colaboraciones INTERNAS: clases de un
        mismo paquete que trabajan juntas y se necesitan entre sí, pero
        cuyo detalle no le importa al resto del sistema.

        En el ejemplo (abre store/Warehouse.java):
        - Product.adjustStock(int) no tiene modificador. Solo Warehouse,
          que vive en el mismo paquete, puede llamarlo.
        - MovementLog es una clase sin public. Fuera de su
          paquete, ni siquiera se puede nombrar.

        Resultado: la ÚNICA forma de mover el stock desde fuera del
        paquete es Warehouse.receive/dispatch, y ahí cada movimiento queda
        registrado. Nadie puede saltarse el movementLog.

        Desde aquí:
            libro.adjustStock(5);                  // ERROR: not public in Product;
                                                    //        cannot be accessed from outside package
            new MovementLog();            // ERROR: cannot find symbol
    */
    static boolean packageAccessExample() {
        Product libro = new Product("LIB-002", new BigDecimal("250"), 5);
        Warehouse almacen = new Warehouse();

        almacen.receive(libro, 10);   // stock 15
        almacen.dispatch(libro, 4);  // stock 11
        boolean stockMoved = libro.getStock() == 11 && almacen.movementCount() == 2;

        boolean ruleProtected;
        try {
            almacen.dispatch(libro, 100); // dejaría el stock negativo
            ruleProtected = false;
        } catch (IllegalStateException e) {
            ruleProtected = true;
        }

        return stockMoved && ruleProtected && libro.getStock() == 11;
    }

    /*
        El acceso de paquete se subestima mucho. Muchos programadores
        solo usan private y public, y terminan haciendo public cosas que
        eran internas "porque otra clase del paquete lo necesitaba".
        Organizar los paquetes por FUNCIONALIDAD (store, sales) en vez
        de por tipo técnico (models, services, utils) permite que muchas
        clases sean de paquete y la API pública quede pequeña.
    */

    /*
        =================================================================
        CAPA 7) PROTECTED: EL ACUERDO CON LAS HIJAS
        =================================================================

        protected = todo el paquete + las clases hijas, aunque estén en
        otro paquete.

        Sirve para ofrecer PUNTOS DE EXTENSIÓN: cosas que solo tienen
        sentido para quien hereda. En Product:
        - tax() es protected: una hija puede sobrescribirlo para
          cambiar cómo se calcula el impuesto.
        - getBasePrice() es protected y final: una herramienta para
          las hijas, que no pueden cambiar.
        - finalPrice() es public y final: todos lo usan, y ninguna hija
          puede cambiar la fórmula; solo el impuesto que usa.

        ImportedProduct (abre sales/ImportedProduct.java) está en
        otro paquete pero hereda de Product, así que puede sobrescribir
        tax() y usar getBasePrice().
    */
    static boolean protectedExample() {
        Product importado = new ImportedProduct("IMP-001", new BigDecimal("100"), 5, new BigDecimal("0.10"));

        // 100 + 16 de IVA + 10 de arancel. finalPrice (public) usa el tax() de la hija.
        return importado.finalPrice().compareTo(new BigDecimal("126.00")) == 0;

        // importado.tax();   // ERROR desde aquí: esta clase no es hija de Product
        //                         // ni está en su paquete.
    }

    /*
        Tres reglas técnicas de protected:

        1) La regla fina (explicada con código en ImportedProduct.java):
           una hija de otro paquete puede usar lo protected de sí misma
           y de objetos de SU tipo, pero NO lo protected de un objeto
           del tipo padre cualquiera.

        2) Al sobrescribir se puede AMPLIAR el acceso (protected ->
           public) pero nunca reducirlo (public -> protected no compila).
           Quien usa al padre debe poder llamar al método en cualquier
           hija (Polymorphism / Inheritance, CAPA 5).

        3) protected también abre el acceso a TODO el paquete. Un campo
           protected es casi tan expuesto como uno public. Por eso los
           CAMPOS deben ser private incluso en clases hechas para
           heredarse; lo protected deben ser MÉTODOS controlados
           (Encapsulation, CAPA 10).
    */

    /*
        =================================================================
        CAPA 8) ACCESO EN CLASES Y CONSTRUCTORES
        =================================================================

        CLASES DE NIVEL SUPERIOR (las que no están dentro de otra):
        solo pueden ser public o de paquete. private y protected no
        tienen sentido ahí y no compilan.

            public class Product            -> visible desde cualquier paquete
            final class MovementLog -> solo visible en su paquete

        Regla del archivo: un archivo .java puede tener VARIAS clases de
        nivel superior, pero solo UNA public, y esa debe llamarse
        exactamente igual que el archivo. (En la práctica: una clase por
        archivo.)

        CLASES ANIDADAS: pueden usar los cuatro niveles, como cualquier
        miembro (NestedClasses).

        CONSTRUCTORES: el acceso del constructor decide QUIÉN PUEDE
        CREAR objetos:
        - public     -> cualquiera (Product)
        - de paquete -> solo el paquete; útil cuando una fábrica del
                        mismo paquete debe controlar la creación
        - protected  -> el paquete y las hijas (común en clases abstractas)
        - private    -> nadie de fuera: clases utilitarias y fábricas
                        estáticas (Static, CAPAS 5 y 6)
    */

    /*
        =================================================================
        CAPA 9) CÓMO ELEGIR EL NIVEL CORRECTO
        =================================================================

        La regla de Effective Java: "minimiza la accesibilidad de clases
        y miembros". Empieza siempre por lo más cerrado y abre solo
        cuando haya una razón concreta, en este orden:

        1) ¿Solo lo usa esta clase?                       -> private
        2) ¿Lo necesitan otras clases del mismo paquete?  -> de paquete
        3) ¿Es un punto de extensión para hijas?          -> protected
                                                             (método, no campo)
        4) ¿Es parte de lo que la clase ofrece al mundo?  -> public

        Para cada nivel, la pregunta es: "si mañana cambio esto, ¿a
        quién rompo?". private: a nadie. De paquete: solo a mi paquete,
        que controlo. protected: a cualquier hija, incluso de otros
        equipos. public: a todo el mundo.

        Más allá de public: con el sistema de módulos
        (v12_jvm/part2_classes/Modules), un paquete public puede quedar
        oculto para otros módulos si no se exporta. Es el nivel de encapsulación de librerías completas.
    */

    /*
        =================================================================
        CAPA 10) FINAL: LO QUE NO PUEDE CAMBIAR
        =================================================================

        final no es de acceso, pero se usa junto a ellos todo el tiempo.
        Significa algo distinto según dónde se pone:

        | Dónde                 | Significa                          | A fondo en              |
        |-----------------------|------------------------------------|-------------------------|
        | variable local        | no se puede reasignar              | aquí                    |
        | parámetro             | no se puede reasignar              | aquí                    |
        | campo                 | se asigna una vez (al construir)   | Encapsulation, CAPA 7   |
        | static final          | constante                          | Static, CAPA 3          |
        | método                | no se puede sobrescribir           | Inheritance, CAPA 8     |
        | clase                 | no se puede heredar                | Inheritance, CAPA 8     |

        Y lo que final NO hace: no vuelve inmutable al objeto. Solo fija
        la variable (Encapsulation, CAPA 7).

        Reglas técnicas de los campos final:
        - Deben quedar asignados al terminar CADA constructor, por
          cualquier camino. Si un if deja uno sin asignar, no compila.
        - No se pueden asignar dos veces.
    */
    static final class Receipt {
        private final String folio;
        private final BigDecimal total;

        Receipt(String folio, BigDecimal total) {
            this.folio = folio;
            this.total = total;
            // this.folio = "otro";   // ERROR: variable folio might already have been assigned
        }

        // final en un parámetro: dentro del método no se puede reasignar.
        Receipt withDiscount(final BigDecimal discount) {
            // discount = BigDecimal.ZERO;   // ERROR: final parameter discount may not be assigned
            final BigDecimal discounted = total.subtract(discount); // final en variable local
            return new Receipt(folio, discounted);
        }

        BigDecimal getTotal() {
            return total;
        }
    }

    static boolean finalExample() {
        Receipt receipt = new Receipt("F-0001", new BigDecimal("500"));
        Receipt discounted = receipt.withDiscount(new BigDecimal("50"));

        // final en campos: el recibo original no cambió; withDiscount creó otro.
        return receipt.getTotal().compareTo(new BigDecimal("500")) == 0
                && discounted.getTotal().compareTo(new BigDecimal("450")) == 0;
    }

    /*
        ¿Vale la pena final en variables locales y parámetros?
        Es cuestión de estilo de cada equipo. Lo que sí importa: una
        variable que nunca se reasigna se llama "efectivamente final", y
        eso es lo que exigen las lambdas para usar variables de fuera
        (v7_functional/part1_lambdas/Lambdas). Escribir final solo lo hace explícito.

        abstract (el otro modificador "de diseño") está explicado a
        fondo en Abstraction: una clase abstract no se instancia y
        un método abstract no tiene cuerpo y obliga a las hijas a
        implementarlo.
    */

    /*
        =================================================================
        CAPA 11) MAPA DE TODOS LOS MODIFICADORES DE JAVA
        =================================================================

        Para que nada te tome por sorpresa, esta es la lista completa,
        agrupada por la pregunta que responde cada uno:

        ¿QUIÉN LO PUEDE USAR? (acceso)
            public, protected, private, (nada)         -> esta guía

        ¿ES DE LA CLASE O DEL OBJETO? (pertenencia)
            static                                     -> Static

        ¿PUEDE CAMBIAR O EXTENDERSE? (restricción)
            final                                      -> esta guía, CAPA 10
            sealed, non-sealed                         -> Inheritance, CAPA 8

        ¿ESTÁ INCOMPLETO A PROPÓSITO?
            abstract                                   -> Abstraction

        ¿CÓMO SE COMPORTA CON VARIOS HILOS?
            synchronized, volatile                     -> v13_concurrency/

        ¿SE GUARDA AL SERIALIZAR?
            transient                                  -> v8_apis/part2_io_and_network/Serialization

        ¿ESTÁ ESCRITO EN OTRO LENGUAJE?
            native                                     -> v12_jvm/ (raro en código de aplicación)

        OBSOLETO
            strictfp (sin efecto desde Java 17)

        ¿DÓNDE SE PERMITE CADA UNO?
        | Declaración      | Modificadores permitidos                                      |
        |------------------|---------------------------------------------------------------|
        | clase superior   | public, abstract, final, sealed, non-sealed                   |
        | clase anidada    | los 4 de acceso, static, abstract, final, sealed, non-sealed  |
        | campo            | los 4 de acceso, static, final, transient, volatile           |
        | método           | los 4 de acceso, static, final, abstract, synchronized, native |
        | constructor      | solo los 4 de acceso                                          |
        | variable local   | solo final                                                    |
        | parámetro        | solo final                                                    |
    */

    /*
        =================================================================
        CAPA 12) CÓMO LEER CUALQUIER DECLARACIÓN
        =================================================================

        Cualquier declaración de Java sigue esta estructura:

            Campo:        [acceso] [static] [final] Tipo nombre
            Método:       [acceso] [static] [final | abstract] TipoRetorno nombre(parámetros)
            Clase:        [acceso] [final | abstract | sealed] class Nombre
            Constructor:  [acceso] Nombre(parámetros)

        Orden recomendado cuando hay varios (el que usa el JDK):
            public protected private abstract static final transient volatile synchronized native

            public static final int MAX_STOCK = 1000;     // bien
            final static public int MAX_STOCK = 1000;     // compila, pero nadie lo escribe así

        Las preguntas que te haces antes de declarar algo (de tus notas
        en Modifiers/Main.java):
        1) ¿Qué estoy declarando? (clase, campo, método, constructor)
        2) ¿Qué tipo es o qué devuelve?
        3) ¿Quién lo debe poder usar?            -> modificador de acceso
        4) ¿Es del objeto o de la clase?         -> static o no
        5) ¿Debe poder cambiar?                  -> final o no
        6) ¿Está incompleto a propósito?         -> abstract o no
        7) ¿Se usa desde fuera o solo por dentro? -> vuelve a la 3, empezando por private
    */

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) Hacer todo public "para que funcione".
           Cada public es una promesa que ya no puedes cambiar (CAPAS 4 y 9).

        2) Campos public o protected.
           Los campos van private; se exponen métodos (CAPAS 5 y 7).

        3) Creer que protected es más cerrado que el acceso de paquete.
           Es al revés: protected incluye todo el paquete (CAPA 3).

        4) Creer que un subpaquete tiene acceso a su paquete "padre".
           Para Java son paquetes sin relación (CAPA 2).

        5) Olvidar el acceso de paquete y usar public para colaboraciones
           internas (CAPA 6).

        6) Pensar que heredar da acceso a lo de paquete o a lo private.
           Solo da acceso a protected y public (CAPA 7).

        7) Usar lo protected de un objeto del tipo padre desde una hija
           de otro paquete (la regla fina, CAPA 7).

        8) Reducir el acceso al sobrescribir un método (CAPA 7).

        9) Creer que final hace inmutable al objeto (CAPA 10).

        10) Poner dos clases public en un archivo, o una clase public con
            nombre distinto al del archivo (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Los modificadores de acceso deciden desde dónde se usa algo; los
          revisa el compilador.
        - private: solo la clase. De paquete: el paquete. protected: el
          paquete y las hijas. public: todos.
        - El paquete es la frontera: debe coincidir con las carpetas, y
          los subpaquetes no tienen acceso especial.
        - public es una promesa; publica lo mínimo.
        - El acceso de paquete es ideal para colaboración interna.
        - protected: para métodos de extensión, nunca para campos.
        - Clases de nivel superior: public o de paquete; una public por
          archivo, con su nombre.
        - El constructor decide quién crea objetos.
        - Elige siempre el nivel más cerrado que funcione.
        - final fija variables, métodos y clases; no hace inmutable al
          objeto.
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
