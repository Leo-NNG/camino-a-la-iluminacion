# Imagen de la aplicación: compila ROOT.war con Maven y lo despliega en Tomcat 10.1 (Java 17).
# Uso normal: docker compose up --build (ver docker-compose.yml y backend/README.md).

# --- Etapa 1: compilar el WAR (backend + frontend) ---
FROM maven:3.9-eclipse-temurin-17 AS compilacion
WORKDIR /app
# Primero solo el pom: Docker reutiliza las dependencias descargadas mientras el pom no cambie.
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -ntp -f backend/pom.xml dependency:go-offline
COPY index.html ./
COPY paginas paginas
COPY css css
COPY js js
COPY vendor vendor
COPY backend/src backend/src
# Las pruebas corren en GitHub Actions (y con mvn test en tu PC); aquí solo se empaqueta.
RUN mvn -B -ntp -o -f backend/pom.xml -DskipTests package

# --- Etapa 2: Tomcat con solo el WAR ---
FROM tomcat:10.1-jdk17-temurin
ENV TZ=America/Lima \
    DOCS_DIR=/datos/docs \
    CAI_LOG_DIR=/datos/logs
# Sin las aplicaciones de ejemplo de Tomcat; la app queda en la raíz (/).
# Usuario sin privilegios: Tomcat no corre como root dentro del contenedor.
RUN rm -rf /usr/local/tomcat/webapps/* \
    && useradd --system --uid 10001 --home-dir /datos --shell /usr/sbin/nologin cai \
    && mkdir -p /datos/docs /datos/logs \
    && chown -R cai:cai /datos /usr/local/tomcat/webapps /usr/local/tomcat/work /usr/local/tomcat/temp /usr/local/tomcat/logs
COPY --from=compilacion --chown=cai:cai /app/backend/target/ROOT.war /usr/local/tomcat/webapps/ROOT.war
USER cai
WORKDIR /datos
EXPOSE 8080
CMD ["catalina.sh", "run"]
