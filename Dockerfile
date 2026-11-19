FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY java ./java
RUN mkdir classes && javac -encoding UTF-8 --release 11 -d classes java/com/mycompany/facturacionventasposykardex/*.java

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/classes ./classes
COPY lib/postgresql-42.7.14.jar ./lib/postgresql-42.7.14.jar
COPY web ./web
USER 10001:1000
ENV SURTI_MODE=cloud
EXPOSE 10000
CMD ["java", "-XX:MaxRAMPercentage=60.0", "-Dfile.encoding=UTF-8", "-cp", "classes:lib/*", "com.mycompany.facturacionventasposykardex.SurtiladinoWeb"]
