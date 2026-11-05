"""
================================================================================
RECURSION
================================================================================
[IGUAL QUE JS]

Una funcion recursiva es una funcion que se llama A SI MISMA, directa o
indirectamente. Toda funcion recursiva necesita dos partes: un CASO BASE
(la condicion mas simple, donde la funcion NO se vuelve a llamar a si
misma y simplemente devuelve un valor) y un CASO RECURSIVO (donde la
funcion se llama a si misma con un argumento "mas cerca" del caso base).

En Python esta tecnica se usa muchisimo como alternativa natural a los
loops para ciertos problemas (recorrer estructuras anidadas, dividir un
problema en subproblemas mas chicos).

EN RESUMEN: una funcion puede llamarse a si misma igual que en JS; lo
que cambia es el uso: en Python se recurre a la recursion con mucha mas
frecuencia.
"""

def factorial(n):
    if n == 0:
        return 1
    return n * factorial(n - 1)

print(factorial(5))  # 120


"""
[IGUAL QUE JS]
ORDEN DE EJECUCION de factorial(5): cada llamada se queda "esperando" a
que su llamada interna termine antes de poder calcular su propio
resultado, apilandose una sobre otra (esto es el "call stack" o pila de
llamadas):

    factorial(5) llama a factorial(4), y espera
      factorial(4) llama a factorial(3), y espera
        factorial(3) llama a factorial(2), y espera
          factorial(2) llama a factorial(1), y espera
            factorial(1) llama a factorial(0), y espera
              factorial(0) NO se vuelve a llamar: devuelve 1 (caso base)
            factorial(1) recibe 1, calcula 1 * 1, devuelve 1
          factorial(2) recibe 1, calcula 2 * 1, devuelve 2
        factorial(3) recibe 2, calcula 3 * 2, devuelve 6
      factorial(4) recibe 6, calcula 4 * 6, devuelve 24
    factorial(5) recibe 24, calcula 5 * 24, devuelve 120

Si a una funcion recursiva le falta el caso base (o nunca se alcanza),
se llama a si misma para siempre hasta que Python detiene el programa
con RecursionError — el equivalente conceptual a un loop infinito, pero
con la pila de llamadas.

EN RESUMEN: cada llamada recursiva se apila sobre la anterior y espera
su resultado; sin caso base (o si nunca se alcanza) la pila crece para
siempre y Python la corta con RecursionError.
"""

def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n - 1) + fibonacci(n - 2)

for i in range(8):
    print(fibonacci(i), end=" ")  # 0 1 1 2 3 5 8 13  (todo en una sola linea, separado por espacios)
print()  # imprime el salto de linea final despues de la secuencia de arriba


"""
================================================================================
COPIAR COLECCIONES: ALIAS vs COPIA REAL
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Un array o un objeto asignado con const b = a; tambien queda por
referencia: no se crea una copia, ambos nombres apuntan al mismo dato.

EN PYTHON:
list2 = list1 NO crea una lista nueva: ambos nombres quedan apuntando
al MISMO objeto en memoria (son un ALIAS uno del otro). Modificar list2
modifica exactamente el mismo objeto que list1 ve. Esto es una fuente
MUY comun de bugs en algoritmos, porque las listas y diccionarios se
pasan y se asignan todo el tiempo dentro de loops y funciones.

EN RESUMEN: listas y objetos son por referencia en ambos lenguajes;
asignar una variable a otra no copia el contenido, solo copia el
puntero al mismo dato.
"""

list1 = [1, 2, 3]
list2 = list1
list2.append(4)

print(list1)  # [1, 2, 3, 4]
print(list2)  # [1, 2, 3, 4]
print(list1 is list2)  # True


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Para copiar un array se usa el spread ([...arr]) o array.slice(); para
copiar un objeto se usa el spread ({...obj}) o Object.assign({}, obj).

EN PYTHON:
Para obtener una copia INDEPENDIENTE de una lista de valores simples
(donde modificar una no afecte a la otra) hay tres formas equivalentes:

EN RESUMEN: ambos lenguajes ofrecen varias formas de copiar una
coleccion sin que quede como alias del original; en Python las tres de
abajo son equivalentes entre si.
"""

list1 = [1, 2, 3]
copy_a = list1.copy()
copy_b = list1[:]
copy_c = list(list1)

copy_a.append(99)
print(list1)  # [1, 2, 3]
print(copy_a)  # [1, 2, 3, 99]


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
array.slice() o el spread tambien hacen una copia SUPERFICIAL: si el
array contiene otros arrays u objetos adentro, esos elementos internos
siguen siendo la misma referencia compartida entre el original y la
copia.

EN PYTHON:
CUIDADO: .copy() (y [:] y list()) hacen una copia SUPERFICIAL (shallow
copy) exactamente por la misma razon. Si la lista contiene OTRAS listas
adentro (una matriz, ver 07_colecciones.py), la copia superficial copia
solo el contenedor de afuera; las listas internas siguen siendo la
MISMA referencia compartida entre el original y la copia.

EN RESUMEN: una copia superficial nunca alcanza cuando hay colecciones
anidadas adentro; el contenedor externo se copia, pero el contenido
interno se sigue compartiendo.
"""

matrix = [[1, 2], [3, 4]]
shallow_copy = matrix.copy()
shallow_copy[0].append(100)

print(matrix)  # [[1, 2, 100], [3, 4]]  (matrix[0] tambien cambio: es la misma lista interna)
print(shallow_copy)  # [[1, 2, 100], [3, 4]]


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Para una copia profunda se usa structuredClone(obj), o antes se
simulaba con JSON.parse(JSON.stringify(obj)).

EN PYTHON:
Para copiar TODOS los niveles de anidamiento de forma independiente se
usa copy.deepcopy() del modulo copy.

EN RESUMEN: cuando hay que copiar estructuras anidadas por completo,
ambos lenguajes ofrecen una funcion dedicada para eso; en Python es
copy.deepcopy().
"""

import copy

original_matrix = [[1, 2], [3, 4]]
deep_copy = copy.deepcopy(original_matrix)
deep_copy[0].append(100)

print(original_matrix)  # [[1, 2], [3, 4]]  (independiente de deep_copy)
print(deep_copy)  # [[1, 2, 100], [3, 4]]


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
La misma distincion aplica a objetos y a Map/Set: una copia superficial
no duplica las estructuras anidadas que haya adentro.

EN PYTHON:
Los diccionarios y sets tienen el mismo problema y la misma solucion:
dict.copy() / set.copy() para copia superficial, copy.deepcopy() para
copia completa cuando hay estructuras anidadas adentro.

EN RESUMEN: la regla de copia superficial vs. copia profunda no es
exclusiva de las listas: aplica igual a diccionarios y sets.
"""

d1 = {"a": 1, "b": 2}
d2 = d1.copy()
d2["c"] = 3
print(d1)  # {'a': 1, 'b': 2}
print(d2)  # {'a': 1, 'b': 2, 'c': 3}

s1 = {1, 2, 3}
s2 = s1.copy()
s2.add(4)
print(s1)  # {1, 2, 3}
print(s2)  # {1, 2, 3, 4}


"""
================================================================================
sorted() Y reversed(): FUNCIONES GLOBALES vs METODOS QUE MUTAN
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
array.sort() y array.reverse() tambien MUTAN el array original y no
existe una funcion global que devuelva una copia ordenada sin tocar el
original (hay que copiar el array primero, por ejemplo con
[...array].sort()).

EN PYTHON:
.sort() y .reverse() (vistos en 07_colecciones.py) MODIFICAN la lista
original y devuelven None. sorted() y reversed() son funciones globales
que NO tocan la coleccion original: devuelven una cosa NUEVA. Esta
diferencia importa mucho en un algoritmo cuando necesitas conservar el
orden original para compararlo despues, o cuando la coleccion no es una
lista (sorted() acepta cualquier iterable: tuplas, sets, strings).

EN RESUMEN: los metodos que mutan (.sort()/.reverse()) existen en
ambos lenguajes; lo que Python agrega es la alternativa que no muta:
las funciones globales sorted() y reversed().
"""

original = [3, 1, 4, 1, 5, 9, 2, 6]

new_sorted = sorted(original)
print(original)  # [3, 1, 4, 1, 5, 9, 2, 6]  (sorted() no lo toco)
print(new_sorted)  # [1, 1, 2, 3, 4, 5, 6, 9]

original.sort()
print(original)  # [1, 1, 2, 3, 4, 5, 6, 9]  (.sort() SI lo modifico en su lugar)


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
array.sort() acepta una funcion de comparacion (compareFn) para
personalizar el orden, pero no tiene un parametro reverse aparte.

EN PYTHON:
sorted() acepta los mismos parametros key y reverse que .sort().

EN RESUMEN: en Python, key reemplaza a la funcion de comparacion de JS,
y reverse evita tener que invertir esa logica a mano.
"""

words = ["banana", "kiwi", "apple", "grape"]

print(sorted(words, key=len))  # ['kiwi', 'apple', 'grape', 'banana']
print(sorted(words, reverse=True))  # ['kiwi', 'grape', 'banana', 'apple']

print(list(reversed(original)))  # [9, 6, 5, 4, 3, 2, 1, 1]


"""
[SIMILAR A JS, CON DIFERENCIAS]
ORDENAR POR VARIOS CRITERIOS Y ORDENAR OBJETOS

EN JS:
Para ordenar por varios criterios se encadena la resta dentro del
compareFn, por ejemplo (a, b) => a.edad - b.edad || a.nombre.localeCompare(b.nombre).

EN PYTHON:
key puede devolver una TUPLA: Python compara tuplas posicion por
posicion, asi que ordenar por una tupla (criterio1, criterio2) ordena
primero por criterio1, y usa criterio2 solo para desempatar. Si los
elementos son objetos propios, key tambien puede usar sus atributos (o,
si la clase define __lt__ como en 12_programacion_orientada_a_objetos.py,
sorted() puede compararlos directo sin necesitar key).

EN RESUMEN: ordenar por varios criterios en Python se resuelve
devolviendo una tupla desde key, en vez de encadenar comparaciones a
mano como en JS.
"""

people = [("Alice", 25), ("Bob", 25), ("Carol", 19)]

by_age_and_name = sorted(people, key=lambda p: (p[1], p[0]))
print(by_age_and_name)  # [('Carol', 19), ('Alice', 25), ('Bob', 25)]


"""
================================================================================
METODOS QUE FALTARON DE LISTAS, DICCIONARIOS Y SETS
================================================================================
"""

numbers = [1, 2, 3]

numbers.extend([4, 5])
print(numbers)  # [1, 2, 3, 4, 5]

print(numbers.index(3))  # 2

numbers_copy = numbers.copy()
numbers.clear()
print(numbers)  # []
print(numbers_copy)  # [1, 2, 3, 4, 5]  (la copia guardada antes de .clear() sigue intacta)


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Borrar o reemplazar un tramo de un array se hace con
array.splice(inicio, cantidadABorrar, ...nuevosElementos).

EN PYTHON:
Borrar o reemplazar un TRAMO completo de una lista se hace indexando con
slice (ver 05_strings.py para el slicing en strings; en listas funciona
identico).

EN RESUMEN: lo que en JS se hace con array.splice(), en Python se hace
asignando o borrando directamente sobre un slice de la lista.
"""

chars = ["a", "b", "c", "d", "e"]
del chars[1:3]
print(chars)  # ['a', 'd', 'e']

chars[1:1] = ["x", "y"]
print(chars)  # ['a', 'x', 'y', 'd', 'e']


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
UNPACKING EXTENDIDO: el primer/ultimo valor se separan con nombre normal,
y * agrupa "todo lo demas" en una lista.

EN RESUMEN: es parecido al rest de JS en destructuring de arrays
(const [first, ...rest] = arr), pero en Python el * tambien puede ir al
principio (*init, last = numbers) para agrupar "todo menos el ultimo".
"""

numbers = [1, 2, 3, 4, 5]
first, *rest = numbers
print(first, rest)  # 1 [2, 3, 4, 5]

*init, last = numbers
print(init, last)  # [1, 2, 3, 4] 5


"""
[SIMILAR A JS, CON DIFERENCIAS]
DICCIONARIOS: metodos que faltaron.

EN JS:
Un objeto no tiene metodos propios tan directos para estos casos; se
resuelven con Object.assign, delete, o comprobando la clave a mano.

EN PYTHON:
dict trae varios metodos dedicados para tareas comunes: update(),
pop(), setdefault() y dict.fromkeys(), que se ven en el codigo de abajo.

EN RESUMEN: donde JS resuelve estos casos con funciones sueltas o
codigo manual, Python los cubre con metodos dedicados del dict.
"""

person = {"name": "Alice", "age": 25}

person.update({"city": "NYC", "age": 26})
print(person)  # {'name': 'Alice', 'age': 26, 'city': 'NYC'}

popped_value = person.pop("age")
print(popped_value)  # 26
print(person)  # {'name': 'Alice', 'city': 'NYC'}

person.setdefault("country", "Mexico")
print(person)  # {'name': 'Alice', 'city': 'NYC', 'country': 'Mexico'}

zero_counts = dict.fromkeys(["a", "b", "c"], 0)
print(zero_counts)  # {'a': 0, 'b': 0, 'c': 0}


"""
[SIMILAR A JS, CON DIFERENCIAS]
SETS: los mismos operadores | & - ^ tienen su version como METODO.

EN JS:
El JS moderno (ES2025 — Chrome/Node desde 2024, ya ampliamente
disponible) SI trae metodos nativos en Set: union(), intersection(),
difference(), symmetricDifference(), isSubsetOf(), isSupersetOf() e
isDisjointFrom(). En codigo mas viejo (o si hay que soportar motores
antiguos) esto se seguia armando a mano combinando spread y
filter/has().

EN PYTHON:
Los operadores | & - ^ (ver 07_colecciones.py) tienen su version como
METODO (union(), intersection(), difference(), etc.), que se usa cuando
el otro lado no es necesariamente otro set (por ejemplo, puede ser una
lista).

EN RESUMEN: ambos lenguajes terminaron trayendo estas operaciones de
conjuntos listas para usar como metodo; la diferencia es que Python
ADEMAS ofrece los operadores | & - ^ para lo mismo, mientras que en JS
solo existen como metodo.
"""

a = {1, 2, 3}
b = [2, 3, 4]

print(a.union(b))  # {1, 2, 3, 4}
print(a.intersection(b))  # {2, 3}
print(a.difference(b))  # {1}
print(a.issubset({1, 2, 3, 4}))  # True
print(a.issuperset({1, 2}))  # True


"""
================================================================================
for...else
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Igual que while (ver 06_loops.py), un for tambien puede llevar un else,
que se ejecuta SOLO si el loop termino de forma normal (recorrio todos
los elementos sin interrupcion), y se SALTA si el loop termino por
break.

EN RESUMEN: es un patron muy usado para "buscar algo en una coleccion"
sin necesitar una variable bandera aparte para saber si se encontro.
"""

numbers = [1, 3, 5, 7, 9]
target = 4

for n in numbers:
    if n == target:
        print("found")  # (nunca se ejecuta: 4 no esta en numbers)
        break
else:
    print("not found in the list")  # not found in the list


"""
================================================================================
OPERADOR MORSA :=
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Agregado en Python 3.8. El operador := asigna un valor a una variable Y
devuelve ese mismo valor, en la misma expresion — util para no repetir
un calculo dentro de la condicion de un while/if o dentro de una
comprehension.

EN JS, la asignacion = ya devuelve el valor asignado y se puede usar
embebida dentro de una expresion, sin necesitar un operador especial.

EN PYTHON, el = normal NO es una expresion (no se puede usar embebido
dentro de un if o un while); por eso hizo falta agregar el operador :=
aparte para cubrir ese mismo caso de uso.

EN RESUMEN: := le da a Python lo que en JS ya viene gratis con el =
normal: poder asignar y usar el valor en la misma expresion.
"""

data = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

results = [square for n in data if (square := n ** 2) > 20]
print(results)  # [25, 36, 49, 64, 81, 100]


"""
================================================================================
divmod()
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Para obtener cociente y residuo hay que calcularlos por separado:
Math.floor(a / b) para el cociente y a % b para el residuo.

EN PYTHON:
divmod(a, b) devuelve, en una sola llamada, el resultado de // y de %
juntos como una tupla: (cociente, residuo). Evita escribir la misma
division dos veces por separado.

EN RESUMEN: divmod() empaqueta en una sola llamada lo que en JS
requiere dos operaciones separadas.
"""

quotient, remainder = divmod(17, 5)
print(quotient, remainder)  # 3 2


"""
================================================================================
VARIOS TIPOS DE EXCEPCION EN UN MISMO except
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Cuando el mismo bloque de manejo de error debe aplicar a mas de un tipo
de excepcion, se agrupan en una tupla dentro del mismo except, en vez de
repetir el bloque para cada tipo.

EN RESUMEN: except (TipoA, TipoB): evita duplicar el mismo bloque de
manejo de errores para cada tipo de excepcion por separado.
"""

def convert(value):
    try:
        return int(value) / 2
    except (ValueError, TypeError):
        return None

print(convert("10"))  # 5.0
print(convert("abc"))  # None (ValueError capturado)
print(convert(None))  # None (TypeError capturado)


"""
================================================================================
PILA (STACK) Y COLA (QUEUE) CON LISTAS
================================================================================
[IGUAL QUE JS]

Python no tiene un tipo Stack/Queue dedicado en la sintaxis basica; se
construyen con los metodos que list ya tiene.

EN JS, una pila (stack) se arma con .push() para apilar y .pop() para
desapilar el ultimo elemento de un array.

EN PYTHON, PILA (LIFO — el ultimo que entra es el primero que sale):
append() para apilar, pop() (sin indice) para desapilar el ULTIMO
elemento.

EN RESUMEN: apilar y desapilar funciona identico en ambos lenguajes;
solo cambian los nombres de los metodos (push/pop en JS, append/pop en
Python).
"""

stack = []
stack.append(1)
stack.append(2)
stack.append(3)
print(stack.pop())  # 3
print(stack)  # [1, 2]


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Una cola (queue) se puede armar con .push() para encolar y .shift()
para desencolar el primer elemento de un array, pero .shift() es lento
en arrays grandes porque reacomoda todos los indices; por eso a veces
se prefiere una estructura de cola dedicada.

EN PYTHON:
COLA (FIFO — el primero que entra es el primero que sale): append() para
encolar, pop(0) para desencolar el PRIMER elemento. Funciona, pero
pop(0) en una lista normal es lento para colas grandes (tiene que
recorrer toda la lista para acomodar el hueco que deja el primer
elemento). Para una cola de verdad se usa collections.deque, que
soporta popleft() en tiempo constante.

EN RESUMEN: en ambos lenguajes, sacar el primer elemento de un array o
lista normal es lento cuando la coleccion es grande; la solucion en
Python es collections.deque.
"""

from collections import deque

queue = deque()
queue.append(1)
queue.append(2)
queue.append(3)
print(queue.popleft())  # 1
print(queue)  # deque([2, 3])


"""
================================================================================
PARSEAR INPUT PARA EJERCICIOS
================================================================================
[IGUAL QUE JS]

Un patron que apareceras usando todo el tiempo al practicar algoritmos:
leer una linea con varios numeros separados por espacio, y convertirlos
todos a int de un jalon combinando .split() (ver 05_strings.py) con
map() (ver 08_funciones.py).

EN RESUMEN: el mismo patron de "split + map" para parsear numeros de un
string funciona igual en JS y en Python.
"""

line = "3 7 12 4"
numbers = list(map(int, line.split()))
print(numbers)  # [3, 7, 12, 4]

numbers_comprehension = [int(x) for x in line.split()]
print(numbers_comprehension)  # [3, 7, 12, 4]


"""
================================================================================
collections.Counter Y collections.defaultdict
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Counter cuenta cuantas veces aparece cada elemento de un iterable de un
solo jalon, sin tener que armar un diccionario manual con
dict.get(x, 0) + 1 en un loop. Es EXTREMADAMENTE comun en ejercicios de
algoritmos (frecuencia de letras, de palabras, de numeros).

EN RESUMEN: Counter(iterable) reemplaza el patron manual de "recorrer y
sumar 1 en un diccionario" por una sola llamada.
"""

from collections import Counter

letters = "mississippi"
frequency = Counter(letters)

print(frequency)  # Counter({'i': 4, 's': 4, 'p': 2, 'm': 1})
print(frequency["s"])  # 4
print(frequency.most_common(2))  # [('i', 4), ('s', 4)]


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
defaultdict es un diccionario que, al acceder a una clave que NO
existe, en vez de lanzar KeyError (ver 07_colecciones.py) le asigna
automaticamente un valor inicial (segun la funcion que le des: list,
int, set...). Evita escribir "if key not in dict: dict[key] = ..."
antes de cada uso.

EN RESUMEN: defaultdict evita escribir el chequeo manual de "si la
clave no existe, inicializala" antes de cada uso.
"""

from collections import defaultdict

groups = defaultdict(list)

groups["fruits"].append("apple")
groups["fruits"].append("banana")
groups["vegetables"].append("carrot")

print(dict(groups))  # {'fruits': ['apple', 'banana'], 'vegetables': ['carrot']}


"""
================================================================================
itertools: HERRAMIENTAS PARA GENERAR COMBINACIONES SIN LOOPS ANIDADOS
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

El modulo itertools trae funciones ya hechas para los patrones de
"generar todas las combinaciones/ordenes posibles de algo" que aparecen
todo el tiempo en ejercicios de algoritmos, y que a mano requerirían
loops anidados (o recursion) escritos a pulso.

IMPORTANTE: al igual que los generators (ver 09_iterables_y_comprehensions.py),
TODAS las funciones de itertools son LAZY: devuelven un iterador que va
calculando cada combinacion sobre la marcha, no una lista ya armada en
memoria. Por eso en todos los ejemplos de abajo se envuelven con
list(...) para poder ver e imprimir todos los valores de un jalon.

EN RESUMEN: itertools reemplaza loops anidados o recursion escrita a
mano para generar combinaciones, y lo hace de forma lazy (perezosa).
"""

import itertools


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
itertools.chain(*iterables) encadena varios iterables en uno solo,
recorriendolos en secuencia, sin construir antes una lista combinada.

EN RESUMEN: chain() recorre varios iterables como si fueran uno solo,
sin necesitar concatenarlos primero.
"""

evens = [2, 4, 6]
odds = [1, 3, 5]

print(list(itertools.chain(evens, odds)))  # [2, 4, 6, 1, 3, 5]


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
itertools.product(*iterables, repeat=1) da el PRODUCTO CARTESIANO: todas
las combinaciones posibles tomando un elemento de cada iterable, en
TODOS los ordenes cruzados (el orden importa y SI se repiten elementos
entre combinaciones distintas). Es la forma estandar de generar, por
ejemplo, todas las coordenadas de una grilla, o todas las combinaciones
de un par de opciones independientes, sin escribir loops anidados a mano.

EN RESUMEN: product() reemplaza los loops anidados que harian falta
para combinar varias listas entre si.
"""

rows = [1, 2]
cols = ["a", "b"]

print(list(itertools.product(rows, cols)))  # [(1, 'a'), (1, 'b'), (2, 'a'), (2, 'b')]


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
repeat=2 hace el producto cartesiano de un iterable consigo mismo dos
veces (equivalente a product(iterable, iterable)) — util para generar,
por ejemplo, todos los pares de 2 digitos posibles a partir de un
conjunto chico de digitos.

EN RESUMEN: repeat evita escribir el mismo iterable varias veces a mano
cuando se quiere combinarlo consigo mismo.
"""

digits = [0, 1]

print(list(itertools.product(digits, repeat=2)))  # [(0, 0), (0, 1), (1, 0), (1, 1)]


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
itertools.permutations(iterable, r=None) da todos los ORDENES posibles
de r elementos (por defecto, de todos los elementos): el orden importa,
y cada elemento se usa como maximo una vez por combinacion. Es un
patron EXTREMADAMENTE comun en ejercicios de entrevistas de codigo
("generar todos los arreglos posibles de...").

EN RESUMEN: permutations() genera todos los ordenes posibles sin
repetir un mismo elemento dentro de cada resultado.
"""

print(list(itertools.permutations([1, 2, 3])))
# [(1, 2, 3), (1, 3, 2), (2, 1, 3), (2, 3, 1), (3, 1, 2), (3, 2, 1)]


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
itertools.combinations(iterable, r) da todas las formas de ELEGIR r
elementos donde el orden NO importa, sin repetir el mismo elemento. La
diferencia con permutations se ve clara comparando el mismo iterable y
el mismo r: combinations trata (1, 2) y (2, 1) como la misma eleccion
(solo aparece una vez), mientras que permutations los cuenta como dos
resultados distintos.

EN RESUMEN: usa combinations() cuando el orden no importa (elegir un
equipo), y permutations() cuando si importa (asignar puestos).
"""

print(list(itertools.combinations([1, 2, 3], 2)))  # [(1, 2), (1, 3), (2, 3)]
print(list(itertools.permutations([1, 2, 3], 2)))
# [(1, 2), (1, 3), (2, 1), (2, 3), (3, 1), (3, 2)]

"""
[EXCLUSIVO DE PYTHON — no existe en JS]
(itertools.combinations_with_replacement existe tambien, para el caso
de combinaciones donde SI se puede repetir un mismo elemento).

EN RESUMEN: combinations_with_replacement() es la variante de
combinations() para cuando un mismo elemento puede elegirse mas de una
vez.
"""

print(list(itertools.combinations_with_replacement([1, 2, 3], 2)))
# [(1, 1), (1, 2), (1, 3), (2, 2), (2, 3), (3, 3)]
