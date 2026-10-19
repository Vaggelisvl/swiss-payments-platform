-- One database per service: services never share tables.
CREATE DATABASE ingest;
CREATE DATABASE processor;
CREATE DATABASE reconciliation;
