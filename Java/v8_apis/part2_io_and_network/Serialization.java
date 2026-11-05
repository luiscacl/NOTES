package v8_apis.part2_io_and_network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
    =====================================================================
    SERIALIZACIÓN: DE OBJETOS A BYTES (Y POR QUÉ HOY SE USA JSON)
    =====================================================================

    Ruta de apis/: guía 6 de 6  (part2_io_and_network)
    Anterior:  part2_io_and_network/HttpClientUsage
    Siguiente: v9_tools/.../build/maven/Maven  (última de apis/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Las capas de JSON muestran el código de Jackson (la librería que se
      usa en la práctica) como REFERENCIA, porque este proyecto todavía no
      descarga librerías externas. Se vuelve ejecutable cuando el proyecto
      pase a Maven (sección 9 del roadmap).

    Índice:
        CAPA 1)  El problema: un objeto solo existe dentro de su proceso
        CAPA 2)  Serializar y deserializar
        CAPA 3)  La serialización nativa de Java: Serializable
        CAPA 4)  Grafos de objetos: referencias compartidas y ciclos
        CAPA 5)  transient: lo que no se guarda
        CAPA 6)  serialVersionUID: la versión de la clase
        CAPA 7)  El agujero: deserializar NO llama al constructor
        CAPA 8)  Los records se deserializan de forma segura
        CAPA 9)  El peligro mayor: deserializar datos ajenos
        CAPA 10) Si no hay más remedio: filtros de deserialización
        CAPA 11) JSON: el formato que se usa hoy
        CAPA 12) Escribir JSON a mano: el error de concatenar
        CAPA 13) Jackson: objetos <-> JSON
        CAPA 14) DTO: el formato público no es tu clase interna
        CAPA 15) Cómo elegir
        CAPA 16) Errores comunes
        RESUMEN FINAL
*/
public class Serialization {

    public static void main(String[] args) throws Exception {
        check(roundTripExample(), "CAPA 3");
        check(objectGraphExample(), "CAPA 4");
        check(transientExample(), "CAPA 5");
        check(versionExample(), "CAPA 6");
        check(constructorBypassExample(), "CAPA 7");
        check(recordExample(), "CAPA 8");
        check(filterExample(), "CAPA 10");
        check(jsonEscapingExample(), "CAPA 12");
        check(dtoExample(), "CAPA 14");
        System.out.println("Serialization: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: UN OBJETO SOLO EXISTE DENTRO DE SU PROCESO
        =================================================================

        Un objeto de Java vive en el heap de la JVM, dentro de la memoria
        de UN proceso (FilesAndIO, CAPA 1). Sus campos que son referencias
        (un Order que apunta a su Customer) son, en el fondo, DIRECCIONES
        DE MEMORIA de ese proceso.

        El sistema operativo le da a cada proceso su propio espacio de
        direcciones (memoria virtual): la dirección 0x7f3a...10 de tu
        proceso no significa NADA en otro proceso, ni en otra máquina, ni
        en tu mismo programa mañana. Y cuando el proceso termina, el
        sistema operativo libera toda su memoria: tus objetos desaparecen.

        Pero todo el tiempo necesitas que los datos SALGAN del proceso:
        - guardarlos en un archivo o una base de datos;
        - mandarlos por la red a otro servicio (HttpClientUsage);
        - pasarlos a otro proceso, o a una caché.

        Lo único que sale de un proceso son BYTES (un archivo, un socket).
        Así que hay que convertir el objeto, con sus referencias, en una
        secuencia de bytes que se pueda reconstruir del otro lado.
    */

    /*
        =================================================================
        CAPA 2) SERIALIZAR Y DESERIALIZAR
        =================================================================

        SERIALIZAR: convertir un objeto (y los objetos a los que apunta)
        en una secuencia de bytes, en un FORMATO acordado.
        DESERIALIZAR: el camino de regreso, leer bytes y construir objetos
        nuevos con el mismo contenido.

            Order (en el heap) --serializar--> bytes --(archivo, red)--> bytes --deserializar--> Order (otro heap)

        Lo deserializado es una COPIA: un objeto nuevo en otra parte de la
        memoria (o en otra máquina). Cambiarlo no cambia el original.

        Lo que decide todo es el FORMATO:
        - Binario propio de Java (CAPAS 3 a 10): lo hace Java solo, pero
          solo lo entiende Java, y es peligroso.
        - JSON (CAPAS 11 a 14): texto, lo entiende cualquier lenguaje.
          Es el formato de casi todas las APIs actuales.
        (Existen otros, como XML en sistemas antiguos, o formatos binarios
        compactos entre servicios internos; JSON es el que vas a usar.)
    */

    /*
        =================================================================
        CAPA 3) LA SERIALIZACIÓN NATIVA DE JAVA: SERIALIZABLE
        =================================================================

        Java trae su propio formato desde la versión 1.1:
        1) La clase implementa java.io.Serializable, una interface SIN
           métodos (una "interface marcadora";
           v2_oop/part3_interfaces_and_relationships/Interfaces, CAPA 9):
           solo declara "esta clase permite ser serializada".
        2) ObjectOutputStream.writeObject(objeto) escribe los bytes.
        3) ObjectInputStream.readObject() los lee y devuelve un Object
           (hay que hacer cast).

        Esos streams se conectan a cualquier destino (FilesAndIO, CAPA 9):
        un archivo, un socket o, como en estos ejemplos, un arreglo de
        bytes en memoria.

        Qué se escribe: el nombre COMPLETO de la clase, su versión
        (CAPA 6), y el valor de cada campo. Los bytes empiezan siempre con
        AC ED (el "número mágico" del formato).

        Todos los campos deben ser serializables también (los primitivos,
        String y las colecciones del JDK lo son). Si uno no lo es:
            java.io.NotSerializableException: <nombre de esa clase>

        Detalle que el compilador vigila (-Xlint:serial): un campo
        declarado como List<String> NO garantiza nada, porque List es una
        interface y el objeto real podría ser cualquier implementación, no
        necesariamente serializable. javac avisa: "non-transient instance
        field of a serializable class declared with a non-serializable
        type". Por eso, en una clase Serializable, el campo se declara con
        un tipo concreto que sí lo es (ArrayList) y se copia al recibirlo.
        Es otra señal de lo incómodo que es este mecanismo.
    */
    static final class Customer implements Serializable {
        private static final long serialVersionUID = 1L;   // CAPA 6
        final String name;

        Customer(String name) {
            this.name = name;
        }
    }

    static final class Order implements Serializable {
        private static final long serialVersionUID = 1L;
        final String id;
        final Customer customer;
        final ArrayList<String> items;                      // tipo concreto Serializable (ver arriba)

        Order(String id, Customer customer, List<String> items) {
            this.id = id;
            this.customer = customer;
            this.items = new ArrayList<>(items);
        }
    }

    static final class Logger { }                           // no es Serializable

    static final class AuditedOrder implements Serializable {
        private static final long serialVersionUID = 1L;
        @SuppressWarnings("serial")                         // a propósito: un campo no serializable
        final Logger logger = new Logger();
    }

    static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return in.readObject();
        }
    }

    static boolean roundTripExample() throws Exception {
        Order original = new Order("A-1", new Customer("Ana"), new ArrayList<>(List.of("teclado", "mouse")));
        byte[] data = serialize(original);
        Order copy = (Order) deserialize(data);

        boolean failsWithNonSerializableField;
        try {
            serialize(new AuditedOrder());
            failsWithNonSerializableField = false;
        } catch (NotSerializableException e) {
            failsWithNonSerializableField = e.getMessage().endsWith("Serialization$Logger");
        }

        return copy != original                                 // otro objeto...
                && copy.id.equals("A-1") && copy.customer.name.equals("Ana")
                && copy.items.equals(List.of("teclado", "mouse"))   // ...con el mismo contenido
                && (data[0] & 0xFF) == 0xAC && (data[1] & 0xFF) == 0xED
                && failsWithNonSerializableField;
    }

    /*
        =================================================================
        CAPA 4) GRAFOS DE OBJETOS: REFERENCIAS COMPARTIDAS Y CICLOS
        =================================================================

        Los objetos forman un GRAFO: un pedido apunta a su cliente, el
        cliente a sus pedidos... Serializar un objeto serializa todo lo
        alcanzable desde él.

        La serialización nativa recuerda qué objetos ya escribió. Si
        aparece otra vez el MISMO objeto, escribe solo "el mismo de
        antes". Así:
        - dos referencias al mismo objeto siguen siendo UN objeto al
          deserializar (no dos copias);
        - los CICLOS (A apunta a B y B a A) no causan un ciclo infinito.

        Es una ventaja real frente a JSON, que es un árbol: un objeto
        compartido se escribe dos veces, y un ciclo hace fallar la
        conversión (CAPA 13).
    */
    static final class Node implements Serializable {
        private static final long serialVersionUID = 1L;
        final String name;
        Node next;

        Node(String name) {
            this.name = name;
        }
    }

    static boolean objectGraphExample() throws Exception {
        Node a = new Node("a");
        Node b = new Node("b");
        a.next = b;
        b.next = a;                                         // ciclo
        List<Node> nodes = new ArrayList<>(List.of(a, b, a));   // "a" aparece dos veces

        @SuppressWarnings("unchecked")                      // readObject devuelve Object
        List<Node> copy = (List<Node>) deserialize(serialize(nodes));

        return copy.get(0) == copy.get(2)                   // sigue siendo UN objeto
                && copy.get(0).next.next == copy.get(0)     // el ciclo se reconstruyó
                && copy.get(0) != a;
    }

    /*
        =================================================================
        CAPA 5) TRANSIENT: LO QUE NO SE GUARDA
        =================================================================

        Un campo marcado transient NO se serializa. Al deserializar queda
        con su valor por defecto (null, 0, false), NO con el que le pongas
        al declararlo, porque el constructor no corre (CAPA 7).

        Para qué:
        - Datos SENSIBLES que no deben salir del proceso (una contraseña,
          un token).
        - Cosas que solo tienen sentido dentro del proceso: una conexión
          abierta, un hilo, una caché que se puede recalcular.

        (Los campos static tampoco se serializan: son de la clase, no del
        objeto; v2_oop/part1_basics/Static.)
    */
    static final class Session implements Serializable {
        private static final long serialVersionUID = 1L;
        final String user;
        transient String accessToken = "token-secreto";     // no sale del proceso

        Session(String user) {
            this.user = user;
        }
    }

    static boolean transientExample() throws Exception {
        Session copy = (Session) deserialize(serialize(new Session("ana")));
        return copy.user.equals("ana") && copy.accessToken == null;   // null, no "token-secreto"
    }

    /*
        =================================================================
        CAPA 6) SERIALVERSIONUID: LA VERSIÓN DE LA CLASE
        =================================================================

        Los bytes viajan o se guardan; la CLASE sigue evolucionando. Si
        guardaste un Order en un archivo y después agregaste un campo a la
        clase, ¿se puede leer el archivo viejo?

        Java escribe en los bytes un número de versión de la clase, el
        serialVersionUID, y al leer lo compara con el de la clase actual.
        Si no coinciden: java.io.InvalidClassException ("local class
        incompatible").

        Si no lo declaras, Java lo CALCULA a partir de la forma de la clase
        (nombre, campos, métodos...). Cualquier cambio, incluso agregar un
        método que no tiene nada que ver, cambia el número, y los datos
        guardados se vuelven ilegibles. Además, distintos compiladores
        pueden calcular números distintos.

        Por eso, si una clase es Serializable, se DECLARA:
            private static final long serialVersionUID = 1L;
        y solo se cambia a propósito cuando el cambio es incompatible. (El
        compilador avisa con -Xlint:serial si falta.)
    */
    @SuppressWarnings("serial")                             // a propósito: para ver el número calculado
    static final class WithoutDeclaredVersion implements Serializable {
        int value;
    }

    static boolean versionExample() {
        long declared = ObjectStreamClass.lookup(Order.class).getSerialVersionUID();
        long computed = ObjectStreamClass.lookup(WithoutDeclaredVersion.class).getSerialVersionUID();
        return declared == 1L && computed != 1L && computed != 0L;   // un número largo derivado de la clase
    }

    /*
        =================================================================
        CAPA 7) EL AGUJERO: DESERIALIZAR NO LLAMA AL CONSTRUCTOR
        =================================================================

        Esta es la falla de diseño más importante. Al deserializar una
        clase normal, Java crea el objeto SIN llamar a su constructor y
        escribe los campos directamente con los valores que vienen en los
        bytes.

        Todo lo que tu constructor garantiza (v2_oop/part2_pillars/
        Encapsulation, CAPA 4: invariantes, "el objeto nace válido") NO se
        revisa. Si alguien modifica los bytes, obtiene un objeto que tu
        código jamás permitiría crear.

        El ejemplo lo hace de verdad: serializa una cuenta con saldo 100,
        cambia en los bytes el número 100 por -100, y deserializa. El
        constructor prohíbe saldos negativos... y la cuenta llega con -100.
        A esto se le llama un "constructor extralingüístico": una forma de
        crear objetos que se salta las reglas del lenguaje.

        (Existe una defensa manual, escribir un método privado readObject
        que revise los campos, pero hay que acordarse en cada clase.)
    */
    static int accountConstructorCalls = 0;

    static final class Account implements Serializable {
        private static final long serialVersionUID = 1L;
        final int balanceInCents;

        Account(int balanceInCents) {
            accountConstructorCalls++;
            if (balanceInCents < 0) {
                throw new IllegalArgumentException("El saldo no puede ser negativo");
            }
            this.balanceInCents = balanceInCents;
        }
    }

    /** Cambia la última aparición de un int (4 bytes) dentro de los bytes serializados. */
    static byte[] tamperInt(byte[] data, int oldValue, int newValue) {
        byte[] tampered = data.clone();
        for (int i = tampered.length - 4; i >= 0; i--) {
            if (readInt(tampered, i) == oldValue) {
                tampered[i] = (byte) (newValue >>> 24);
                tampered[i + 1] = (byte) (newValue >>> 16);
                tampered[i + 2] = (byte) (newValue >>> 8);
                tampered[i + 3] = (byte) newValue;
                return tampered;
            }
        }
        throw new IllegalArgumentException("No se encontró el valor " + oldValue);
    }

    private static int readInt(byte[] data, int at) {
        return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | (data[at + 3] & 0xFF);
    }

    static boolean constructorBypassExample() throws Exception {
        byte[] data = serialize(new Account(100));
        accountConstructorCalls = 0;

        Account forged = (Account) deserialize(tamperInt(data, 100, -100));   // bytes modificados por un atacante

        return forged.balanceInCents == -100                 // un objeto "imposible"
                && accountConstructorCalls == 0;             // el constructor nunca se ejecutó
    }

    /*
        =================================================================
        CAPA 8) LOS RECORDS SE DESERIALIZAN DE FORMA SEGURA
        =================================================================

        Los records (v2_oop/part4_advanced_types/Records) se diseñaron
        corrigiendo ese error: un record serializable se deserializa
        LLAMANDO A SU CONSTRUCTOR CANÓNICO con los valores leídos. Las
        validaciones del constructor compacto SÍ se ejecutan.

        Con los mismos bytes manipulados, el record rechaza el valor
        inválido: java.io.InvalidObjectException, con la
        IllegalArgumentException del constructor como causa.

        Regla: si de verdad necesitas serializar con el formato de Java,
        usa records (o clases con un readObject que valide).
    */
    static int moneyConstructorCalls = 0;

    record Money(int cents) implements Serializable {
        Money {
            moneyConstructorCalls++;
            if (cents < 0) {
                throw new IllegalArgumentException("Monto negativo");
            }
        }
    }

    static boolean recordExample() throws Exception {
        byte[] data = serialize(new Money(100));
        moneyConstructorCalls = 0;
        Money copy = (Money) deserialize(data);
        boolean constructorRan = moneyConstructorCalls == 1 && copy.cents() == 100;

        boolean forgeryRejected;
        try {
            deserialize(tamperInt(data, 100, -100));
            forgeryRejected = false;
        } catch (InvalidObjectException e) {
            forgeryRejected = e.getCause() instanceof IllegalArgumentException;   // la validación corrió
        }
        return constructorRan && forgeryRejected;
    }

    /*
        =================================================================
        CAPA 9) EL PELIGRO MAYOR: DESERIALIZAR DATOS AJENOS
        =================================================================

        Lo de la CAPA 7 es solo el comienzo. readObject() lee EN LOS BYTES
        el nombre de la clase que debe crear, y crea un objeto de ESA
        clase, cualquiera que exista en el programa (incluidas todas las
        de las librerías). Mientras lo reconstruye, ejecuta código de esas
        clases (sus métodos readObject, hashCode de los mapas que se
        rellenan, etc.).

        Un atacante que controla los bytes elige qué clases se crean y en
        qué orden. Encadenando clases ordinarias de librerías populares
        (lo que se llama una "cadena de gadgets"), se logró EJECUTAR
        COMANDOS en el servidor (abrir una terminal, borrar archivos...)
        solo enviándole bytes. Esto pasó en servidores reales de grandes
        empresas, y durante años fue una de las causas más frecuentes de
        vulnerabilidades graves en aplicaciones Java. Los propios
        arquitectos del lenguaje han dicho que la serialización fue un
        error de diseño.

        Además de la seguridad, el formato nativo tiene otros problemas:
        - Solo lo entiende Java (un frontend o un servicio en otro
          lenguaje no puede leerlo).
        - Es frágil ante cambios de las clases (CAPA 6).
        - Expone los campos PRIVADOS: el formato es tu implementación
          interna.

        LA REGLA: NUNCA deserialices con ObjectInputStream datos que
        vengan de fuera de tu control (una petición, un archivo subido,
        una cola de mensajes compartida). En código nuevo, no uses la
        serialización nativa: usa JSON (CAPA 11).
    */

    /*
        =================================================================
        CAPA 10) SI NO HAY MÁS REMEDIO: FILTROS DE DESERIALIZACIÓN
        =================================================================

        Si tienes que leer datos en formato nativo (un sistema viejo que
        no puedes cambiar), Java 9 agregó ObjectInputFilter: una LISTA DE
        CLASES PERMITIDAS. Antes de crear cada objeto, el filtro revisa la
        clase; si no está permitida, falla con InvalidClassException
        ("filter status: REJECTED") sin crear nada.

        El patrón se escribe como texto: nombres de clases separados por
        ;, y "!*" al final significa "todo lo demás, rechazado":
            ObjectInputFilter.Config.createFilter("com.tienda.Money;!*")

        Siempre lista de PERMITIDOS (lo que esperas), nunca de prohibidos:
        no puedes adivinar todas las clases peligrosas. También se puede
        configurar para toda la JVM con la propiedad jdk.serialFilter.
    */
    static Object deserializeOnly(byte[] data, String allowedClassesPattern) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(allowedClassesPattern));
            return in.readObject();
        }
    }

    static boolean filterExample() throws Exception {
        String onlyMoney = Money.class.getName() + ";!*";

        Money allowed = (Money) deserializeOnly(serialize(new Money(5)), onlyMoney);

        boolean rejected;
        try {
            deserializeOnly(serialize(new Account(5)), onlyMoney);   // otra clase: ni siquiera se crea
            rejected = false;
        } catch (InvalidClassException e) {
            rejected = e.getMessage().equals("filter status: REJECTED");
        }
        return allowed.cents() == 5 && rejected;
    }

    /*
        =================================================================
        CAPA 11) JSON: EL FORMATO QUE SE USA HOY
        =================================================================

        JSON (JavaScript Object Notation) es un formato de TEXTO para datos.
        Nació en JavaScript pero lo leen y escriben todos los lenguajes, y
        es el formato de casi todas las APIs web.

            {
              "id": "A-1",
              "customer": "Ana",
              "total": 1250.50,
              "paid": true,
              "items": ["teclado", "mouse"],
              "notes": null
            }

        Solo tiene SEIS tipos de valores:
            objeto   { "clave": valor, ... }    (las claves siempre entre comillas dobles)
            arreglo  [ valor, valor, ... ]
            texto    "entre comillas dobles"
            número   10, -3.5, 1e6              (no distingue enteros de decimales)
            booleano true / false
            null

        Lo que NO tiene, y hay que decidir cómo representar:
        - FECHAS: no existen. Se mandan como texto ISO 8601
          ("2024-03-15T10:00:00Z"; DatesAndTimes, CAPA 11).
        - DINERO: un número JSON muchas veces se lee como double en el otro
          lado (en JavaScript, todos los números son double), con sus
          errores de redondeo (v1_fundamentals/part4_data_and_input/Numbers).
          Para montos exactos, se mandan como texto ("1250.50") o en
          centavos enteros (125050).
        - CLASES: el JSON no dice de qué clase es un objeto; quien lo lee
          decide en qué clase convertirlo. (Eso es justamente lo que lo
          hace más seguro que la CAPA 9.)
        - Comentarios, referencias compartidas y ciclos (CAPA 4).

        Al ser texto, también importa el charset: JSON siempre va en UTF-8
        (FilesAndIO, CAPA 7).
    */

    /*
        =================================================================
        CAPA 12) ESCRIBIR JSON A MANO: EL ERROR DE CONCATENAR
        =================================================================

        Tentación: armar el JSON pegando textos:
            "{\"name\": \"" + name + "\"}"

        Funciona con "Ana". Con un nombre como  Ana "la jefa"  produce
            {"name": "Ana "la jefa""}
        que ya NO es JSON válido: la comilla del nombre cierra el texto
        antes de tiempo. Y si el valor viene de un usuario, puede inyectar
        campos: un nombre como  x", "admin": true, "y": "  agrega
        "admin": true a tu JSON. Es el mismo problema que la CAPA 6 de
        HttpClientUsage con las URLs: los datos se mezclan con la sintaxis.

        Dentro de un texto JSON hay que ESCAPAR: \" para comillas, \\ para
        la barra invertida, \n y \t, y para los demás caracteres de
        control, una barra invertida, la letra u y su código en cuatro
        dígitos hexadecimales. El ejemplo implementa esas reglas para que veas qué hace
        una librería por ti. En código real, NUNCA escribas JSON a mano:
        usa la librería (CAPA 13), que escapa siempre.
    */
    static String jsonString(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));   // otros caracteres de control
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    static String toJsonObject(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(field -> jsonString(field.getKey()) + ": " + jsonString(field.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    static boolean jsonEscapingExample() {
        String name = "Ana \"la jefa\"";
        String concatenated = "{\"name\": \"" + name + "\"}";              // ERROR de diseño: no escapa
        String escaped = toJsonObject(Map.of("name", name));

        String attack = "x\", \"admin\": true, \"y\": \"";
        String injected = "{\"name\": \"" + attack + "\"}";

        return concatenated.equals("{\"name\": \"Ana \"la jefa\"\"}")          // comillas sin escapar: inválido
                && escaped.equals("{\"name\": \"Ana \\\"la jefa\\\"\"}")      // {"name": "Ana \"la jefa\""}
                && injected.contains("\"admin\": true")                       // el atacante agregó un campo
                && toJsonObject(Map.of("name", attack)).equals("{\"name\": \"x\\\", \\\"admin\\\": true, \\\"y\\\": \\\"\"}")
                && jsonString("línea1\nlínea2").equals("\"línea1\\nlínea2\"");
    }

    /*
        =================================================================
        CAPA 13) JACKSON: OBJETOS <-> JSON
        =================================================================

        El JDK no trae una librería de JSON. La estándar de la industria
        es JACKSON: la que usa Spring (v15_spring/) por dentro, y la que vas a
        encontrar en casi cualquier proyecto Java. (Existen otras, pero
        con Jackson cubres lo que importa.)

        Su pieza central es ObjectMapper: convierte objetos en JSON y JSON
        en objetos, mirando los campos o los componentes del record. Es
        costoso de crear y seguro con hilos: se crea UNO y se comparte
        (como el HttpClient; HttpClientUsage, CAPA 7).

        Así se ve el código. Es de REFERENCIA (no compila todavía en este
        proyecto, que aún no descarga librerías; ver el encabezado):

            record ProductDto(String sku, String name, BigDecimal price) { }

            ObjectMapper mapper = new ObjectMapper();

            String json = mapper.writeValueAsString(new ProductDto("ABC-123", "Teclado", new BigDecimal("500")));
            // {"sku":"ABC-123","name":"Teclado","price":500}

            ProductDto product = mapper.readValue(json, ProductDto.class);

            List<ProductDto> list = mapper.readValue(jsonArray, new TypeReference<List<ProductDto>>() { });

        Lo que Jackson hace por ti: escapar (CAPA 12), convertir números y
        booleanos, recorrer listas y objetos anidados, y crear el objeto
        del tipo que TÚ le pides (no el que diga el JSON, a diferencia de
        la CAPA 9).

        Detalles que vas a encontrar en la práctica:
        - Anotaciones para ajustar: @JsonProperty("product_name") para
          otro nombre en el JSON, @JsonIgnore para no incluir un campo.
        - Por defecto FALLA si el JSON trae un campo que tu clase no tiene
          (UnrecognizedPropertyException). Para APIs de otros, que agregan
          campos con el tiempo, se desactiva esa regla y se ignoran los
          desconocidos ("lector tolerante").
        - En Jackson 2 (Spring Boot 3), las fechas de java.time necesitan el
          módulo de fechas para salir como texto ISO; Jackson 3 (Spring Boot
          4) ya las trae (v9_tools/.../libraries/jackson/Jackson, CAPA 2).
        - Un ciclo de referencias (CAPA 4) produce un error: el JSON es un
          árbol.
        - NUNCA actives la opción de que el JSON indique qué CLASE crear
          ("default typing"): reabre el peligro de la CAPA 9.

        Cuando el proyecto pase a Maven (tools/), este código se vuelve
        ejecutable y se verá a fondo.
    */

    /*
        =================================================================
        CAPA 14) DTO: EL FORMATO PÚBLICO NO ES TU CLASE INTERNA
        =================================================================

        Error frecuente: convertir directamente a JSON las clases del
        dominio (las que tienen la lógica de negocio o reflejan la base de
        datos). Problemas:
        - Se filtran datos que no debían salir (el hash de la contraseña,
          un costo interno, notas privadas).
        - Cualquier cambio interno (renombrar un campo) rompe a todos los
          que consumen tu API.
        - Las relaciones entre objetos generan JSON enormes o ciclos.

        La solución es un DTO (Data Transfer Object, objeto de
        transferencia): una clase, normalmente un RECORD, que define
        EXACTAMENTE lo que sale o entra por la API. Es un CONTRATO: tu
        clase interna puede cambiar libremente mientras el DTO se mantenga.
        La conversión entre los dos es explícita y está en un solo lugar.

        Versionar el contrato:
        - AGREGAR un campo nuevo es compatible (los clientes tolerantes lo
          ignoran).
        - QUITAR o RENOMBRAR un campo, o cambiar su tipo, rompe a los
          clientes: se hace con una nueva versión de la API (/v2/...).
    */
    static final class UserAccount {                        // clase interna del dominio
        final String email;
        final String passwordHash;                          // jamás debe salir
        final BigDecimal internalCreditScore;               // tampoco
        final Instant createdAt;

        UserAccount(String email, String passwordHash, BigDecimal internalCreditScore, Instant createdAt) {
            this.email = email;
            this.passwordHash = passwordHash;
            this.internalCreditScore = internalCreditScore;
            this.createdAt = createdAt;
        }
    }

    record UserResponse(String email, String memberSince) {    // el contrato público
        static UserResponse from(UserAccount account) {
            return new UserResponse(account.email, account.createdAt.toString());   // fecha como texto ISO
        }
    }

    static boolean dtoExample() {
        UserAccount account = new UserAccount("ana@correo.com", "$2a$10$hash...", new BigDecimal("712"),
                Instant.parse("2024-03-15T10:00:00Z"));
        UserResponse response = UserResponse.from(account);

        Map<String, String> fields = new LinkedHashMap<>();       // conserva el orden de los campos
        fields.put("email", response.email());
        fields.put("memberSince", response.memberSince());
        String json = toJsonObject(fields);

        return json.equals("{\"email\": \"ana@correo.com\", \"memberSince\": \"2024-03-15T10:00:00Z\"}")
                && !json.contains("hash") && !json.contains("712");      // lo interno no salió
    }

    /*
        =================================================================
        CAPA 15) CÓMO ELEGIR
        =================================================================

        | Necesitas                                             | Usa                                  |
        |-------------------------------------------------------|--------------------------------------|
        | Mandar o recibir datos por una API                    | JSON con Jackson + DTOs (records)    |
        | Guardar datos en un archivo que leerá tu programa     | JSON (o una base de datos)           |
        | Datos de otro sistema en formato nativo de Java       | ObjectInputStream CON filtro de      |
        |                                                       | clases permitidas, y planear migrar  |
        | Guardar objetos en una base de datos                  | la base de datos con su librería     |
        |                                                       | (v15_spring/), no serialización          |
        | Copiar un objeto en memoria                           | un constructor o método de copia     |
        |                                                       | (v2_oop/part4_advanced_types/        |
        |                                                       | ObjectContract), no serializar       |

        En resumen: Serializable y ObjectInputStream, prácticamente nunca
        en código nuevo.
    */

    /*
        =================================================================
        CAPA 16) ERRORES COMUNES
        =================================================================

        1) Deserializar con ObjectInputStream datos que no controlas
           (CAPA 9).

        2) Clases Serializable sin serialVersionUID declarado (CAPA 6).

        3) Confiar en las validaciones del constructor de una clase
           Serializable: no corren al deserializar (CAPA 7).

        4) Serializar contraseñas o tokens sin transient (CAPA 5).

        5) Mandar dinero como número JSON y leerlo como double (CAPA 11).

        6) Armar JSON concatenando texto (CAPA 12).

        7) Crear un ObjectMapper por cada conversión (CAPA 13).

        8) Activar en Jackson que el JSON decida qué clase crear (CAPA 13).

        9) Convertir a JSON las clases internas en vez de DTOs; renombrar
           campos de una API publicada (CAPA 14).

        10) Usar la serialización para "clonar" objetos (CAPA 15).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Un objeto vive en la memoria de UN proceso; para que salga hay
          que convertirlo en bytes con un formato.
        - Nativo de Java: Serializable + ObjectOutputStream/ObjectInputStream.
          Maneja grafos y ciclos; transient excluye campos; declara
          serialVersionUID.
        - Su gran falla: no llama al constructor (objetos "imposibles") y
          deserializar datos ajenos permite ejecutar código. Los records sí
          validan. Si no hay remedio: ObjectInputFilter con lista de
          permitidos.
        - Hoy: JSON. Seis tipos; fechas como texto ISO; dinero como texto o
          centavos; UTF-8.
        - Nunca JSON a mano: Jackson (ObjectMapper, uno compartido).
        - DTOs (records) como contrato público; agregar campos es
          compatible, renombrar no.
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
