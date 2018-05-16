ARG MARATHON_VERSION
FROM mesosphere/marathon:$MARATHON_VERSION
ARG APP_VERSION
ARG MARATHON_VERSION
ADD build/distributions/marathon-vault-plugin_${MARATHON_VERSION}-${APP_VERSION}.tar /plugins/
COPY plugin-conf-test.json /plugin-conf-test.json
