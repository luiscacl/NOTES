"""
================================================================================
LITERALES DE STRING: SECUENCIAS DE ESCAPE Y RAW STRINGS
================================================================================
[IGUAL QUE JS]

Dentro de un string, una barra invertida \\ inicia una SECUENCIA DE
ESCAPE: un caracter especial que no se puede escribir tal cual, igual
que en JS.

    \\n    salto de linea         \\t    tabulador
    \\\\    una barra invertida     \\"    comilla doble literal

EN RESUMEN: las secuencias de escape de Python son las mismas que ya
conoces de JS, caracter por caracter.
"""

text_with_newline = "first line\nsecond line"
print(text_with_newline)  # first line\nsecond line (se imprime en dos lineas)

normal_path = "C:\\Users\\John"
print(normal_path)  # C:\Users\John


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Un RAW STRING (con una r antes de la comilla de apertura) desactiva por
completo las secuencias de escape: cada caracter se toma literal, tal
cual se escribio. Se usa sobre todo para rutas de Windows y expresiones
regulares, donde \\ aparece todo el tiempo sin querer decir "escape".

EN RESUMEN: r"..." apaga las secuencias de escape; lo que ves es
exactamente lo que hay.
"""

raw_path = r"C:\Users\John"
print(raw_path)  # C:\Users\John


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Un template literal con backticks conserva saltos de linea reales
escritos dentro de el, funcionando como texto multilinea.

EN PYTHON:
Un string entre tres comillas cumple el mismo papel: no es solo un
bloque de comentario (como se ha usado en estos archivos), tambien es
un literal de string real que conserva los saltos de linea escritos
tal cual dentro de el.

EN RESUMEN: las triples comillas son el equivalente a un template
literal multilinea de JS.
"""

paragraph = """First line.
Second line.
Third line."""

print(paragraph)  # First line.\nSecond line.\nThird line. (tres lineas)


"""
================================================================================
STRING METHODS
================================================================================
[IGUAL QUE JS]

Los strings tienen metodos igual que en JS, se llaman con punto. Muchos
tienen equivalente directo, otros cambian de nombre.

    JS                      Python
    .trim()                 .strip()
    .toUpperCase()          .upper()
    .toLowerCase()          .lower()
    .includes(x)            x in texto
    .indexOf(x)             .find(x)
    .split(sep)             .split(sep)
    .padStart(n, c)         .rjust(n, c)
    .padEnd(n, c)           .ljust(n, c)

EN RESUMEN: mismo concepto de metodos encadenables con punto, solo
cambian algunos nombres.
"""

text = "  Hello World  "

print(text.upper())  # "  HELLO WORLD  "
print(text.lower())  # "  hello world  "
print(text.strip())  # "Hello World"
print(text.strip().replace("World", "Python"))  # "Hello Python"
print("World" in text)  # True (equivalente a .includes() de JS)


"""
[IGUAL QUE JS]
Un detalle de ejecucion importante: los strings son INMUTABLES (se ve a
detalle mas abajo), asi que NINGUNO de estos metodos modifica el string
original. Cada uno DEVUELVE un string nuevo con el resultado; si no
guardas ese resultado en una variable (o lo encadenas, como
.strip().replace(...) arriba), se pierde.

EN RESUMEN: igual que en JS, estos metodos nunca mutan el string
original, siempre devuelven uno nuevo.
"""

phrase = "python is great"

print(phrase.split(" "))  # ['python', 'is', 'great']
print("-".join(["a", "b", "c"]))  # "a-b-c"
print(phrase.startswith("python"))  # True
print(phrase.endswith("great"))  # True
print(phrase.find("is"))  # 7
print(phrase.count("e"))  # 1
print(phrase.title())  # "Python Is Great"
print(phrase.capitalize())  # "Python is great"
print(phrase.replace(" ", "_"))  # "python_is_great"


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
isdigit(), isalpha(), isalnum() son validaciones muy usadas para revisar
el contenido de un string antes de convertirlo (por ejemplo, antes de
pasarlo a int()).

EN RESUMEN: son formas rapidas de preguntar "¿este texto es solo
digitos / solo letras / solo alfanumerico?" sin regex.
"""

print("123".isdigit())  # True
print("abc".isalpha())  # True
print("abc123".isalnum())  # True


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Otros metodos frecuentes: rsplit (como split pero cuenta divisiones
desde la derecha), splitlines (separa por saltos de linea), zfill
(rellena con ceros a la izquierda, comun para IDs), swapcase (invierte
mayus/minus), y lstrip/rstrip (strip pero solo del lado izquierdo o
derecho).

EN RESUMEN: variantes utilitarias de split/strip que cubren casos
puntuales sin tener que armarlos a mano.
"""

print("a,b,c,d".rsplit(",", 1))  # ['a,b,c', 'd']
print("line1\nline2".splitlines())  # ['line1', 'line2']
print("7".zfill(3))  # "007"
print("Python".swapcase())  # "pYTHON"
print("   text   ".lstrip())  # "text   "
print("   text   ".rstrip())  # "   text"


"""
[IGUAL QUE JS]
Los strings tambien se pueden comparar con < > <= >=: la comparacion
es LEXICOGRAFICA (caracter por caracter, segun su valor Unicode), igual
que JS al comparar strings con esos mismos operadores.

EN RESUMEN: comparar strings con < > <= >= funciona letra por letra,
igual que en JS.
"""

print("apple" < "pear")  # True
print("Zebra" < "ant")  # True (Z mayuscula vale menos que a minuscula en Unicode)


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
El operador + entre un string y un numero convierte implicitamente el
numero a texto y concatena.

EN PYTHON:
+ exige que AMBOS lados sean str; mezclar un str con un numero lanza
TypeError, no hay conversion implicita. Ademas, * repite el string el
numero de veces indicado, algo que en JS no existe de forma nativa con
el operador * sobre un string.

EN RESUMEN: en Python hay que convertir numeros a texto a mano antes de
concatenar, pero a cambio se gana "texto" * n para repetir.
"""

print("a" + "b")  # "ab"
print("ab" * 3)  # "ababab"


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
padStart() y padEnd() rellenan un string hasta un ancho dado, por la
izquierda o por la derecha respectivamente.

EN PYTHON:
.rjust() y .ljust() cubren exactamente lo mismo (ya vistos en la tabla
de arriba). Ademas existe .center(), que no tiene equivalente directo
en JS: ahi tocaria calcular a mano cuantos espacios van de cada lado.

EN RESUMEN: rjust/ljust son padStart/padEnd con otro nombre, y center
es un extra que Python trae de fabrica.
"""

print("hi".rjust(6))  # "    hi"
print("hi".rjust(6, "0"))  # "0000hi"
print("hi".ljust(6))  # "hi    "
print("hi".ljust(6, "-"))  # "hi----"
print("hi".center(10))  # "    hi    "
print("hi".center(10, "*"))  # "****hi****"


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Ademas de isdigit(), isalpha() e isalnum() ya vistos arriba, existe
toda una familia de metodos "is*" para validar el contenido de un
string sin usar regex.

En JS, este tipo de validacion normalmente se resuelve con expresiones
regulares (por ejemplo /^[A-Z]+$/.test(x)), porque el lenguaje no trae
estos metodos incorporados.

isnumeric(), isdecimal() e isdigit() parecen lo mismo pero no lo son:
isdecimal() solo acepta digitos decimales "puros" (0-9 y equivalentes),
isdigit() ademas acepta cosas como superindices, e isnumeric() es el
mas amplio e incluye fracciones, numeros romanos y numerales de otros
sistemas de escritura.

EN RESUMEN: toda una familia de validaciones "is*" listas para usar,
donde en JS tocaria escribir una regex.
"""

print("HELLO".isupper())  # True
print("hello".islower())  # True
print("   ".isspace())  # True
print("Hello World".istitle())  # True
print("12".isnumeric())  # True
print("12".isdecimal())  # True
print("Ⅷ".isnumeric())  # True
print("Ⅷ".isdecimal())  # False
print("Ⅷ".isdigit())  # False


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
removeprefix() y removesuffix() (agregados en Python 3.9) quitan un
prefijo o sufijo exacto si esta presente, y si no esta, devuelven el
string sin cambios. Antes de que existieran, habia que hacerlo a mano
con un startswith() mas slicing, algo mas torpe:
    text[len(prefix):] if text.startswith(prefix) else text

EN RESUMEN: una forma directa de quitar un prefijo/sufijo exacto sin
slicing manual.
"""

print("test_string".removeprefix("test_"))  # "string"
print("test_string".removesuffix("_string"))  # "test"


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
.casefold() es como .lower() pero mas agresivo: normaliza tambien
casos especiales de Unicode que .lower() deja igual, como la ß alemana
(que .casefold() convierte a "ss"). Para comparar dos strings sin
importar mayusculas/minusculas de forma confiable, .casefold() es la
opcion mas correcta.

EN RESUMEN: para comparar texto ignorando mayus/minus de forma segura,
usa .casefold() en vez de .lower().
"""

print("Straße".lower())  # "straße"
print("Straße".casefold())  # "strasse"


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
.partition(sep) divide el string en una tupla de 3 elementos: (antes,
separador, despues), usando la PRIMERA aparicion del separador.
.rpartition(sep) hace lo mismo pero usando la ULTIMA aparicion. Si el
separador no aparece, .partition() devuelve (string_completo, "", "").
No existe nada identico en JS.

EN RESUMEN: partition/rpartition dividen en 3 partes de una sola vez,
conservando el separador.
"""

print("key=value".partition("="))  # ('key', '=', 'value')
print("a=b=c".rpartition("="))  # ('a=b', '=', 'c')


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
String.fromCharCode(codigo) convierte un numero de codigo a su
caracter, y "A".charCodeAt(0) hace lo inverso: son el equivalente
directo de chr()/ord() (para code points fuera del BMP tambien existen
String.fromCodePoint() y .codePointAt()).

EN PYTHON:
chr(codigo) y ord(caracter) hacen exactamente lo mismo que esas
funciones de JS. Son muy usados en ejercicios de algoritmos que hacen
aritmetica con letras (por ejemplo, un cifrado Cesar que desplaza cada
letra n posiciones).

str.maketrans(origen, destino) construye una tabla de sustitucion
caracter por caracter, que .translate(tabla) aplica sobre un string
completo de un solo golpe. Esto si es exclusivo de Python: no hay
equivalente directo en JS, ahi tocaria armar la sustitucion a mano
(por ejemplo encadenando .replace() o iterando caracter por caracter).

EN RESUMEN: chr/ord si tienen equivalente directo en JS
(String.fromCharCode/.charCodeAt), pero maketrans/translate para
sustitucion masiva de un solo golpe si son exclusivos de Python.
"""

print(ord("A"))  # 65
print(chr(65))  # "A"
print(chr(ord("A") + 1))  # "B"

cipher_table = str.maketrans("abc", "xyz")
print("abcdef".translate(cipher_table))  # "xyzdef"


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
.indexOf() busca una subcadena y devuelve -1 si no la encuentra; nunca
lanza una excepcion. .lastIndexOf() hace lo mismo buscando desde la
derecha.

EN PYTHON:
.find() (ya visto arriba) se comporta como .indexOf(): devuelve -1 si
no encuentra la subcadena. .index() hace lo mismo pero, si no la
encuentra, lanza ValueError en vez de devolver -1. .rfind() y .rindex()
son sus versiones que buscan desde la derecha (equivalentes a
.lastIndexOf()), con la misma diferencia entre ellas. La ultima linea
de abajo demuestra intencionalmente el ValueError de .index().

EN RESUMEN: find/rfind son silenciosos como en JS (-1 si no hay
match); index/rindex son la version que revienta con una excepcion.
"""

print(phrase.index("is"))  # 7
print(phrase.rfind("t"))  # 14
print(phrase.rindex("t"))  # 14
phrase.index("xyz")  # ValueError: substring not found


"""
================================================================================
INDEXING Y SLICING
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Un string se indexa con corchetes igual que un array. No se soportan
indices negativos directamente con []: texto[-1] no da el ultimo
caracter, da undefined.

EN PYTHON:
Un string tambien se indexa con corchetes, pero ademas soporta INDICES
NEGATIVOS para contar posiciones desde el final: word[-1] si da el
ultimo caracter.

EN RESUMEN: mismo [] para indexar, pero en Python los indices negativos
funcionan de fabrica.
"""

word = "python"

print(word[0])  # "p"
print(word[-1])  # "n"
print(len(word))  # 6


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Un array permite reasignar un elemento por indice: arr[0] = "x" si
funciona y modifica el array original.

EN PYTHON:
Los strings son INMUTABLES: una vez creado un string, ninguno de sus
caracteres se puede reasignar por indice. La linea de abajo lanza
TypeError; cualquier "modificacion" de un string en realidad crea un
string COMPLETAMENTE NUEVO en memoria (como hacen .upper(), .replace(),
etc. arriba) y el original queda intacto.

EN RESUMEN: a diferencia de un array de JS, un string de Python nunca
se edita in-place; siempre se crea uno nuevo.
"""

word[0] = "P"  # TypeError: 'str' object does not support item assignment


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
.slice(inicio, fin) extrae una parte del string; no existe un tercer
parametro de paso.

EN PYTHON:
El slicing se escribe directo con corchetes: palabra[inicio:fin:paso].
El inicio SI se incluye, el fin NO se incluye. Cualquiera de los tres
valores se puede omitir (inicio por defecto es 0, fin por defecto es el
final, paso por defecto es 1), y el tercer valor de paso si existe.

EN RESUMEN: mismo concepto que .slice(), pero con sintaxis de corchetes
y un tercer parametro de paso que JS no tiene.
"""

print(word[0:3])  # "pyt"
print(word[:3])  # "pyt"
print(word[3:])  # "hon"
print(word[:])  # "python"
print(word[::2])  # "pto"
print(word[::-1])  # "nohtyp"
print(word[-3:])  # "hon"


"""
[IGUAL QUE JS]

EN JS:
Un string tambien se puede recorrer directo con for...of, sin
convertirlo primero: for (const char of texto) { ... } ya funciona tal
cual, porque los strings son iterables en JS desde ES6. Convertirlo con
[...texto] o texto.split("") solo hace falta cuando de verdad necesitas
un ARRAY (por ejemplo, para usar .map()/.filter() o invertirlo con
.reverse()), no solo para iterar caracter por caracter.

EN PYTHON:
Un for funciona directo sobre el string mismo, sin necesitar
convertirlo primero — igual que for...of en JS.

EN RESUMEN: en ambos lenguajes un string ya es iterable tal cual con un
for; convertirlo a lista/array solo hace falta si necesitas ese array
para otra cosa (ordenar, invertir, usar metodos de array).
"""

for letter in word:
    print(letter)
# p
# y
# t
# h
# o
# n


"""
================================================================================
F-STRINGS Y FORMAT SPECIFIERS
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Un template literal con backticks habilita usar ${ } para insertar
cualquier expresion dentro del texto. Basta con usar backticks en vez
de comillas normales.

EN PYTHON:
La interpolacion se hace con f-strings: una f antes de la comilla de
apertura habilita usar { } (sin el simbolo $) para insertar cualquier
expresion dentro del texto. La letra f es obligatoria antes de la
comilla; una comilla normal NO interpreta las { }, solo las imprime tal
cual.

EN RESUMEN: mismo concepto de interpolacion, pero se activa con una f
antes de la comilla en vez de usar backticks.
"""

name = "Alice"
age = 25

print(f"Hello {name}, you are {age} years old")  # "Hello Alice, you are 25 years old"
print(f"Next year you will be {age + 1}")  # "Next year you will be 26"


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Dentro de las llaves, despues de dos puntos, va un mini-lenguaje de
formato para controlar decimales, separadores de miles y alineacion.

En JS no hay nada equivalente integrado en el lenguaje mismo; para
lograr lo mismo se necesitaria Intl.NumberFormat o padStart/padEnd
manuales.

    especificador     significado
    .2f                2 decimales, notacion fija
    ,                  separador de miles
    >10                alinear a la derecha en un ancho de 10
    <10                alinear a la izquierda en un ancho de 10
    ^10                centrar en un ancho de 10
    03                 rellenar con ceros a la izquierda hasta 3 digitos

EN RESUMEN: el ":" dentro de las llaves abre un mini-lenguaje de
formato que JS no trae incorporado.
"""

price = 15000.5

print(f"{price:.2f}")  # "15000.50"
print(f"{price:,.2f}")  # "15,000.50"
print(f"{price:>15,.2f}")  # "      15,000.50"
print(f"{price:<15,.2f}fin")  # "15,000.50      fin"

number = 7
print(f"{number:03}")  # "007"
print(f"{'centered':^20}")  # "      centered      "


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Un atajo muy usado al depurar: agregar = justo antes de cerrar la
llave imprime la EXPRESION y su valor al mismo tiempo, sin tener que
escribir el nombre dos veces. No existe nada identico en los template
literals de JS.

EN RESUMEN: f"{x=}" imprime "x=42" de un solo golpe, util para prints
rapidos de depuracion.
"""

x = 42
print(f"{x=}")  # "x=42"
print(f"{x * 2=}")  # "x * 2=84"


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
Existen formas mas antiguas de formatear (.format() y el operador %),
que apareceran en codigo de otros que consultes o en cursos viejos, pero
f-strings es la forma moderna, mas legible, y la recomendada para
escribir codigo nuevo.

EN RESUMEN: .format() y % todavia se ven en codigo existente, pero para
codigo nuevo usa f-strings.
"""

print("Hello {}, you are {} years old".format(name, age))  # "Hello Alice, you are 25 years old"
print("Hello %s, you are %d years old" % (name, age))  # "Hello Alice, you are 25 years old"


"""
================================================================================
BYTES VS STR
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

EN JS:
Un string SIEMPRE es texto UTF-16; no existe, a nivel de lenguaje, un
tipo separado de "bytes".

EN PYTHON:
Python separa de forma tajante el TEXTO del contenido BINARIO: str
siempre es texto Unicode, y bytes (con un literal b"...") es una
secuencia de bytes crudos, sin ninguna nocion de caracteres ni
codificacion. Son dos tipos distintos que no se pueden mezclar
directamente ("a" + b"a" lanza TypeError).

.encode() convierte un str a bytes, y .decode() hace lo inverso. Si no
se especifica otra cosa, ambos usan UTF-8 por defecto. Un mal manejo
de codificacion (por ejemplo decodificar bytes de UTF-8 asumiendo otra
codificacion) es la causa tipica de un UnicodeDecodeError.

EN RESUMEN: str es texto, bytes es binario crudo, y encode/decode es el
puente entre los dos.
"""

message = "café"
encoded_message = message.encode()  # UTF-8 por defecto
print(encoded_message)  # b'caf\xc3\xa9'
print(type(encoded_message))  # <class 'bytes'>
print(encoded_message.decode())  # "café"


"""
================================================================================
EXPRESIONES REGULARES (modulo re)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

La sintaxis de los patrones (\\d, \\w, grupos con parentesis, etc.) es
la misma en ambos lenguajes; lo que cambia es la FORMA de usarlos.

EN JS:
El literal /patron/ ES el objeto regex, con metodos .test() y .exec().

EN PYTHON:
No hay literal de regex: el patron es un string normal que se pasa como
argumento a las funciones del modulo re, como re.search(patron, texto).

re.search() busca el patron en CUALQUIER posicion del texto y devuelve
un objeto Match (o None si no encuentra nada). re.match() hace lo
mismo pero solo cuenta si el patron aparece justo al INICIO del texto
(a diferencia de search, no importa si hay coincidencias mas
adelante). re.findall() devuelve TODAS las coincidencias como lista de
strings, y re.sub() reemplaza todas las coincidencias por otro texto.

Para reusar el mismo patron muchas veces conviene precompilarlo con
re.compile(patron), que devuelve un objeto con los mismos metodos
(.search(), .match(), etc.) ya "listo" para no repetir el string del
patron cada vez.

EN RESUMEN: la sintaxis del patron se reusa tal cual de JS; lo que
cambia es que en Python el patron es un string que se pasa a funciones
de re, no un objeto regex propio.
"""

import re

order_text = "order 42 shipped, order 7 pending"

print(re.search(r"\d+", order_text))  # <re.Match object; span=(6, 8), match='42'>
print(re.match(r"\d+", order_text))  # None
print(re.match(r"order", order_text))  # <re.Match object; span=(0, 5), match='order'>
print(re.findall(r"\d+", order_text))  # ['42', '7']
print(re.sub(r"\d+", "#", order_text))  # "order # shipped, order # pending"

digit_pattern = re.compile(r"\d+")
print(digit_pattern.search(order_text))  # <re.Match object; span=(6, 8), match='42'>
print(digit_pattern.findall(order_text))  # ['42', '7']


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Los grupos con parentesis en el patron son accesibles en el resultado
de .exec() o .match() por indice (match[1]) o, si tienen nombre con
(?<nombre>...), en match.groups.nombre.

EN PYTHON:
Para extraer solo una parte de la coincidencia se usan GRUPOS con
parentesis, accesibles con .group(n) (o .group("nombre") si el grupo
tiene nombre con la sintaxis (?P<nombre>...)).

EN RESUMEN: mismo concepto de grupos con parentesis; en Python se leen
con .group(n) o .group("nombre") en vez de indexar el resultado.
"""

order_match = re.search(r"order (\d+)", order_text)
print(order_match.group(1))  # "42"

named_order_match = re.search(r"order (?P<order_id>\d+)", order_text)
print(named_order_match.group("order_id"))  # "42"
