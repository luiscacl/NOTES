"""
================================================================================
IF STATEMENTS
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
if usa parentesis alrededor de la condicion y llaves para delimitar el
bloque: if (condicion) { ... }. Para encadenar mas condiciones se usa la
palabra clave "else if".

EN PYTHON:
El if no lleva parentesis ni llaves: se abre con dos puntos y el bloque
se delimita con indentacion (ver 01_sintaxis_basica.py). No existe la
palabra "else if" — la palabra correcta es elif.

EN RESUMEN: if/elif/else en Python se escribe sin parentesis ni llaves,
usando dos puntos e indentacion en vez de else if.
"""

x = 10

if x > 5:
    print("greater than 5")  # greater than 5
elif x == 5:
    print("is 5")
else:
    print("less than 5")


"""
[IGUAL QUE JS]

ORDEN DE EJECUCION de un if/elif/else: las condiciones se evaluan EN
ORDEN, de arriba hacia abajo, y se ejecuta el bloque de la PRIMERA que
sea True; en cuanto una condicion resulta True, ninguna condicion ni
bloque de abajo se vuelve a evaluar (no hay fallthrough). Si ninguna
condicion de los if/elif fue True, se ejecuta el else (si existe); si
no hay else y ninguna condicion se cumplio, sencillamente no se ejecuta
nada.

Con x = 10 arriba: primero se evalua x > 5 (True), se imprime "greater
than 5", y ahi termina el if completo — ni x == 5 ni el else se llegan
a mirar.

EN RESUMEN: se evaluan las condiciones de arriba hacia abajo y solo se
ejecuta el bloque de la primera que resulte verdadera.
"""


"""
[IGUAL QUE JS]

Un if puede ir sin elif ni else, y se puede anidar un if dentro de otro
(un bloque anidado solo necesita un nivel extra de indentacion). El
anidado se evalua de afuera hacia adentro: primero la condicion externa,
y solo si esa es True se entra a evaluar la condicion interna.

EN RESUMEN: un if anidado solo se evalua si la condicion del if externo
que lo contiene fue verdadera.
"""

age = 20
has_id = True

if age >= 18:
    if has_id:
        print("can enter")  # can enter
    else:
        print("missing identification")


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Cuando el bloque if es una sola instruccion corta, es comun escribirla
en la misma linea sin llaves: if (condicion) instruccion;.

EN PYTHON:
El bloque if tambien se puede escribir en la misma linea que el if
cuando es una sola instruccion corta. Es valido pero no es la
convencion recomendada (PEP8 prefiere siempre el bloque indentado en su
propia linea).

EN RESUMEN: se puede escribir el if en una sola linea, pero en Python lo
usual es usar el bloque indentado en su propia linea.
"""

if x > 0: print("positive")  # positive


"""
================================================================================
CONDITIONAL EXPRESSIONS (OPERADOR TERNARIO)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El operador ternario se escribe: condicion ? valorSiTrue : valorSiFalse.
Primero va la condicion, y despues los dos valores posibles separados
por ? y :.

EN PYTHON:
La misma idea se expresa con palabras y en OTRO ORDEN: primero el valor
si la condicion es verdadera, luego la condicion, luego el valor si es
falsa: valorSiTrue if condicion else valorSiFalse.

EN RESUMEN: el ternario existe en los dos lenguajes, pero el orden de
sus partes es distinto.
"""

age = 20

status = "adult" if age >= 18 else "minor"
print(status)  # adult


"""
[IGUAL QUE JS]

El ternario es una EXPRESION (produce un valor en el momento), no una
instruccion de control de flujo como el if normal: se puede usar dentro
de un print, como argumento de una funcion, o asignada directo a una
variable, tal como se ve arriba. El orden de evaluacion es: primero se
evalua la condicion (age >= 18); si es True, el valor completo de la
expresion es lo que esta antes del if; si es False, es lo que esta
despues del else.

EN RESUMEN: el ternario produce un valor que se puede usar donde sea que
se necesite una expresion.
"""

print("even" if age % 2 == 0 else "odd")  # even


"""
[IGUAL QUE JS]

Se pueden encadenar varios ternarios para expresar varias condiciones
seguidas, aunque conviene no abusar porque se vuelve dificil de leer
rapido.

EN RESUMEN: encadenar ternarios funciona, pero mas de uno o dos vuelve
la linea dificil de leer.
"""

grade = 85
letter = "A" if grade >= 90 else "B" if grade >= 80 else "C"
print(letter)  # B


"""
================================================================================
MATCH-CASE
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
switch compara un valor contra cada case. Si un case no termina en
break, la ejecucion sigue cayendo a los casos siguientes (fallthrough),
por lo que hace falta poner break al final de cada uno para evitarlo.
default cubre el caso en que ningun case coincidio.

EN PYTHON:
match-case (agregado en Python 3.10) es el equivalente a switch. NO hay
fallthrough, asi que no se necesita break al final de cada caso — cada
case termina solo en cuanto su bloque acaba de ejecutarse. El patron _
funciona como el default de JS, y por convencion siempre va al final.

EN RESUMEN: match-case compara un valor contra varios casos y ejecuta
solo el bloque del primero que coincide, sin necesidad de break.
"""

day = 3

match day:
    case 1:
        print("monday")
    case 2:
        print("tuesday")
    case 3:
        print("wednesday")  # wednesday
    case _:
        print("other day")


"""
[IGUAL QUE JS]

ORDEN DE EJECUCION: igual que un if/elif, match compara el valor contra
cada case EN ORDEN, de arriba hacia abajo, y ejecuta el bloque del
PRIMERO que haga match; los case restantes ni se revisan (sin
fallthrough — esa diferencia con el switch de JS ya se explico arriba).

Con day = 3 arriba: se descartan case 1 y case 2, case 3 hace match e
imprime "wednesday", y ahi termina — case _ ni se llega a mirar.

EN RESUMEN: match evalua los case en orden y se detiene en el primero
que coincide, igual que if/elif.
"""


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Para que varios valores compartan el mismo bloque, se escriben varios
case seguidos sin break entre ellos (fallthrough intencional), y el
bloque real queda en el ultimo de esos case.

EN PYTHON:
Un mismo case puede agrupar varios valores separados por |, en una sola
linea explicita, sin depender de la ausencia de break.

EN RESUMEN: para que varios valores compartan un mismo bloque, en Python
se listan juntos en un solo case con |.
"""

month = 12

match month:
    case 12 | 1 | 2:
        print("winter")  # winter
    case 3 | 4 | 5:
        print("spring")
    case _:
        print("other season")


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

match-case tambien hace "pattern matching" ESTRUCTURAL: puede des-armar
listas, tuplas y diccionarios directamente dentro del patron del case,
extrayendo sus valores en variables nuevas. El switch de JS solo compara
un valor simple contra otro, asi que no tiene nada parecido a esto.

EN RESUMEN: el patron de un case puede desarmar una estructura de datos
y capturar sus partes en variables, todo en la misma linea del case.
"""

point = (0, 5)

match point:
    case (0, 0):
        print("origin")
    case (0, y):
        print(f"on the y axis, at {y}")  # on the y axis, at 5
    case (x, 0):
        print(f"on the x axis, at {x}")
    case (x, y):
        print(f"point at ({x}, {y})")


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

Un case tambien puede llevar una condicion extra con if, llamada
"guard": el case solo hace match si ademas de coincidir el patron, esa
condicion adicional es True. El switch de JS no tiene equivalente a
esto.

EN RESUMEN: un guard agrega una condicion adicional que debe cumplirse
para que ese case sea el elegido.
"""

age = 25

match age:
    case n if n < 13:
        print("child")
    case n if n < 18:
        print("teenager")
    case _:
        print("adult")  # adult
