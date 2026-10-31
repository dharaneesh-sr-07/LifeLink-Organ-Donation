FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN mkdir -p out

RUN javac -cp ".:lib/sqlite-jdbc-3.50.3.0.jar" -d out src/Main.java

EXPOSE 8080

CMD ["java", "-cp", ".:out:lib/sqlite-jdbc-3.50.3.0.jar", "Main"]