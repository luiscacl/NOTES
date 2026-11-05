# Índice de estudio — Python (viniendo de JS)

Orden recomendado de lectura. Cada archivo agrupa un tema grande completo,
con explicación en bloques de texto en español y código de ejemplo en
inglés sin comentarios de lógica — solo comentarios `#` con el valor real
verificado que produce cada línea, para poder leer sin ejecutar. Basado en
el temario del curso de Bro Code, ampliado con temas avanzados que el
temario no cubre pero que son estándar al dominar el lenguaje, sin los
episodios marcados como proyecto (⭐, esos se practican por tu cuenta
después) y sin la sección de PyQt5 (GUI, framework aparte — se puede
agregar después si hace falta).

1. **01_sintaxis_basica.py** — qué es Python, cómo se ejecuta, indentación,
   `print()`, comentarios, variables, tipado dinámico, constantes, `pass`,
   desempaquetado anidado.
2. **02_tipos_de_datos_y_casting.py** — `int`/`float`/`str`/`bool`/`None`,
   `type()`/`isinstance()`, literales numéricos (guion bajo, notación
   científica, binario/hex/octal, `bin()`/`hex()`/`oct()`), conversión de
   tipos, `input()`.
3. **03_operadores.py** — aritmética, módulo `math`, operadores bit a bit,
   operadores lógicos y su valor real (no solo bool), truthiness, `is` vs
   `==`, comparaciones encadenadas, `in`/`not in`.
4. **04_control_de_flujo.py** — `if`/`elif`/`else`, operador ternario,
   `match`-`case` (con patrones estructurales y guards).
5. **05_strings.py** — literales (escapes, raw strings, multilínea),
   métodos de string casi completos (incluye `center`/`partition`/
   `casefold`/`removeprefix`/`removesuffix`/`translate`/`is*`),
   indexado y slicing, f-strings, `chr()`/`ord()`, bytes vs str
   (`encode`/`decode`), expresiones regulares básicas (`re`).
6. **06_loops.py** — `while`, `for`, `range()`, desempaquetado en `for`,
   loops anidados.
7. **07_colecciones.py** — listas, tuplas (+ `namedtuple`), sets (+
   `frozenset`), diccionarios, colecciones 2D — métodos casi completos de
   cada una, vistas de dict en vivo, spread `*`/`**`, operadores de merge
   de dict (`|`/`|=`), y cuándo usar cada una profesionalmente (con el
   porqué real).
8. **08_funciones.py** — `def`, `return`, argumentos por default, keyword
   arguments, `*args`/`**kwargs`, desempaquetar al llamar, parámetros
   solo-posicionales/solo-por-nombre, type hints (`Optional`/`Union`/`|`),
   lambdas, `functools.reduce`.
9. **09_iterables_y_comprehensions.py** — iterables/iteradores, iterador
   propio (`__iter__`/`__next__`), `enumerate`, `zip`, `any()`/`all()`,
   comprehensions, generadores.
10. **10_modulos_y_scope.py** — `import`, módulos y paquetes, scope
    (LEGB, `global`, `nonlocal`), `if __name__ == "__main__"`.
11. **11_excepciones.py** — `try`/`except`/`else`/`finally`, `raise`,
    `assert`, excepciones propias, `raise ... from`, EAFP vs LBYL.
12. **12_programacion_orientada_a_objetos.py** — clases, `__init__`/`self`,
    variables de clase, herencia (simple y múltiple), `super()`,
    `isinstance`/`issubclass`, polimorfismo, duck typing, métodos
    estáticos/de clase, métodos mágicos (incl. `__repr__`/`__len__`/
    `__contains__`/`__lt__`/`__call__`, y la trampa `__hash__`/`__eq__`),
    `@property`, decoradores (con argumentos y `functools.wraps`), Enum,
    dataclasses, clases abstractas (ABC), y decisiones profesionales
    (composición vs herencia, qué herramienta usar y por qué).
13. **13_archivos_y_stdlib.py** — `random`, archivos (`with`, lectura,
    escritura), context managers propios, `json`, `pathlib`, `datetime`,
    `threading` básico, `requests` básico.
14. **14_complementos_para_algoritmos.py** — recursión, copiar colecciones
    (alias vs copia real, shallow vs deep copy), `sorted()`/`reversed()`,
    métodos adicionales de listas/dicts/sets, `for...else`, unpacking
    extendido, operador morsa `:=`, `divmod()`, múltiples excepciones en
    un `except`, pila/cola con listas y `deque`, parseo de input,
    `Counter`/`defaultdict`, `itertools` (chain/product/permutations/
    combinations).
15. **15_concurrencia_asyncio.py** — `async`/`await`, corrutinas (por qué
    llamarlas no ejecuta nada hasta que algo las impulsa, a diferencia de
    JS), el event loop y su scheduling cooperativo paso a paso,
    `asyncio.sleep()` vs `time.sleep()` (por qué una llamada bloqueante
    congela TODO, con demo cronometrada), `asyncio.gather()`,
    `asyncio.create_task()`, y cuándo usar threading vs asyncio vs
    multiprocessing (y por qué).
16. **16_diseno_y_buenas_practicas.py** — el único archivo de DISEÑO, no
    sintaxis: los 5 principios SOLID con ejemplos, inyección de
    dependencias, patrones de diseño clásicos (Factory/Strategy/
    Singleton/Decorator) y su versión simplificada en Python, jerarquía
    de excepciones para una aplicación completa, cómo organizar un
    proyecto en paquetes por responsabilidad, y guard clauses. Aquí las
    comparaciones son contra Java/C# (el punto de referencia natural para
    OOP formal), no solo contra JS.

Convenciones usadas en todos los archivos:

- La explicación va en bloques `"""..."""` antes de cada bloque de código,
  en español; el código (nombres, strings de ejemplo) está en inglés.
- Cada `print(...)` y cada línea que demuestra un error tiene un
  comentario `#` con el valor/excepción real, verificado ejecutando el
  código — nunca adivinado. Los casos no deterministas (`random`,
  `datetime.now()`, orden de `set`, red, hilos) están marcados como tal.
- Se compara contra JS solo donde el concepto cambia, o se aclara
  explícitamente cuando algo no existe en JS.
- Cada sección lleva una etiqueta visible al inicio: `[IGUAL QUE JS]`,
  `[SIMILAR A JS, CON DIFERENCIAS]`, o `[EXCLUSIVO DE PYTHON — no existe
  en JS]` — para saber de un vistazo qué ya sabes y qué es nuevo.
- Nada de motor interno de CPython — solo sintaxis y uso profesional,
  para escribir código a mano con confianza.
