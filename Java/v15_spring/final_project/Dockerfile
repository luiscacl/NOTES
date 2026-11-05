# La imagen de la tienda (part6_production/.../packaging/PackagingAndDocker explica cada línea).
# Antes:  ./mvnw -pl final_project package            (desde v15_spring/)
# Luego:  docker compose --profile app up --build     (desde final_project/; ver README.md)

FROM eclipse-temurin:25-jre AS builder
WORKDIR /builder
COPY target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 spring
WORKDIR /app
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# Caché AOT. El entrenamiento crea el contexto, y aquí NO hay base de datos: se arranca sin
# migraciones y sin que Hibernate consulte la base (le decimos el dialecto). El pool de conexiones
# no se conecta hasta que alguien pide una conexión, y nadie la pide.
RUN java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh \
        -Dspring.flyway.enabled=false \
        -Dspring.jpa.hibernate.ddl-auto=none \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
        -Dspring.datasource.url=jdbc:postgresql://training-only/none \
        -Dshop.jwt.ephemeral-keys=true \
        -jar app.jar

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
