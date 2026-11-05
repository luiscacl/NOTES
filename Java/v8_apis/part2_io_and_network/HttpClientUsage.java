package v8_apis.part2_io_and_network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
    =====================================================================
    HTTP CON JAVA.NET.HTTP: LA RED DESDE EL PROGRAMA HASTA EL KERNEL
    =====================================================================

    Ruta de apis/: guía 5 de 6  (part2_io_and_network)
    Anterior:  part2_io_and_network/FilesAndIO
    Siguiente: part2_io_and_network/Serialization

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Los ejemplos NO usan internet: main levanta un servidor HTTP de
      prueba en tu propia máquina (TestServer, al final de la CAPA 2) y
      lo apaga al terminar.

    Nota de nombres: la guía se llama HttpClientUsage y no HttpClient
    para no tapar a java.net.http.HttpClient, que es la clase que se
    estudia.

    Sobre el sistema operativo: igual que en FilesAndIO, se usa LINUX
    como referencia. Las cifras medidas son de la máquina de prueba
    (Windows 11, JDK 26).

    Índice:
        CAPA 1)  Qué pasa en una petición HTTP, de punta a punta
        CAPA 2)  Puertos, sockets y la dirección 127.0.0.1
        CAPA 3)  Las tres piezas: HttpClient, HttpRequest, BodyHandler
        CAPA 4)  Un 404 NO es una excepción
        CAPA 5)  Enviar datos: métodos, encabezados y cuerpo
        CAPA 6)  URLs y parámetros: codificar
        CAPA 7)  Reusar el cliente es reusar la conexión TCP
        CAPA 8)  Timeouts: por defecto, Java espera para siempre
        CAPA 9)  Errores de red: qué falló y dónde
        CAPA 10) Redirecciones
        CAPA 11) Reintentos: cuándo sí y cuándo no
        CAPA 12) Esperar la red bloquea un hilo: sendAsync
        CAPA 13) Respuestas grandes
        CAPA 14) HTTP/2
        CAPA 15) Seguridad
        CAPA 16) Errores comunes
        RESUMEN FINAL
*/
public class HttpClientUsage {

    public static void main(String[] args) throws Exception {
        try (TestServer server = TestServer.start();
             HttpClient client = HttpClient.newBuilder()
                     .connectTimeout(Duration.ofSeconds(2))
                     .build()) {
            String base = server.baseUrl();
            check(firstRequestExample(client, base), "CAPA 3");
            check(statusCodeExample(client, base), "CAPA 4");
            check(postExample(client, base), "CAPA 5");
            check(encodingExample(client, base), "CAPA 6");
            check(connectionReuseExample(client, base), "CAPA 7");
            check(timeoutExample(client, base), "CAPA 8");
            check(networkErrorExample(client), "CAPA 9");
            check(redirectExample(client, base), "CAPA 10");
            check(retryExample(client, base), "CAPA 11");
            check(asyncExample(client, base), "CAPA 12");
            check(largeResponseExample(client, base), "CAPA 13");
            check(http2Example(client, base), "CAPA 14");
        }
        System.out.println("HttpClientUsage: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ PASA EN UNA PETICIÓN HTTP, DE PUNTA A PUNTA
        =================================================================

        Pedir https://api.tienda.com/productos/7 parece una sola cosa. Por
        debajo son varios pasos, y casi todos los hace el SISTEMA
        OPERATIVO, no Java:

        1) DNS: traducir el NOMBRE (api.tienda.com) a una DIRECCIÓN IP
           (93.184.216.34). Primero se revisa /etc/hosts (en Linux), luego
           se pregunta a un servidor DNS por la red. La JVM guarda las
           respuestas un tiempo (caché de DNS).

        2) CONEXIÓN TCP: el proceso le pide al kernel un SOCKET (un
           "enchufe" de red) y que lo conecte a esa IP y puerto (443 para
           HTTPS, 80 para HTTP). Igual que un archivo abierto, el socket es
           un DESCRIPTOR en la tabla del proceso (FilesAndIO, CAPA 2). El
           kernel hace el "saludo de tres pasos" (three-way handshake) con
           la otra máquina:
               cliente -> SYN       ("quiero conectarme")
               servidor -> SYN-ACK  ("ok, yo también")
               cliente -> ACK       ("listo")
           Eso es UN viaje de ida y vuelta por la red antes de mandar nada.

        3) TLS (solo HTTPS): otro saludo para acordar cifrado y verificar
           el certificado del servidor. Uno o dos viajes más.

        4) LA PETICIÓN: por fin se manda texto como este:
               GET /productos/7 HTTP/1.1
               Host: api.tienda.com
               Accept: application/json
               (línea vacía; luego el cuerpo, si hay)

        5) LA RESPUESTA:
               HTTP/1.1 200 OK
               Content-Type: application/json
               Content-Length: 42
               (línea vacía)
               {"id": 7, "nombre": "teclado", ...}

        6) La conexión se queda ABIERTA un rato (keep-alive) por si hay
           más peticiones al mismo servidor (CAPA 7).

        Cada viaje de ida y vuelta (round trip) entre ciudades cuesta
        decenas de milisegundos; entre continentes, más de 100. Por eso una
        conexión NUEVA (DNS + TCP + TLS) puede tardar más que la petición
        misma, y por eso reusar conexiones importa tanto.

        En Linux puedes ver las conexiones de un proceso con ss -tnp (o
        netstat), y el tráfico con tcpdump.
    */

    /*
        =================================================================
        CAPA 2) PUERTOS, SOCKETS Y LA DIRECCIÓN 127.0.0.1
        =================================================================

        Una IP identifica una MÁQUINA; un PUERTO (0 a 65535) identifica un
        PROGRAMA dentro de ella. Una conexión TCP queda definida por
        CUATRO datos: IP y puerto del cliente, IP y puerto del servidor.

        - El SERVIDOR "escucha" en un puerto fijo y conocido (80, 443,
          8080). En Linux, los puertos menores a 1024 requieren permisos de
          administrador; por eso las apps de desarrollo usan 8080.
        - El CLIENTE no elige su puerto: el kernel le asigna uno libre y
          temporal, un PUERTO EFÍMERO (en Linux, normalmente entre 32768 y
          60999). La CAPA 7 lo usa para ver si una conexión se reusó.

        127.0.0.1 (localhost) es la dirección de LOOPBACK: "esta misma
        máquina". Los datos nunca salen a la red: el kernel los pasa
        directo de un proceso a otro. Por eso el servidor de prueba de
        esta guía es rápido y no necesita internet.

        Pedir el puerto 0 al abrir un servidor significa "dame cualquier
        puerto libre": así dos ejecuciones de la guía nunca chocan.

        El servidor de prueba usa com.sun.net.httpserver, un servidor HTTP
        sencillo que viene en el JDK. Sirve para pruebas y herramientas; en
        producción las aplicaciones usan el servidor que trae Spring, el
        framework más usado de Java (v15_spring/).
    */
    static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicInteger flakyCalls = new AtomicInteger();

        private TestServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();   // un hilo por petición (v13_concurrency/)
            server.setExecutor(executor);
            TestServer test = new TestServer(server, executor);
            test.registerRoutes();
            server.start();
            return test;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void registerRoutes() {
            server.createContext("/products/7", exchange ->
                    reply(exchange, 200, "{\"id\": 7, \"name\": \"teclado\", \"price\": 500}"));
            server.createContext("/missing", exchange ->
                    reply(exchange, 404, "{\"error\": \"no existe\"}"));
            server.createContext("/echo", exchange -> reply(exchange, 200,
                    exchange.getRequestMethod()
                            + "|" + exchange.getRequestURI().getRawQuery()
                            + "|" + exchange.getRequestHeaders().getFirst("Content-Type")
                            + "|" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            server.createContext("/client-port", exchange ->
                    reply(exchange, 200, String.valueOf(exchange.getRemoteAddress().getPort())));
            server.createContext("/slow", exchange -> {
                sleep(300);                                         // un servicio lento
                reply(exchange, 200, "lento");
            });
            server.createContext("/old-address", exchange -> {
                exchange.getResponseHeaders().add("Location", "/products/7");
                reply(exchange, 301, "");
            });
            server.createContext("/flaky", exchange -> {
                int call = flakyCalls.incrementAndGet();
                reply(exchange, call < 3 ? 503 : 200, call < 3 ? "ocupado" : "listo");   // falla 2 veces
            });
            server.createContext("/report", exchange ->
                    reply(exchange, 200, Stream.iterate(1, n -> n + 1).limit(1_000)
                            .map(n -> "fila " + n).collect(Collectors.joining("\n"))));
            server.createContext("/upgrade-header", exchange ->
                    reply(exchange, 200, String.valueOf(exchange.getRequestHeaders().getFirst("Upgrade"))));
        }

        private static void reply(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);   // -1 = sin cuerpo
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            server.stop(0);                                         // deja de escuchar: libera el puerto
            executor.close();
        }
    }

    /*
        =================================================================
        CAPA 3) LAS TRES PIEZAS: HTTPCLIENT, HTTPREQUEST, BODYHANDLER
        =================================================================

        java.net.http (Java 11) reemplazó a la vieja HttpURLConnection.
        Tiene tres piezas:

        1) HttpClient: el que SE CONECTA. Guarda las conexiones abiertas
           para reusarlas (CAPA 7) y la configuración (timeouts, versión,
           redirecciones). Es inmutable y seguro con hilos: crea UNO y
           compártelo en toda la aplicación. Desde Java 21 es AutoCloseable:
           close() cierra sus conexiones al apagar la aplicación.

        2) HttpRequest: QUÉ pides. Inmutable, se arma con un builder:
               HttpRequest.newBuilder(URI.create(url))
                       .header("Accept", "application/json")
                       .GET()                       // el método por defecto
                       .build()

        3) BodyHandler: CÓMO leer el cuerpo de la respuesta:
               BodyHandlers.ofString()      como texto (UTF-8 si no se indica otro)
               BodyHandlers.ofLines()       Stream<String> de líneas (CAPA 13)
               BodyHandlers.ofFile(path)    directo a un archivo
               BodyHandlers.ofInputStream() como InputStream (FilesAndIO, CAPA 9)
               BodyHandlers.discarding()    no me interesa el cuerpo

        client.send(request, handler) BLOQUEA el hilo hasta tener la
        respuesta (CAPA 12) y devuelve un HttpResponse<T> con:
            statusCode(), headers(), body(), version(), uri()

        send lanza IOException (falló la red) e InterruptedException (el
        hilo fue interrumpido mientras esperaba; v3_exceptions/
        part2_in_practice/ErrorHandlingBestPractices).
    */
    static boolean firstRequestExample(HttpClient client, String base) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/products/7"))
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return response.statusCode() == 200
                && response.body().contains("\"name\": \"teclado\"")
                && response.headers().firstValue("Content-Length").orElse("").equals(
                        String.valueOf(response.body().getBytes(StandardCharsets.UTF_8).length));
    }

    /*
        =================================================================
        CAPA 4) UN 404 NO ES UNA EXCEPCIÓN
        =================================================================

        Para HttpClient, una respuesta 404 o 500 es una respuesta NORMAL:
        el servidor contestó. send() solo lanza excepción si la RED falló
        (CAPA 9). Si no revisas statusCode(), procesas una página de error
        como si fueran tus datos.

        Los códigos se agrupan por centena:
            2xx  éxito             200 OK, 201 Created, 204 No Content
            3xx  redirección       301, 302, 304 Not Modified (CAPA 10)
            4xx  error del CLIENTE 400 Bad Request, 401 sin autenticar,
                                   403 prohibido, 404 no existe,
                                   409 conflicto, 429 demasiadas peticiones
            5xx  error del SERVIDOR 500 error interno, 502/503/504 el
                                   servidor o algo detrás no responde

        La diferencia entre 4xx y 5xx decide qué hacer: con un 4xx,
        repetir la misma petición dará el mismo error (corrige la
        petición); con un 503, reintentar más tarde puede funcionar
        (CAPA 11).

        Patrón profesional: un método que revisa el código y convierte los
        errores en una excepción propia con la información útil
        (v3_exceptions/part2_in_practice/CustomExceptions).
    */
    static final class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;

        ApiException(int status, String body) {
            super("La API respondió " + status + ": " + body);
            this.status = status;
        }

        int status() {
            return status;
        }
    }

    static String getOrThrow(HttpClient client, URI uri) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {     // cualquier cosa que no sea 2xx
            throw new ApiException(response.statusCode(), response.body());
        }
        return response.body();
    }

    static boolean statusCodeExample(HttpClient client, String base) throws IOException, InterruptedException {
        HttpResponse<String> notFound = client.send(
                HttpRequest.newBuilder(URI.create(base + "/missing")).build(), HttpResponse.BodyHandlers.ofString());
        boolean noException = notFound.statusCode() == 404 && notFound.body().contains("no existe");

        boolean converted;
        try {
            getOrThrow(client, URI.create(base + "/missing"));
            converted = false;
        } catch (ApiException e) {
            converted = e.status() == 404;
        }
        return noException && converted;
    }

    /*
        =================================================================
        CAPA 5) ENVIAR DATOS: MÉTODOS, ENCABEZADOS Y CUERPO
        =================================================================

        El MÉTODO dice la intención:
            GET      leer                      (sin cuerpo)
            POST     crear / ejecutar algo     (con cuerpo)
            PUT      reemplazar completo       (con cuerpo)
            PATCH    modificar una parte       (con cuerpo)
            DELETE   borrar

        El CUERPO se manda con un BodyPublisher:
            BodyPublishers.ofString(texto)       texto (UTF-8)
            BodyPublishers.ofByteArray(bytes)
            BodyPublishers.ofFile(path)          subir un archivo sin cargarlo en memoria
            BodyPublishers.noBody()

        Y los ENCABEZADOS describen la petición:
            Content-Type: application/json      QUÉ formato lleva el cuerpo
            Accept: application/json            qué formato quieres de respuesta
            Authorization: Bearer <token>       quién eres (CAPA 15)

        Sin Content-Type, muchos servidores rechazan el cuerpo o lo
        interpretan mal: es como mandar un archivo sin extensión.

        JSON: el JDK no trae una librería para JSON. Aquí se escribe a mano
        con un text block para mostrar la forma; en un proyecto real se
        convierte desde un objeto con Jackson (part2_io_and_network/
        Serialization), nunca concatenando texto (un nombre con comillas
        rompería el JSON).
    */
    static boolean postExample(HttpClient client, String base) throws IOException, InterruptedException {
        String json = """
                {"product": 7, "quantity": 2}""";
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/echo"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        String echoed = client.send(request, HttpResponse.BodyHandlers.ofString()).body();

        return echoed.equals("POST|null|application/json|{\"product\": 7, \"quantity\": 2}");
    }

    /*
        =================================================================
        CAPA 6) URLS Y PARÁMETROS: CODIFICAR
        =================================================================

        Una URL solo puede llevar ciertos caracteres. Espacios, acentos, &,
        = o ? dentro de un VALOR deben CODIFICARSE como %XX (los bytes en
        UTF-8, en hexadecimal). URI.create no lo hace por ti; si le pasas
        un espacio, falla:
            IllegalArgumentException: Illegal character in query at index ...

        Para valores de parámetros (lo que va después de ?):
            URLEncoder.encode("café y pan", StandardCharsets.UTF_8)
            ->  caf%C3%A9+y+pan        (é = bytes C3 A9; espacio = +)

        Y es también una cuestión de SEGURIDAD: si metes un texto del
        usuario sin codificar, un valor como "x&admin=true" agrega un
        parámetro que tú no pusiste.

        Codifica CADA valor por separado y luego une con & y =. (Ojo:
        URLEncoder es para la parte de parámetros; para segmentos de la
        ruta, /productos/{nombre}, el espacio debe ser %20, no +.)
    */
    static URI withQuery(String baseUrl, Map<String, String> parameters) {
        String query = parameters.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return URI.create(baseUrl + "?" + query);
    }

    static boolean encodingExample(HttpClient client, String base) throws IOException, InterruptedException {
        boolean rawFails;
        try {
            URI.create(base + "/echo?q=café y pan");
            rawFails = false;
        } catch (IllegalArgumentException e) {
            rawFails = e.getMessage().startsWith("Illegal character in query");
        }

        URI uri = withQuery(base + "/echo", Map.of("q", "x&admin=true"));    // un "valor" malicioso
        String echoed = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body();

        return rawFails
                && URLEncoder.encode("café y pan", StandardCharsets.UTF_8).equals("caf%C3%A9+y+pan")
                && echoed.startsWith("GET|q=x%26admin%3Dtrue|");      // llegó como UN valor, no como dos parámetros
    }

    /*
        =================================================================
        CAPA 7) REUSAR EL CLIENTE ES REUSAR LA CONEXIÓN TCP
        =================================================================

        HttpClient guarda las conexiones abiertas en un "pool". La segunda
        petición al mismo servidor usa la MISMA conexión TCP: se salta DNS,
        el saludo TCP y el de TLS (CAPA 1).

        El ejemplo lo demuestra con algo que el sistema operativo revela:
        el servidor de prueba responde con el PUERTO EFÍMERO del cliente
        (CAPA 2). Dos peticiones con el mismo HttpClient llegan desde el
        MISMO puerto: es la misma conexión. Otro HttpClient abre una
        conexión nueva, con otro puerto. (Medido en la máquina de prueba:
        61773, 61773 y 61774.)

        El error clásico: crear un HttpClient NUEVO en cada petición
        (dentro del método). Además de pagar la conexión completa cada
        vez, cada conexión cerrada deja su par de puertos un tiempo en el
        estado TIME_WAIT del kernel (TCP lo exige para no confundir
        paquetes viejos con una conexión nueva). Con miles de peticiones
        por segundo se pueden AGOTAR los puertos efímeros y ya no se puede
        abrir ninguna conexión.

        Regla: un HttpClient por aplicación (o por servicio externo con
        configuración distinta), creado al arrancar, compartido entre
        hilos, y cerrado al apagar.
    */
    static String clientPort(HttpClient client, String base) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(base + "/client-port")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    static boolean connectionReuseExample(HttpClient client, String base) throws IOException, InterruptedException {
        String first = clientPort(client, base);
        String second = clientPort(client, base);           // mismo cliente: misma conexión
        String fromOtherClient;
        try (HttpClient other = HttpClient.newHttpClient()) {
            fromOtherClient = clientPort(other, base);      // otro cliente: conexión nueva
        }
        return first.equals(second) && !first.equals(fromOtherClient);
    }

    /*
        =================================================================
        CAPA 8) TIMEOUTS: POR DEFECTO, JAVA ESPERA PARA SIEMPRE
        =================================================================

        Un HttpClient creado con newHttpClient() NO tiene timeouts: si el
        otro servidor acepta la conexión pero nunca responde (se colgó, una
        consulta eterna en su base de datos), tu hilo se queda esperando
        PARA SIEMPRE. En un servidor, cada petición colgada es un hilo
        perdido; cuando se acaban los hilos, TU servicio también deja de
        responder, aunque el problema era del otro. Así se propagan las
        caídas entre sistemas ("fallas en cascada").

        Dos timeouts distintos:
        - connectTimeout (en el CLIENTE): cuánto esperar a que se
          establezca la conexión TCP (CAPA 1, paso 2). Suele ser corto
          (1-5 s). Al vencer: HttpConnectTimeoutException.
        - timeout (en cada REQUEST): cuánto esperar la respuesta completa.
          Depende de la operación. Al vencer: HttpTimeoutException
          ("request timed out").

        Regla: SIEMPRE ambos, en todo cliente de producción. El cliente de
        esta guía tiene connectTimeout de 2 s (ver main).
    */
    static boolean timeoutExample(HttpClient client, String base) throws IOException, InterruptedException {
        boolean defaultHasNone;
        try (HttpClient bare = HttpClient.newHttpClient()) {
            defaultHasNone = bare.connectTimeout().isEmpty();       // sin límite
        }

        HttpRequest impatient = HttpRequest.newBuilder(URI.create(base + "/slow"))
                .timeout(Duration.ofMillis(100))                    // el servidor tarda 300 ms
                .build();
        boolean timedOut;
        try {
            client.send(impatient, HttpResponse.BodyHandlers.ofString());
            timedOut = false;
        } catch (HttpTimeoutException e) {
            timedOut = e.getMessage().equals("request timed out");
        }

        return defaultHasNone && timedOut && client.connectTimeout().equals(Optional.of(Duration.ofSeconds(2)));
    }

    /*
        =================================================================
        CAPA 9) ERRORES DE RED: QUÉ FALLÓ Y DÓNDE
        =================================================================

        Cuando send() lanza una excepción, el TIPO dice en qué paso de la
        CAPA 1 falló:

        | Excepción                  | Paso que falló                | Qué pasó en el sistema operativo |
        |----------------------------|-------------------------------|----------------------------------|
        | UnknownHostException       | 1) DNS                        | nadie conoce ese nombre          |
        | HttpConnectTimeoutException| 2) conexión TCP               | el SYN nunca obtuvo respuesta    |
        |                            |                               | (máquina apagada, firewall)      |
        | ConnectException           | 2) conexión TCP               | la máquina respondió "aquí no    |
        |  "Connection refused"      |                               | escucha nadie" (un paquete RST)  |
        | SSLHandshakeException      | 3) TLS                        | certificado inválido o vencido   |
        | HttpTimeoutException       | 5) esperando respuesta        | conectó, pero no respondió a     |
        |                            |                               | tiempo (CAPA 8)                  |
        | IOException (otras)        | 4-5) a la mitad               | la conexión se cortó             |

        "Connection refused" llega rápido porque el kernel de la otra
        máquina contesta de inmediato que ese puerto está cerrado; un
        firewall que DESCARTA los paquetes, en cambio, no contesta nada y
        terminas en el connectTimeout. Saber cuál de las dos ves te dice
        dónde buscar el problema.

        El ejemplo pide un puerto que acaba de quedar libre: no hay nadie
        escuchando, así que el kernel (aquí mismo, en loopback) rechaza la
        conexión.
    */
    static boolean networkErrorExample(HttpClient client) throws IOException, InterruptedException {
        int closedPort;
        try (ServerSocket temporary = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = temporary.getLocalPort();          // un puerto libre...
        }                                                   // ...que se cierra aquí: ya nadie escucha

        try {
            client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + closedPort + "/")).build(),
                    HttpResponse.BodyHandlers.ofString());
            return false;
        } catch (ConnectException e) {
            return e.getMessage().startsWith("Connection refused");
        }
    }

    /*
        =================================================================
        CAPA 10) REDIRECCIONES
        =================================================================

        Un 301 o 302 dice "lo que buscas está en otra dirección" (en el
        encabezado Location). Por defecto HttpClient NO las sigue
        (Redirect.NEVER): te devuelve el 3xx y decides tú.

            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)

        NORMAL las sigue, salvo de HTTPS a HTTP (bajar a una conexión sin
        cifrar sería inseguro). ALWAYS sigue todas. response.uri() dice a
        dónde llegó al final.
    */
    static boolean redirectExample(HttpClient client, String base) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/old-address")).build();
        int withoutFollowing = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();

        HttpResponse<String> followed;
        try (HttpClient following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            followed = following.send(request, HttpResponse.BodyHandlers.ofString());
        }

        return withoutFollowing == 301
                && followed.statusCode() == 200
                && followed.uri().getPath().equals("/products/7");
    }

    /*
        =================================================================
        CAPA 11) REINTENTOS: CUÁNDO SÍ Y CUÁNDO NO
        =================================================================

        La red falla por momentos: un servidor reiniciándose (503), una
        conexión cortada. Reintentar arregla muchos de esos casos... y
        causa desastres en otros.

        La pregunta clave: ¿la operación es IDEMPOTENTE? Es decir, ¿hacerla
        dos veces deja todo igual que hacerla una?
        - GET, PUT, DELETE: sí, por diseño. Se pueden reintentar.
        - POST: NO. Si el POST "cobrar 500 pesos" llegó al servidor, este
          cobró, y la RESPUESTA se perdió en el camino, reintentar cobra
          dos veces. Para reintentar un POST con seguridad, el servidor
          debe aceptar una "llave de idempotencia" (un id único por
          operación en un encabezado) y ignorar repetidos.

        Qué reintentar: errores de red, timeouts, 502/503/504 y 429.
        Qué NO: 4xx (repetir da lo mismo), y nunca sin límite.

        Cómo: pocas veces (3), esperando cada vez MÁS entre intentos
        (backoff exponencial: 100 ms, 200 ms, 400 ms...) para no hundir más
        a un servidor que ya está saturado, con algo de azar (jitter) para
        que miles de clientes no reintenten en el mismo milisegundo. Si el
        servidor manda el encabezado Retry-After, respétalo.

        (En proyectos grandes esto lo resuelve una librería, junto con el
        "circuit breaker": dejar de llamar un rato a un servicio que está
        caído; v14_architecture/.)
    */
    static HttpResponse<String> getWithRetries(HttpClient client, URI uri, int maxAttempts, AtomicInteger attempts)
            throws IOException, InterruptedException {
        long backoffMillis = 10;
        while (true) {
            attempts.incrementAndGet();
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).build(),
                    HttpResponse.BodyHandlers.ofString());
            boolean retryable = response.statusCode() == 503 || response.statusCode() == 429;
            if (!retryable || attempts.get() >= maxAttempts) {
                return response;                    // éxito, error que no se arregla reintentando, o se acabaron los intentos
            }
            Thread.sleep(backoffMillis + (long) (Math.random() * backoffMillis));   // espera con jitter
            backoffMillis *= 2;                     // backoff exponencial
        }
    }

    static boolean retryExample(HttpClient client, String base) throws IOException, InterruptedException {
        AtomicInteger attempts = new AtomicInteger();
        HttpResponse<String> response = getWithRetries(client, URI.create(base + "/flaky"), 3, attempts);
        return response.statusCode() == 200 && response.body().equals("listo") && attempts.get() == 3;
    }

    /*
        =================================================================
        CAPA 12) ESPERAR LA RED BLOQUEA UN HILO: SENDASYNC
        =================================================================

        Mientras send() espera la respuesta, el HILO que lo llamó no hace
        nada: está bloqueado en el kernel, esperando que lleguen bytes al
        socket. Un hilo normal de Java es un hilo del SISTEMA OPERATIVO,
        con su propia pila de memoria (del orden de 1 MB reservado por
        defecto en Linux de 64 bits) y un costo para el planificador del
        kernel. Tener miles bloqueados esperando la red desperdicia memoria
        y tiene un límite.

        Si necesitas VARIAS respuestas, hacerlas una tras otra suma los
        tiempos de espera. sendAsync manda la petición y devuelve DE
        INMEDIATO un CompletableFuture: una promesa de respuesta. Puedes
        lanzar varias y esperar todas juntas. Medido con 3 peticiones a un
        servicio que tarda 300 ms:
            send, una tras otra     925 ms
            sendAsync, en paralelo  313 ms

        Por dentro, HttpClient usa pocos hilos y la notificación del
        sistema operativo de "llegaron datos a este socket" (epoll en
        Linux), en lugar de un hilo bloqueado por conexión.

        CompletableFuture a fondo (encadenar, combinar, errores) está en
        v13_concurrency/. Y desde Java 21 existe otra salida: los HILOS
        VIRTUALES, que permiten escribir send() normal y bloqueante pero
        con millones de hilos baratos (también en v13_concurrency/; el
        servidor de prueba de esta guía ya los usa).
    */
    static boolean asyncExample(HttpClient client, String base) throws IOException, InterruptedException {
        URI slow = URI.create(base + "/slow");

        long start = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            client.send(HttpRequest.newBuilder(slow).build(), HttpResponse.BodyHandlers.ofString());
        }
        long sequentialMillis = (System.nanoTime() - start) / 1_000_000;

        start = System.nanoTime();
        List<CompletableFuture<HttpResponse<String>>> pending = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            pending.add(client.sendAsync(HttpRequest.newBuilder(slow).build(), HttpResponse.BodyHandlers.ofString()));
        }
        List<String> bodies = pending.stream().map(CompletableFuture::join).map(HttpResponse::body).toList();
        long parallelMillis = (System.nanoTime() - start) / 1_000_000;

        return sequentialMillis >= 900 && parallelMillis < sequentialMillis
                && bodies.equals(List.of("lento", "lento", "lento"));
    }

    /*
        =================================================================
        CAPA 13) RESPUESTAS GRANDES
        =================================================================

        BodyHandlers.ofString() junta TODA la respuesta en memoria antes
        de devolverla. Para una descarga de cientos de MB o un reporte de
        millones de filas, eso es un OutOfMemoryError (igual que
        readAllLines en FilesAndIO, CAPA 11). Alternativas:

            BodyHandlers.ofLines()          Stream<String>: procesar línea por línea
                                            mientras llegan (hay que cerrarlo)
            BodyHandlers.ofFile(path)       guardar directo en disco, sin pasar
                                            el contenido por el heap
            BodyHandlers.ofInputStream()    leer a tu ritmo (y cerrarlo)

        Cerrar el stream libera la conexión para el pool (CAPA 7); si no lo
        cierras, la conexión queda ocupada (y su socket abierto) hasta que
        el servidor la corte.
    */
    static boolean largeResponseExample(HttpClient client, String base) throws IOException, InterruptedException {
        HttpResponse<Stream<String>> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/report")).build(), HttpResponse.BodyHandlers.ofLines());

        long rowsEndingInZero;
        try (Stream<String> lines = response.body()) {      // cerrar: devuelve la conexión al pool
            rowsEndingInZero = lines.filter(line -> line.endsWith("0")).count();
        }
        return response.statusCode() == 200 && rowsEndingInZero == 100;
    }

    /*
        =================================================================
        CAPA 14) HTTP/2
        =================================================================

        HTTP/1.1 manda UNA petición a la vez por conexión: para pedir 10
        cosas en paralelo hacen falta varias conexiones. HTTP/2 (y HTTP/3)
        MULTIPLEXA: muchas peticiones y respuestas intercaladas por UNA sola
        conexión, con encabezados comprimidos.

        HttpClient usa HTTP/2 POR DEFECTO (client.version() es HTTP_2) y
        se adapta a lo que hable el servidor:
        - Con HTTPS, lo negocia durante el saludo TLS (ALPN).
        - Con HTTP sin cifrar, manda la petición en HTTP/1.1 con el
          encabezado "Upgrade: h2c" proponiendo cambiar. El servidor de
          prueba solo habla HTTP/1.1, lo ignora, y todo sigue en 1.1:
          response.version() lo dice.

        No tienes que hacer nada: es transparente. Pero explica por qué
        en HTTP/2 importa todavía más compartir el cliente (CAPA 7).
    */
    static boolean http2Example(HttpClient client, String base) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base + "/upgrade-header")).build(), HttpResponse.BodyHandlers.ofString());
        return client.version() == HttpClient.Version.HTTP_2           // lo que el cliente prefiere
                && response.body().equals("h2c")                       // lo propuso al servidor
                && response.version() == HttpClient.Version.HTTP_1_1;  // el servidor siguió en 1.1
    }

    /*
        =================================================================
        CAPA 15) SEGURIDAD
        =================================================================

        1) HTTPS SIEMPRE fuera de tu máquina. Con HTTP, cualquiera en la
           red (el wifi del café, un proxy) lee y modifica el tráfico.

        2) NUNCA desactives la verificación de certificados. En internet
           abundan "soluciones" con un TrustManager que acepta todo para
           quitar un SSLHandshakeException. Eso convierte HTTPS en HTTP:
           cualquiera puede hacerse pasar por el servidor. Si un
           certificado no se valida, arregla el certificado o agrega la CA
           correcta al almacén de confianza (truststore) de la JVM.

        3) Los SECRETOS (tokens, llaves de API) van en ENCABEZADOS
           (Authorization: Bearer ...), nunca en la URL: las URLs quedan
           escritas en logs de servidores, proxies e historiales. Y no
           registres (log) los encabezados de autenticación.

        4) SSRF (Server-Side Request Forgery): si tu servidor hace
           peticiones a una URL que manda un usuario ("importar imagen
           desde URL"), un atacante puede mandar una dirección INTERNA
           (127.0.0.1, la red privada de la empresa, o la dirección de
           metadatos de la nube, 169.254.169.254) y usar tu servidor para
           leer lo que él no puede. Valida el destino con una lista de
           dominios permitidos.

        5) Timeouts y límites de tamaño en todo lo que venga de afuera
           (CAPAS 8 y 13).
    */

    /*
        =================================================================
        CAPA 16) ERRORES COMUNES
        =================================================================

        1) Crear un HttpClient por petición: sin reuso de conexiones, y
           con riesgo de agotar puertos efímeros (CAPA 7).

        2) No poner timeouts: un servidor colgado cuelga el tuyo (CAPA 8).

        3) No revisar statusCode(): procesar un 404 o 500 como datos
           (CAPA 4).

        4) Construir URLs concatenando valores sin URLEncoder (CAPA 6).

        5) Olvidar Content-Type al mandar un cuerpo (CAPA 5).

        6) Reintentar un POST sin llave de idempotencia, reintentar 4xx, o
           reintentar sin límite y sin backoff (CAPA 11).

        7) Hacer muchas peticiones una tras otra cuando son independientes
           (CAPA 12).

        8) ofString con respuestas enormes; no cerrar ofLines/ofInputStream
           (CAPA 13).

        9) Desactivar la verificación de certificados; secretos en la URL;
           pedir URLs que manda el usuario sin validarlas (CAPA 15).

        10) Esperar que Java siga redirecciones por defecto (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Una petición: DNS -> conexión TCP (socket = descriptor) -> TLS ->
          petición -> respuesta -> conexión abierta para reusar.
        - Cliente: puerto efímero asignado por el kernel; servidor: puerto
          fijo; 127.0.0.1 no sale de la máquina.
        - HttpClient (uno, compartido, con pool de conexiones), HttpRequest
          (inmutable), BodyHandler (cómo leer).
        - 4xx/5xx no lanzan excepción: revisa statusCode.
        - Cuerpo con BodyPublishers + Content-Type; parámetros con
          URLEncoder.
        - SIEMPRE connectTimeout y timeout por petición.
        - El tipo de excepción dice qué paso de la red falló.
        - Redirecciones apagadas por defecto: Redirect.NORMAL.
        - Reintenta solo lo idempotente, pocas veces, con backoff.
        - send bloquea un hilo del sistema; sendAsync para paralelo (o
          hilos virtuales).
        - Respuestas grandes: ofLines/ofFile/ofInputStream, y cerrarlos.
        - HTTP/2 por defecto, transparente.
        - HTTPS, nunca desactivar certificados, secretos en encabezados,
          cuidado con SSRF.
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
