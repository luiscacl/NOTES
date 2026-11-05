package com.practice.spring.testing;

/*
    =====================================================================
    PRUEBAS DE INTEGRACIÓN: LA APLICACIÓN COMPLETA, DE VERDAD
    =====================================================================

    Ruta de spring/: guía 14  (part4_testing)
    Anterior:  part4_testing/UnitAndSliceTests
    Siguiente: part5_security/...  (la primera de seguridad)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Como en UnitAndSliceTests, el código son las pruebas, en
      src/test/java/com/practice/spring/testing/shop/:
          IntegrationTestBase         la configuración común (CAPAS 2 y 5)
          CheckoutIntegrationTest     el flujo de compra (CAPAS 3 y 4)
          HistoryIntegrationTest      otra clase que REUSA el contexto
      Desde v15_spring/:
          ./mvnw -pl part4_testing test -Dtest='*IntegrationTest'
    - Tiempos y resultados citados: corridas reales, con Docker.

    Importancia: ALTA. Son las que encuentran los errores que las demás no
    pueden ver. Esta guía tiene uno de verdad (CAPA 4).

    Índice:
        CAPA 1)  Qué prueban que las otras no
        CAPA 2)  @SpringBootTest con servidor real y PostgreSQL compartido
        CAPA 3)  Un flujo de punta a punta
        CAPA 4)  El error que solo esta prueba encontró
        CAPA 5)  Los sistemas externos: falsos que se portan como reales
        CAPA 6)  Datos limpios entre pruebas
        CAPA 7)  Que sean pocas, y rápidas
        CAPA 8)  Errores comunes
        RESUMEN FINAL
*/
public final class IntegrationTests {

    private IntegrationTests() {
    }

    /*
        =================================================================
        CAPA 1) QUÉ PRUEBAN QUE LAS OTRAS NO
        =================================================================

        Las unitarias prueban cada pieza con sus vecinos reemplazados; las
        de una capa, un pedazo de Spring. Nadie ha comprobado todavía que
        las piezas, JUNTAS, funcionan:
        - que los beans se conectan (el contexto completo arranca);
        - que la configuración real (propiedades, perfiles) es correcta;
        - que las TRANSACCIONES hacen lo que se espera (CAPA 4);
        - que el JSON que sale del controller lo entiende quien lo lee;
        - que las consultas funcionan contra el esquema real.

        Una prueba de integración levanta la aplicación COMPLETA, con su
        servidor HTTP y una base de datos real, y la usa como lo haría un
        cliente. Solo se reemplaza lo que no podemos controlar: los sistemas
        de OTROS (el banco, el correo) (CAPA 5).
    */

    /*
        =================================================================
        CAPA 2) @SPRINGBOOTTEST CON SERVIDOR REAL Y POSTGRESQL COMPARTIDO
        =================================================================

        IntegrationTestBase:

            @SpringBootTest(classes = ShopApplication.class,
                            webEnvironment = RANDOM_PORT)    Tomcat real, puerto libre
            @Import({Containers.class, FakeExternalSystems.class})

        - RANDOM_PORT: un servidor de verdad en un puerto libre (nunca un
          puerto fijo: dos compilaciones a la vez chocarían). RestTestClient
          le habla por HTTP (part2_web/.../rest/RestControllers, CAPA 8).
        - El PostgreSQL es un BEAN de una @TestConfiguration, con
          @ServiceConnection: Spring lo arranca con el contexto, le pone la
          URL al DataSource, y lo detiene con el contexto.

        Y por qué una CLASE BASE: Spring guarda en caché cada contexto de
        prueba y lo REUSA en otra clase que pida EXACTAMENTE la misma
        configuración (UnitAndSliceTests, CAPA 8). Como el contenedor es un
        bean de ese contexto, también se reusa. Medido:

            CheckoutIntegrationTest   22.7 s   (arranca Spring y PostgreSQL)
            HistoryIntegrationTest     0.18 s  (mismo contexto, mismo contenedor)

        Y medido lo contrario: una tercera clase con UNA propiedad distinta
        (@TestPropertySource) creó OTRO contexto y OTRO contenedor (4.5 s
        más, con dos PostgreSQL vivos a la vez). Por eso todas las pruebas de
        integración heredan de la misma base, y nadie le agrega cosas "solo
        a su clase".
    */

    /*
        =================================================================
        CAPA 3) UN FLUJO DE PUNTA A PUNTA
        =================================================================

        CheckoutIntegrationTest.unaCompraPagadaQuedaGuardadaYApareceEnElHistorial
        hace lo que haría un cliente, y revisa TODOS los efectos:

            POST /api/orders (JSON real)         -> 201, total y paymentId
            el "banco" falso                     -> cobró 42,300 (36,000 -
                                                    10% GOLD + envío)
            la tabla shop_orders (con JdbcClient) -> una fila PAID
            GET /api/orders?email=...            -> el pedido aparece

        Revisar la base de datos con SQL directo, y no con el mismo
        repositorio de la aplicación, evita que un error en el repositorio
        se "confirme a sí mismo".

        Qué flujos merecen una prueba así: los CAMINOS PRINCIPALES del
        negocio (comprar, pagar, cancelar) y las fallas CRÍTICAS (un pago
        rechazado). No cada combinación: esas van en unitarias.
    */

    /*
        =================================================================
        CAPA 4) EL ERROR QUE SOLO ESTA PRUEBA ENCONTRÓ
        =================================================================

        El servicio, cuando el banco rechaza el pago, guarda el pedido como
        REJECTED (para tener registro del intento) y relanza la excepción
        para que el controller responda 402.

        La prueba UNITARIA (OrderServiceTest) lo "comprobaba": con Mockito,
        verificaba que se llamó a save() con un pedido REJECTED. Y pasaba.

        La prueba de INTEGRACIÓN, la primera vez que corrió, FALLÓ:

            Expecting actual:
              []
            to contain exactly (and in same order):
              ["REJECTED"]

        La tabla estaba VACÍA. El método es @Transactional, y la excepción
        relanzada es no checked: la transacción hace ROLLBACK, y se lleva el
        save del pedido rechazado (part3_data/.../jdbc/JdbcAndTransactions,
        CAPA 5). El mock no tiene transacciones; solo una base de datos real
        lo muestra.

        La corrección, en ShopApp.OrderService:

            @Transactional(noRollbackFor = PaymentDeclinedException.class)

        Lección: los mocks prueban que tu código LLAMA a las cosas; no que
        las cosas, juntas, hacen lo que esperas. Por eso existen las dos.
    */

    /*
        =================================================================
        CAPA 5) LOS SISTEMAS EXTERNOS: FALSOS QUE SE PORTAN COMO REALES
        =================================================================

        El banco real no se llama desde una prueba (cuesta dinero, es lento,
        y si falla, tu prueba falla por algo que no es tuyo). Se reemplaza:

        - un bean FALSO con @Primary en una @TestConfiguration
          (RecordingPayments): gana sobre el de la aplicación, se comporta
          como el real en lo que importa (aprueba hasta un límite, rechaza
          arriba), y ANOTA lo que le pidieron para revisarlo;
        - o, si el cliente es HTTP, un servidor HTTP falso local (part2_web/
          .../clients/HttpClients, CAPA 7): prueba también el cliente HTTP
          real, sus tiempos límite y su manejo de errores.

        ¿Y @MockitoBean? Funciona, pero cada @MockitoBean distinto es un
        contexto distinto (CAPA 2). Un falso compartido en la clase base
        mantiene un solo contexto para todas.
    */

    /*
        =================================================================
        CAPA 6) DATOS LIMPIOS ENTRE PRUEBAS
        =================================================================

        Con @DataJpaTest, cada prueba se deshacía sola (rollback). Aquí NO:
        - el servidor atiende cada petición en SU hilo, con SU transacción,
          y la confirma. Poner @Transactional en la prueba no la deshace (es
          otra transacción, en otro hilo);
        - el contexto, y con él la base de datos, se COMPARTE entre clases
          (CAPA 2).

        Así que cada prueba deja la base de datos como la necesita, ANTES de
        empezar: el @BeforeEach de IntegrationTestBase borra los pedidos y
        limpia el banco falso. Antes y no después: si una prueba falla a la
        mitad, la siguiente igual empieza limpia.

        Con muchas tablas, se borra en orden por las llaves foráneas, o se
        usa truncate ... cascade de PostgreSQL. Y las pruebas no dependen de
        ids fijos (las secuencias no se reinician).
    */

    /*
        =================================================================
        CAPA 7) QUE SEAN POCAS, Y RÁPIDAS
        =================================================================

        - POCAS: los caminos principales y las fallas críticas (CAPA 3). La
          lógica y sus combinaciones van abajo en la pirámide
          (UnitAndSliceTests, CAPA 1).
        - UN CONTEXTO: todas heredan de la misma base (CAPA 2).
        - UN CONTENEDOR: compartido por el contexto. En tu máquina, además,
          Testcontainers puede dejarlo vivo ENTRE corridas
          (testcontainers.reuse.enable=true en ~/.testcontainers.properties
          y .withReuse(true)): arranca en milisegundos la segunda vez. Solo
          para desarrollo local, nunca en el servidor de integración.
        - SEPARARLAS si crecen: Maven corre con Surefire las que terminan en
          Test (las rápidas, en cada cambio) y con el plugin Failsafe las que
          terminan en IT (las de integración, antes de integrar). Así el ciclo
          rápido no espera a Docker.
    */

    /*
        =================================================================
        CAPA 8) ERRORES COMUNES
        =================================================================

        1) Solo unitarias con mocks: los errores de transacciones, mapeos y
           configuración llegan a producción (CAPAS 1 y 4).

        2) Puertos fijos; una base de datos en memoria en vez de la real
           (CAPA 2).

        3) Cada clase con su propia configuración: un contexto y un
           contenedor por clase (CAPA 2).

        4) Revisar el resultado solo con el mismo código que se está
           probando (CAPA 3).

        5) Llamar a sistemas externos reales desde las pruebas (CAPA 5).

        6) Esperar rollback automático con RANDOM_PORT; limpiar después en
           vez de antes; depender de ids fijos (CAPA 6).

        7) Probar cada combinación con pruebas de integración (CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Integración = la aplicación completa, HTTP real, base de datos
          real; solo los sistemas ajenos se reemplazan.
        - @SpringBootTest(RANDOM_PORT) + RestTestClient + PostgreSQL como bean
          con @ServiceConnection.
        - Una clase base: un contexto y un contenedor para todas (22.7 s la
          primera, 0.18 s la segunda, medido).
        - Encuentran lo que los mocks no ven: aquí, un rollback que borraba
          los pedidos rechazados.
        - Falsos con @Primary que se portan como el sistema real y anotan lo
          que reciben.
        - Limpiar los datos ANTES de cada prueba.
        - Pocas: caminos principales y fallas críticas.
    */
}
