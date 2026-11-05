# Security Policy

This repository contains a Spring Boot / Spring Cloud internet-banking reference
platform (API gateway, config server, service registry, user, fund-transfer,
utility-payment and core-banking services, plus Keycloak/MySQL docker-compose
support). Because it models financial workflows, we take security reports seriously.

## Supported Versions

Only the latest code on the `main` branch (release line 2.x: Java 21,
Spring Boot 3.2.x, Spring Cloud 2023.0.x) receives security fixes.

| Version                         | Supported          |
| ------------------------------- | ------------------ |
| 2.x (`main`)                    | :white_check_mark: |
| 1.x (Java 11 / Spring Boot 2)   | :x:                |

## Reporting a Vulnerability

**Please do not open public GitHub issues, pull requests or discussions for
security vulnerabilities.**

Report vulnerabilities privately through GitHub's private vulnerability reporting:

1. Go to the repository's **Security** tab.
2. Click **Report a vulnerability** (Security Advisories → *New draft security advisory*).
3. Include the affected service(s) (e.g. `core-banking-service`,
   `internet-banking-api-gateway`), version/commit, reproduction steps,
   and the potential impact (e.g. authentication bypass, unauthorized fund
   transfer, data exposure).

Direct link: https://github.com/Cognition-Partner-Workshops/ts-java-spring-boot-internet-banking/security/advisories/new

### What to expect

- Acknowledgement within **3 business days**.
- An initial assessment and severity rating within **10 business days**.
- Fixes are developed in a private advisory fork and released on `main`;
  the advisory is published once a fix is available, crediting the reporter
  unless anonymity is requested.

## Scope notes

- Credentials and client secrets found in `docker-compose/` (Keycloak realm
  export, MySQL) and `postman_collection/` are local-development defaults and
  must never be used in a real deployment. Reports about their mere presence are
  out of scope; reports about them leaking into non-local configuration are in scope.
- Vulnerabilities in third-party dependencies should also be reported upstream;
  Dependabot is enabled on this repository to track dependency updates.
