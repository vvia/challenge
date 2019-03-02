
# Alpine Linux with OpenJDK JRE
FROM openjdk:8-jre-alpine
RUN apk add --no-cache bash

RUN mkdir challenge
COPY target/challenge*.jar /challenge/challenge.jar 

WORKDIR challenge

EXPOSE 4000/tcp

ENTRYPOINT ["/usr/bin/java", "-jar", "challenge.jar"]

# run application with this command line 
#CMD ["/usr/bin/java", "-jar", "/challenge.jar"]

