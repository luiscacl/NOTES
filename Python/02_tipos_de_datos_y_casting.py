"""
================================================================================
TIPOS DE DATOS BASICOS
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
typeof devuelve un string corto que identifica la categoria de un valor,
como "number" o "string".

EN PYTHON:
Los tipos fundamentales con los que vas a trabajar todo el tiempo son:
int (entero), float (decimal), str (texto), bool (True/False) y None
(ausencia de valor). type() devuelve el tipo de un valor, pero a
diferencia de typeof devuelve la clase completa del objeto, no un string
corto.

EN RESUMEN: type() te dice de que clase es un valor; se usa
constantemente para inspeccionar y depurar datos.
"""

integer_number = 10
float_number = 3.14
text = "hello"
boolean_value = True
none_value = None

print(type(integer_number))  # <class 'int'>
print(type(float_number))  # <class 'float'>
print(type(text))  # <class 'str'>
print(type(boolean_value))  # <class 'bool'>
print(type(none_value))  # <class 'NoneType'>


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

isinstance()

Para revisar el tipo de un valor dentro de una condicion, isinstance() es
la forma idiomatica en vez de comparar con type() y ==: hace lo mismo
que type(x) == ClaseX en el caso simple, pero ADEMAS reconoce herencia
(un objeto de una clase hija cuenta como instancia de la clase padre
tambien, ver 12_programacion_orientada_a_objetos.py). Tambien acepta una
tupla de tipos, para preguntar "es de CUALQUIERA de estos tipos". JS no
tiene una funcion equivalente a esta.

EN RESUMEN: isinstance(valor, Tipo) es la forma correcta de preguntar de
que tipo es algo, incluyendo tipos heredados.
"""

print(isinstance(10, int))  # True
print(isinstance("hello", str))  # True
print(isinstance(10, (int, float)))  # True
print(isinstance(10, str))  # False


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

int VS float

Python distingue explicitamente entre int y float como dos tipos
separados: 10 es int, 10.0 es float, y esa distincion se conserva salvo
que una operacion fuerce la conversion (dividir con / siempre da float,
por ejemplo). JS en cambio solo tiene un tipo numerico (number, siempre
float de 64 bits por debajo), asi que ahi 10 y 10.0 son indistinguibles
internamente.

int en Python no tiene limite de tamaño: crece tanto como la memoria lo
permita, sin perder precision. En JS, un numero entero muy grande empieza
a perder precision porque comparte representacion con los decimales.
float en Python si sigue el mismo estandar IEEE-754 que usa JS, por lo
que hereda el mismo problema clasico de precision con decimales.

EN RESUMEN: Python separa int (precision infinita) de float (IEEE-754,
con las mismas limitaciones de precision que en JS); dividir con /
siempre produce float.
"""

huge_number = 99999999999999999999999999999
print(huge_number + 1)  # 100000000000000000000000000000

print(0.1 + 0.2)  # 0.30000000000000004


"""
[IGUAL QUE JS]

LITERALES NUMERICOS: GUION BAJO Y NOTACION CIENTIFICA

Para hacer legibles los numeros grandes, se puede usar un guion bajo _
como separador visual entre digitos; Python lo ignora al leer el valor,
es puramente para que el humano lo lea mejor. JS tiene el mismo recurso
con la misma sintaxis (1_000_000). Los floats tambien aceptan notacion
cientifica con e.

EN RESUMEN: el guion bajo en literales numericos es solo un separador
visual sin efecto en el valor; funciona igual en ambos lenguajes.
"""

one_million = 1_000_000
print(one_million)  # 1000000

scientific_number = 1.5e3
print(scientific_number)  # 1500.0


"""
[IGUAL QUE JS]

LITERALES BINARIOS, HEXADECIMALES Y OCTALES

Ademas del decimal de siempre, Python permite escribir un literal entero
directamente en binario (prefijo 0b), hexadecimal (prefijo 0x) u octal
(prefijo 0o). Por debajo no son un tipo especial: son int comunes, solo
que escritos en otra base; una vez creados ya no se distinguen entre si.
JS admite los mismos prefijos con el mismo significado.

EN RESUMEN: 0b, 0x y 0o son solo formas alternativas de escribir el
mismo valor entero.
"""

binary_literal = 0b1010
hex_literal = 0x1A
octal_literal = 0o17

print(type(hex_literal))  # <class 'int'>
print(hex_literal == 26)  # True
print(binary_literal, hex_literal, octal_literal)  # 10 26 15


"""
[EXCLUSIVO DE PYTHON — no existe en JS]

bin(), hex() y oct() hacen la conversion inversa: toman un int decimal y
devuelven su representacion en esa base, pero como str, no como int (el
prefijo 0b/0x/0o queda incluido en ese string). JS no tiene funciones
equivalentes a estas.

EN RESUMEN: bin(), hex() y oct() convierten un entero a su texto en esa
base.
"""

print(bin(10))  # 0b1010
print(hex(26))  # 0x1a
print(oct(15))  # 0o17
print(type(hex(26)))  # <class 'str'>


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
parseInt(text, radix) parsea un string como entero interpretandolo en la
base indicada por radix, sin necesidad de que el texto lleve el prefijo
0b/0x/0o.

EN PYTHON:
int(text, base) hace lo mismo: parsea un str como entero
interpretandolo en la base que le indiques como segundo argumento. Es
la herramienta comun en ejercicios de manipulacion de bits o conversion
entre bases. El operador de potencia ** y los operadores bit a bit (&,
|, ^, <<, >>, ya vistos en 03_operadores.py) son los complementos
naturales de este tipo de conversiones.

EN RESUMEN: int(text, base) convierte un string a entero interpretando
sus digitos en la base que especifiques.
"""

print(int("1010", 2))  # 10
print(int("1A", 16))  # 26


"""
[SIMILAR A JS, CON DIFERENCIAS]

str

EN JS:
Las comillas simples y dobles se comportan igual entre si, pero los
backticks habilitan un modo especial de template literal.

EN PYTHON:
str va entre comillas simples o dobles, y son completamente
intercambiables entre si (no hay un tercer tipo de comilla con
comportamiento especial). No existe un tipo "char" separado para un solo
caracter: "a" tambien es un str, de longitud 1.

EN RESUMEN: en Python cualquier texto es un str, sin importar si usa
comillas simples o dobles, y no hay un tipo aparte para un solo
caracter.
"""

single_quotes = 'text'
double_quotes = "text"
single_char = "a"
print(type(single_char))  # <class 'str'>


"""
[SIMILAR A JS, CON DIFERENCIAS]

bool

EN JS:
Los valores booleanos se escriben true y false, en minusculas.

EN PYTHON:
bool solo tiene dos valores posibles: True y False, siempre con
mayuscula inicial. Un detalle propio de Python: bool es tecnicamente un
subtipo de int por debajo, asi que True se comporta como 1 y False como
0 en cualquier operacion aritmetica.

EN RESUMEN: True y False son los unicos valores booleanos, se escriben
con mayuscula inicial, y funcionan como 1 y 0 en operaciones
aritmeticas porque son subtipo de int.
"""

print(True + True)  # 2
print(False + 5)  # 5
print(True == 1)  # True


"""
[SIMILAR A JS, CON DIFERENCIAS]

None

EN JS:
null representa "ningun valor". Ademas existe undefined, un estado
intermedio para una variable declarada pero sin valor asignado todavia.

EN PYTHON:
None es el equivalente conceptual a null: representa "ningun valor".
Python no tiene un equivalente a undefined: no existe ese estado
intermedio. Si usas un nombre que nunca se asigno, el resultado no es
None, es un error (NameError) al momento de evaluar esa linea.

EN RESUMEN: None significa "ausencia de valor" explicita; usar una
variable no asignada no da None, lanza un error.
"""

result = None
print(result)  # None
print(type(None))  # <class 'NoneType'>


"""
================================================================================
TYPE CASTING (CONVERSION DE TIPOS)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
La conversion de tipos muchas veces pasa sola por coercion implicita:
"5" + 1 da "51", "5" * 2 da 10.

EN PYTHON:
La conversion es siempre EXPLICITA: se llama a una funcion para
convertir de un tipo a otro, y mezclar tipos incompatibles sin convertir
de forma manual lanza un error en vez de intentar adivinar que quisiste
hacer.

EN RESUMEN: en Python nunca hay conversion automatica entre tipos
incompatibles; siempre hay que convertir a mano con int(), float(),
str(), etc.
"""

age_text = "25"
age_number = int(age_text)
height = float("1.75")
quantity = str(100)
active = bool(1)

print(age_number, type(age_number))  # 25 <class 'int'>
print(height, type(height))  # 1.75 <class 'float'>
print(quantity, type(quantity))  # 100 <class 'str'>
print(active, type(active))  # True <class 'bool'>


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Number() ante un texto invalido devuelve NaN, sin detener el programa.

EN PYTHON:
Si el texto no representa un numero valido para ese tipo, int()/float()
lanzan ValueError. int("25.5") por ejemplo falla, porque "25.5" no es un
entero valido; hace falta pasar primero por float y despues por int si
de verdad quieres truncarlo: int(float("25.5")). La ultima linea de
abajo demuestra intencionalmente el ValueError.

EN RESUMEN: una conversion invalida en Python detiene el programa con un
error en vez de devolver un valor especial como NaN.
"""

print(int(float("25.5")))  # 25
int("25.5")  # ValueError: invalid literal for int() with base 10: '25.5'


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Los valores considerados falsy son 0, "" (string vacio), null, undefined
y NaN; cualquier otro valor es truthy, incluyendo un string con
contenido como "false" o "0" escritos como texto.

EN PYTHON:
bool() sigue reglas de "truthiness" similares (se profundiza en el
archivo de operadores): 0, 0.0, "" (string vacio) y None se convierten
en False; CUALQUIER otro valor se convierte en True, incluyendo un
string con contenido como "False" o "0" escritos como texto, porque no
estan vacios.

EN RESUMEN: un valor es falsy si es cero, vacio o None; todo lo demas es
truthy, incluso texto que "parece" falso.
"""

print(bool(0))  # False
print(bool(1))  # True
print(bool(""))  # False
print(bool("False"))  # True
print(bool("0"))  # True
print(bool(None))  # False


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El operador + concatena implicitamente un numero con un string,
convirtiendo el numero a texto automaticamente.

EN PYTHON:
Sumar directamente un numero y un string sin convertir lanza TypeError.
Hay que convertir el numero a str explicitamente antes de unirlo a otro
texto.

EN RESUMEN: + nunca mezcla tipos en Python; para pegar un numero a un
texto hay que convertirlo primero con str().
"""

age = 25
message = "I am " + str(age) + " years old"
print(message)  # I am 25 years old


"""
================================================================================
USER INPUT
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
prompt() pausa la ejecucion en el navegador, muestra el mensaje que le
pasas como argumento, y devuelve lo que el usuario haya escrito al
aceptar el dialogo.

EN PYTHON:
input() hace lo mismo pero en la terminal: pausa la ejecucion, muestra
el mensaje que le pasas como argumento, y devuelve lo que el usuario
haya escrito al presionar Enter. El punto mas importante: input()
SIEMPRE devuelve un str, sin importar que el usuario haya tecleado
numeros. Si el valor se va a usar como numero, hay que convertirlo con
int() o float(), igual que con cualquier otro string.

EN RESUMEN: input() siempre devuelve texto; conviertelo con int() o
float() si necesitas un numero.
"""

username = input("What is your name? ")  # example: user types "Ann", so username = "Ann"
print("Hello " + username)  # Hello Ann

age_as_text = input("What is your age? ")  # example: user types "25", so age_as_text = "25"
print(type(age_as_text))  # <class 'str'>

age_as_number = int(input("What is your age? "))  # example: user types "30", so age_as_number = 30
print(age_as_number + 1)  # 31


"""
Esto aplica igual cuando el texto invalido viene de input(): int()/float()
lanzan el mismo ValueError visto arriba (en vez del NaN que devolveria
Number() en JS), ahi mismo, y detienen el programa si no se maneja (ver
11_excepciones.py para capturarlo con try/except y evitar que el
programa truene).
"""
