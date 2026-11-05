"""
================================================================================
WHILE LOOPS
================================================================================
[IGUAL QUE JS]

El bloque se repite MIENTRAS la condicion sea True. La sintaxis usa el
patron ya conocido: dos puntos + indentacion en vez de parentesis y
llaves.

ORDEN DE EJECUCION: antes de CADA repeticion, Python evalua la
condicion; si es True ejecuta el bloque completo y vuelve a evaluar la
condicion; en cuanto la condicion da False, el loop termina y la
ejecucion continua en la linea siguiente al bloque. Si la condicion es
False desde la primera vez, el bloque no se ejecuta ni una sola vez.

EN RESUMEN: el while de Python se comporta igual que el de JS; solo
cambia la sintaxis de bloque.
"""

counter = 0

while counter < 5:
    print(counter)
    counter += 1
# Salida: 0 / 1 / 2 / 3 / 4 (una linea por cada vuelta)


"""
[IGUAL QUE JS]
break saca la ejecucion del loop por completo de inmediato (sin volver
a evaluar la condicion); continue corta la iteracion actual ahi mismo y
salta directo a evaluar la condicion de nuevo, sin ejecutar el resto
del bloque en esa vuelta.

EN RESUMEN: break y continue funcionan exactamente igual que en JS.
"""

n = 0

while True:
    if n == 3:
        break
    print(n)
    n += 1
# Salida: 0 / 1 / 2 (se corta antes de imprimir 3 por el break)

n = 0

while n < 5:
    n += 1
    if n % 2 == 0:
        continue
    print(n)
# Salida: 1 / 3 / 5 (los pares se saltan con continue)


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
El while puede llevar un else. Ese else se ejecuta cuando el loop
termina de forma NORMAL (la condicion se volvio False por si sola),
pero se SALTA por completo si el loop termino por un break.

EN RESUMEN: while...else es una sintaxis exclusiva de Python; el else
corre solo si el loop no fue interrumpido por un break.
"""

attempts = 0

while attempts < 3:
    print("trying...")
    attempts += 1
else:
    print("ran out of attempts")
# Salida: "trying..." (x3), luego "ran out of attempts" (el while
# termino solo, sin break, asi que el else si se ejecuta)


"""
================================================================================
FOR LOOPS
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Existen dos estilos de for: el for...of, que recorre los elementos de
un iterable uno por uno, y el for clasico con contador e indice manual
(for (let i = 0; i < n; i++)).

EN PYTHON:
El for siempre recorre los ELEMENTOS de algo iterable, uno por uno — es
el equivalente al for...of de JS. El for clasico con contador e indice
manual no existe como tal; para lograr lo mismo se usa range() (ver
abajo).

EN RESUMEN: el for de Python equivale al for...of de JS; el for con
contador estilo C no existe en Python y se reemplaza con range().
"""

fruits = ["apple", "pear", "grape"]

for fruit in fruits:
    print(fruit)
# Salida: apple / pear / grape


"""
[IGUAL QUE JS]
ORDEN DE EJECUCION: Python le pide al iterable un valor a la vez (de
principio a fin), asigna ese valor a la variable del for (fruit en el
ejemplo), ejecuta el bloque completo con ese valor, y repite con el
siguiente elemento; cuando ya no quedan elementos, el loop termina solo,
sin necesitar una condicion de corte explicita como en while.

EN RESUMEN: el for recorre el iterable de principio a fin y termina
solo, igual que el for...of de JS.
"""


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Para generar una secuencia de numeros se suele usar
Array.from({length: n}, (_, i) => i), pero eso construye el array
COMPLETO en memoria de una sola vez (no es perezoso/bajo demanda); el
for clasico con contador si evita crear un array, porque simplemente
cuenta sin guardar nada en una coleccion.

EN PYTHON:
range() genera una secuencia de numeros bajo demanda, de forma
eficiente en memoria (no crea una lista completa de antemano), con
esta sintaxis:

range(fin)                empieza en 0, sin incluir "fin"
range(inicio, fin)         incluye "inicio", sin incluir "fin"
range(inicio, fin, paso)   agrega un paso (puede ser negativo)

EN RESUMEN: range() es la forma en que Python genera secuencias de
numeros para iterar sin crear una coleccion completa de antemano; en JS
el equivalente perezoso seria un for con contador (Array.from, en
cambio, si crea el array completo de inmediato, igual que list(range(n))
en Python).
"""

for i in range(5):
    print(i)
# Salida: 0 / 1 / 2 / 3 / 4

for i in range(2, 8):
    print(i)
# Salida: 2 / 3 / 4 / 5 / 6 / 7

for i in range(0, 10, 2):
    print(i)
# Salida: 0 / 2 / 4 / 6 / 8

for i in range(10, 0, -1):
    print(i)
# Salida: 10 / 9 / 8 / 7 / 6 / 5 / 4 / 3 / 2 / 1


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
enumerate() entrega INDICE y VALOR al mismo tiempo en cada vuelta,
evitando tener que hacer range(len(lista)) y despues indexar
manualmente dentro del loop para saber en que posicion vas.

EN RESUMEN: enumerate() da indice y valor juntos en cada vuelta, sin
tener que armarlo a mano.
"""

for index, fruit in enumerate(fruits):
    print(index, fruit)
# Salida: "0 apple" / "1 pear" / "2 grape"


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El for...of tambien puede desestructurar cada elemento directo en la
declaracion, por ejemplo for (const [x, y] of coordinates).

EN PYTHON:
Cuando cada elemento del iterable YA es una tupla (o una lista de
tamaño fijo), for puede DESEMPAQUETARLA directo en variables separadas
en la misma linea del for, sin pedir cada posicion a mano — la misma
idea de la asignacion multiple (ver 01_sintaxis_basica.py) aplicada
elemento por elemento del loop.

EN RESUMEN: ambos lenguajes permiten desempaquetar/desestructurar los
elementos directo en el for; la sintaxis cambia pero la idea es la
misma.
"""

coordinates = [(0, 0), (1, 5), (3, 2)]

for x, y in coordinates:
    print(f"x={x}, y={y}")
# Salida: "x=0, y=0" / "x=1, y=5" / "x=3, y=2"


"""
[IGUAL QUE JS]
break y continue funcionan exactamente igual que en while.

EN RESUMEN: el comportamiento de break y continue es el mismo dentro
de un for que dentro de un while, y es igual al de JS.
"""

for number in range(10):
    if number == 5:
        break
    if number % 2 == 0:
        continue
    print(number)
# Salida: 1 / 3 (0, 2 y 4 se saltan con continue; el loop se corta
# justo en 5 por el break)


"""
================================================================================
NESTED LOOPS (LOOPS ANIDADOS)
================================================================================
[IGUAL QUE JS]

Un loop dentro de otro funciona igual que en JS, solo cambia la sintaxis
de siempre: el loop interno necesita un nivel extra de indentacion
respecto al externo.

ORDEN DE EJECUCION: por cada vuelta del loop EXTERNO, el loop INTERNO se
ejecuta completo de principio a fin antes de que el externo avance a su
siguiente vuelta. Es decir, si el externo tiene 3 vueltas y el interno 3
vueltas, el bloque mas interno termina ejecutandose 3 x 3 = 9 veces en
total.

EN RESUMEN: los loops anidados funcionan igual que en JS; solo cambia
la indentacion en vez de las llaves.
"""

for i in range(3):
    for j in range(3):
        print(i, j)
# Salida (9 lineas): "0 0" / "0 1" / "0 2" / "1 0" / "1 1" / "1 2" /
# "2 0" / "2 1" / "2 2"


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
console.log() siempre agrega un salto de linea al final y no tiene una
opcion integrada para quitarlo; para imprimir sin salto de linea hay
que usar process.stdout.write() en su lugar.

EN PYTHON:
Ejemplo clasico: imprimir una figura combinando dos for. El loop externo
controla las filas; el interno controla lo que se imprime en cada fila
antes de saltar de linea. print() por defecto agrega un salto de linea
al final; end="" se lo quita para poder seguir imprimiendo en la misma
linea.

EN RESUMEN: print() puede evitar el salto de linea con end=""; en JS
eso requiere cambiar de funcion (a process.stdout.write).
"""

rows = 4

for i in range(1, rows + 1):
    for j in range(i):
        print("*", end="")
    print()
# Salida (figura resultante):
# *
# **
# ***
# ****


"""
[IGUAL QUE JS]
break y continue solo afectan al loop MAS INTERNO en el que estan
escritos: un break dentro del loop interno no saca del loop externo,
solo del interno. En JS ocurre lo mismo cuando no se usan labels; JS
tiene labels para que un break o continue apunten directo a un loop
externo especifico.

Python no tiene labels. Si se necesita salir de ambos loops a la vez,
se usa una variable de control que se revisa tambien en el loop
externo, o se convierte el bloque en una funcion y se usa return para
salir de un jalon.

EN RESUMEN: break y continue afectan solo al loop mas interno en ambos
lenguajes; JS puede evitarlo con labels, Python no tiene esa opcion y
usa variables de control o return en su lugar.
"""

found = False

for i in range(3):
    for j in range(3):
        if i == 1 and j == 1:
            found = True
            break
    if found:
        break

print(i, j)
# Salida: "1 1" (en i=1, j=1 el break interno corta el for de j;
# found queda en True, asi que el "if found: break" corta tambien el
# for de i, dejando i=1 y j=1 como valores finales)


def find_pair():
    for i in range(3):
        for j in range(3):
            if i == 1 and j == 1:
                return i, j


result = find_pair()
print(result)
# Salida: (1, 1) (return corta ambos loops de un jalon, sin necesitar
# la variable de control found del ejemplo anterior)
