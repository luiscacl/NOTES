package com.practice.tools.quality.git;

/*
    =====================================================================
    GIT: UN REPASO DE LO QUE SALVA EN EL TRABAJO
    =====================================================================

    Ruta de tools/: guía 12  (quality; la última de la sección 9)
    Anterior:  libraries/jackson/Jackson
    Siguiente: v10_design/  (sección 10; la 11 a la 15 siguen en orden)

    Cómo leer esta guía:
    - Es un REPASO: supone que ya haces add, commit, push y pull a diario.
      Se enfoca en lo que más se confunde y en lo que te saca de problemas.
    - No tiene código Java propio. Las comprobaciones son GitTest: cada
      prueba crea repositorios en una carpeta temporal y corre Git DE
      VERDAD (Git 2.51). Desde v9_tools/:
          ./mvnw test -Dtest=GitTest           (PowerShell: .\mvnw.cmd)
      Tarda cerca de un minuto en Windows: ahí arrancar CUALQUIER proceso
      cuesta ~180 ms (medido; en Linux, ~1 ms) y la prueba llama a git unas
      150 veces.
    - Para practicar sin miedo: haz lo mismo en una carpeta de prueba
      (git init), nunca en el repositorio del trabajo.

    Importancia: ALTA. Un error con Git puede borrar el trabajo de un
    compañero; saber deshacer es lo que te hace confiable en un equipo.

    Índice:
        CAPA 1)  El modelo: fotos, punteros y tres lugares
        CAPA 2)  Commits que sirven (y lo que nunca se sube)
        CAPA 3)  Merge y conflictos
        CAPA 4)  Rebase: la historia limpia y su regla de oro
        CAPA 5)  Deshacer: restore, reset, revert y reflog
        CAPA 6)  Guardar y copiar: stash y cherry-pick
        CAPA 7)  Encontrar el commit que rompió algo: bisect
        CAPA 8)  Con el equipo: push rechazado y force-with-lease
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public final class Git {

    private Git() {
    }

    /*
        =================================================================
        CAPA 1) EL MODELO: FOTOS, PUNTEROS Y TRES LUGARES
        =================================================================

        Casi todo lo confuso de Git se aclara con tres ideas.

        1) Un COMMIT es una FOTO completa del proyecto (no una lista de
           cambios) más quién, cuándo, el mensaje y el commit ANTERIOR (su
           padre). Medido con git cat-file -p HEAD:
               tree <hash>                     la foto de los archivos
               author Ana <ana@example.com> 1793893681 -0600
               committer Ana <ana@example.com> ...
               Agregar lista de precios
           (el primer commit no tiene "parent"; los demás sí). Su hash sale
           de TODO eso: si cambias cualquier cosa, es otro commit con otro
           hash (CAPA 4).

        2) Una RAMA es solo un PUNTERO a un commit. Medido: el archivo
           .git/refs/heads/main pesa 41 bytes (el hash de 40 caracteres y
           un salto de línea). Crear una rama es crear ese archivo: cuesta
           nada. HEAD dice en qué rama estás.

        3) Tus cambios viven en TRES lugares:
               carpeta de trabajo  ->  git add  ->  staging (lo preparado)
               staging             ->  git commit -> el repositorio
           git status los muestra en dos columnas: "M " está preparado,
           " M" está modificado pero no preparado. Esto es lo que mueven los
           distintos reset (CAPA 5).
    */

    /*
        =================================================================
        CAPA 2) COMMITS QUE SIRVEN (Y LO QUE NUNCA SE SUBE)
        =================================================================

        Un buen commit hace UNA cosa (un arreglo, una funcionalidad) y
        compila y pasa las pruebas por sí solo. Así se puede revisar,
        revertir (CAPA 5) o copiar (CAPA 6) sin arrastrar otras cosas.

        El mensaje:
            Corregir el IVA en pedidos con descuento          <- resumen corto,
                                                                 en imperativo
            El descuento se aplicaba después del IVA, así     <- el POR QUÉ
            que el impuesto salía sobre el precio completo.      (el qué ya lo
            Se aplica antes, como pide el SAT.                   dice el diff)

        "cambios", "fix", "wip" no le dicen nada a quien lo lea en un año
        (que puedes ser tú, buscando por qué existe una línea: CAPA 7).
        git add -p te deja elegir PARTES de un archivo para separar dos
        cambios mezclados en dos commits.

        .gitignore: lo que NUNCA entra al repositorio:
            target/  build/                 lo generado (se reconstruye)
            .idea/  .vscode/ (casi todo)    configuración personal del IDE
            *.log
            .env  *.pem                     secretos

        FINES DE LÍNEA. Windows termina las líneas con CRLF; Linux y macOS con
        LF. En Windows, Git suele venir con core.autocrlf=true y convierte al
        sacar y al guardar. Medido en esta máquina, al hacer commit:
            warning: in the working copy of 'f.txt', LF will be replaced by
            CRLF the next time Git touches it
        En un equipo mixto eso produce diffs donde "cambió" cada línea sin
        cambiar nada, y scripts .sh que fallan en Linux por el CR. La
        solución es un archivo .gitattributes en el repositorio (aplica a
        todos, sin depender de la configuración de cada quien):
            * text=auto eol=lf
            *.cmd text eol=crlf          (los scripts de Windows sí con CRLF)
        (GitTest apaga autocrlf justamente para que sus comparaciones no
        dependan de tu configuración.)

        Un SECRETO (contraseña, token, llave) que ya se subió NO se arregla
        borrándolo en otro commit: sigue en la historia y en cada copia que
        ya existe. Hay que CAMBIARLO (revocar la llave y crear otra) y avisar
        a tu equipo de seguridad. Por eso las llaves van en variables de
        entorno o archivos fuera del repositorio
        (v15_spring/final_project/README.md, "Decisiones").
    */

    /*
        =================================================================
        CAPA 3) MERGE Y CONFLICTOS
        =================================================================

        git merge feature trae los commits de feature a tu rama. Dos casos:

        FAST-FORWARD: si tu rama no avanzó desde que salió feature, Git solo
        mueve el puntero (CAPA 1). Medido:
            Updating ed50010..0008357
            Fast-forward
        No crea commit nuevo. (Con --no-ff se fuerza un commit de merge, si
        el equipo quiere ver en la historia dónde entró cada rama.)

        MERGE DE VERDAD: las dos avanzaron. Git combina, y si las dos
        cambiaron LA MISMA línea, no adivina. Medido: main bajó el precio a
        95 y la rama "a" lo subió a 110:
            CONFLICT (content): Merge conflict in precios.txt
            Automatic merge failed; fix conflicts and then commit the result.
        El archivo quedó así:
            <<<<<<< HEAD
            precio=95                   lo de TU rama
            =======
            precio=110                  lo que llega
            >>>>>>> a
        y git status --short lo marcó "UU" (los dos lo cambiaron).

        Resolver: editas el archivo dejando lo correcto (que puede ser una
        mezcla, o algo nuevo: es una decisión de NEGOCIO, pregunta si no
        sabes), quitas las marcas, git add y git commit. Después, compila y
        corre las pruebas: un merge sin conflictos también puede romper (dos
        cambios que por separado estaban bien).

        Si te arrepientes a la mitad: git merge --abort deja todo como
        antes (medido: git status quedó vacío).
    */

    /*
        =================================================================
        CAPA 4) REBASE: LA HISTORIA LIMPIA Y SU REGLA DE ORO
        =================================================================

        git rebase main (estando en feature) toma TUS commits y los vuelve a
        aplicar encima del main de hoy, como si hubieras empezado hoy. La
        historia queda en línea recta, sin commits de merge. Medido:
            * d181fc2 Agregar b
            * 25d0e0d Agregar a
            * 42a98b4 Cambio en main
            * c40d65a Base
        Pero "Agregar b" se llamaba bb6b9bf antes y d181fc2 después: rebase
        crea commits NUEVOS (otro padre = otro hash, CAPA 1). Los viejos
        quedan huérfanos.

        LA REGLA DE ORO: nunca hagas rebase de commits que otros ya tienen
        (una rama compartida, main). Ellos siguen sobre los viejos, tú sobre
        los nuevos, y al juntarse aparecen duplicados y conflictos. Rebase
        sí, sobre TU rama antes de abrir el pull request.

        Limpiar tu rama antes de compartirla: un commit de corrección con el
        prefijo fixup! y el nombre del commit que arregla, y luego
        --autosquash lo funde con él. Medido: 5 commits ("fixup! Agregar a"
        incluido) quedaron en 4, y "Agregar a" ya traía el arreglo:
            git commit -m "fixup! Agregar a"
            git rebase -i --autosquash main
        (rebase -i abre un editor para reordenar, juntar o renombrar
        commits; --autosquash ya deja la lista armada.)

        git pull --rebase hace lo mismo con lo que bajas: tus commits locales
        se acomodan encima de lo que subieron los demás (CAPA 8).
    */

    /*
        =================================================================
        CAPA 5) DESHACER: RESTORE, RESET, REVERT Y REFLOG
        =================================================================

        | Quiero...                                    | Comando                         |
        |----------------------------------------------|---------------------------------|
        | tirar lo que cambié en un archivo            | git restore archivo             |
        | sacarlo de staging (sin perder el cambio)    | git restore --staged archivo    |
        | arreglar el ÚLTIMO commit (mensaje o archivo | git commit --amend              |
        | olvidado), si no lo he subido                |                                 |
        | quitar commits locales (no subidos)          | git reset (abajo)               |
        | deshacer un commit YA SUBIDO                 | git revert <commit>             |
        | recuperar algo que "borré"                   | git reflog + git reset          |

        RESET mueve la rama hacia atrás; la opción dice qué pasa con los
        cambios de los commits quitados. Medido con 3 commits, reset HEAD~1:

            --soft    quedan 2 commits; el cambio queda PREPARADO   ("M ")
            --mixed   quedan 2 commits; el cambio queda sin preparar (" M")
                      (es el valor por defecto)
            --hard    quedan 2 commits; el cambio se BORRA de la carpeta

        --soft sirve para rehacer los últimos commits como uno solo; --hard
        es el único que destruye trabajo sin commit.

        REVERT no borra nada: crea un commit NUEVO que hace lo contrario.
        Medido: después de git revert HEAD, el log dijo
            Revert "Versión 3"
            Versión 3
        y el archivo volvió a v2. Es lo correcto para algo que ya está en el
        remoto: no reescribe la historia de nadie (CAPA 4).

        REFLOG: el diario de a dónde ha apuntado HEAD. Casi nada se pierde
        de verdad. Medido: reset --hard HEAD~2 "borró" dos commits;
            git reflog
                HEAD@{0}: reset: moving to HEAD~2
                HEAD@{1}: revert: Revert "Versión 3"
                ...
            git reset --hard HEAD@{1}
        y volvieron. El reflog es LOCAL (no se sube) y Git lo guarda por un
        tiempo: 90 días por defecto, pero solo 30 para commits que ya no
        están en ninguna rama, justo como los que "borró" un reset --hard.
        No esperes un mes. Lo que de verdad no se recupera: los cambios que
        nunca pasaron por commit, stash ni add.
    */

    /*
        =================================================================
        CAPA 6) GUARDAR Y COPIAR: STASH Y CHERRY-PICK
        =================================================================

        STASH: estás a medias y te piden revisar otra cosa urgente.
            git stash push -m "trabajo a medias"   guarda y deja limpio
            git stash list                         stash@{0}: On feature:
                                                   trabajo a medias
            git stash pop                          lo regresa (medido: el
                                                   archivo volvió a " M")
        Es una pila temporal: no la uses de cajón. Si va a durar más de un
        rato, mejor un commit en una rama.

        CHERRY-PICK: copiar UN commit a otra rama. El caso típico: un
        arreglo urgente hecho en hotfix que también necesita tu rama.
            git cherry-pick hotfix
        Medido: el commit "Corregir IVA" era cdd1610 en hotfix y la copia
        quedó como d1045aa: es otro commit (otro padre), con el mismo
        cambio. (Y al revés: con el mismo padre, el mismo contenido, el
        mismo autor y el mismo segundo, Git calcula el MISMO hash; un commit
        no es más que esos datos. GitTest falló una vez justo por eso.) Cuando las dos ramas se junten, Git suele darse cuenta de
        que es el mismo cambio; si se repite mucho, la rama equivocada es el
        problema.
    */

    /*
        =================================================================
        CAPA 7) ENCONTRAR EL COMMIT QUE ROMPIÓ ALGO: BISECT
        =================================================================

        Para leer la historia:
            git log --oneline --graph --all     el dibujo de las ramas
            git log -p archivo                  cada cambio de un archivo
            git blame archivo                   quién cambió cada línea, en
                                                qué commit (y su mensaje: el
                                                por qué de la CAPA 2)

        BISECT: "funcionaba hace 20 commits y ya no". Git hace búsqueda
        binaria (partir a la mitad cada vez, como binarySearch:
        v1_fundamentals/part4_data_and_input/Arrays, CAPA 6): marcas un commit bueno y uno malo, y Git te va dando el de en
        medio. Mejor aún, con un comando que diga si funciona (salida 0 =
        bien, otra = mal), lo hace solo:

            git bisect start HEAD HEAD~19        malo, bueno
            git bisect run ./mvnw -q test -Dtest=PricingTest
            git bisect reset                     volver a donde estabas

        Medido con 20 commits donde el IVA cambió de 0.16 a 0.15 en el 13:
            e87b75c... is the first bad commit
                Cambio 13
        en 5 pasos (log2 de 20 ≈ 4.3), no 20. Con una prueba que
        reproduzca el error, encontrar al culpable entre cientos de commits
        toma minutos.
    */

    /*
        =================================================================
        CAPA 8) CON EL EQUIPO: PUSH RECHAZADO Y FORCE-WITH-LEASE
        =================================================================

        fetch  baja lo nuevo del remoto SIN tocar tu rama (origin/main se
               actualiza; tu main no).
        pull   = fetch + merge (o + rebase, con --rebase).

        PUSH RECHAZADO. Medido: Luis subió un commit; Ana, con el suyo, hizo
        push:
            ! [rejected]        main -> main (fetch first)
            hint: Updates were rejected because the remote contains work
                  that you do not ...
        Git NO deja que pises lo de otro. La salida normal:
            git pull --rebase origin main     tus commits encima de los suyos
            git push
        Medido: la historia quedó en línea (Inicio, Cambio de Luis, Cambio
        de Ana) y el push pasó.

        FORZAR. Después de un rebase o un --amend sobre algo ya subido, el
        push normal se rechaza y hay que forzar. Las dos formas no son
        iguales (medido, después de que Luis subió "Luis 2"):
            git push --force             pasó... y "Luis 2" DESAPARECIÓ del
                                         remoto. Sin aviso.
            git push --force-with-lease  ! [rejected] main -> main (stale
                                         info): se negó, porque el remoto ya
                                         no estaba como Ana lo vio la última
                                         vez.
        Si alguna vez fuerzas, que sea con --force-with-lease, y nunca sobre
        main ni ramas compartidas (CAPA 4). La mayoría de los equipos
        protegen main en el servidor para que nadie pueda forzar ahí.

        El flujo usual en una empresa: una rama por tarea, pull request,
        revisión de código y pruebas automáticas (CI) antes de entrar a
        main; etiquetas (git tag v1.4.0) para marcar cada versión liberada.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) No entender que una rama es un puntero y un commit una foto con
           padre: de ahí salen casi todas las sorpresas (CAPA 1).

        2) Commits gigantes con mensajes "cambios"; subir target/, la
           configuración del IDE o un secreto; fines de línea sin
           .gitattributes en un equipo con Windows y Linux (CAPA 2).

        3) Resolver un conflicto sin preguntar qué es lo correcto, o sin
           compilar y probar después (CAPA 3).

        4) Rebase de algo que otros ya tienen (CAPA 4).

        5) reset --hard con cambios sin commit; reset en vez de revert sobre
           algo ya subido; no conocer reflog (CAPA 5).

        6) El stash como cajón de cosas olvidadas (CAPA 6).

        7) Buscar a mano entre cientos de commits en vez de bisect (CAPA 7).

        8) git push --force sobre una rama compartida (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Commit = foto + padre + autor + mensaje (el hash sale de todo eso);
          rama = puntero de 41 bytes; carpeta -> staging -> repositorio.
        - Commits pequeños, de una cosa, con el por qué; .gitignore; un
          secreto subido se cambia, no se borra.
        - Merge: fast-forward o commit de merge; conflictos se resuelven con
          criterio y se prueban; merge --abort.
        - Rebase reescribe (hashes nuevos): solo en tu rama. fixup! +
          --autosquash.
        - restore, reset --soft/--mixed/--hard, revert para lo subido, reflog
          para recuperar.
        - stash para un rato; cherry-pick copia un commit (otro hash).
        - bisect run encuentra al culpable en log2(n) pasos.
        - push rechazado -> pull --rebase; forzar solo con
          --force-with-lease, nunca en main.
    */
}
