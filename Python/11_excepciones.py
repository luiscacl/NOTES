"""
================================================================================
MANEJO DE EXCEPCIONES: try / except
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
JS tiene try/catch, uno solo y generico para cualquier tipo de error.

EN PYTHON:
Python usa try/except, y se puede (y se recomienda) capturar por TIPO
de error especifico, con un except distinto para cada tipo, en vez de
uno solo generico que atrapa cualquier cosa.

EN RESUMEN: ambos detienen la ejecucion y saltan a un bloque de manejo
de errores, pero Python favorece distinguir el tipo de error en vez de
atraparlos todos por igual.
"""

try:
    number = int("not a number")
except ValueError:
    print("that was not a valid number")  # that was not a valid number


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

ORDEN DE EJECUCION: Python ejecuta el bloque try de arriba hacia abajo.
Si NINGUNA linea lanza una excepcion, todos los bloques except se
ignoran por completo y la ejecucion sigue despues del try/except. En
cuanto una linea DENTRO del try lanza una excepcion, la ejecucion de ese
bloque se corta ahi mismo (las lineas siguientes del try NO se
ejecutan), y Python busca, de arriba hacia abajo, el PRIMER except cuyo
tipo coincida con la excepcion lanzada.

EN RESUMEN: el try corre hasta el primer error; ahi se detiene y Python
busca, en orden, el primer except cuyo tipo coincida con ese error.
"""

try:
    numbers = [1, 2, 3]
    print(numbers[10])  # raises IndexError: list index out of range
except ValueError:
    print("this doesn't print, the error is not ValueError")  # not executed - the error raised is IndexError, not ValueError
except IndexError:
    print("index out of range")  # index out of range


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

Se pueden encadenar varios except para manejar distintos tipos de error
de forma distinta, cada uno con su propio mensaje o logica de
recuperacion.

EN RESUMEN: se apilan varios except, uno por tipo de error, cada uno
con su propia logica de recuperacion.
"""

def divide(a, b):
    try:
        return a / b
    except ZeroDivisionError:
        print("cannot divide by zero")  # cannot divide by zero
        return None
    except TypeError:
        print("arguments must be numbers")  # arguments must be numbers
        return None

print(divide(10, 2))  # 5.0
print(divide(10, 0))  # None
print(divide(10, "two"))  # None


"""
[IGUAL QUE JS]

Un except sin especificar tipo atrapa CUALQUIER excepcion. Se puede
usar, pero es mala practica abusar de el: oculta errores que quiza no
deberian ignorarse en silencio (un bug real en tu codigo se veria
identico a un error esperado, como un archivo que no existe).

EN RESUMEN: funciona igual que el catch generico de JS, que tambien
atrapa cualquier error sin distinguir su tipo.

NOTA SOBRE as: esta es la segunda vez que aparece esta palabra reservada
con un significado distinto. En import (10_modulos_y_scope.py) as le
pone un alias a un modulo; aqui, en except ... as nombre, guarda el
OBJETO de la excepcion capturada en esa variable, para poder leerlo
(como error.args o simplemente imprimirlo). Mas adelante, en with ...
as (13_archivos_y_stdlib.py), as vuelve a aparecer con un tercer
significado (guardar lo que devuelve __enter__). as no tiene un
significado propio fijo: siempre quiere decir "guarda lo anterior bajo
este nombre", pero que es "lo anterior" cambia segun la instruccion.
"""

try:
    result = 10 / 0
except Exception as error:
    print(f"something failed: {error}")  # something failed: division by zero


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

else Y finally

else se ejecuta SOLO si el try termino sin ningun error (ni una sola
excepcion lanzada). finally se ejecuta SIEMPRE al final, haya habido
error o no, incluso si el error no fue capturado por ningun except —
util para liberar recursos (cerrar un archivo, una conexion) sin
importar como haya terminado el bloque.

EN RESUMEN: else corre solo si no hubo error, finally corre siempre,
pase lo que pase.
"""

try:
    value = int("42")
except ValueError:
    print("could not convert")  # not executed - int("42") succeeds
else:
    print(f"successful conversion: {value}")  # successful conversion: 42
finally:
    print("this always prints")  # this always prints


"""
================================================================================
raise: LANZAR UNA EXCEPCION MANUALMENTE
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
throw detiene la ejecucion en ese punto y dispara una excepcion, que
viaja hacia arriba buscando un catch que la capture (si nadie la
captura, el programa termina mostrando el error).

EN PYTHON:
raise hace lo mismo: detiene la ejecucion en ese punto y dispara una
excepcion, que viaja hacia arriba buscando un except que la capture (si
nadie la captura, el programa termina mostrando el error).

EN RESUMEN: raise es el equivalente directo de throw.
"""

def check_age(age):
    if age < 0:
        raise ValueError("age cannot be negative")
    return age

try:
    check_age(-5)
except ValueError as error:
    print(error)  # age cannot be negative


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
JS tambien trae tipos de error predefinidos para situaciones comunes
(TypeError, RangeError, ReferenceError, etc.), aunque en la practica se
usan con menos disciplina que en Python.

EN PYTHON:
Python trae muchos tipos de excepcion ya definidos para situaciones
comunes, y usar el tipo correcto (en vez de uno generico) es lo que
permite a quien use tu codigo despues capturar justo el error que le
interesa:

    ValueError          un valor del tipo correcto, pero invalido
                         (int("abc"))
    TypeError            operacion entre tipos incompatibles
                         ("2" + 2)
    IndexError            indice fuera de rango en una secuencia
    KeyError               clave inexistente en un diccionario
    ZeroDivisionError       division entre cero
    FileNotFoundError        un archivo que no existe (ver
                             13_archivos_y_stdlib.py)

EN RESUMEN: la idea es la misma en ambos lenguajes (usar el tipo de
error mas especifico posible), pero en Python es una convencion mucho
mas establecida y con una jerarquia de tipos mas rica.
"""


"""
================================================================================
assert
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

assert revisa que una condicion sea True; si es False, lanza
AssertionError de inmediato (opcionalmente con un mensaje). Se usa para
verificar suposiciones internas del programa mientras se escribe y se
prueba un algoritmo — una forma rapida de decir "esto SIEMPRE deberia
ser verdad aqui; si no lo es, hay un bug". JS no tiene assert como
palabra reservada del lenguaje: existe console.assert(), pero solo
imprime un mensaje de error en la consola, no detiene el programa. Node
si trae un modulo assert (const assert = require("node:assert")) que
lanza un AssertionError real y detiene la ejecucion, como en Python,
pero es parte de la biblioteca estandar de Node (no del lenguaje JS en
si) y no existe en el navegador.

EN RESUMEN: assert es una herramienta de autochequeo para detectar bugs
propios durante el desarrollo, no para validar datos de entrada.
"""

def calculate_average(numbers):
    assert len(numbers) > 0, "list cannot be empty"
    return sum(numbers) / len(numbers)

print(calculate_average([1, 2, 3]))  # 2.0

calculate_average([])  # raises AssertionError: list cannot be empty


"""
================================================================================
EXCEPCIONES PROPIAS
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
se puede definir una clase de error propia heredando de Error, para
lanzar y capturar justo ese tipo especifico en vez de un Error
generico.

EN PYTHON:
se puede definir un tipo de excepcion propio heredando de Exception (ver
herencia en 12_programacion_orientada_a_objetos.py), para que quien use
tu codigo pueda capturar justo ESE error especifico con su propio
except, en vez de un ValueError o TypeError generico que podria
confundirse con errores de otras partes del programa.

EN RESUMEN: en ambos lenguajes se hereda de la clase base de errores
para crear un tipo de error propio y mas especifico.
"""

class InsufficientBalanceError(Exception):
    pass

def withdraw(balance, amount):
    if amount > balance:
        raise InsufficientBalanceError("not enough balance to withdraw that amount")
    return balance - amount

try:
    withdraw(100, 500)
except InsufficientBalanceError as error:
    print(error)  # not enough balance to withdraw that amount


"""
================================================================================
raise ... from: ENCADENAR EXCEPCIONES
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
desde ES2022 el propio lenguaje admite pasar { cause: error } al crear
un Error (new Error("mensaje", { cause: error })), y error.cause guarda
ahi la excepcion original. Es un mecanismo real y ya estandar (no un
truco de un entorno en particular), pero mas basico que el de Python:
es solo una propiedad de datos que hay que ir a leer a mano
(error.cause), no algo que el motor imprima automaticamente encadenado
si el error termina sin capturarse.

EN PYTHON:
cuando dentro de un except decides lanzar UNA EXCEPCION DISTINTA (por
ejemplo, traducir un error de bajo nivel a uno mas especifico de tu
programa), raise ... from conserva un rastro de cual fue la excepcion
ORIGINAL que la provoco, en vez de que parezca que aparecio de la nada.
Python la muestra como "the above exception was the direct cause of the
following exception" si el error no se captura.

EN RESUMEN: raise ... from encadena una excepcion nueva con la original
que la causo; Python lo soporta de forma nativa y ademas lo imprime
automaticamente encadenado si nadie lo captura. JS tiene su version
estandar desde ES2022 (error.cause), pero es mas basica: guarda el dato,
no gestiona ni imprime la cadena por vos.

NOTA SOBRE from: igual que as, from ya aparecio antes con OTRO
significado: en from modulo import nombre (10_modulos_y_scope.py) trae
nombres especificos de un modulo. Aqui, en raise ... from ..., conecta
una excepcion nueva con la excepcion original que la causo. Son dos
usos sin relacion entre si que comparten la misma palabra.
"""

def parse_age(text):
    try:
        return int(text)
    except ValueError as error:
        raise ValueError(f"invalid age: {text}") from error

try:
    parse_age("abc")
except ValueError as error:
    print(error)  # invalid age: abc
    print(error.__cause__)  # invalid literal for int() with base 10: 'abc'


"""
================================================================================
EAFP VS LBYL: LA FILOSOFIA DE PYTHON PARA ESTO
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Viniendo de JS, la tentacion natural es revisar TODO antes de actuar
("Look Before You Leap" — LBYL): preguntar si algo existe/es valido, y
solo despues usarlo.
"""

user_data = {"name": "Ann"}

if "age" in user_data:
    print(user_data["age"])
else:
    print("age not available")  # age not available


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

Python, en cambio, favorece "Easier to Ask Forgiveness than Permission"
(EAFP): intentar la operacion directo, y manejar el error si no se
pudo. Esto no es solo estilo, tiene una razon tecnica real: revisar y
despues actuar son DOS pasos separados, y algo puede cambiar entre uno
y otro (por ejemplo, otro hilo o proceso borra la clave justo entre el
"in" y el acceso — una condicion de carrera). Con try/except la
operacion se intenta una sola vez, de forma atomica desde la
perspectiva del programa. Ademas, revisar-antes-de-actuar muchas veces
implica hacer el trabajo de busqueda DOS veces (una para el if, otra
para el acceso real), mientras que EAFP lo hace una sola vez.
"""

try:
    print(user_data["age"])
except KeyError:
    print("age not available")  # age not available


"""
EN RESUMEN: esto no significa "usa try/except para todo": para validaciones de
ENTRADA de usuario (formularios, argumentos de un endpoint) LBYL sigue
siendo mas claro. La regla practica: si el caso "no existe" es una
posibilidad NORMAL y esperada del flujo (una clave que puede o no
estar, una conversion que puede fallar), EAFP es la convencion Python;
si es una validacion previa de datos de entrada, un if normal sigue
siendo la opcion mas clara.
"""
