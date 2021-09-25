FROM clojure:openjdk-17-tools-deps-alpine AS clojure
RUN apk add git
RUN adduser -D pa
USER pa
WORKDIR /home/pa
COPY --chown=pa ./deps.edn ./
RUN clojure -A:dev -Spath && clojure -Spath
COPY --chown=pa . .
RUN clojure -A:dev -M -m pa.build

FROM openjdk:17-jdk-alpine
RUN adduser -D pa
USER pa
WORKDIR /home/pa
COPY --from=clojure --chown=pa /home/pa/target/ap.jar ./
CMD ["java", "-jar", "pa.jar"]
