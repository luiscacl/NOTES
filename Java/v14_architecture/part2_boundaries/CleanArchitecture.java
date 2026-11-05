package v14_architecture.part2_boundaries;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
    =====================================================================
    CLEAN ARCHITECTURE: CÍRCULOS, LA REGLA DE DEPENDENCIA Y PRESENTERS
    =====================================================================

    Ruta de architecture/: guía 4 de 6  (part2_boundaries)
    Anterior:  part2_boundaries/Hexagonal
    Siguiente: part3_systems/CommandsQueriesAndConsistency

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Lo que ya se vio: hexagonal (part2_boundaries/Hexagonal), que
    comparte con esta el corazón de la idea. Aquí solo lo que Clean
    Architecture AGREGA: separar reglas de empresa y de aplicación, el
    presenter, y "los frameworks son un detalle".

    Importancia: MEDIA-ALTA. El nombre aparece en entrevistas y en muchos
    equipos; conviene saber qué dice de verdad y qué partes se usan en la
    práctica.

    Índice:
        CAPA 1)  Los círculos y la regla de dependencia
        CAPA 2)  Entidades: reglas de la empresa
        CAPA 3)  Casos de uso: reglas de la aplicación
        CAPA 4)  Cruzar una frontera: el flujo va hacia afuera, la dependencia hacia adentro
        CAPA 5)  El presenter: el caso de uso no decide cómo se ve
        CAPA 6)  Los frameworks y la base de datos son detalles
        CAPA 7)  Una arquitectura que "grita" de qué se trata
        CAPA 8)  Hexagonal, cebolla y clean: la misma familia
        CAPA 9)  Con pragmatismo: lo que se usa y lo que sobra
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class CleanArchitecture {

    public static void main(String[] args) {
        check(entityExample(), "CAPA 2");
        check(useCaseExample(), "CAPA 3");
        check(presentersExample(), "CAPA 5");
        System.out.println("CleanArchitecture: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) LOS CÍRCULOS Y LA REGLA DE DEPENDENCIA
        =================================================================

        Robert C. Martin ("Uncle Bob") la describió en 2012 y en su libro
        Clean Architecture (2017), con círculos concéntricos:

            +---------------------------------------------------------+
            |  FRAMEWORKS Y DRIVERS: web, base de datos, Spring, UI   |
            |   +---------------------------------------------------+ |
            |   | ADAPTADORES DE INTERFAZ: controllers, presenters,  | |
            |   | gateways (los adaptadores de Hexagonal)            | |
            |   |   +---------------------------------------------+  | |
            |   |   | CASOS DE USO: reglas de la APLICACIÓN       |  | |
            |   |   |   +--------------------------------------+  |  | |
            |   |   |   | ENTIDADES: reglas de la EMPRESA      |  |  | |
            |   |   |   +--------------------------------------+  |  | |
            |   |   +---------------------------------------------+  | |
            |   +---------------------------------------------------+ |
            +---------------------------------------------------------+

        LA REGLA DE DEPENDENCIA: el código fuente solo puede depender
        HACIA ADENTRO. Nada de un círculo interior puede nombrar algo de uno
        exterior: ni una clase, ni una función, ni un formato de datos.

        Mientras más adentro, más ESTABLE y más valioso: las reglas del
        negocio cambian por razones de negocio, no porque salió una versión
        nueva del framework. Mientras más afuera, más DETALLE: cambia
        seguido y por razones técnicas.
    */

    /*
        =================================================================
        CAPA 2) ENTIDADES: REGLAS DE LA EMPRESA
        =================================================================

        Clean Architecture distingue dos tipos de reglas del negocio:

        - REGLAS DE LA EMPRESA (las ENTIDADES, el círculo central): las
          que existirían aunque no hubiera software. Un banco calcula la
          mensualidad de un préstamo con la misma fórmula en la sucursal, en
          la app y en el sistema de cobranza. Son las más estables.

        - REGLAS DE LA APLICACIÓN (los CASOS DE USO, CAPA 3): las que existen
          porque ESTA aplicación automatiza un proceso: "si el solicitante
          tiene un puntaje menor a 650, rechazar en línea". Otra aplicación
          del mismo banco podría tener otras.

        La entidad aquí es Loan: la fórmula de amortización (la mensualidad
        fija de un préstamo), con BigDecimal y su redondeo (v1_fundamentals/
        part4_data_and_input/Numbers, CAPAS 5 y 6). No sabe de solicitudes,
        pantallas ni bases de datos.

            mensualidad = P * r / (1 - (1 + r)^-n)
            P: monto, r: tasa mensual, n: meses

        Medido: $100,000 al 12% anual a 12 meses -> $8,884.88 al mes.
    */
    record Loan(BigDecimal principal, BigDecimal annualRate, int months) {   // entidad: regla de la empresa
        private static final MathContext PRECISION = MathContext.DECIMAL64;

        Loan {
            if (principal.signum() <= 0 || months <= 0 || annualRate.signum() < 0) {
                throw new IllegalArgumentException("Préstamo inválido");
            }
        }

        BigDecimal monthlyPayment() {
            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), PRECISION);
            if (monthlyRate.signum() == 0) {
                return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_EVEN);
            }
            BigDecimal discount = BigDecimal.ONE.subtract(
                    BigDecimal.ONE.divide(BigDecimal.ONE.add(monthlyRate).pow(months), PRECISION));
            return principal.multiply(monthlyRate).divide(discount, PRECISION).setScale(2, RoundingMode.HALF_EVEN);
        }

        BigDecimal totalToPay() {
            return monthlyPayment().multiply(BigDecimal.valueOf(months));
        }
    }

    static boolean entityExample() {
        Loan loan = new Loan(new BigDecimal("100000"), new BigDecimal("0.12"), 12);
        return loan.monthlyPayment().equals(new BigDecimal("8884.88"))
                && loan.totalToPay().equals(new BigDecimal("106618.56"));
    }

    /*
        =================================================================
        CAPA 3) CASOS DE USO: REGLAS DE LA APLICACIÓN
        =================================================================

        Un caso de uso ("solicitar un préstamo en línea") en Clean
        Architecture tiene una forma precisa:

        - recibe un REQUEST MODEL: un record simple con los datos de
          entrada (nada de HTTP);
        - entra por un INPUT BOUNDARY: la interface que el controller llama
          (el puerto de entrada de Hexagonal);
        - usa GATEWAYS para lo que necesita de afuera: aquí, el puntaje de
          crédito del buró (el puerto de salida de Hexagonal);
        - aplica las reglas de ESTA aplicación (el puntaje mínimo, el monto
          máximo para aprobar en línea), y usa la ENTIDAD para la regla de
          la empresa (la mensualidad);
        - y entrega un RESPONSE MODEL... no devolviéndolo, sino a un OUTPUT
          BOUNDARY: la interface que implementa el PRESENTER (CAPA 5).

        El "interactor" (la clase del caso de uso) se prueba con un gateway
        falso y un presenter que solo anota lo que recibe.
    */
    record LoanRequest(String applicantId, BigDecimal amount, int months) {   // request model
    }

    sealed interface LoanDecision {                         // response model: datos del caso de uso, sin formato
    }

    record Approved(String applicantId, BigDecimal monthlyPayment, BigDecimal totalToPay, int months)
            implements LoanDecision {
    }

    record Rejected(String applicantId, String reason) implements LoanDecision {
    }

    interface RequestLoanInputBoundary {                    // lo que llama el controller
        void request(LoanRequest request);
    }

    interface LoanDecisionOutputBoundary {                  // lo que implementa el presenter
        void present(LoanDecision decision);
    }

    interface CreditBureauGateway {                         // lo que el caso de uso necesita de afuera
        int scoreOf(String applicantId);
    }

    static final class RequestLoanInteractor implements RequestLoanInputBoundary {
        static final int MINIMUM_SCORE = 650;               // reglas de ESTA aplicación
        static final BigDecimal MAXIMUM_ONLINE = new BigDecimal("200000");
        static final BigDecimal ANNUAL_RATE = new BigDecimal("0.12");

        private final CreditBureauGateway bureau;
        private final LoanDecisionOutputBoundary presenter;

        RequestLoanInteractor(CreditBureauGateway bureau, LoanDecisionOutputBoundary presenter) {
            this.bureau = bureau;
            this.presenter = presenter;
        }

        @Override
        public void request(LoanRequest request) {
            if (request.amount().compareTo(MAXIMUM_ONLINE) > 0) {
                presenter.present(new Rejected(request.applicantId(), "MONTO_EXCEDE_LINEA"));
                return;
            }
            if (bureau.scoreOf(request.applicantId()) < MINIMUM_SCORE) {
                presenter.present(new Rejected(request.applicantId(), "PUNTAJE_INSUFICIENTE"));
                return;
            }
            Loan loan = new Loan(request.amount(), ANNUAL_RATE, request.months());   // regla de la empresa
            presenter.present(new Approved(request.applicantId(), loan.monthlyPayment(),
                    loan.totalToPay(), request.months()));
        }
    }

    static final Map<String, Integer> SCORES = Map.of("ana", 720, "eve", 580);

    static boolean useCaseExample() {
        List<LoanDecision> presented = new ArrayList<>();
        RequestLoanInputBoundary useCase = new RequestLoanInteractor(SCORES::get, presented::add);   // falsos: lambdas
        useCase.request(new LoanRequest("ana", new BigDecimal("100000"), 12));
        useCase.request(new LoanRequest("eve", new BigDecimal("50000"), 12));
        useCase.request(new LoanRequest("ana", new BigDecimal("900000"), 24));
        return presented.equals(List.of(
                new Approved("ana", new BigDecimal("8884.88"), new BigDecimal("106618.56"), 12),
                new Rejected("eve", "PUNTAJE_INSUFICIENTE"),
                new Rejected("ana", "MONTO_EXCEDE_LINEA")));
    }

    /*
        =================================================================
        CAPA 4) CRUZAR UNA FRONTERA: EL FLUJO VA HACIA AFUERA, LA
                DEPENDENCIA HACIA ADENTRO
        =================================================================

        El caso de uso tiene que entregar su resultado a algo de AFUERA (la
        pantalla). Pero no puede depender de nada de afuera. ¿Cómo?

            FLUJO DE CONTROL (quién llama a quién al ejecutar):
                controller -> interactor -> presenter -> vista
            DEPENDENCIA DEL CÓDIGO (quién conoce a quién al compilar):
                controller -> interactor <- presenter
                                  |
                       define LoanDecisionOutputBoundary

        El interactor llama a una interface que ÉL MISMO define (el output
        boundary), y el presenter, que está afuera, la implementa. En
        ejecución el control va hacia afuera; en el código, la dependencia
        apunta hacia adentro. Es la inversión de dependencias
        (v10_design/part2_solid/DependencyInversion) usada en cada frontera.

        Y lo que cruza la frontera son DATOS SIMPLES (records como Approved
        y Rejected): ni entidades, ni objetos del framework. Si el caso de
        uso entregara la entidad Loan, la vista podría llamar a sus métodos
        y depender de las reglas de la empresa.
    */

    /*
        =================================================================
        CAPA 5) EL PRESENTER: EL CASO DE USO NO DECIDE CÓMO SE VE
        =================================================================

        Lo más característico de Clean Architecture, y lo que Hexagonal no
        detalla: el caso de uso entrega DATOS (8884.88, "PUNTAJE_INSUFICIENTE")
        y un PRESENTER decide cómo mostrarlos:
        - el formato del dinero ($8,884.88 en México);
        - el texto que ve el usuario para cada motivo de rechazo;
        - el código HTTP, o los colores, o el idioma.

        El presenter produce un VIEW MODEL: lo que la vista necesita, ya
        listo, para que la vista sea "humilde" (CAPA 6): solo copia datos a
        la pantalla, sin ninguna decisión.

        El ejemplo: el MISMO caso de uso, con dos presenters:
        - uno web, que produce un código HTTP y un JSON;
        - uno de consola, que produce texto en español con el dinero
          formateado (Locale es-MX).
        El caso de uso no cambió nada para tener dos formas de mostrarse.
    */
    record WebViewModel(int httpStatus, String json) {
    }

    static final class WebPresenter implements LoanDecisionOutputBoundary {
        WebViewModel viewModel;

        @Override
        public void present(LoanDecision decision) {
            viewModel = switch (decision) {
                case Approved(String _, BigDecimal monthly, BigDecimal _, int months) ->
                        new WebViewModel(200, "{\"status\":\"APPROVED\",\"monthlyPayment\":\"" + monthly.toPlainString()
                                + "\",\"months\":" + months + "}");
                case Rejected(String _, String reason) ->
                        new WebViewModel(422, "{\"status\":\"REJECTED\",\"reason\":\"" + reason + "\"}");
            };
        }
    }

    static final class ConsolePresenter implements LoanDecisionOutputBoundary {
        private static final Locale MEXICO = Locale.of("es", "MX");
        String text;

        @Override
        public void present(LoanDecision decision) {
            NumberFormat money = NumberFormat.getCurrencyInstance(MEXICO);   // el formato: decisión del presenter
            text = switch (decision) {
                case Approved(String _, BigDecimal monthly, BigDecimal total, int months) ->
                        "Aprobado: " + months + " pagos de " + money.format(monthly) + " (total " + money.format(total) + ")";
                case Rejected(String _, String reason) -> "Lo sentimos: " + switch (reason) {
                    case "PUNTAJE_INSUFICIENTE" -> "tu historial de crédito no permite aprobarlo en línea.";
                    case "MONTO_EXCEDE_LINEA" -> "ese monto se solicita en sucursal.";
                    default -> "no fue posible aprobarlo.";
                };
            };
        }
    }

    static boolean presentersExample() {
        LoanRequest request = new LoanRequest("ana", new BigDecimal("100000"), 12);

        WebPresenter web = new WebPresenter();
        new RequestLoanInteractor(SCORES::get, web).request(request);

        ConsolePresenter console = new ConsolePresenter();
        new RequestLoanInteractor(SCORES::get, console).request(request);       // el MISMO caso de uso

        ConsolePresenter consoleRejected = new ConsolePresenter();
        new RequestLoanInteractor(SCORES::get, consoleRejected).request(new LoanRequest("eve", new BigDecimal("1000"), 6));

        return web.viewModel.equals(new WebViewModel(200,
                        "{\"status\":\"APPROVED\",\"monthlyPayment\":\"8884.88\",\"months\":12}"))
                && console.text.equals("Aprobado: 12 pagos de $8,884.88 (total $106,618.56)")
                && consoleRejected.text.equals("Lo sentimos: tu historial de crédito no permite aprobarlo en línea.");
    }

    /*
        =================================================================
        CAPA 6) LOS FRAMEWORKS Y LA BASE DE DATOS SON DETALLES
        =================================================================

        La frase más famosa (y más malinterpretada) del libro: "la base de
        datos es un detalle", "la web es un detalle", "el framework es un
        detalle".

        No significa que no importen. Significa que las DECISIONES sobre
        ellos no deben moldear el núcleo: el núcleo no hereda de clases del
        framework, no lleva anotaciones de la base de datos en las
        entidades, y se podría probar y razonar sin ellos.

        Dos ideas que lo hacen posible:
        - EL "MAIN" ES EL COMPONENTE MÁS SUCIO: un lugar en el círculo más
          externo que conoce TODO y lo conecta (el composition root de
          v10_design/part3_responsibilities/DependencyInjection, CAPA 4). En
          una aplicación de Spring, es la configuración de Spring.
        - EL OBJETO HUMILDE (humble object): lo difícil de probar (la
          pantalla, la base de datos, el servidor HTTP) se separa en una
          parte "humilde" sin lógica, que solo copia datos; la lógica se va
          a algo que SÍ se prueba fácil. El presenter y su view model
          (CAPA 5) son eso: toda decisión de formato está en el presenter,
          probado sin pantalla; la vista solo pinta.
    */

    /*
        =================================================================
        CAPA 7) UNA ARQUITECTURA QUE "GRITA" DE QUÉ SE TRATA
        =================================================================

        "Screaming architecture": al abrir el proyecto, las carpetas de
        arriba deberían gritar DE QUÉ trata el sistema, no con qué está
        hecho:

            grita "Spring":                 grita "préstamos":
            controllers/                    loans/
            services/                           application/
            repositories/                       ...
            config/                         payments/
                                            customers/

        Es la organización por funcionalidad de part1_layers/
        LayeredArchitecture, CAPA 7, con la misma razón: lo que cambia
        junto, junto.
    */

    /*
        =================================================================
        CAPA 8) HEXAGONAL, CEBOLLA Y CLEAN: LA MISMA FAMILIA
        =================================================================

            Hexagonal (Cockburn, 2005)       núcleo, puertos y adaptadores;
                                             adentro/afuera
            Cebolla (Palermo, 2008)          capas concéntricas: dominio en el
                                             centro, servicios, infraestructura
                                             afuera
            Clean (Martin, 2012)             círculos con nombre, entidades vs
                                             casos de uso, presenters

        Las tres dicen lo MISMO en lo esencial: el dominio en el centro, sin
        depender de la tecnología; las dependencias hacia adentro; la
        tecnología conectada por interfaces que define el núcleo. Lo demás
        son nombres y nivel de detalle.

        Equivalencias:
            puerto de entrada   = input boundary   (PlaceOrderUseCase)
            puerto de salida    = gateway          (PaymentPort, CreditBureauGateway)
            adaptador           = controller, presenter, gateway implementado
            núcleo              = entidades + casos de uso
    */

    /*
        =================================================================
        CAPA 9) CON PRAGMATISMO: LO QUE SE USA Y LO QUE SOBRA
        =================================================================

        Seguido al pie de la letra, Clean Architecture puede producir, por
        cada caso de uso: un request model, un response model, un input
        boundary, un output boundary, un interactor, un presenter, un view
        model y un controller. Ocho clases para "guardar un cliente". Es la
        crítica más común, y es justa para aplicaciones simples.

        Lo que la mayoría de los equipos SÍ adopta:
        - la regla de dependencia (lo esencial, siempre);
        - entidades y reglas de negocio sin framework;
        - casos de uso con su interface de entrada y sus gateways;
        - datos simples al cruzar las fronteras.

        Lo que se adopta según el caso:
        - el presenter con output boundary: vale la pena cuando hay varias
          formas de mostrar lo mismo o formato complejo. En una API REST
          sencilla, el caso de uso DEVUELVE su response model y el
          controller lo convierte a DTO (v11_patterns/part4_modern/DTO), y
          está bien;
        - entidades separadas de los casos de uso: cuando de verdad hay
          reglas compartidas por varias aplicaciones.

        La pregunta siempre es la misma: ¿esta separación protege algo que
        cambia por razones distintas? Si no, es ceremonia.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Un círculo interior que nombra algo de afuera (una anotación del
           framework en una entidad, un tipo de HTTP en un caso de uso)
           (CAPA 1).

        2) Mezclar reglas de la empresa con reglas de una aplicación
           (CAPAS 2 y 3).

        3) Entregar entidades hacia afuera en vez de datos simples (CAPA 4).

        4) Formato (dinero, fechas, textos al usuario) dentro del caso de
           uso (CAPA 5).

        5) Vistas con lógica que no se puede probar (CAPA 6).

        6) Carpetas que gritan el framework en vez del negocio (CAPA 7).

        7) Aplicar las ocho clases por caso de uso a un CRUD (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Círculos: entidades, casos de uso, adaptadores de interfaz,
          frameworks; el código solo depende hacia adentro.
        - Entidades: reglas de la empresa (la fórmula de la mensualidad).
          Casos de uso: reglas de esta aplicación (puntaje mínimo).
        - El caso de uso entrega datos a un output boundary; el presenter
          decide el formato y produce un view model; la vista es humilde.
        - Flujo hacia afuera, dependencias hacia adentro: inversión de
          dependencias en cada frontera.
        - Los frameworks y la base de datos son detalles; main es el más
          sucio y lo conecta todo.
        - Hexagonal, cebolla y clean: la misma idea con otros nombres.
        - Adoptar la regla de dependencia siempre; el resto, según lo que
          proteja.
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
