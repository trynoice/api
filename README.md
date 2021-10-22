# Noice API

## Development

### Recommendations

- Use IntelliJ IDEA for editing.
- Use Docker to run database server.

### Build & Run

Create a PostgreSQL instance before running the API server. The `./scripts` directory contains a
Docker Compose config to create PostgreSQL and pgadmin containers. It also creates a fresh `develop`
database when the PostgreSQL container starts. Furthermore, it is the default database configuration
declared in API server's [`application.properties`](src/main/resources/application.properties).

```sh
docker-compose -f scripts/postgres-with-docker-compose.yaml up -d
```

Use [**API** run configuration](.idea/runConfigurations/API.xml) to run the server from IntelliJ
IDEA. To hot-reload a running API server instance, recompile project files using `Ctrl (Cmd) + F9`.
A development configuration _might be needed_ for the hot-reload to work.

To run from the terminal, use the Gradle wrapper.

```sh
./gradlew build --continuous # to continuously build the new changes
./gradlew bootRun # to run once and/or auto restart whenever the build mutates
```

### Development Configuration

On running API server through the `bootRun` Gradle task or the [API IntelliJ run
configuration](.idea/runConfigurations/API.xml), `dev` Spring profile automatically activated. You
can customise [`application.properties`](src/main/resources/application.properties) for your
development environment by overriding them in `application-dev.properties` (which is loaded only for
the `dev` spring profile). It is ignored by Git and a sample file for the `dev` profile available
[here](src/main/resources/application-dev.properties.sample).

## Production Configuration

As a convention, all production configuration must be accepted through environment variables. To
enable such behaviour, use [`application.properties`](src/main/resources/application.properties) to
inject environment variables in Spring configuration.

| Name                            | Description                              |  Default  |
| ------------------------------- | ---------------------------------------- | :-------: |
| `PGDB_HOST`                     | PostgreSQL server host                   | localhost |
| `PGDB_PORT`                     | PostgreSQL server port                   |   5432    |
| `PGDB_NAME`                     | PostgreSQL database name                 |  develop  |
| `PGDB_USER`                     | PostgreSQL user name                     |   admin   |
| `PGDB_PASSWORD`                 | PostgreSQL user password                 | password  |
| `AUTH_HMAC_SECRET`              | HMAC secret for signing JWTs             |     -     |
| `AUTH_REFRESH_TOKEN_EXPIRY`     | Duration for the expiry of refresh token |    7d     |
| `AUTH_ACCESS_TOKEN_EXPIRY`      | Duration for the expiry of access token  |    30m    |
| `AUTH_SIGN_IN_TOKEN_EXPIRY`     | Duration for the expiry of sign-in token |    15m    |
| `AUTH_SIGN_IN_TOKEN_FROM_EMAIL` | `from` email for sending sign-in tokens  |     -     |
