#Stage 1: BUILDER: JDK + Maven for compile. 
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy only pom.xml first, then load dependency
# only rebuild when pom.xml changed -> cache all library
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now, copy source. when update code
COPY src ./src
RUN mvn clean package -DskipTests -B

# Slice fat jar to layer tool 
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

#Stage 2: RUNTIME: only layered JRE + jar. this is the last image
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Create normal user, not use root to run
RUN groupadd -r aiops && useradd -r -g aiops aiops

# Copy each layer serial least change -> usually change
COPY --from=builder /build/target/extracted/dependencies/ ./
COPY --from=builder /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/target/extracted/application/ ./

# Down permission to normal user before start
USER aiops


# loader of spring boot 3.x 
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# build image: docker build -t aiops-app:dev .
# check size: docker images aiops-app:dev   