# Image de production du backend Billet+ (Spring Boot, JDK 21).
#
# Construction, depuis la racine du dépôt :
#   docker build -t billetplus-backend backend
#
# Deux étapes : la première compile avec le JDK et Maven, la seconde ne garde que
# le JRE et le jar (image plus petite, sans compilateur ni sources).
# Les tests ne sont pas lancés ici : la CI les exécute (./mvnw clean verify)
# avant tout merge, avec Docker pour Testcontainers, absent pendant un build d'image.

FROM eclipse-temurin:21-jdk AS construction
WORKDIR /build

# Dépendances d'abord, dans une couche à part : tant que pom.xml ne change pas,
# Docker réutilise cette couche et ne retélécharge rien.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/backend-*.jar application.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

# Utilisateur sans privilège : une faille dans l'application ne donne pas root dans le conteneur.
RUN groupadd --system billetplus && useradd --system --gid billetplus --no-create-home billetplus

COPY --from=construction /build/application.jar application.jar
USER billetplus

ENV SPRING_PROFILES_ACTIVE=prod
# La JVM dimensionne son tas sur la mémoire du conteneur, pas sur celle de la machine.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
