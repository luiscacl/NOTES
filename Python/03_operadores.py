"""
================================================================================
ARITMETICA Y MODULO MATH
================================================================================

[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Los operadores basicos +, -, *, / funcionan de la forma usual. La
division / entre dos numeros puede devolver un entero si el resultado
es exacto (10 / 5 da 2), o un decimal si no lo es.

EN PYTHON:
Los mismos operadores +, -, *, / existen y se escriben igual. La
division / SIEMPRE devuelve un float, incluso cuando el resultado es
exacto (10 / 5 da 2.0, no 2). Ademas existe un operador extra, //, que
no tiene equivalente en JS.

EN RESUMEN: +, -, *, / funcionan como siempre, pero recuerda que / en
Python siempre te da un float.
"""

print(10 + 3)  # 13
print(10 - 3)  # 7
print(10 * 3)  # 30
print(10 / 3)  # 3.3333333333333335
print(10 / 5)  # 2.0


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
No existe un operador de division entera dedicado; para lograr un
efecto parecido hay que usar Math.floor(a / b). El operador % (modulo)
con numeros negativos sigue el signo del DIVIDENDO.

EN PYTHON:
// es la DIVISION ENTERA (floor division): divide y redondea el
resultado hacia el infinito negativo, descartando cualquier parte
decimal. No es lo mismo que "truncar": con negativos, // redondea hacia
ABAJO, no hacia cero. El operador % tambien existe, pero con negativos
el signo del resultado sigue el signo del DIVISOR, lo cual puede dar un
resultado distinto al que darias por hecho viniendo de JS.

EN RESUMEN: usa // cuando quieras solo la parte entera de una division,
y ten cuidado con el signo de % si alguno de los numeros es negativo.
"""

print(10 // 3)  # 3
print(-10 // 3)  # -4
print(10 % 3)  # 1
print(-10 % 3)  # 2


"""
[IGUAL QUE JS]

** es el operador de potencia, igual que en JS desde ES2016 (antes de
eso JS solo tenia Math.pow()). En Python es la forma idiomatica de
elevar un numero a otro.

EN RESUMEN: ** eleva un numero a una potencia, por ejemplo 2 ** 10 da 1024.
"""

print(2 ** 10)  # 1024
print(9 ** 0.5)  # 3.0


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Existen los operadores ++ y -- para incrementar o decrementar una
variable en 1, ademas de los operadores compuestos +=, -=, *=, /=.

EN PYTHON:
No existen ++ ni --. Para incrementar o decrementar hay que reescribir
la variable completa (x = x + 1) o usar los operadores compuestos +=,
-=, *=, /=, //=, **=, que si existen y se comportan igual que en JS.

EN RESUMEN: para sumar o restar 1 a una variable, usa += 1 o -= 1; no
hay atajo de un solo caracter.
"""

counter = 0
counter += 1
counter -= 1
counter *= 5
counter /= 2
print(counter)  # 0.0

power = 2
power **= 4
print(power)  # 16

divided = 10
divided //= 3
print(divided)  # 3


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Funciones como min, max y round no existen como globales sueltas; viven
colgadas del objeto Math (Math.min, Math.max, Math.round).

EN PYTHON:
min, max, round, abs y pow son funciones GLOBALES, disponibles sin
necesidad de importar nada.

EN RESUMEN: estas funciones matematicas basicas estan siempre
disponibles, sin prefijo ni import.
"""

x, y, z = 3.42, 8, 12

print(min(x, y, z))  # 3.42
print(max(x, y, z))  # 12
print(round(x))  # 3
print(round(3.14159, 2))  # 3.14
print(abs(-7))  # 7
print(pow(x, 3))  # 40.001687999999994


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Para operaciones mas especificas se usa el objeto global Math
(Math.floor, Math.ceil, Math.sqrt, Math.PI), disponible siempre sin
necesidad de importarlo.

EN PYTHON:
El equivalente es el modulo math, pero hay que traerlo explicitamente
con import math antes de poder usarlo.

EN RESUMEN: import math te da floor, ceil, trunc, sqrt, pi y otras
funciones/constantes matematicas mas especificas.
"""

import math

print(math.floor(4.9))  # 4
print(math.ceil(4.1))  # 5
print(math.trunc(4.9))  # 4
print(math.trunc(-4.9))  # -4
print(math.sqrt(16))  # 4.0
print(math.pi)  # 3.141592653589793


"""
================================================================================
OPERADORES LOGICOS
================================================================================

[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Los operadores logicos son &&, || y !. Ademas existe una distincion
entre == (compara con coercion de tipos) y === (compara valor y tipo
sin coercion), por lo que casi siempre se recomienda usar === y !==
para evitar sorpresas.

EN PYTHON:
Los operadores logicos se escriben como palabras: and, or, not. No
existe una version "estricta" separada de == y != porque Python no
tiene coercion de tipos rara que evitar: == y != ya comparan valor Y
tipo al mismo tiempo, siempre.

EN RESUMEN: usa and/or/not en vez de &&/||/!, y usa == y != sin
preocuparte por una version "estricta" aparte.

    JS    &&    ||    !      ===   !==
    Py    and   or    not    ==    !=
"""

a, b = 3, 7

if a > 0 and b > 0:
    print("both positive")  # both positive

if a > 100 or b > 0:
    print("at least one is true")  # at least one is true

if not a > 100:
    print("a is not greater than 100")  # a is not greater than 100

print("5" == 5)  # False


"""
[IGUAL QUE JS]

& y | SI existen en Python, pero son operadores BIT A BIT (bitwise), no
logicos. Compilan sin error dentro de un if, pero por precedencia de
operadores dan resultados incorrectos si los usas pensando que son
and/or. No son un reemplazo valido de and/or para logica booleana
normal (en pandas/numpy se usan asi sobre arrays, pero es una convencion
propia de esas librerias, no del lenguaje base).

Los operadores bit a bit completos son & (and), | (or), ^ (xor), ~
(negacion), << (recorrer bits a la izquierda) y >> (recorrer bits a la
derecha). << n equivale a multiplicar por 2 elevado a n; >> n equivale a
dividir entre 2 elevado a n, descartando el residuo. Son los mismos
operadores y el mismo significado que en JS.

EN RESUMEN: & y | manipulan bits individuales, no son un atajo para
and/or.
"""

print(4 << 1)  # 8
print(4 >> 1)  # 2
print(5 & 3)  # 1
print(5 | 2)  # 7
print(5 ^ 1)  # 4
print(~4)  # -5


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Los valores FALSY son: false, null, undefined, 0, -0, NaN, "" (string
vacio). Cualquier objeto o array, incluso vacio, es TRUTHY.

EN PYTHON:
TRUTHINESS es que cuenta como True o False dentro de un if u otra
evaluacion booleana, sin ser literalmente un bool. Son FALSY: False,
None, 0, 0.0, "" (string vacio), [], {}, set(), (). Todo lo demas es
TRUTHY, incluyendo strings como "False" o "0" (por tener contenido,
aunque ese contenido "parezca" un valor falso).

EN RESUMEN: dentro de un if, ademas de False/None/0, las colecciones
vacias tambien cuentan como falsas. Esta es la trampa mas comun
viniendo de JS (donde [] y {} SIEMPRE son truthy) y produce bugs
silenciosos si no se tiene presente.
"""

if []:
    print("not printed")  # no se ejecuta: [] es falsy
else:
    print("empty list -> False")  # empty list -> False

if [1, 2]:
    print("list with elements -> True")  # list with elements -> True

if "":
    print("not printed")  # no se ejecuta: "" es falsy
else:
    print("empty string -> False")  # empty string -> False


"""
[SIMILAR A JS, CON DIFERENCIAS]

and / or DEVUELVEN UN OPERANDO, NO SOLO True/False

EN JS:
&& y || tambien devuelven uno de los dos operandos, no un bool puro: &&
devuelve el primer operando falsy que encuentra (o el ultimo, si todos
son truthy); || devuelve el primer operando truthy que encuentra (o el
ultimo, si todos son falsy). Ambos son de EVALUACION CORTA
(short-circuit): en cuanto el resultado ya quedo decidido, el operando
de la derecha ni se evalua. Por eso || y ?? se usan tanto para dar
valores por defecto.

EN PYTHON:
and y or se comportan igual que && y ||: and devuelve el primer
operando FALSY que encuentra (o el ultimo, si todos son truthy); or
devuelve el primer operando TRUTHY que encuentra (o el ultimo, si todos
son falsy). Tambien son de evaluacion corta, sin evaluar el operando de
la derecha si ya no hace falta.

EN RESUMEN: and/or no siempre dan True/False, dan uno de los dos
valores originales; por eso or es la forma comun de poner un valor por
defecto.
"""

print(0 and "not reached")  # 0
print(3 and "reached")  # reached
print("" or "default value")  # default value
print("real data" or "default value")  # real data

username = ""
final_name = username or "guest"
print(final_name)  # guest


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

== vs is

== compara VALOR (si el contenido es equivalente). is compara IDENTIDAD:
si dos nombres apuntan exactamente al mismo objeto en memoria, no solo a
un valor parecido. JS no tiene esta distincion en sus operadores de
comparacion.

Para comparar con None siempre se usa is / is not por convencion (y
porque None es un unico objeto compartido en todo el programa, nunca
hay una "copia" de None).

EN RESUMEN: usa == para comparar valores normales, y usa is solo para
None (o para verificar si dos variables son literalmente el mismo
objeto).
"""

x = None

if x is None:
    print("x is None")  # x is None

list_a = [1, 2, 3]
list_b = [1, 2, 3]
print(list_a == list_b)  # True
print(list_a is list_b)  # False


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

COMPARACIONES ENCADENADAS

Python permite escribir varias comparaciones seguidas en una sola
expresion, uniendolas todas con and de forma implicita: 0 < n < 10
equivale a (0 < n) and (n < 10). JS no soporta esto: escribir
0 < n < 10 en JS se evalua de izquierda a derecha como (0 < n) < 10, es
decir compara un bool contra 10, lo cual NO es lo mismo que "n esta
entre 0 y 10" y es una fuente de bugs silenciosos en JS.

EN RESUMEN: puedes escribir 0 < n < 10 directamente en vez de
0 < n and n < 10.
"""

n = 5

if 0 < n < 10:
    print("n is between 0 and 10")  # n is between 0 and 10

if 0 <= n <= 10 != 100:
    print("you can chain as many comparisons as you need")  # you can chain as many comparisons as you need


"""
================================================================================
OPERADORES DE MEMBRESIA: in / not in
================================================================================

[SIMILAR A JS, CON DIFERENCIAS]

in revisa si un valor esta contenido en un iterable: funciona en
strings, listas, tuplas, sets y diccionarios (donde revisa las CLAVES
por defecto, no los valores). JS SI tiene una palabra clave in, pero
hace algo distinto: revisa si existe una CLAVE o INDICE en un objeto o
array (por ejemplo, 2 in [10, 20, 30] pregunta si el indice 2 existe,
no si el valor 2 esta presente), y ademas no funciona con strings
(lanza TypeError). El verdadero equivalente al in de Python para buscar
un VALOR dentro de una coleccion es .includes() en JS.

EN RESUMEN: usa in para preguntar si un valor esta dentro de una
coleccion, y not in para lo contrario.
"""

numbers = [1, 2, 3, 4, 5]

print(3 in numbers)  # True
print(10 in numbers)  # False
print(10 not in numbers)  # True

print("a" in "banana")  # True
print("xyz" in "banana")  # False

person = {"name": "Ann", "age": 25}
print("name" in person)  # True
print("Ann" in person)  # False
print("Ann" in person.values())  # True


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

Para un set, in es especialmente rapido (el set esta indexado por hash,
sin necesitar revisar elemento por elemento como en una lista). Esa es
una razon comun para convertir una lista a set antes de hacer muchas
busquedas repetidas con in, cuando el orden de los elementos no importa.

EN RESUMEN: si vas a hacer muchas busquedas con in y el orden no
importa, conviertelo a set primero para que sea mas rapido.
"""

colors_list = ["red", "green", "blue"]
colors_set = set(colors_list)

print("green" in colors_list)  # True
print("green" in colors_set)  # True
