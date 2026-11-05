package v11_patterns.part4_modern;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/*
    =====================================================================
    REPOSITORY: EL NEGOCIO NO SABE DÓNDE SE GUARDAN LOS DATOS
    =====================================================================

    Ruta de patterns/: guía 25 de 28  (part4_modern)
    Anterior:  part4_modern/NullObject
    Siguiente: part4_modern/DTO

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Como este proyecto todavía no usa librerías, no hay una base de datos
      real: hay DOS implementaciones del mismo repositorio, una en memoria
      y otra que guarda en un archivo de verdad (en una carpeta temporal
      que se borra al terminar). Así se ve lo esencial: el negocio no
      cambia aunque cambie dónde se guardan los datos.

    Importancia: ALTA. Casi toda aplicación con base de datos usa
    repositorios, y Spring los genera por ti (CAPA 9).

    Índice:
        CAPA 1)  Qué es un repositorio
        CAPA 2)  Diseñar la interface: en el idioma del negocio
        CAPA 3)  Implementación en memoria
        CAPA 4)  Implementación en un archivo
        CAPA 5)  Las mismas pruebas para todas las implementaciones
        CAPA 6)  El negocio, usando el repositorio
        CAPA 7)  Un repositorio por "agregado"
        CAPA 8)  Lo que un repositorio NO debe hacer
        CAPA 9)  Repositorios en Spring
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class Repository {

    public static void main(String[] args) throws IOException {
        Path workDir = Files.createTempDirectory("repository-");
        try {
            check(inMemoryExample(), "CAPA 3");
            check(fileExample(workDir), "CAPA 4");
            check(contractTestsExample(workDir), "CAPA 5");
            check(serviceExample(), "CAPA 6");
        } finally {
            deleteRecursively(workDir);
        }
        System.out.println("Repository: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN REPOSITORIO
        =================================================================

        Martin Fowler lo define así: "media entre el dominio y la capa de
        datos usando una interface PARECIDA A UNA COLECCIÓN para acceder a
        los objetos del dominio."

        La idea: para el código de negocio, los clientes "están en una
        colección": puede AGREGAR uno, BUSCAR por id, BUSCAR por correo,
        QUITAR. No sabe ni le importa si esa "colección" es una tabla de
        PostgreSQL, un archivo, una API de otro sistema o un HashMap.

        Es la inversión de dependencias aplicada a los datos
        (v10_design/part2_solid/DependencyInversion): el negocio define la
        interface en SUS términos; cada tecnología de almacenamiento la
        implementa. Y es un Pure Fabrication de GRASP (v10_design/
        part3_responsibilities/GRASP, CAPA 7): una clase que el negocio no
        tiene, inventada para que Customer no sepa guardarse.

        Consecuencias:
        - el negocio no tiene SQL, ni conexiones, ni conversiones de filas;
        - las pruebas del negocio usan un repositorio en memoria: rápidas,
          sin base de datos;
        - se puede cambiar de tecnología de almacenamiento sin tocar el
          negocio.
    */
    record Customer(long id, String name, String email, boolean active) {
        Customer withActive(boolean newActive) {
            return new Customer(id, name, email, newActive);
        }
    }

    /*
        =================================================================
        CAPA 2) DISEÑAR LA INTERFACE: EN EL IDIOMA DEL NEGOCIO
        =================================================================

        Reglas para la interface del repositorio:

        1) Habla de objetos del DOMINIO (Customer), nunca de filas, tablas,
           conexiones ni tipos de la base de datos (DependencyInversion,
           CAPA 6).

        2) Ofrece SOLO las consultas que el negocio NECESITA, con nombres del
           negocio: findByEmail, findActive. No un método genérico
           "query(String sql)", que sería una abstracción con fugas
           (DependencyInversion, CAPA 5).

        3) Búsqueda de UNO -> Optional (puede no existir; v7_functional/
           part2_streams/OptionalUsage). Búsqueda de VARIOS -> List (vacía
           si no hay; part4_modern/NullObject, CAPA 4). Nunca null.

        4) save tanto crea como actualiza (como un put en un mapa). Si el
           objeto no tiene id, el repositorio le asigna uno y devuelve el
           objeto con su id.

        5) Si una consulta puede devolver MUCHOS (todos los clientes), no la
           ofrezcas sin límite: pide un tamaño de página.
    */
    interface CustomerRepository {
        Customer save(Customer customer);                   // crea (id 0) o actualiza

        Optional<Customer> findById(long id);

        Optional<Customer> findByEmail(String email);       // la consulta que el negocio necesita

        List<Customer> findActive(int limit);               // varios: con límite

        boolean delete(long id);

        long count();
    }

    /*
        =================================================================
        CAPA 3) IMPLEMENTACIÓN EN MEMORIA
        =================================================================

        La implementación más simple: un mapa de id -> objeto. Sirve para
        las PRUEBAS del negocio (CAPA 6), para prototipos, y como
        referencia de cómo debe comportarse cualquier implementación.

        Un detalle que importa: como Customer es un record INMUTABLE, el
        repositorio puede guardarlo tal cual. Si fuera mutable, habría que
        guardar copias: si no, cambiar el objeto afuera cambiaría "la base
        de datos" sin llamar a save, algo que una base de datos real nunca
        haría, y las pruebas en memoria se comportarían distinto que
        producción.
    */
    static final class InMemoryCustomerRepository implements CustomerRepository {
        private final Map<Long, Customer> byId = new HashMap<>();
        private long nextId = 1;

        @Override
        public Customer save(Customer customer) {
            Customer stored = customer.id() == 0
                    ? new Customer(nextId++, customer.name(), customer.email(), customer.active())   // asigna id al crear
                    : customer;
            byId.put(stored.id(), stored);
            return stored;
        }

        @Override
        public Optional<Customer> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Customer> findByEmail(String email) {
            return byId.values().stream().filter(customer -> customer.email().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public List<Customer> findActive(int limit) {
            return byId.values().stream()
                    .filter(Customer::active)
                    .sorted(Comparator.comparingLong(Customer::id))   // orden estable: el mapa no tiene orden
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean delete(long id) {
            return byId.remove(id) != null;
        }

        @Override
        public long count() {
            return byId.size();
        }
    }

    static boolean inMemoryExample() {
        CustomerRepository repository = new InMemoryCustomerRepository();
        Customer ana = repository.save(new Customer(0, "Ana", "ana@correo.com", true));
        repository.save(new Customer(0, "Luis", "luis@correo.com", false));
        return ana.id() == 1
                && repository.findByEmail("ANA@correo.com").isPresent()     // la regla de negocio: sin importar mayúsculas
                && repository.findActive(10).size() == 1
                && repository.findById(99).isEmpty();
    }

    /*
        =================================================================
        CAPA 4) IMPLEMENTACIÓN EN UN ARCHIVO
        =================================================================

        La MISMA interface, guardando en un archivo de texto: una línea por
        cliente ("id;nombre;correo;activo"). Es lo que haría una
        implementación con base de datos, en pequeño:
        - CONVERTIR entre objetos y el formato de almacenamiento (aquí,
          líneas de texto; con una base de datos, filas). A esto se le llama
          "mapeo".
        - Guardar de forma SEGURA: escribe un temporal y lo renombra
          (escritura atómica; v8_apis/part2_io_and_network/FilesAndIO,
          CAPA 16). Un corte a la mitad nunca deja el archivo a medias.
        - PERSISTIR: otra instancia del repositorio (o el programa después
          de reiniciarse) lee los mismos datos.

        No es para producción (cada save reescribe el archivo completo, y no
        soporta varios procesos a la vez): una base de datos existe para
        resolver eso. Lo importante es que el negocio no notaría la
        diferencia.
    */
    static final class FileCustomerRepository implements CustomerRepository {
        private final Path file;

        FileCustomerRepository(Path file) {
            this.file = file;
        }

        private List<Customer> readAll() {
            try {
                if (!Files.exists(file)) {
                    return new ArrayList<>();
                }
                List<Customer> customers = new ArrayList<>();
                for (String line : Files.readAllLines(file)) {
                    String[] parts = line.split(";");       // MAPEO: línea -> objeto
                    customers.add(new Customer(Long.parseLong(parts[0]), parts[1], parts[2], Boolean.parseBoolean(parts[3])));
                }
                return customers;
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo leer " + file, e);   // error técnico, envuelto
            }
        }

        private void writeAll(List<Customer> customers) {
            List<String> lines = customers.stream()         // MAPEO: objeto -> línea
                    .map(c -> c.id() + ";" + c.name() + ";" + c.email() + ";" + c.active())
                    .toList();
            try {
                Path temp = Files.createTempFile(file.getParent(), "customers-", ".tmp");
                Files.write(temp, lines, StandardCharsets.UTF_8);
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo guardar " + file, e);
            }
        }

        @Override
        public Customer save(Customer customer) {
            List<Customer> all = readAll();
            Customer stored = customer.id() == 0
                    ? new Customer(all.stream().mapToLong(Customer::id).max().orElse(0) + 1,
                            customer.name(), customer.email(), customer.active())
                    : customer;
            all.removeIf(existing -> existing.id() == stored.id());
            all.add(stored);
            writeAll(all);
            return stored;
        }

        @Override
        public Optional<Customer> findById(long id) {
            return readAll().stream().filter(customer -> customer.id() == id).findFirst();
        }

        @Override
        public Optional<Customer> findByEmail(String email) {
            return readAll().stream().filter(customer -> customer.email().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public List<Customer> findActive(int limit) {
            return readAll().stream().filter(Customer::active)
                    .sorted(Comparator.comparingLong(Customer::id)).limit(limit).toList();
        }

        @Override
        public boolean delete(long id) {
            List<Customer> all = readAll();
            boolean removed = all.removeIf(customer -> customer.id() == id);
            if (removed) {
                writeAll(all);
            }
            return removed;
        }

        @Override
        public long count() {
            return readAll().size();
        }
    }

    static boolean fileExample(Path dir) {
        Path file = dir.resolve("customers.txt");
        new FileCustomerRepository(file).save(new Customer(0, "Mia", "mia@correo.com", true));

        CustomerRepository afterRestart = new FileCustomerRepository(file);   // otra instancia: como reiniciar el programa
        return afterRestart.findByEmail("mia@correo.com").map(Customer::id).orElse(-1L) == 1
                && Files.exists(file);
    }

    /*
        =================================================================
        CAPA 5) LAS MISMAS PRUEBAS PARA TODAS LAS IMPLEMENTACIONES
        =================================================================

        Dos implementaciones de una interface deben comportarse IGUAL ante
        el negocio (v10_design/part2_solid/LiskovSubstitution). La forma de
        asegurarlo son las PRUEBAS DE CONTRATO (LiskovSubstitution, CAPA 11):
        escribir las pruebas UNA vez contra la interface y correrlas contra
        cada implementación.

        Aquí, el mismo método honorsContract revisa: asignación de ids,
        actualización, búsqueda sin importar mayúsculas, límite, borrado y
        conteo. Pasa con la de memoria Y con la de archivo. Si mañana se
        escribe la de PostgreSQL, tiene que pasar las mismas pruebas. Así,
        las pruebas del negocio con la versión en memoria valen también
        para producción.
    */
    static boolean honorsContract(Supplier<CustomerRepository> factory) {
        CustomerRepository repository = factory.get();
        Customer ana = repository.save(new Customer(0, "Ana", "ana@correo.com", true));
        Customer luis = repository.save(new Customer(0, "Luis", "luis@correo.com", true));
        repository.save(new Customer(0, "Mia", "mia@correo.com", false));

        boolean idsAssigned = ana.id() != luis.id() && ana.id() > 0;
        repository.save(luis.withActive(false));                             // actualizar
        boolean updated = repository.findById(luis.id()).map(Customer::active).orElse(true) == false;
        boolean caseInsensitive = repository.findByEmail("ANA@CORREO.COM").isPresent();
        boolean limited = repository.findActive(1).size() == 1 && repository.findActive(10).size() == 1;
        boolean deleted = repository.delete(ana.id()) && !repository.delete(ana.id());   // la segunda vez ya no existe
        return idsAssigned && updated && caseInsensitive && limited && deleted && repository.count() == 2;
    }

    static boolean contractTestsExample(Path dir) {
        return honorsContract(InMemoryCustomerRepository::new)
                && honorsContract(() -> new FileCustomerRepository(dir.resolve("contract.txt")));
    }

    /*
        =================================================================
        CAPA 6) EL NEGOCIO, USANDO EL REPOSITORIO
        =================================================================

        El servicio de registro tiene una regla: no puede haber dos clientes
        con el mismo correo. Lo escribe en términos del negocio, usando el
        repositorio, sin una línea de almacenamiento. Y se prueba con la
        implementación en memoria, en microsegundos.
    */
    static final class EmailAlreadyRegisteredException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        EmailAlreadyRegisteredException(String email) {
            super("Ya existe un cliente con el correo " + email);
        }
    }

    static final class RegistrationService {
        private final CustomerRepository customers;

        RegistrationService(CustomerRepository customers) {
            this.customers = customers;
        }

        Customer register(String name, String email) {
            if (customers.findByEmail(email).isPresent()) {
                throw new EmailAlreadyRegisteredException(email);
            }
            return customers.save(new Customer(0, name, email.toLowerCase(), true));
        }
    }

    static boolean serviceExample() {
        RegistrationService service = new RegistrationService(new InMemoryCustomerRepository());
        Customer ana = service.register("Ana", "Ana@Correo.com");
        boolean duplicateRejected;
        try {
            service.register("Otra Ana", "ana@correo.com");
            duplicateRejected = false;
        } catch (EmailAlreadyRegisteredException e) {
            duplicateRejected = true;
        }
        return ana.email().equals("ana@correo.com") && duplicateRejected;
    }

    /*
        =================================================================
        CAPA 7) UN REPOSITORIO POR "AGREGADO"
        =================================================================

        ¿Un repositorio por tabla? No: uno por cada objeto PRINCIPAL del
        negocio, con lo que le pertenece.

        Un pedido tiene líneas; las líneas no tienen sentido sin su pedido
        (composición; v2_oop/part3_interfaces_and_relationships/
        CompositionAndAggregation, CAPA 5). En el diseño guiado por el
        dominio (DDD; v14_architecture/), el pedido con sus líneas es un
        AGREGADO, y el pedido es su "raíz". Entonces:
        - hay un OrderRepository que guarda y carga el pedido COMPLETO, con
          sus líneas;
        - NO hay un OrderLineRepository: nadie debería poder guardar una
          línea suelta, saltándose las reglas del pedido (el total, el
          máximo de líneas).

        Aunque en la base de datos sean dos tablas, para el negocio es una
        cosa.
    */

    /*
        =================================================================
        CAPA 8) LO QUE UN REPOSITORIO NO DEBE HACER
        =================================================================

        - REGLAS DE NEGOCIO: "no puede haber correos repetidos" va en el
          servicio o en el dominio (CAPA 6), no escondido en el repositorio.
          El repositorio guarda y busca. (La base de datos puede reforzar la
          regla con un índice único, como segunda línea de defensa.)
        - EXPONER LA TECNOLOGÍA: ni devolver tipos de la base de datos, ni
          lanzar sus excepciones técnicas hacia el negocio (en el archivo, la
          IOException se envuelve).
        - DEVOLVER "TODO" SIN LÍMITE: un findAll() con un millón de clientes
          carga todo en memoria. Paginar.
        - LLAMAR A OTROS REPOSITORIOS: si un repositorio coordina varios, esa
          coordinación es negocio y va en un servicio.
        - UNA CONSULTA POR ELEMENTO: cargar 100 pedidos y luego hacer una
          consulta por cada uno para traer su cliente son 101 consultas (el
          famoso problema "N+1"). Si el negocio necesita pedidos con su
          cliente, el repositorio ofrece UNA consulta que los traiga juntos.
    */

    /*
        =================================================================
        CAPA 9) REPOSITORIOS EN SPRING
        =================================================================

        Con Spring (v15_spring/), el repositorio se declara como una INTERFACE
        y Spring escribe la implementación por ti al arrancar:

            interface CustomerRepository extends JpaRepository<Customer, Long> {
                Optional<Customer> findByEmailIgnoreCase(String email);
                List<Customer> findByActiveTrue(Pageable page);
            }

        A partir del NOMBRE del método (findBy + campo + condición), Spring
        genera la consulta. La implementación es un proxy generado
        (part2_structural/Proxy, CAPA 5).

        Todo lo de esta guía sigue valiendo: nombres del negocio, Optional,
        paginación, un repositorio por agregado, sin reglas de negocio
        adentro. Y un cuidado extra: JpaRepository trae decenas de métodos
        (findAll, deleteAll...); que tu servicio dependa de una interface
        más pequeña con solo lo que usa (v10_design/part2_solid/
        InterfaceSegregation).
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) SQL, conexiones o conversiones de filas dentro del código de
           negocio (CAPA 1).

        2) Interfaces genéricas tipo query(String) o con tipos de la base de
           datos (CAPA 2).

        3) Devolver null en vez de Optional o lista vacía (CAPA 2).

        4) Implementaciones en memoria que no se comportan como la real (sin
           pruebas de contrato) (CAPA 5).

        5) Un repositorio por tabla en vez de por agregado (CAPA 7).

        6) Reglas de negocio dentro del repositorio; findAll sin límite;
           consultas N+1 (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Repositorio: una interface tipo colección de objetos del dominio
          que esconde dónde y cómo se guardan.
        - En el idioma del negocio: findByEmail, Optional para uno, List con
          límite para varios, save que crea o actualiza.
        - Implementaciones intercambiables (memoria, archivo, base de datos)
          que pasan las MISMAS pruebas de contrato.
        - El negocio se prueba con la de memoria, sin base de datos.
        - Uno por agregado; sin reglas de negocio; sin exponer la
          tecnología; sin N+1.
        - Spring genera la implementación a partir de los nombres de los
          métodos.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static void deleteRecursively(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
