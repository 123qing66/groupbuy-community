# 应用镜像（配合 docker-compose.yml，进阶项 Issue #33）
# 先在本机打 jar：mvn clean package -DskipTests
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY target/groupbuy-community-1.0.0-SNAPSHOT.jar app.jar

# 容器里 MySQL / Redis 的地址由 compose 的服务名提供
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
