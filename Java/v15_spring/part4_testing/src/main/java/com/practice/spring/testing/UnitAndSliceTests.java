package com.practice.spring.testing;

/*
    =====================================================================
    PRUEBAS UNITARIAS Y DE UNA CAPA: RÁPIDAS, CLARAS Y CONFIABLES
    =====================================================================

    Ruta de spring/: guía 13  (part4_testing)
    Anterior:  part3_data/.../migrations/Migrations
    Siguiente: part4_testing/IntegrationTests

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - En esta guía el código SON LAS PRUEBAS. Lo que se prueba es una
      tienda pequeña (shop/ShopApp, shop/ShopApplication,
      shop/ShopOrderRepository), y cada capa apunta a la prueba que la
      demuestra, en src/test/java/com/practice/spring/testing/shop/:
          PricingTest                 unitaria pura (CAPAS 2 y 3)
          OrderServiceTest            unitaria con Mockito (CAPAS 4 y 5)
          OrderControllerTest         @WebMvcTest (CAPA 6)
          ShopOrderRepositoryTest     @DataJpaTest + PostgreSQL (CAPA 7)
      Desde v15_spring/:  ./mvnw -pl part4_testing test
    - Versiones verificadas: JUnit 6.0 (Jupiter), AssertJ 3.27, Mockito
      5.23. Tiempos y mensajes citados: corridas reales.

    Importancia: MUY ALTA. En un equipo profesional, el código sin pruebas
    no se acepta; y las pruebas lentas o inestables se ignoran hasta que
    dejan de servir.

    Índice:
        CAPA 1)  Para qué y de qué tipo: la pirámide, medida
        CAPA 2)  JUnit: la estructura de una prueba
        CAPA 3)  AssertJ: comprobaciones que se leen y que explican
        CAPA 4)  Unitarias sin Spring: new, y un reloj fijo
        CAPA 5)  Mockito: mocks, stubs y verificaciones
        CAPA 6)  @WebMvcTest: solo la capa web
        CAPA 7)  @DataJpaTest: solo la capa de datos
        CAPA 8)  Lo que rompe las pruebas de una capa (medido)
        CAPA 9)  Pruebas inestables: de dónde salen
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public final class UnitAndSliceTests {

    private UnitAndSliceTests() {
    }

    /*
        =================================================================
        CAPA 1) PARA QUÉ Y DE QUÉ TIPO: LA PIRÁMIDE, MEDIDA
        =================================================================

        Una prueba automática es código que ejecuta tu código y revisa el
        resultado. Sirven para tres cosas: saber que funciona HOY, poder
        CAMBIARLO mañana sin miedo (si rompes algo, una prueba falla), y
        DOCUMENTAR qué se supone que hace (una prueba bien nombrada es un
        ejemplo que no se desactualiza).

        Los tipos, de más rápidos y numerosos a más lentos y pocos:

            UNITARIAS       una clase, sin Spring ni base de datos
            DE UNA CAPA     un pedazo de Spring: solo la web, solo los datos
            (slices)
            INTEGRACIÓN     la aplicación completa, con base de datos real
                            (part4_testing/IntegrationTests)

        Medido en este módulo (cada clase de prueba, incluido lo que tarda en
        preparar su entorno):

            PricingTest              unitaria pura           ~0.2 s
            OrderServiceTest         unitaria con Mockito    ~0.7 a 0.8 s
            OrderControllerTest      @WebMvcTest             ~9.6 s
            ShopOrderRepositoryTest  @DataJpaTest + Docker   ~17 a 20 s

        (Lo de las capas incluye arrancar Spring y, en la de datos, un
        PostgreSQL en Docker; una vez arrancado, cada prueba tarda
        milisegundos, y Spring REUSA el contexto entre clases con la misma
        configuración: CAPA 8.)

        Por eso la PIRÁMIDE: muchas unitarias (toda la lógica y los casos de
        borde), menos de una capa (lo que solo se ve con Spring: mapeos,
        validación, consultas), y pocas de integración (que todo junto
        funciona).
    */

    /*
        =================================================================
        CAPA 2) JUNIT: LA ESTRUCTURA DE UNA PRUEBA
        =================================================================

        JUnit es el marco que encuentra y corre las pruebas. Spring Boot 4
        trae JUnit 6 (verificado con dependency:tree); la API de pruebas
        ("Jupiter") es la misma que en JUnit 5, así que todo lo que veas de
        JUnit 5 aplica.

        En PricingTest:
            @Test                       un método = una prueba
            @DisplayName("...")         el nombre que se muestra (en español,
                                        como una frase del negocio)
            @Nested                     agrupar pruebas de un mismo tema
            @ParameterizedTest + @CsvSource   la MISMA prueba con varios
                                        datos (una fila por caso)
            @BeforeEach                 preparar antes de CADA prueba
                                        (OrderServiceTest)

        La forma de cada prueba, ARRANGE - ACT - ASSERT (preparar, ejecutar,
        comprobar), separada a la vista. Y una sola razón para fallar por
        prueba: si falla, el nombre dice qué se rompió.

        Un dato que pasó de verdad: al escribir los casos de @CsvSource,
        calculé mal el de un cliente GOLD que compra 1,100 pesos. Con el 10%
        queda en 990, DEBAJO del envío gratis, y vuelve a pagar envío: 1,089.
        La prueba sacó a la luz una regla dudosa que nadie había pensado.
        Para eso también sirven: encontrar casos de borde y llevarlos al
        negocio.
    */

    /*
        =================================================================
        CAPA 3) ASSERTJ: COMPROBACIONES QUE SE LEEN Y QUE EXPLICAN
        =================================================================

        AssertJ (lo trae Spring Boot) escribe las comprobaciones como frases:

            assertThat(total).isEqualTo(59_900);
            assertThat(orders).extracting(ShopOrder::getTotalCents)
                              .containsExactly(99_000L, 25_000L, 10_000L);
            assertThatThrownBy(() -> Pricing.total(List.of(), REGULAR))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Un pedido necesita al menos una línea");

        La ventaja no es estética: cuando FALLA, el mensaje dice qué esperaba
        y qué llegó, con los elementos de la lista. Lo has visto en estas
        guías: "Expecting actual: [null, "1"] to contain exactly: ["1"] but
        some elements were not expected: [null]" (Migrations, CAPA 5).
        Con un assertTrue(a == b) solo dice "expected true".
    */

    /*
        =================================================================
        CAPA 4) UNITARIAS SIN SPRING: NEW, Y UN RELOJ FIJO
        =================================================================

        Si las clases reciben sus dependencias por el constructor
        (part1_core/.../container/IoCContainer, CAPA 3), probarlas NO
        necesita Spring: se crean con new y se les pasan dependencias de
        prueba. OrderServiceTest:

            service = new OrderService(orders, payments, Clock.fixed(NOW, UTC));

        El reloj fijo es la clave para probar cualquier cosa con fechas: el
        servicio guarda la hora del Clock que recibió, y la prueba sabe EXACTA
        qué hora debe quedar (v10_design/part2_solid/DependencyInversion,
        CAPA 9). Con Instant.now() adentro, la prueba no podría comprobar la
        hora, y las reglas que dependen del día fallarían a medianoche.

        Si una clase es difícil de probar así (necesita 9 dependencias, o
        crea sus colaboradores con new adentro), la prueba está diciendo algo
        del DISEÑO: escúchala.
    */

    /*
        =================================================================
        CAPA 5) MOCKITO: MOCKS, STUBS Y VERIFICACIONES
        =================================================================

        Mockito crea implementaciones FALSAS de interfaces y clases (mocks),
        a las que les dices qué responder y luego les preguntas qué les
        pasó. En OrderServiceTest:

            @ExtendWith(MockitoExtension.class)
            @Mock ShopOrderRepository orders;       un mock, creado solo

            when(payments.charge("ana@correo.com", 59_900)).thenReturn("pay-123");   STUB
            when(payments.charge(eq(...), anyLong())).thenThrow(...);            STUB de error
            when(orders.save(any())).thenAnswer(call -> ...);                    con lógica

            verify(orders).save(captor.capture());   se llamó UNA vez... y con qué
            verify(payments, never()).charge(...);   NO se llamó

        ArgumentCaptor captura el argumento para revisarlo: no basta saber
        que se guardó un pedido, hay que saber que se guardó REJECTED, sin id
        de pago.

        La extensión de Mockito es ESTRICTA: un stub que la prueba prepara y
        nunca se usa hace FALLAR la prueba (UnnecessaryStubbingException). Es
        a propósito: un stub sobrante suele significar que la prueba no
        prueba lo que crees.

        MOCK O FAKE. Un FAKE es una implementación de verdad, simple, hecha
        para pruebas: la lambda que siempre aprueba en
        conUnFalsoEscritoAManoEnVezDeUnMock, o el repositorio en memoria de
        v11_patterns/part4_modern/Repository. Los fakes prueban
        COMPORTAMIENTO; los mocks, además, INTERACCIONES (qué se llamó).
        Reglas que evitan pruebas frágiles:
        - mockea las FRONTERAS (repositorios, clientes HTTP, correo), no las
          clases de tu dominio ni los value objects: usa los reales;
        - no mockees lo que no es tuyo (una librería): envuélvelo en una
          interface tuya (el puerto de v14_architecture/part2_boundaries/
          Hexagonal) y mockea esa;
        - si una prueba verifica cada llamada interna, se rompe con cualquier
          refactor aunque el resultado siga igual: verifica lo IMPORTANTE.

        Y la configuración que hace falta hoy: en JDK modernos, Mockito se
        cargaba como agente "a la fuerza" al ejecutar, y avisaba (medido):

            Mockito is currently self-attaching to enable the
            inline-mock-maker. This will no longer work in future releases
            of the JDK. Please add Mockito as an agent to your build...
            WARNING: A Java agent has been loaded dynamically (...)

        v15_spring/pom.xml lo resuelve como pide la advertencia: el plugin
        de dependencias expone la ruta del jar de Mockito, y Surefire la pasa
        como -javaagent. Detalle real: tu carpeta de usuario tiene un ESPACIO
        ("Luis Casillas") y la JVM no arrancaba hasta poner la ruta entre
        comillas ("Error opening zip file or JAR manifest missing").
    */

    /*
        =================================================================
        CAPA 6) @WEBMVCTEST: SOLO LA CAPA WEB
        =================================================================

        @WebMvcTest(OrderController.class) arranca SOLO lo de Spring MVC
        (conversión de JSON, validación, manejo de errores) y ESE controller.
        No hay servicios, repositorios ni base de datos (la prueba comprueba
        que no existe el bean dataSource).

        El servicio que necesita el controller se reemplaza con
        @MockitoBean: un mock de Mockito registrado DENTRO del contenedor.
        (En Spring Boot 4 ya NO existe @MockBean, que verás en todo el
        código de Boot 2 y 3; se reemplazó por @MockitoBean de Spring
        Framework. Verificado en los jars.)

        Qué se prueba aquí, y solo aquí: que la ruta y el verbo son los
        correctos, que el JSON entra y sale con la forma acordada, que la
        validación rechaza lo inválido SIN llamar al servicio
        (verifyNoInteractions), y que las excepciones se traducen al código
        correcto (402). La lógica del servicio NO: esa ya la probó la
        unitaria.
    */

    /*
        =================================================================
        CAPA 7) @DATAJPATEST: SOLO LA CAPA DE DATOS
        =================================================================

        @DataJpaTest arranca SOLO JPA, Hibernate y los repositorios. Con
        @Testcontainers + @ServiceConnection, contra un PostgreSQL REAL (no
        una base de datos en memoria que se comporta distinto: las
        diferencias de SQL entre bases de datos son reales).

        Lo que hace por ti:
        - cada prueba corre en una TRANSACCIÓN que se deshace al final
          (la prueba lo comprueba con TestTransaction): las pruebas no se
          ensucian entre sí;
        - TestEntityManager para preparar datos sin pasar por el repositorio;
        - muestra el SQL (spring.jpa.show-sql) en la salida.

        Un cuidado: como todo pasa en la misma transacción, los inserts no se
        mandan hasta el flush. entityManager.flush() antes de consultar, para
        que la consulta vaya contra lo que de verdad está en la base de datos
        (y para que una restricción violada falle AQUÍ, no al final).

        Qué se prueba aquí: las consultas propias (@Query, nombres derivados
        complicados), los mapeos raros, las restricciones. Un findById de
        Spring Data no necesita prueba: ya la tiene Spring.
    */

    /*
        =================================================================
        CAPA 8) LO QUE ROMPE LAS PRUEBAS DE UNA CAPA (MEDIDO)
        =================================================================

        Las pruebas de una capa usan la clase @SpringBootApplication como
        punto de partida de su configuración. Al escribir esta guía, eso
        rompió dos cosas:

        1) CONFIGURACIÓN DE UN ÁREA EN LA CLASE PRINCIPAL. La primera versión
           tenía @EnableJpaRepositories en ShopApplication. Resultado: el
           @WebMvcTest, que no tiene JPA, intentó crear los repositorios y
           falló:

               No bean named 'entityManagerFactory' available

           La documentación de Spring Boot lo advierte: la clase principal se
           deja limpia, y la configuración de cada área (JPA, seguridad,
           programación de tareas) va en su propia clase @Configuration.

        2) COMPONENTES DENTRO DE LA CLASE PRINCIPAL. Con el controller como
           clase anidada DENTRO de la @SpringBootApplication, el @DataJpaTest
           lo cargó (Spring registra las clases anidadas de una clase de
           configuración junto con ella, sin pasar por el filtro de la
           prueba). Por eso aquí la clase principal está sola en su archivo.
           En un proyecto normal, con cada clase en su archivo, no pasa.

        Y el COSTO de los contextos: Spring guarda en caché el contexto de
        cada configuración de prueba y lo REUSA entre clases que piden lo
        mismo. Cada @MockitoBean distinto, cada propiedad distinta y cada
        @DirtiesContext crean un contexto nuevo (otros segundos). En un
        proyecto con cientos de pruebas, la diferencia es entre 3 y 30
        minutos: hay que reusar configuraciones (una clase base común, los
        mismos mocks).
    */

    /*
        =================================================================
        CAPA 9) PRUEBAS INESTABLES: DE DÓNDE SALEN
        =================================================================

        Una prueba INESTABLE (flaky) a veces pasa y a veces falla sin que el
        código cambie. Es peor que no tenerla: el equipo aprende a ignorar
        los fallos, y un día ignora uno real. Las causas, y lo que se vio en
        estas guías:

        - el TIEMPO: Instant.now(), la fecha de hoy, la medianoche -> un Clock
          fijo (CAPA 4);
        - TIEMPOS LÍMITE JUSTOS: la prueba de clientes HTTP con 300 ms falló
          2 veces al correr todo el proyecto con la máquina cargada; con
          margen (1 s contra 3 s) no (part2_web/.../clients/HttpClients,
          CAPA 4);
        - sleep para "esperar a que termine algo": esperar una señal (latch,
          future) o una condición, nunca un tiempo (v13_concurrency/
          part1_threads/ThreadBasics, CAPA 9);
        - ESTADO COMPARTIDO entre pruebas (datos que otra prueba dejó; el
          orden en que corren no está garantizado): cada prueba prepara lo
          suyo (los @BeforeEach con delete de part3_data, el rollback de
          @DataJpaTest);
        - lo ALEATORIO sin semilla fija;
        - SERVICIOS EXTERNOS reales: un falso local (part2_web/.../clients/
          HttpClients, CAPA 7) o un contenedor.

        Cuando una prueba es inestable, se ARREGLA o se borra; no se
        reintenta "hasta que pase".
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Todo con @SpringBootTest: suites de 30 minutos (CAPA 1).

        2) Pruebas sin nombre claro, con varias cosas comprobadas a la vez
           (CAPA 2).

        3) assertTrue(a.equals(b)) en vez de assertThat (CAPA 3).

        4) Instant.now() y new de colaboradores dentro de las clases: no se
           pueden probar (CAPA 4).

        5) Mockear todo (incluido el dominio), verificar cada llamada
           interna; ignorar la advertencia del agente de Mockito (CAPA 5).

        6) Probar lógica de negocio en @WebMvcTest; @MockBean en Boot 4
           (CAPA 6).

        7) Bases de datos en memoria en vez de la real; consultar sin flush
           (CAPA 7).

        8) Configuración de áreas en la clase principal; un @MockitoBean
           distinto en cada clase (CAPA 8).

        9) Pruebas inestables que se reintentan en vez de arreglarse
           (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Pirámide: muchas unitarias (~ms), menos de una capa (segundos),
          pocas de integración.
        - JUnit 6 (API de JUnit 5): @Test, @DisplayName, @Nested,
          @ParameterizedTest; arrange-act-assert.
        - AssertJ: frases legibles y mensajes que explican el fallo.
        - Unitarias con new y un Clock fijo; si cuesta probar, revisa el
          diseño.
        - Mockito: stubs, verify, captores; estricto; mockear fronteras, no
          el dominio; configurarlo como agente.
        - @WebMvcTest + @MockitoBean (no @MockBean en Boot 4): solo web.
        - @DataJpaTest + Testcontainers: solo datos, con rollback; flush antes
          de consultar.
        - Clase principal limpia; reusar contextos; cero pruebas inestables.
    */
}
