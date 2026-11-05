package v2_oop.part2_pillars;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/*
    =====================================================================
    HERENCIA EN JAVA — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 5 de 14  (part2_pillars)
    Anterior:  part2_pillars/Encapsulation
    Siguiente: part2_pillars/Polymorphism

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 0)  Vocabulario mínimo
        CAPA 1)  Qué es la herencia: la relación "ES UN"
        CAPA 2)  El problema: código duplicado entre tipos parecidos
        CAPA 3)  extends: qué se hereda y qué no
        CAPA 4)  Constructores y super(...)
        CAPA 5)  Sobrescritura de métodos (overriding)
        CAPA 6)  Ocultamiento: campos y métodos static
        CAPA 7)  Object: la raíz de todas las clases
        CAPA 8)  Controlar quién hereda: final y sealed
        CAPA 9)  Formas de jerarquía y por qué no hay herencia múltiple
        CAPA 10) Los peligros reales de la herencia
        CAPA 11) Herencia vs composición: cómo decidir
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class Inheritance {

    public static void main(String[] args) {
        check(inheritedMembersExample(), "CAPA 3");
        check(constructorsExample(), "CAPA 4");
        check(overridingExample(), "CAPA 5");
        check(hidingExample(), "CAPA 6");
        check(objectRootExample(), "CAPA 7");
        check(sealedExample() == 60, "CAPA 8");
        check(diamondExample().equals("nadar y correr"), "CAPA 9");
        check(fragileBaseClassExample(), "CAPA 10 (clase base frágil)");
        check(liskovExample() == 16, "CAPA 10 (Liskov)");
        System.out.println("Inheritance: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 0) VOCABULARIO MÍNIMO
        =================================================================

        - CLASE PADRE (superclase, clase base): la clase más general.
        - CLASE HIJA (subclase, clase derivada): la clase más específica,
          que hereda de la padre.
        - extends: la palabra con la que una hija declara a su padre.
              class Manager extends Employee
        - JERARQUÍA: el "árbol familiar" de clases que heredan unas de
          otras.
        - MIEMBRO: cualquier cosa declarada dentro de una clase: campos,
          métodos, constructores, clases anidadas.
    */

    /*
        =================================================================
        CAPA 1) QUÉ ES LA HERENCIA: LA RELACIÓN "ES UN"
        =================================================================

        Heredar es crear una clase nueva a partir de otra existente.
        La hija recibe automáticamente lo que la padre ya sabe hacer, y
        puede AGREGAR cosas nuevas o CAMBIAR algunas que heredó.

        Ejemplo de la vida real: en una empresa, todo gerente ES UN
        empleado. Tiene nombre, número de empleado y sueldo, como
        cualquier empleado. Pero además tiene un bono. No tiene sentido
        volver a describir desde cero qué es un empleado para describir
        a un gerente: describes solo lo que lo hace DIFERENTE.

        La única pregunta que justifica usar herencia:

            "¿La hija ES UN tipo de la padre, SIEMPRE y en TODO contexto?"

            Gerente   ES UN empleado      -> herencia tiene sentido
            Ingeniero ES UN empleado      -> herencia tiene sentido
            Empleado  TIENE UNA dirección -> NO es herencia, es composición
            Coche     TIENE UN motor      -> NO es herencia, es composición

        Si la frase natural es "TIENE UN", la respuesta es composición
        (guardar el otro objeto como campo). La CAPA 11 profundiza.

        Para qué sirve la herencia:
        1) Reutilizar código común sin copiarlo.
        2) Crear jerarquías de TIPOS: un Manager se puede usar en
           cualquier lugar donde se espere un Employee. Esta es la razón
           más importante, y es la base del polimorfismo
           (ver Polymorphism.java).
    */

    /*
        =================================================================
        CAPA 2) EL PROBLEMA: CÓDIGO DUPLICADO ENTRE TIPOS PARECIDOS
        =================================================================

        Sin herencia, un ingeniero y un gerente se escribirían así:

            class EngineerWithoutInheritance {
                private final String id;          // repetido
                private final String name;        // repetido
                private final BigDecimal salary;  // repetido
                ...validación de id, nombre y salario... // repetida
                private final String primaryLanguage;
            }

            class ManagerWithoutInheritance {
                private final String id;          // repetido
                private final String name;        // repetido
                private final BigDecimal salary;  // repetido
                ...validación de id, nombre y salario... // repetida
                private final BigDecimal bonusRate;
            }

        (Está en comentario solo para no llenar la guía de código
        repetido; ese es justamente el problema.)

        Problemas:
        - Cada regla común (el salario no puede ser negativo) existe en
          N lugares. Cambiarla significa encontrarlos todos.
        - No hay un tipo común: no puedes tener una lista de "empleados"
          que mezcle ingenieros y gerentes para calcular la nómina.

        La herencia resuelve las dos cosas: lo común se escribe UNA vez
        en Employee, y Engineer y Manager SON Employee.
    */

    /*
        =================================================================
        CAPA 3) EXTENDS: QUÉ SE HEREDA Y QUÉ NO
        =================================================================

        La clase padre. Tiene todo lo que comparten los empleados.
        Sus campos son private (ver Encapsulation.java, CAPA 10): las
        hijas los usan a través de métodos, no directamente.
    */
    static class Employee {
        private final String id;
        private final String name;
        private final BigDecimal baseSalary;

        Employee(String id, String name, BigDecimal baseSalary) {
            Objects.requireNonNull(baseSalary, "baseSalary");
            if (baseSalary.signum() < 0) {
                throw new IllegalArgumentException("El salario no puede ser negativo");
            }
            this.id = requireText(id, "id");
            this.name = requireText(name, "name");
            this.baseSalary = baseSalary;
        }

        String getId() {
            return id;
        }

        String getName() {
            return name;
        }

        BigDecimal getBaseSalary() {
            return baseSalary;
        }

        // Pago mensual. Las hijas pueden sobrescribirlo (CAPA 5).
        BigDecimal monthlyPay() {
            return baseSalary;
        }

        /*
            Este método llama a monthlyPay(). Si el objeto real es un
            Manager, se ejecutará el monthlyPay() de Manager, no este.
            (Se demuestra en la CAPA 5.)
        */
        final BigDecimal payForMonths(int months) {
            return monthlyPay().multiply(BigDecimal.valueOf(months));
        }

        /*
            protected: visible para las hijas (y el mismo paquete), no
            para el resto. Es una herramienta PARA las hijas, no parte de
            la cara pública del empleado.
        */
        protected final BigDecimal percentageOfBase(BigDecimal rate) {
            return baseSalary.multiply(rate);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + id + ", " + name + "]";
        }
    }

    /*
        La hija. Con "extends Employee" recibe todo lo heredable, y solo
        declara lo que la hace distinta: su lenguaje principal.
    */
    static class Engineer extends Employee {
        private final String primaryLanguage;

        Engineer(String id, String name, BigDecimal baseSalary, String primaryLanguage) {
            super(id, name, baseSalary);   // la parte Employee la construye Employee (CAPA 4)
            this.primaryLanguage = requireText(primaryLanguage, "primaryLanguage");
        }

        String getPrimaryLanguage() {
            return primaryLanguage;
        }
    }

    static boolean inheritedMembersExample() {
        Engineer ana = new Engineer("E-001", "Ana", new BigDecimal("40000"), "Java");

        // getName() y monthlyPay() NO están escritos en Engineer: vienen de Employee.
        return ana.getName().equals("Ana")
                && ana.getPrimaryLanguage().equals("Java")
                && same(ana.monthlyPay(), "40000");
    }

    /*
        Qué recibe exactamente una hija:

        | Miembro del padre               | ¿Se hereda?  | ¿La hija lo usa directo? |
        |---------------------------------|--------------|--------------------------|
        | public                          | Sí           | Sí                       |
        | protected                       | Sí           | Sí                       |
        | sin modificador (de paquete)    | Sí           | Solo si está en el mismo paquete |
        | private                         | EXISTE en el objeto | No. Solo vía métodos del padre |
        | constructores                   | NO           | Solo llamándolos con super(...) |
        | static                          | Accesible    | Pertenece a la CLASE, no al objeto (CAPA 6) |

        El punto que más se malinterpreta: los campos private del padre
        SÍ están dentro de cada objeto Engineer (Ana tiene id, name y
        baseSalary guardados). Lo que no existe es ACCESO directo desde
        el código de Engineer. Por eso Engineer no puede escribir
        this.name, pero sí getName().

        Cómo se ve en memoria UN objeto Engineer:

            objeto Ana (uno solo)
            ├── parte Employee:  id="E-001", name="Ana", baseSalary=40000
            └── parte Engineer:  primaryLanguage="Java"

        No hay un objeto Employee por un lado y otro Engineer por otro.
        Es UN objeto con las capas de toda su jerarquía.
    */

    /*
        =================================================================
        CAPA 4) CONSTRUCTORES Y SUPER(...)
        =================================================================

        Regla 1: LOS CONSTRUCTORES NO SE HEREDAN.
        Cada clase construye SU parte del objeto. Employee sabe validar
        id, nombre y salario; Engineer sabe validar su lenguaje.

        Regla 2: ANTES de que corra el cuerpo del constructor de la hija,
        SIEMPRE se ejecuta un constructor de la padre. Es decir, el
        objeto se construye de ARRIBA hacia ABAJO: primero la parte más
        general, al final la más específica. Tiene lógica: la hija puede
        necesitar usar lo que la padre ya dejó listo.

        super(argumentos) es la instrucción que elige QUÉ constructor de
        la padre ejecutar y con qué datos.

        Para ver el orden sin imprimir nada, estas tres clases anotan su
        nombre en una lista al construirse:
    */
    static class Vehicle {
        Vehicle(List<String> constructionLog) {
            constructionLog.add("Vehicle");
        }
    }

    static class Car extends Vehicle {
        Car(List<String> constructionLog) {
            super(constructionLog);
            constructionLog.add("Car");
        }
    }

    static final class ElectricCar extends Car {
        ElectricCar(List<String> constructionLog) {
            super(constructionLog);
            constructionLog.add("ElectricCar");
        }
    }

    /*
        Regla 3: si NO escribes super(...), Java inserta super() (sin
        argumentos) por ti. Y si una clase no declara NINGÚN constructor,
        Java genera uno vacío que solo hace super().

        Ejemplo: Invoice no declara constructor. Java genera:
            Invoice() { super(); }
        y por eso la parte AuditableRecord igual queda inicializada.
    */
    static class AuditableRecord {
        private final String createdBy;

        AuditableRecord() {
            this.createdBy = "system";
        }

        String getCreatedBy() {
            return createdBy;
        }
    }

    static final class Invoice extends AuditableRecord {
        // sin constructor a propósito
    }

    /*
        Regla 4: si la padre NO tiene constructor sin argumentos (como
        Employee), el super() automático no encuentra a quién llamar y
        el código NO COMPILA. Estás obligado a escribir super(...) con
        los argumentos correctos.

            class Intern extends Employee {
                Intern() { }        // ERROR: Employee() no existe
            }

        Regla 5 (Java 25+): ANTES de super(...) puedes escribir código
        que NO lea el objeto que se está construyendo (no puede leer
        this ni llamar a sus métodos; sí puede asignar sus propios campos). Sirve para validar argumentos
        antes de construir la parte del padre. En versiones anteriores,
        super(...) tenía que ser obligatoriamente la primera línea.
    */
    static class Manager extends Employee {
        private final BigDecimal bonusRate;   // 0.20 = 20 % del salario base

        Manager(String id, String name, BigDecimal baseSalary, BigDecimal bonusRate) {
            Objects.requireNonNull(bonusRate, "bonusRate");
            if (bonusRate.signum() < 0 || bonusRate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("El bono debe estar entre 0 y 1");
            }
            super(id, name, baseSalary);   // permitido después de validar, desde Java 25
            this.bonusRate = bonusRate;
        }

        /*
            Sobrescritura (CAPA 5): el gerente cobra su base MÁS un bono.
            super.monthlyPay() ejecuta la versión de Employee; así la
            hija EXTIENDE el comportamiento del padre en lugar de
            reescribirlo desde cero.
        */
        @Override
        BigDecimal monthlyPay() {
            return super.monthlyPay().add(percentageOfBase(bonusRate));
        }
    }

    static boolean constructorsExample() {
        List<String> log = new ArrayList<>();
        new ElectricCar(log);
        boolean topDown = log.equals(List.of("Vehicle", "Car", "ElectricCar"));

        boolean implicitSuperRan = new Invoice().getCreatedBy().equals("system");

        boolean validatedBeforeSuper;
        try {
            new Manager("M-001", "Laura", new BigDecimal("50000"), new BigDecimal("3")); // bono de 300 %
            validatedBeforeSuper = false;
        } catch (IllegalArgumentException e) {
            validatedBeforeSuper = true;
        }

        return topDown && implicitSuperRan && validatedBeforeSuper;
    }

    /*
        Los tres usos de super:
        1) super(...)        llama al constructor del padre.
        2) super.metodo()    ejecuta la versión del padre de un método
                             sobrescrito (Manager.monthlyPay).
        3) super.campo       accede al campo del padre cuando la hija
                             declaró otro con el mismo nombre (CAPA 6).

        super NO es otro objeto. Es una forma de decir "la parte del
        padre de ESTE MISMO objeto".
    */

    /*
        =================================================================
        CAPA 5) SOBRESCRITURA DE MÉTODOS (OVERRIDING)
        =================================================================

        Sobrescribir es que la hija dé SU PROPIA versión de un método
        heredado. Cuando alguien llame a ese método sobre un objeto de
        la hija, se ejecuta la versión de la hija.

        Ya lo viste: Manager sobrescribe monthlyPay().

        La parte poderosa: el método se elige según el OBJETO REAL, no
        según el tipo de la variable ni según la clase donde está escrita
        la llamada. Employee.payForMonths() llama a monthlyPay(); si el
        objeto es un Manager, se ejecuta el de Manager. Esto se llama
        "despacho dinámico" y se estudia a fondo en Polymorphism.java.

        Una jerarquía de 3 niveles (multinivel) para verlo encadenado:
    */
    static final class SeniorEngineer extends Engineer {
        private static final BigDecimal MENTORING_ALLOWANCE = new BigDecimal("5000");

        SeniorEngineer(String id, String name, BigDecimal baseSalary, String primaryLanguage) {
            super(id, name, baseSalary, primaryLanguage);
        }

        @Override
        BigDecimal monthlyPay() {
            return super.monthlyPay().add(MENTORING_ALLOWANCE);
        }
    }

    /*
        Reglas técnicas para que un método cuente como sobrescritura
        (las que no compilan van en comentario solo por eso):

        1) Mismo nombre y MISMOS tipos de parámetros. Si cambian los
           parámetros, NO es sobrescritura: es SOBRECARGA (overloading),
           un método distinto que solo comparte nombre.

        2) Tipo de retorno igual o MÁS específico ("retorno covariante").
           Si el padre devuelve Employee, la hija puede devolver Manager.

        3) No puede REDUCIR la visibilidad: si el padre es public, la
           hija no puede hacerlo protected. Quien usa al padre debe poder
           llamar al método en cualquier hija.

        4) No puede declarar excepciones checked nuevas o más amplias que
           las del padre.

        5) Métodos final: no se pueden sobrescribir.
               @Override BigDecimal payForMonths(int m) {...}  // ERROR en una hija
           (payForMonths es final en Employee.)

        6) Métodos private: no se heredan, así que no se sobrescriben.
           Si la hija declara uno igual, es un método nuevo e independiente.

        7) Métodos static: no se sobrescriben, se OCULTAN (CAPA 6).

        8) USA SIEMPRE @Override. Le pide al compilador que verifique que
           de verdad estás sobrescribiendo. Sin él, un error de tipeo en
           los parámetros crea en silencio una sobrecarga.

        El ejemplo clásico de la regla 8, que es un bug real:
        equals de Object recibe Object. Si escribes equals(BrokenPoint),
        creaste un método NUEVO (sobrecarga). Las colecciones de Java
        siguen llamando a equals(Object), que no tocaste.
    */
    static final class BrokenPoint {
        private final int x;
        private final int y;

        BrokenPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        // Sin @Override. Parece sobrescritura, pero es SOBRECARGA.
        public boolean equals(BrokenPoint other) {
            return other != null && x == other.x && y == other.y;
        }
    }

    static final class FixedPoint {
        private final int x;
        private final int y;

        FixedPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override // el compilador confirma que esto sí sobrescribe Object.equals
        public boolean equals(Object other) {
            return other instanceof FixedPoint p && x == p.x && y == p.y;
        }

        @Override // equals y hashCode siempre se sobrescriben juntos (guía de Object en el roadmap)
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static boolean overridingExample() {
        Employee laura = new Manager("M-001", "Laura", new BigDecimal("50000"), new BigDecimal("0.20"));
        Employee luis = new SeniorEngineer("E-002", "Luis", new BigDecimal("45000"), "Java");

        // La variable es Employee, pero corre la versión del objeto real.
        boolean managerPay = same(laura.monthlyPay(), "60000");        // 50000 + 20 %
        boolean seniorPay = same(luis.monthlyPay(), "50000");          // 45000 + 5000
        // payForMonths está escrito en Employee, pero usa el monthlyPay() de Manager.
        boolean dispatchFromParent = same(laura.payForMonths(3), "180000");

        boolean overloadBug = !List.of(new BrokenPoint(1, 2)).contains(new BrokenPoint(1, 2));
        boolean overrideWorks = List.of(new FixedPoint(1, 2)).contains(new FixedPoint(1, 2));

        return managerPay && seniorPay && dispatchFromParent && overloadBug && overrideWorks;
    }

    /*
        =================================================================
        CAPA 6) OCULTAMIENTO: CAMPOS Y MÉTODOS STATIC
        =================================================================

        La sobrescritura SOLO aplica a métodos de instancia. Los campos y
        los métodos static funcionan distinto: si la hija declara uno con
        el mismo nombre, no reemplaza al del padre, lo OCULTA (hiding).
        Los dos siguen existiendo.

        Y la diferencia clave:
        - Método de instancia sobrescrito -> decide el OBJETO REAL
          (al ejecutar).
        - Campo o método static ocultos   -> decide el TIPO DE LA
          VARIABLE o la clase escrita (al compilar).

        Ejemplo puramente mecánico, para ver la diferencia:
    */
    static class ParentLabel {
        String label = "padre";

        static String kind() {
            return "padre";
        }

        String describe() {
            return "padre";
        }
    }

    static class ChildLabel extends ParentLabel {
        String label = "hija";        // OCULTA el campo del padre; ahora hay DOS campos label

        static String kind() {        // OCULTA el static del padre
            return "hija";
        }

        @Override
        String describe() {           // SOBRESCRIBE: este sí reemplaza
            return "hija";
        }

        String bothLabels() {
            return this.label + "/" + super.label;   // super.campo llega al campo oculto del padre
        }
    }

    static boolean hidingExample() {
        ChildLabel child = new ChildLabel();
        ParentLabel sameObjectAsParent = child;   // la MISMA instancia, vista como ParentLabel

        boolean fieldDependsOnVariable = child.label.equals("hija") && sameObjectAsParent.label.equals("padre");
        boolean methodDependsOnObject = sameObjectAsParent.describe().equals("hija");
        boolean bothFieldsExist = child.bothLabels().equals("hija/padre");
        // Los static se llaman con el nombre de la clase: no hay objeto que decida.
        boolean staticByClass = ParentLabel.kind().equals("padre") && ChildLabel.kind().equals("hija");

        return fieldDependsOnVariable && methodDependsOnObject && bothFieldsExist && staticByClass;
    }

    /*
        Conclusión práctica: el ocultamiento existe, pero NUNCA lo uses a
        propósito. Dos campos con el mismo nombre en una jerarquía solo
        generan confusión. Con campos private (lo correcto) ni siquiera
        puede pasar, porque la hija no ve los campos del padre.
    */

    /*
        =================================================================
        CAPA 7) OBJECT: LA RAÍZ DE TODAS LAS CLASES
        =================================================================

        Toda clase en Java hereda, directa o indirectamente, de
        java.lang.Object. Si no escribes extends, Java pone
        "extends Object" por ti:

            class Employee            ==   class Employee extends Object

        Por eso cualquier objeto tiene métodos que nunca escribiste:
            toString()   -> texto que representa al objeto
            equals(..)   -> si dos objetos son "iguales"
            hashCode()   -> número usado por HashMap y HashSet
            getClass()   -> la clase real del objeto

        La jerarquía real de esta guía es:
            Object -> Employee -> Engineer -> SeniorEngineer

        Employee sobrescribe toString() (CAPA 3), y como usa
        getClass().getSimpleName(), cada hija muestra SU nombre sin
        tener que sobrescribirlo de nuevo.
    */
    static boolean objectRootExample() {
        Object asObject = new SeniorEngineer("E-003", "Mia", new BigDecimal("48000"), "Kotlin");

        // La variable es Object, pero toString es el de Employee y getClass() dice la clase real.
        return asObject.toString().equals("SeniorEngineer[E-003, Mia]");
    }

    /*
        =================================================================
        CAPA 8) CONTROLAR QUIÉN HEREDA: FINAL Y SEALED
        =================================================================

        Por defecto, cualquiera puede heredar de tu clase y sobrescribir
        tus métodos. Eso es un riesgo (CAPA 10). Java te da tres niveles
        de control:

        1) final en un MÉTODO: se hereda, pero nadie lo puede sobrescribir.
           Ejemplo: payForMonths y percentageOfBase en Employee.

        2) final en una CLASE: nadie puede heredar de ella.
               final class SeniorEngineer ...
               class X extends SeniorEngineer   // ERROR
           String, Integer y LocalDate son final. Por eso nadie puede
           crear un "String" que se comporte distinto.

        3) sealed (Java 17+): un punto medio. La clase dice EXACTAMENTE
           quiénes pueden ser sus hijas, y nadie más.
               sealed class X permits A, B
           Cada hija permitida debe declararse:
               final      -> la jerarquía termina ahí
               sealed     -> sigue cerrada, con su propia lista
               non-sealed -> se vuelve a abrir para cualquiera

        Úsalo cuando el negocio tiene un número FIJO de variantes. Un
        tipo de contrato solo puede ser de tiempo completo o de medio
        tiempo; nadie debería poder inventar un tercero por su cuenta.
        (Si las hijas están en el mismo archivo, "permits" se puede
        omitir, pero escribirlo documenta la intención.)
    */
    abstract static sealed class EmploymentType permits FullTime, PartTime {
        abstract int hoursPerWeek();
    }

    static final class FullTime extends EmploymentType {
        @Override
        int hoursPerWeek() {
            return 40;
        }
    }

    static final class PartTime extends EmploymentType {
        @Override
        int hoursPerWeek() {
            return 20;
        }
    }

    static int sealedExample() {
        List<EmploymentType> contracts = List.of(new FullTime(), new PartTime());
        int total = 0;
        for (EmploymentType contract : contracts) {
            total += contract.hoursPerWeek();
        }
        return total; // 60
    }

    /*
        =================================================================
        CAPA 9) FORMAS DE JERARQUÍA Y POR QUÉ NO HAY HERENCIA MÚLTIPLE
        =================================================================

        Las formas que vas a ver con clases:

        1) SIMPLE:      Employee -> Manager
        2) MULTINIVEL:  Employee -> Engineer -> SeniorEngineer
        3) JERÁRQUICA:  Employee -> Engineer
                        Employee -> Manager     (varias hijas, un padre)

        Lo que NO existe: una clase con DOS clases padre.
            class TechLead extends Engineer, Manager   // ERROR

        Por qué: el "problema del diamante". Si Engineer y Manager tienen
        cada uno su monthlyPay() y su propio campo baseSalary, ¿cuál
        hereda TechLead? ¿Una copia de los campos de Employee o dos?
        Java evita la ambigüedad prohibiéndolo.

        Lo que SÍ se permite es implementar varias interfaces. Como las
        interfaces no tienen estado, el problema de los campos
        desaparece. Si dos interfaces traen un método default con el
        mismo nombre, Java te OBLIGA a sobrescribirlo y decidir, y
        puedes llamar a cada versión con Interfaz.super.metodo():
    */
    interface Swimmer {
        default String move() {
            return "nadar";
        }
    }

    interface Runner {
        default String move() {
            return "correr";
        }
    }

    static final class Triathlete implements Swimmer, Runner {
        @Override // sin esto NO compila: hay dos move() heredados y Java no elige por ti
        public String move() {
            return Swimmer.super.move() + " y " + Runner.super.move();
        }
    }

    static String diamondExample() {
        return new Triathlete().move();
    }

    /*
        =================================================================
        CAPA 10) LOS PELIGROS REALES DE LA HERENCIA
        =================================================================

        La herencia es la relación MÁS FUERTE que puede haber entre dos
        clases: la hija depende no solo de lo que hace el padre, sino de
        CÓMO lo hace por dentro. Eso trae dos problemas que todo experto
        conoce.

        -----------------------------------------------------------------
        PELIGRO 1: LA CLASE BASE FRÁGIL
        -----------------------------------------------------------------

        Queremos un HashSet que cuente cuántos elementos se intentaron
        agregar. Heredamos y sobrescribimos add y addAll.
        (<E> es un genérico: "conjunto de elementos de tipo E". Tiene su
        propia guía; aquí no importa.)
    */
    @SuppressWarnings("serial") // HashSet es Serializable; ese tema no aplica a este ejemplo
    static final class CountingHashSet<E> extends HashSet<E> {
        private int addAttempts;

        @Override
        public boolean add(E element) {
            addAttempts++;
            return super.add(element);
        }

        @Override
        public boolean addAll(Collection<? extends E> elements) {
            addAttempts += elements.size();
            return super.addAll(elements);
        }

        int getAddAttempts() {
            return addAttempts;
        }
    }

    /*
        Agregamos 3 elementos con addAll... y el contador dice 6.

        Por qué: por DENTRO, el addAll de HashSet llama a add() por cada
        elemento. Y como el objeto es un CountingHashSet, ese add() es
        NUESTRO (despacho dinámico): cada elemento se cuenta dos veces.

        Nada en la documentación pública obligaba a HashSet a hacerlo
        así. Si en una versión futura cambia su implementación interna,
        nuestro contador cambia de resultado sin que toquemos una línea.
        A esto se le llama "clase base frágil": la hija se rompe por
        detalles internos del padre.

        La solución es COMPOSICIÓN: en lugar de SER un HashSet, nuestra
        clase TIENE un HashSet guardado en un campo y le delega el
        trabajo. Cuando el HashSet interno llama a su propio add(), es el
        suyo, no el nuestro: no hay doble conteo posible.
    */
    static final class CountingSet<E> {
        private final Set<E> elements = new HashSet<>();
        private int addAttempts;

        boolean add(E element) {
            addAttempts++;
            return elements.add(element);
        }

        boolean addAll(Collection<? extends E> newElements) {
            addAttempts += newElements.size();
            return elements.addAll(newElements);
        }

        int getAddAttempts() {
            return addAttempts;
        }
    }

    static boolean fragileBaseClassExample() {
        CountingHashSet<String> inherited = new CountingHashSet<>();
        inherited.addAll(List.of("a", "b", "c"));

        CountingSet<String> composed = new CountingSet<>();
        composed.addAll(List.of("a", "b", "c"));

        return inherited.getAddAttempts() == 6 && composed.getAddAttempts() == 3;
    }

    /*
        (En un proyecto real, CountingSet implementaría la interface Set
        y reenviaría todos sus métodos al conjunto interno. Ese diseño es
        el patrón Decorator, que tiene su guía en el roadmap.)

        -----------------------------------------------------------------
        PELIGRO 2: UNA HIJA QUE NO PUEDE CUMPLIR LO QUE PROMETE EL PADRE
        -----------------------------------------------------------------

        El Principio de Sustitución de Liskov (la "L" de SOLID) dice:

            Donde el código espera un Padre, debe poder recibir CUALQUIER
            hija sin que nada se comporte de forma inesperada.

        "ES UN" en el lenguaje cotidiano no basta. En geometría, un
        cuadrado ES UN rectángulo. En código, no siempre:
    */
    static class Rectangle {
        private int width;
        private int height;

        void setWidth(int width) {
            this.width = width;
        }

        void setHeight(int height) {
            this.height = height;
        }

        int area() {
            return width * height;
        }
    }

    // Para seguir siendo cuadrado, cambiar un lado debe cambiar el otro.
    static final class Square extends Rectangle {
        @Override
        void setWidth(int side) {
            super.setWidth(side);
            super.setHeight(side);
        }

        @Override
        void setHeight(int side) {
            super.setWidth(side);
            super.setHeight(side);
        }
    }

    /*
        Este método confía en lo que promete Rectangle: ancho y alto son
        independientes. Con un Rectangle devuelve 20. Con un Square, que
        "ES UN" Rectangle, devuelve 16. El método no hizo nada mal; la
        jerarquía está mal diseñada.
    */
    static int resizeTo5x4(Rectangle rectangle) {
        rectangle.setWidth(5);
        rectangle.setHeight(4);
        return rectangle.area();
    }

    static int liskovExample() {
        return resizeTo5x4(new Square()); // 16, no 20
    }

    /*
        La pregunta correcta no es "¿ES UN en la vida real?", sino:
        "¿La hija puede hacer TODO lo que el padre promete, sin
        excepciones ni sorpresas?". Si la hija necesita lanzar
        excepciones en métodos heredados, o cambiar reglas que el padre
        garantiza, no debe heredar.
    */

    /*
        =================================================================
        CAPA 11) HERENCIA VS COMPOSICIÓN: CÓMO DECIDIR
        =================================================================

        Regla general en la industria (Effective Java, Gang of Four):
            "Prefiere composición sobre herencia."

        No significa "nunca heredes". Significa que la herencia necesita
        cumplir TODAS estas condiciones:

        [ ] La hija ES UN tipo del padre de forma permanente (un empleado
            no "se vuelve" gerente cambiando de clase en tiempo de
            ejecución; si el rol cambia, es composición).
        [ ] La hija puede cumplir TODO lo que el padre promete (Liskov).
        [ ] Necesitas que la hija se use donde se espera al padre.
        [ ] El padre fue diseñado para heredarse (documenta qué métodos
            se pueden sobrescribir y cómo se llaman entre sí), o es tuyo
            y lo controlas.

        Si alguna falla, usa composición: guarda el otro objeto como
        campo y delégale el trabajo (como CountingSet).

        | Herencia                                 | Composición                         |
        |---------------------------------------|-------------------------------------|
        | Relación "ES UN"                      | Relación "TIENE UN" / "USA UN"      |
        | Fijada al compilar                    | Se puede cambiar al ejecutar        |
        | La hija depende del interior del padre| Solo depende de la cara pública     |
        | Una sola clase padre                  | Tantos objetos como quieras         |
        | Hereda TODO, aunque no lo quiera      | Expone solo lo que elige            |

        Un error muy común: heredar de una clase solo para reutilizar
        sus métodos.
            class EmployeeList extends ArrayList<Employee>   // MAL
        EmployeeList ahora tiene clear(), remove(), set()... todos
        públicos, saltándose cualquier regla de negocio. Lo correcto es
        que TENGA una lista privada y exponga solo lo que tiene sentido.
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) "La hija hereda TODO."
           No hereda constructores, y no accede a lo private (aunque esos
           campos sí existen dentro del objeto). CAPA 3.

        2) "Se crean dos objetos: el padre y la hija."
           Se crea UNO solo, con las capas de toda la jerarquía. super no
           es otro objeto. CAPA 3 y 4.

        3) "Si no escribo super(...) no pasa nada."
           Java inserta super(). Si el padre no tiene constructor vacío,
           no compila. CAPA 4.

        4) "Declarar un campo con el mismo nombre lo sobrescribe."
           No: lo oculta y quedan dos. CAPA 6.

        5) "Los métodos static son polimórficos."
           No: se ocultan y decide la clase escrita, no el objeto. CAPA 6.

        6) Olvidar @Override.
           Un parámetro distinto convierte la sobrescritura en sobrecarga
           sin avisar (el bug de equals). CAPA 5.

        7) Campos protected "para que las hijas los usen".
           Las hijas pueden romper las reglas del padre. Campos private
           y métodos protected controlados (ver Encapsulation.java).

        8) Llamar métodos sobrescribibles desde el constructor del padre.
           Se ejecuta la versión de la hija ANTES de que la hija termine
           de construirse, con sus campos todavía vacíos.

        9) Heredar para reutilizar código sin relación "ES UN" real.
           EmployeeList extends ArrayList. CAPA 11.

        10) Jerarquías profundas.
            Más de 2 o 3 niveles suele indicar mal diseño: cada nivel
            agrega dependencias ocultas. Prefiere jerarquías planas.

        11) Dejar clases abiertas a la herencia sin haberlo pensado.
            "Diseña para herencia o prohíbela": si no, márcala final.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Herencia = una clase nueva reutiliza y especializa otra.
          Solo para relaciones "ES UN" reales y permanentes.
        - Se heredan los miembros accesibles; lo private existe pero no
          se toca directo; los constructores no se heredan.
        - El objeto se construye de arriba hacia abajo; super(...) elige
          el constructor del padre.
        - Sobrescribir: la hija reemplaza un método; decide el objeto
          real. Usa siempre @Override.
        - Campos y static no se sobrescriben, se ocultan: evítalo.
        - Todo hereda de Object.
        - final y sealed controlan quién puede heredar.
        - Sin herencia múltiple de clases; sí de interfaces.
        - Peligros: clase base frágil y violar Liskov.
        - Por defecto, composición. Herencia solo cuando pasa todas las
          condiciones de la CAPA 11.
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

    // Compara cantidades sin importar decimales: 60000 y 60000.00 son lo mismo.
    private static boolean same(BigDecimal actual, String expected) {
        return actual.compareTo(new BigDecimal(expected)) == 0;
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
