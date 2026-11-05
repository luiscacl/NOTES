"""
================================================================================
MODULOS Y PAQUETES
================================================================================
[IGUAL QUE JS]

Un MODULO es simplemente un archivo .py. Cualquier variable, funcion o
clase definida ahi se puede usar en otro archivo importandolo, igual
que el sistema de import/export de JS (ES modules).

En Python no hace falta un export explicito: todo lo definido a nivel
de modulo (sin indentacion, "suelto" en el archivo) es importable por
defecto desde otro archivo.

EN RESUMEN: un modulo de Python es como un archivo de ES modules en
JS, salvo que no necesita export: todo lo de nivel superior ya es
importable.
"""

import math
print(math.sqrt(16))  # 4.0

import math as m
print(m.sqrt(16))  # 4.0

from math import sqrt, pi
print(sqrt(16))  # 4.0
print(pi)  # 3.141592653589793

from math import *
print(floor(4.7))  # 4


"""
[SIMILAR A JS, CON DIFERENCIAS]

    JS                                    Python
    import x from "module"               import module
    import { a, b } from "module"        from module import a, b
    import * as m from "module"          import module as m
"""


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Los ES modules tambien se ejecutan una sola vez: la primera vez que un
modulo se importa, JS corre su codigo de arriba hacia abajo, y si otro
archivo lo vuelve a importar despues, reutiliza el mismo modulo ya
cargado en vez de re-ejecutarlo.

EN PYTHON:
La primera vez que un modulo se importa en todo el programa, Python
ejecuta ESE ARCHIVO COMPLETO de arriba hacia abajo (tal como correria
si lo ejecutaras directo), y deja el resultado (sus variables,
funciones, clases ya definidas) disponible bajo el nombre del modulo.
Si el mismo modulo se vuelve a importar en otro lado del programa,
Python NO lo vuelve a ejecutar: reutiliza lo que ya quedo cargado la
primera vez.

Ademas, Python permite import * (from math import *), que trae TODO al
espacio de nombres actual sin ningun prefijo. Se considera mala
practica fuera de casos muy puntuales: puede pisar nombres que ya
existian sin avisar, y hace dificil saber de donde vino cada nombre al
leer el codigo despues.

EN RESUMEN: en ambos lenguajes un modulo se ejecuta una sola vez y
luego se cachea; Python ademas ofrece import * para traer todo sin
prefijo, algo que casi nunca conviene usar.
"""


"""
[IGUAL QUE JS]

Un modulo PROPIO se importa igual, usando el nombre del archivo sin la
extension .py. Si tuvieras un archivo utilities.py con una funcion
add() adentro, en otro archivo del mismo directorio harias:

    import utilities
    utilities.add(2, 3)

    from utilities import add
    add(2, 3)

EN RESUMEN: importar tus propios archivos funciona igual que importar
una libreria: solo cambia que usas el nombre del archivo en vez del
nombre de un paquete instalado.
"""


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
En un proyecto de Node, una carpeta con varios modulos adentro tambien
necesita un archivo que la marque como unidad importable: por defecto
Node busca un index.js dentro de la carpeta (o el campo "main" de un
package.json ahi mismo); si no encuentra ninguno de los dos, el import
de la carpeta falla con un error.

EN PYTHON:
Un PAQUETE es una carpeta con varios modulos adentro, que Python trata
como una unidad importable, tradicionalmente marcada con un archivo
__init__.py adentro (en versiones modernas de Python ya no es
obligatorio para que funcione).

EN RESUMEN: una carpeta de modulos necesita un archivo especial que la
marque como unidad importable en ambos lenguajes: index.js (o "main"
en package.json) en JS, __init__.py en Python (aunque en Python ya no
es estrictamente obligatorio en versiones modernas).
"""


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
npm es el gestor de paquetes de Node. Se usa desde la terminal, y las
dependencias externas de un proyecto se listan en package.json.

EN PYTHON:
pip es el gestor de paquetes de Python. Tambien se usa desde la
terminal, no dentro del codigo:

    pip install package_name
    pip install -r requirements.txt

Las dependencias externas de un proyecto se listan en requirements.txt.

EN RESUMEN: pip es el npm de Python, y requirements.txt cumple el
mismo rol que package.json.
"""


"""
================================================================================
SCOPE (ALCANCE DE VARIABLES): LEGB
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

El scope determina en que parte del codigo un nombre (variable, funcion)
es visible y usable. Esta seccion cubre uno de los puntos donde Python
cambia MAS radicalmente frente a JS: el scope de bloque.

EN JS:
Con let/const, JS tiene SCOPE DE BLOQUE: cualquier { } (un if, un for,
un while) crea su propio scope nuevo, y una variable declarada adentro
no existe afuera de ese bloque.

EN PYTHON:
Python NO tiene scope de bloque. Un if, un for, o un while NO crean un
scope nuevo: una variable creada dentro de ellos sigue existiendo
DESPUES del bloque, en el mismo scope donde estaba el bloque. Los UNICOS
que crean un scope nuevo en Python son: una FUNCION (def o lambda), una
CLASE, y el modulo completo.

EN RESUMEN: en JS una variable de bloque muere al salir del { }; en
Python esa misma variable sigue viva despues del if/for/while, porque
solo funciones, clases y el modulo crean scope nuevo.
"""

if True:
    block_variable = "I still exist outside the if"

print(block_variable)  # I still exist outside the if

for i in range(3):
    last_value = i

print(last_value)  # 2


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

LEGB es el orden en que Python BUSCA un nombre cuando lo encuentra en el
codigo, de mas cercano a mas lejano:

    L ocal      dentro de la funcion actual
    E nclosing  dentro de una funcion que envuelve a la actual (si hay
                una funcion definida dentro de otra)
    G lobal     a nivel del modulo (archivo) completo
    B uilt-in   los nombres que Python ya trae de fabrica (print, len,
                range, etc.), sin necesitar import

Python revisa esos cuatro niveles EN ESE ORDEN y se queda con el primero
que encuentra un nombre coincidente.

EN RESUMEN: cuando Python ve un nombre, lo busca primero en local,
luego en enclosing, luego en global, y por ultimo en built-in; se
queda con la primera coincidencia que encuentra.
"""

x = "global"

def outer_function():
    x = "enclosing"

    def inner_function():
        x = "local"
        print(x)  # local

    inner_function()
    print(x)  # enclosing

outer_function()
print(x)  # global

def uses_builtin():
    print(len([1, 2, 3]))  # 3

uses_builtin()


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

GLOBAL: dentro de una funcion, LEER una variable global funciona sin
nada especial (Python la encuentra siguiendo LEGB). El problema aparece
en cuanto la funcion le ASIGNA un valor en algun punto de su cuerpo: en
ese caso Python decide, desde ANTES de ejecutar una sola linea de la
funcion, que ese nombre es LOCAL en TODA la funcion completa — incluidas
las lineas anteriores a la asignacion. Por eso increment_bad() de
abajo no "usa por accidente" la global: directamente truena con
UnboundLocalError, porque intenta leer counter como variable local
antes de que esa variable local tenga un valor asignado.

Para modificar de verdad la variable global desde dentro de una
funcion, hay que declararla con global primero; eso le dice a Python
"este nombre no es local, es el global", y ya no se crea ninguna
variable local con ese nombre.

EN RESUMEN: leer una global adentro de una funcion es automatico, pero
para asignarle un valor nuevo hace falta declarar global antes, o
Python la trata como una variable local nueva (y truena si la lees
antes de asignarla).
"""

counter = 0

def increment_bad():
    counter = counter + 1

def increment_good():
    global counter
    counter = counter + 1

increment_good()
print(counter)  # 1

increment_bad()  # UnboundLocalError: cannot access local variable 'counter' where it is not associated with a value


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

NONLOCAL: la misma idea que global, pero para el caso de una funcion
anidada que quiere modificar una variable de su funcion ENVOLVENTE (no
la global del modulo). Sin nonlocal, la asignacion crearia una variable
local nueva dentro de la funcion interna, en vez de modificar la de
afuera.

EN RESUMEN: nonlocal hace por el scope enclosing lo mismo que global
hace por el scope global: permite que una funcion anidada modifique
una variable de afuera en vez de crear una copia local.
"""

def outer_counter():
    count = 0

    def increment():
        nonlocal count
        count += 1
        print(count)  # 1 then 2

    increment()
    increment()

outer_counter()


"""
================================================================================
if __name__ == "__main__":
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Node resuelve el mismo problema (saber si un archivo se ejecuto
directo o fue importado) revisando require.main === module, o
simplemente separando el codigo de "entrada" en su propio archivo.

EN PYTHON:
Cada archivo .py, ademas de su propio codigo, tiene una variable
especial __name__ que Python llena automaticamente. Su valor depende de
COMO se ejecuto ese archivo:

    Si el archivo se corrio DIRECTO (python archivo.py), __name__ vale
    el string "__main__".

    Si el archivo fue IMPORTADO desde otro archivo (import archivo),
    __name__ vale el nombre del modulo, no "__main__".

Esto permite escribir un bloque de codigo que solo se ejecute cuando el
archivo se corre directamente, pero NO cuando alguien mas lo importa
solo para usar sus funciones.

EN RESUMEN: if __name__ == "__main__" es la forma en que Python
distingue "corri este archivo directo" de "alguien me importo",
equivalente a lo que require.main === module resuelve en Node.
"""

def add(a, b):
    return a + b

if __name__ == "__main__":
    print("this file is running directly")  # this file is running directly
    print(add(2, 3))  # 5


"""
Si este mismo archivo se importara desde otro (import nombre_de_este_
archivo), la funcion add quedaria disponible para usarse, pero el
bloque de adentro del if NO se ejecutaria — es la forma estandar de
separar "codigo reusable" de "codigo que solo debe correr cuando este
archivo es el programa principal".
"""
