FROM eclipse-temurin:21-jre-alpine

# 安全加固（缺陷 1.27）：使用非 root 用户运行 Java 进程，降低容器逃逸风险
RUN addgroup -S appuser && adduser -S -G appuser appuser

WORKDIR /app
COPY target/*.jar app.jar
RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Djava.security.egd=file:/dev/./urandom", "app.jar"]
