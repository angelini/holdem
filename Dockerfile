FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/holdem.jar /holdem/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/holdem/app.jar"]
