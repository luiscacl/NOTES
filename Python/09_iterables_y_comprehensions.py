"""
================================================================================
ITERABLES E ITERADORES
================================================================================
[IGUAL QUE JS]

Un ITERABLE es cualquier objeto que se puede recorrer con for: listas,
tuplas, strings, sets, diccionarios, range(). Es el concepto central que
hace funcionar for, el unpacking de variables, y muchas funciones
built-in que reciben "cualquier coleccion".

EN RESUMEN: es el mismo concepto que los objetos que soportan for...of
en JS, solo que en Python abarca ademas el unpacking y varias funciones
built-in.
"""

for x in [1, 2, 3]:
    print(x)
# 1
# 2
# 3

for x in "abc":
    print(x)
# a
# b
# c

for x in (1, 2, 3):
    print(x)
# 1
# 2
# 3

for x in {1, 2, 3}:
    print(x)
# 1
# 2
# 3


"""
[SIMILAR A JS, CON DIFERENCIAS]
ORDEN DE EJECUCION exacto de un for por debajo

EN JS:
for...of tambien pide un iterador con Symbol.iterator y llama a next()
repetidamente, pero next() devuelve un objeto {value, done}; el loop
revisa la propiedad done para saber cuando parar, sin usar excepciones.

EN PYTHON:
Python le pide al iterable un ITERADOR con iter(), y despues le va
pidiendo un valor a la vez a ese iterador con next(), hasta que se
agota y lanza StopIteration (esa excepcion especial es la señal interna
de "ya no hay mas elementos"; for la atrapa solo y termina el loop,
nunca la ves como error). No necesitas escribir esto a mano casi nunca,
pero explica por que un mismo iterable se puede "recorrer desde cero"
varias veces con distintos for: cada for pide un iterador NUEVO, que
empieza otra vez desde el principio.

EN RESUMEN: ambos lenguajes piden un iterador nuevo y lo van avanzando
paso a paso; la diferencia es como se señala "ya no hay mas" (excepcion
StopIteration en Python, bandera done en JS).
"""

numbers = [10, 20, 30]
iterator = iter(numbers)

print(next(iterator))  # 10
print(next(iterator))  # 20
print(next(iterator))  # 30
print(next(iterator))  # StopIteration


"""
[SIMILAR A JS, CON DIFERENCIAS]
CREAR UN ITERABLE PROPIO: __iter__ Y __next__

EN JS:
Una clase se vuelve iterable implementando Symbol.iterator, que debe
devolver un objeto con next() que entrega {value, done} en cada
llamada.

EN PYTHON:
Una clase se vuelve iterable (utilizable en un for) implementando el
metodo magico __iter__, que debe devolver un ITERADOR: un objeto con
__next__, que entrega un valor cada vez que se le llama y lanza
StopIteration cuando ya no hay mas. La forma mas simple es que la
propia clase implemente ambos metodos.

EN RESUMEN: la idea de fondo es identica en los dos lenguajes; solo
cambian los nombres de los metodos y como se señala el final (done vs
StopIteration).
"""

class CountUpTo:
    def __init__(self, limit):
        self.limit = limit
        self.current = 0

    def __iter__(self):
        return self

    def __next__(self):
        if self.current >= self.limit:
            raise StopIteration
        self.current += 1
        return self.current

for number in CountUpTo(4):
    print(number)
# 1
# 2
# 3
# 4


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
zip() combina varios iterables en pares/tuplas, elemento a elemento en
el mismo orden, y se DETIENE en cuanto el mas corto de los dos se acaba
(no truena, ni rellena con None). Es muy usado para recorrer dos listas
relacionadas al mismo tiempo.

EN RESUMEN: zip() no tiene equivalente directo en JS; ahi recorrer dos
arrays relacionados a la vez requiere un for con indice manual.
"""

names = ["Alice", "Bob", "Carol"]
ages = [25, 19, 32]

for name, age in zip(names, ages):
    print(name, age)
# Alice 25
# Bob 19
# Carol 32

print(list(zip(names, ages)))  # [('Alice', 25), ('Bob', 19), ('Carol', 32)]


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
array.entries() devuelve un iterador de pares [indice, valor], parecido
en la idea a enumerate(), pero hay que llamarlo explicitamente sobre el
array y no se usa tan seguido.

EN PYTHON:
enumerate() (ya visto en 06_loops.py) tambien es, tecnicamente, un
iterable que produce pares (indice, valor) uno a la vez, siguiendo el
mismo protocolo de iter()/next() que cualquier otro iterable.

EN RESUMEN: la idea de emparejar indice y valor existe en ambos, pero
en Python enumerate() es mucho mas comun y se usa directamente dentro
del for.
"""

for index, name in enumerate(names):
    print(index, name)
# 0 Alice
# 1 Bob
# 2 Carol

print(list(enumerate(names)))  # [(0, 'Alice'), (1, 'Bob'), (2, 'Carol')]


"""
================================================================================
any() Y all()
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Lo mas parecido son los metodos array.some() y array.every(), que
tambien hacen short-circuit, pero son metodos del array, no funciones
globales que acepten cualquier iterable.

EN PYTHON:
any(iterable) devuelve True si AL MENOS UN elemento es truthy (y deja
de revisar en cuanto encuentra el primero — short-circuit). all(iterable)
devuelve True solo si TODOS los elementos son truthy (y se detiene en
cuanto encuentra el primer falsy).

EN RESUMEN: any()/all() hacen lo mismo que some()/every(), pero como
funciones globales que reciben cualquier iterable, no solo arrays.
"""

numbers = [2, 4, 6, 7, 8]

print(any(n % 2 != 0 for n in numbers))  # True
print(all(n % 2 == 0 for n in numbers))  # False
print(any([]))  # False
print(all([]))  # True


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Math.min()/Math.max() solo aceptan numeros sueltos; para sacar el
minimo o maximo de un array por un criterio hay que combinar .reduce()
o .sort() a mano.

EN PYTHON:
min() y max() (vistos en 03_operadores.py con argumentos sueltos)
tambien aceptan un iterable completo como unico argumento, y un
parametro key para decidir por que criterio comparar cada elemento —
igual que sorted().

EN RESUMEN: min()/max() de Python cubren en una sola llamada lo que en
JS requiere reduce() o sort() manual.
"""

people = [("Alice", 25), ("Bob", 19), ("Carol", 32)]

print(min(people, key=lambda p: p[1]))  # ('Bob', 19)
print(max(people, key=lambda p: p[1]))  # ('Carol', 32)


"""
================================================================================
LIST COMPREHENSIONS
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Una comprehension construye una coleccion NUEVA en una sola expresion,
recorriendo un iterable y opcionalmente filtrando y transformando cada
elemento en el camino.

EN RESUMEN: JS no tiene una sintaxis equivalente dentro del lenguaje;
lo mas cercano es encadenar .map() y .filter().
"""

numbers = [1, 2, 3, 4, 5]

doubles = [n * 2 for n in numbers]
print(doubles)  # [2, 4, 6, 8, 10]


"""
    JS:  numbers.map(n => n * 2)
    Py:  [n * 2 for n in numbers]
"""

evens = [n for n in numbers if n % 2 == 0]
print(evens)  # [2, 4]


"""
    JS:  numbers.filter(n => n % 2 === 0)
    Py:  [n for n in numbers if n % 2 == 0]
"""

doubles_of_evens = [n * 2 for n in numbers if n % 2 == 0]
print(doubles_of_evens)  # [4, 8]


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
ORDEN DE EJECUCION dentro de la comprehension: se lee de IZQUIERDA A
DERECHA como "para cada n en numeros, si se cumple la condicion,
calcula n * 2 y agregalo al resultado" — primero se recorre, luego se
filtra (si hay if), y al final se transforma cada elemento que
sobrevivio al filtro.

    JS:  numbers.filter(n => n % 2 === 0).map(n => n * 2)
    Py:  [n * 2 for n in numbers if n % 2 == 0]

EN RESUMEN: la comprehension equivale a encadenar filter().map(), pero
en un solo paso y en el orden filtro-primero, transformacion-despues.
"""

labels = ["even" if n % 2 == 0 else "odd" for n in numbers]
print(labels)  # ['odd', 'even', 'odd', 'even', 'odd']


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Tambien se pueden anidar dos for dentro de la misma comprehension, para
recorrer combinaciones o aplanar una matriz (ver 07_colecciones.py). El
orden de los for adentro de la comprehension es el mismo que tendrian
escritos como loops anidados normales: el primer for es el externo.

EN RESUMEN: dos for dentro de una comprehension equivalen a dos for
anidados normales, en el mismo orden.
"""

matrix = [[1, 2], [3, 4], [5, 6]]
flattened = [value for row in matrix for value in row]
print(flattened)  # [1, 2, 3, 4, 5, 6]


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
DICT Y SET COMPREHENSIONS

La misma idea aplica a diccionarios (con llaves y clave: valor) y a
sets (con llaves, sin clave: valor, para que no se confunda con dict).

EN RESUMEN: la misma sintaxis de comprehension sirve para listas,
diccionarios y sets; solo cambian los simbolos y si hay clave: valor.
"""

squares = {n: n ** 2 for n in range(5)}
print(squares)  # {0: 0, 1: 1, 2: 4, 3: 9, 4: 16}

evens_set = {n for n in numbers if n % 2 == 0}
print(evens_set)  # {2, 4}


"""
================================================================================
GENERADORES
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Existe un equivalente con function* y yield, pero se usa muchisimo
menos que en Python.

EN PYTHON:
Una funcion generadora se define como una funcion normal con def, pero
en vez de return usa yield.

ORDEN DE EJECUCION, el detalle mas importante de un generador: la
primera vez que se le pide un valor, la funcion corre desde el inicio
hasta el PRIMER yield, entrega ese valor, y ahi se PAUSA guardando todo
su estado interno (variables locales incluidas). La siguiente vez que se
le pide otro valor, continua exactamente donde se quedo — no vuelve a
empezar — hasta el siguiente yield o hasta que la funcion termina.

EN RESUMEN: generadores y function*/yield comparten la idea de pausar y
reanudar codigo, pero en Python son una herramienta central y muy usada.
"""

def count_up_to(n):
    number = 1
    while number <= n:
        yield number
        number += 1

for value in count_up_to(3):
    print(value)
# 1
# 2
# 3


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Los generadores de function*/yield tambien son lazy: producen valores
uno a uno bajo demanda en vez de calcular todo de una vez.

EN PYTHON:
La diferencia clave frente a una funcion normal que arma y devuelve una
lista completa: un generador no calcula ni guarda TODOS los valores en
memoria de una sola vez, los va produciendo uno a uno segun se van
pidiendo ("lazy evaluation" — evaluacion perezosa). Para una secuencia
enorme (o incluso infinita), eso ahorra memoria de forma importante,
porque nunca existe "la lista completa" en ningun momento.

EN RESUMEN: en ambos lenguajes el generador ahorra memoria al no
construir la coleccion completa de antemano.
"""

def squares_up_to(n):
    for i in range(n):
        yield i ** 2

generator = squares_up_to(5)
print(type(generator))  # <class 'generator'>
print(next(generator))  # 0
print(next(generator))  # 1
print(list(generator))  # [4, 9, 16]


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Una comprehension escrita entre parentesis en vez de corchetes es una
GENERATOR EXPRESSION: la version compacta de un generador, igual de
"lazy" que una funcion generadora con yield (vista arriba), pero sin
construir nunca la lista completa en memoria — a diferencia de una list
comprehension, que SI construye la lista completa de un jalon.

EN RESUMEN: no hay sintaxis equivalente en JS; es una comprehension con
parentesis en vez de corchetes.
"""

sum_of_squares = sum(n ** 2 for n in range(1000000))
print(sum_of_squares)  # 333332833333500000


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
LIST COMPREHENSION VS GENERATOR EXPRESSION: CUAL USAR

Ambas se ven casi identicas ([ ] contra ( )), asi que la pregunta
profesional es cuando usar cada una, y la respuesta depende de COMO se
va a consumir el resultado despues:

Usa una LIST COMPREHENSION cuando necesitas recorrer el resultado MAS
DE UNA VEZ, necesitas su longitud con len(), necesitas indexarlo
(resultado[0]), o el resultado no es tan grande como para preocuparse
por la memoria.

Usa una GENERATOR EXPRESSION cuando solo vas a recorrer el resultado
UNA SOLA VEZ de principio a fin (como dentro de sum(), any(), all(), o
un for) y la coleccion de entrada es grande: evita construir la lista
completa en memoria de una sola vez, generando cada valor solo cuando
se pide. La contra es que un generador se AGOTA: una vez recorrido
completo, recorrerlo de nuevo no da nada (a diferencia de una lista,
que se puede recorrer las veces que se quiera).

EN RESUMEN: lista si necesitas reutilizar o indexar el resultado;
generador si solo lo vas a recorrer una vez y quieres ahorrar memoria.
"""

consumed_generator = (n for n in range(3))
print(list(consumed_generator))  # [0, 1, 2]
print(list(consumed_generator))  # []  (ya se agoto, no vuelve a empezar)
