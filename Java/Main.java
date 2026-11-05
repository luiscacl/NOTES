/* 

PREGUNTAS MAS IMPORTANTES ANTES DE DECLARAR CUALQUIER COSA

1. ¿Qué estoy declarando?
atributo, método, clase, constructor, interfaz

2. ¿Qué tipo es o qué devuelve?
int, String, void, objeto, etc.

3. ¿Quién puede usarlo?
public, private, protected o sin modificador

4. ¿Necesita pertenecer al objeto o a la clase?
normal o static

5. ¿Debe cambiar o quedar fijo?
normal o final

6. ¿Esto está incompleto y otra clase debe completarlo?
normal o abstract

7. ¿Se usará desde fuera o solo internamente?



---------------------------------- Capa 1: primero divide todo lo que declaras en grupos ----------------------------------

A. ¿Estoy declarando una clase o estructura?
- class
- interface
- enum

B. ¿Estoy declarando un atributo?
Una variable que pertenece a la clase/objeto.
- private String nombre;

C. ¿Estoy declarando un método?
- public void saludar() {}

D. ¿Estoy declarando un constructor?
- public Persona(String nombre) {}



---------------------------------- Capa 2: cada grupo acepta cosas distintas ----------------------------------

Piensa:
“según lo que estoy declarando, solo ciertas palabras tienen sentido”



---------------------------------- Capa 3: categorías de modificadores más importantes ----------------------------------

Categoría 1: ACCESO --- ¿Quién puede usarlo?
- public
- private
- protected
- sin modificador (default / package-private)

Categoría 2: PERTENENCIA --- ¿Pertenece al objeto o a la clase?
- static

Categoría 3: BLOQUEO / RESTRICCIÓN --- ¿Puede cambiar, sobrescribirse o heredarse?
- final
En variable -> no puede reasignarse
En método -> no puede sobrescribirse
En clase -> no puede heredarse

Categoría 4: INCOMPLETO / BASE --- ¿Esto está incompleto y otra clase debe completarlo? Herramienta cuando quieres una base común compartida
- abstract
En método -> una subclase debe implementarlo
En clase -> no se puede instanciar directamente



---------------------------------- Capa 4: uso de modificadores según lo que se declara ----------------------------------

CLASE
- public
- sin modificador
- abstract
- final

ATRIBUTO
- public / private / protected / sin modificador
- static
- final

MÉTODO
- public / private / protected / sin modificador
- static
- final
- abstract

CONSTRUCTOR
- public / private / protected / sin modificador

VARIABLE LOCAL
- casi ninguno de estos



---------------------------------- Capa 5: estructura mental general ----------------------------------

Atributo:
[acceso] [static] [final] tipo nombre

Método:
[acceso] [static] [final/abstract] tipo nombre(...)

Clase:
[acceso] [final/abstract] class Nombre

Constructor:
[acceso] Nombre(...)


*/

public class Main {
    public static void main(String[] args) {
    }
}
