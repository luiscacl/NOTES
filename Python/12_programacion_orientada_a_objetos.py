"""
================================================================================
CLASES Y OBJETOS
================================================================================
[IGUAL QUE JS]

class define un molde para crear objetos, igual que class en JS (desde
ES6).

Un par de diferencias son solo de nombre, no de concepto: el
constructor no se llama constructor, se llama __init__ (doble guion
bajo a cada lado — un "metodo magico", tema que se retoma mas abajo).
Ademas, el primer parametro de CUALQUIER metodo de instancia es self,
escrito de forma explicita.

EN RESUMEN: el concepto de class es el mismo que en JS; solo cambian
nombres puntuales (constructor -> __init__, this -> self explicito).
"""

class Car:
    def __init__(self, brand, model):
        self.brand = brand
        self.model = model

    def describe(self):
        print(f"{self.brand} {self.model}")

my_car = Car("Toyota", "Corolla")
my_car.describe()  # Toyota Corolla


"""
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
this esta disponible IMPLICITAMENTE dentro de cualquier metodo, sin
necesidad de declararlo como parametro — aunque a que objeto apunta
depende de COMO se llama el metodo: si lo separas del objeto y lo
invocas suelto (const fn = obj.metodo; fn()), this deja de apuntar a
obj (y en modo estricto queda undefined); las arrow functions ni
siquiera tienen su propio this.

EN PYTHON:
self es el equivalente a this, con una diferencia sintactica clave:
self NO es magico ni implicito, es un parametro NORMAL que TODO metodo
de instancia debe declarar explicitamente como su primer parametro.
Python se lo pasa solo cuando llamas al metodo desde un objeto
(my_car.describe() es, por debajo, Car.describe(my_car)).

ORDEN DE EJECUCION al crear un objeto: Car("Toyota", "Corolla") primero
reserva memoria para el objeto nuevo, y despues llama automaticamente a
__init__ pasandole ese objeto como self junto con los demas argumentos.
Todo lo que se le asigne a self dentro de __init__ (self.brand = brand)
queda guardado como ATRIBUTO DE INSTANCIA, propio de ESE objeto en
particular.

EN RESUMEN: self cumple el mismo rol que this, pero en Python hay que
escribirlo a mano como primer parametro de cada metodo; nunca aparece
solo.
"""

other_car = Car("Honda", "Civic")
my_car.describe()  # Toyota Corolla
other_car.describe()  # Honda Civic


"""
================================================================================
VARIABLES DE CLASE
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Un campo static dentro de la clase solo se accede desde la clase misma
(NombreClase.campo); no se "hereda" automaticamente como valor por
defecto legible desde cada instancia.

EN PYTHON:
Una variable declarada dentro de la clase pero FUERA de __init__ (y sin
self.) es una VARIABLE DE CLASE: se comparte entre TODAS las instancias,
en vez de que cada objeto tenga su propia copia independiente como pasa
con los atributos de instancia (self.algo). Ademas, cada instancia
puede leerla como si fuera propia, sin nada especial.

EN RESUMEN: una variable de clase de Python es como un campo static de
JS, pero ademas queda visible desde cualquier instancia como si fuera
un atributo propio (hasta que esa instancia recibe uno realmente
suyo).
"""

class Employee:
    company = "Acme Corp"

    def __init__(self, name):
        self.name = name

employee1 = Employee("Anna")
employee2 = Employee("Bob")

print(employee1.company)  # Acme Corp
print(employee2.company)  # Acme Corp


"""
CUIDADO: leer una variable de clase desde una instancia (employee1.company)
funciona porque Python la busca ahi cuando no la encuentra como atributo
de instancia. Pero ASIGNARLE un valor nuevo desde una instancia
(employee1.company = "Another Company") NO modifica la variable de clase: crea
un atributo de INSTANCIA nuevo que oculta a la de clase, solo para ese
objeto. Para modificar la variable de clase de verdad hay que hacerlo
a traves del nombre de la clase.
"""

employee1.company = "Another Company"
print(employee1.company)  # Another Company
print(employee2.company)  # Acme Corp

Employee.company = "New Global Company"
print(employee2.company)  # New Global Company


"""
================================================================================
HERENCIA
================================================================================
[IGUAL QUE JS]

Una clase puede heredar de otra escribiendo el nombre de la clase padre
entre parentesis.

EN RESUMEN: es lo mismo que extends en JS; solo cambia la sintaxis (la
clase padre va entre parentesis en vez de despues de extends).
"""

class Animal:
    def __init__(self, name):
        self.name = name

    def make_sound(self):
        print("some generic sound")

class Dog(Animal):
    def bark(self):
        print(f"{self.name} says woof")

my_dog = Dog("Rex")
my_dog.make_sound()  # some generic sound
my_dog.bark()  # Rex says woof


"""
Dog no definio su propio __init__, asi que hereda el de Animal tal
cual, sin ninguna palabra clave extra. En JS pasa algo parecido: si
class Dog extends Animal no define su propio constructor, JS genera
uno implicito que solo llama a super(...args) — tampoco hace falta
escribir nada extra unicamente para heredar el comportamiento por
defecto. En los dos lenguajes, un constructor/__init__ propio con su
super() explicito solo hace falta cuando la clase hija va a agregar
logica o campos nuevos al construirse.
"""


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
HERENCIA MULTIPLE

Python permite heredar de VARIAS clases al mismo tiempo, separandolas
con coma.

JS no tiene equivalente directo: una clase con extends solo puede tener
UN padre; lo mas parecido son los mixins manuales, que no son parte del
lenguaje como tal.

EN RESUMEN: la herencia multiple es una funcionalidad nativa de Python
que JS no ofrece de forma directa.
"""

class Flyer:
    def fly(self):
        print(f"{self.name} is flying")

class Swimmer:
    def swim(self):
        print(f"{self.name} is swimming")

class Duck(Animal, Flyer, Swimmer):
    pass

my_duck = Duck("Donald")
my_duck.fly()  # Donald is flying
my_duck.swim()  # Donald is swimming


"""
Cuando dos clases padre tienen un metodo con el MISMO nombre, Python
decide cual usar siguiendo el MRO (Method Resolution Order): revisa las
clases padre en el orden en que se escribieron entre parentesis, de
izquierda a derecha. Duck(Animal, Flyer, Swimmer) prioriza Animal
primero si hubiera un nombre repetido entre las tres.
"""

class LandCreature:
    def move(self):
        print("walks on land")

class WaterCreature:
    def move(self):
        print("swims in water")

class Frog(LandCreature, WaterCreature):
    pass

frog = Frog()
frog.move()  # walks on land
print(Frog.__mro__)
# (<class '__main__.Frog'>, <class '__main__.LandCreature'>, <class '__main__.WaterCreature'>, <class 'object'>)


"""
frog.move() imprime "walks on land", no "swims in water": Frog hereda de
LandCreature y WaterCreature, ambas con un move() distinto, y como
LandCreature aparece PRIMERO entre los parentesis, el MRO la prioriza.
Frog.__mro__ muestra el orden exacto en que Python busca cada metodo.
"""


"""
================================================================================
super()
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
super() da acceso a los metodos de la clase padre desde dentro de la
clase hija, sin tener que escribir el nombre del padre a mano, y se
llama directamente como super(argumentos) en el constructor de la
clase hija. Si la clase hija define un constructor, JS EXIGE llamar a
super(...) antes de poder usar this dentro de ese constructor — usarlo
antes lanza un ReferenceError.

EN PYTHON:
super() cumple el mismo papel, pero con una diferencia de sintaxis:
super() por si solo devuelve un objeto que da acceso al padre, y el
metodo que quieres se llama aparte sobre ese resultado — como
super().__init__(brand), no super(brand). El uso mas comun es un
__init__ de la clase hija que necesita ejecutar TAMBIEN la logica de
inicializacion del padre, ademas de la propia. A diferencia de JS, en
Python self ya existe desde antes de entrar a __init__, asi que no hay
ninguna restriccion de orden: en teoria podrias usar self antes de
llamar a super().__init__(brand) (aunque casi siempre conviene
llamarlo primero, como aqui).

EN RESUMEN: super() resuelve el mismo problema en los dos lenguajes,
pero en Python primero se llama super() y despues, aparte, el metodo
que se necesita sobre ese resultado.
"""

class Vehicle:
    def __init__(self, brand):
        self.brand = brand
        print(f"vehicle created: {self.brand}")

class Motorcycle(Vehicle):
    def __init__(self, brand, engine_size):
        super().__init__(brand)
        self.engine_size = engine_size
        print(f"motorcycle with engine size {self.engine_size}")

motorcycle = Motorcycle("Yamaha", 250)
# vehicle created: Yamaha
# motorcycle with engine size 250


"""
ORDEN DE EJECUCION: al crear Motorcycle("Yamaha", 250), primero se
ejecuta el __init__ de Motorcycle desde el inicio; en cuanto llega a
super().__init__(brand), la ejecucion SALTA a __init__ de Vehicle, lo
corre completo (incluido su propio print), y AL TERMINAR regresa
exactamente a la linea siguiente dentro de __init__ de Motorcycle para
seguir ahi. Sin ese super().__init__(brand), self.brand nunca se
asignaria, porque esa linea vive solo en el __init__ del padre.
"""


"""
================================================================================
isinstance() Y issubclass()
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
instanceof revisa si un objeto es instancia de una clase, y tambien
reconoce las clases padre gracias a la herencia (un Dog construido con
class Dog extends Animal responde true a dog instanceof Animal). JS no
tiene un equivalente directo a issubclass(): comparar dos clases
directamente sin pasar por un objeto no es una operacion estandar del
lenguaje.

EN PYTHON:
isinstance(objeto, Clase) hace lo mismo que instanceof: revisa si un
objeto es instancia de una clase — y ADEMAS de cualquiera de sus clases
padre, gracias a la herencia. Es mas confiable que comparar
type(objeto) == Clase, porque type() exige coincidencia EXACTA de clase
y no reconoce que un Dog tambien "es un" Animal. issubclass(ClaseA,
ClaseB) revisa la relacion entre dos clases directamente, sin necesitar
un objeto de por medio.

EN RESUMEN: isinstance() es el equivalente a instanceof, pero Python
ademas ofrece issubclass() para comparar dos clases sin pasar por un
objeto.
"""

print(isinstance(my_dog, Dog))  # True
print(isinstance(my_dog, Animal))  # True
print(type(my_dog) == Animal)  # False

print(issubclass(Dog, Animal))  # True
print(issubclass(Animal, Dog))  # False


"""
================================================================================
POLIMORFISMO
================================================================================
[IGUAL QUE JS]

Polimorfismo es que distintas clases respondan al MISMO nombre de
metodo, cada una con su propio comportamiento — sin que quien llama al
metodo necesite saber de que clase exacta es el objeto.

EN RESUMEN: funciona igual que en JS, que logra lo mismo con
sobreescritura de metodos entre clase padre e hija.
"""

class Cat(Animal):
    def make_sound(self):
        print(f"{self.name} says meow")

class Cow(Animal):
    def make_sound(self):
        print(f"{self.name} says moo")

animals = [Cat("Whiskers"), Cow("Bessie"), Animal("Generic")]

for animal in animals:
    animal.make_sound()
# Whiskers says meow
# Bessie says moo
# some generic sound


"""
El mismo for llama a .make_sound() en los tres objetos sin ningun if
que distinga de que clase es cada uno; cada objeto "sabe" cual version
de make_sound le corresponde, por ser Cat, Cow o Animal.
"""


"""
================================================================================
DUCK TYPING
================================================================================
[IGUAL QUE JS]

"Si camina como pato y suena como pato, es un pato": ni Python ni JS
exigen que dos objetos compartan una clase padre en comun para poder
usarlos de forma intercambiable, solo que tengan el metodo o atributo
que se les va a usar en ese momento. Los dos son lenguajes dinamicos:
no hay ninguna verificacion de que el metodo exista sino hasta que se
intenta usar, en tiempo de ejecucion — duck typing no es exclusivo de
Python, es el comportamiento normal de JS tambien.

Esto es muy distinto de TypeScript, donde una interfaz formal declara
ese contrato de forma explicita y verificada ANTES de ejecutar el
codigo (aunque, una vez compilado a JS plano, ese chequeo desaparece:
en tiempo de ejecucion TypeScript se comporta igual de "duck typed"
que JS o Python).

EN RESUMEN: duck typing es el comportamiento normal tanto de Python
como de JS puro; solo TypeScript agrega, antes de ejecutar, una capa
de verificacion formal que de todos modos desaparece en tiempo de
ejecucion.
"""

class ToyDuck:
    def make_sound(self):
        print("quack (toy)")

class Person:
    def make_sound(self):
        print("I'm imitating a duck: quack")

def make_it_sound(thing):
    thing.make_sound()

make_it_sound(ToyDuck())  # quack (toy)
make_it_sound(Person())  # I'm imitating a duck: quack


"""
make_it_sound() no pregunta de que clase es "thing": simplemente confia en
que tenga un metodo .make_sound(), sin importar que ToyDuck y
Person no compartan ninguna clase padre en comun.
"""


"""
================================================================================
METODOS ESTATICOS Y DE CLASE
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Un metodo NORMAL recibe self (la instancia). Python agrega dos
variantes con decoradores (los decoradores en general se explican mas
abajo):

@staticmethod: NO recibe self ni la clase. Es una funcion normal que
solo "vive" dentro de la clase por organizacion, sin necesitar ningun
dato de una instancia en particular. (El equivalente mas cercano en JS
es un metodo static.)

@classmethod: recibe cls (la CLASE misma) en vez de self (una
instancia). Se usa sobre todo para constructores alternativos: formas
extra de crear un objeto ademas de __init__. JS no tiene un equivalente
directo a @classmethod.

EN RESUMEN: @staticmethod es parecido a static de JS; @classmethod no
tiene paralelo directo en JS — es la forma pythonica de escribir
constructores alternativos.
"""

class Calculator:
    @staticmethod
    def add(a, b):
        return a + b

print(Calculator.add(3, 4))  # 7

class Date:
    def __init__(self, day, month, year):
        self.day = day
        self.month = month
        self.year = year

    @classmethod
    def from_text(cls, text):
        day, month, year = text.split("/")
        return cls(int(day), int(month), int(year))

    def display(self):
        print(f"{self.day}/{self.month}/{self.year}")

date1 = Date(15, 8, 2024)
date2 = Date.from_text("20/3/2025")

date1.display()  # 15/8/2024
date2.display()  # 20/3/2025


"""
================================================================================
METODOS MAGICOS (DUNDER METHODS)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Comportamientos especiales de un objeto se logran sobreescribiendo
metodos como toString(), valueOf(), o usando los Symbol.* (por ejemplo
Symbol.iterator).

EN PYTHON:
Los metodos que empiezan y terminan con doble guion bajo (__algo__) son
"magicos": Python los llama SOLO por su cuenta ante ciertas situaciones
(crear el objeto, imprimirlo, compararlo, sumarlo), nunca los llamas tu
directo con ese nombre. __init__ ya es uno de ellos. Son mucho mas
usados y variados que sus equivalentes en JS.

EN RESUMEN: los metodos magicos de Python cubren el mismo terreno que
toString()/valueOf()/Symbol.* de JS, pero son muchos mas y estan mucho
mas integrados al lenguaje.
"""

class Point:
    def __init__(self, x, y):
        self.x = x
        self.y = y

    def __str__(self):
        return f"Point({self.x}, {self.y})"

    def __eq__(self, other):
        return self.x == other.x and self.y == other.y

    def __add__(self, other):
        return Point(self.x + other.x, self.y + other.y)

p1 = Point(1, 2)
p2 = Point(1, 2)
p3 = Point(3, 4)

print(p1)  # Point(1, 2)
print(p1 == p2)  # True
print(p1 == p3)  # False
print(p1 + p3)  # Point(4, 6)


"""
Sin __str__, print(p1) mostraria algo como <__main__.Point object at
0x...> (la direccion de memoria del objeto). Un objeto de JS sin
toString() tiene el mismo problema: se muestra como [object Object].

Sin __eq__, p1 == p2 compararia
IDENTIDAD (ver is en 03_operadores.py) en vez de comparar sus atributos,
y daria False aunque tengan el mismo x y y. __add__ es lo que permite
que el operador + funcione entre dos objetos Point — sin definirlo, +
lanzaria TypeError.
"""


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
__str__ vs __repr__

__str__ es la version "para humanos" (la que usa print() y f-strings).
__repr__ es la version "para depurar": la usan la consola interactiva, y
CUALQUIER contenedor (una lista de objetos usa __repr__ de cada
elemento al imprimirse, NUNCA __str__). Si solo defines __str__, una
lista de objetos se sigue viendo con la direccion de memoria por
dentro. Por convencion, __repr__ deberia devolver algo que en teoria
podrias pegar en el codigo para recrear el objeto.

EN RESUMEN: __str__ es para mostrar al usuario; __repr__ es para
depurar y es el que usan los contenedores — conviene definir ambos.
"""

class Coordinate:
    def __init__(self, x, y):
        self.x = x
        self.y = y

    def __str__(self):
        return f"({self.x}, {self.y})"

    def __repr__(self):
        return f"Coordinate({self.x}, {self.y})"

p = Coordinate(1, 2)
print(p)  # (1, 2)
print([p, p])  # [Coordinate(1, 2), Coordinate(1, 2)]


"""
Otros metodos magicos comunes: __len__ (para que len(objeto) funcione),
__contains__ (para que in funcione contra el objeto), y __lt__ (para
que sorted() sepa como ordenar objetos de esta clase entre si,
comparando "menor que").
"""

class Team:
    def __init__(self, name, players):
        self.name = name
        self.players = players

    def __len__(self):
        return len(self.players)

    def __contains__(self, player):
        return player in self.players

    def __lt__(self, other):
        return len(self.players) < len(other.players)

team_a = Team("A", ["Anna", "Bob"])
team_b = Team("B", ["Carol", "Dani", "Eve"])

print(len(team_a))  # 2
print("Anna" in team_a)  # True
print(team_a < team_b)  # True
print(sorted([team_b, team_a], key=len))
# [<__main__.Team object at 0x...>, <__main__.Team object at 0x...>]
# (team_a primero, team_b segundo; Team no define __repr__, asi que se ve
# la direccion de memoria por defecto, que cambia en cada ejecucion)


"""
================================================================================
LA TRAMPA DE __hash__ Y __eq__
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Por defecto, CUALQUIER objeto en Python es "hasheable" (se puede meter en
un set o usar como llave de un dict), usando su identidad en memoria
como base del hash. Pero en cuanto una clase define su propio __eq__
(como Point o Coordinate arriba, que comparan por VALOR en vez de por
identidad), Python DESACTIVA automaticamente __hash__ para esa clase (lo
deja en None) — porque ya no seria seguro asumir que la identidad en
memoria sigue sirviendo como hash. El resultado: esa clase deja de poder
usarse en un set o como llave de dict, aunque ANTES de agregar __eq__ si
funcionaba (todo objeto normal es hasheable de entrada).

EN RESUMEN: agregar __eq__ propio desactiva __hash__ automatico; si
necesitas que la clase siga siendo hasheable, hay que definir __hash__
tambien a mano.
"""

class Money:
    def __init__(self, amount, currency):
        self.amount = amount
        self.currency = currency

    def __eq__(self, other):
        return self.amount == other.amount and self.currency == other.currency

wallet1 = Money(10, "USD")
wallet2 = Money(10, "USD")

print(wallet1 == wallet2)  # True

{wallet1}  # TypeError: cannot use 'Money' as a set element (unhashable type: 'Money')


"""
LA REGLA que hay que respetar: si dos objetos son __eq__-iguales, DEBEN
tener el mismo hash (si no, un set/dict podria guardar "duplicados" que
en teoria son iguales, o fallar en encontrar una llave que en teoria ya
esta ahi — un bug silencioso y dificil de rastrear). Por eso el arreglo
es definir __hash__ a mano, hasheando la MISMA tupla de campos que ya
usa __eq__ para comparar.
"""

class SafeMoney:
    def __init__(self, amount, currency):
        self.amount = amount
        self.currency = currency

    def __eq__(self, other):
        return self.amount == other.amount and self.currency == other.currency

    def __hash__(self):
        return hash((self.amount, self.currency))

price1 = SafeMoney(10, "USD")
price2 = SafeMoney(10, "USD")

print(price1 == price2)  # True
print(len({price1, price2}))  # 1

prices = {price1: "keyboard price"}
print(prices[price2])  # keyboard price


"""
{price1, price2} termina con UN solo elemento, no dos: ademas de ser
__eq__-iguales, ahora tambien comparten hash, asi que el set los
reconoce como "el mismo" y descarta el duplicado. prices[price2]
encuentra el valor guardado bajo price1 por la misma razon: un dict
busca la llave primero por hash, y solo despues confirma con __eq__.
"""


"""
================================================================================
__call__: INSTANCIAS QUE SE COMPORTAN COMO FUNCIONES
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

__call__ permite que una instancia se use directamente con parentesis
(instancia(argumentos)), como si fuera una funcion. Sirve para objetos
que guardan una configuracion fija (como el factor de un multiplicador)
y despues se "usan" repetidamente con esa configuracion ya lista.

EN RESUMEN: __call__ hace que un objeto se pueda "llamar" como funcion,
util para encapsular una configuracion fija que se reutiliza.
"""

class Multiplier:
    def __init__(self, factor):
        self.factor = factor

    def __call__(self, value):
        return value * self.factor

double = Multiplier(2)
triple = Multiplier(3)

print(double(5))  # 10
print(triple(5))  # 15


"""
double(5) no es double.some_method(5): double ES el objeto "tipo
funcion" directamente, gracias a __call__. Cada instancia de Multiplier
guarda su propio factor, asi que double y triple se comportan distinto
aunque comparten la misma clase.
"""


"""
================================================================================
@property
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
get/set dentro de la clase permiten que un metodo se lea o se asigne
como si fuera un atributo normal, controlando por debajo que pasa en
cada caso.

EN PYTHON:
@property logra lo mismo: un metodo se LEE como si fuera un atributo
normal (sin parentesis). En vez de las palabras clave get/set, Python
usa decoradores para marcar cual metodo es el getter (@property) y
cual el setter (@nombre.setter).

EN RESUMEN: mismo concepto que get/set de JS, pero con sintaxis de
decoradores en vez de palabras clave dedicadas.
"""

class Circle:
    def __init__(self, radius):
        self._radius = radius

    @property
    def radius(self):
        return self._radius

    @radius.setter
    def radius(self, value):
        if value <= 0:
            raise ValueError("radius must be positive")
        self._radius = value

    @property
    def area(self):
        return 3.14159 * self._radius ** 2

circle = Circle(5)
print(circle.radius)  # 5
print(circle.area)  # 78.53975

circle.radius = 10
print(circle.area)  # 314.159

circle.radius = -1  # ValueError: radius must be positive


"""
circle.radius se lee SIN parentesis aunque por debajo sea un metodo
(gracias a @property), y circle.radius = 10 pasa ese 10 al metodo
marcado con @radius.setter en vez de simplemente sobreescribir un
atributo sin control. area es una @property de SOLO LECTURA (no tiene
setter): un valor calculado a partir de _radius que se comporta como
atributo, pero no se puede asignar directamente.

El guion bajo en _radius es solo CONVENCION ("uso interno", no lo toques
directo desde afuera de la clase). JS tiene campos realmente privados
con #, y TypeScript los tiene con private; Python no tiene un
equivalente forzado por el lenguaje: cualquier cosa se puede seguir
accediendo si alguien de verdad quiere, la proteccion es solo un
acuerdo entre programadores.
"""


"""
================================================================================
DECORADORES
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
No hay una sintaxis de decoradores madura y estandar en el lenguaje
base; existe una propuesta del TC39 en etapa avanzada (stage 3) que
todavia no es parte oficial de JS. Aun asi, los decoradores SI se usan
mucho en la practica via TypeScript o Babel — frameworks como Angular
o NestJS dependen fuertemente de ellos (@Component, @Injectable,
etc.); lo que falta es que JS estandar los adopte sin depender de
herramientas externas.

EN PYTHON:
Un decorador es una funcion que RECIBE otra funcion, y devuelve una
version nueva de ella (normalmente envuelta con comportamiento extra
antes y/o despues). @staticmethod, @classmethod y @property (arriba)
son decoradores ya construidos por Python; tambien se pueden escribir
decoradores propios, para CUALQUIER funcion, no solo metodos de clase.
Son parte normal y muy usada del lenguaje desde hace años.

EN RESUMEN: los decoradores son una herramienta estandar y muy usada en
Python; en JS todavia no son parte oficial del lenguaje base, aunque se
usan mucho en la practica gracias a TypeScript/Babel y a frameworks que
dependen de ellos.
"""

def measure_time(func):
    def wrapper(*args, **kwargs):
        print(f"calling {func.__name__}")
        result = func(*args, **kwargs)
        print(f"{func.__name__} finished")
        return result
    return wrapper

@measure_time
def say_hello(name):
    print(f"hello {name}")

say_hello("Anna")
# calling say_hello
# hello Anna
# say_hello finished


"""
ORDEN DE EJECUCION: @measure_time arriba de def say_hello(name) es
azucar sintactico para escribir say_hello = measure_time(say_hello) justo
despues de definirla. Es decir, el nombre say_hello YA NO apunta a la
funcion original, sino a wrapper (la funcion que measure_time
devolvio). Por eso, al llamar say_hello("Anna"), en realidad se ejecuta
wrapper("Anna"): primero su propio print, despues llama a la funcion
original guardada en el parametro func, y al final su ultimo print —
todo esto sin que quien llama a say_hello("Anna") tenga que saber que paso
por en medio una capa extra.
"""


"""
[EXCLUSIVO DE PYTHON — no existe en JS]
functools.wraps: CONSERVAR LA IDENTIDAD DE LA FUNCION ORIGINAL

Al decorar una funcion (como arriba), el nombre queda apuntando a la
funcion ENVOLTURA, no a la original — por lo que greet.__name__
terminaria valiendo "wrapper" en vez de "greet", lo cual rompe
herramientas de depuracion y documentacion. @functools.wraps copia esos
metadatos (nombre, docstring) de la funcion original hacia la
envoltura.

EN RESUMEN: al escribir un decorador propio, siempre hay que envolver
la funcion interna con @functools.wraps(func) para que conserve su
identidad original.
"""

from functools import wraps

def log_call(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        print(f"calling {func.__name__}")
        return func(*args, **kwargs)
    return wrapper

@log_call
def greet(name):
    print(f"hello {name}")

greet("Ann")
# calling greet
# hello Ann
print(greet.__name__)  # greet


"""
DECORADORES CON ARGUMENTOS PROPIOS

Para que un decorador acepte SUS PROPIOS argumentos (@repeat(3) en vez
de @repeat), hace falta una capa extra de funcion: una funcion externa
que recibe esos argumentos y devuelve el decorador real, que a su vez
recibe la funcion y devuelve la envoltura.
"""

def repeat(times):
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            for _ in range(times):
                func(*args, **kwargs)
        return wrapper
    return decorator

@repeat(3)
def say_hi():
    print("hi")

say_hi()
# hi
# hi
# hi


"""
================================================================================
ENUM
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
JS puro no tiene nada equivalente a un enum; TypeScript si tiene enum
como parte del lenguaje.

EN PYTHON:
Enum define un tipo con un conjunto FIJO de valores con nombre, en vez
de usar strings o numeros sueltos como "constantes" (ver
01_sintaxis_basica.py). Evita errores de escritura (Status.ACTIVE en
vez de "active", que podrias escribir mal sin que nada te avise) y dice
explicitamente cuales son TODOS los valores validos.

EN RESUMEN: Enum de Python cubre el mismo rol que enum de TypeScript;
JS puro no ofrece nada parecido de forma nativa.
"""

from enum import Enum

class Status(Enum):
    ACTIVE = "active"
    INACTIVE = "inactive"
    BANNED = "banned"

current_status = Status.ACTIVE

print(current_status)  # Status.ACTIVE
print(current_status.name)  # ACTIVE
print(current_status.value)  # active
print(current_status == Status.ACTIVE)  # True


"""
================================================================================
DATACLASSES
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Una interface de TypeScript describe la forma de un dato, pero
desaparece al compilar: no genera nada real en tiempo de ejecucion.

EN PYTHON:
@dataclass genera automaticamente __init__, __repr__ y __eq__ para una
clase que principalmente guarda datos, a partir solo de declarar sus
atributos con anotaciones de tipo — sin escribir a mano el
__init__/self.atributo = atributo repetitivo que se vio al inicio de
este archivo. A diferencia de una interface, @dataclass SI genera una
clase real con comportamiento en tiempo de ejecucion, no solo un tipo
que se usa al revisar el codigo.

EN RESUMEN: @dataclass es parecido en intencion a una interface de
TypeScript, pero produce una clase real y funcional, no solo un
contrato que se borra al compilar.
"""

from dataclasses import dataclass

@dataclass
class Product:
    name: str
    price: float
    in_stock: bool = True

product_a = Product("Keyboard", 49.99)
product_b = Product("Keyboard", 49.99)

print(product_a)  # Product(name='Keyboard', price=49.99, in_stock=True)
print(product_a == product_b)  # True
print(product_a.price)  # 49.99


"""
================================================================================
CLASES ABSTRACTAS (ABC)
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

from abc import ABC, abstractmethod permite marcar una clase como
"abstracta": una clase que NO esta pensada para crear objetos de ella
directamente, sino solo para servir de base, obligando a que cada clase
hija implemente ciertos metodos marcados con @abstractmethod. La ventaja
real es CUANDO aparece el error: en el momento de CREAR el objeto
(TypeError), no mucho despues, cuando por fin se llega a llamar ese
metodo faltante y falla con un AttributeError confuso en medio de la
ejecucion.

(JS puro no tiene ninguna nocion de clase abstracta. TypeScript si la
tiene, con su propio abstract class y metodos abstract — pero esa
verificacion es solo de TypeScript en tiempo de compilacion: al
transpilar a JS plano desaparece, a diferencia de Python, donde ABC
sigue aplicando el chequeo en tiempo real de ejecucion.)

EN RESUMEN: ABC obliga a implementar los metodos abstractos desde el
momento en que se crea el objeto, en vez de descubrir el olvido mucho
despues con un error confuso.
"""

from abc import ABC, abstractmethod

class Shape(ABC):
    @abstractmethod
    def area(self):
        pass

class Square(Shape):
    def __init__(self, side):
        self.side = side

    def area(self):
        return self.side ** 2

square = Square(4)
print(square.area())  # 16

class Triangle(Shape):
    def __init__(self, base, height):
        self.base = base
        self.height = height

triangle = Triangle(3, 4)  # TypeError: Can't instantiate abstract class Triangle without an implementation for abstract method 'area'


"""
Triangle hereda de Shape pero nunca define area(), asi que Python ni
siquiera deja construir el objeto: senala de inmediato, por nombre,
cual metodo abstracto falta. Sin ABC, ese mismo olvido se descubriria
solo hasta que algo intentara llamar triangle.area(), quizas mucho
despues y en un lugar del codigo lejano a donde esta el bug real.
"""


"""
================================================================================
DECISIONES PROFESIONALES: QUE HERRAMIENTA DE OOP USAR, Y POR QUE
================================================================================

COMPOSICION SOBRE HERENCIA — la herencia (HERENCIA arriba) es tentadora
porque se ve "elegante", pero en codigo profesional se usa MENOS de lo
que un principiante espera. El problema real de abusar de la herencia
es el "fragile base class": si Vehicle cambia su __init__, TODAS sus
clases hijas (Motorcycle y cualquier otra) pueden romperse sin que se
haya tocado ni una linea de ellas — quedan fuertemente ACOPLADAS a los
detalles internos del padre. La alternativa, COMPOSICION, es que una
clase simplemente GUARDE una instancia de otra como atributo y delegue
en ella, en vez de heredar su comportamiento.
"""

class Engine:
    def start(self):
        print("engine started")

class Car2:
    def __init__(self, engine):
        self.engine = engine

    def start(self):
        self.engine.start()

car = Car2(Engine())
car.start()  # engine started


"""
La regla practica para decidir: usa herencia SOLO cuando la relacion es
genuinamente "ES-UN" (Dog ES-UN Animal, y siempre lo sera de forma
estable) Y ademas necesitas polimorfismo real (tratar distintas clases
hijas de forma intercambiable, como en POLIMORFISMO arriba). Si la
relacion es "TIENE-UN" o "USA-UN" (un Car TIENE-UN Engine, no ES-UN
Engine), o si solo quieres reusar codigo sin esa relacion conceptual,
composicion es la eleccion mas profesional — es mas facil de probar
(puedes pasarle un Engine falso/mock al hacer pruebas) y no hereda
comportamiento no deseado del padre.

CLASE NORMAL vs DATACLASS vs NAMEDTUPLE (ver namedtuple en
07_colecciones.py) — las tres crean algo que guarda datos, pero para
casos distintos:

    NAMEDTUPLE     inmutable, ligera, ideal para un registro fijo y
                   simple que no necesitara metodos propios (una
                   coordenada, un color).
    DATACLASS      cuando el registro SI necesita metodos propios,
                   valores por defecto mas flexibles, o mutabilidad
                   (@dataclass genera __init__/__repr__/__eq__, pero la
                   clase sigue mutable salvo que agregues
                   @dataclass(frozen=True)).
    CLASE NORMAL   cuando el comportamiento (los metodos) es el punto
                   central de la clase, no solo los datos que carga —
                   herencia real, logica de validacion compleja en
                   @property, polimorfismo, etc.

ENUM EN VEZ DE STRINGS SUELTOS — cualquier vez que un valor solo puede
ser "uno de estos N casos conocidos" (un estado, un rol de usuario, un
tipo de pago), usar Enum en vez de comparar strings sueltos
("active" == status) es la practica profesional: un typo en un string
("activ") no truena hasta que falla en produccion silenciosamente,
mientras que Status.ACTIV directamente no existe y el editor/Python lo
señala de inmediato.
"""


"""
================================================================================
__slots__
================================================================================
[EXCLUSIVO DE PYTHON — no existe en JS]

Por defecto, cada instancia guarda sus atributos en un diccionario
interno, lo que permite agregarle atributos nuevos en cualquier
momento (self.algo_nuevo = 5 funciona aunque __init__ nunca lo haya
mencionado). __slots__ declara de antemano la LISTA COMPLETA y FIJA de
atributos que la clase va a tener, desactivando ese diccionario
interno. La razon profesional para usarlo no es solo memoria (aunque
para miles/millones de instancias si ahorra una cantidad real): tambien
ATRAPA TYPOS que de otra forma pasarian en silencio. Sin __slots__,
self.naem = "typo" simplemente crea un atributo nuevo sin quejarse;
con __slots__, esa misma linea lanza AttributeError de inmediato.

EN RESUMEN: __slots__ fija de antemano los atributos permitidos, ahorra
memoria en instancias masivas, y atrapa typos que de otro modo crearian
atributos fantasma en silencio.
"""

class Vector2D:
    __slots__ = ["x", "y"]

    def __init__(self, x, y):
        self.x = x
        self.y = y

vector = Vector2D(3, 4)
print(vector.x, vector.y)  # 3 4

vector.z = 10  # AttributeError: 'Vector2D' object has no attribute 'z' and no __dict__ for setting new attributes


"""
================================================================================
typing.Protocol: TIPADO ESTRUCTURAL (EL PARALELO A UNA INTERFACE DE TS)
================================================================================
[SIMILAR A JS, CON DIFERENCIAS]

EN JS:
Una interface de TypeScript describe que forma debe tener un objeto sin
exigir herencia de una clase en particular — es tipado ESTRUCTURAL: dos
objetos sin relacion entre si pueden cumplir la misma interface con tal
de tener la forma correcta. TypeScript lo verifica ANTES de ejecutar el
programa.

EN PYTHON:
DUCK TYPING (visto arriba) es informal: Python no verifica nada hasta
que el codigo se ejecuta e intenta usar un metodo que quiza no existe.
typing.Protocol formaliza esa misma idea de manera parecida a una
interface de TypeScript: describe QUE METODOS/ATRIBUTOS debe tener un
objeto, sin exigir que herede de una clase en particular (a diferencia
de una interface de Java/C#, que si exige "implements" explicito). Las
herramientas de tipado (como mypy) lo verifican ANTES de ejecutar el
programa; en tiempo de ejecucion sigue comportandose como duck typing
normal.

EN RESUMEN: typing.Protocol es el paralelo a una interface de
TypeScript (tipado estructural, verificado antes de ejecutar), pero en
tiempo de ejecucion Python sigue sin exigir nada — solo mypy y
herramientas similares hacen cumplir el contrato.
"""

from typing import Protocol

class Playable(Protocol):
    def play(self) -> None:
        ...

class Song:
    def play(self) -> None:
        print("playing a song")

class Video:
    def play(self) -> None:
        print("playing a video")

def start_playback(item: Playable) -> None:
    item.play()

start_playback(Song())  # playing a song
start_playback(Video())  # playing a video
