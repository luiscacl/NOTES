package v2_oop.part2_pillars;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/*
    =====================================================================
    ENCAPSULACIÓN EN JAVA — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 4 de 14  (part2_pillars)
    Anterior:  part1_basics/AccessModifiers
    Siguiente: part2_pillars/Inheritance

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 0)  Vocabulario mínimo
        CAPA 1)  Qué es la encapsulación
        CAPA 2)  El problema: campos públicos
        CAPA 3)  La solución: private + acceso controlado
        CAPA 4)  Invariantes: el objeto nace válido y se queda válido
        CAPA 5)  Comportamiento en vez de setters (Tell, Don't Ask)
        CAPA 6)  Getters y setters bien hechos
        CAPA 7)  final e inmutabilidad
        CAPA 8)  Fugas por referencias mutables y copias defensivas
        CAPA 9)  Ocultar la representación interna
        CAPA 10) Encapsulación y herencia
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Encapsulation {

    public static void main(String[] args) {
        check(publicFieldsExample().balance.signum() < 0, "CAPA 2");
        check(privateAccessExample(), "CAPA 3");
        check(invariantExample(), "CAPA 4");
        check(anemicModelExample(), "CAPA 5");
        check(gettersAndSettersExample(), "CAPA 6");
        check(immutabilityExample(), "CAPA 7");
        check(mutableReferencesExample(), "CAPA 8");
        check(hiddenRepresentationExample(), "CAPA 9");
        check(inheritanceExample(), "CAPA 10");
        System.out.println("Encapsulation: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 0) VOCABULARIO MÍNIMO
        =================================================================

        - ESTADO: los datos que un objeto tiene guardados en un momento
          dado. El saldo de una cuenta, el stock de un producto.
          En Java, el estado vive en los CAMPOS (variables de la clase).

        - MUTAR: cambiar el estado. Depositar muta el saldo.

        - INVARIANTE: una regla que SIEMPRE debe ser cierta para el
          objeto, sin excepción.
              "El saldo nunca es negativo."
              "La fecha de salida siempre es después de la de entrada."

        - CÓDIGO CLIENTE: cualquier código que usa un objeto desde fuera
          de su clase.
    */

    /*
        =================================================================
        CAPA 1) QUÉ ES LA ENCAPSULACIÓN
        =================================================================

        Encapsular es meter los DATOS y las REGLAS que los protegen en
        el mismo lugar (la clase), y obligar a que cualquier cambio pase
        por esas reglas.

        Ejemplo de la vida real: un cajero automático.
        El dinero está dentro de una caja fuerte (estado privado).
        Tú no metes la mano; usas los botones (métodos públicos).
        Cada botón aplica reglas antes de tocar el dinero:
            - ¿el NIP es correcto?
            - ¿hay saldo suficiente?
            - ¿no excede el límite diario?
        Es imposible sacar dinero saltándote esas reglas, porque la
        ÚNICA puerta a la caja fuerte son los botones.

        En código es igual:
            - los campos son la caja fuerte  -> private
            - los métodos son los botones    -> public
            - las validaciones son las reglas dentro de cada método

        El objetivo final: que sea IMPOSIBLE que el objeto quede en un
        estado inválido, sin importar quién lo use ni cómo.

        Diferencia con abstracción (ver Abstraction.java):
        - Abstracción decide QUÉ cara mostrar hacia afuera.
        - Encapsulación PROTEGE lo que hay adentro.
    */

    /*
        =================================================================
        CAPA 2) EL PROBLEMA: CAMPOS PÚBLICOS
        =================================================================

        Así se ve una cuenta bancaria sin encapsular. Cualquiera puede
        leer y escribir sus campos directamente.
    */
    static final class PublicBankAccount {
        public String owner;
        public BigDecimal balance;
    }

    static PublicBankAccount publicFieldsExample() {
        PublicBankAccount account = new PublicBankAccount();
        account.owner = "Luis";
        account.balance = new BigDecimal("1000");

        // Cualquier parte del programa puede hacer esto. Compila sin problema.
        account.balance = new BigDecimal("-5000");
        account.owner = "";

        return account; // un objeto "válido" para Java, pero absurdo para el negocio
    }

    /*
        Que compile NO significa que esté bien. Los problemas:

        1) Nadie protege las reglas. Un saldo negativo o un dueño vacío
           son imposibles en el negocio, pero el objeto los acepta.

        2) Las reglas terminan COPIADAS en cada lugar que usa la cuenta:
               if (account.balance.compareTo(amount) >= 0) { ... }
           Si hay 20 lugares que retiran dinero, hay 20 copias de esa
           regla. Basta con que UNA se olvide para tener un error.

        3) No puedes cambiar nada después. Si mañana quieres registrar
           cada movimiento (auditoría), no hay un método por donde pasen
           todos los cambios: la gente escribe directo en el campo.

        4) Cuando aparece un saldo negativo en producción, no hay forma
           de saber QUIÉN lo puso. Cualquier línea del sistema pudo ser.
    */

    /*
        =================================================================
        CAPA 3) LA SOLUCIÓN: PRIVATE + ACCESO CONTROLADO
        =================================================================

        Los modificadores de acceso deciden QUIÉN puede ver cada cosa:

        | Modificador      | Misma clase | Mismo paquete | Hijas | Todos |
        |------------------|-------------|---------------|-------|-------|
        | private          | Sí          | No            | No    | No    |
        | (sin modificador)| Sí          | Sí            | No*   | No    |
        | protected        | Sí          | Sí            | Sí    | No    |
        | public           | Sí          | Sí            | Sí    | Sí    |
        * solo si la hija está en el mismo paquete.

        REGLA DE ORO: empieza todo en private y abre SOLO lo que alguien
        de fuera realmente necesite. Es fácil abrir algo después; cerrar
        algo que ya usan otros rompe su código.

        La cuenta encapsulada:
        - Los campos son private: nadie de fuera puede escribirlos.
        - El saldo se puede LEER (getBalance), pero no hay setBalance.
        - La ÚNICA forma de cambiar el saldo es deposit, withdraw o
          transferTo, y cada una aplica las reglas.
    */
    static class BankAccount {
        private final String owner;   // final: el dueño no cambia nunca (CAPA 7)
        private BigDecimal balance;

        BankAccount(String owner, BigDecimal initialBalance) {
            Objects.requireNonNull(initialBalance, "initialBalance");
            if (initialBalance.signum() < 0) {
                throw new IllegalArgumentException("El saldo inicial no puede ser negativo");
            }
            this.owner = requireText(owner, "owner");
            this.balance = initialBalance;
        }

        String getOwner() {
            return owner;
        }

        // Se puede devolver tal cual porque BigDecimal no se puede modificar (CAPA 7).
        BigDecimal getBalance() {
            return balance;
        }

        // final: ninguna clase hija puede sobrescribirlo para saltarse la validación (CAPA 10).
        final void deposit(BigDecimal amount) {
            requirePositive(amount);
            balance = balance.add(amount);
        }

        final void withdraw(BigDecimal amount) {
            requirePositive(amount);
            if (amount.compareTo(balance) > 0) {
                throw new IllegalStateException("Fondos insuficientes");
            }
            balance = balance.subtract(amount);
        }

        final void transferTo(BankAccount target, BigDecimal amount) {
            Objects.requireNonNull(target, "target");
            if (target == this) {
                throw new IllegalArgumentException("No puedes transferirte a la misma cuenta");
            }
            withdraw(amount);       // si falla, lanza excepción y el depósito nunca ocurre
            target.deposit(amount);
        }

        /*
            Dato técnico importante: private es por CLASE, no por OBJETO.
            Dentro de BankAccount puedes leer el campo privado de OTRA
            BankAccount (other.balance). La protección es contra código
            de OTRAS clases, no entre objetos de la misma clase.
        */
        boolean hasMoreMoneyThan(BankAccount other) {
            return this.balance.compareTo(other.balance) > 0;
        }
    }

    static boolean privateAccessExample() {
        BankAccount luis = new BankAccount("Luis", new BigDecimal("500"));
        BankAccount ana = new BankAccount("Ana", new BigDecimal("200"));

        luis.transferTo(ana, new BigDecimal("100")); // Luis 400, Ana 300

        // luis.balance = new BigDecimal("-5000");
        // Con BankAccount en su propio archivo (lo normal en un proyecto), esa línea
        // NO compila: balance es private. Aquí compilaría solo por un detalle de las
        // clases anidadas: la clase que las contiene (Encapsulation) sí ve sus private.

        return luis.hasMoreMoneyThan(ana);
    }

    /*
        =================================================================
        CAPA 4) INVARIANTES: EL OBJETO NACE VÁLIDO Y SE QUEDA VÁLIDO
        =================================================================

        Encapsular no empieza en los setters: empieza en el CONSTRUCTOR.
        Si el objeto puede nacer roto, todo lo demás da igual.

        Reglas profesionales para constructores:

        1) Valida TODO antes de asignar. Si algo es inválido, lanza una
           excepción: el objeto ni siquiera llega a existir. A esto se
           le llama FAIL FAST (fallar rápido): el error aparece en la
           línea que lo causó, no 500 líneas después.

        2) Las invariantes más valiosas son las que relacionan VARIOS
           campos. Validar cada campo por separado no basta.

        3) Lo que se puede CALCULAR no se guarda. Si guardas el dato y
           también lo que se deriva de él, tarde o temprano se
           desincronizan.

        Ejemplo: una estancia de hotel.
        Invariante: la salida es estrictamente después de la entrada.
    */
    static final class Stay {
        private final LocalDate checkIn;
        private final LocalDate checkOut;

        Stay(LocalDate checkIn, LocalDate checkOut) {
            Objects.requireNonNull(checkIn, "checkIn");   // lanza NullPointerException con ese mensaje
            Objects.requireNonNull(checkOut, "checkOut");
            if (!checkOut.isAfter(checkIn)) {
                throw new IllegalArgumentException("La salida debe ser después de la entrada");
            }
            this.checkIn = checkIn;
            this.checkOut = checkOut;
        }

        /*
            Las noches NO son un campo: se calculan. Así es imposible
            tener checkIn=1, checkOut=4 y nights=10.
        */
        long nights() {
            return ChronoUnit.DAYS.between(checkIn, checkOut);
        }
    }

    static boolean invariantExample() {
        Stay valid = new Stay(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 23));

        boolean invalidRejected;
        try {
            new Stay(LocalDate.of(2026, 12, 23), LocalDate.of(2026, 12, 20)); // salida antes que entrada
            invalidRejected = false;
        } catch (IllegalArgumentException e) {
            invalidRejected = true; // el objeto inválido nunca llegó a existir
        }

        return valid.nights() == 3 && invalidRejected;
    }

    /*
        =================================================================
        CAPA 5) COMPORTAMIENTO EN VEZ DE SETTERS (TELL, DON'T ASK)
        =================================================================

        Esta es la idea más importante de toda la guía.

        Poner los campos en private y generar getter + setter para cada
        uno NO es encapsular. Es exactamente igual que tener los campos
        públicos, solo que con más código. Mira:
    */
    static final class AnemicAccount {
        private BigDecimal balance = BigDecimal.ZERO;

        BigDecimal getBalance() {
            return balance;
        }

        void setBalance(BigDecimal balance) {
            this.balance = balance;
        }
    }

    /*
        Como la cuenta no tiene reglas, cada servicio que la usa tiene
        que ACORDARSE de aplicarlas. Este sí se acordó...
    */
    static final class AnemicWithdrawalService {
        void withdraw(AnemicAccount account, BigDecimal amount) {
            if (amount.compareTo(account.getBalance()) > 0) {
                throw new IllegalStateException("Fondos insuficientes");
            }
            account.setBalance(account.getBalance().subtract(amount));
        }
    }

    /*
        ...y este, escrito meses después por otra persona, no.
    */
    static final class AnemicFeeService {
        void chargeMonthlyFee(AnemicAccount account, BigDecimal fee) {
            account.setBalance(account.getBalance().subtract(fee)); // puede dejar saldo negativo
        }
    }

    static boolean anemicModelExample() {
        AnemicAccount anemic = new AnemicAccount();
        anemic.setBalance(new BigDecimal("30"));
        new AnemicFeeService().chargeMonthlyFee(anemic, new BigDecimal("50")); // saldo -20

        BankAccount encapsulated = new BankAccount("Ana", new BigDecimal("30"));
        boolean protectedByObject;
        try {
            encapsulated.withdraw(new BigDecimal("50")); // la regla vive en el objeto: no hay forma de saltarla
            protectedByObject = false;
        } catch (IllegalStateException e) {
            protectedByObject = true;
        }

        return anemic.getBalance().signum() < 0 && protectedByObject;
    }

    /*
        A una clase que solo tiene datos + getters/setters, con toda la
        lógica afuera en "servicios", se le llama MODELO ANÉMICO. Es uno
        de los errores de diseño más comunes en proyectos Java reales.

        El principio que lo evita se llama TELL, DON'T ASK
        ("dile qué hacer, no le preguntes sus datos para hacerlo tú"):

            ASK (mal):
                if (account.getBalance() >= amount)
                    account.setBalance(account.getBalance() - amount);

            TELL (bien):
                account.withdraw(amount);

        Con TELL, la regla vive en UN solo lugar (BankAccount.withdraw)
        y todos los que retiran dinero pasan por ella. No hay forma de
        olvidarla.

        Además, el nombre del método dice QUÉ pasa en el negocio:
        withdraw, deposit, transferTo. setBalance no dice nada: ¿fue un
        depósito?, ¿una comisión?, ¿un error?
    */

    /*
        =================================================================
        CAPA 6) GETTERS Y SETTERS BIEN HECHOS
        =================================================================

        Getters y setters no son malos en sí. Lo malo es generarlos en
        automático para todos los campos. Para cada campo pregúntate:

        1) ¿Alguien de fuera necesita LEERLO?
           Sí -> getter. No -> nada.

        2) ¿Debe poder CAMBIAR después de crear el objeto?
           Nunca (un id) -> campo final, sin setter.
           Sí            -> un método con nombre del negocio que valide,
                            no un setter que solo asigna.

        3) ¿Cambiarlo afecta a otros campos?
           Entonces el método se encarga de mantenerlos coherentes.
           Esto es justo lo que un setter simple olvida.

        Ejemplo: perfil de usuario. Regla de negocio: si el usuario
        cambia su correo, el nuevo correo deja de estar verificado.
    */
    static final class UserProfile {
        private final long id;          // no cambia nunca: final y sin setter
        private String email;
        private boolean verified;       // empieza en false

        UserProfile(long id, String email) {
            if (id <= 0) {
                throw new IllegalArgumentException("El id debe ser positivo");
            }
            this.id = id;
            this.email = normalizeEmail(email);
        }

        long getId() {
            return id;
        }

        String getEmail() {
            return email;
        }

        // Convención: los getters de boolean empiezan con "is".
        boolean isVerified() {
            return verified;
        }

        /*
            En lugar de setEmail: valida, normaliza y mantiene la
            coherencia con "verified". Un setEmail(String) genérico haría
            solo la asignación y dejaría un correo nuevo marcado como
            verificado: un hueco de seguridad.
        */
        void changeEmail(String newEmail) {
            String normalized = normalizeEmail(newEmail);
            if (!normalized.equals(email)) {
                email = normalized;
                verified = false;
            }
        }

        void markVerified() {
            verified = true;
        }

        /*
            La validación vive en UN método que usan tanto el constructor
            como changeEmail: la regla no se duplica.

            Es private static a propósito. Un constructor NUNCA debe
            llamar a métodos que una clase hija pueda sobrescribir: la
            versión de la hija se ejecutaría ANTES de que la hija termine
            de construirse, con sus campos todavía vacíos. Un método
            private o static no se puede sobrescribir, así que es seguro.
        */
        private static String normalizeEmail(String email) {
            String normalized = requireText(email, "email").trim().toLowerCase();
            if (!normalized.contains("@")) {
                throw new IllegalArgumentException("Correo inválido: " + email);
            }
            return normalized;
        }
    }

    static boolean gettersAndSettersExample() {
        UserProfile profile = new UserProfile(7, "  Luis@Correo.com ");
        profile.markVerified();
        profile.changeEmail("luis.nuevo@correo.com");

        // El correo quedó normalizado y la verificación se reinició sola.
        return profile.getEmail().equals("luis.nuevo@correo.com") && !profile.isVerified();
    }

    /*
        =================================================================
        CAPA 7) FINAL E INMUTABILIDAD
        =================================================================

        La forma más fuerte de encapsular es que el estado NO PUEDA
        cambiar nunca. Un objeto INMUTABLE se crea válido y se queda
        así para siempre: no hay ninguna forma de romperlo.

        Receta de una clase inmutable:
        1) Todos los campos private final (se asignan una vez, en el
           constructor, y no cambian).
        2) Ningún método modifica campos.
        3) Las "modificaciones" devuelven un objeto NUEVO.
        4) La clase es final, para que nadie cree una hija mutable.
        5) Si guarda algo mutable (una lista), hace copias defensivas
           (CAPA 8).

        Ventajas:
        - Se puede compartir entre partes del programa sin miedo:
          nadie te lo puede cambiar por debajo.
        - Se puede usar desde varios hilos a la vez sin sincronizar.
        - Es más fácil de razonar: lo que ves al crearlo es lo que hay.

        Java ya está lleno de clases inmutables: String, Integer,
        BigDecimal, LocalDate. Por eso en BankAccount se puede devolver
        el BigDecimal del saldo sin miedo: nadie puede modificarlo.

        Ejemplo: dinero con moneda.
    */
    static final class Money {
        private final BigDecimal amount;
        private final String currency;

        Money(BigDecimal amount, String currency) {
            Objects.requireNonNull(amount, "amount");
            this.currency = requireText(currency, "currency");
            this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        }

        // No cambia este objeto: devuelve uno nuevo.
        Money plus(Money other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("No se pueden sumar " + currency + " y " + other.currency);
            }
            return new Money(amount.add(other.amount), currency);
        }

        BigDecimal getAmount() {
            return amount;
        }

        String getCurrency() {
            return currency;
        }
    }

    /*
        CUIDADO: final en una variable NO hace inmutable al objeto.
        final solo prohíbe REASIGNAR la variable a otro objeto.
        Si el objeto es mutable, se puede seguir cambiando por dentro.
    */
    static boolean immutabilityExample() {
        Money price = new Money(new BigDecimal("100"), "MXN");
        Money total = price.plus(new Money(new BigDecimal("50"), "MXN"));
        boolean originalUntouched = price.getAmount().equals(new BigDecimal("100.00"));

        final List<String> tags = new ArrayList<>();
        tags.add("oferta");       // compila: la LISTA es mutable
        // tags = new ArrayList<>();  <- esto NO compila: la VARIABLE es final

        return originalUntouched && total.getAmount().equals(new BigDecimal("150.00")) && tags.size() == 1;
    }

    /*
        =================================================================
        CAPA 8) FUGAS POR REFERENCIAS MUTABLES Y COPIAS DEFENSIVAS
        =================================================================

        Este es el hueco que más gente deja abierta, aun usando private.

        Recordatorio clave: en Java, una variable de objeto no guarda el
        objeto, guarda una REFERENCIA (una dirección) hacia él. Si dos
        variables tienen la misma referencia, apuntan al MISMO objeto.

        Entonces, si tu clase guarda una lista private y la devuelve
        tal cual en un getter, le estás dando a quien llama la dirección
        de TU lista interna. Puede modificarla sin pasar por ninguna de
        tus reglas. private protegió el campo, pero no el objeto.

        La fuga ocurre por DOS puertas:
        - SALIDA: un getter que devuelve la lista interna.
        - ENTRADA: un constructor que guarda la lista que le pasaron,
          sin copiarla. Quien la pasó sigue teniendo la referencia.

        Las tres formas de devolver una colección:

        | Forma                                | Se puede modificar | Refleja cambios futuros |
        |--------------------------------------|--------------------|-------------------------|
        | return members;                      | Sí (la REAL)       | Sí  -> FUGA             |
        | Collections.unmodifiableList(members)| No (lanza error)   | Sí  -> vista de solo lectura |
        | List.copyOf(members)                 | No (lanza error)   | No  -> foto de ese momento |

        Las dos últimas son seguras. Elige vista si quien llama debe ver
        cambios posteriores; elige copia si quieres una foto fija.
    */
    static final class Team {
        private static final int MAX_MEMBERS = 5;

        private final String name;
        private final List<String> members;

        Team(String name, List<String> initialMembers) {
            this.name = requireText(name, "name");
            this.members = new ArrayList<>(initialMembers);   // copia de ENTRADA
            if (members.size() > MAX_MEMBERS) {
                throw new IllegalArgumentException("Máximo " + MAX_MEMBERS + " integrantes");
            }
        }

        String getName() {
            return name;
        }

        // La única forma correcta de agregar: aplica la regla del máximo.
        void addMember(String member) {
            requireText(member, "member");
            if (members.size() >= MAX_MEMBERS) {
                throw new IllegalStateException("El equipo está lleno");
            }
            members.add(member);
        }

        // MAL: entrega la lista interna. Cualquiera puede saltarse addMember.
        List<String> getMembersUnsafe() {
            return members;
        }

        // BIEN: vista de solo lectura, ve los cambios futuros.
        List<String> getMembers() {
            return Collections.unmodifiableList(members);
        }

        // BIEN: copia inmutable, una foto de este momento.
        List<String> getMembersSnapshot() {
            return List.copyOf(members);
        }
    }

    static boolean mutableReferencesExample() {
        // Fuga de ENTRADA evitada: cambiar la lista original no afecta al equipo.
        List<String> initial = new ArrayList<>(List.of("Luis", "Ana"));
        Team team = new Team("Backend", initial);
        initial.add("Intruso");
        boolean inputProtected = team.getMembers().size() == 2;

        // Fuga de SALIDA: con la lista real, se rompe la regla del máximo.
        Team leaky = new Team("Mobile", List.of("Carlos"));
        List<String> leaked = leaky.getMembersUnsafe();
        for (int i = 0; i < 10; i++) {
            leaked.add("Intruso " + i); // 11 integrantes en un equipo de máximo 5
        }
        boolean leakBrokeRule = leaky.getMembersUnsafe().size() == 11;

        // Vista vs copia.
        List<String> view = team.getMembers();
        List<String> snapshot = team.getMembersSnapshot();
        team.addMember("Elena");
        boolean viewSeesChange = view.size() == 3;
        boolean snapshotFrozen = snapshot.size() == 2;

        boolean viewIsReadOnly;
        try {
            view.add("Intruso");
            viewIsReadOnly = false;
        } catch (UnsupportedOperationException e) {
            viewIsReadOnly = true;
        }

        return inputProtected && leakBrokeRule && viewSeesChange && snapshotFrozen && viewIsReadOnly;
    }

    /*
        =================================================================
        CAPA 9) OCULTAR LA REPRESENTACIÓN INTERNA
        =================================================================

        Encapsular también significa que nadie de fuera sabe CÓMO
        guardas los datos por dentro. Eso te deja cambiarlo después sin
        romper a nadie.

        Ejemplo: una temperatura. Por dentro se guarda en Kelvin (la
        escala científica, sin negativos). Por fuera se ofrece en Celsius
        y Fahrenheit. Ningún cliente sabe que existe Kelvin.

        Si mañana decides guardar en Celsius, cambias solo las líneas de
        esta clase. Todo el código que usa celsius() y fahrenheit() sigue
        funcionando igual. Con un campo público "kelvin", ese cambio
        rompería a todos los que lo leen.

        La representación interna también ayuda a la invariante: en
        Kelvin, "no existe nada más frío que el cero absoluto" es
        simplemente "kelvin >= 0".
    */
    static final class Temperature {
        private static final double KELVIN_OFFSET = 273.15;

        private final double kelvin;

        Temperature(double celsius) {
            double k = celsius + KELVIN_OFFSET;
            if (k < 0) {
                throw new IllegalArgumentException("Nada es más frío que el cero absoluto (-273.15 °C)");
            }
            this.kelvin = k;
        }

        double celsius() {
            return kelvin - KELVIN_OFFSET;
        }

        double fahrenheit() {
            return celsius() * 9 / 5 + 32;
        }
    }

    static boolean hiddenRepresentationExample() {
        Temperature boiling = new Temperature(100);
        return Math.abs(boiling.fahrenheit() - 212) < 1e-9; // con double se compara con margen, no con ==
    }

    /*
        =================================================================
        CAPA 10) ENCAPSULACIÓN Y HERENCIA
        =================================================================

        Una clase hija es código que NO escribiste tú (o que escribirás
        dentro de un año, ya sin recordar las reglas). Si le das acceso
        directo a tus campos con protected, puede romper tus invariantes
        igual que cualquiera con un campo public.

        Así se ve el problema:
    */
    static class LeakyBaseAccount {
        protected BigDecimal balance = BigDecimal.ZERO;   // las hijas pueden escribirlo directo

        void withdraw(BigDecimal amount) {
            if (amount.compareTo(balance) > 0) {
                throw new IllegalStateException("Fondos insuficientes");
            }
            balance = balance.subtract(amount);
        }
    }

    static final class CashbackAccount extends LeakyBaseAccount {
        void chargeAnnualFee(BigDecimal fee) {
            balance = balance.subtract(fee);   // se salta la validación del padre: saldo negativo
        }
    }

    /*
        La forma correcta: el padre mantiene sus campos private y sus
        métodos que protegen invariantes como final (como BankAccount
        en la CAPA 3). La hija solo puede usar esos métodos: lo que
        puede hacer cambia, pero las reglas no se pueden saltar.
    */
    static final class RewardsAccount extends BankAccount {
        private int points;

        RewardsAccount(String owner, BigDecimal initialBalance) {
            super(owner, initialBalance);
        }

        void addPoints(int earned) {
            if (earned <= 0) {
                throw new IllegalArgumentException("Los puntos deben ser positivos");
            }
            points += earned;
        }

        // Cada 100 puntos valen 10 pesos. Solo puede tocar el saldo a través de deposit().
        void redeemPoints() {
            int blocks = points / 100;
            if (blocks > 0) {
                deposit(new BigDecimal(blocks * 10));
                points -= blocks * 100;
            }
        }

        int getPoints() {
            return points;
        }
    }

    static boolean inheritanceExample() {
        CashbackAccount leaky = new CashbackAccount();
        leaky.chargeAnnualFee(new BigDecimal("300")); // saldo -300: la hija rompió al padre

        RewardsAccount rewards = new RewardsAccount("Laura", new BigDecimal("100"));
        rewards.addPoints(250);
        rewards.redeemPoints(); // +20 de saldo, quedan 50 puntos

        return leaky.balance.signum() < 0
                && rewards.getBalance().equals(new BigDecimal("120"))
                && rewards.getPoints() == 50;
    }

    /*
        Reglas para herencia y encapsulación:

        1) Campos SIEMPRE private, también en clases pensadas para
           heredarse. Si la hija necesita algo, dale un método protected
           que valide, no el campo.

        2) Marca final los métodos que protegen invariantes, para que
           ninguna hija los sobrescriba con una versión sin validar.

        3) "Diseña para herencia o prohíbela" (Effective Java): si no
           pensaste cómo se va a extender tu clase, márcala final.
           Por eso casi todas las clases de esta guía son final.

        4) Nunca llames métodos sobrescribibles desde el constructor
           (ver CAPA 6).
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) "Encapsular es poner private y generar getters y setters."
           No. Eso da un modelo anémico (CAPA 5): los campos son private
           pero cualquiera los cambia sin reglas.

        2) Validar en quien llama en vez de en el objeto.
           La regla debe vivir dentro de la clase, una sola vez. Si está
           en los que la usan, se copia y tarde o temprano se olvida.

        3) Permitir que el objeto nazca inválido.
           El constructor es la primera línea de defensa (CAPA 4).

        4) Devolver colecciones o arreglos internos tal cual.
           private no protege el objeto al que apunta el campo (CAPA 8).
           Lo mismo aplica a arreglos: devuelve array.clone() o una lista
           inmutable, nunca el arreglo interno.

        5) Guardar lo que recibes sin copiarlo.
           La fuga de ENTRADA es igual de peligrosa que la de salida.

        6) Creer que final hace inmutable al objeto.
           final protege la variable, no el contenido (CAPA 7).

        7) Campos protected "para que las hijas los usen".
           Las hijas también pueden romper las reglas (CAPA 10).

        8) Exponer datos sensibles en getters o en toString.
           Una contraseña o un número de tarjeta completo nunca deben
           salir del objeto; si acaso, una versión enmascarada.

        9) Abrir todo "por si acaso".
           Cada cosa public es una promesa que ya no puedes cambiar sin
           romper a quien la usa. Abre solo lo que se necesita hoy.

        10) Guardar datos que se pueden calcular.
            Dos fuentes del mismo dato acaban contradiciéndose (CAPA 4).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Encapsular = los datos y las reglas que los protegen viven
          juntos, y la única forma de cambiar los datos es pasando por
          esas reglas.
        - El objetivo: que sea IMPOSIBLE que el objeto quede inválido.
        - private es el mínimo, no el objetivo.
        - El constructor valida todo: el objeto nace válido (fail fast).
        - Métodos con nombre del negocio (withdraw) en lugar de setters
          (setBalance): Tell, Don't Ask.
        - Inmutable siempre que se pueda; si no, copias defensivas al
          recibir y al devolver datos mutables.
        - La representación interna es un secreto: así puedes cambiarla.
        - Con herencia: campos private, métodos críticos final, y clases
          final si no fueron diseñadas para heredarse.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía (validaciones compartidas).
    // -----------------------------------------------------------------

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " no puede ser null ni vacío");
        }
        return value;
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
