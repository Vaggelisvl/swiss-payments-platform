# Dashboard for the whole platform: static files plus a reverse proxy to the four services (no CORS needed).
FROM nginx:1.27-alpine
ENV INGEST_UPSTREAM=ingest:8080 \
    PROCESSOR_UPSTREAM=processor:8080 \
    RECONCILIATION_UPSTREAM=reconciliation:8080 \
    ASSISTANT_UPSTREAM=assistant:8080 \
    DNS_RESOLVER=127.0.0.11
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY index.html /usr/share/nginx/html/index.html
EXPOSE 80
