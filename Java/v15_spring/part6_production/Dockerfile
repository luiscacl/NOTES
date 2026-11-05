# La imagen de la aplicación empaquetada (packaging/PackagingAndDocker, CAPAS 4 a 6).
# Antes:  ./mvnw -pl part6_production package      (desde v15_spring/; deja el jar en target/)
# Luego:  docker build -t tienda part6_production

# --- Etapa 1: separar el jar en capas. Esta etapa NO llega a la imagen final. ---
FROM eclipse-temurin:25-jre AS builder
WORKDIR /builder
COPY target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# --- Etapa 2: la imagen que corre. Solo el JRE y las capas, de la que menos cambia a la que más. ---
FROM eclipse-temurin:25-jre
# Nunca como root (CAPA 5). Va ARRIBA: lo que no cambia, antes de lo que cambia (CAPA 4).
RUN useradd --system --uid 1001 spring
WORKDIR /app
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# Caché AOT de Java 25 (CAPA 6): un arranque de "entrenamiento" que se detiene al terminar de
# crear el contexto (spring.context.exit=onRefresh) y deja anotado lo que cargó.
RUN java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar app.jar

USER spring

EXPOSE 8080
# Forma "exec" (lista JSON): java es el proceso 1 y recibe SIGTERM directo (CAPA 5).
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
