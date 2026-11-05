"""
================================================================================
NUMEROS ALEATORIOS
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Math.random() es un metodo global disponible sin necesidad de importar
nada: siempre devuelve un float entre 0 y 1.

EN PYTHON:
No existe un Math.random() global; hay que importar el modulo random,
que ademas de random.random() (equivalente a Math.random()) trae mas
funciones para generar numeros aleatorios de distintas formas.

EN RESUMEN: en Python los numeros aleatorios viven en su propio modulo
(random), no en un objeto global como Math en JS.
"""

import random

print(random.random())  # e.g. 0.8903788642626553 (random, changes every run)
print(random.randint(1, 6))  # e.g. 5 (random, changes every run)
print(random.uniform(1, 10))  # e.g. 3.6581699128567844 (random, changes every run)


"""
    funcion                          que hace
    random.random()                  float entre 0.0 y 1.0 (sin incluir 1.0)
    random.randint(a, b)             int entre a y b, AMBOS incluidos
    random.uniform(a, b)             float entre a y b
    random.choice(secuencia)         elige un elemento al azar
    random.sample(secuencia, k)      elige k elementos distintos al azar
    random.shuffle(lista)            revuelve la lista in-place
"""

options = ["rock", "paper", "scissors"]

print(random.choice(options))  # e.g. rock (random, changes every run)
print(random.sample(range(1, 50), 6))  # e.g. [5, 45, 42, 22, 36, 38] (random, changes every run)

cards = [1, 2, 3, 4, 5]
random.shuffle(cards)
print(cards)  # e.g. [2, 3, 5, 4, 1] (random, changes every run)


"""
================================================================================
DETECCION, ESCRITURA Y LECTURA DE ARCHIVOS
================================================================================
[IGUAL QUE JS]

os.path.exists() revisa si una ruta existe en disco antes de intentar
usarla, evitando un error si el archivo todavia no fue creado.

EN RESUMEN: revisa primero con os.path.exists() si quieres evitar un
error por archivo faltante.
"""

import os

print(os.path.exists("data.txt"))  # False (assuming this is the first run and data.txt doesn't exist yet in this folder)
print(os.path.exists("folder_that_does_not_exist"))  # False (this path never exists)


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

open() abre un archivo y devuelve un objeto de archivo, con un
parametro de MODO que decide que se puede hacer con el:

    "w"    escribir, BORRANDO todo el contenido anterior si el archivo
           ya existia (o creandolo si no existia)
    "a"    append: escribir agregando al final, sin borrar lo anterior
    "r"    leer (falla con FileNotFoundError si el archivo no existe)

La forma correcta de abrir un archivo es con with, un CONTEXT MANAGER:
garantiza que el archivo se CIERRE solo al salir del bloque, incluso si
ocurre un error adentro. JS no tiene una construccion equivalente
integrada en el lenguaje para "recurso que se libera solo"; normalmente
se maneja a mano con try/finally.

NOTA SOBRE as: esta es la tercera vez que aparece esta palabra (antes:
import ... as en 10_modulos_y_scope.py, except ... as en
11_excepciones.py). Aqui, con with ... as nombre, guarda en esa
variable lo que devuelve el metodo __enter__ del context manager (para
un archivo, el propio objeto de archivo abierto). Tres construcciones
distintas, la misma idea de fondo: as siempre asigna un nombre a lo que
sea que produjo la instruccion de la izquierda.

EN RESUMEN: open() necesita un modo ("w"/"a"/"r"), y with se encarga de
cerrar el archivo solo, incluso si algo falla adentro del bloque.
"""

with open("data.txt", "w") as file:
    file.write("first line\n")
    file.write("second line\n")

with open("data.txt", "a") as file:
    file.write("third line added afterwards\n")


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

Un solo with puede abrir VARIOS context managers a la vez, separados
por coma — util para copiar de un archivo a otro sin anidar dos with
por separado. Ambos se cierran solos al salir del bloque, en el orden
inverso al que se abrieron.

EN RESUMEN: with archivo1 as a, archivo2 as b abre y cierra ambos
archivos sin anidar dos bloques with.
"""

with open("data.txt", "r") as source, open("data_copy.txt", "w") as destination:
    destination.write(source.read())

with open("data_copy.txt", "r") as file:
    print(file.read())  # first line\nsecond line\nthird line added afterwards\n (contenido identico al original)


"""
================================================================================
CREAR TU PROPIO CONTEXT MANAGER
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

with no es exclusivo de archivos: cualquier objeto que implemente los
metodos magicos __enter__ y __exit__ puede usarse con with. __enter__
se ejecuta al ENTRAR al bloque (su valor de retorno es lo que recibe la
variable del as); __exit__ se ejecuta SIEMPRE al salir, sea que el
bloque haya terminado normal o por un error — el mismo lugar donde
pondrias logica de limpieza en un finally.

EN RESUMEN: __enter__ y __exit__ convierten cualquier clase propia en
un context manager usable con with.
"""

import time

class Timer:
    def __enter__(self):
        self.start = time.time()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        elapsed = time.time() - self.start
        print(f"elapsed: {elapsed:.4f}s")  # e.g. elapsed: 0.0083s (varies each run, depends on the machine)

with Timer():
    total = sum(range(1000000))

print(total)  # 499999500000


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

Una forma mas corta de lograr lo mismo, sin escribir una clase completa,
es con el decorador @contextlib.contextmanager sobre una funcion
generadora (ver 09_iterables_y_comprehensions.py): todo antes del yield
es el __enter__, todo despues del yield es el __exit__.

EN RESUMEN: @contextmanager evita escribir una clase con __enter__ y
__exit__ cuando basta con una funcion generadora.
"""

from contextlib import contextmanager

@contextmanager
def timer():
    start = time.time()
    yield
    elapsed = time.time() - start
    print(f"elapsed: {elapsed:.4f}s")  # e.g. elapsed: 0.0069s (varies each run, depends on the machine)

with timer():
    total = sum(range(1000000))


with open("data.txt", "r") as file:
    content = file.read()

print(content)  # first line\nsecond line\nthird line added afterwards\n\n (extra blank line: print() adds its own newline after content, which already ends in "\n")


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Leer un archivo linea por linea normalmente requiere el modulo
readline combinado con un stream (fs.createReadStream), o bien leer
todo el contenido con fs.readFileSync() y luego partirlo a mano con
.split("\n").

EN PYTHON:
Se puede iterar directamente sobre el objeto de archivo con for (cada
vuelta entrega una linea), sin streams ni modulos aparte — mas
eficiente para archivos grandes que leer todo de un jalon con .read().
.readlines() hace algo parecido pero de un jalon, devolviendo una lista
con todas las lineas ya separadas.

EN RESUMEN: iterar con for sobre un archivo abierto ya te da linea por
linea; no hace falta nada adicional como en JS.
"""

with open("data.txt", "r") as file:
    for line in file:
        print(line.strip())  # prints, one per iteration: first line / second line / third line added afterwards

with open("data.txt", "r") as file:
    lines = file.readlines()

print(lines)  # ['first line\n', 'second line\n', 'third line added afterwards\n']


"""
================================================================================
EL MODULO json
================================================================================
[IGUAL QUE JS]

json.dumps(objeto) convierte un dict o list de Python en un string con
formato JSON — el equivalente casi 1:1 de JSON.stringify() en JS. Nota
que True se convierte en true (minusculas), como en JSON de verdad.

EN RESUMEN: json.dumps() es JSON.stringify(), con True/False/None
convertidos a true/false/null.
"""

import json

person = {"name": "Ana", "age": 30, "active": True}

text = json.dumps(person)
print(text)  # {"name": "Ana", "age": 30, "active": true}
print(type(text))  # <class 'str'>


"""
[IGUAL QUE JS]

json.loads(texto) hace lo inverso: un string con formato JSON se
convierte en un dict o list de Python — el equivalente de JSON.parse()
en JS.

EN RESUMEN: json.loads() es JSON.parse().
"""

parsed = json.loads(text)
print(parsed)  # {'name': 'Ana', 'age': 30, 'active': True}
print(type(parsed))  # <class 'dict'>


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
No existe un helper que escriba o lea JSON directo a archivo: hay que
combinar fs.writeFileSync(ruta, JSON.stringify(objeto)) para escribir,
y JSON.parse(fs.readFileSync(ruta, "utf8")) para leer.

EN PYTHON:
json.dump(objeto, archivo) y json.load(archivo) son las versiones que
trabajan directo con un archivo (en vez de con un string): escriben o
leen JSON usando un objeto de archivo ya abierto con open(), sin que
tengas que pasar por dumps()/loads() a mano.

EN RESUMEN: dump()/load() hacen en un solo paso lo que en JS requiere
combinar fs con JSON.stringify()/JSON.parse() a mano.
"""

with open("person.json", "w") as file:
    json.dump(person, file)

with open("person.json", "r") as file:
    loaded = json.load(file)

print(loaded)  # {'name': 'Ana', 'age': 30, 'active': True}


"""
================================================================================
pathlib.Path
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El modulo path trabaja con las rutas como simples strings: funciones
sueltas como path.join() o path.resolve() reciben y devuelven strings;
revisar si algo existe en disco requiere ademas el modulo fs
(fs.existsSync()).

EN PYTHON:
pathlib.Path representa una ruta como un OBJETO con metodos propios
(path.exists(), y mas adelante path.read_text()) — la forma MODERNA y
mas idiomatica de trabajar con rutas, alternativa a las funciones de
os.path usadas arriba.

EN RESUMEN: Path envuelve la ruta en un objeto con metodos, en vez de
pasar strings sueltos entre funciones como en JS.
"""

from pathlib import Path

path = Path("data.txt")
print(path)  # data.txt
print(path.exists())  # True


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
fs.readFileSync(ruta, "utf8") logra algo parecido: lee el archivo
completo de un jalon, pero como funcion suelta que recibe la ruta como
string, no como metodo de un objeto ruta.

EN PYTHON:
path.read_text() es un atajo que abre, lee y cierra el archivo en una
sola llamada, sin necesitar un bloque with explicito.

EN RESUMEN: read_text() es el atajo de Path para lo que en JS seria
fs.readFileSync(ruta, "utf8").
"""

content_from_path = path.read_text()
print(content_from_path)  # first line\nsecond line\nthird line added afterwards\n\n (extra blank line: print() adds its own newline after content, which already ends in "\n")


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

Los objetos Path sobrecargan el operador / para UNIR partes de una ruta
— mas agradable de leer que os.path.join("folder", "file.txt"), y algo
que JS no tiene: el modulo path de JS solo ofrece path.join() como
funcion, no sobrecarga de operadores.

EN RESUMEN: Path("folder") / "file.txt" une rutas con /, en vez de una
funcion como path.join().
"""

joined_path = Path("folder") / "file.txt"
print(joined_path)  # folder/file.txt


"""
================================================================================
ARGUMENTOS DE LINEA DE COMANDOS Y VARIABLES DE ENTORNO
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
process.argv en Node.js es una lista con los argumentos de la
terminal, donde los primeros DOS elementos son fijos: el ejecutable de
node y la ruta del archivo que se corrio.

EN PYTHON:
sys.argv es una lista con los argumentos que se pasaron al ejecutar el
archivo desde la terminal (python archivo.py arg1 arg2). El primer
elemento SIEMPRE es el nombre del propio archivo — un solo elemento
fijo, no dos como en Node.

EN RESUMEN: sys.argv es process.argv, pero con un solo elemento fijo al
inicio (el archivo) en vez de dos (node + archivo).
"""

import sys

print(sys.argv)  # ['13_archivos_y_stdlib.py']  (sin argumentos extra en este ejemplo; cada argumento adicional pasado en la terminal apareceria como un string mas en esta lista)


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
process.env en Node.js es un objeto plano con las variables de entorno
del sistema operativo como propiedades string.

EN PYTHON:
os.environ es un dict-like con las mismas variables de entorno del
sistema operativo, accesible y modificable con la misma sintaxis que un
dict normal.

EN RESUMEN: os.environ es process.env, con interfaz de dict en vez de
objeto plano.
"""

import os

os.environ["MY_CUSTOM_VARIABLE"] = "hello"
print(os.environ["MY_CUSTOM_VARIABLE"])  # hello
print(os.environ.get("VARIABLE_THAT_DOES_NOT_EXIST", "default value"))  # default value


"""
================================================================================
FECHAS Y HORAS
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El objeto Date es un solo tipo que junta fecha y hora en un mismo
objeto, con metodos para extraer cada parte (getFullYear(), getHours(),
etc.).

EN PYTHON:
El modulo datetime divide el concepto en varios tipos segun lo que
representan: date (solo fecha), time (solo hora), datetime (fecha +
hora), y timedelta (una DURACION, la diferencia entre dos
fechas/horas).

EN RESUMEN: donde JS usa un solo Date para todo, Python separa fecha,
hora y duracion en tipos distintos.
"""

from datetime import datetime, timedelta

now = datetime.now()
print(now)  # e.g. 2026-08-22 19:06:01.043720 (depends on when you run it)
print(now.year, now.month, now.day)  # e.g. 2026 8 22 (depends on when you run it)
print(now.hour, now.minute)  # e.g. 19 6 (depends on when you run it)


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Dar formato a una fecha normalmente implica construir el string a mano
combinando los metodos de Date (getFullYear(), getMonth(), etc.), o
usar Intl.DateTimeFormat para casos mas completos.

EN PYTHON:
strftime() da formato de fecha a texto con un mini-lenguaje de codigos
en vez de armar el string a mano:

    %Y   año con 4 digitos      %m   mes (01-12)      %d   dia (01-31)
    %H   hora 24h                 %M   minutos           %S   segundos

EN RESUMEN: strftime() arma el string de fecha con codigos tipo
%Y-%m-%d, en vez de concatenar los metodos de Date a mano como en JS.
"""

print(now.strftime("%Y-%m-%d"))  # e.g. 2026-08-22 (depends on when you run it)
print(now.strftime("%H:%M:%S"))  # e.g. 19:06:01 (depends on when you run it)


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

timedelta representa una DURACION, y se puede sumar o restar
directamente a un datetime con + y - normales (Python SI sobrecarga esos
operadores para estos tipos, ver __add__ en
12_programacion_orientada_a_objetos.py). Restar dos datetime entre si
tambien da un timedelta.

EN RESUMEN: sumas o restas timedelta a un datetime con + y - normales,
y restar dos datetime te da directamente la duracion entre ellos.
"""

in_one_week = now + timedelta(days=7)
print(in_one_week)  # e.g. 2026-08-29 19:06:01.043720 (depends on when you run it: now's value + 7 days)

difference = in_one_week - now
print(difference)  # 7 days, 0:00:00 (deterministic: always exactly 7 days, regardless of when you run it)
print(difference.days)  # 7


"""
================================================================================
MULTITHREADING (BASICO)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
No hay hilos reales: JS corre en un solo hilo con un event loop, y la
concurrencia que dan las Promises/async-await es cooperativa — nunca
dos lineas de tu codigo corren en el mismo instante exacto.

EN PYTHON:
threading permite correr funciones de forma CONCURRENTE, sin esperar a
que una termine para arrancar la otra, usando hilos reales del sistema
operativo (aunque el CPython "clasico" limita cuanto paralelismo real de
CPU se aprovecha — el detalle de por que queda fuera del alcance de este
archivo, que es sobre sintaxis, no sobre el interprete).

EN RESUMEN: threading usa hilos reales del sistema operativo; el event
loop de JS es un solo hilo que reparte turnos, nunca hilos paralelos de
verdad.
"""

import threading
import time

def task(name, seconds):
    print(f"{name} empieza")  # task A empieza / task B empieza (order confirmed: both start almost together)
    time.sleep(seconds)
    print(f"{name} termina")  # task B termina / task A termina (order confirmed: B finishes first, it sleeps less)

thread1 = threading.Thread(target=task, args=("task A", 2))
thread2 = threading.Thread(target=task, args=("task B", 1))

thread1.start()
thread2.start()

thread1.join()
thread2.join()

print("ambas tareas terminaron")  # ambas tareas terminaron


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Llamar una funcion async sin await (o disparar una Promise sin
encadenar .then) tampoco bloquea: el codigo que sigue se ejecuta de
inmediato, y recien esperas el resultado cuando haces await o .then().

EN PYTHON:
ORDEN DE EJECUCION: .start() lanza el hilo y la ejecucion del programa
principal SIGUE de inmediato a la linea siguiente, sin esperar a que la
tarea termine (por eso thread1.start() y thread2.start() se disparan casi
al mismo tiempo, una detras de otra sin pausa). .join() si BLOQUEA: hace
que el programa principal espere a que ESE hilo en particular termine
antes de continuar — por eso el print final solo se ve hasta que ambos
hilos ya acabaron.

EN RESUMEN: start() es como disparar una promesa sin esperarla; join()
es como hacerle await mas adelante, cuando de verdad necesitas su
resultado.
"""

def slow_task():
    time.sleep(2)
    print("slow_task termino")  # se ve al final, despues del join()

thread3 = threading.Thread(target=slow_task)
thread3.start()
print("esta linea se imprime de inmediato, sin esperar a slow_task")  # se ve primero: start() no bloquea
thread3.join()
print("recien aqui, despues de join(), sabemos que slow_task ya termino")  # se ve al final: join() si bloquea


"""
================================================================================
PETICIONES A UNA API (requests)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
fetch() hace peticiones HTTP de forma ASINCRONA: devuelve una Promise
que hay que resolver con .then() o await antes de tener la respuesta.

EN PYTHON:
requests es la libreria mas comun para hacer peticiones HTTP (no viene
incluida por defecto, se instala con pip install requests) y es
SINCRONA por defecto: la linea que hace la peticion se detiene ahi
mismo hasta que llega la respuesta.

EN RESUMEN: requests.get() bloquea hasta tener respuesta; fetch() en JS
no bloquea, devuelve una Promise para resolver despues.
"""

import requests

response = requests.get("https://api.github.com")

print(response.status_code)  # 200 (live network call to a real API; can vary if GitHub changes something or you're offline)
print(response.json())  # e.g. {'current_user_url': 'https://api.github.com/user', 'authorizations_url': ...} — a dict with ~30 URL template fields (live data, shape can change over time)


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
response.json() TAMBIEN es asincrono: devuelve una Promise, no el dato
ya listo, asi que necesita su PROPIO await (o .then()) ademas del await
que ya usaste en el fetch():

    const response = await fetch(url);
    const data = await response.json();

Olvidar ese segundo await es un error comun: data terminaria siendo la
Promise en si, no el dato parseado.

EN PYTHON:
response.json() es SINCRONO: convierte el cuerpo de la respuesta (texto
en formato JSON) en estructuras nativas de Python en una sola llamada,
sin await ni Promise de por medio — un dict si la raiz del JSON era un
objeto, una list si era un arreglo (el mismo mapeo de tipos que hace
JSON.parse() en JS, solo que sin la capa asincrona encima):

    response = requests.get(url)
    data = response.json()

EN RESUMEN: el mapeo de tipos que hace .json() es el mismo en ambos
lenguajes, pero en JS response.json() TAMBIEN devuelve una Promise (hay
que await-earla aparte del await del fetch), mientras que en Python es
una llamada sincrona normal, sin await de ningun tipo.
"""
