FROM quay.io/lib/eclipse-temurin:17-jre

ADD target/convertjrb-*-jar-with-dependencies.jar /convertjrb.jar

RUN apt-get update && \
    apt-get install -y --no-install-recommends rrdtool && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /

ENTRYPOINT [ "java" , "-jar", "convertjrb.jar", "-rrdtool", "/usr/bin/rrdtool" ]

CMD [ "/data" ]
