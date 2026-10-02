# ==========================================
# Etapa 1: Build del artefacto con Maven y JDK 21 LTS
# ==========================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar pom.xml primero
COPY pom.xml .

# Copiar código fuente y recursos (incluye WSDL local)
COPY src ./src

# Empaquetar JAR omitiendo ejecución de tests durante el build de imagen
RUN mvn clean package -DskipTests

# ==========================================
# Etapa 2: Runtime liviano para despliegue en Render (Alpine Linux)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiar el ejecutable generado en la etapa anterior
COPY --from=build /app/target/microservice-ticketing-gp-*.jar app.jar

# Optimización estricta de memoria para el contenedor en Render (Plan Free 512 MB RAM):
# -XX:+UseSerialGC: Recolector de basura ligero de bajo overhead.
# -Xss512k: Reduce el uso de memoria de stack por thread.
# -XX:MaxRAMPercentage=75: Asigna hasta el 75% de RAM al Heap (~384MB max).
ENV JAVA_OPTS="-XX:+UseSerialGC -Xss512k -XX:MaxRAMPercentage=75"

# Puerto asignado dinámicamente por Render (default 8080)
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

