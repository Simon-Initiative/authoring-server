FROM olisimon/wildfly-mysql:4.5
MAINTAINER Raphael Gachuhi, oli.cmu.edu

ADD execute.sh ${JBOSS_HOME}/
ADD wait-for-it.sh ${JBOSS_HOME}/
RUN chmod +x ${JBOSS_HOME}/execute.sh && chmod +x ${JBOSS_HOME}/wait-for-it.sh

ADD standalone.xml ${JBOSS_HOME}/standalone/configuration/

USER root

RUN rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history/current/*

RUN mkdir -p /oli/course_content_xml \
    && mkdir /oli/course_content_volume \
    && mkdir /oli/webcontent \
    && mkdir /oli/repository \
    && mkdir /oli/service_config \
    && mkdir /oli/dtd \
    && chown -R jboss:0 /oli \
    && chmod a+rwx -R /oli

USER jboss

ENV CONTENT_SERVICE_CONFIG /oli/service_config/content-service-conf.json
COPY data/ /oli/
COPY conf/status.war target/content-service.wa* $DEPLOYMENT_DIR

ENTRYPOINT ${JBOSS_HOME}/execute.sh
