"""
================================================================================
POR QUE ESTE ARCHIVO ES DISTINTO A LOS DEMAS
================================================================================

Todos los archivos anteriores son sobre SINTAXIS: que palabra clave
existe, que hace, como se compara con JS. Este archivo es sobre DISEÑO:
como estructurar un programa completo usando esas piezas, siguiendo
principios que se usan en la industria sin importar el lenguaje.

Por eso aqui las comparaciones no siempre son contra JS. Para varios de
estos principios el punto de referencia mas natural es un lenguaje con
OOP mas formal como Java o C#, porque ahi estas ideas se volvieron
convencion escrita en el lenguaje mismo (interfaces, modificadores de
acceso). Python las adopta como ESTILO y CONVENCION, no como reglas que
el interprete fuerce.
"""


"""
================================================================================
SOLID: CINCO PRINCIPIOS DE DISEÑO ORIENTADO A OBJETOS
================================================================================

SOLID es un acronimo de cinco principios pensados originalmente para
lenguajes como Java, pero aplicables en cualquier OOP. La idea central
detras de los cinco: el codigo debe ser facil de EXTENDER sin tener que
MODIFICAR lo que ya funciona y ya fue probado.
"""


"""
S — SINGLE RESPONSIBILITY (responsabilidad unica)

Una clase deberia tener UN SOLO motivo para cambiar. Si una clase mezcla
dos responsabilidades distintas, un cambio en una puede romper la otra
sin que tenga nada que ver.
"""

class ReportBad:
    def __init__(self, data):
        self.data = data

    def calculate_total(self):
        return sum(self.data)

    def save_to_file(self, filename):
        with open(filename, "w") as file:
            file.write(str(self.calculate_total()))


"""
ReportBad mezcla LOGICA DE NEGOCIO (calcular un total) con
INFRAESTRUCTURA (escribir a disco). Si mañana el reporte se debe guardar
en una base de datos en vez de un archivo, hay que tocar la misma clase
que calcula el total — un cambio de infraestructura arriesga romper una
regla de negocio que no tenia nada que ver.
"""

class Report:
    def __init__(self, data):
        self.data = data

    def calculate_total(self):
        return sum(self.data)

class ReportFileWriter:
    def save(self, report, filename):
        with open(filename, "w") as file:
            file.write(str(report.calculate_total()))


"""
EN RESUMEN: si al describir una clase necesitas usar la palabra "y"
("calcula el total Y lo guarda"), probablemente son dos
responsabilidades que deberian ser dos clases.
"""


"""
O — OPEN/CLOSED (abierto/cerrado)

Un modulo deberia estar ABIERTO a extension pero CERRADO a
modificacion: agregar un caso nuevo no deberia obligarte a editar
codigo ya existente y ya probado.
"""

class DiscountBad:
    def calculate(self, customer_type, amount):
        if customer_type == "regular":
            return amount
        elif customer_type == "premium":
            return amount * 0.9
        elif customer_type == "vip":
            return amount * 0.8
        # cada tipo de cliente nuevo obliga a volver a editar este metodo


"""
Cada vez que se agrega un tipo de cliente hay que volver a abrir
DiscountBad y agregar otro elif — arriesgando romper los casos que ya
funcionaban. La alternativa es apoyarse en POLIMORFISMO (ver
12_programacion_orientada_a_objetos.py): cada tipo de descuento es su
propia clase, y agregar un tipo nuevo significa agregar una clase
nueva, sin tocar las que ya existen.
"""

class Discount:
    def calculate(self, amount):
        return amount

class PremiumDiscount(Discount):
    def calculate(self, amount):
        return amount * 0.9

class VipDiscount(Discount):
    def calculate(self, amount):
        return amount * 0.8

def charge(amount, discount):
    return discount.calculate(amount)

print(charge(100, PremiumDiscount()))  # 90.0
print(charge(100, VipDiscount()))  # 80.0


"""
EN RESUMEN: si agregar un caso nuevo significa agregar un elif mas a una
cadena que ya es larga, es señal de que el codigo deberia extenderse con
una clase nueva en vez de modificar la que ya existe.
"""


"""
L — LISKOV SUBSTITUTION (sustitucion de Liskov)

Un objeto de una clase hija deberia poder USARSE en cualquier lugar
donde se espera un objeto de la clase padre, SIN que el programa se
comporte de forma incorrecta o inesperada.
"""

class RectangleBad:
    def __init__(self, width, height):
        self.width = width
        self.height = height

    def area(self):
        return self.width * self.height

class SquareBad(RectangleBad):
    def __init__(self, side):
        super().__init__(side, side)

    def set_width(self, width):
        self.width = width
        self.height = width

    def set_height(self, height):
        self.width = height
        self.height = height


"""
SquareBad "es un" RectangleBad en el sentido de herencia de Python, pero
NO se comporta como uno: si un codigo generico hace
rectangle.set_width(4); rectangle.set_height(5) esperando area 20,
un SquareBad silenciosamente termina con area 25 porque cambiar un lado
cambia el otro. SquareBad rompe una expectativa razonable de quien usa
un RectangleBad — viola Liskov. La correccion tipica es NO forzar esa
relacion de herencia cuando el comportamiento no es verdaderamente
intercambiable: Square y Rectangle pueden compartir una interfaz comun
(algo con .area()) sin que uno herede del otro.
"""

print(f"{SquareBad(4).area()=}")  # SquareBad(4).area()=16


"""
EN RESUMEN: si usar una clase hija en vez de la clase padre puede
sorprender a quien llama el codigo (un resultado que no esperaria), la
jerarquia de herencia esta mal planteada, sin importar que
conceptualmente "suene" correcta.
"""


"""
I — INTERFACE SEGREGATION (segregacion de interfaces)

EN JAVA:
Una interface muy grande, con metodos que no todas las clases que la
implementan realmente necesitan, obliga a esas clases a implementar
metodos irrelevantes (o lanzar UnsupportedOperationException), solo
para cumplir con el contrato completo.

EN PYTHON:
Con typing.Protocol (visto en 12_programacion_orientada_a_objetos.py) el
mismo riesgo existe si se define un solo Protocol gigante. La correccion
es la misma idea: varios Protocols PEQUEÑOS y enfocados, y que cada
clase implemente solo los que de verdad necesita.
"""

from typing import Protocol

class Printable(Protocol):
    def print_document(self) -> None: ...

class Scannable(Protocol):
    def scan_document(self) -> None: ...

class SimplePrinter:
    def print_document(self):
        print("printing...")

class AllInOnePrinter:
    def print_document(self):
        print("printing...")

    def scan_document(self):
        print("scanning...")

def use_printer(device: Printable):
    device.print_document()

use_printer(SimplePrinter())  # printing...
use_printer(AllInOnePrinter())  # printing...


"""
SimplePrinter nunca tuvo que "fingir" que sabe escanear solo para
calificar como Printable — cada Protocol pide solo lo que de verdad usa.

EN RESUMEN: preferir varios contratos chicos y especificos sobre uno
solo grande evita forzar a una clase a implementar cosas que no le
corresponden.
"""


"""
D — DEPENDENCY INVERSION (inversion de dependencias)

El codigo de alto nivel (la logica de negocio) NO deberia depender
directamente de una implementacion concreta y especifica de bajo nivel
(un servicio de email en particular, una base de datos en particular) —
deberia depender de una ABSTRACCION (un Protocol, una interface), y la
implementacion concreta se le entrega desde afuera. A esto tambien se le
llama INYECCION DE DEPENDENCIAS, y se retoma con mas detalle abajo.
"""

class NotificationSender(Protocol):
    def send(self, message: str) -> None: ...

class EmailSender:
    def send(self, message: str) -> None:
        print(f"sending email: {message}")

class SmsSender:
    def send(self, message: str) -> None:
        print(f"sending sms: {message}")

class OrderService:
    def __init__(self, sender: NotificationSender):
        self.sender = sender

    def complete_order(self, order_id):
        self.sender.send(f"order {order_id} completed")

service_with_email = OrderService(EmailSender())
service_with_email.complete_order(42)  # sending email: order 42 completed

service_with_sms = OrderService(SmsSender())
service_with_sms.complete_order(42)  # sending sms: order 42 completed


"""
OrderService nunca menciona EmailSender ni SmsSender por su nombre en su
propia logica — solo sabe que recibe "algo que puede send()". Cambiar de
email a SMS (o agregar un tercer canal) no requiere tocar OrderService
en absoluto.

EN RESUMEN: si una clase de logica de negocio importa y crea
directamente sus propias dependencias concretas (bases de datos,
servicios externos), sera dificil de probar y de cambiar; si las RECIBE
como parametro contra una abstraccion, se vuelve flexible y facil de
probar con una version falsa.
"""


"""
================================================================================
INYECCION DE DEPENDENCIAS (dependency injection)
================================================================================

Ya se vio la idea arriba con OrderService, pero vale la pena nombrarla
aparte porque es EL patron mas usado en codigo profesional para lograr
bajo acoplamiento: en vez de que una clase construya sus propias
dependencias por dentro (con new/llamando al constructor directo), se
las "inyecta" quien la crea, normalmente por parametro del constructor.
"""

class ReportGeneratorBad:
    def __init__(self):
        self.writer = ReportFileWriter()

    def generate(self, report, filename):
        self.writer.save(report, filename)


"""
ReportGeneratorBad esta ATADO para siempre a ReportFileWriter: para
probarlo sin tocar el disco de verdad, o para cambiarlo a guardar en la
nube, hay que editar esta clase. La version con inyeccion recibe el
writer ya construido desde afuera:
"""

class ReportGenerator:
    def __init__(self, writer):
        self.writer = writer

    def generate(self, report, filename):
        self.writer.save(report, filename)

generator = ReportGenerator(ReportFileWriter())
generator.generate(Report([10, 20, 30]), "report.txt")


"""
EN RESUMEN: pasa las dependencias como parametro en vez de crearlas
adentro de la clase que las usa — hace que cada pieza se pueda probar y
reemplazar por separado, sin editar el resto del sistema.
"""


"""
================================================================================
PATRONES DE DISEÑO CLASICOS, VERSION PYTHON
================================================================================

Muchos patrones de diseño (del libro clasico de la "Gang of Four",
pensado para lenguajes como Java/C++) existen para rodear limitaciones
que esos lenguajes tienen y Python no tiene: falta de funciones de
primera clase, tipado estatico rigido, ausencia de duck typing. En
Python, la version "profesional" de varios patrones es simplemente mas
simple que su version clasica.

FACTORY (fabrica) — en Java se usa para no exponer directamente el
constructor y decidir que subclase crear. En Python, una funcion normal
que devuelve el objeto correcto ya cumple ese papel, sin necesitar una
clase Factory dedicada:
"""

def create_discount(customer_type):
    if customer_type == "premium":
        return PremiumDiscount()
    if customer_type == "vip":
        return VipDiscount()
    return Discount()

print(charge(100, create_discount("vip")))  # 80.0


"""
STRATEGY (estrategia) — en Java se modela con una interface y varias
clases, una por cada algoritmo intercambiable. En Python, si el
"algoritmo" no necesita guardar estado propio, alcanza con pasar una
FUNCION directamente (ver 08_funciones.py) en vez de crear toda una
jerarquia de clases:
"""

def sort_by_price(item):
    return item["price"]

def sort_by_name(item):
    return item["name"]

products = [{"name": "b", "price": 20}, {"name": "a", "price": 10}]

print(sorted(products, key=sort_by_price))  # [{'name': 'a', 'price': 10}, {'name': 'b', 'price': 20}]
print(sorted(products, key=sort_by_name))  # [{'name': 'a', 'price': 10}, {'name': 'b', 'price': 20}]


"""
SINGLETON (instancia unica) — en Java se implementa con un constructor
privado y un metodo estatico que controla que solo exista una instancia.
En Python casi nunca hace falta escribir esa maquinaria: un MODULO
completo ya se comporta como singleton por si solo (ver
10_modulos_y_scope.py — Python solo ejecuta y carga un modulo la
PRIMERA vez que se importa, sin importar cuantas veces se vuelva a
importar despues), asi que un valor definido a nivel de modulo ya es,
de facto, una unica instancia compartida por todo el programa:
"""

_shared_counter = {"count": 0}  # este dict a nivel de modulo ya actua como unica instancia compartida

def increment():
    _shared_counter["count"] += 1
    return _shared_counter["count"]

print(increment())  # 1
print(increment())  # 2


"""
Cualquier otra funcion del programa que llame increment() opera sobre el
MISMO _shared_counter — no hay forma de crear una segunda copia
independiente, igual que un Singleton clasico.

DECORATOR (decorador) — en Java se implementa envolviendo un objeto
dentro de otro que implementa la misma interface. En Python este patron
esta integrado directamente en el lenguaje con la sintaxis @decorador
(ver DECORADORES en 12_programacion_orientada_a_objetos.py) — no hace
falta construir manualmente la jerarquia de clases para lograrlo:
"""

def logged(func):
    def wrapper(*args, **kwargs):
        print(f"calling {func.__name__}")
        return func(*args, **kwargs)
    return wrapper

@logged
def add(a, b):
    return a + b

result = add(2, 3)  # calling add
print(result)  # 5


"""
add() quedo "envuelto" por wrapper sin que add ni quien lo llama tengan
que saber que existe un wrapper — logged() cumple el mismo papel que la
clase decoradora de Java, pero como funcion.

EN RESUMEN: antes de implementar un patron de diseño clasico completo en
Python, vale la pena preguntarse si una funcion, un modulo, o la sintaxis
nativa del lenguaje ya resuelven el mismo problema de forma mas simple.
"""


"""
================================================================================
JERARQUIA DE EXCEPCIONES PARA UNA APLICACION COMPLETA
================================================================================

11_excepciones.py mostro como crear UNA excepcion propia. En una
aplicacion real, la practica profesional es crear una JERARQUIA: una
excepcion BASE para toda la aplicacion, y excepciones especificas que
heredan de ella. Esto permite a quien llama el codigo decidir que tan
especifico quiere ser al capturar errores.
"""

class AppError(Exception):
    pass

class ValidationError(AppError):
    pass

class NotFoundError(AppError):
    pass

def find_user(user_id):
    if user_id <= 0:
        raise ValidationError("user_id must be positive")
    if user_id != 1:
        raise NotFoundError(f"user {user_id} not found")
    return {"id": 1, "name": "Ann"}

try:
    find_user(99)
except NotFoundError as error:
    print(f"not found: {error}")  # not found: user 99 not found

try:
    find_user(-5)
except AppError as error:
    print(f"app error: {error}")  # app error: user_id must be positive


"""
El segundo try captura AppError, la clase BASE, y por herencia tambien
atrapa ValidationError sin necesitar saber ese detalle — util en un
punto general del programa (como el manejador de errores de un
servidor) que solo quiere decir "algo de MI aplicacion fallo" sin
importar cual, mientras que codigo mas especifico si puede capturar
NotFoundError o ValidationError por separado cuando la diferencia
importa.

EN RESUMEN: en una aplicacion real, no crees excepciones propias sueltas
sin relacion entre si — hazlas heredar todas de una excepcion base
comun para tu aplicacion, asi el codigo que las llama puede capturar tan
amplio o tan especifico como necesite.
"""


"""
================================================================================
ESTRUCTURA DE UN PROYECTO REAL (PAQUETES POR RESPONSABILIDAD)
================================================================================

10_modulos_y_scope.py explico que ES un paquete. La practica profesional
es organizar los modulos de un proyecto por RESPONSABILIDAD, no por tipo
de archivo, y evitar que dos modulos se importen el uno al otro
(import circular, que Python no permite resolver y lanza ImportError).

Una forma comun de organizar un proyecto mediano:

    mi_proyecto/
        models/         clases que representan los datos (User, Order)
        services/        logica de negocio (OrderService, PaymentService)
        repositories/     acceso a datos (leer/escribir en base de datos
                          o archivos)
        utils/            funciones auxiliares sin logica de negocio
        main.py           punto de entrada (if __name__ == "__main__")

La regla practica para evitar imports circulares: las capas "de abajo"
(models, utils) nunca deberian importar nada de las capas "de arriba"
(services) — la dependencia va en un solo sentido. Si dos modulos
necesitan conocerse mutuamente, es señal de que algo de esa logica
deberia vivir en un tercer modulo del que ambos dependan.

EN RESUMEN: separa el codigo por lo que HACE (datos, logica de negocio,
acceso a datos) en vez de mezclarlo todo en un solo archivo gigante, y
manten las dependencias entre esas partes fluyendo en una sola
direccion.
"""


"""
================================================================================
CODIGO LIMPIO: GUARD CLAUSES EN VEZ DE ANIDAR IFs
================================================================================

Una practica de legibilidad muy usada en Python (y en general): salir
temprano de una funcion con return/raise ante los casos invalidos
primero (GUARD CLAUSES), en vez de anidar el caso "bueno" varios niveles
adentro de ifs.
"""

def process_order_bad(order):
    if order is not None:
        if order.get("items"):
            if order.get("paid"):
                return "processing"
            else:
                return "unpaid"
        else:
            return "empty order"
    else:
        return "no order"

def process_order_good(order):
    if order is None:
        return "no order"
    if not order.get("items"):
        return "empty order"
    if not order.get("paid"):
        return "unpaid"
    return "processing"

print(process_order_good({"items": [1], "paid": True}))  # processing
print(process_order_good({"items": [], "paid": True}))  # empty order


"""
Ambas funciones hacen exactamente lo mismo, pero process_order_good
nunca anida mas de un nivel: cada guard clause descarta un caso invalido
y sale de inmediato, dejando el caso "todo bien" al final, sin
indentacion extra.

EN RESUMEN: revisa primero los casos invalidos y sal de la funcion de
inmediato (return/raise); deja el caso principal sin anidar al final —
es mucho mas facil de leer que ir agregando un else por cada nivel.
"""
