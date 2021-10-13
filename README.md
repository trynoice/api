# Noice API

## Development

### Recommendations

- Use IntelliJ IDEA for editing.
- Use Docker to run database server.

### Build & Run

Create a PostgreSQL instance before running the API server. The `./scripts` directory contains a
Docker Compose config to create PostgreSQL and pgadmin containers. It also creates a fresh `develop`
database when the PostgreSQL container starts. Furthermore, the API server uses this configuration
in development environment through [dev](src/main/resources/application-dev.properties) Spring
profile.

```sh
docker-compose -f scripts/postgres-with-docker-compose.yaml up -d
```

Use [**API** run configuration](.idea/runConfigurations/API.xml) to run the server from the IntelliJ
IDEA. To hot-reload a running API server instance, recompile project files using `Ctrl (Cmd) + F9`.

To run from the terminal, use the Gradle wrapper.

```sh
./gradlew build --continuous # to continuously build the new changes
./gradlew bootRun # to run once and/or auto restart whenever the build mutates
```

## Production Configuration

As a convention, all production configuration must be accepted through environment variables. To
enable such behaviour, use [application.properties](src/main/resources/application.properties) to
inject environment variables in Spring configuration.

| Name            | Description              | Default |
| --------------- | ------------------------ | :-----: |
| `PGDB_HOST`     | PostgreSQL server host   |    -    |
| `PGDB_PORT`     | PostgreSQL server port   |  5432   |
| `PGDB_NAME`     | PostgreSQL database name |    -    |
| `PGDB_USER`     | PostgreSQL user name     |    -    |
| `PGDB_PASSWORD` | PostgreSQL user password |    -    |
