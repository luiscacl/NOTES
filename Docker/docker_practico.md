# Docker en la práctica: guía paso a paso

Este archivo es el complemento práctico de `sistema_y_docker.md`. Aquel explica el mecanismo real detrás de cada pieza de Docker (namespaces, cgroups, OverlayFS, syscalls); este archivo se enfoca en usarlo: instalar, escribir comandos, construir imágenes, levantar proyectos completos, y practicar. Cuando algo requiera entender el "por qué" a fondo, se referencia la sección correspondiente de `sistema_y_docker.md` en vez de repetirla.

Convenciones de este archivo:

- Cada comando nuevo se explica la primera vez que aparece: qué hace cada parte de la línea, qué vas a ver en la terminal, y por qué.
- Los bloques de salida de terminal son representativos (los IDs, hashes y tiempos van a variar en tu máquina), pero la estructura y el significado de cada campo es real.
- Cada sección grande cierra con **Practica esto** (ejercicios guiados) y las más avanzadas con retos abiertos.
- No se usan analogías; cuando algo necesita más claridad, se agrega un ejemplo concreto o un diagrama.


## 1. Instalación

### 1.1 Windows (Docker Desktop + WSL2)

1. Verifica que la virtualización esté activa en la BIOS/UEFI (casi siempre lo está en máquinas modernas).
2. Abre PowerShell **como administrador** y ejecuta:
   ```
   wsl --install
   ```
   Este comando instala WSL2 y, si no tienes ninguna, una distribución Linux por defecto (Ubuntu). Si ya tenías WSL instalado, actualiza con `wsl --update`. Reinicia la máquina cuando lo pida.
3. Descarga Docker Desktop desde `docker.com/products/docker-desktop` e instala el ejecutable. Durante la instalación, deja marcada la opción "Use WSL 2 instead of Hyper-V" (es la opción por defecto en versiones actuales).
4. Abre Docker Desktop. La primera vez tarda un poco en arrancar la VM de WSL2 y el daemon dentro de ella; qué pasos exactos ocurre al abrir la app, por qué `docker` no funciona con la app cerrada, y por qué esto no es una limitación "poco profesional" sino la arquitectura esperada en un sistema sin kernel Linux nativo, está explicado a fondo en `sistema_y_docker.md`, sección D5.
5. Revisa **Settings → Resources**: ahí se fija cuánta CPU, RAM y disco recibe la VM completa (el techo real de cualquier `--memory`/`--cpus` que uses después), y en **Settings → General**, la opción "Start Docker Desktop when you sign in" evita tener que abrir la aplicación a mano cada vez.
6. Abre una terminal (PowerShell, o la terminal de WSL) y verifica con los comandos de la sección 1.3.

### 1.2 Linux (Ubuntu / Debian, instalación de Docker Engine directo, sin Docker Desktop)

Estos son los pasos oficiales actuales para agregar el repositorio de Docker y instalar desde ahí (en vez del paquete `docker.io` de los repositorios de Ubuntu, que suele ir más atrasado en versión):

```bash
# 1. Dependencias y llave GPG de Docker
sudo apt-get update
sudo apt-get install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# 2. Agregar el repositorio oficial de Docker a apt
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update

# 3. Instalar Docker Engine, la CLI, containerd, buildx y el plugin de Compose
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Qué instaló cada paquete:

- `docker-ce`: el daemon (`dockerd`), descrito en `sistema_y_docker.md` D4.
- `docker-ce-cli`: el cliente (`docker`), el comando que vas a escribir en la terminal.
- `containerd.io`: `containerd` y `runc`, también de D4.
- `docker-buildx-plugin`: el motor de build moderno (BuildKit), que es el que usa `docker build` por defecto desde hace varias versiones.
- `docker-compose-plugin`: agrega el subcomando `docker compose` (sección 8 de este archivo). Nota: es `docker compose`, con espacio; el binario viejo `docker-compose` con guion es la versión 1, ya obsoleta.

Paso extra, casi siempre necesario en Linux: por defecto solo `root` puede hablarle al socket del daemon (`/var/run/docker.sock`), así que cada comando `docker` pediría `sudo`. Para evitarlo, agrega tu usuario al grupo `docker`:

```bash
sudo usermod -aG docker $USER
```

Cierra sesión y vuelve a entrar (o reinicia) para que el cambio de grupo tome efecto.

### 1.3 Verificar que todo funciona

```bash
docker version
```
Muestra dos bloques: `Client` (la versión de la CLI) y `Server` (la versión del daemon). Si el bloque `Server` no aparece o da error de conexión, el daemon no está corriendo (en Windows/Mac, abre Docker Desktop; en Linux, `sudo systemctl start docker`).

```bash
docker info
```
Muestra el estado completo del daemon: cuántos contenedores hay corriendo, cuántas imágenes, qué driver de almacenamiento usa (normalmente `overlay2`, el OverlayFS de `sistema_y_docker.md` C8), y el runtime (`runc`).

```bash
docker run hello-world
```
Esta es la prueba definitiva. Si todo está bien, vas a ver un mensaje como:

```
Unable to find image 'hello-world:latest' locally
latest: Pulling from library/hello-world
...
Status: Downloaded newer image for hello-world:latest

Hello from Docker!
This message shows that your installation appears to be working correctly.
```

Ese mensaje describe exactamente la arquitectura de `sistema_y_docker.md` D4: el cliente le pidió al daemon correr esa imagen, el daemon no la tenía localmente, la pidió al registry (Docker Hub), y una vez descargada `runc` arrancó el contenedor.


## 2. Comandos básicos de contenedores

### 2.1 `docker run`: crear y arrancar un contenedor

La forma más simple:

```bash
docker run nginx
```

Esto descarga la imagen `nginx` (si no la tienes) y arranca un contenedor con ella. Vas a ver los logs de nginx directamente en tu terminal, y la terminal queda "atrapada" (en foreground) hasta que lo detengas con `Ctrl+C`.

Flags que vas a usar todo el tiempo, explicados uno por uno:

- **`-d`** (detached): corre el contenedor en segundo plano y te devuelve la terminal de inmediato.
  ```bash
  docker run -d nginx
  ```
- **`--name <nombre>`**: le pone un nombre fijo al contenedor, en vez del nombre aleatorio que Docker asigna por defecto (algo como `laughing_hopper`). Sin esto, tienes que referirte al contenedor por su ID largo o corto.
  ```bash
  docker run -d --name miweb nginx
  ```
- **`-p <puerto_host>:<puerto_contenedor>`**: publica un puerto (el mecanismo de NAT de `sistema_y_docker.md` D10). Sin este flag, el contenedor sigue teniendo su puerto abierto por dentro, pero nada de afuera del host puede llegar a él.
  ```bash
  docker run -d --name miweb -p 8080:80 nginx
  ```
  Ahora `http://localhost:8080` en tu navegador te muestra la página de nginx.
- **`--rm`**: borra el contenedor automáticamente en cuanto termina, en vez de dejarlo en la lista de contenedores detenidos. Muy útil para pruebas rápidas que no quieres tener que limpiar después.
  ```bash
  docker run --rm alpine echo "hola"
  ```
- **`-it`**: combina dos flags, `-i` (interactivo, mantiene abierta la entrada estándar) y `-t` (asigna una pseudo-terminal). Se usa cuando quieres una sesión interactiva dentro del contenedor, típicamente con una shell:
  ```bash
  docker run -it ubuntu bash
  ```
  Esto te deja literalmente dentro de una shell `bash` corriendo dentro del contenedor. Para salir sin detener el contenedor: `Ctrl+P` seguido de `Ctrl+Q` (desconecta la terminal, el contenedor sigue vivo). Para salir y detenerlo: simplemente escribe `exit`.
- **`-e <VARIABLE>=<valor>`** (o `--env`): define una variable de entorno dentro del contenedor.
  ```bash
  docker run -d --name db -e POSTGRES_PASSWORD=secreta postgres:16
  ```
- **`-P`** (mayúscula, distinta de `-p`): publica automáticamente TODOS los puertos que la imagen declaró con `EXPOSE` (`sistema_y_docker.md` D9), cada uno mapeado a un puerto aleatorio libre del host. Útil para pruebas rápidas cuando no te importa qué puerto exacto del host se usa; `docker ps` te muestra cuál le tocó.
  ```bash
  docker run -d -P nginx
  docker ps
  ```
- **`--init`**: inserta un proceso `init` minúsculo (tini) como PID 1 dentro del contenedor, en vez de que tu aplicación misma sea el PID 1. Por qué importa esto se explica con detalle en la sección 4 (Dockerfile), junto con `CMD`/`ENTRYPOINT`.

Combinando varios flags, así se ve un `docker run` real:

```bash
docker run -d --name miweb -p 8080:80 --rm nginx
```

### 2.2 Ver y gestionar contenedores

```bash
docker ps
```
Lista los contenedores **en ejecución**: columnas `CONTAINER ID`, `IMAGE`, `COMMAND`, `CREATED`, `STATUS`, `PORTS`, `NAMES`.

```bash
docker ps -a
```
Lista **todos** los contenedores, incluyendo los detenidos (`STATUS` dirá algo como `Exited (0) 5 minutes ago`).

```bash
docker logs miweb
```
Muestra el stdout/stderr acumulado del proceso principal del contenedor (`sistema_y_docker.md` D6).

```bash
docker logs -f miweb
```
Igual, pero en modo "follow": se queda mostrando líneas nuevas en tiempo real, como `tail -f`. Se sale con `Ctrl+C` (esto no detiene el contenedor, solo deja de seguir el log).

```bash
docker exec -it miweb bash
```
Abre una shell interactiva **dentro** de un contenedor que ya está corriendo (distinto de `docker run`, que crea uno nuevo). Si la imagen no tiene `bash` (las basadas en Alpine no lo traen por defecto), usa `sh`:
```bash
docker exec -it miweb sh
```

```bash
docker stop miweb
```
Detiene el contenedor mandando SIGTERM y esperando hasta 10 segundos antes de forzar con SIGKILL (`sistema_y_docker.md` D6).

```bash
docker start miweb
```
Vuelve a arrancar un contenedor detenido, reutilizando su misma capa de escritura (no es lo mismo que `docker run`).

```bash
docker restart miweb
```
Equivale a `stop` seguido de `start`.

```bash
docker rm miweb
```
Borra un contenedor detenido (falla si sigue corriendo, a menos que agregues `-f` para forzarlo: `docker rm -f miweb`).

**🔧 Practica esto:**
1. `docker run -d --name web1 -p 8081:80 nginx`
2. `docker ps` y confirma que aparece con el puerto mapeado
3. Abre `http://localhost:8081` en el navegador
4. `docker exec -it web1 bash`, y dentro corre `curl localhost:80` para ver la misma página desde adentro
5. `docker logs web1` para ver los logs de acceso que generó ese `curl`
6. `docker stop web1` y luego `docker start web1`, confirma que sigue sirviendo en el mismo puerto
7. `docker rm -f web1` para limpiar


## 3. Trabajando con imágenes

```bash
docker images
```
Lista las imágenes descargadas localmente: `REPOSITORY`, `TAG`, `IMAGE ID`, `CREATED`, `SIZE`.

```bash
docker pull postgres:16
```
Descarga una imagen sin correrla todavía. Si omites el tag (`docker pull postgres`), Docker asume `:latest`.

```bash
docker rmi postgres:16
```
Borra una imagen local. Falla si algún contenedor (aunque esté detenido) todavía la usa; en ese caso primero hay que borrar el contenedor.

```bash
docker tag mi-imagen:v1 mi-imagen:v2
```
Agrega una etiqueta adicional a una imagen que ya existe localmente. No duplica nada en disco, solo agrega una referencia (`sistema_y_docker.md` D13).

```bash
docker history nginx
```
Muestra las capas de la imagen en orden, con el tamaño que agregó cada una. Es la forma directa de ver el concepto de D8 (capas de imagen) en tu propia terminal.

```bash
docker inspect miweb
```
Vuelca toda la metadata del contenedor (o imagen) en JSON: IP asignada, variables de entorno, volúmenes montados, política de reinicio, IDs de namespaces, etc. Para extraer un solo campo sin leer todo el JSON:
```bash
docker inspect -f '{{.NetworkSettings.IPAddress}}' miweb
```

```bash
docker diff miweb
```
Muestra, archivo por archivo, qué cambió dentro de un contenedor respecto a la imagen con la que arrancó: `A` (agregado), `C` (modificado) o `D` (borrado). Es la forma más directa de ver, de verdad, la capa de escritura que describe `sistema_y_docker.md` D7: todo lo que `docker diff` lista vive únicamente en esa capa, nunca en la imagen de solo lectura de abajo.

**🔧 Practica esto:**
1. `docker pull alpine:3.19` y `docker pull alpine:latest`
2. `docker images` y compara los `IMAGE ID`: si son iguales, `latest` apuntaba justo a esa versión en el momento en que se publicó
3. `docker history nginx` y observa cuántas capas tiene y qué instrucción generó cada una
4. `docker run -d --name test-diff alpine sleep 300`, luego `docker exec test-diff sh -c "echo hola > /tmp/archivo.txt"`, y después `docker diff test-diff` para ver ese archivo listado como `A /tmp/archivo.txt`


## 4. Tu primer Dockerfile

Crea una carpeta para el proyecto, y dentro un archivo `index.html`:

```html
<!DOCTYPE html>
<html>
<body><h1>Hola desde mi contenedor</h1></body>
</html>
```

Y un `Dockerfile` (sin extensión, así se llama el archivo):

```dockerfile
FROM nginx:alpine
COPY index.html /usr/share/nginx/html/index.html
```

Explicación línea por línea:
- `FROM nginx:alpine`: usa como base la imagen oficial de nginx en su variante Alpine (mucho más pequeña que la variante basada en Debian).
- `COPY index.html /usr/share/nginx/html/index.html`: copia tu archivo local a la ruta donde nginx sirve sus archivos por defecto.

Construir la imagen:

```bash
docker build -t mi-sitio .
```

- `-t mi-sitio`: le pone el nombre (tag) `mi-sitio` a la imagen resultante.
- `.`: el contexto de build es el directorio actual (Docker empaqueta todo lo que hay aquí, salvo lo excluido por `.dockerignore`, y se lo manda al daemon).

Correrla:

```bash
docker run -d --name mi-sitio-c -p 8080:80 mi-sitio
```

Abre `http://localhost:8080` y deberías ver tu HTML.

### Segundo ejemplo: una API en Node.js

Estructura del proyecto:
```
mi-api/
├── Dockerfile
├── package.json
├── server.js
└── .dockerignore
```

`package.json`:
```json
{
  "name": "mi-api",
  "version": "1.0.0",
  "main": "server.js",
  "dependencies": {
    "express": "^4.19.0"
  }
}
```

`server.js`:
```javascript
const express = require("express");
const app = express();
app.get("/", (req, res) => res.send("Hola desde Express en Docker"));
app.listen(3000, () => console.log("Escuchando en el puerto 3000"));
```

`.dockerignore`:
```
node_modules
npm-debug.log
```
Esto evita mandar tu `node_modules` local (si existe) al contexto de build; se instalará limpio dentro de la imagen.

`Dockerfile`:
```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package.json .
RUN npm install
COPY . .
EXPOSE 3000
CMD ["node", "server.js"]
```

Por qué el orden importa (cache de capas, `sistema_y_docker.md` D8): `package.json` se copia y se instalan las dependencias ANTES de copiar el resto del código. Mientras no cambies `package.json`, `docker build` reutiliza la capa de `npm install` aunque cambies `server.js` mil veces.

Construir y correr:
```bash
docker build -t mi-api .
docker run -d --name mi-api-c -p 3000:3000 mi-api
curl http://localhost:3000
```

**🔧 Practica esto:**
1. Cambia el texto de `server.js`, reconstruye (`docker build -t mi-api .`) y observa en la salida que la línea de `npm install` dice `CACHED`
2. Borra el contenedor viejo (`docker rm -f mi-api-c`) y crea uno nuevo con la imagen reconstruida
3. Confirma con `curl` que el texto cambió

### Forma exec vs. forma shell en `CMD` (y por qué `docker stop` a veces tarda 10 segundos completos)

`CMD ["node", "server.js"]`, con corchetes y comillas, es la **forma exec**: Docker ejecuta ese binario directamente como PID 1 del contenedor (el proceso descrito en `sistema_y_docker.md` A6/A7, dentro del PID namespace de C5). Existe una segunda forma, la **forma shell**, sin corchetes:

```dockerfile
CMD node server.js
```

Esta segunda forma no ejecuta `node` directamente como PID 1: Docker en realidad arranca `/bin/sh -c "node server.js"`, y es ese `/bin/sh` el que queda como PID 1, con `node` corriendo como un proceso hijo suyo.

La diferencia importa por las señales (`sistema_y_docker.md` D6): cuando ejecutas `docker stop`, Docker le manda SIGTERM al proceso PID 1 del contenedor. Si PID 1 es tu aplicación (forma exec), tu código recibe la señal directamente y puede reaccionar (cerrar conexiones, terminar limpio). Si PID 1 es `/bin/sh` (forma shell), muchas shells simplemente **no reenvían** SIGTERM a sus procesos hijos automáticamente, así que `node` nunca se entera; Docker espera los 10 segundos completos del timeout y termina matándolo todo con SIGKILL, en vez de dejarlo cerrar limpio. Por esta razón, la forma exec (con corchetes) es la recomendada casi siempre.

Hay un problema adicional incluso usando la forma exec: en Linux, el proceso PID 1 tiene responsabilidades especiales del kernel que la mayoría de los programas normales no implementan, entre ellas "reap" (recolectar) a los procesos hijos que terminan, para que no queden como procesos zombis acumulándose. Si tu aplicación genera procesos hijos (por ejemplo, llama a otros programas) y no fue escrita pensando en ser PID 1, esos hijos pueden quedar sin recolectar. La flag `--init` de `docker run` (ya mencionada en la sección 2) resuelve esto insertando un proceso `init` minúsculo (`tini`) como verdadero PID 1, que sí reenvía señales correctamente y sí recolecta procesos zombis, dejando tu aplicación corriendo como su hijo directo:

```bash
docker run -d --init --name mi-api-c -p 3000:3000 mi-api
```

En Compose, el equivalente es agregar `init: true` al servicio:
```yaml
services:
  api:
    build: .
    init: true
```

**🔧 Practica esto:** cambia el `CMD` de tu Dockerfile de `mi-api` a la forma shell (`CMD node server.js`), reconstruye, arranca el contenedor y mide cuánto tarda `docker stop` (`time docker stop mi-api-c` en Linux/Mac, o simplemente cronometra a ojo en Windows). Luego vuelve a la forma exec y compara.


## 5. Multi-stage builds en la práctica

Si construyeras la imagen anterior para producción tal cual, incluiría todo Node.js y las herramientas de desarrollo dentro de la imagen final. Para una app que se compila (React, Vue, TypeScript, etc.), esto es innecesario: solo hace falta el resultado ya compilado.

Ejemplo con un frontend de React:

```dockerfile
# Etapa 1: build (usa Node solo para compilar)
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json .
RUN npm install
COPY . .
RUN npm run build

# Etapa 2: producción (solo sirve archivos estáticos)
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
```

`COPY --from=build` copia archivos de la primera etapa (identificada con `AS build`) a la segunda. La imagen final nunca tuvo Node.js instalado, solo los archivos ya compilados.

**🔧 Practica esto:** construye una imagen de un proyecto simple con y sin multi-stage, y compara el tamaño con `docker images`. La diferencia suele ser de cientos de MB.

### Qué agrega BuildKit sobre el motor de build clásico

Desde hace varias versiones, `docker build` usa **BuildKit** por defecto (instalado con `docker-buildx-plugin`, sección 1.2), en vez del motor de build antiguo. La diferencia no es solo velocidad:

- **Etapas independientes en paralelo.** En un multi-stage build donde una etapa no depende de otra, BuildKit las construye al mismo tiempo, en vez de una tras otra.
- **Cache mounts**, para directorios de caché de un gestor de paquetes que NO quieres que terminen dentro de la imagen final pero SÍ quieres conservar entre builds (más rápido que reinstalar todo cada vez, sin inflar el tamaño de la imagen):
  ```dockerfile
  RUN --mount=type=cache,target=/root/.npm npm install
  ```
- **Secretos de build**, para pasarle a `RUN` un dato sensible (un token, una contraseña) sin que quede grabado en ninguna capa de la imagen final:
  ```dockerfile
  RUN --mount=type=secret,id=npm_token npm install
  ```
  ```bash
  docker build --secret id=npm_token,src=./token.txt -t mi-imagen .
  ```
- **`--progress=plain`**: muestra el log de build completo, línea por línea, en vez del resumen compacto por defecto. Útil para depurar un `RUN` que está fallando y necesitas ver exactamente qué imprimió.
  ```bash
  docker build --progress=plain -t mi-api .
  ```

**🔧 Practica esto:** agrega un cache mount al `RUN npm install` del Dockerfile de la sección 4, borra la imagen y reconstrúyela dos veces seguidas borrando el contenedor entre medio; la segunda vez, `npm install` debería completarse notablemente más rápido aunque no haya cambiado el cache de capas de Docker.


## 6. Redes en la práctica

```bash
docker network ls
```
Lista las redes existentes. Por defecto vas a ver `bridge`, `host` y `none` (los drivers de `sistema_y_docker.md` D10).

```bash
docker network create mired
```
Crea una red **definida por el usuario**, con el driver `bridge` por defecto. Esto es distinto de la red `bridge` que trae Docker por defecto, y la diferencia es importante:

- En la red `bridge` por defecto, los contenedores NO se resuelven entre sí por nombre; solo por IP.
- En una red creada con `docker network create`, Docker activa un **servidor DNS interno** (en la IP `127.0.0.11` dentro de cada contenedor) que sí resuelve los nombres de los demás contenedores conectados a esa misma red.

```
┌──────────────────────────────────────────────────────────────┐
│ RED PERSONALIZADA: mired (creada con docker network create)  │
└──────────────────────────────────────────────────────────────┘

Servidor DNS interno de Docker: 127.0.0.11, activo solo en
redes creadas con 'docker network create' (no en la red
'bridge' por defecto).

┌────────────────────────────┐      ┌────────────────────────────┐
│ CONTENEDOR: web            │      │ CONTENEDOR: db             │
│ hostname = web             │      │ hostname = db              │
│ IP = 172.20.0.2            │      │ IP = 172.20.0.3            │
└────────────────────────────┘      └────────────────────────────┘

Dentro de 'web', conectar a postgres://db:5432 funciona:
el contenedor 'web' le pregunta al DNS interno (127.0.0.11)
'¿qué IP tiene db?' y recibe 172.20.0.3, sin que tú hayas
configurado nada a mano.
```

```bash
docker run -d --name web --network mired nginx
docker run -it --rm --network mired alpine sh
# dentro del contenedor alpine:
ping web
```

`ping web` funciona porque ambos contenedores están en `mired`, y el DNS interno resuelve `web` a su IP real.

```bash
docker network inspect mired
```
Muestra los contenedores conectados, sus IPs, y el rango de subred asignado.

**🔧 Practica esto:**
1. Repite el ejemplo de arriba, pero primero probando SIN una red personalizada (`docker run -d --name web nginx` sin `--network`), y confirma que `ping web` desde otro contenedor por defecto **no** resuelve el nombre
2. Conecta ambos a `mired` y repite la prueba


## 7. Volúmenes y persistencia en la práctica

```bash
docker volume create midata
docker volume ls
docker volume inspect midata
```

Usarlo con un contenedor:
```bash
docker run -d --name basedatos -v midata:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreta postgres:16
```

Prueba de persistencia real:
```bash
docker exec -it basedatos psql -U postgres -c "CREATE TABLE prueba (id serial);"
docker rm -f basedatos
docker run -d --name basedatos2 -v midata:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreta postgres:16
docker exec -it basedatos2 psql -U postgres -c "\dt"
```
La tabla `prueba` sigue ahí, aunque el primer contenedor ya no existe: los datos vivían en el volumen, no en la capa de escritura del contenedor (`sistema_y_docker.md` D7 y D11).

Bind mount, útil para desarrollo (edita en tu editor, el contenedor ve el cambio al instante):
```bash
docker run -d --name mi-api-dev -v "$(pwd)":/app -p 3000:3000 mi-api
```
Aquí `$(pwd)` (o `%cd%` en CMD de Windows, `${PWD}` en PowerShell) es la ruta de tu carpeta actual en el host, montada dentro del contenedor en `/app`.

Limpieza:
```bash
docker volume rm midata
docker volume prune
```
`prune` borra todos los volúmenes que no están en uso por ningún contenedor (pide confirmación).

**🔧 Practica esto:** repite el ejemplo de la tabla de prueba, pero esta vez usando `docker volume rm` a propósito ANTES de recrear el contenedor, y confirma que en ese caso la tabla sí desaparece (porque el volumen ya no existe).


## 8. Docker Compose paso a paso

Compose describe varios contenedores relacionados en un solo archivo y los levanta con un comando. La versión actual (el plugin `docker compose`, con espacio) usa la **Compose Specification**: ya no hace falta (ni se recomienda) poner una línea `version:` al principio del archivo.

Estructura de un proyecto:
```
mi-proyecto/
├── docker-compose.yml
├── Dockerfile
├── .env
└── (el resto de tu código)
```

Ejemplo completo, `docker-compose.yml`:
```yaml
services:
  api:
    build: .
    ports:
      - "3000:3000"
    environment:
      - DATABASE_URL=postgres://postgres:${DB_PASSWORD}@db:5432/tareas
    depends_on:
      - db

  db:
    image: postgres:16
    environment:
      - POSTGRES_PASSWORD=${DB_PASSWORD}
      - POSTGRES_DB=tareas
    volumes:
      - db_data:/var/lib/postgresql/data

volumes:
  db_data:
```

`.env` (mismo directorio, Compose lo carga automáticamente):
```
DB_PASSWORD=secreta
```

Explicación campo por campo:
- `services`: cada clave debajo (`api`, `db`) es un contenedor que Compose va a crear.
- `build: .`: en vez de usar una imagen ya existente, construye una a partir del `Dockerfile` en este directorio.
- `image: postgres:16`: usa directamente una imagen del registry, sin construir nada.
- `ports`: igual que `-p` en `docker run`.
- `environment`: igual que `-e`. `${DB_PASSWORD}` se sustituye por el valor del archivo `.env`.
- `depends_on`: le dice a Compose que arranque `db` antes que `api`. **Importante**: por defecto esto solo espera a que el contenedor de `db` haya *arrancado*, no a que Postgres ya esté listo para aceptar conexiones (eso puede tardar unos segundos más). Para esperar de verdad a que esté listo, se usa una condición de healthcheck:
  ```yaml
  db:
    image: postgres:16
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 3s
      timeout: 3s
      retries: 5
  api:
    depends_on:
      db:
        condition: service_healthy
  ```
- `volumes` (a nivel de servicio): monta el volumen `db_data` dentro del contenedor.
- `volumes` (al final, a nivel raíz): declara ese volumen para que Compose lo cree si no existe.

Comandos:

```bash
docker compose up
```
Construye (si hace falta) y arranca todos los servicios, mostrando los logs de todos entrelazados en la terminal.

```bash
docker compose up -d
```
Igual, pero en segundo plano.

```bash
docker compose ps
```
Lista los contenedores de este proyecto (equivalente a `docker ps` pero filtrado a este `docker-compose.yml`).

```bash
docker compose logs -f api
```
Sigue los logs de un solo servicio.

```bash
docker compose exec api sh
```
Abre una shell dentro del contenedor `api` ya corriendo.

```bash
docker compose down
```
Detiene y elimina los contenedores y la red que Compose creó para el proyecto. **No** borra los volúmenes con nombre por defecto.

```bash
docker compose down -v
```
Igual, pero además borra los volúmenes declarados. Úsalo quirúrgicamente: esto sí destruye los datos.

```bash
docker compose build
```
Reconstruye las imágenes de los servicios que tienen `build:`, sin arrancar nada.

```bash
docker compose config
```
Imprime el archivo ya "resuelto" (con las variables de entorno sustituidas), útil para depurar por qué algo no está tomando el valor que esperabas.

**🔧 Practica esto:**
1. Copia el ejemplo de arriba en una carpeta con el `Dockerfile` de la sección 4 (API en Node)
2. `docker compose up -d`
3. `docker compose ps` para confirmar que ambos servicios están arriba
4. `curl http://localhost:3000`
5. `docker compose logs -f db` para ver los logs de Postgres arrancando
6. `docker compose down` y luego `docker compose up -d` de nuevo: confirma que los datos de Postgres siguen ahí (el volumen sobrevivió)
7. Esta vez sí, `docker compose down -v`, y confirma que un `docker volume ls` ya no muestra `db_data`


## 9. Depuración y solución de problemas

| Síntoma | Causa típica | Cómo confirmarlo / resolverlo |
|---|---|---|
| `Error response from daemon: Ports are not available` o `address already in use` | Otro proceso (o contenedor) ya está usando ese puerto del host | En Linux/Mac: `sudo lsof -i :8080`. En Windows: `netstat -ano \| findstr :8080`. Cambia el puerto del host en `-p` o detén el otro proceso |
| `Cannot connect to the Docker daemon` | El daemon no está corriendo | Windows/Mac: abre Docker Desktop. Linux: `sudo systemctl start docker` |
| El contenedor aparece con `STATUS: Exited (137)` | Recibió SIGKILL, casi siempre porque el kernel lo mató por quedarse sin memoria (OOM) | `docker inspect <id>` y busca el campo `OOMKilled: true`. Sube el límite con `--memory` o reduce el consumo de la app |
| El contenedor aparece con `STATUS: Exited (143)` | Recibió SIGTERM y terminó correctamente en respuesta (por ejemplo, `docker stop`) | Normal, no es un error en sí mismo |
| El contenedor aparece con `STATUS: Exited (1)` | La aplicación dentro terminó con un error propio | `docker logs <id>` para ver el error real que imprimió el proceso |
| `no space left on device` | El disco donde Docker guarda imágenes/contenedores/volúmenes se llenó | `docker system df` para ver el desglose, `docker system prune` para limpiar lo no usado |
| El disco se llena sin que crezcan imágenes ni volúmenes | Un contenedor lleva mucho tiempo corriendo y generando logs sin límite (`docker logs` los va acumulando en disco indefinidamente por defecto) | `du -sh $(docker inspect --format='{{.LogPath}}' <contenedor>)` para confirmar el tamaño real del archivo de log; ver el recuadro de abajo para evitarlo hacia adelante |

Recordatorio de los códigos de salida: en Linux, cuando un proceso muere por una señal, el código de salida reportado es `128 + número de señal`. SIGTERM es la señal 15 (128+15=143), SIGKILL es la señal 9 (128+9=137).

Comandos útiles de diagnóstico:

```bash
docker system df
```
Muestra cuánto espacio en disco ocupan imágenes, contenedores, volúmenes y el build cache, con cuánto de eso está "en uso" vs. recuperable.

```bash
docker system prune
```
Borra contenedores detenidos, redes no usadas, imágenes sin tag (`dangling`) y build cache. Agrega `-a` para incluir también imágenes sin usar aunque tengan tag, y `--volumes` para incluir volúmenes no usados (esto último borra datos, úsalo con cuidado).

```bash
docker stats
```
Muestra, en vivo, el uso de CPU/memoria/red/disco de cada contenedor corriendo. Es la forma directa de ver en acción los límites de cgroups de `sistema_y_docker.md` C6.

**🔧 Practica esto:** arranca un contenedor con un límite de memoria muy bajo y algo que consuma memoria dentro, para ver el OOM kill en vivo:
```bash
docker run -d --name test-oom --memory=10m python:3.12-alpine python -c "a = []
while True: a.append('x' * 10**6)"
docker ps -a
docker inspect test-oom -f '{{.State.OOMKilled}}'
```

### Por qué los logs de un contenedor pueden llenarte el disco

Por defecto, Docker usa el driver de logs `json-file`, que escribe todo lo que el proceso principal imprime a stdout/stderr en un archivo en el host, **sin ningún límite de tamaño**. Un contenedor de larga duración que imprime logs constantemente (un servidor web con mucho tráfico, por ejemplo) puede terminar con un archivo de log de varios gigabytes, sin que `docker system prune` lo toque, porque técnicamente no es una imagen, un contenedor detenido ni un volumen: es el log de un contenedor que sigue corriendo. Para evitarlo, se le pone un límite al driver de logs, ya sea por contenedor:

```bash
docker run -d --log-opt max-size=10m --log-opt max-file=3 nginx
```

o para todos los contenedores del daemon, en `/etc/docker/daemon.json` (Linux) o en Docker Desktop → Settings → Docker Engine (Windows/Mac):

```json
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
```

`max-size` limita el tamaño de cada archivo de log; `max-file` limita cuántos archivos rotados se conservan antes de borrar los más viejos.


## 10. Publicar imágenes en un registry

1. Crea una cuenta en `hub.docker.com` si no tienes una.
2. Inicia sesión desde la terminal:
   ```bash
   docker login
   ```
3. Etiqueta tu imagen con el formato que exige Docker Hub (`tu-usuario/nombre:tag`):
   ```bash
   docker tag mi-api tuusuario/mi-api:v1
   ```
4. Súbela:
   ```bash
   docker push tuusuario/mi-api:v1
   ```
5. Desde cualquier otra máquina con Docker instalado:
   ```bash
   docker pull tuusuario/mi-api:v1
   docker run -d -p 3000:3000 tuusuario/mi-api:v1
   ```

### Opcional: un registry privado local, para practicar sin depender de Docker Hub

Docker Hub ofrece la imagen oficial `registry:2`, que es un registry completo corriendo en un contenedor:

```bash
docker run -d -p 5000:5000 --name registry-local registry:2
docker tag mi-api localhost:5000/mi-api:v1
docker push localhost:5000/mi-api:v1
docker pull localhost:5000/mi-api:v1
```

**🔧 Practica esto:** sube una imagen tuya a Docker Hub, bórrala localmente (`docker rmi`), y vuelve a bajarla con `docker pull` para comprobar que el ciclo completo funciona.


## 11. Buenas prácticas

- **Usa imágenes base pequeñas.** Las variantes `alpine` o `slim` pesan una fracción de las imágenes completas basadas en Debian/Ubuntu. Compara `docker images` entre `node:20` y `node:20-alpine`.
- **No corras como root dentro del contenedor si no es necesario.** Muchas imágenes oficiales ya definen un usuario sin privilegios; si escribes tu propio Dockerfile, agrega:
  ```dockerfile
  RUN adduser -D appuser
  USER appuser
  ```
- **No uses `:latest` en nada que vaya a producción.** Fija una versión concreta (`node:20.11-alpine`, no `node:latest`), para que un rebuild dentro de un mes no te cambie la versión debajo sin avisar.
- **Ordena el Dockerfile de lo que cambia menos a lo que cambia más** (sección 4), para aprovechar el cache de capas.
- **Un proceso principal por contenedor.** Si necesitas varios procesos relacionados (una API y un worker, por ejemplo), normalmente van en contenedores separados, coordinados con Compose, no metidos a la fuerza en el mismo contenedor.
- **Agrega un healthcheck** a los servicios que otros dependen de que estén realmente listos, no solo arrancados (sección 8).
- **Usa `.dockerignore`** siempre que el directorio del proyecto tenga carpetas pesadas que no deben ir a la imagen (`node_modules`, `.git`, entornos virtuales de Python).
- **Usa `--init`** (sección 4) en cualquier contenedor de larga duración cuya imagen no incluya ya su propio init, para que las señales se propaguen bien y no se acumulen procesos zombis.
- **Nunca montes `/var/run/docker.sock` dentro de un contenedor sin saber exactamente por qué lo haces.** Algunos tutoriales lo hacen (`-v /var/run/docker.sock:/var/run/docker.sock`) para que un contenedor pueda lanzar otros contenedores. Eso le da a ese contenedor la capacidad de hablarle al daemon exactamente igual que tú desde la terminal, incluyendo crear contenedores con `--privileged` o montar cualquier ruta del host: en la práctica equivale a darle acceso de root sobre la máquina completa, no solo sobre ese contenedor.

**🔧 Practica esto:** toma el Dockerfile de la sección 4 y agrégale un usuario sin privilegios y una versión fija de la imagen base, en vez de `node:20-alpine` (que sigue actualizándose dentro de la serie 20).


## 12. Proyecto guiado: API con base de datos

Vas a construir, de principio a fin, una API que guarda tareas en Postgres.

```
┌──────────────────────────────────────────────────┐
│ CLIENTE (tu navegador o curl)                    │
└──────────────────────────────────────────────────┘
             │  http://localhost:3000
             ▼
┌──────────────────────────────────────────────────┐
│ Servicio 'api' (contenedor, imagen construida    │
│   con tu Dockerfile, puerto 3000 publicado)      │
└──────────────────────────────────────────────────┘
             │  postgres://db:5432/tareas
             ▼
┌──────────────────────────────────────────────────┐
│ Servicio 'db' (contenedor, imagen postgres:16,   │
│   sin puerto publicado al host)                  │
└──────────────────────────────────────────────────┘
             │
             ▼
┌──────────────────────────────────────────────────┐
│ Volumen con nombre 'db_data'                     │
│ (persiste aunque borres y recrees el contenedor) │
└──────────────────────────────────────────────────┘
```

Estructura de archivos:
```
proyecto-tareas/
├── docker-compose.yml
├── Dockerfile
├── .env
├── package.json
└── server.js
```

`package.json`:
```json
{
  "name": "proyecto-tareas",
  "version": "1.0.0",
  "main": "server.js",
  "dependencies": {
    "express": "^4.19.0",
    "pg": "^8.11.0"
  }
}
```

`server.js`:
```javascript
const express = require("express");
const { Pool } = require("pg");

const app = express();
app.use(express.json());
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

app.get("/tareas", async (req, res) => {
  const { rows } = await pool.query("SELECT * FROM tareas ORDER BY id");
  res.json(rows);
});

app.post("/tareas", async (req, res) => {
  const { titulo } = req.body;
  const { rows } = await pool.query(
    "INSERT INTO tareas (titulo) VALUES ($1) RETURNING *",
    [titulo]
  );
  res.status(201).json(rows[0]);
});

async function iniciar() {
  await pool.query(
    "CREATE TABLE IF NOT EXISTS tareas (id SERIAL PRIMARY KEY, titulo TEXT NOT NULL)"
  );
  app.listen(3000, () => console.log("API escuchando en el puerto 3000"));
}

iniciar();
```

`Dockerfile`:
```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package.json .
RUN npm install
COPY . .
EXPOSE 3000
CMD ["node", "server.js"]
```

`.env`:
```
DB_PASSWORD=secreta
```

`docker-compose.yml`:
```yaml
services:
  api:
    build: .
    ports:
      - "3000:3000"
    environment:
      - DATABASE_URL=postgres://postgres:${DB_PASSWORD}@db:5432/tareas
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:16
    environment:
      - POSTGRES_PASSWORD=${DB_PASSWORD}
      - POSTGRES_DB=tareas
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 3s
      timeout: 3s
      retries: 5
    volumes:
      - db_data:/var/lib/postgresql/data

volumes:
  db_data:
```

Levantarlo y probarlo:

```bash
docker compose up -d
docker compose logs -f api
```

En otra terminal:
```bash
curl -X POST http://localhost:3000/tareas -H "Content-Type: application/json" -d "{\"titulo\":\"Aprender Docker\"}"
curl http://localhost:3000/tareas
```

Deberías ver la tarea recién creada en la respuesta del segundo `curl`.

Prueba de persistencia:
```bash
docker compose down
docker compose up -d
curl http://localhost:3000/tareas
```
La tarea sigue ahí: vive en el volumen `db_data`, no en el contenedor.

**🔧 Practica esto:** agrega un endpoint `DELETE /tareas/:id` al `server.js`, reconstruye con `docker compose up -d --build`, y pruébalo con `curl -X DELETE http://localhost:3000/tareas/1`.


## 13. Retos para seguir practicando por tu cuenta

En orden de dificultad creciente, sin solución dada, para que los resuelvas usando todo lo anterior:

1. Containeriza un sitio estático propio (HTML/CSS/JS que ya tengas) y publícalo en Docker Hub.
2. Al proyecto de la sección 12, agrégale un tercer servicio: un contenedor de `redis:alpine` que la API use como caché para el endpoint `GET /tareas` (guardar el resultado unos segundos antes de volver a consultar Postgres).
3. Pon un límite de memoria y de CPU al servicio `api` en el `docker-compose.yml` (`deploy.resources.limits` o, en Compose sin modo Swarm, los flags equivalentes de `docker run` aplicados vía `mem_limit`/`cpus` a nivel de servicio) y comprueba con `docker stats` que se respeta.
4. Convierte el Dockerfile de la API a multi-stage, aunque Node no necesite "compilarse": una etapa instala TODAS las dependencias (incluyendo las de desarrollo) y corre las pruebas; la segunda etapa, más limpia, solo copia el código y las dependencias de producción.
5. Levanta el registry privado de la sección 10 y usa ese, en vez de Docker Hub, como destino del `docker push` en tu `docker-compose.yml` (cambiando el nombre de la imagen a `localhost:5000/...`).
6. Agrega un cuarto servicio, `nginx`, que actúe como proxy inverso delante de la API (el propio contenedor `api` deja de publicar el puerto 3000 al host; solo `nginx`, en el puerto 80, lo hace, y su configuración reenvía las peticiones a `api:3000` usando la resolución de nombres por DNS interno de la sección 6).


## 14. Cómo dockerizar un proyecto existente y complejo

Todo lo de las secciones 1 a 13 parte de proyectos pensados para Docker desde el principio. Lo más común en la práctica es lo contrario: un proyecto real que ya existe, que nunca se diseñó pensando en contenedores, con varios servicios corriendo a mano, configuración repartida en archivos sueltos, y ninguna garantía de que funcione igual en otra máquina. Esta sección es la metodología para convertir eso en algo portable.

Como ejemplo guía de esta sección: un proyecto típico con un backend en Django, un worker de tareas en segundo plano con Celery, Redis (como broker de Celery y como caché), Postgres, y un frontend en React, todo corriendo hoy con varios scripts sueltos en la máquina de quien lo desarrolla.

### 14.1 Auditar el proyecto antes de escribir un solo Dockerfile

Antes de tocar nada, se responde esto por cada pieza que compone el proyecto:

- ¿Qué runtime y qué versión exacta usa? Se revisa `.nvmrc`, `.python-version`, el campo `engines` de `package.json`, `runtime.txt`, `go.mod` o el equivalente del lenguaje; no se asume ni se elige la versión "más nueva" porque sí.
- ¿Qué dependencias de sistema operativo necesita para compilar o correr? (librerías de compilación para paquetes nativos, por ejemplo `libpq-dev` para el driver de Postgres en Python, o herramientas de build para paquetes de Node con partes en C).
- ¿Qué variables de entorno lee hoy, y de dónde las saca? (un archivo `.env`, variables exportadas a mano en la terminal de quien lo corre, valores hardcodeados directamente en el código).
- ¿En qué puerto escucha cada pieza?
- ¿A qué otras piezas se conecta, y con qué dirección? Aquí es donde casi siempre aparecen `localhost` hardcodeados (ver 14.6).
- ¿Qué datos necesita persistir entre reinicios? (una base de datos, archivos subidos por usuarios, logs que hay que conservar).
- ¿Qué pasos de build tiene, aparte de instalar dependencias? (compilar assets del frontend, generar archivos estáticos, correr migraciones).

Aplicado al proyecto de ejemplo:

| Pieza | Runtime | Puerto | Se conecta a | Estado a persistir |
|---|---|---|---|---|
| Backend Django | Python 3.12 | 8000 | Postgres, Redis | ninguno propio |
| Worker Celery | Python 3.12, mismo código que el backend | ninguno | Redis, Postgres | ninguno propio |
| Redis | n/a | 6379 | n/a | normalmente ninguno |
| Postgres | n/a | 5432 | n/a | los datos de la app |
| Frontend React | Node 20, solo para compilar | ninguno en producción | el backend, desde el navegador del usuario | ninguno |

### 14.2 Elegir la imagen base según lo que el proyecto ya necesita

Con la tabla de 14.1, la imagen base de cada Dockerfile no se elige a ojo: se elige para que coincida con lo que el proyecto YA usa. Si hoy corre con Python 3.12.4, la imagen base es `python:3.12-slim`, no `python:3.13-alpine` solo por ser la más nueva o la más pequeña. Un cambio de versión de runtime es un cambio real de comportamiento potencial, y dockerizar no es el momento de mezclarlo con una migración de versión.

### 14.3 Externalizar toda la configuración

La idea central es que la misma imagen, construida una sola vez, debe poder correr en desarrollo, en staging y en producción sin reconstruirse, cambiando únicamente las variables de entorno con las que se arranca el contenedor. En la práctica:

- Todo valor hoy hardcodeado en el código (la URL de la base de datos, la clave secreta de la app, el host de Redis) pasa a leerse de una variable de entorno.
- El `.env` que el proyecto ya tiene deja de ser un archivo que el código lee directamente, y se convierte en el archivo que Compose carga para inyectar esas variables (sección 8).
- **Nunca se usa `ARG` para pasar un secreto a un Dockerfile.** Aunque `ARG` solo existe en tiempo de build, su valor puede terminar visible en `docker history` de la imagen resultante si algún `RUN` lo usa de forma que quede grabado en una capa. Para secretos que hacen falta durante el build (la clave de acceso a un paquete privado, por ejemplo) se usa `--mount=type=secret` de BuildKit (sección 5), nunca `ARG`/`--build-arg`.
- Para secretos que la aplicación necesita en tiempo de EJECUCIÓN (la contraseña de la base de datos, por ejemplo), se pasan como variables de entorno al arrancar el contenedor (`-e` o `environment:`), nunca copiados dentro de la imagen con `COPY`.

### 14.4 Separar lo que es estado de lo que es efímero

De la tabla de 14.1, en este ejemplo solo Postgres tiene datos que de verdad importa conservar; eso se traduce en un volumen con nombre (sección 7). Todo lo demás (el código de la app, sus dependencias instaladas, archivos temporales) vive dentro del contenedor y se puede recrear desde cero sin perder nada real. Si el proyecto además guarda archivos subidos por usuarios en disco o logs que hay que conservar, esos directorios también necesitan su propio volumen.

### 14.5 Decidir los límites de cada servicio

Un proyecto complejo casi nunca es un solo contenedor: es varios, cada uno con una única responsabilidad (el principio de "un proceso principal por contenedor" de la sección 11). En el ejemplo, aunque el backend Django y el worker Celery comparten exactamente el mismo código fuente, van en contenedores separados porque son procesos distintos que se escalan y se reinician de forma independiente. La misma imagen sirve para ambos: solo cambia el comando con el que se arranca cada contenedor.

### 14.6 El error más común: código que dice `localhost`

Este es, en la práctica, el obstáculo que más tiempo hace perder al dockerizar algo que ya existía. Un proyecto que hasta ahora corría todo en la misma máquina de desarrollo típicamente tiene código o configuración que dice, literalmente, `localhost:5432` para la base de datos o `localhost:6379` para Redis. Dentro de un contenedor, `localhost` se refiere al propio contenedor, nunca a un contenedor vecino, porque cada uno tiene su propia interfaz de loopback dentro de su propio network namespace (`sistema_y_docker.md` C5).

La solución es la misma resolución de nombres por DNS de la sección 6: cuando todos los servicios corren bajo el mismo `docker-compose.yml`, cada uno se resuelve por su nombre de servicio (`db`, `redis`), no por `localhost`. Por eso esos valores tienen que estar externalizados como variables de entorno (14.3): el mismo código sirve corriendo local (`localhost`) o dentro de Docker (`db`), sin tocar una línea, solo cambiando el valor de la variable según el entorno.

### 14.7 Escribir los Dockerfiles y el docker-compose.yml

Con 14.1 a 14.6 resueltos, escribir los archivos concretos es lo ya cubierto en las secciones 4 y 8: un Dockerfile por cada imagen distinta que hace falta (en el ejemplo, uno para el backend/worker en Python, otro para el build del frontend), y un `docker-compose.yml` que declara los servicios, sus variables de entorno, sus dependencias (`depends_on` con `condition: service_healthy`, sección 8) y sus volúmenes.

Estructura de archivos resultante:
```
proyecto/
├── backend/
│   ├── Dockerfile
│   └── (código Django/Celery)
├── frontend/
│   ├── Dockerfile        (multi-stage: build de React, luego nginx)
│   └── (código React)
├── docker-compose.yml
└── .env
```

```yaml
services:
  backend:
    build: ./backend
    command: gunicorn config.wsgi --bind 0.0.0.0:8000
    ports:
      - "8000:8000"
    environment:
      - DATABASE_URL=postgres://postgres:${DB_PASSWORD}@db:5432/app
      - REDIS_URL=redis://redis:6379/0
    depends_on:
      db:
        condition: service_healthy

  worker:
    build: ./backend
    command: celery -A config worker -l info
    environment:
      - DATABASE_URL=postgres://postgres:${DB_PASSWORD}@db:5432/app
      - REDIS_URL=redis://redis:6379/0
    depends_on:
      db:
        condition: service_healthy

  frontend:
    build: ./frontend
    ports:
      - "80:80"

  redis:
    image: redis:7-alpine

  db:
    image: postgres:16
    environment:
      - POSTGRES_PASSWORD=${DB_PASSWORD}
      - POSTGRES_DB=app
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 3s
      timeout: 3s
      retries: 5
    volumes:
      - db_data:/var/lib/postgresql/data

volumes:
  db_data:
```

`backend` y `worker` construyen de la misma imagen (`build: ./backend`) y solo cambian el `command:`, exactamente el punto de 14.5.

### 14.8 Verificar que de verdad es portable

Dockerizar algo no garantiza por sí solo que sea portable; lo garantiza haber quitado toda dependencia oculta de la máquina de desarrollo. La prueba real:

1. Detén cualquier instalación local de Postgres, Redis, o lo que sea que el proyecto usaba antes de dockerizarlo.
2. Borra cualquier archivo de configuración local que no sea el `.env` versionado del proyecto.
3. Levanta todo exclusivamente con `docker compose up -d`.
4. Si algo falla en este punto, es la señal de que quedó una dependencia oculta sin externalizar: una ruta absoluta, un servicio que el código asumía que ya estaba corriendo en la máquina, o una variable de entorno que solo existía en la shell local.

Esa es la definición práctica de portable: el proyecto corre igual en la máquina de otra persona, en un servidor de CI, o en producción, con el mismo `docker-compose.yml` y solo un `.env` distinto por entorno.

### 14.9 Checklist resumido

- [ ] Versión exacta de cada runtime identificada y reflejada en el `FROM` de cada Dockerfile (14.1, 14.2)
- [ ] Ninguna URL, contraseña o ruta absoluta hardcodeada en el código; todo viene de variables de entorno (14.3)
- [ ] Ningún secreto pasado por `ARG`/`--build-arg`; los de build usan `--mount=type=secret`, los de runtime van en `environment:`/`.env` (14.3)
- [ ] Cada dato que debe sobrevivir un `docker compose down` tiene su volumen con nombre (14.4)
- [ ] Cada proceso de larga duración va en su propio servicio, aunque comparta imagen con otro (14.5)
- [ ] Ningún `localhost` apuntando a otro servicio; todo usa el nombre del servicio de Compose (14.6)
- [ ] `.dockerignore` excluye lo que no debe ir a la imagen (sección 3, sección 4)
- [ ] Probado desde cero, sin nada instalado localmente aparte de Docker (14.8)

**🔧 Practica esto:** aplica esta metodología a un proyecto propio que ya exista y que hoy corras sin Docker. Completa primero la tabla de 14.1 antes de escribir ningún Dockerfile; en la mayoría de los casos, esa tabla sola ya revela qué se te habría olvidado externalizar si hubieras empezado directo por el código.
