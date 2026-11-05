"""
================================================================================
QUE ES PYTHON Y COMO SE EJECUTA
================================================================================
[IGUAL QUE JS]

Python es un lenguaje INTERPRETADO y de TIPADO DINAMICO.

Interpretado significa que no hay un paso de compilacion previo que
genere un binario (como en Java o C): el interprete (el programa
"python") lee tu archivo .py y lo va ejecutando el mismo.

Tipado dinamico significa que una variable no tiene un tipo fijo
declarado de antemano. El tipo lo trae el VALOR asignado en cada
momento, no el nombre de la variable. En TypeScript, en cambio, el tipo
se declara de antemano junto con la variable (algo como let x: number =
5); eso no existe en Python.

ORDEN DE EJECUCION: un archivo .py se ejecuta de arriba hacia abajo,
instruccion por instruccion, en el orden exacto en que aparecen
escritas. Una funcion o clase definida mas abajo en el archivo no existe
todavia para el codigo que esta arriba de ella; por eso hay que definir
las cosas antes de usarlas. Python no aplica ningun tipo de hoisting.

Un script de JS ejecutado top-level tambien corre de arriba hacia abajo,
en ese mismo orden. La diferencia es que JS aplica hoisting a las
declaraciones hechas con var (el nombre ya existe desde el inicio,
inicializado a undefined) y tambien a las funciones declaradas con
function, pero en este segundo caso la funcion queda completamente
definida y ya se puede invocar incluso antes de la linea donde aparece
escrita.

Un archivo se corre desde la terminal con: python nombre_archivo.py

EN RESUMEN: Python ejecuta el archivo de arriba hacia abajo sin
compilacion previa y sin hoisting; el tipo de cada variable lo determina
su valor actual, no una declaracion previa.
"""


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
INDENTACION: sustituye a las llaves { }

Python delimita un bloque de codigo (el cuerpo de un if, un loop, una
funcion, una clase) con DOS PUNTOS para abrirlo, y con la INDENTACION
(los espacios al inicio de la linea) para decidir que pertenece a ese
bloque y que no.

JS usa llaves { } para delimitar sus bloques de codigo. Python no usa
llaves en absoluto para eso.

La indentacion no es un estilo opcional aqui, es sintaxis obligatoria:
si dos lineas que deberian estar en el mismo bloque no tienen exactamente
la misma indentacion, el programa truena con IndentationError antes de
correr una sola linea. La convencion universal (PEP8) es 4 espacios por
nivel, nunca tabs, y nunca mezclar tabs con espacios en el mismo archivo.

EN RESUMEN: los dos puntos abren el bloque y la indentacion (4 espacios
por nivel, de forma consistente) es lo que define que codigo queda
dentro de ese bloque.
"""

if True:
    message = "inside the block"
    print(message)  # inside the block
print("this line is no longer indented, it's outside the if")  # this line is no longer indented, it's outside the if


"""
[SIMILAR A JS, CON DIFERENCIAS]
print() Y LOS COMENTARIOS

EN JS:
console.log() manda texto a la consola, y acepta varios argumentos
separados por coma que se imprimen en una sola linea. Los comentarios de
una sola linea empiezan con //, y los comentarios de varias lineas usan
el simbolo dedicado /* ... */.

EN PYTHON:
print() manda texto a la terminal. Acepta varios argumentos separados
por coma y los imprime todos en una sola linea, separados por un
espacio.

Los comentarios de una sola linea empiezan con #. Python no tiene un
simbolo dedicado para comentarios de varias lineas; en su lugar se usa
una cadena de texto entre tres comillas (\"\"\" o ''') que, si queda
"suelta" sin asignarse a nada, el interprete la evalua y la descarta sin
ningun efecto — por eso funciona como bloque de comentario en la
practica, aunque tecnicamente sea un string. Este mismo archivo usa ese
recurso para las explicaciones largas.

EN RESUMEN: print() imprime a la terminal, # comenta una sola linea, y
un string de tres comillas sin asignar funciona como comentario de
varias lineas.
"""

name = "Ann"
age = 25
print("name:", name, "age:", age)  # name: Ann age: 25


"""
[SIMILAR A JS, CON DIFERENCIAS]
PUNTO Y COMA

EN JS:
El punto y coma al final de una linea normalmente es opcional gracias al
ASI (Automatic Semicolon Insertion), pero en ciertos casos especificos el
motor SI lo necesita para evitar ambiguedad al interpretar la linea
siguiente.

EN PYTHON:
No se usan punto y coma al final de linea. Un ; SI es una sintaxis
valida, pero solo para separar varias instrucciones dentro de una misma
linea; no es la convencion, y no resuelve ningun problema que no este ya
resuelto con un salto de linea normal.

EN RESUMEN: en Python el punto y coma casi nunca se usa; el salto de
linea ya marca el final de cada instruccion.
"""

a = 1; b = 2
print(a, b)  # 1 2


"""
================================================================================
VARIABLES
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Una variable se declara con let, const o var, y opcionalmente se le
asigna un valor en esa misma linea.

EN PYTHON:
No existen las palabras let, const ni var. Para crear una variable
simplemente se le asigna un valor con =; esa misma linea la crea y la
inicializa al mismo tiempo. Python infiere el tipo a partir del valor
asignado; no existe una sintaxis para declarar el tipo por adelantado
(algo como int x = 5 no existe).

EN RESUMEN: en Python, asignar un valor con = ya es la forma de crear la
variable; no hace falta ninguna palabra reservada previa.
"""

name = "Bro"
height = 1.75
is_student = True


"""
[IGUAL QUE JS]
El tipo de una variable puede cambiar en cualquier momento durante la
ejecucion, porque lo que tiene tipo es el VALOR actual, no el nombre. El
nombre es solo una etiqueta que en cada momento apunta a un valor; se le
puede volver a apuntar a un valor de otro tipo sin ningun error. En esto
Python se comporta igual que JS.

EN RESUMEN: una misma variable puede guardar un numero y despues una
lista sin que el interprete se queje.
"""

value = 10
value = "now I am text"
value = [1, 2, 3]


"""
[SIMILAR A JS, CON DIFERENCIAS]
REGLAS DE NOMBRES

EN JS:
Un nombre de variable puede usar letras, numeros, guion bajo _ y tambien
el simbolo $. No puede empezar con un numero, y no puede ser una palabra
reservada del lenguaje. Es sensible a mayusculas/minusculas (age y Age
son variables distintas). La convencion de estilo mas comun es
camelCase.

EN PYTHON:
Un nombre de variable solo puede usar letras, numeros y guion bajo _; no
se permite el simbolo $ ni el guion -. No puede empezar con un numero, y
no puede ser una palabra reservada del lenguaje (if, for, class, etc).
Tambien es sensible a mayusculas/minusculas (age y Age son variables
distintas). La convencion de estilo (PEP8) es snake_case en minusculas,
NO camelCase.

EN RESUMEN: los nombres de variable en Python se escriben en snake_case,
usando solo letras, numeros y guion bajo.
"""

total_sales = 1500
myVariableInCamelCase = "valid, but not the convention in Python"


"""
[SIMILAR A JS, CON DIFERENCIAS]
ASIGNACION MULTIPLE

EN JS:
Para asignar varias variables a partir de una lista de valores en una
sola linea hace falta destructuring: const [a, b] = [1, 2].

EN PYTHON:
Se pueden asignar varias variables en una sola linea de forma directa,
sin ninguna sintaxis especial: es la forma normal de asignar valores
desde el principio.

EN RESUMEN: x, y, z = 1, 2, 3 asigna las tres variables en un solo paso.
"""

x, y, z = 1, 2, 3
print(x, y, z)  # 1 2 3

a = b = c = 0
print(a, b, c)  # 0 0 0


"""
[SIMILAR A JS, CON DIFERENCIAS]
INTERCAMBIAR VALORES (swap)

EN JS:
Intercambiar los valores de dos variables normalmente requiere una
tercera variable temporal donde guardar uno de los dos valores mientras
se hace el cambio, o bien destructuring: [x, y] = [y, x].

EN PYTHON:
El swap no necesita ninguna variable temporal. El lado derecho de la
asignacion se evalua POR COMPLETO primero, formando una tupla temporal
con los valores originales, y solo hasta entonces se hace la asignacion
a la izquierda. Por eso x y y terminan intercambiados sin necesitar un
tercer nombre donde "guardar" uno de los dos mientras se hace el cambio.

EN RESUMEN: x, y = y, x intercambia los dos valores en un solo paso.
"""

x, y = 5, 10
x, y = y, x
print(x, y)  # 10 5


"""
[SIMILAR A JS, CON DIFERENCIAS]
CONSTANTES

EN JS:
const declara una constante real: el motor impide reasignar ese nombre
despues de la declaracion inicial, y lanza un error si se intenta.

EN PYTHON:
Python no tiene constantes reales a nivel de lenguaje: no existe una
palabra reservada equivalente a const que impida reasignar. La
convencion para señalar "esto no se debe reasignar en el resto del
programa" es escribir el nombre completo en MAYUSCULAS. Es solo una
convencion visual que otros programadores van a respetar por costumbre;
el interprete no impide reasignarla si de todas formas lo haces.

EN RESUMEN: MAYUSCULAS es una señal para otros programadores, no una
proteccion real contra reasignar el valor.
"""

PI = 3.14159
MAX_ATTEMPTS = 3


"""
================================================================================
pass: LA INSTRUCCION "NO HACER NADA"
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

En Python, ningun bloque (if, for, while, def, class) puede quedar
VACIO por escrito: si necesitas dejar un bloque sin implementar
todavia, o una rama que a proposito no debe hacer nada, hace falta
escribir ALGO adentro. pass es exactamente eso: una instruccion que no
hace nada, solo ocupa el lugar donde el interprete exige una linea de
codigo.

JS no necesita un equivalente, porque sus bloques { } pueden quedar
vacios sin ningun error.

EN RESUMEN: pass es un relleno valido que se usa para dejar un bloque
sintacticamente completo sin que haga nada todavia.
"""

def pending_function():
    pass

if True:
    pass
else:
    print("this doesn't get printed")  # no output — this branch never runs, since the condition is True

class EmptyClass:
    pass


"""
================================================================================
DESEMPAQUETADO ANIDADO
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El destructuring tambien puede aplicarse a estructuras anidadas, por
ejemplo const [[a, b], c] = [[1, 2], 3], siempre que la forma del patron
calce con la forma del valor.

EN PYTHON:
La asignacion multiple (vista arriba) tambien puede desempaquetar
ESTRUCTURAS ANIDADAS, siempre que la forma de la izquierda calce
exactamente con la forma de la derecha.

EN RESUMEN: (a, b), c = (1, 2), 3 desempaqueta una tupla anidada en un
solo paso, siempre que las formas coincidan.
"""

(a, b), c = (1, 2), 3
print(a, b, c)  # 1 2 3

name, (day, month, year) = "Ann", (15, 8, 2024)
print(name, day, month, year)  # Ann 15 8 2024


"""
================================================================================
del: ELIMINAR UN NOMBRE POR COMPLETO
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

del ya se ha visto borrando un elemento de una lista o una clave de un
diccionario (07_colecciones.py), pero su forma mas basica es borrar una
VARIABLE completa: el nombre deja de existir en ese scope, como si
nunca se hubiera asignado.

JS no tiene un equivalente para variables normales: delete solo aplica a
propiedades de un objeto, nunca a una variable declarada con let o
const.

EN RESUMEN: del temporary_value borra el nombre por completo; usarlo
despues lanza NameError.
"""

temporary_value = 42
del temporary_value

print(temporary_value)  # NameError: name 'temporary_value' is not defined
