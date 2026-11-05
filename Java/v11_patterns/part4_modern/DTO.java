package v11_patterns.part4_modern;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/*
    =====================================================================
    DTO (DATA TRANSFER OBJECT): LO QUE SALE Y ENTRA POR LA FRONTERA
    =====================================================================

    Ruta de patterns/: guía 26 de 28  (part4_modern)
    Anterior:  part4_modern/Repository
    Siguiente: part4_modern/ServiceLayer

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Como el proyecto todavía no usa librerías, no hay JSON de verdad. Al
      final hay dos utilidades pequeñas (toJsonFields y bindJson) que hacen
      lo MISMO que hace Jackson, la librería de JSON que usa Spring: leer y
      llenar un record por los NOMBRES de sus componentes. Con eso se ven
      los problemas reales sin instalar nada.

    Importancia: ALTA. Toda API (REST, mensajes entre sistemas) los usa, y
    los errores de esta guía son fugas de datos reales.

    Índice:
        CAPA 1)  El problema: exponer el objeto del dominio
        CAPA 2)  Qué es un DTO
        CAPA 3)  DTO de respuesta y su mapeo
        CAPA 4)  DTO de entrada: el ataque de "asignación masiva"
        CAPA 5)  Validar en la frontera (y no filtrar contraseñas al log)
        CAPA 6)  Un DTO por caso de uso
        CAPA 7)  DTOs anidados y listas
        CAPA 8)  El DTO es un contrato: el dominio cambia, la API no
        CAPA 9)  Cuándo NO usar DTOs
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class DTO {

    public static void main(String[] args) {
        check(leakExample(), "CAPA 1");
        check(responseExample(), "CAPA 3");
        check(massAssignmentExample(), "CAPA 4");
        check(validationExample(), "CAPA 5");
        check(nestedExample(), "CAPA 7");
        check(contractExample(), "CAPA 8");
        System.out.println("DTO: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: EXPONER EL OBJETO DEL DOMINIO
        =================================================================

        El objeto del dominio (el que usa el negocio y guarda el
        repositorio; part4_modern/Repository) tiene TODO lo que el sistema
        necesita saber de un usuario: también el hash de la contraseña, los
        intentos fallidos de login, notas internas.

        Lo más rápido es devolverlo tal cual en la API: "que Spring lo
        convierta a JSON". Y Jackson convierte TODOS sus componentes (o
        getters). Resultado: el hash de la contraseña sale en la respuesta
        a cualquiera que pida el perfil.

        No es un error de "olvido": es el diseño. Cada campo que alguien
        agregue mañana al dominio saldrá automáticamente por la API, sin
        que nadie lo decida.
    */
    record User(long id, String email, String passwordHash, int failedLogins) {
    }

    static boolean leakExample() {
        User user = new User(7, "ana@correo.com", "$2a$10$hashSecreto", 2);
        Map<String, Object> json = toJsonFields(user);              // lo que haría Jackson
        return json.containsKey("passwordHash") && json.containsKey("failedLogins");   // fuga
    }

    /*
        =================================================================
        CAPA 2) QUÉ ES UN DTO
        =================================================================

        Un DTO es un objeto SOLO con datos, sin comportamiento, hecho para
        CRUZAR UNA FRONTERA: salir o entrar por una API HTTP, una cola de
        mensajes, un archivo de exportación.

        Origen: Martin Fowler lo describió para llamadas REMOTAS. Cada
        llamada por red es lenta (v8_apis/part2_io_and_network/
        HttpClientUsage), así que en vez de pedir el nombre, luego el
        correo, luego la dirección (tres viajes), se manda UN objeto con
        todo lo necesario (un viaje). Hoy el uso principal es el otro
        beneficio: SEPARAR lo que ve el cliente de lo que tiene el dominio.

        En Java moderno un DTO es un RECORD:
        - inmutable: nadie lo cambia a mitad del camino;
        - equals, hashCode y toString gratis;
        - Jackson sabe leer y crear records directamente.

        Dominio vs DTO:

            Objeto del dominio                DTO
            ------------------------------    ------------------------------
            tiene reglas y comportamiento     solo datos
            lo diseña el negocio              lo diseña el contrato con el cliente
            cambia cuando cambia el negocio   cambia con cuidado (CAPA 8)
            nunca sale del sistema            está hecho para salir
    */

    /*
        =================================================================
        CAPA 3) DTO DE RESPUESTA Y SU MAPEO
        =================================================================

        El DTO de respuesta tiene SOLO lo que el cliente debe ver. Pasar del
        dominio al DTO se llama MAPEO, y lo normal es un método estático
        from(...) en el propio DTO.

        ¿Por qué en el DTO y no en User? Por la dirección de las
        dependencias (v10_design/part2_solid/DependencyInversion): la capa
        de la API conoce al dominio, pero el dominio NO conoce a la API. Si
        User tuviera un toResponse(), el núcleo del negocio dependería de
        un detalle de la frontera.

        Ahora un campo nuevo en User NO sale por la API hasta que alguien lo
        agregue al DTO a propósito. La fuga de la CAPA 1 ya no es posible
        por accidente.
    */
    record UserResponse(long id, String email) {
        static UserResponse from(User user) {
            return new UserResponse(user.id(), user.email());
        }
    }

    static boolean responseExample() {
        User user = new User(7, "ana@correo.com", "$2a$10$hashSecreto", 2);
        Map<String, Object> json = toJsonFields(UserResponse.from(user));
        return json.keySet().equals(Set.of("id", "email"));
    }

    /*
        =================================================================
        CAPA 4) DTO DE ENTRADA: EL ATAQUE DE "ASIGNACIÓN MASIVA"
        =================================================================

        El mismo problema, al revés. El cliente manda un JSON para
        registrarse; si se llena DIRECTAMENTE el objeto del dominio con
        ese JSON, el cliente controla TODOS sus campos, también los que no
        debería:

            { "email": "eve@correo.com", "role": "ADMIN" }

        Se llama ASIGNACIÓN MASIVA (mass assignment u over-posting), y es
        una vulnerabilidad real y conocida: el usuario se registra como
        administrador.

        La solución: un DTO de ENTRADA con solo los campos que el cliente
        PUEDE mandar. El resto lo decide el servidor. El campo "role" del
        JSON no tiene dónde caer y se ignora. (Spring Boot, por defecto,
        ignora los campos que el DTO no tiene, igual que bindJson.)

        Por eso los DTOs de entrada y de salida suelen ser DISTINTOS: lo que
        el cliente puede mandar y lo que puede ver no son lo mismo.
    */
    record Account(String email, String name, String role) {
    }

    record SignUpRequest(String email, String name) {
    }

    static Account signUp(SignUpRequest request) {
        return new Account(request.email(), request.name(), "CUSTOMER");   // el ROL lo decide el servidor
    }

    static boolean massAssignmentExample() {
        Map<String, String> attackerJson = Map.of("email", "eve@correo.com", "name", "Eve", "role", "ADMIN");

        Account naive = bindJson(attackerJson, Account.class);              // llenar el dominio directo
        Account safe = signUp(bindJson(attackerJson, SignUpRequest.class)); // pasar por el DTO

        return naive.role().equals("ADMIN")                                 // el ataque funciona
                && safe.role().equals("CUSTOMER");                          // el ataque no tiene efecto
    }

    /*
        =================================================================
        CAPA 5) VALIDAR EN LA FRONTERA (Y NO FILTRAR CONTRASEÑAS AL LOG)
        =================================================================

        Todo lo que llega de afuera es sospechoso. El constructor compacto
        del record (v2_oop/part4_advanced_types/Records, CAPA 3) es el lugar para
        las validaciones de FORMA, las que no necesitan consultar nada:
        - que el campo venga (Jackson pone null si falta);
        - que no esté vacío;
        - normalizar (quitar espacios, correo en minúsculas).
        Así ningún DTO inválido llega a existir, y el servicio recibe datos
        con forma correcta.

        Las reglas de NEGOCIO ("ese correo ya está registrado") NO van aquí:
        necesitan el repositorio y van en el servicio (part4_modern/
        Repository, CAPA 6; part4_modern/ServiceLayer).

        Y una trampa: el toString() automático de un record imprime TODOS
        sus campos. Si alguien registra la petición en el log ("llegó
        login: " + request), la contraseña queda escrita en texto plano en
        un archivo que leen muchas personas y que se guarda por meses. Los
        DTOs con datos sensibles sobrescriben toString para ocultarlos.
    */
    record LoginRequest(String email, String password) {
        LoginRequest {
            email = requireText(email, "email").strip().toLowerCase(Locale.ROOT);
            requireText(password, "password");
            if (!email.contains("@")) {
                throw new IllegalArgumentException("email no tiene formato de correo");
            }
        }

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=***]";   // nunca la contraseña al log
        }
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
        return value;
    }

    static boolean validationExample() {
        LoginRequest request = bindJson(Map.of("email", "  Ana@Correo.COM ", "password", "s3cr3t"), LoginRequest.class);

        boolean missingRejected;
        try {
            bindJson(Map.of("email", "ana@correo.com"), LoginRequest.class);   // sin password
            missingRejected = false;
        } catch (IllegalArgumentException e) {
            missingRejected = e.getMessage().equals("password es obligatorio");
        }

        return request.email().equals("ana@correo.com")                   // normalizado
                && !request.toString().contains("s3cr3t")                 // no se filtra
                && missingRejected;
    }

    /*
        =================================================================
        CAPA 6) UN DTO POR CASO DE USO
        =================================================================

        Una tentación: UN solo ProductDto para todo, con todos los campos,
        y los que no aplican en null. Problemas:
        - el cliente no sabe qué campos vendrán llenos en cada respuesta;
        - la lista de 500 productos manda la descripción larga de cada uno,
          que nadie va a leer en la lista;
        - para crear, el cliente "puede" mandar un id o un precio de
          costo que no debería (CAPA 4).

        Mejor un DTO por necesidad, cada uno pequeño y completo:

            ProductSummary(id, name, priceCents)              en la lista
            ProductDetail(id, name, priceCents, description)  en el detalle
            CreateProductRequest(name, priceCents, description)  al crear

        Esto también se aprovecha en el repositorio: una consulta que
        devuelve directamente ProductSummary (una "proyección") trae de la
        base de datos solo esas columnas, no el objeto completo.
    */
    record ProductSummary(long id, String name, long priceCents) {
    }

    record ProductDetail(long id, String name, long priceCents, String description) {
    }

    record CreateProductRequest(String name, long priceCents, String description) {
    }

    /*
        =================================================================
        CAPA 7) DTOS ANIDADOS Y LISTAS
        =================================================================

        Un pedido con sus líneas se devuelve como un DTO que contiene una
        lista de DTOs. Tres detalles:

        1) Las líneas también son DTOs. Meter el objeto del dominio
           OrderLine "porque ya existe" trae de vuelta el problema de la
           CAPA 1, solo que un nivel más adentro.

        2) Un record es inmutable, pero una List dentro de él puede no
           serlo. List.copyOf en el constructor compacto hace una copia
           inmutable (v5_collections/part4_tools/ImmutableCollections): si
           quien creó el DTO cambia su lista después, el DTO no cambia.

        3) Los valores CALCULADOS (el total) se calculan al mapear, en el
           servidor. El cliente no debería repetir la regla de cómo se
           suma un pedido. El dinero va en centavos (long) para no tener
           los errores de redondeo de double.
    */
    record OrderLine(String product, int quantity, long unitPriceCents) {
    }

    record Order(String id, List<OrderLine> lines) {
    }

    record OrderLineResponse(String product, int quantity, long subtotalCents) {
    }

    record OrderResponse(String id, List<OrderLineResponse> lines, long totalCents) {
        OrderResponse {
            lines = List.copyOf(lines);                     // inmutable de verdad
        }

        static OrderResponse from(Order order) {
            List<OrderLineResponse> lines = order.lines().stream()
                    .map(line -> new OrderLineResponse(line.product(), line.quantity(),
                            line.quantity() * line.unitPriceCents()))
                    .toList();
            long total = lines.stream().mapToLong(OrderLineResponse::subtotalCents).sum();
            return new OrderResponse(order.id(), lines, total);
        }
    }

    static boolean nestedExample() {
        Order order = new Order("P-1", List.of(new OrderLine("Café", 2, 4_500), new OrderLine("Taza", 1, 12_000)));
        OrderResponse response = OrderResponse.from(order);

        List<OrderLineResponse> mutable = new ArrayList<>(response.lines());
        OrderResponse copy = new OrderResponse("P-2", mutable, 0);
        mutable.clear();                                    // cambiar la lista original...

        return response.totalCents() == 21_000
                && copy.lines().size() == 2;                // ...no cambia el DTO
    }

    /*
        =================================================================
        CAPA 8) EL DTO ES UN CONTRATO: EL DOMINIO CAMBIA, LA API NO
        =================================================================

        Los clientes de la API (la app móvil, otro equipo, otra empresa)
        dependen de los NOMBRES y tipos de los campos del JSON. Renombrar un
        campo los rompe, y la app móvil vieja sigue instalada en miles de
        teléfonos durante meses.

        Con DTOs, el dominio puede cambiar libremente. Ejemplo: el negocio
        decide separar el nombre en nombre y apellido. El dominio cambia;
        el DTO sigue mandando "name", armado con los dos. Los clientes no se
        enteran.

        Sin DTOs (devolviendo el dominio), ese cambio interno habría roto a
        todos los clientes.

        Cuando de verdad hay que cambiar el contrato, las reglas son:
        - AGREGAR campos es seguro (los clientes ignoran los que no
          conocen);
        - QUITAR o RENOMBRAR rompe: se crea una versión nueva de la API
          (por ejemplo /v2/customers) y se mantienen las dos un tiempo.
    */
    record Customer(long id, String firstName, String lastName) {        // dominio, después del cambio
    }

    record CustomerResponse(long id, String name) {                       // contrato: igual que antes
        static CustomerResponse from(Customer customer) {
            return new CustomerResponse(customer.id(), customer.firstName() + " " + customer.lastName());
        }
    }

    static boolean contractExample() {
        Map<String, Object> json = toJsonFields(CustomerResponse.from(new Customer(3, "Ana", "Ruiz")));
        return json.equals(Map.of("id", 3L, "name", "Ana Ruiz"));
    }

    /*
        =================================================================
        CAPA 9) CUÁNDO NO USAR DTOS
        =================================================================

        Los DTOs tienen un costo: más clases y código de mapeo que hay que
        mantener. Se justifican EN LAS FRONTERAS del sistema (lo que entra
        y sale). No se justifican:

        - ENTRE CAPAS INTERNAS: copiar User a UserDto para pasarlo del
          servicio al repositorio, y otra vez a UserEntity... son tres
          copias del mismo dato sin ninguna frontera. Adentro, el dominio
          viaja tal cual.
        - CUANDO EL OBJETO YA ES SOLO DATOS PÚBLICOS: un record del
          dominio como Country(code, name), sin nada interno, puede salir
          tal cual. Si mañana gana un campo interno, ese día se separa.

        El mapeo se escribe a mano (un from() de pocas líneas). Existen
        librerías que generan los mappers, pero para empezar, y en muchos
        proyectos reales, el mapeo a mano es más claro y suficiente.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Devolver el objeto del dominio en la API: hashes, datos
           internos y campos futuros salen sin que nadie lo decida (CAPA 1).

        2) Poner toResponse() en el dominio: el núcleo depende de la API
           (CAPA 3).

        3) Llenar el dominio directo con el JSON de entrada: asignación
           masiva (CAPA 4).

        4) Reglas de negocio en el DTO, o DTOs sin validar la forma
           (CAPA 5).

        5) toString() automático en DTOs con contraseñas o tokens: van a
           parar al log (CAPA 5).

        6) Un DTO gigante con nulls para todos los casos (CAPA 6).

        7) Objetos del dominio dentro de un DTO; listas mutables dentro de
           un record (CAPA 7).

        8) Renombrar o quitar campos de un DTO publicado sin una versión
           nueva (CAPA 8).

        9) DTOs entre capas internas, copiando lo mismo varias veces
           (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - DTO: un record solo con datos para cruzar una frontera (API,
          mensajes). El dominio nunca sale tal cual.
        - Respuesta: solo lo que el cliente debe ver; mapeo con from() en
          el DTO, porque el dominio no conoce a la API.
        - Entrada: solo lo que el cliente puede mandar; el resto lo decide
          el servidor (contra la asignación masiva).
        - Validar la forma en el constructor compacto; las reglas de
          negocio, en el servicio. Ocultar secretos en toString.
        - Un DTO por caso de uso; anidados también DTOs; List.copyOf;
          valores calculados en el servidor.
        - Es un contrato: agregar es seguro; quitar o renombrar requiere una
          versión nueva.
        - Solo en las fronteras, no entre capas internas.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía: hacen, en pequeño, lo mismo que
    // Jackson con los records (leer y llenar por nombre de componente).
    // -----------------------------------------------------------------

    /** Como Jackson al escribir JSON: todos los componentes, por nombre. */
    static Map<String, Object> toJsonFields(Record value) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            try {
                fields.put(component.getName(), component.getAccessor().invoke(value));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return fields;
    }

    /**
     * Como Jackson al leer JSON: llena cada componente con el campo del mismo
     * nombre (null si falta; los campos que el record no tiene se ignoran).
     * Solo entiende componentes String, suficiente para la guía.
     */
    static <R extends Record> R bindJson(Map<String, String> json, Class<R> type) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values = Arrays.stream(components).map(component -> json.get(component.getName())).toArray();
        try {
            return type.getDeclaredConstructor(types).newInstance(values);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException validationError) {
                throw validationError;                      // el error del constructor compacto, tal cual
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
