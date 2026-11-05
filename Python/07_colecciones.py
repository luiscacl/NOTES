"""
================================================================================
PANORAMA: CUATRO ESTRUCTURAS, CUATRO PROPOSITOS
================================================================================

JS practicamente solo tiene UN tipo de coleccion de uso general para
datos en secuencia: el array (ademas de Object para clave-valor, y Set
como agregado mas reciente). Python separa el concepto en CUATRO tipos
built-in distintos, cada uno con una regla de comportamiento propia, y
elegir el correcto es parte normal de escribir Python:

    tipo     ordenado   permite duplicados   se puede modificar (mutable)
    list      si              si                     si
    tuple     si              si                     NO
    set       no              NO                     si (los elementos, no por indice)
    dict      si*             claves NO, valores si  si

    (*) dict mantiene el orden de insercion de sus claves desde Python
    3.7, pero sigue siendo clave-valor, no indexado por posicion numerica.

La pregunta para elegir cual usar casi siempre es: "¿necesito que se
pueda modificar?", "¿me importa el orden?", "¿pueden repetirse los
valores?", "¿necesito buscar por una clave con nombre en vez de por
posicion?".
"""


"""
================================================================================
LISTAS (list)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El array es la coleccion de uso general: ordenado, mutable, permite
duplicados y permite mezclar tipos distintos dentro del mismo array.

EN PYTHON:
list es la estructura que mas se parece al array de JS: ordenada,
MUTABLE, permite elementos duplicados, y permite mezclar tipos distintos
dentro de la misma lista. Se escribe con corchetes [ ].

EN RESUMEN: list en Python es, en la practica, el equivalente directo
al array de JS.
"""

numbers = [1, 2, 3, 4, 5]
mixed = [1, "two", 3.0, True]

print(numbers[0])  # 1
print(numbers[-1])  # 5
print(numbers[1:3])  # [2, 3]
print(len(numbers))  # 5


"""
[IGUAL QUE JS]
    JS                       Python
    arr.push(x)              list.append(x)
    arr.pop()                list.pop()
    arr.unshift(x)           list.insert(0, x)
    arr.splice(i, 1)         list.pop(i)  /  del list[i]
    arr.includes(x)          x in list
    arr.indexOf(x)           list.index(x)
    arr.reverse()            list.reverse()
    arr.sort()               list.sort()
    arr.concat(other)        list + other  /  list.extend(other)
"""

numbers.append(6)
numbers.insert(0, 0)
numbers.remove(3)
numbers.pop()
numbers.sort()
numbers.reverse()
print(numbers)  # [5, 4, 2, 1, 0]

print(3 in numbers)  # False
print(numbers.count(2))  # 1


"""
[IGUAL QUE JS]
Al ser MUTABLE, una lista se puede modificar por indice directamente,
igual que un array de JS.

EN RESUMEN: numbers[0] = valor reasigna esa posicion en el propio
lugar, igual que en JS.
"""

numbers[0] = 100
print(numbers)  # [100, 4, 2, 1, 0]


"""
[SIMILAR A JS, CON DIFERENCIAS]
DESEMPAQUETAR (SPREAD) DENTRO DE UN LITERAL DE LISTA

EN JS:
El spread [...a, ...b] dentro de un literal de array esparce el
contenido de otro iterable ahi mismo, combinando varias colecciones en
un array nuevo.

EN PYTHON:
Un * dentro de un [ ] hace exactamente lo mismo: "esparce" el contenido
de otro iterable ahi mismo, combinando varias colecciones en una lista
nueva.

EN RESUMEN: [*part1, *part2, 100] en Python equivale a [...a, ...b, 100]
en JS.
"""

part1 = [1, 2, 3]
part2 = [4, 5, 6]

combined_list = [*part1, *part2, 100]
print(combined_list)  # [1, 2, 3, 4, 5, 6, 100]


"""
================================================================================
TUPLAS (tuple)
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

tuple se escribe con parentesis ( ) en vez de corchetes. Estructuralmente
es identica a una lista (ordenada, permite duplicados, permite mezclar
tipos), con UNA diferencia que lo cambia todo: es INMUTABLE. Una vez
creada, no se puede agregar, quitar, ni reasignar ninguno de sus
elementos.

JS no tiene un tipo equivalente nativo. Lo mas cercano es
Object.freeze() sobre un array, pero eso solo congela el array despues
de crearlo: no es un tipo del lenguaje pensado para eso desde el inicio.

EN RESUMEN: tuple es una lista que nunca cambia una vez creada, y no
tiene equivalente nativo real en JS.
"""

coordinate = (10, 20)
print(coordinate[0])  # 10
print(coordinate[1])  # 20


"""
Intentar modificarla lanza TypeError de inmediato, en la linea donde se
intenta la asignacion: una tupla ni siquiera permite el intento a medias,
falla por completo antes de cambiar nada.
"""

coordinate[0] = 100  # TypeError: 'tuple' object does not support item assignment


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
¿Por que usar una tupla en vez de una lista? Se usa quando el conjunto de
valores representa algo que conceptualmente no deberia cambiar una vez
creado: una coordenada (x, y), un color RGB (r, g, b), o varios valores
que una funcion devuelve juntos y que quien los recibe no deberia poder
alterar por accidente (ver 08_funciones.py, donde return a, b crea
justamente una tupla).

Una tupla de UN SOLO elemento necesita una coma al final para no
confundirse con un simple parentesis de agrupacion matematica.

EN RESUMEN: una tupla comunica "esto no deberia cambiar"; usala para
registros fijos y valores de retorno multiples.
"""

single_element = (5,)
not_a_tuple = (5)
print(type(single_element))  # <class 'tuple'>
print(type(not_a_tuple))  # <class 'int'>


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
NAMEDTUPLE: TUPLAS CON CAMPOS CON NOMBRE

collections.namedtuple crea un tipo de tupla donde cada posicion ADEMAS
tiene un nombre, para acceder por .nombre en vez de solo por indice
numerico, sin perder ninguna de las ventajas de una tupla normal
(inmutable, ligera). Sigue comportandose como tupla: se puede indexar,
desempaquetar, comparar por valor.

En JS, lo mas parecido en intencion seria un objeto de solo lectura,
pero ahi la comparacion termina: un objeto de JS no se puede indexar
por posicion ni desempaquetar como tupla.

EN RESUMEN: namedtuple da nombres a los campos de una tupla sin quitarle
ninguna de sus capacidades de tupla.
"""

from collections import namedtuple

Point = namedtuple("Point", ["x", "y"])

p1 = Point(10, 20)
print(p1.x)  # 10
print(p1[0])  # 10
print(p1)  # Point(x=10, y=20)

x, y = p1
print(x, y)  # 10 20


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
METODOS DE TUPLA

Al ser inmutable, tuple solo tiene DOS metodos (a diferencia de list,
que tiene muchos: append, insert, remove, sort...). Como no se puede
agregar, quitar ni reordenar nada, no tiene sentido que existan
metodos para eso. Los unicos dos que si tienen sentido son de solo
LECTURA:

    tuple.count(value)  cuantas veces aparece value en la tupla
    tuple.index(value)  la posicion de la PRIMERA aparicion de value

Son los mismos dos metodos que tiene list, simplemente sin todos los
demas que dependen de poder modificar la coleccion.

EN RESUMEN: tuple solo ofrece .count() e .index(), porque cualquier
metodo que mutara la coleccion no tendria sentido en algo inmutable.
"""

scores = (10, 20, 20, 30)
print(scores.count(20))  # 2
print(scores.index(30))  # 3


"""
================================================================================
SETS (set)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Set es una coleccion de elementos unicos: al agregar un valor repetido,
el duplicado se descarta solo. Para operaciones de teoria de conjuntos
(union, interseccion, diferencia) no hay operadores dedicados; hay que
combinarlas a mano con metodos.

EN PYTHON:
set es una coleccion NO ORDENADA de elementos UNICOS: al crearla o
agregarle valores repetidos, los duplicados se descartan solos, sin que
tengas que filtrarlos tu. Ademas, un set soporta directamente
operaciones de teoria de conjuntos (union, interseccion, diferencia)
con operadores dedicados.

EN RESUMEN: set en Python es el equivalente a Set de JS, pero con
operadores nativos para union/interseccion/diferencia que JS no tiene.
"""

colors = {"red", "green", "blue", "red"}
print(colors)  # e.g. {'blue', 'red', 'green'} (set order is not guaranteed)

colors.add("yellow")
colors.remove("green")
print(colors)  # e.g. {'blue', 'yellow', 'red'} (set order is not guaranteed)

a = {1, 2, 3}
b = {2, 3, 4}

print(a | b)  # e.g. {1, 2, 3, 4} (set order is not guaranteed)
print(a & b)  # e.g. {2, 3} (set order is not guaranteed)
print(a - b)  # e.g. {1} (set order is not guaranteed)
print(a ^ b)  # e.g. {1, 4} (set order is not guaranteed)


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Al NO estar ordenado, un set no se puede indexar con colors[0]: esa
operacion no existe para set porque no hay un "primer elemento"
garantizado. Para revisar si algo esta adentro se usa in (ver
03_operadores.py), que ademas es mucho mas rapido en un set que en una
lista larga.

Un detalle de sintaxis que confunde viniendo de otros lenguajes: un
diccionario vacio {} es dict, NO un set vacio. Para un set vacio hay que
usar la funcion set() explicitamente, porque {} ya esta reservado para
diccionario vacio.

EN RESUMEN: sin indexacion por posicion, usa in para pertenencia; y
recuerda que {} vacio es dict, nunca set.
"""

empty = set()
print(type(empty))  # <class 'set'>
print(type({}))  # <class 'dict'>


"""
[SIMILAR A JS, CON DIFERENCIAS]
.discard(x) VS .remove(x): EL CLASICO ERROR A EVITAR

EN JS:
Set.prototype.delete() siempre se comporta igual sin importar si el
elemento existe: nunca lanza, solo devuelve true o false.

EN PYTHON:
Ambos metodos quitan un elemento del set, pero se comportan distinto
cuando ese elemento NO existe: .remove(x) lanza KeyError (parecido a
como list.remove lanza ValueError si el valor no esta), mientras que
.discard(x) no lanza nada, simplemente no hace nada si x no estaba.

Usa .remove() cuando esperas que el elemento SI este y quieres que un
bug te avise si no; usa .discard() cuando quitar algo que quiza no
estaba es un caso normal, no un error.

EN RESUMEN: Python separa "quitar y avisar si falta" (.remove) de
"quitar si esta, ignorar si no" (.discard); JS solo tiene el
comportamiento equivalente a .discard().
"""

fruits = {"apple", "banana", "cherry"}

fruits.remove("kiwi")  # KeyError: 'kiwi'


"""
.discard(), en cambio, no lanza nada aunque el elemento no exista.
"""

fruits.discard("kiwi")
print(fruits)  # e.g. {'apple', 'cherry', 'banana'} (set order is not guaranteed)


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
.pop() EN UN SET: UN ELEMENTO ARBITRARIO, NO EL "PRIMERO"

list.pop() sin argumento quita y devuelve el ULTIMO elemento, porque
list tiene orden. set no tiene orden, asi que set.pop() quita y
devuelve UN elemento ARBITRARIO: no hay ninguna garantia de cual sera,
no se puede predecir ni depender de cual va a salir.

EN RESUMEN: set.pop() no tiene "ultimo elemento" que devolver porque el
set no tiene orden; el elemento que sale es arbitrario.
"""

removed_fruit = fruits.pop()
print(removed_fruit)  # e.g. apple (arbitrary element, order is not guaranteed)
print(fruits)  # e.g. {'cherry', 'banana'} (set order is not guaranteed)


"""
[SIMILAR A JS, CON DIFERENCIAS]
.clear() Y .copy()

EN JS:
Set.prototype.clear() tambien vacia el set en el propio lugar, pero Set
no tiene un metodo .copy(): para copiarlo hay que usar
new Set(original).

EN PYTHON:
.clear() vacia el set en el propio lugar. .copy() devuelve una copia
NUEVA e independiente (superficial, igual que list.copy()): modificar
la copia no afecta al original.

EN RESUMEN: .clear() se comporta igual en ambos lenguajes; .copy() es
un metodo dedicado en Python donde JS necesita new Set(original).
"""

fruits_copy = fruits.copy()
fruits_copy.add("mango")

print(fruits)  # e.g. {'cherry', 'banana'} (set order is not guaranteed)
print(fruits_copy)  # e.g. {'cherry', 'mango', 'banana'} (set order is not guaranteed)

fruits.clear()
print(fruits)  # set()


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
.isdisjoint(other)

Devuelve True si dos sets NO tienen ningun elemento en comun (su
interseccion esta vacia).

JS no tiene un metodo equivalente directo; habria que armarlo a mano
comprobando si la interseccion resulta vacia.

EN RESUMEN: .isdisjoint() es un atajo directo para una comprobacion que
en JS toca construir a mano.
"""

set_a = {1, 2, 3}
set_b = {4, 5, 6}
set_c = {3, 4}

print(set_a.isdisjoint(set_b))  # True
print(set_a.isdisjoint(set_c))  # False


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
VERSIONES "EN EL PROPIO LUGAR" (IN-PLACE) DE LAS OPERACIONES DE CONJUNTOS

|, &, -, ^ (vistos arriba) siempre devuelven un set NUEVO, dejando los
originales intactos. Para cada uno existe tambien un metodo que hace la
MISMA operacion pero MODIFICANDO el set original en vez de crear uno
nuevo — el mismo contraste que ya viste entre sorted() (lista nueva) y
list.sort() (modifica en el propio lugar) en
14_complementos_para_algoritmos.py:

    operador   crea un set nuevo      metodo equivalente que muta in-place
    |          union                  .update(other)
    &          interseccion           .intersection_update(other)
    -          diferencia             .difference_update(other)
    ^          diferencia simetrica   .symmetric_difference_update(other)

EN RESUMEN: por cada operador que crea un set nuevo hay un metodo
gemelo que modifica el original en el propio lugar.
"""

x = {1, 2, 3}
y = {2, 3, 4}

x.update(y)
print(x)  # e.g. {1, 2, 3, 4} (set order is not guaranteed)

x = {1, 2, 3}
x.intersection_update(y)
print(x)  # e.g. {2, 3} (set order is not guaranteed)

x = {1, 2, 3}
x.difference_update(y)
print(x)  # e.g. {1} (set order is not guaranteed)

x = {1, 2, 3}
x.symmetric_difference_update(y)
print(x)  # e.g. {1, 4} (set order is not guaranteed)


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
FROZENSET: LA VERSION INMUTABLE DE SET

Asi como tuple es la version inmutable de list (ver la tabla al inicio
de este archivo), frozenset es la version inmutable de set: los mismos
elementos unicos y sin orden, pero una vez creado no se le puede
agregar ni quitar nada (no tiene .add(), .remove(), .discard(), etc.).

La razon real para que exista no es solo "por si acaso": un set normal
es MUTABLE, y en Python solo lo que es inmutable (y por lo tanto
hasheable) puede usarse como clave de un dict o como elemento DENTRO de
otro set. Un set normal no puede cumplir ninguno de esos dos roles.

EN RESUMEN: frozenset existe para los casos donde necesitas un
"conjunto" que a su vez sea hasheable, algo que set no puede ser.
"""

visited_pages = {"home", "about"}

page_cache = {visited_pages: "cached page"}  # TypeError: cannot use 'set' as a dict key (unhashable type: 'set')


"""
frozenset si puede cumplir esos dos roles, precisamente porque es
inmutable.
"""

visited_pages = frozenset({"home", "about"})

page_cache = {visited_pages: "cached page"}
print(page_cache[visited_pages])  # cached page

group_of_groups = {frozenset({1, 2}), frozenset({3, 4})}
print(group_of_groups)  # e.g. {frozenset({3, 4}), frozenset({1, 2})} (set order is not guaranteed)


"""
================================================================================
DICCIONARIOS (dict)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El objeto plano ({ clave: valor }) se usa como estructura de datos
clave-valor, aunque tambien puede llevar metodos y this (esa otra cara
del objeto se ve en 12_programacion_orientada_a_objetos.py con class).

EN PYTHON:
dict es el equivalente al objeto plano de JS usado especificamente como
estructura de datos clave-valor. Las claves pueden ser cualquier tipo
INMUTABLE: str, int, float, bool, tuple. Una lista NO puede ser clave,
precisamente porque es mutable.

EN RESUMEN: dict es el objeto {clave: valor} de JS, pero restringido a
claves inmutables y separado del concepto de "objeto con metodos".
"""

person = {
    "name": "Anna",
    "age": 25,
    "active": True,
}

print(person["name"])  # Anna
print(person.get("age"))  # 25
print(person.get("phone", "not registered"))  # not registered


"""
[IGUAL QUE JS]
    JS                          Python
    obj.key / obj["key"]        dict["key"]
    obj.key ?? default          dict.get("key", default)
    Object.keys(obj)            dict.keys()
    Object.values(obj)          dict.values()
    Object.entries(obj)         dict.items()
    delete obj.key              del dict["key"]
    "key" in obj                 "key" in dict
"""

person["phone"] = "555-1234"
person["age"] = 26
del person["active"]

print(person)  # {'name': 'Anna', 'age': 26, 'phone': '555-1234'}
print("name" in person)  # True


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
for...in itera sobre las claves de un objeto plano; para obtener clave
y valor juntos en la misma vuelta hay que combinarlo con
Object.entries().

EN PYTHON:
Recorrer un diccionario con for itera sobre las CLAVES por defecto. Para
obtener clave y valor juntos en la misma vuelta se usa .items().

EN RESUMEN: por defecto, un for solo entrega claves; usa .items() para
obtener clave y valor a la vez, igual que Object.entries() en JS.
"""

for key in person:
    print(key)
    # name
    # age
    # phone

for key, value in person.items():
    print(key, value)
    # name Anna
    # age 26
    # phone 555-1234

for value in person.values():
    print(value)
    # Anna
    # 26
    # 555-1234


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
.keys()/.values()/.items() DEVUELVEN VISTAS VIVAS, NO UNA FOTO

Esta es una diferencia sutil e importante con JS. Object.keys(obj) en
JS devuelve un ARRAY nuevo, una foto congelada en ese instante: si
despues mutas obj, ese array ya devuelto no cambia.

dict.keys() (y lo mismo .values() y .items()) en Python NO devuelve una
copia: devuelve un objeto "vista" que queda CONECTADO al diccionario.
Si el diccionario cambia despues, la vista que ya tenias guardada en una
variable refleja el cambio automaticamente, sin que la vuelvas a pedir.

EN RESUMEN: en JS, Object.keys() congela un snapshot; en Python,
dict.keys() es una ventana en vivo hacia el diccionario.
"""

stock = {"apples": 10, "bananas": 5}

stock_keys = stock.keys()
print(stock_keys)  # dict_keys(['apples', 'bananas'])

stock["oranges"] = 8
print(stock_keys)  # dict_keys(['apples', 'bananas', 'oranges'])


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Acceder con obj.missingKey a una clave que no existe simplemente
devuelve undefined, sin detener el programa.

EN PYTHON:
Acceder con [] a una clave que no existe lanza KeyError de inmediato.
.get() evita ese error devolviendo None, o el valor por defecto que le
pases como segundo argumento, en vez de lanzar una excepcion.

EN RESUMEN: Python falla ruidosamente (KeyError) donde JS falla en
silencio (undefined); usa .get() cuando quieras el comportamiento
silencioso de JS.
"""

person["missing_key"]  # KeyError: 'missing_key'


"""
[SIMILAR A JS, CON DIFERENCIAS]
DESEMPAQUETAR (SPREAD) DENTRO DE UN LITERAL DE DICCIONARIO

EN JS:
El spread {...a, ...b} dentro de un literal de objeto junta varios
objetos en uno nuevo. Si dos tienen la MISMA clave, gana el valor del
que aparece MAS A LA DERECHA.

EN PYTHON:
** dentro de un { } hace exactamente lo mismo para diccionarios: junta
varios en uno nuevo, con la misma regla de que gana el valor del que
aparece MAS A LA DERECHA si hay claves repetidas.

EN RESUMEN: {**base, **extra} en Python equivale a {...a, ...b} en JS,
con identica regla de desempate.
"""

base = {"name": "Anna", "age": 25}
extra = {"age": 26, "city": "NYC"}

combined_dict = {**base, **extra}
print(combined_dict)  # {'name': 'Anna', 'age': 26, 'city': 'NYC'}


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
.popitem() QUITA EL ULTIMO PAR INSERTADO

.popitem() quita y devuelve, como tupla (clave, valor), el ULTIMO par
que se agrego al diccionario. Depende del orden de insercion que dict
mantiene desde Python 3.7 (por eso es LIFO: Last In, First Out), no es
un par al azar.

JS no tiene un equivalente directo para un objeto plano, porque este no
expone un metodo para "sacar la ultima propiedad agregada".

EN RESUMEN: .popitem() es un atajo de Python sin equivalente nativo en
JS.
"""

settings = {"theme": "dark", "volume": 80, "language": "en"}

last_setting = settings.popitem()
print(last_setting)  # ('language', 'en')
print(settings)  # {'theme': 'dark', 'volume': 80}


"""
[SIMILAR A JS, CON DIFERENCIAS]
.copy(): COPIA SUPERFICIAL INDEPENDIENTE

EN JS:
{...obj} u Object.assign({}, obj) tambien crean una copia superficial
(shallow): el objeto en si es independiente, pero si algun valor es a
su vez un objeto o array, esa referencia interna se comparte entre
ambas copias.

EN PYTHON:
.copy() devuelve un diccionario nuevo con los mismos pares clave-valor.
Es una copia SUPERFICIAL (shallow): el diccionario en si es
independiente (agregar o quitar claves en uno no afecta al otro), pero
si algun valor es a su vez una lista o diccionario mutable, ese valor
interno sigue siendo la MISMA referencia compartida en ambas copias.

EN RESUMEN: el concepto de copia superficial es identico en ambos
lenguajes: el contenedor se duplica, los valores anidados mutables no.
"""

settings_copy = settings.copy()
settings_copy["volume"] = 100

print(settings)  # {'theme': 'dark', 'volume': 80}
print(settings_copy)  # {'theme': 'dark', 'volume': 100}


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
OPERADORES DE MEZCLA | Y |= (Python 3.9+)

Ademas del spread {**a, **b} ya visto, dict tiene un operador dedicado
para mezclar: dict1 | dict2 devuelve un diccionario NUEVO (igual que
{**dict1, **dict2}, con la misma regla de que gana el valor del que
esta MAS A LA DERECHA si hay claves repetidas). dict1 |= dict2 hace lo
mismo pero MODIFICANDO dict1 en el propio lugar, igual que x += y para
numeros.

JS no tiene un operador equivalente; el spread {...a, ...b} es lo mas
cercano, pero siempre crea un objeto nuevo, nunca modifica in-place.

EN RESUMEN: | y |= le dan a Python una forma de mezclar diccionarios
in-place que JS simplemente no tiene.
"""

defaults = {"theme": "light", "volume": 50}
overrides = {"volume": 80, "language": "en"}

merged_settings = defaults | overrides
print(merged_settings)  # {'theme': 'light', 'volume': 80, 'language': 'en'}
print(defaults)  # {'theme': 'light', 'volume': 50}

defaults |= overrides
print(defaults)  # {'theme': 'light', 'volume': 80, 'language': 'en'}


"""
================================================================================
COLECCIONES 2D
================================================================================
[IGUAL QUE JS]

Una lista puede contener otras listas, formando una estructura de filas
y columnas (matriz), igual que un array de arrays en JS.

EN RESUMEN: una lista de listas es una matriz, igual que en JS.
"""

matrix = [
    [1, 2, 3],
    [4, 5, 6],
    [7, 8, 9],
]

print(matrix[0])  # [1, 2, 3]
print(matrix[0][0])  # 1
print(matrix[1][2])  # 6


"""
[IGUAL QUE JS]
Recorrer una matriz completa necesita un loop anidado (ver 06_loops.py):
el externo recorre las filas, el interno recorre los elementos de cada
fila.

EN RESUMEN: matriz completa, loop anidado; misma logica en ambos
lenguajes.
"""

for row in matrix:
    for value in row:
        print(value, end=" ")
    print()
    # 1 2 3
    # 4 5 6
    # 7 8 9


"""
[IGUAL QUE JS]
Modificar un elemento especifico se hace indexando ambos niveles, uno
tras otro.

EN RESUMEN: matrix[fila][columna] = valor, igual que en JS.
"""

matrix[0][0] = 100
print(matrix)  # [[100, 2, 3], [4, 5, 6], [7, 8, 9]]


"""
[SIMILAR A JS, CON DIFERENCIAS]
CUIDADO al crear una matriz repitiendo una lista con *: multiplicar una
lista que contiene otra lista REPITE LA REFERENCIA a esa misma lista
interna en cada posicion, no crea copias independientes.

EN JS:
El mismo problema ocurre con Array(rows).fill(Array(cols).fill(0)):
.fill() tambien repite la MISMA referencia del array interno en cada
posicion, no crea copias.

EN PYTHON:
[[0] * cols] * rows repite la referencia a la misma lista interna en
cada posicion. Modificar una fila termina modificando "todas" porque en
realidad son la misma lista vista tres veces. La forma correcta es una
comprehension que construya cada fila por separado:
[[0] * cols for _ in range(rows)].

EN RESUMEN: tanto en JS como en Python, repetir un contenedor mutable
con un metodo de "llenar" copia la referencia, no el contenido; hay que
construir cada fila por separado.
"""

rows, cols = 3, 3

bad_matrix = [[0] * cols] * rows
bad_matrix[0][0] = 1
print(bad_matrix)  # [[1, 0, 0], [1, 0, 0], [1, 0, 0]]

good_matrix = [[0] * cols for _ in range(rows)]
good_matrix[0][0] = 1
print(good_matrix)  # [[1, 0, 0], [0, 0, 0], [0, 0, 0]]


"""
================================================================================
CUANDO USAR CADA UNA EN LA PRACTICA (Y POR QUE)
================================================================================

Esto es lo que de verdad separa a alguien que "conoce la sintaxis" de
alguien que escribe Python profesional: elegir la estructura correcta
no es preferencia, cada una tiene una razon tecnica real detras.

LIST — la opcion por defecto para una secuencia. Usala cuando el orden
importa y vas a recorrer/agregar/quitar elementos con frecuencia. Es la
eleccion correcta el 80% de las veces que "necesitas guardar varias
cosas".

TUPLE en vez de list — cuando el dato es un REGISTRO de forma fija que
no deberia cambiar despues de creado (una coordenada, un color RGB, una
fila que sale de una base de datos). La razon no es solo estilo: al ser
inmutable, una tuple SI puede ser clave de un dict o elemento de un set
(una list no puede, ver KeyError/TypeError si lo intentas), y comunica
una intencion explicita a quien lea el codigo despues ("esto no debe
mutar"), evitando bugs donde una funcion recibe una lista y la modifica
por accidente en un lugar que no deberia. NAMEDTUPLE o UN DATACLASS
(visto en 12_programacion_orientada_a_objetos.py) son la mejora
profesional sobre una tuple simple cuando el codigo accede a sus campos
por nombre en varios lugares: punto[0] no dice nada al leerlo; punto.x
si. Cambiar de indices magicos a nombres reales es una de las
correcciones mas comunes en un code review.

SET — cuando lo que importa es "¿esto ya esta aqui?" (pertenencia) o
"eliminar duplicados", y el orden no importa. La razon es de
RENDIMIENTO, no solo de conveniencia: revisar in en un set es, en
promedio, un tiempo CONSTANTE sin importar cuantos elementos tenga
(esta indexado por hash); revisar in en una list recorre elemento por
elemento, y con miles de elementos la diferencia se nota de verdad. Es
por esto que en un algoritmo de recorrido (BFS/DFS sobre un grafo, por
ejemplo) el conjunto de "nodos ya visitados" casi siempre se implementa
como set, nunca como list.

DICT — cuando necesitas buscar un valor por una clave con SIGNIFICADO
(un id, un nombre, un codigo) en vez de por una posicion numerica que
hay que recordar. El error clasico de alguien que no ha interiorizado
esto es usar varias LISTAS PARALELAS para relacionar datos:
"""

names_parallel = ["Anna", "Bob", "Carol"]
ages_parallel = [25, 19, 32]
print(ages_parallel[names_parallel.index("Bob")])  # 19


"""
Esto funciona, pero es fragil: si una lista se ordena, se filtra, o se
le agrega un elemento sin actualizar la otra al mismo tiempo, los datos
quedan desincronizados sin que Python avise nada. La correccion
profesional es un dict (o una lista de dicts/dataclasses si hay varios
registros), donde la relacion entre los datos vive en la propia
estructura, no en que dos listas separadas "coincidan por indice":
"""

people_by_name = {"Anna": 25, "Bob": 19, "Carol": 32}
print(people_by_name["Bob"])  # 19


"""
Si de verdad ya tienes los datos repartidos en dos listas paralelas
(por ejemplo, porque asi llegaron de otro lado), el arreglo idiomatico
NO es escribir un loop a mano para combinarlas: zip() (visto en
09_iterables_y_comprehensions.py) mas dict() las junta en una sola
linea.
"""

people_by_name_zipped = dict(zip(names_parallel, ages_parallel))
print(people_by_name_zipped)  # {'Anna': 25, 'Bob': 19, 'Carol': 32}


"""
dict tambien es la base de patrones muy comunes en entrevistas y
codigo real: contar frecuencias, agrupar elementos, o usarse como
CACHE/memoizacion (guardar el resultado de un calculo costoso usando
sus argumentos como clave, para no recalcularlo si se vuelve a pedir).
collections.Counter y collections.defaultdict (ver
14_complementos_para_algoritmos.py) existen justamente para esos
patrones sin tener que escribir el dict a mano cada vez.

deque EN VEZ DE list PARA UNA COLA — mencionado en
14_complementos_para_algoritmos.py, pero vale la pena recalcarlo aqui:
list.pop(0) para desencolar es lento a proposito evitado en codigo
profesional cuando la cola puede crecer mucho, porque list esta
optimizada para agregar/quitar del FINAL (O(1)), no del inicio (donde
mover el hueco cuesta recorrer toda la lista). collections.deque esta
optimizada para ambos extremos por igual, por eso es la eleccion
correcta para colas reales (BFS, colas de tareas, etc.), no una list.
"""


"""
COMPLEJIDAD DE LAS OPERACIONES MAS COMUNES (referencia rapida)

Las razones de "por que" de arriba se resumen en esta tabla. O(1)
significa tiempo constante (no importa que tan grande sea la
coleccion); O(n) significa que el tiempo crece proporcional al tamaño:

    operacion                    list      dict/set     deque
    acceder por indice/clave     O(1)      O(1)          O(1) extremos
    agregar al FINAL              O(1)*     O(1)          O(1)
    agregar al INICIO              O(n)      O(1)          O(1)
    buscar con in                   O(n)      O(1)          O(n)
    eliminar del medio               O(n)      O(1)          O(n)

    (*) O(1) "amortizado": de vez en cuando list necesita pedir mas
    memoria y copiar todo, pero en promedio agregar al final es
    constante.

Esta tabla es la razon tecnica detras de CADA recomendacion dada
arriba: set/dict ganan en busqueda, deque gana en los extremos, list
gana en acceso por indice y en agregar al final.
"""


"""
dict VS dataclass/namedtuple PARA UN REGISTRO ESTRUCTURADO

Ambos guardan varios valores relacionados, pero la eleccion
profesional depende de si la FORMA del dato se conoce de antemano:

Usa dict cuando las claves son DINAMICAS o no se conocen al escribir
el codigo (datos que vienen de una API externa en JSON, un conteo de
frecuencias donde las claves salen de los datos mismos, configuracion
generica). Usa dataclass o namedtuple (ver
12_programacion_orientada_a_objetos.py) cuando la forma es FIJA y
conocida de antemano (un registro con siempre los mismos campos): el
editor autocompleta los nombres de campo, un typo en person.naem
truena de inmediato en vez de fallar en silencio como person["naem"]
en un dict (que simplemente lanza KeyError en tiempo de ejecucion, ya
tarde), y las anotaciones de tipo documentan la forma exacta del dato.
"""
