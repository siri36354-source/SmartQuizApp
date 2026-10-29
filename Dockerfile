FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY src ./src
COPY web ./web
COPY questions.csv ./questions.csv
RUN mkdir -p out && javac -d out src/*.java
ENV PORT=8081
EXPOSE 8081
CMD ["sh", "-c", "java -cp out ApiServer"]
