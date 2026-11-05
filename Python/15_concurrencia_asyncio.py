"""
================================================================================
POR QUE EXISTE asyncio
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

EN JS:
Un programa de JS corre en UN SOLO hilo, y ese hilo siempre tiene un
EVENT LOOP corriendo por debajo, automaticamente, desde que el programa
arranca. Por eso async/await "simplemente funciona" en cualquier script
o pagina web: nunca tienes que arrancar tu el mecanismo que hace
posible la espera no bloqueante, ya viene encendido de fabrica.

EN PYTHON:
Python NO tiene ningun event loop corriendo por defecto. asyncio es
UNA opcion de concurrencia entre varias (las otras son threading, visto
en 13_archivos_y_stdlib.py, y multiprocessing), y hay que arrancarla de
forma explicita con asyncio.run() antes de que cualquier await tenga
sentido.

EN RESUMEN: asyncio es la libreria que le agrega a Python la capacidad
de async/await, pero a diferencia de JS ese motor no viene encendido
solo — tienes que prenderlo tu mismo con asyncio.run().
"""


"""
================================================================================
async def Y await: QUE PASA EXACTAMENTE AL LLAMAR UNA FUNCION ASYNC
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Llamar a una funcion async la EJECUTA de inmediato, de forma sincrona,
hasta llegar al primer await. En ese punto la funcion devuelve una
Promise (que ya esta "en camino"), y el resto de tu codigo sincrono
sigue corriendo normalmente mientras esa Promise trabaja por su cuenta.

EN PYTHON:
async def define una CORRUTINA. Llamarla NO ejecuta su cuerpo — ni
siquiera la primera linea. Lo unico que produce esa llamada es un
OBJETO CORRUTINA, inerte, en espera de que algo lo impulse. await, DENTRO
de otra corrutina, es una de las formas de impulsarlo: espera el
resultado de la corrutina indicada, cediendo el control mientras tanto
en vez de bloquear.
"""

import asyncio

async def say_hello():
    print("hello")
    await asyncio.sleep(1)
    print("...one second later")

asyncio.run(say_hello())
# hello
# ...one second later  (impreso 1 segundo despues, sin bloquear el hilo)


"""
asyncio.run() es lo que realmente arranca el event loop, corre la
corrutina que le pasaste hasta que termina, y cierra el loop al final.
Es el "motor" que le falta a un objeto corrutina para hacer algo — sin
el (o sin un await dentro de otra corrutina ya en marcha), llamar a una
funcion async def no tiene ningun efecto observable.

Si llamas una corrutina y no haces nada con el objeto que devuelve (ni
await, ni asyncio.run(), ni asyncio.create_task(), visto mas abajo),
Python ni siquiera lanza un error: se queda ahi sin ejecutarse jamas, y
solo te avisa con una advertencia.
"""

say_hello()
print("this runs immediately, right after")
# RuntimeWarning: coroutine 'say_hello' was never awaited
# this runs immediately, right after
# (ni "hello" ni el resto del cuerpo de say_hello se llegan a imprimir:
# la linea say_hello() de arriba NO ejecuta nada, solo crea el objeto
# corrutina y lo descarta de inmediato al no guardarlo ni esperarlo)


"""
EN RESUMEN: async def crea una tarea que no arranca sola. En JS, con
solo llamarla ya empieza a correr; en Python hace falta impulsarla
explicitamente con await, asyncio.run(), o asyncio.create_task().
"""


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
No existe una funcion de "dormir" que bloquee el unico hilo del
programa — JS solo ofrece setTimeout(), que ya es no bloqueante por
diseño (programa algo para despues y le devuelve el control al
programa de inmediato).

EN PYTHON:
Existen DOS funciones de espera con comportamiento opuesto, y
confundirlas es un error comun. time.sleep(segundos) (vista en
13_archivos_y_stdlib.py) BLOQUEA el programa entero durante ese tiempo,
sin ceder el control a nadie. await asyncio.sleep(segundos) SI le
devuelve el control al event loop mientras espera, permitiendo que
OTRAS corrutinas avancen durante esa espera.

EN RESUMEN: dentro de una corrutina, siempre usa await asyncio.sleep(),
nunca time.sleep() — mas abajo se muestra exactamente que se rompe si
usas la que no es.
"""


"""
================================================================================
asyncio.gather(): CORRER VARIAS CORRUTINAS AL MISMO TIEMPO
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Promise.all() recibe varias Promises, las deja avanzar concurrentemente,
y devuelve una sola Promise que se resuelve hasta que TODAS terminan,
con los resultados en el mismo orden en que se le pasaron.

EN PYTHON:
asyncio.gather() hace exactamente lo mismo con corrutinas: les pasas
varias, las corre CONCURRENTEMENTE (mientras una espera en un await,
otra avanza), y devuelve una lista con los resultados de todas, en el
orden en que se le pasaron — no en el orden en que terminaron.
"""

async def fetch_task(name, seconds):
    print(f"{name} starts")
    await asyncio.sleep(seconds)
    print(f"{name} finishes")
    return f"{name} result"

async def main():
    results = await asyncio.gather(
        fetch_task("task A", 2),
        fetch_task("task B", 1),
    )
    print(results)

asyncio.run(main())
# task A starts
# task B starts
# task B finishes   (termina primero porque solo espera 1 segundo)
# task A finishes    (termina despues, esperaba 2 segundos)
# ['task A result', 'task B result']  (el orden del resultado SI respeta
# el orden en que se pasaron las corrutinas a gather, no el orden en que
# terminaron)


"""
[IGUAL QUE JS]

Compara el tiempo total: las dos tareas juntas tardan ~2 segundos (el
maximo de las dos), NO 3 segundos (la suma) — igual que con Promise.all()
en JS. Si se hubieran esperado una despues de otra con await normal (sin
gather), si hubiera tardado la suma completa.

EN RESUMEN: gather() (como Promise.all()) corre varias tareas a la vez;
el tiempo total es el de la tarea MAS LENTA, no la suma de todas.
"""


"""
================================================================================
COMO FUNCIONA EL EVENT LOOP POR DENTRO: SCHEDULING COOPERATIVO
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

El event loop es, en el fondo, un simple bucle que mantiene una lista
de corrutinas "listas para avanzar" y les va dando su turno una por
una. La regla central es COOPERATIVA: una corrutina en ejecucion NUNCA
es interrumpida a la fuerza — sigue corriendo hasta que ELLA MISMA
llega a un await sobre algo que todavia no esta listo (un timer, una
respuesta de red). En ese momento, y SOLO en ese momento, le devuelve
el control al event loop, que aprovecha para avanzar otra corrutina que
si este lista.

Trazando el ejemplo de fetch_task("task A", 2) y fetch_task("task B", 1)
dentro de gather() de arriba, paso a paso:

    1. el event loop arranca task A: imprime "task A starts", llega a
       await asyncio.sleep(2) y CEDE el control (su timer de 2s aun no
       vence)
    2. el event loop arranca task B: imprime "task B starts", llega a
       await asyncio.sleep(1) y CEDE el control (su timer de 1s aun no
       vence)
    3. ninguna corrutina esta lista todavia; el event loop simplemente
       espera a que el primer timer expire
    4. al segundo 1, el timer de task B expira primero: el event loop
       la retoma justo despues de su await, imprime "task B finishes",
       termina y entrega su resultado
    5. al segundo 2, el timer de task A expira: el event loop la
       retoma, imprime "task A finishes", termina y entrega su
       resultado
    6. gather() ya tiene ambos resultados: los devuelve en una lista,
       en el orden en que se pasaron las corrutinas (A, B), no en el
       orden en que terminaron (B, A)

Ningun paso de estos corre "al mismo tiempo" en un sentido literal — es
UN SOLO hilo turnandose entre corrutinas cada vez que una cede el
control con await. La "concurrencia" es que MIENTRAS task A espera su
timer, el hilo no se queda parado: usa ese tiempo libre para avanzar
task B.

EN RESUMEN: el event loop no hace magia de paralelismo real, solo
turna rapidamente entre corrutinas cada vez que una de ellas cede el
control voluntariamente en un await.
"""


"""
================================================================================
EL PELIGRO REAL: UNA LLAMADA BLOQUEANTE CONGELA TODO
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El mismo riesgo existe ahi tambien, aunque se ve distinto: como JS
corre en un solo hilo con su propio event loop, un calculo sincrono muy
largo (un loop pesado sin ningun await/promesa de por medio) congela
ESE hilo completo — es literalmente la causa mas comun de que una
pagina web se sienta "trabada" o que un servidor de Node deje de
responder a otras peticiones mientras tanto.

EN PYTHON:
Pasa lo mismo, pero con una trampa especifica de asyncio: si dentro de
una corrutina usas time.sleep() (bloqueante, sin await) en vez de
await asyncio.sleep(), el event loop nunca se entera de que deberia
darle el turno a otra corrutina, porque time.sleep() nunca cede el
control — congela el UNICO hilo que existe, entero, durante ese tiempo.
"""

import time

async def bad_task(name, seconds):
    print(f"{name} starts")
    time.sleep(seconds)
    print(f"{name} finishes")

async def bad_main():
    start = time.time()
    await asyncio.gather(
        bad_task("task A", 2),
        bad_task("task B", 1),
    )
    print(f"total time: {time.time() - start:.2f}s")

asyncio.run(bad_main())
# task A starts
# task A finishes
# task B starts
# task B finishes
# total time: 3.01s


"""
Compara este resultado con el de fetch_task() de arriba: aqui task B
NI SIQUIERA EMPIEZA hasta que task A termina por completo — el "starts"
de task B sale DESPUES del "finishes" de task A. El tiempo total pasa
de ser el MAXIMO (~2s, como con asyncio.sleep) a ser la SUMA completa
(~3s): exactamente como si nunca hubieras usado gather(), porque
time.sleep() vuelve secuencial algo que se pretendia concurrente.

EN RESUMEN: dentro de codigo async, usa siempre versiones "async-aware"
de las operaciones que esperan algo (asyncio.sleep en vez de
time.sleep, clientes HTTP async en vez de requests normal, etc.) — una
sola llamada bloqueante en medio arruina la concurrencia de TODO lo
demas, no solo de esa tarea.
"""


"""
================================================================================
asyncio.create_task(): PROGRAMAR UNA CORRUTINA EN SEGUNDO PLANO
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Guardar una Promise en una variable sin ponerle await todavia deja que
siga corriendo en segundo plano por su cuenta, porque una Promise
arranca a ejecutarse desde el instante en que se crea (ejecucion
eager) — el await posterior solo sirve para recoger su resultado
cuando haga falta.

EN PYTHON:
await por si solo espera a que UNA corrutina termine antes de seguir.
Para lanzar una corrutina que avance "en paralelo cooperativo" con el
resto del codigo, sin bloquear ahi mismo esperandola, se usa
asyncio.create_task(): la registra en el event loop de inmediato y
devuelve un objeto Task, que se puede await MAS ADELANTE, cuando de
verdad se necesite su resultado. La diferencia clave con la Promise de
JS: la Task de create_task() no empieza a avanzar en el instante exacto
en que se crea, sino hasta que el event loop tiene un respiro (el
siguiente await en el codigo que la creo).
"""

async def background_job():
    print("background_job: starting")
    await asyncio.sleep(1)
    print("background_job: done")
    return "background result"

async def main_with_task():
    task = asyncio.create_task(background_job())
    print("main: task created, continuing without waiting yet")
    await asyncio.sleep(0.3)
    print("main: did something else for 0.3s")
    result = await task
    print("main: got result ->", result)

asyncio.run(main_with_task())
# main: task created, continuing without waiting yet
# background_job: starting
# main: did something else for 0.3s
# background_job: done
# main: got result -> background result


"""
ORDEN DE EJECUCION exacto: create_task() agenda background_job pero NO
la corre todavia — el print de main sigue primero. En cuanto main llega
a su propio await asyncio.sleep(0.3), le cede el turno al event loop,
que aprovecha para arrancar background_job (su print "starting" sale
ahi). background_job llega a su await de 1s y cede el turno de vuelta.
Como 0.3s es menor a 1s, main termina su espera primero e imprime "did
something else". Recien entonces main hace await task, lo cual SI lo
bloquea hasta que background_job termine su segundo restante.

EN RESUMEN: create_task() lanza una corrutina para que avance por su
cuenta sin esperarla de inmediato, pero recuerda hacerle await en
algun momento para recoger su resultado (o su error, si algo fallo).
"""


"""
================================================================================
CUANDO USAR CADA MECANISMO DE CONCURRENCIA (Y POR QUE)
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Python ofrece tres formas distintas de "hacer varias cosas a la vez", y
elegir la correcta depende del tipo de trabajo, no de preferencia:

THREADING (13_archivos_y_stdlib.py) — hilos reales del sistema
operativo. Bueno para tareas de I/O (leer archivos, esperar red) que
usan librerias que NO son "async-aware". El CPython estandar tiene un
GIL (Global Interpreter Lock) que impide que dos hilos ejecuten
bytecode Python al MISMO tiempo — por eso threading no acelera trabajo
que sea puro calculo (CPU-bound), solo ayuda cuando los hilos pasan la
mayoria del tiempo ESPERANDO algo externo (I/O-bound).

ASYNCIO — concurrencia cooperativa en UN SOLO hilo. Ideal para miles de
tareas de I/O ligeras al mismo tiempo (por ejemplo, un servidor web
atendiendo muchas conexiones), porque cambiar entre corrutinas es mucho
mas barato que crear miles de hilos. La condicion es que TODA la cadena
de llamadas use await de principio a fin con librerias compatibles con
asyncio.

MULTIPROCESSING — procesos separados del sistema operativo, cada uno
con su propio interprete de Python y su propio GIL. Es la unica de las
tres opciones que de verdad paraleliza trabajo CPU-bound (calculos
pesados, procesamiento de datos), porque el GIL de un proceso no afecta
a los demas. El costo es mayor overhead para crear cada proceso y
compartir datos entre ellos, comparado con un hilo o una corrutina.

    tipo de trabajo                                    mecanismo recomendado
    esperar red/disco (poco)                           threading
    esperar red/disco (muchisimo, miles de tareas)      asyncio
    calculo puro (CPU-bound)                            multiprocessing

EN JS esta decision no existe de la misma forma: al ser un solo hilo
por diseño, async/await YA cubre el caso de I/O por defecto, y para
CPU-bound real se recurre a Worker Threads, un mecanismo aparte que no
esta integrado en async/await.

EN RESUMEN: si tu codigo pasa el tiempo ESPERANDO algo externo, usa
asyncio (muchas tareas ligeras) o threading (pocas tareas, librerias no
compatibles con asyncio); si tu codigo pasa el tiempo CALCULANDO, usa
multiprocessing.
"""

import multiprocessing

def compute_square_sum(name, n):
    total = sum(i * i for i in range(n))
    print(f"{name}: {total}")

if __name__ == "__main__":
    # get_context("fork") en vez de multiprocessing.Process directo: el
    # metodo por defecto en macOS/Windows es "spawn", que REIMPORTA este
    # archivo en cada proceso hijo y volveria a correr todos los ejemplos
    # de arriba otra vez. "fork" solo clona el proceso actual, sin reimportar.
    ctx = multiprocessing.get_context("fork")
    process_a = ctx.Process(target=compute_square_sum, args=("proc A", 30_000_000))
    process_b = ctx.Process(target=compute_square_sum, args=("proc B", 2_000_000))
    process_a.start()
    process_b.start()
    process_a.join()
    process_b.join()
    print("both processes finished")
    # proc B: 2666664666667000000   (menos trabajo, termina primero)
    # proc A: 8999999550000005000000
    # both processes finished
    # (los dos procesos calculan en paralelo REAL, en distintos nucleos del
    # CPU: a diferencia de threading, aqui el GIL de cada proceso es
    # independiente y no le estorba al otro)
