# De la computadora física a Docker: el sistema completo

Este archivo explica, de punta a punta y sin dar nada por sentado, cómo funciona una computadora, lo mínimo de redes necesario, cómo se logra el aislamiento entre procesos sin usar máquinas virtuales, y finalmente Docker completo, siempre a nivel de sistema: qué mecanismo del kernel se está usando en cada paso, no solo qué comando escribir.

Idea central, a la que se vuelve constantemente:

> **Docker no introduce ningún mecanismo de aislamiento nuevo.** Todo lo que hace se apoya en piezas que el kernel de Linux ya tenía antes de que Docker existiera. Docker automatiza la construcción de esas piezas y las empaqueta de forma reutilizable y distribuible.

Cuatro partes, cada una construida sobre la anterior:

- **Parte A**: hardware, sistema operativo, kernel, syscalls, proceso, hilo.
- **Parte B**: redes (IP, puerto, socket, modelo cliente-servidor, bridge, NAT).
- **Parte C**: aislamiento sin Docker (máquinas virtuales, namespaces, cgroups, capabilities/seccomp, OverlayFS).
- **Parte D**: Docker (arquitectura, comandos, imágenes, Dockerfile, redes, volúmenes, Compose, proyecto final).

Si en la Parte D algo no queda claro, la explicación de fondo está casi siempre en una parte anterior. Está pensado para leerse en orden.

# PARTE A: Cómo funciona una computadora por dentro

## A1. Hardware: los tres componentes físicos

- **CPU (procesador)**, compuesta por uno o más **núcleos**. Un núcleo ejecuta instrucciones de máquina (aritmética, comparaciones, saltos) una por una, a una frecuencia de miles de millones de ciclos por segundo. No interpreta "programas": ejecuta instrucciones binarias definidas por el conjunto de instrucciones del procesador.
- **RAM (memoria principal)**: almacenamiento **volátil**, cuyo contenido se pierde al cortar la alimentación eléctrica. Tiempos de acceso del orden de decenas de nanosegundos. Aquí residen el código y los datos de todo lo que está en ejecución en un momento dado.
- **Disco (HDD o SSD)**: almacenamiento **persistente**, que sobrevive a un apagado. Su tiempo de acceso es varios órdenes de magnitud mayor que el de la RAM (microsegundos a milisegundos). Por esa diferencia de velocidad, ningún programa opera directamente sobre datos que están en disco; antes se copian a RAM.

Todo lo demás en este documento (sistema operativo, procesos, contenedores, Docker) es software corriendo sobre estos tres componentes.

## A2. Qué significa "ejecutar un programa"

Un programa guardado en disco es un archivo binario: una secuencia estática de instrucciones y datos. Ejecutarlo implica que el sistema operativo copie ese binario (o las partes necesarias) del disco a la RAM, reserve las estructuras de control necesarias, y haga que uno o más núcleos empiecen a leer y ejecutar esas instrucciones desde una dirección de memoria inicial. A partir de ese momento, a esa actividad en curso se le llama **proceso** (A6).

## A3. El problema que resuelve un sistema operativo

Sin un sistema operativo, cada programa tendría que incluir el código necesario para controlar el modelo exacto de controlador de disco, tarjeta de red, etc. presentes en cada máquina en particular, y coordinar por sí mismo el uso del procesador con cualquier otro programa que quisiera ejecutarse al mismo tiempo. Un sistema operativo resuelve esto proporcionando una interfaz uniforme para acceder a esos recursos (independiente del fabricante y modelo exacto del hardware subyacente), y gestionando la asignación de CPU, memoria y dispositivos entre todos los procesos que compiten por ellos.

## A4. El kernel y las syscalls

El **kernel** es la parte del sistema operativo con acceso directo y privilegiado al hardware, el único capaz de hablarle de verdad al procesador, a la memoria y al disco. Un proceso normal, ejecutándose en **espacio de usuario** (user space), no tiene permiso de escribir directamente en el disco ni de enviar bytes por la tarjeta de red, así que se lo tiene que pedir al kernel. Esa petición se llama **syscall** (system call), y no es un comando que un humano escriba, sino una función técnica de bajo nivel que hace que el procesador cambie de modo de ejecución (de user mode a kernel mode) para que el kernel la atienda. `mmap()` reserva memoria, `write()` escribe datos en el disco y `clone()` crea un proceso nuevo, por dar solo tres ejemplos.

Ejemplo concreto, paso a paso, de qué ocurre cuando un programa guarda un archivo:

```
┌────────────────────────────────────────────────────────────────┐
│ PROCESO en espacio de usuario (userland)                       │
│                                                                │
│ Invoca write(fd, buffer, longitud): una llamada de biblioteca  │
│ que internamente ejecuta la instrucción de trap/syscall del    │
│ procesador.                                                    │
└────────────────────────────────────────────────────────────────┘
                        │  syscall write(): cambio de anillo de ejecución (user mode → kernel mode)
                        ▼
┌────────────────────────────────────────────────────────────────┐
│ KERNEL                                                         │
│                                                                │
│ Recibe el número de syscall y sus argumentos. Verifica         │
│ permisos del descriptor de archivo, localiza los bloques       │
│ libres en el disco, arma la operación de E/S y la entrega al   │
│ controlador del dispositivo.                                   │
└────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────┐
│ DISCO (hardware real)                                          │
│                                                                │
│ El controlador de almacenamiento graba los bytes físicamente.  │
│ El proceso en espacio de usuario nunca tuvo acceso directo al  │
│ bus del disco: todo pasó por el kernel.                        │
└────────────────────────────────────────────────────────────────┘

Esta secuencia (petición vía syscall → validación y ejecución en
kernel mode → acceso real al hardware) ocurre en toda operación de
E/S: leer un archivo, enviar datos por un socket de red (Parte B),
reservar memoria, o crear un proceso nuevo con clone().
```

Esta misma secuencia (petición vía syscall, validación y ejecución en kernel mode, acceso real al hardware) ocurre en prácticamente cualquier operación, ya sea leer un archivo, enviar datos por un socket de red (Parte B), pedir más memoria o crear un proceso nuevo con `clone()`. El kernel funciona, en la práctica, como una API, un conjunto de funciones que otros programas invocan, pensada para que la llame código y no directamente un humano desde una terminal.

## A5. La distribución (Ubuntu, Debian, Fedora...) y el userland

Una distribución **no modifica el kernel de fondo**, ya que todas usan básicamente el mismo tipo de kernel Linux. Lo que construye es todo el **userland**, la capa de programas que traduce lo que el usuario quiere hacer en las syscalls exactas que el kernel entiende (A4): el intérprete de comandos (shell), los comandos básicos (`ls`, `cp`, `mkdir`, cada uno invocando internamente syscalls como `getdents()`, `open()` o `mkdir()`), el gestor de paquetes (`apt`, `dnf`) y, opcionalmente, un entorno gráfico. Sin esta capa, el kernel resulta inútil para un humano, porque tiene todo el poder sobre el hardware pero ninguna forma cómoda de acceder a él.

A ese conjunto completo, todo lo que corre en espacio de usuario, en contraste con el kernel, que corre en espacio privilegiado, se le llama **userland**. Este término va a aparecer constantemente en la Parte D: **una imagen de Docker es, literalmente, un userland empaquetado**, ejecutable sobre cualquier kernel Linux compatible.

## A6. Proceso

Cuando el sistema operativo ejecuta un programa, crea una estructura de control para ese proceso (en Linux, un `task_struct`) que incluye:

- **Memoria propia**, no accesible directamente por otros procesos.
- Un **identificador único**, el **PID** (Process ID).
- Una **tabla de recursos asignados**: descriptores de archivos abiertos, sockets de red, etc.

Esa estructura, por sí sola, no ejecuta ninguna instrucción: necesita al menos un hilo (A7) para que ocurra actividad real.

## A7. Hilo

El **hilo** es la unidad real de ejecución dentro de un proceso: la secuencia que efectivamente avanza instrucción por instrucción sobre un núcleo de CPU. **Todo proceso tiene como mínimo un hilo**, no puede tener cero. Un proceso puede crear hilos adicionales para ejecutar tareas en paralelo; **todos los hilos de un mismo proceso comparten el mismo espacio de memoria**, a diferencia de dos procesos distintos, que tienen espacios de memoria separados.

La cantidad de hilos que pueden ejecutar instrucciones en el mismo instante está limitada por el número de **núcleos físicos** disponibles. Con más hilos que núcleos, el planificador del kernel (**scheduler**) les asigna turnos de CPU muy cortos (**time-slicing**), dando la apariencia de ejecución simultánea sin que realmente lo sea a nivel de hardware.

Un contenedor, técnicamente, no es más que un proceso con al menos un hilo: exactamente el objeto descrito en esta sección, con restricciones adicionales del kernel aplicadas encima (Parte C).

# PARTE B: Redes necesarias para entender Docker

Docker usa IP, puertos y NAT constantemente. Esta parte cubre esos conceptos con la profundidad exacta que hace falta para que la sección de redes de Docker (D10) sea comprensible a nivel de mecanismo, no solo de sintaxis.

## B1. Qué es una red

Una red es un conjunto de dos o más computadoras (u otros dispositivos) capaces de intercambiar datos entre sí a través de un medio físico (cable, fibra, radiofrecuencia). Internet es la red más grande que existe: una red de redes interconectadas.

## B2. Dirección IP

Una **dirección IP** es un identificador numérico que distingue a una computadora dentro de una red (ej. `192.168.1.10` en IPv4). Es necesaria para que los datos que viajan por la red sepan a qué máquina exacta deben llegar. Existen rangos de direcciones **privadas** (como `192.168.0.0/16` o `172.16.0.0/12`), válidas solo dentro de una red local y no enrutables directamente en Internet. Docker usa este tipo de rangos para sus redes internas (D10).

## B3. Puerto

Una sola máquina normalmente ejecuta varios programas que quieren recibir datos de red al mismo tiempo (un servidor web, un servidor de base de datos, un servicio SSH). La IP identifica la máquina, pero no distingue a cuál de esos programas corresponde un paquete entrante. Para eso existe el **puerto**: un número de 16 bits (0 a 65535) que, junto con la IP y el protocolo (TCP o UDP), identifica de forma única un extremo de comunicación.

```
┌──────────────────────────────────────────────────────┐
│ HOST con dirección IP 192.168.1.10                   │
└──────────────────────────────────────────────────────┘

Tabla de sockets en escucha que mantiene el kernel:

  PUERTO   PROTOCOLO   PROCESO EN ESCUCHA (PID)
  ------   ---------   -------------------------
  22       TCP         sshd (812)
  80       TCP         nginx (1421)
  443      TCP         nginx (1421)

Un paquete entrante trae en su cabecera la IP y el puerto de
destino: 192.168.1.10:80. El kernel busca en esta tabla el socket
en estado LISTEN que coincide exactamente con ese puerto y le
entrega los datos al proceso dueño de ese socket (nginx, PID
1421). Sin el número de puerto, el kernel no tendría forma de
decidir a cuál de los tres procesos de la tabla corresponden
los datos que acaban de llegar a esa IP.
```

## B4. Socket

Un **socket** es la estructura del sistema operativo que representa un extremo de una conexión de red, definida por la combinación de IP local, puerto local, IP remota, puerto remoto y protocolo. Un programa lo crea llamando a la syscall `socket()`, que le devuelve un descriptor de archivo (el mismo tipo de identificador numérico que usa para archivos abiertos en disco, ver A6): a partir de ahí, leer y escribir en la red se hace con las mismas syscalls genéricas de E/S (`read()`, `write()`) que usaría sobre cualquier otro descriptor. Encima de esa base se apoyan las syscalls más específicas de red que arman la conexión: `bind()`, `listen()` y `accept()` del lado servidor (ver B5), o `connect()` del lado cliente. Es, otra vez, la misma mecánica de A4: el programa nunca manipula la tarjeta de red directamente, siempre se lo pide al kernel a través de estas llamadas.

## B5. Modelo cliente-servidor

"Servidor" y "cliente" son los dos roles posibles y cualquier proceso puede tomar uno u otro según qué syscalls llame.

Un proceso toma el rol de **servidor** cuando llama a `bind()`, pidiéndole al kernel que asocie su socket con una IP y un puerto locales específicos, y luego a `listen()`, que marca ese socket como apto para recibir conexiones. A partir de ese `listen()`, el kernel empieza a mantener, para ese socket, una cola (el backlog) donde se acumulan las peticiones de conexión entrantes que el proceso todavía no ha recogido. Un proceso toma el rol de **cliente** cuando, en lugar de eso, llama a `connect()` indicando la IP y el puerto de quien hace de servidor; esa llamada dispara, del lado del kernel, el intercambio de paquetes necesario para establecer la conexión (en TCP, el conocido "three-way handshake"). Cuando la conexión queda lista, el proceso servidor la retira de su cola de backlog con `accept()` (B4), que le entrega un socket nuevo, distinto del socket original que sigue en escucha y dedicado por completo a esa conversación.

Como todo este mecanismo se resuelve mediante syscalls que atiende el kernel (A4), da exactamente igual si el proceso que juega alguno de estos dos roles corre directamente sobre hardware físico, dentro de una máquina virtual o dentro de un contenedor (Parte C): en los tres casos hay un kernel Linux debajo procesando las mismas syscalls de la misma manera.

## B6. Bridge (puente de red)

Un **switch** (en su forma física) o un **bridge** (su forma virtual, implementada en software) es un dispositivo de red de nivel de enlace (capa 2 del modelo OSI) que conecta varios equipos dentro de una misma red local y reenvía cada trama Ethernet al destino correcto basándose en la dirección MAC de destino, sin necesidad de salir a otra red. El kernel de Linux implementa un bridge de software (el driver `bridge`), que es exactamente el mecanismo que usa Docker para conectar contenedores entre sí (D10).

## B7. NAT (traducción de direcciones)

**NAT (Network Address Translation)** es la reescritura, en tránsito, de las direcciones IP (y opcionalmente los puertos) en la cabecera de un paquete. Se usa típicamente para permitir que equipos con direcciones IP privadas (B2) se comuniquen con el exterior, o para redirigir tráfico entrante hacia un destino interno específico. En Linux se implementa mediante **netfilter/iptables**. Este es exactamente el mecanismo detrás de `docker run -p` (D10): una regla de NAT reescribe la IP/puerto de destino de un paquete que llega al host, para entregarlo dentro del namespace de red aislado de un contenedor.

# PARTE C: Virtualización y contenedores (el aislamiento, sin Docker todavía)

## C1. El problema

Ya se estableció en A6 que cada proceso tiene su propia memoria, pero eso no basta para muchos casos reales, donde hace falta que un grupo de procesos no pueda ver los archivos de otro, que no pueda consumir toda la CPU o memoria del sistema, y que pueda tener sus propias versiones de librerías sin chocar con las de otro grupo de procesos. Hay dos aproximaciones para resolver esto: las máquinas virtuales y los contenedores.

## C2. Máquina virtual

Una **máquina virtual (VM)** es un sistema operativo completo, con su propio kernel, no compartido con nadie, corriendo sobre hardware que, en realidad, no existe físicamente de forma dedicada para ella. Un programa llamado **hipervisor** expone, ante ese sistema operativo, un procesador, una memoria y un disco virtualizados, cuando en realidad todo eso se reparte entre varias VMs sobre el mismo hardware físico real.

Como cada VM trae su propio kernel completo y hay un hipervisor de por medio traduciendo el acceso al hardware, el aislamiento es más fuerte que el de un contenedor, pero el costo es mayor: más uso de RAM y disco, y un arranque más lento (implica arrancar un sistema operativo completo).

## C3. El costo de las VMs a gran escala

Si se necesita correr 50 copias de la misma aplicación, con VMs eso implica 50 kernels completos ejecutándose simultáneamente sobre el mismo hardware, cada uno con su propia copia de archivos base del sistema operativo, consumiendo varios gigabytes de RAM y disco solo por existir, antes siquiera de que la aplicación real empiece a correr.

## C4. Contenedores

Un **contenedor** es uno o varios procesos normales ejecutándose sobre el **mismo kernel real** de la máquina host. **Los contenedores no tienen su propio kernel.** El aislamiento se logra mediante mecanismos que el kernel de Linux ya provee, no mediante un sistema operativo separado:

- Los **namespaces** dan a un proceso una vista aislada de determinados recursos del sistema (procesos, red, usuarios, montajes/filesystem).
- Los **cgroups** limitan y contabilizan los recursos que puede usar un proceso (CPU, memoria, número de procesos y, según la configuración, I/O).
- Las **capabilities** y **seccomp** restringen qué privilegios y qué syscalls tiene permitido usar ese proceso.

Además, un contenedor normalmente tiene su propio filesystem, construido a partir de capas mediante un sistema de archivos por capas (C8). Esto permite que tenga sus propios archivos, librerías y versiones de software (incluso un userland completo basado en Ubuntu, Debian o Alpine), aunque el kernel que ejecuta esos programas siga siendo el kernel del host. Por eso dos contenedores pueden tener distintos userlands y distintas versiones de librerías, compartiendo el mismo kernel.

## C5. Namespaces en detalle

Cada tipo de namespace aísla un recurso distinto del kernel. En todos los casos, el proceso de adentro obtiene una vista restringida de ese recurso, mientras el recurso real del host sigue existiendo por debajo, sin duplicarse.

- El **PID namespace** hace que el kernel mantenga, para un mismo proceso, más de un número de identificación simultáneo (una entrada en el arreglo `numbers[]` de su `struct pid`), un PID visible desde el host (ej. 4721) y otro PID visible desde dentro del namespace (ej. 1). Es el mismo `task_struct` (el mismo proceso), referenciado con un identificador distinto según quién lo consulta.
- El **mount namespace** le da a cada proceso su propia `struct mnt_namespace` (su propio árbol de puntos de montaje), aunque ese árbol apunta al mismo almacenamiento físico de abajo. Crear este namespace no copia ningún dato; solo cambia qué rutas del filesystem son visibles para ese proceso.
- El **network namespace** le da a cada proceso su propia `struct net` completa, con su propia interfaz de red y su propia tabla de rutas. Recién creado está prácticamente vacío (solo la interfaz de loopback, sin `eth0`, sin rutas hacia el host), porque es un espacio de red separado, no una vista filtrada del espacio de red del host.
- El **user namespace** introduce una tabla de mapeo de identificadores de usuario, donde el UID 0 (root) dentro del namespace puede corresponder al UID 100000 fuera de él. No crea una identidad nueva, sino que traduce una identidad existente según el namespace desde el que se consulte.

## C6. Cgroups

Un **cgroup** (control group) es el mecanismo que limita y contabiliza el uso de recursos (CPU, memoria, número de procesos y, según la configuración, I/O de disco) de un proceso o grupo de procesos. A diferencia de los namespaces, que son estructuras internas del kernel, un cgroup (en su versión v2) es **un directorio real** dentro de un sistema de archivos virtual (`cgroupfs`, normalmente montado en `/sys/fs/cgroup/`), con archivos como `memory.max` o `cpu.max` que representan los límites configurados. Es un único árbol compartido por todo el host, no una estructura duplicada por proceso.

## C7. Capabilities y seccomp

Un proceso ejecutándose como root dentro de su propio namespace de usuario conserva, por defecto, un conjunto amplio de privilegios a nivel de kernel. Las **capabilities** descomponen los privilegios de root en unidades independientes, representadas internamente como un bitmask (un número de 64 bits donde cada bit habilita o deshabilita un privilegio específico). Docker, por defecto, retira capabilities como `CAP_SYS_ADMIN` antes de arrancar un contenedor, dejando un subconjunto reducido (`CapEff`).

**Seccomp** (secure computing mode) es un filtro, implementado con BPF, que restringe qué syscalls (A4) puede invocar un proceso. El perfil por defecto de Docker bloquea aproximadamente 44 de las cerca de 450 syscalls existentes en el kernel.

## C8. Filesystem por capas (OverlayFS)

Copiar físicamente un userland completo por cada contenedor sería tan costoso en disco como una VM. La solución es un sistema de archivos **por capas**, como OverlayFS: varias capas de solo lectura apiladas (reutilizables entre muchos contenedores distintos) más una única capa de escritura propia de cada contenedor, montada encima. El resultado combinado (`merged/`) se comporta como un filesystem normal ante el proceso, aunque por debajo está compuesto de piezas compartidas y una pieza privada. Esta misma estructura reaparece, con nombres de Docker (imagen, capa de escritura), en D7.

## C9. Diagrama completo

```
┌──────────────────────────────────────────────────────────────────┐
│ LINUX KERNEL                                                     │
│                                                                  │
│ Administra DIRECTAMENTE: procesos, memoria, CPU,                 │
│ filesystem, red, permisos.                                       │
│                                                                  │
│ Namespaces, cgroups y capabilities NO son recursos:              │
│ son 3 MECANISMOS DISTINTOS para controlar el acceso              │
│ de un proceso a esos recursos:                                   │
│   • namespaces           → qué VE el proceso                     │
│   • cgroups               → cuánto PUEDE USAR                    │
│   • capabilities/seccomp  → qué tiene PERMITIDO HACER            │
└──────────────────────────────────────────────────────────────────┘

                                 │
                                 ▼
   ┌────────────────┐   ┌────────────────┐   ┌────────────────┐
   │   PROCESO A    │   │   PROCESO B    │   │   PROCESO C    │
   │     normal     │   │     normal     │   │  (container)   │
   └────────────────┘   └────────────────┘   └────────────────┘
                                                       │
                                                       ▼  usa 6 mecanismos del kernel:

┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
│ PID NS               │  │ MOUNT NS             │  │ NETWORK NS           │
│ ES: 1 entrada en     │  │ ES: struct           │  │ ES: struct net       │
│   numbers[] de       │  │   mnt_namespace      │  │   completa           │
│   struct pid         │  │   (árbol vfsmount)   │  │                      │
│                      │  │                      │  │ » NUEVO, vacío       │
│ » REFERENCIA         │  │ » NUEVO árbol,       │  │ Recién creado:       │
│ MISMO task_struct,   │  │   storage REAL       │  │ solo 'lo' (down),    │
│ otro ID según        │  │ Propio, pero         │  │ sin eth0, sin        │
│ quién lo mira.       │  │ apunta al MISMO      │  │ rutas del host.      │
│ n0=4721 (host)       │  │ disco de abajo.      │  │ Universo aparte,     │
│ n1=1 (adentro)       │  │ No copia nada.       │  │ no una vista.        │
└──────────────────────┘  └──────────────────────┘  └──────────────────────┘

┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
│ USER NS              │  │ CGROUP (v2)          │  │ CAPS + SECCOMP       │
│ ES: struct           │  │ ES: directorio       │  │ ES: bitmask (u64)    │
│   user_namespace     │  │   real en cgroupfs   │  │   + filtro BPF       │
│   (tabla mapeo)      │  │   (NO es namespace)  │  │   (NO es namespace)  │
│                      │  │                      │  │                      │
│ » NUEVO mapeo        │  │ » REAL               │  │ » BITMASK/FILTRO     │
│ UID 0 adentro        │  │ /sys/fs/cgroup/      │  │ CapEff: subset       │
│   = UID 100000       │  │   docker-<id>/       │  │ reducido (sin        │
│   afuera.            │  │     cpu.max          │  │ CAP_SYS_ADMIN).      │
│ No crea datos,       │  │     memory.max       │  │ seccomp bloquea      │
│ solo traduce.        │  │ UN árbol, para       │  │ ~44 de ~450          │
└──────────────────────┘  │ TODO el host.        │  │ syscalls.            │
                          └──────────────────────┘  └──────────────────────┘

                                   │
                                   ▼  MOUNT NS y CGROUP apuntan aquí (no lo duplican):

┌──────────────────────────────────────────────────────────────────┐
│ STORAGE: mismo disco físico, para A, B y C                       │
│                                                                  │
│ /sys/fs/cgroup/system.slice/docker-<id>.scope/                   │
│   ← REAL, UN solo árbol, compartido por todo el host             │
│                                                                  │
│ /var/lib/docker/overlay2/                                        │
│   ├── <capa-imagen>/diff/    ← solo lectura                      │
│   └── <container_id>/                                            │
│        ├── diff/             ← capa escribible del contenedor    │
│        └── merged/  ← el mnt_ns de C monta ESTO como "/"         │
└──────────────────────────────────────────────────────────────────┘

                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│ ¿QUÉ ES UN CONTENEDOR, EXACTAMENTE?                              │
│                                                                  │
│ NO existe un "struct container" en el kernel.                    │
│ No es un tipo de objeto que el kernel reconozca.                 │
│                                                                  │
│ Un contenedor ES:                                                │
│   1 proceso Linux normal (1 task_struct)                         │
│   + namespaces propios      (controla qué VE)                    │
│   + su propio cgroup        (controla cuánto USA)                │
│   + capabilities reducidas  (controla qué HACE)                  │
│   + seccomp                 (controla qué SYSCALLS)              │
│   + mnt_ns → rootfs vía OverlayFS  (su filesystem)               │
│                                                                  │
│ "Contenedor" es el NOMBRE que le damos a la SUMA                 │
│ coordinada de estas piezas, no una entidad nueva                 │
│ que el kernel construye; es configuración aplicada               │
│ a un proceso que, por dentro, sigue siendo un                    │
│ proceso Linux corriente.                                         │
└──────────────────────────────────────────────────────────────────┘
```

## C10. Resumen de esta parte

El kernel nunca ejecuta "un contenedor", sino que ejecuta procesos normales todo el tiempo. Lo que Docker o `runc` hacen es tomar uno de esos procesos y adjuntarle, pieza por pieza, seis mecanismos distintos, cada uno de naturaleza diferente: una referencia, tres estructuras nuevas, un árbol real y un filtro. Ninguna de esas seis piezas por sí sola es "el contenedor"; la palabra describe el conjunto, no un objeto que se pueda señalar y decir "eso es".

## C11. Contenedor vs. máquina virtual

|                        | Máquina virtual                                   | Contenedor                                                        |
| ---------------------- | ------------------------------------------------- | ----------------------------------------------------------------- |
| Kernel                 | Uno propio por VM, virtualizado por el hipervisor | Comparte el kernel real del host                                  |
| Aislamiento            | Sistema operativo completo separado               | Namespaces + cgroups + capabilities/seccomp sobre el mismo kernel |
| Arranque               | Segundos a minutos (arranca un SO completo)       | Milisegundos a segundos (arranca un proceso)                      |
| Peso en disco/RAM      | Gigabytes (SO completo por cada VM)               | Megabytes (solo el userland adicional de esa imagen)              |
| Diferencia fundamental | Ejecuta su propio kernel                          | Usa el kernel del host                                            |

# PARTE D: Docker

Con las Partes A, B y C ya establecidas, Docker no introduce ningún concepto nuevo de aislamiento, sino herramientas: automatización de lo visto en la Parte C, un formato de empaquetado, y una arquitectura cliente-servidor para operarlo.

## D1. Qué agrega Docker sobre lo ya descrito en la Parte C

| Mecanismo (Parte C)                       | Cómo lo usa Docker                                                  |
| ----------------------------------------- | ------------------------------------------------------------------- |
| Namespaces (PID, MOUNT, NETWORK, USER...) | Uno por contenedor, creados al hacer `docker run`                   |
| Cgroups                                   | Uno por contenedor, para limitar CPU/memoria (`--memory`, `--cpus`) |
| Capabilities + seccomp                    | Docker arranca cada contenedor con un set reducido por defecto      |
| Filesystem por capas (OverlayFS, C8)      | Es la estructura interna de una **imagen** de Docker                |

Lo que Docker agrega, y que el kernel no provee por sí solo, son tres piezas:

1. La **imagen**, un formato estándar para empaquetar un userland (A5) en capas, portátil entre máquinas.
2. El **daemon y el CLI**, una arquitectura cliente-servidor que evita tener que construir namespaces y cgroups manualmente por cada contenedor.
3. El **registry**, un repositorio remoto (Docker Hub, u otro) para distribuir imágenes.

El resto de esta parte desarrolla estas tres piezas.

## D2. Las 4 razones por las que se usa Docker

| Razón                           | Problema que resuelve                                                                       | Mecanismo de sistema que lo sostiene                                                                                                                  |
| ------------------------------- | ------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Reproducibilidad**            | Diferencias de comportamiento entre entornos                                                | La imagen empaqueta el userland completo (A5): mismas versiones de librerías y binarios, independientemente de qué tenga instalado la máquina destino |
| **Aislamiento de dependencias** | Dos aplicaciones necesitan versiones distintas de una misma dependencia en la misma máquina | Cada contenedor tiene su propio mount namespace (C5) con su propio árbol de archivos                                                                  |
| **Portabilidad**                | Mover una aplicación de una máquina a otra sin reinstalar nada                              | Si el kernel de destino es Linux (o una VM Linux, D5), la imagen se ejecuta de forma idéntica                                                         |
| **Versionado**                  | Volver a una versión anterior sin reconstruir manualmente                                   | Cada imagen es inmutable y está identificada por un tag/hash, equivalente a un commit de un filesystem completo                                       |

**🔧 Practica esto:** ejecuta `docker run hello-world` y lee el mensaje que imprime, que describe en texto plano los pasos que acaba de dar el daemon (buscar imagen local, no encontrarla, solicitarla al registry, crear el contenedor, ejecutarlo y devolver el output). En esencia, ese mensaje resume la arquitectura descrita en D4.

## D3. Por qué la industria migró de VMs a contenedores

Con 50 VMs corriendo la misma aplicación, hay 50 kernels completos compitiendo por la misma CPU/RAM física, cada uno sosteniendo un sistema operativo entero. Con 50 contenedores, un solo kernel se comparte entre todos, y cada uno solo consume el costo real de su propio userland. De ahí la mayor densidad de contenedores por servidor físico y su arranque en milisegundos (arrancan un proceso, no un sistema operativo). Esto no invalida a las VMs: cuando el aislamiento total justifica el costo, se siguen usando (de hecho, D5 muestra que Docker en Windows/Mac corre, técnicamente, dentro de una VM).

## D4. Arquitectura de Docker

Docker no es un único programa: es varios procesos separados que se comunican entre sí.

```
┌────────────────────────────────────────────────────────────────────┐
│ TÚ escribes en la terminal:                                        │
│                                                                    │
│ docker run nginx                                                   │
└────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│ DOCKER CLI (cliente)                                               │
│                                                                    │
│ Solo traduce tu comando a una petición HTTP. No ejecuta nada por   │
│ sí mismo, ni siquiera necesita estar en la misma máquina que el    │
│ daemon.                                                            │
└────────────────────────────────────────────────────────────────────┘
                        │  REST API sobre socket Unix (/var/run/docker.sock)
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│ DOCKERD: el daemon (corre en segundo plano SIEMPRE)                │
│                                                                    │
│ Decide QUÉ hay que hacer: ¿existe la imagen local? ¿hay que        │
│ descargarla? ¿qué red, qué volumen, qué límites de CPU/memoria le  │
│ pongo?                                                             │
└────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│ CONTAINERD                                                         │
│                                                                    │
│ Gestiona el ciclo de vida del contenedor (crear, pausar, parar,    │
│ borrar). No toca el kernel directamente: delega la ejecución real  │
│ un nivel más abajo.                                                │
└────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│ RUNC                                                               │
│                                                                    │
│ El que EJECUTA las syscalls de verdad: clone() con las flags de    │
│ namespaces, escribe en cgroupfs, dropea capabilities, aplica el    │
│ filtro seccomp. Es quien arma, pieza por pieza, los 6 mecanismos   │
│ descritos en la Parte C.                                           │
└────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│ PROCESO nginx corriendo                                            │
│                                                                    │
│ Un contenedor real ya existe: un proceso Linux normal con          │
│ namespaces + cgroup + capabilities reducidas + su rootfs vía       │
│ OverlayFS.                                                         │
└────────────────────────────────────────────────────────────────────┘

                (aparte, fuera de esta cadena vertical)
┌────────────────────────────────────────────────────────────────────┐
│ REGISTRY: ej. Docker Hub                                           │
│                                                                    │
│ Almacena imágenes de forma remota. dockerd solo le pregunta        │
│ "¿tienes nginx:latest?" cuando NO encuentra ya esa imagen guardada │
│ en el disco local.                                                 │
└────────────────────────────────────────────────────────────────────┘
```

- El **Docker CLI** traduce el comando escrito a una petición HTTP y no ejecuta nada por sí mismo; puede estar en una máquina distinta a la del daemon.
- **`dockerd`** (el daemon) es un proceso que corre permanentemente en segundo plano y decide qué acciones tomar: verificar si la imagen existe localmente, descargarla si hace falta, configurar red, volúmenes y límites de recursos. Si se detiene, los contenedores ya en ejecución pueden seguir vivos (los sostiene `containerd`), pero quedan sin gestión hasta que el daemon vuelva a levantar.
- **`containerd`** supervisa el ciclo de vida del contenedor (crear, pausar, detener, eliminar) y no interactúa con el kernel directamente.
- **`runc`** es el único componente de esta cadena que ejecuta syscalls contra el kernel, con `clone()` para las flags de namespaces, escritura en `cgroupfs`, retiro de capabilities y aplicación del filtro seccomp. Construye, syscall por syscall, los seis mecanismos descritos en la Parte C.
- El **registry** queda externo a esta cadena; `dockerd` lo consulta únicamente cuando la imagen solicitada no existe en el disco local.

El cliente y el daemon no tienen por qué estar en la misma máquina: lo único que los conecta es la dirección donde el cliente busca el socket del daemon, y esa dirección la resuelve un **context**. Por defecto, en Linux/Mac el cliente busca el socket Unix `/var/run/docker.sock`; en Windows, busca el named pipe `\\.\pipe\docker_engine`. `docker context ls` muestra los contexts disponibles (marcando con `*` el activo) y `docker context use <nombre>` cambia a cuál daemon le habla el cliente, sin reinstalar nada. La variable de entorno `DOCKER_HOST` (ej. `DOCKER_HOST=ssh://usuario@servidor`) permite lo mismo de forma puntual, incluso para hablarle a un daemon en otra máquina por SSH.

**🔧 Practica esto:** con Docker en ejecución, `ps aux | grep -E "dockerd|containerd"` (en Linux nativo; en Docker Desktop, entrando con `wsl -d docker-desktop`) muestra ambos procesos activos de forma permanente, incluso sin ningún contenedor corriendo. Después, `docker context ls` muestra el context activo y su dirección de conexión real.

## D5. Docker en Windows/Mac

```
EN LINUX NATIVO (la ruta corta):

┌────────────────────────────────────────────────────────────────────┐
│ KERNEL LINUX DEL HOST                                              │
│                                                                    │
│ Ya tiene namespaces, cgroups, capabilities. dockerd le habla       │
│ directo, sin nada de por medio.                                    │
└────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│ dockerd + contenedores                                             │
│                                                                    │
│ Procesos normales sobre ESE MISMO kernel.                          │
└────────────────────────────────────────────────────────────────────┘


EN WINDOWS:

┌────────────────────────────────────────────────────────────────────┐
│ ¿DÓNDE ESTÁ EL KERNEL LINUX SI WINDOWS NO TIENE UNO?               │
└────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│ WSL2: Windows Subsystem for Linux v2                               │
│                                                                    │
│ NO es una capa de traducción de syscalls (eso era WSL1).           │
│ Es una máquina virtual, con su propio kernel Linux real,           │
│ corriendo sobre Hyper-V. Mismo concepto descrito en C2: un         │
│ hipervisor expone hardware virtualizado a un kernel completo.      │
└────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│ KERNEL LINUX (dentro de la VM WSL2)                                │
│                                                                    │
│ Aquí sí existen namespaces/cgroups/capabilities de verdad.         │
└────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────────┐
│ dockerd corre DENTRO de esta VM, no en Windows.                    │
│ Los contenedores son procesos normales sobre el kernel             │
│ de la VM; NO sobre el kernel de Windows (que ni siquiera           │
│ lo expone).                                                        │
└────────────────────────────────────────────────────────────────────┘

En Windows, un contenedor sigue siendo un proceso con los 6
mecanismos de la Parte C adjuntos. El kernel que los sostiene
vive dentro de una VM Linux real, no en el kernel de Windows.
```

Retomando la comparación de C11, Docker Desktop en Windows es, por dentro, una máquina virtual, con su propio kernel completo y su propio hipervisor (Hyper-V), y esa VM ejecuta contenedores dentro de sí misma con el mecanismo estándar de la Parte C. No hay contenedores corriendo directamente sobre Windows, sino siempre sobre el kernel Linux de esa VM. En Mac ocurre lo mismo, con un hipervisor distinto (Apple Virtualization Framework).

Esto también explica por qué operaciones de I/O intensivas pueden rendir menos en Windows/Mac que en Linux nativo: cada operación de disco o red cruza la frontera entre la VM y el host, un salto que en Linux nativo no existe (`dockerd` habla directamente con el kernel real de la máquina).

### Qué pasa exactamente al abrir la aplicación Docker Desktop

Docker Desktop es, en sí mismo, un programa de escritorio normal para Windows (o Mac), y al abrirlo dispara una secuencia concreta de pasos:

1. Arranca (si no estaba ya corriendo) la distro WSL2 utilitaria `docker-desktop`, la VM Linux del diagrama de arriba. Windows puede tener esa VM apagada mientras no la necesites; una VM apagada no consume CPU ni RAM.
2. Dentro de esa VM, arranca `dockerd`.
3. En el lado de Windows, arranca un proceso de backend propio de Docker Desktop que expone el named pipe `\\.\pipe\docker_engine` y lo reenvía hacia el socket Unix real de `dockerd`, dentro de la VM. Esa traducción de transporte (named pipe de Windows ↔ socket Unix de Linux) es exactamente el puente que le permite a un `docker.exe` nativo de Windows hablarle a un daemon que en realidad vive en un kernel Linux.
4. También arranca `vpnkit` (o el mecanismo equivalente según la versión), que traduce el tráfico de red entre la VM y la red real de Windows, y un servicio de sincronización de archivos que permite montar rutas de Windows dentro de contenedores Linux con `-v` (ver `docker_practico.md`, sección 7).

Mientras Docker Desktop está cerrado, nada de esto existe: ni la VM, ni `dockerd`, ni el named pipe. Por eso `docker version` falla con un error de conexión hasta que abres la aplicación; no es que el comando `docker` "no funcione", es que no hay ningún daemon del otro lado del socket al que preguntarle nada.

**¿Es poco profesional depender de abrir una aplicación?** No, es una consecuencia directa de que Windows y Mac no tienen un kernel Linux nativo. En un servidor Linux de producción, `dockerd` es un servicio gestionado por `systemd` (`sudo systemctl enable docker` lo deja arrancando automáticamente en cada boot, sin ninguna aplicación gráfica de por medio, ver `docker_practico.md`, sección 1.2). Docker Desktop existe únicamente para replicar esa misma comodidad en un sistema operativo que no puede correr `dockerd` directamente; la "aplicación que hay que abrir" es, en el fondo, el proceso que arranca y sostiene la VM y el daemon por ti. En Linux, ese trabajo lo hace `systemd` en segundo plano, sin interfaz gráfica, desde el arranque de la máquina.

Configuración relevante en Docker Desktop (**Settings → Resources**): ahí se fija cuánta CPU, RAM y disco recibe la VM completa. Ese valor es un techo real: si le das 4 GB de RAM a la VM y luego intentas `docker run --memory=8g`, el límite de cgroup (`sistema_y_docker.md` C6) que puede llegar a aplicar `runc` nunca podrá superar lo que la propia VM tiene disponible. En **Settings → General** está la opción "Start Docker Desktop when you sign in", que, si la activas, hace que los pasos de arriba ocurran automáticamente al iniciar sesión en Windows, sin que tengas que abrir la aplicación a mano cada vez.

**🔧 Practica esto:** en Windows, `wsl -l -v` en PowerShell muestra una distro `docker-desktop` en ejecución, que es justamente la VM del diagrama. Con `wsl -d docker-desktop` y luego `uname -a` se confirma que es un kernel Linux real, distinto al de Windows. Cierra Docker Desktop por completo y corre `wsl -l -v` de nuevo: la distro `docker-desktop` va a aparecer detenida (`Stopped`), confirmando que cerrar la aplicación apaga la VM entera, no solo una ventana.

## D6. Comandos básicos y su efecto en el kernel

| Comando                     | Qué hace                                                      | Qué ocurre a nivel de kernel/arquitectura                                                                                                                                       |
| --------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docker pull <img>`         | Descarga una imagen del registry                              | dockerd solicita al registry las capas que no existan ya en `/var/lib/docker/overlay2/`                                                                                         |
| `docker run <img>`          | Crea y arranca un contenedor                                  | Si la imagen no existe localmente, hace `pull` primero → `runc` monta el overlay (capas + nueva capa R/W) → crea los namespaces → crea el cgroup → arranca el proceso principal |
| `docker ps`                 | Lista contenedores en ejecución                               | Consulta el estado que `containerd` mantiene de cada proceso                                                                                                                    |
| `docker stop <id>`          | Detiene un contenedor de forma ordenada                       | Envía **SIGTERM** al proceso principal, espera (10s por defecto) y, si no terminó, envía **SIGKILL**                                                                            |
| `docker kill <id>`          | Detiene un contenedor de inmediato                            | Envía **SIGKILL** directamente, sin espera                                                                                                                                      |
| `docker start <id>`         | Vuelve a arrancar un contenedor detenido                      | Reutiliza la misma capa R/W que ya tenía; no equivale a `run`                                                                                                                   |
| `docker rm <id>`            | Elimina un contenedor detenido                                | Libera la capa R/W del overlay y los namespaces asociados                                                                                                                       |
| `docker rmi <img>`          | Elimina una imagen                                            | Solo si ningún contenedor la está usando                                                                                                                                        |
| `docker inspect <id>`       | Muestra metadata en formato JSON                              | Incluye los IDs reales de namespaces y la configuración de cgroup asignada                                                                                                      |
| `docker logs <id>`          | Muestra stdout/stderr del proceso                             | Docker redirige la salida estándar del proceso principal a un archivo de log                                                                                                    |
| `docker exec -it <id> bash` | Ejecuta un proceso nuevo dentro de un contenedor en ejecución | Crea un proceso que se une a los namespaces YA EXISTENTES de ese contenedor; no crea namespaces nuevos                                                                          |

`SIGTERM` es una señal que solicita a un proceso que termine, dándole oportunidad de liberar recursos y cerrar archivos correctamente. `SIGKILL`, en cambio, es una señal que el proceso no puede interceptar ni ignorar, así que termina de inmediato y sin posibilidad de limpieza. Este mecanismo de señales no es específico de Docker, sino el mismo que se usa para terminar cualquier proceso Linux (Parte A).

**Diferencia `start` vs `run`:** `run` crea un contenedor desde cero (imagen + nueva capa R/W vacía). `start` reinicia un contenedor existente, con su capa R/W y los datos que ya contenía.

**🔧 Practica esto:**

1. `docker run -d --name web nginx`
2. `docker ps`, para anotar el ID
3. `docker exec -it web bash` y, ya dentro, `cat /proc/1/status | grep NSpid` y `ls -la /proc/1/ns/`, que muestran los namespaces reales de PID 1 dentro del contenedor
4. Desde fuera, `docker stop web`, midiendo cuánto tarda (~10s si el proceso no responde de inmediato a SIGTERM)

## D7. Imagen vs. contenedor

- **Imagen** = plantilla de solo lectura, compuesta de capas apiladas (C8). No cambia una vez construida.
- **Contenedor** = una imagen + una capa de escritura propia + un proceso en ejecución con namespaces/cgroup/capabilities adjuntos (Parte C, ya en marcha).

```
┌──────────────────────────────────────────────────────────────┐
│ IMAGEN nginx:latest: SOLO LECTURA, nunca cambia              │
└──────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ Capa 4: COPY nginx.conf /etc/nginx/  (tu Dockerfile)         │
└──────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ Capa 3: RUN apt-get install nginx                            │
└──────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ Capa 2: RUN apt-get update                                   │
└──────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ Capa 1: FROM ubuntu:22.04  (el userland base)                │
└──────────────────────────────────────────────────────────────┘

                        │  docker run crea esto ENCIMA:
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ CAPA DE ESCRITURA DEL CONTENEDOR (thin R/W layer)            │
│ Aquí van los archivos que el proceso crea o modifica         │
│ en caliente. Se borra con 'docker rm'. Las capas de          │
│ la imagen, debajo, nunca se modifican.                       │
└──────────────────────────────────────────────────────────────┘

   + namespaces propios + su cgroup + capabilities reducidas
   + un proceso en ejecución con ese conjunto de mecanismos
   ────────────────────────────────────────
   = eso, junto, es lo que Docker llama "un contenedor"
```

Es el mismo mecanismo de OverlayFS descrito en C8 y en el diagrama de C9 (la caja "STORAGE"): la imagen corresponde a los directorios `diff/` de solo lectura, y la capa del contenedor es el `diff/` escribible montado encima, con `merged/` como resultado final visible para el proceso.

**Consecuencia práctica:** 100 contenedores creados a partir de la misma imagen `nginx:latest` comparten una única copia en disco de las capas de la imagen; cada contenedor solo agrega su propia capa de escritura, generalmente pequeña. De ahí que los contenedores sean sustancialmente más livianos que 100 copias de una VM completa (C3).

## D8. Capas de imagen y cache de build

Cada instrucción de un Dockerfile que modifica el filesystem (`RUN`, `COPY`, `ADD`) genera una capa nueva, inmutable, identificada por un hash. `docker history <imagen>` muestra esa lista de capas en orden.

El orden de las instrucciones importa para el cache de build: `docker build` reutiliza todas las capas desde el inicio hasta la primera que cambió. Si una instrucción que cambia con frecuencia (copiar código fuente) se coloca antes de una que casi nunca cambia (instalar dependencias), se invalida el cache de las dependencias en cada build, aunque no hayan cambiado.

```dockerfile
# Orden que invalida el cache de dependencias en cada build:
COPY . .
RUN npm install

# Orden que preserva el cache mientras package.json no cambie:
COPY package.json .
RUN npm install
COPY . .
```

**🔧 Practica esto:** construir la misma imagen dos veces seguidas (`docker build -t test .`) muestra `CACHED` en casi todas las líneas la segunda vez. Cambiar solo una línea del código fuente (sin tocar dependencias) y reconstruir muestra que únicamente las capas posteriores a esa línea se reconstruyen.

## D9. Dockerfile: instrucciones principales

| Instrucción  | Cuándo se ejecuta                | Genera capa                | Función                                                                                                                                                  |
| ------------ | -------------------------------- | -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `FROM`       | Al construir                     | Sí (hereda las de la base) | Define la imagen base (el userland de partida, A5)                                                                                                       |
| `RUN`        | Al construir (`docker build`)    | **Sí**                     | Ejecuta un comando y persiste su resultado en el filesystem de la imagen                                                                                 |
| `COPY`       | Al construir                     | Sí                         | Copia archivos del contexto de build al filesystem de la imagen                                                                                          |
| `ADD`        | Al construir                     | Sí                         | Como `COPY`, con capacidad adicional de descomprimir tars y descargar URLs; se prefiere `COPY` por ser más predecible                                    |
| `WORKDIR`    | Al construir (afecta al runtime) | No por sí sola             | Define el directorio de trabajo por defecto                                                                                                              |
| `ENV`        | Al construir (afecta al runtime) | No por sí sola             | Define variables de entorno del contenedor                                                                                                               |
| `EXPOSE`     | Documentación únicamente         | No                         | **No abre ningún puerto real.** Es metadata para quien lea el Dockerfile (o para `docker run -P`); el puerto real se abre con `-p` en `docker run` (D10) |
| `CMD`        | Al ejecutar (`docker run`)       | No                         | Define el comando por defecto del contenedor; puede sobreescribirse pasando otro comando al final de `docker run`                                        |
| `ENTRYPOINT` | Al ejecutar                      | No                         | Define el proceso principal, fijo; si coexiste con `CMD`, este último se convierte en sus argumentos por defecto                                         |

`RUN` se ejecuta una única vez, durante el build, y su efecto queda grabado en una capa permanente. `CMD`/`ENTRYPOINT` nunca se ejecutan durante el build, sino que solo definen qué proceso arranca cada vez que se hace `docker run` (el proceso/hilo principal descrito en A6/A7, ahora dentro de un namespace).

**`.dockerignore`** funciona como `.gitignore` para el build: excluye archivos innecesarios (`node_modules`, `.git`) del contexto de build, reduciendo tiempo de build y evitando capas de tamaño excesivo.

**🔧 Practica esto:** construye un Dockerfile con `CMD ["echo", "hola"]` y ejecútalo con `docker run tu-imagen` (imprime "hola"); luego prueba `docker run tu-imagen echo adios`, que imprime "adios" porque el CMD quedó sobreescrito. Repetir con `ENTRYPOINT` en vez de `CMD` muestra que ya no se sobreescribe de la misma forma.

## D10. Redes de Docker

| Driver             | Namespace de red usado                                                   | Caso de uso                                                                  |
| ------------------ | ------------------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| `bridge` (default) | Network namespace propio (C5), conectado mediante un bridge virtual (B6) | Contenedores que necesitan red aislada pero deben poder comunicarse entre sí |
| `host`             | Ninguno; comparte el network namespace del host                          | Máximo rendimiento de red, sin aislamiento de red                            |
| `none`             | Namespace de red vacío                                                   | Contenedores sin necesidad de red                                            |
| `overlay`          | Red virtual que cruza varios hosts físicos                               | Docker Swarm / clústeres multi-máquina (fuera del alcance de este documento) |

**Mecanismo del bridge, en detalle:**

```
HOST (namespace de red por defecto)

┌──────────────────────────────────────────────────────────┐
│ eth0 (interfaz física real, con la IP real del host)     │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│ docker0: bridge virtual creado por Docker                    │
│ (driver bridge del kernel, actúa como switch L2, 172.17.0.1) │
└──────────────────────────────────────────────────────────────┘
        │                                    │
        │ veth-host-a                        │ veth-host-b
        ▼                                    ▼
┌──────────────────────┐                ┌──────────────────────┐
│ veth (mitad A)       │                │ veth (mitad B)       │
└──────────────────────┘                └──────────────────────┘
        │                                    │
  cruza al otro                        cruza al otro
  NETWORK NAMESPACE                     NETWORK NAMESPACE
        │                                    │
        ▼                                    ▼
┌────────────────────────┐          ┌────────────────────────┐
│ eth0 DENTRO del        │          │ eth0 DENTRO del        │
│ CONTENEDOR A           │          │ CONTENEDOR B           │
│ (172.17.0.2)           │          │ (172.17.0.3)           │
└────────────────────────┘          └────────────────────────┘


ENTRADA DE TRÁFICO EXTERNO (docker run -p 8080:80)

┌──────────────────────────────────────────────────────────────┐
│ CLIENTE externo solicita: <ip-publica-del-host>:8080         │
└──────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────────────┐
│ HOST, regla de NAT (netfilter/iptables) creada por Docker:     │
│ "destino 8080/tcp del host → reescribir a 172.17.0.2:80"       │
└────────────────────────────────────────────────────────────────┘
                        │  el paquete, ya reescrito, cruza al netns
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ CONTENEDOR A : su proceso, con un socket en escucha en       │
│ el puerto 80 de SU namespace, recibe el paquete              │
└──────────────────────────────────────────────────────────────┘
```

Esto corresponde a la caja "NETWORK NS" del diagrama C9: cada contenedor tiene su propio namespace de red (IP, tabla de rutas propias, B2-B4), y la única conexión con el exterior es un **par `veth`** (dos interfaces virtuales enlazadas entre sí) cuya mitad vive en el namespace del host, conectada al bridge `docker0` (B6), y cuya otra mitad vive dentro del namespace del contenedor.

`docker run -p 8080:80` aplica exactamente el mecanismo de NAT descrito en B7: se agrega una regla en el host que reescribe el destino de los paquetes que llegan al puerto 8080 hacia `172.17.0.2:80` (la IP interna del contenedor). El contenedor no expone nada hacia afuera por sí mismo, sino que es el host quien redirige tráfico hacia dentro del namespace aislado.

**🔧 Practica esto:**

1. `docker network create mired`
2. `docker run -d --name c1 --network mired nginx`
3. `ip addr show docker0` en el host (o dentro de la VM de Docker Desktop), para ver la interfaz bridge
4. `docker exec c1 ip addr show eth0`, y comparar la IP interna con la del bridge visto desde afuera

## D11. Persistencia de datos: volumes, bind mounts y tmpfs

La capa de escritura de un contenedor (D7) se elimina por completo con `docker rm`. Para que los datos sobrevivan a eso, es necesario montar almacenamiento desde fuera del contenedor. Los tres mecanismos siguientes son, en el fondo, la misma mecánica de mount namespace descrita en C5: un árbol nuevo que apunta al mismo disco físico, sin copiar nada.

| Tipo           | Ubicación en el host                     | Gestión                     | Caso de uso                                                  |
| -------------- | ---------------------------------------- | --------------------------- | ------------------------------------------------------------ |
| **Volume**     | `/var/lib/docker/volumes/<nombre>/_data` | Docker (`docker volume ls`) | Datos gestionados por Docker, como bases de datos            |
| **Bind mount** | Cualquier ruta elegida manualmente       | Manual, directa             | Reflejar cambios de código en tiempo real durante desarrollo |
| **tmpfs**      | Solo en RAM, nunca en disco              | Docker                      | Datos temporales que no requieren persistir tras un reinicio |

`-v mivolumen:/data` (volume) y `-v /ruta/en/el/host:/data` (bind mount) tienen sintaxis similar, pero difieren en que el volume es una ruta gestionada por Docker, mientras que el bind mount es una ruta arbitraria del host.

**🔧 Practica esto:**

1. `docker run -d --name db -v midata:/var/lib/data alpine sleep 3600`
2. `docker exec db sh -c "echo hola > /var/lib/data/archivo.txt"`
3. `docker rm -f db`
4. `docker run --rm -v midata:/var/lib/data alpine cat /var/lib/data/archivo.txt` para ver que el archivo persiste aunque el contenedor original ya no exista

## D12. Docker Compose

Cuando una aplicación requiere varios contenedores (por ejemplo, un servicio web más una base de datos), coordinarlos manualmente con `docker run`, asegurando las redes y volúmenes correctos, es repetitivo y propenso a error. Compose describe esa configuración en un único archivo YAML y la levanta con un solo comando.

```yaml
services:
  web:
    build: .
    ports:
      - "8080:80"
    depends_on:
      - db
  db:
    image: postgres:16
    volumes:
      - dbdata:/var/lib/postgresql/data

volumes:
  dbdata:
```

Compose no introduce ningún mecanismo nuevo. `docker compose up` genera, por debajo, la misma secuencia descrita en D6, D10 y D11: crea una red bridge propia del proyecto, ejecuta `docker build`/`docker run` por cada servicio conectándolo a esa red, y crea los volumes declarados.

**🔧 Practica esto:** con el archivo anterior, `docker compose up -d` seguido de `docker network ls` muestra una red creada automáticamente con el nombre del proyecto, con ambos servicios conectados a ella sin haber ejecutado `docker network create` manualmente.

## D13. Ciclo de vida de imágenes y registry

```
build ──► tag ──► push ──► (otra máquina) pull ──► run
```

- `docker build -t usuario/repo:tag .` construye la imagen a partir del Dockerfile en el directorio actual (el "contexto de build").
- `docker tag` asigna un nombre o etiqueta adicional a una imagen ya existente localmente, sin duplicar datos.
- `docker push usuario/repo:tag` sube la imagen a un registry (Docker Hub por defecto) y requiere `docker login` previo.
- `docker pull usuario/repo:tag` descarga la imagen en otra máquina.

**Convención de nombres:** `usuario/repositorio:tag` (ej. `luiscasillas/mi-web:v1.0`). Si se omite el tag, Docker asume `:latest`, que no indica automáticamente "la versión más reciente" sino que es simplemente el tag por defecto, y si nunca se actualiza, `latest` puede quedar desactualizado.

## D14. Extras

Las **multi-stage builds** resuelven el problema de que las herramientas de compilación (compiladores, dependencias de desarrollo) no son necesarias en producción. Una build multi-stage usa una imagen para compilar y otra, más pequeña, que solo copia el resultado ya compilado:

```dockerfile
FROM node:20 AS build
WORKDIR /app
COPY . .
RUN npm install && npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
```

La imagen final no contiene Node.js, solo los archivos estáticos ya compilados, y eso reduce el tamaño de imagen en producción de cientos de MB a unos pocos.

**`docker exec` vs `docker attach` vs `docker run`:**

- `run` crea un contenedor nuevo.
- `exec` ejecuta un proceso adicional dentro de un contenedor ya existente y en ejecución.
- `attach` conecta la terminal al proceso principal ya existente (el mismo proceso, no uno nuevo); salir con Ctrl+C puede terminar el contenedor.

Las **restart policies** (`--restart`) definen qué hace el daemon si el proceso principal del contenedor termina por sí solo o si Docker o la máquina se reinician, con valores como `no` (default), `on-failure`, `always` y `unless-stopped`.

**Síntesis: comando Docker → mecanismo de kernel**

| Comando/flag              | Mecanismo real                                                    |
| ------------------------- | ----------------------------------------------------------------- |
| `docker run`              | `clone()` con flags de namespaces (PID, MOUNT, NETWORK, UTS, IPC) |
| `--memory`, `--cpus`      | Escritura en archivos de cgroupfs (`memory.max`, `cpu.max`)       |
| `--cap-drop`, `--cap-add` | Bitmask de capabilities del proceso                               |
| `--network`               | Namespace de red + par veth + reglas de NAT si hay `-p`           |
| `-v` / `--mount`          | Mount namespace: nuevo árbol montado sobre el mismo disco         |
| Cualquier imagen          | Directorios `diff/` de OverlayFS combinados en un `merged/`       |

## D15. Proyecto: containerizar un sitio web

1. **Dockerfile**, usando una imagen base como `nginx:alpine` para un sitio estático, o multi-stage si requiere compilación previa (D14).
2. **Build y prueba local:** `docker build -t mi-sitio .` y `docker run -p 8080:80 mi-sitio`, y verificar en `localhost:8080`.
3. **Tag:** `docker tag mi-sitio tuusuario/mi-sitio:v1`.
4. **Login y push:** `docker login` y `docker push tuusuario/mi-sitio:v1`.
5. **Verificación:** confirmar la publicación en `hub.docker.com/r/tuusuario/mi-sitio`.

**Despliegue en un servidor real (EC2 u otro):** los comandos son idénticos a los ya descritos, empezando por instalar Docker en esa máquina Linux (D5, sin necesidad de VM porque ya es Linux nativo), `docker pull tuusuario/mi-sitio:v1`, `docker run -p 80:80 -d tuusuario/mi-sitio:v1`. El mecanismo subyacente (namespaces, cgroups, overlay) es el mismo sin importar el proveedor; lo único que cambia entre proveedores es la configuración de acceso de red pública a la máquina (security groups en AWS, firewall en otros), que es configuración de infraestructura, no de Docker.

## Mapa de cierre

```
┌──────────────────────────────────────────────────────────────────────┐
│ HARDWARE FÍSICO: CPU, RAM, disco (lo único que existe de verdad)     │
└──────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────────────┐
│ KERNEL LINUX: el único que le habla al hardware                      │
│ (vía syscalls: clone(), write(), mount()...)                         │
└──────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 6 MECANISMOS DEL KERNEL (Parte C)                                    │
│ namespaces · cgroups · capabilities · seccomp · OverlayFS            │
└──────────────────────────────────────────────────────────────────────┘
                        │  runc arma estos 6 mecanismos
                        ▼
┌──────────────────────────────────────────────────────────────────────┐
│ PROCESO CONTENEDOR: un proceso Linux normal con todo eso adjunto     │
└──────────────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────────────┐
│ dockerd → containerd → runc  (Parte D, arquitectura de Docker)       │
│ el tooling que automatiza construir la caja de arriba                │
└──────────────────────────────────────────────────────────────────────┘
                        │  lee su plantilla desde:
                        ▼
┌──────────────────────────────────────────────────────────────────────┐
│ IMAGEN: capas read-only guardadas en disco local                     │
└──────────────────────────────────────────────────────────────────────┘
                        │  que a veces vienen de:
                        ▼
┌──────────────────────────────────────────────────────────────────────┐
│ REGISTRY (Docker Hub u otro): donde viven las imágenes remotas       │
└──────────────────────────────────────────────────────────────────────┘

Docker no reemplaza nada de la Parte A/C: le agrega tooling
encima (empaquetado + distribución + orquestación).
```

Un contenedor de Docker es, según lo establecido en la Parte C, un proceso Linux (Parte A) con seis mecanismos del kernel adjuntos. Todo lo desarrollado en la Parte D (imágenes, Dockerfile, redes aplicando la Parte B, volúmenes, Compose) es tooling para automatizar y distribuir la construcción de esos seis mecanismos, no un reemplazo de ellos.
