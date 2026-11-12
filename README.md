# Sanitation Management System (Backend)

REST API backend that connects **customers** who need sanitation services with **sanitation organizations** that provide them.

## Roles
`CUSTOMER`, `ORGANIZATION`, `ADMIN`

## Planned features
- Registration and authentication
- Organization registration, verified by an admin
- Organizations manage their business information
- Customers submit service requests; organizations accept or reject them
- Service request status tracking
- Payments and platform commission
- Reviews and complaints
- Notifications

> Organizations manage their own workers **outside** this platform. There are no worker, employee, salary or workforce modules by design.

## Tech stack
Java 17 · Spring Boot 3.x · Maven · MySQL · Spring Web, Data JPA, Validation, Security · Lombok (optional)

## Status
Foundation only: package structure, build and configuration. No business logic, entities or endpoints yet.

## Run locally
**Prerequisites:** JDK 17+, Maven 3.9+, MySQL 8+

1. Create the database:
   ```sql
   CREATE DATABASE sanitation_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. Copy `.env.example` to `.env` for reference, then export the variables (Spring does not load `.env` itself):
   ```bash
   export DB_USERNAME=... DB_PASSWORD=...
   ```
3. Build and test:
   ```bash
   mvn clean test
   ```
4. Run:
   ```bash
   mvn spring-boot:run
   ```

Note: with Spring Security on the classpath, all endpoints are locked behind a generated password until a security configuration is added.

## Package overview
Base package: `com.sanitation.management`

| Package | Purpose |
|---|---|
| `config` | Cross-cutting configuration (security, CORS, etc.) |
| `common` | Shared exceptions, response wrappers, utilities |
| `auth` | Registration, login, tokens |
| `user` | User accounts and roles |
| `organization` | Organization profiles and admin verification |
| `service_request` | Service requests and their status lifecycle |
| `payment` | Payments and platform commission |
| `review` | Customer reviews |
| `complaint` | Customer complaints |
| `notification` | Notifications |
| `admin` | Admin-only operations |

Empty packages contain `.gitkeep` so Git preserves them; delete it when you add real files.
