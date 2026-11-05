package v11_patterns.part3_behavioral;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/*
    =====================================================================
    CHAIN OF RESPONSIBILITY: UNA CADENA DE MANEJADORES
    =====================================================================

    Ruta de patterns/: guía 13 de 28  (part3_behavioral)
    Anterior:  part2_structural/Proxy
    Siguiente: part3_behavioral/Command

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: MEDIA-ALTA. Toda aplicación web procesa cada petición
    con una cadena de "filtros" (CAPA 4), y es la forma natural de
    escribir aprobaciones y validaciones por pasos.

    Índice:
        CAPA 1)  El problema: un if gigante con todos los casos
        CAPA 2)  La solución clásica: cada manejador conoce al siguiente
        CAPA 3)  La versión moderna: una lista de manejadores
        CAPA 4)  La otra forma: filtros que envuelven la petición
        CAPA 5)  El orden de la cadena importa
        CAPA 6)  ¿Y si nadie lo maneja? El final de la cadena
        CAPA 7)  Dónde aparece
        CAPA 8)  Chain of Responsibility y Decorator
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class ChainOfResponsibility {

    public static void main(String[] args) {
        check(bigIfExample(), "CAPA 1");
        check(linkedChainExample(), "CAPA 2");
        check(listChainExample(), "CAPA 3");
        check(filterChainExample(), "CAPA 4");
        check(orderMattersExample(), "CAPA 5");
        check(endOfChainExample(), "CAPA 6");
        System.out.println("ChainOfResponsibility: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: UN IF GIGANTE CON TODOS LOS CASOS
        =================================================================

        Las compras de la empresa necesitan aprobación según el monto:
            hasta $5,000      la aprueba el supervisor
            hasta $50,000     el gerente
            hasta $500,000    el director
            más               se rechaza (va al consejo, fuera del sistema)

        La forma directa es un método con todos los casos:

            if (amount <= 5000) ...supervisor...
            else if (amount <= 50000) ...gerente...
            else if ...

        Funciona, pero:
        - Cada nivel nuevo (un "gerente regional" en medio) o cada cambio de
          orden obliga a MODIFICAR ese método (v10_design/part2_solid/
          OpenClosed).
        - Cada nivel tiene sus propias reglas (el director además pide dos
          cotizaciones; el supervisor no aprueba compras de tecnología) y
          todas terminan en el mismo método, que crece sin fin.
        - Quien envía la solicitud tiene que conocer a todos los posibles
          aprobadores.
    */
    record PurchaseRequest(String item, BigDecimal amount, String category) { }

    static String approveWithIfs(PurchaseRequest request) {
        BigDecimal amount = request.amount();
        if (amount.compareTo(new BigDecimal("5000")) <= 0 && !request.category().equals("tecnologia")) {
            return "supervisor";
        } else if (amount.compareTo(new BigDecimal("50000")) <= 0) {
            return "gerente";
        } else if (amount.compareTo(new BigDecimal("500000")) <= 0) {
            return "director";
        }
        return "rechazada";
    }

    static boolean bigIfExample() {
        return approveWithIfs(new PurchaseRequest("sillas", new BigDecimal("3000"), "mobiliario")).equals("supervisor")
                && approveWithIfs(new PurchaseRequest("laptop", new BigDecimal("3000"), "tecnologia")).equals("gerente");
    }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN CLÁSICA: CADA MANEJADOR CONOCE AL SIGUIENTE
        =================================================================

        CHAIN OF RESPONSIBILITY: "Evita acoplar al emisor de una petición
        con su receptor, dando a varios objetos la oportunidad de
        manejarla. Encadena los receptores y pasa la petición a lo largo de
        la cadena hasta que un objeto la maneje."

        - Cada aprobador es un objeto con SUS reglas y una referencia al
          SIGUIENTE de la cadena.
        - Recibe la solicitud: si puede, la maneja; si no, la PASA al
          siguiente.
        - Quien envía solo conoce al PRIMERO de la cadena.

            solicitud ─> Supervisor ─(no puede)─> Gerente ─(no puede)─> Director ─> ...

        Agregar un gerente regional es una clase nueva y un cambio en cómo
        se arma la cadena; ningún aprobador existente cambia.
    */
    abstract static class Approver {                        // HANDLER
        private Approver next;

        Approver then(Approver next) {                      // arma la cadena: a.then(b).then(c)
            this.next = next;
            return next;
        }

        final String handle(PurchaseRequest request) {
            if (canApprove(request)) {
                return name();
            }
            return next == null ? "rechazada" : next.handle(request);   // pasarla al siguiente
        }

        protected abstract boolean canApprove(PurchaseRequest request);

        protected abstract String name();
    }

    static final class Supervisor extends Approver {       // CONCRETE HANDLER
        @Override
        protected boolean canApprove(PurchaseRequest request) {
            return request.amount().compareTo(new BigDecimal("5000")) <= 0
                    && !request.category().equals("tecnologia");    // su regla propia, en su clase
        }

        @Override
        protected String name() {
            return "supervisor";
        }
    }

    static final class Manager extends Approver {
        @Override
        protected boolean canApprove(PurchaseRequest request) {
            return request.amount().compareTo(new BigDecimal("50000")) <= 0;
        }

        @Override
        protected String name() {
            return "gerente";
        }
    }

    static final class Director extends Approver {
        @Override
        protected boolean canApprove(PurchaseRequest request) {
            return request.amount().compareTo(new BigDecimal("500000")) <= 0;
        }

        @Override
        protected String name() {
            return "director";
        }
    }

    static Approver approvalChain() {
        Approver first = new Supervisor();
        first.then(new Manager()).then(new Director());     // supervisor -> gerente -> director
        return first;
    }

    static final List<PurchaseRequest> SAMPLES = List.of(
            new PurchaseRequest("sillas", new BigDecimal("3000"), "mobiliario"),
            new PurchaseRequest("laptop", new BigDecimal("3000"), "tecnologia"),
            new PurchaseRequest("servidor", new BigDecimal("120000"), "tecnologia"),
            new PurchaseRequest("edificio", new BigDecimal("9000000"), "inmuebles"));

    static boolean linkedChainExample() {
        Approver chain = approvalChain();
        for (PurchaseRequest request : SAMPLES) {
            if (!chain.handle(request).equals(approveWithIfs(request))) {   // mismo resultado que el if gigante
                return false;
            }
        }
        return chain.handle(SAMPLES.get(2)).equals("director");
    }

    /*
        =================================================================
        CAPA 3) LA VERSIÓN MODERNA: UNA LISTA DE MANEJADORES
        =================================================================

        La versión clásica enlaza los manejadores entre sí (cada uno guarda
        su "next"). Hoy es frecuente algo más simple: una LISTA de
        manejadores, y un recorrido que pregunta a cada uno en orden hasta
        que alguno responda.

        - Los manejadores ya no se conocen entre sí; pueden ser lambdas.
        - Reordenar, agregar o quitar es editar una lista (incluso desde
          configuración).
        - La lógica de "pasar al siguiente" vive en UN lugar (el recorrido),
          no repetida en cada manejador.

        Aquí cada manejador devuelve un Optional: con valor si lo manejó,
        vacío si lo deja pasar (v7_functional/part2_streams/OptionalUsage).
        Y el recorrido es un stream que se detiene en el primero que
        responde (findFirst; v7_functional/part2_streams/StreamsBasics,
        CAPA 6).
    */
    interface ApprovalRule {
        Optional<String> tryApprove(PurchaseRequest request);
    }

    static ApprovalRule upTo(String approver, String limit) {   // una "fábrica" de reglas por monto
        return request -> request.amount().compareTo(new BigDecimal(limit)) <= 0 ? Optional.of(approver) : Optional.empty();
    }

    static final List<ApprovalRule> RULES = List.of(
            request -> request.category().equals("tecnologia") ? Optional.empty() : upTo("supervisor", "5000").tryApprove(request),
            upTo("gerente", "50000"),
            upTo("director", "500000"));

    static String approve(PurchaseRequest request) {
        return RULES.stream()
                .map(rule -> rule.tryApprove(request))
                .flatMap(Optional::stream)
                .findFirst()                                // el primero que la maneja; los demás ni se evalúan
                .orElse("rechazada");
    }

    static boolean listChainExample() {
        for (PurchaseRequest request : SAMPLES) {
            if (!approve(request).equals(approveWithIfs(request))) {
                return false;
            }
        }
        return true;
    }

    /*
        =================================================================
        CAPA 4) LA OTRA FORMA: FILTROS QUE ENVUELVEN LA PETICIÓN
        =================================================================

        En la versión de la CAPA 2, UNO maneja la petición y ahí termina.
        Hay una segunda forma, la que más vas a ver: CADA eslabón hace algo
        con la petición y DECIDE si la deja seguir. Se le llama cadena de
        FILTROS o "middleware", y es como se procesa CADA petición en una
        aplicación web:

            petición ─> [registro] ─> [autenticación] ─> [límite de uso] ─> lógica de la página
                                           │
                                  (sin sesión: responde 401 y la petición NO sigue)

        Cada filtro recibe la petición y "el resto de la cadena":
        - puede DETENERLA (no llama al siguiente) y responder él mismo;
        - puede hacer algo ANTES de pasarla y algo DESPUÉS de que regrese
          (medir el tiempo total, agregar encabezados a la respuesta).
        Por eso la ejecución es como capas de cebolla: entra por el primero
        y sale, al final, también por el primero.

        El ejemplo construye una mini cadena de filtros web: un registro que
        anota la entrada y la salida, una autenticación que corta si no hay
        usuario, y al final la "página".
    */
    record HttpRequest(String path, Map<String, String> headers) { }

    record HttpResponse(int status, String body) { }

    interface Filter {
        HttpResponse filter(HttpRequest request, FilterChain chain);
    }

    static final class FilterChain {                        // "el resto de la cadena"
        private final List<Filter> filters;
        private final int position;
        private final Function<HttpRequest, HttpResponse> endpoint;

        FilterChain(List<Filter> filters, Function<HttpRequest, HttpResponse> endpoint) {
            this(filters, 0, endpoint);
        }

        private FilterChain(List<Filter> filters, int position, Function<HttpRequest, HttpResponse> endpoint) {
            this.filters = filters;
            this.position = position;
            this.endpoint = endpoint;
        }

        HttpResponse proceed(HttpRequest request) {
            if (position == filters.size()) {
                return endpoint.apply(request);             // pasó todos los filtros: la lógica real
            }
            return filters.get(position).filter(request, new FilterChain(filters, position + 1, endpoint));
        }
    }

    static Filter logging(List<String> log) {
        return (request, chain) -> {
            log.add("entra " + request.path());             // ANTES
            HttpResponse response = chain.proceed(request);
            log.add("sale " + response.status());           // DESPUÉS: ve la respuesta final
            return response;
        };
    }

    static Filter authentication() {
        return (request, chain) -> {
            if (!request.headers().containsKey("Authorization")) {
                return new HttpResponse(401, "Inicia sesión");      // CORTA: la petición no sigue
            }
            return chain.proceed(request);
        };
    }

    static boolean filterChainExample() {
        List<String> log = new ArrayList<>();
        FilterChain chain = new FilterChain(List.of(logging(log), authentication()),
                request -> new HttpResponse(200, "Tus pedidos"));

        HttpResponse ok = chain.proceed(new HttpRequest("/pedidos", Map.of("Authorization", "Bearer abc")));
        HttpResponse denied = chain.proceed(new HttpRequest("/pedidos", Map.of()));

        return ok.equals(new HttpResponse(200, "Tus pedidos"))
                && denied.equals(new HttpResponse(401, "Inicia sesión"))
                && log.equals(List.of("entra /pedidos", "sale 200", "entra /pedidos", "sale 401"));   // el registro vio las dos
    }

    /*
        =================================================================
        CAPA 5) EL ORDEN DE LA CADENA IMPORTA
        =================================================================

        Los mismos filtros en otro orden hacen OTRA cosa:
        - registro ANTES de autenticación: se registran TODAS las
          peticiones, incluidas las rechazadas (útil para detectar ataques).
        - registro DESPUÉS de autenticación: solo se registran las que
          pasaron; los intentos sin sesión no dejan rastro.

        En las aprobaciones (CAPA 2) pasa lo mismo: si el director estuviera
        PRIMERO, aprobaría todo hasta $500,000 y el supervisor nunca vería
        nada. Por eso el orden de la cadena se define en UN lugar visible,
        con intención, y se prueba.
    */
    static boolean orderMattersExample() {
        List<String> logFirst = new ArrayList<>();
        List<String> logAfterAuth = new ArrayList<>();
        HttpRequest anonymous = new HttpRequest("/admin", Map.of());

        new FilterChain(List.of(logging(logFirst), authentication()), r -> new HttpResponse(200, "ok")).proceed(anonymous);
        new FilterChain(List.of(authentication(), logging(logAfterAuth)), r -> new HttpResponse(200, "ok")).proceed(anonymous);

        Approver directorFirst = new Director();
        directorFirst.then(new Supervisor());

        return logFirst.equals(List.of("entra /admin", "sale 401"))     // vio el intento rechazado
                && logAfterAuth.isEmpty()                               // no vio nada
                && directorFirst.handle(SAMPLES.get(0)).equals("director");   // el supervisor nunca llegó a verla
    }

    /*
        =================================================================
        CAPA 6) ¿Y SI NADIE LO MANEJA? EL FINAL DE LA CADENA
        =================================================================

        Un riesgo de la cadena: la petición llega al final y NADIE la
        manejó. Si la cadena simplemente termina sin decir nada, la
        solicitud de compra "desaparece": ni aprobada ni rechazada.

        La cadena siempre necesita un final EXPLÍCITO:
        - un último manejador que rechaza con un motivo (como el
          "rechazada" de las CAPAS 2 y 3);
        - o lanzar una excepción clara ("ningún aprobador para esta
          solicitud");
        - y en la cadena de filtros, el endpoint al final (CAPA 4).

        Fíjate en que el resultado del ejemplo no es solo "rechazada": dice
        POR QUÉ, para que alguien pueda actuar.
    */
    static String approveOrExplain(PurchaseRequest request) {
        return RULES.stream()
                .map(rule -> rule.tryApprove(request))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> "rechazada: " + request.amount() + " excede el límite de aprobación; requiere consejo");
    }

    static boolean endOfChainExample() {
        return approveOrExplain(SAMPLES.get(3)).equals("rechazada: 9000000 excede el límite de aprobación; requiere consejo");
    }

    /*
        =================================================================
        CAPA 7) DÓNDE APARECE
        =================================================================

        - FILTROS WEB: en Java, la especificación de servidores web
          (Servlet) define Filter y FilterChain casi idénticos a los de la
          CAPA 4; la seguridad de Spring es una cadena de más de diez
          filtros (v15_spring/).
        - LAS EXCEPCIONES: cuando un método lanza una excepción y no la
          atrapa, sube al método que lo llamó, y al que llamó a ese... hasta
          que un catch la "maneja" (v3_exceptions/part1_concepts/
          WhatAreExceptions). Es una cadena de responsabilidad que la JVM
          recorre por la pila de llamadas.
        - EL LOGGING del JDK (java.util.logging): un registro se pasa del
          logger hijo a sus padres ("com.tienda.pagos" -> "com.tienda" ->
          raíz), y cada uno puede manejarlo con sus propios destinos.
        - VALIDACIONES en cadena (v7_functional/part1_lambdas/
          FunctionalInterfaces, CAPA 9: Validator con and()), reglas de
          descuento, clasificadores de tickets de soporte.
    */

    /*
        =================================================================
        CAPA 8) CHAIN OF RESPONSIBILITY Y DECORATOR
        =================================================================

        Los filtros de la CAPA 4 se parecen mucho a decoradores apilados
        (part2_structural/Decorator, CAPA 3): cada uno envuelve al
        siguiente y puede actuar antes y después. La diferencia:

        | Aspecto                   | Chain of Responsibility             | Decorator                         |
        |---------------------------|-------------------------------------|-----------------------------------|
        | ¿Siempre llega al final?  | NO: un eslabón puede detenerla      | sí: cada capa delega siempre      |
        | Intención                 | decidir QUIÉN maneja (o si sigue)   | AGREGAR comportamiento            |
        | Forma típica              | lista ordenada de manejadores       | objetos envueltos uno en otro     |
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Un método con todos los casos en ifs que crece con cada regla
           nueva (CAPA 1).

        2) Manejadores que conocen a los demás o dependen de su posición
           (CAPA 3).

        3) Armar la cadena en varios lugares, o en un orden sin intención
           (CAPA 5).

        4) Una cadena sin final explícito: peticiones que nadie maneja y
           desaparecen en silencio (CAPA 6).

        5) Un filtro que olvida llamar a chain.proceed cuando sí debía
           dejarla pasar: la petición se "pierde" sin error (CAPA 4).

        6) Cadenas tan largas que nadie sabe qué eslabón respondió:
           registrar (log) cuál manejó cada petición ayuda a depurar.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Chain of Responsibility: varios manejadores en cadena; la petición
          pasa hasta que uno la maneja. El emisor solo conoce el inicio.
        - Clásica: cada manejador tiene un next. Moderna: una lista
          recorrida en orden (lambdas, Optional, findFirst).
        - Filtros/middleware: cada eslabón actúa antes y después, y puede
          cortar la cadena (autenticación -> 401).
        - El orden cambia el resultado: se define en un lugar y se prueba.
        - Siempre un final explícito, con motivo.
        - Aparece en filtros web (Servlet, seguridad de Spring), en la
          propagación de excepciones y en el logging del JDK.
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
