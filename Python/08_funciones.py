"""
================================================================================
FUNCIONES
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Una funcion se puede definir con function, con una function expression,
o con una arrow function.

EN PYTHON:
Se definen con def. No hay arrow function ni function expression para
el caso basico; def siempre crea una funcion con nombre, y el cuerpo es
el bloque indentado debajo de la linea def.

EN RESUMEN: Python tiene una sola forma basica de definir funciones (def
con nombre), en vez de las varias formas de JS.
"""

def greet():
    print("hello")  # hello

greet()


"""
[IGUAL QUE JS]
ORDEN DE EJECUCION: definir una funcion con def NO ejecuta su cuerpo
todavia; solo lo registra, listo para usarse. El cuerpo se ejecuta hasta
que la funcion se LLAMA (greet() con parentesis). Llamarla salta la
ejecucion al primer renglon de su cuerpo, ejecuta ese bloque de arriba
hacia abajo, y al terminar (o al llegar a un return) regresa exactamente
al punto donde se hizo la llamada, para seguir ahi con la linea
siguiente.

Los parametros no llevan tipo declarado (a menos que se agreguen
anotaciones opcionales, ver mas abajo). return corta la ejecucion de la
funcion en ese punto y entrega ese valor a quien la llamo. Si no hay
return explicito, la funcion devuelve None.

EN RESUMEN: el flujo de definir, llamar y retornar es identico al de
JS; solo cambia la sintaxis (def en vez de function).
"""

def add(a, b):
    return a + b

result = add(3, 4)
print(result)  # 7

def no_return():
    print("returns nothing explicit")  # returns nothing explicit

value = no_return()
print(value)  # None


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Una funcion puede devolver varios valores separados por coma: en
realidad esta devolviendo una TUPLA (ver 07_colecciones.py), que se
puede desempaquetar directo en la asignacion.

EN RESUMEN: para lograr algo parecido, JS necesita devolver un array o
un objeto y desestructurarlo; Python lo hace de forma nativa con la
tupla implicita.
"""

def divide(a, b):
    return a // b, a % b

quotient, remainder = divide(17, 5)
print(quotient, remainder)  # 3 2


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
ANOTACIONES DE TIPO (type hints): se pueden agregar como pista de
documentacion sobre que tipos espera y devuelve la funcion, pero Python
NO las verifica en tiempo de ejecucion: no truenan si les pasas un tipo
distinto. Sirven para que el editor y herramientas externas (como mypy)
detecten errores antes de correr el programa, pero el interprete las
ignora en la practica.

EN RESUMEN: son puramente informativas, a diferencia de TypeScript,
donde los tipos si se verifican (en tiempo de compilacion).
"""

def multiply(a: int, b: int) -> int:
    return a * b

print(multiply(2, 3))  # 6
print(multiply("ab", 3))  # ababab


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Cuando un valor puede ser de mas de un tipo, o puede ser None, se anota
con | entre los tipos posibles (Python 3.10+), o con Optional/Union del
modulo typing en versiones anteriores.

EN RESUMEN: es el equivalente a los tipos union de TypeScript (string |
null), pero recordando que en Python estas anotaciones no se verifican
en tiempo de ejecucion.
"""

def find_user(user_id: int) -> str | None:
    if user_id == 1:
        return "Ann"
    return None

print(find_user(1))  # Ann
print(find_user(99))  # None

from typing import Optional, Union

def format_price(value: Union[int, float]) -> str:
    return f"${value:.2f}"

def get_nickname(name: Optional[str] = None) -> str:
    return name or "anonymous"

print(format_price(19.9))  # $19.90
print(get_nickname())  # anonymous


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Docstring: una funcion puede llevar un string como PRIMERA linea de su
cuerpo para documentarse a si misma. Se accede despues con
nombre_funcion.__doc__, y es lo que muestran las herramientas de ayuda.

EN RESUMEN: es documentacion integrada al lenguaje, distinta de un
comentario normal porque queda accesible en tiempo de ejecucion.
"""

def circle_area(radius):
    """Devuelve el area de un circulo dado su radio."""
    return 3.14159 * radius ** 2

print(circle_area.__doc__)  # Devuelve el area de un circulo dado su radio.


"""
================================================================================
DEFAULT ARGUMENTS (VALORES POR DEFECTO)
================================================================================
[IGUAL QUE JS]

Un parametro puede tener un valor por defecto que se usa si la llamada
no le pasa argumento para el.

EN RESUMEN: funciona igual que los default parameters de JS.
"""

def greet_with_name(name="guest"):
    print(f"hello {name}")  # hello guest / hello Ann

greet_with_name()
greet_with_name("Ann")


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
CUIDADO: usar un objeto MUTABLE (lista, dict) como valor por defecto es
un error clasico de Python. El valor por defecto se crea UNA sola vez,
en el momento en que se define la funcion (no cada vez que se llama), y
esa misma lista se COMPARTE entre todas las llamadas que no pasen su
propio argumento para ese parametro.

EN RESUMEN: cada llamada sin argumento reutiliza y modifica el MISMO
objeto por defecto, en vez de recibir uno nuevo cada vez.
"""

def add_bad(item, items=[]):
    items.append(item)
    return items

print(add_bad("a"))  # ['a']
print(add_bad("b"))  # ['a', 'b']


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
La forma correcta es usar None como valor por defecto, y crear la lista
nueva DENTRO del cuerpo de la funcion si no llego ninguna.

EN RESUMEN: asi cada llamada sin argumento recibe su propia lista nueva,
en vez de compartir una sola entre todas las llamadas.
"""

def add_good(item, items=None):
    if items is None:
        items = []
    items.append(item)
    return items

print(add_good("a"))  # ['a']
print(add_good("b"))  # ['b']


"""
================================================================================
KEYWORD ARGUMENTS
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Al llamar una funcion, los argumentos se pueden pasar por POSICION (el
orden en que aparecen importa) o por NOMBRE (una vez que nombras el
parametro, el orden ya no importa).

EN RESUMEN: JS no tiene esto de forma nativa para funciones normales; lo
mas parecido es desestructurar un objeto de opciones como unico
parametro.
"""

def describe_person(name, age, city):
    print(f"{name}, {age} years, {city}")  # Ann, 25 years, NYC

describe_person("Ann", 25, "NYC")
describe_person(age=25, city="NYC", name="Ann")
describe_person("Ann", city="NYC", age=25)


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
BUENA PRACTICA: en codigo profesional, si una funcion tiene mas de 2 o
3 parametros (o varios del MISMO tipo, como aqui dos strings seguidos),
se llama con keyword arguments aunque no sea obligatorio. La razon es
real, no solo estilo: describe_person("Ann", 25, "NYC") obliga a quien
LEE la llamada a recordar el orden exacto de la definicion para saber
que es cada valor; un error como invertir "NYC" y "Ann" no lo detecta
Python (ambos son str), y el bug pasa silencioso. Con
describe_person(name="Ann", age=25, city="NYC") el error es imposible
de cometer sin darse cuenta, porque cada valor lleva su etiqueta.
"""

describe_person("NYC", 25, "Ann")  # NYC, 25 years, Ann (bug: name y city invertidos, Python no lo detecta)
describe_person(name="Ann", age=25, city="NYC")  # Ann, 25 years, NYC (con keywords el error es imposible)


"""
================================================================================
*ARGS Y **KWARGS
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El operador rest (...args) agrupa cualquier cantidad de argumentos
extra en un array dentro de la funcion.

EN PYTHON:
*args recibe cualquier cantidad de argumentos POSICIONALES extra y los
agrupa en una tupla dentro de la funcion. El nombre "args" es solo
convencion (lo que de verdad importa sintacticamente es el asterisco);
podria llamarse *cualquier_cosa.

EN RESUMEN: cumplen el mismo papel, pero Python agrupa en una tupla
(inmutable) y JS en un array (mutable).
"""

def sum_all(*args):
    print(type(args))  # <class 'tuple'>
    return sum(args)

print(sum_all(1, 2, 3, 4))  # 10


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
No hay un equivalente directo a **kwargs; lo mas cercano sigue siendo el
operador rest (...args), que no distingue posicionales de nombrados.

EN PYTHON:
**kwargs recibe cualquier cantidad de argumentos POR NOMBRE extra y los
agrupa en un diccionario dentro de la funcion.

EN RESUMEN: Python separa explicitamente "extra posicionales" (*args) de
"extra por nombre" (**kwargs); JS los junta todos en un solo array rest.
"""

def show_info(**kwargs):
    print(type(kwargs))  # <class 'dict'>
    for key, value in kwargs.items():
        print(key, value)  # name Ann / age 25 / city NYC

show_info(name="Ann", age=25, city="NYC")


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Se pueden combinar todos en un mismo def, siempre respetando este orden:
parametros normales, *args, parametros con valor por defecto, **kwargs.

EN RESUMEN: Python define un orden estricto para mezclar estos cuatro
tipos de parametros en una sola firma de funcion.
"""

def full_function(a, b, *args, c=10, **kwargs):
    print(a, b, args, c, kwargs)  # 1 2 (3, 4) 99 {'extra': 'data'}

full_function(1, 2, 3, 4, c=99, extra="data")


"""
================================================================================
DESEMPAQUETAR ARGUMENTOS AL LLAMAR (* Y ** EN LA LLAMADA)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El operador spread esparce un array como los argumentos de una llamada:
fn(...arr).

EN PYTHON:
* y ** tambien se usan en el sentido CONTRARIO a *args/**kwargs: no
solo para RECIBIR varios argumentos agrupados, sino para ESPARCIR una
lista o diccionario ya existente como los argumentos de una llamada. *
esparce una lista/tupla como argumentos posicionales, y ** esparce un
diccionario como argumentos por nombre.

EN RESUMEN: * en una llamada es el equivalente exacto al spread de JS;
** no tiene equivalente en JS porque JS no distingue argumentos
nombrados.
"""

def sum_three(a, b, c):
    return a + b + c

values = [1, 2, 3]
print(sum_three(*values))  # 6

data = {"a": 10, "b": 20, "c": 30}
print(sum_three(**data))  # 60


"""
================================================================================
PARAMETROS SOLO-POSICIONALES Y SOLO-POR-NOMBRE
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Se puede forzar como se debe llamar a un parametro usando dos
separadores especiales en la definicion. Todo lo que va DESPUES de un *
suelto debe pasarse por nombre obligatoriamente; todo lo que va ANTES
de un / suelto debe pasarse por posicion obligatoriamente.

EN RESUMEN: JS no tiene una forma de forzar esto para funciones
normales; en Python es parte de la sintaxis de def.
"""

def create_user(name, *, age, active=True):
    print(name, age, active)  # Ann 25 True

create_user("Ann", age=25)

def strict_divide(a, b, /):
    return a / b

print(strict_divide(10, 2))  # 5.0


"""
================================================================================
FUNCIONES LAMBDA
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Una arrow function puede tener un cuerpo de una sola expresion (con
retorno implicito) o un cuerpo con llaves de varias instrucciones (con
return explicito).

EN PYTHON:
Una lambda es una funcion anonima de UNA SOLA EXPRESION: el cuerpo NO
puede tener varias instrucciones ni un return explicito. Es solo una
expresion, y su resultado se devuelve automaticamente sin escribir la
palabra return.

EN RESUMEN: la lambda de Python es mas limitada que la arrow function de
JS; solo cubre el caso de una sola expresion con retorno implicito.
"""

square = lambda x: x ** 2
print(square(5))  # 25

add = lambda a, b: a + b
print(add(3, 4))  # 7


"""
    JS:  const square = x => x ** 2;
    Py:  square = lambda x: x ** 2
"""


"""
[SIMILAR A JS, CON DIFERENCIAS]
Las lambdas se usan casi siempre como argumento de OTRA funcion, cuando
escribir una funcion completa con def seria excesivo para una operacion
tan corta. Los casos mas comunes son sorted() (con el parametro key),
map() y filter().

EN RESUMEN: es el mismo uso que las arrow functions cortas en JS pasadas
a .sort(), .map() o .filter(), pero con las funciones equivalentes de
Python.
"""

people = [("Ann", 25), ("Bob", 19), ("Carol", 32)]

people.sort(key=lambda person: person[1])
print(people)  # [('Bob', 19), ('Ann', 25), ('Carol', 32)]

numbers = [1, 2, 3, 4, 5]

doubles = list(map(lambda n: n * 2, numbers))
evens = list(filter(lambda n: n % 2 == 0, numbers))

print(doubles)  # [2, 4, 6, 8, 10]
print(evens)  # [2, 4]


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Las arrow functions se usan todo el tiempo, incluso para funciones
largas con varias instrucciones.

EN PYTHON:
Fuera del caso de "funcion de una linea pasada como argumento", una
funcion normal con def suele ser mas clara y es la forma preferida en la
mayoria del codigo Python.

EN RESUMEN: Python reserva la lambda para casos muy cortos; JS usa la
arrow function como estilo por defecto incluso en funciones largas.
"""


"""
================================================================================
functools.reduce
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

EN JS:
Array.reduce() es un metodo directo de cualquier array.

EN PYTHON:
reduce() NO es built-in (a proposito, para no tentar a abusar de el
donde un for normal se lee mas claro); vive en el modulo functools.
Combina todos los elementos de un iterable en un solo valor, aplicando
una funcion de dos argumentos acumulada de izquierda a derecha.

EN RESUMEN: hacen lo mismo, pero en JS es un metodo del array y en
Python es una funcion aparte que hay que importar.
"""

from functools import reduce

numbers = [1, 2, 3, 4, 5]

total_sum = reduce(lambda accumulator, current: accumulator + current, numbers)
print(total_sum)  # 15


"""
    JS:  numbers.reduce((accumulator, current) => accumulator + current, 0)
    Py:  reduce(lambda accumulator, current: accumulator + current, numbers, 0)

[IGUAL QUE JS]
reduce() acepta un tercer argumento opcional como valor inicial del
acumulador.

EN RESUMEN: cumple el mismo papel que el segundo argumento de .reduce()
en JS.
"""

sum_with_initial = reduce(lambda accumulator, current: accumulator + current, numbers, 100)
print(sum_with_initial)  # 115
