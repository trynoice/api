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

Both the `bootRun` Gradle task and the [**API** IntelliJ run
configuration](.idea/runConfigurations/API.xml), activate the `dev` Spring profile. To customise
[`application.properties`](src/main/resources/application.properties) for your development
environment, override properties in `application-dev.properties` (loaded when the `dev` Spring
profile is active). `application-dev.properties` is ignored by Git and a sample file is available
[here](src/main/resources/application-dev.properties.sample).

### Integration Tests

[`application-test.properties`](src/integrationTest/resources/application-test.properties) contain
configuration critical for running integration tests. Both, the ["Integration Tests" IntelliJ run
configuration](.idea/runConfigurations/Integration_Tests.xml) and the `integrationTest` Gradle task,
enable `test` profile by default to load this configuration before running integration tests.

The `test` profile configures the application to use the [PostgreSQL
module](https://www.testcontainers.org/modules/databases/postgres/) of
[Testcontainers](https://www.testcontainers.org). It requires access to an active Docker daemon. It
creates one PostgreSQL container per test class and one database per test. One should note that a
[`ParameterizedTest`](https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests)
set is considered a single test, i.e. all cases in a parameterized test will share the same
database.

## Production Configuration

As a convention, all production configuration must be accepted through environment variables. To
enable such behaviour, use [`application.properties`](src/main/resources/application.properties) to
inject environment variables in Spring configuration.

| Name                               | Description                                   |             Default              |
| ---------------------------------- | --------------------------------------------- | :------------------------------: |
| `PGDB_HOST`                        | PostgreSQL server host                        |            localhost             |
| `PGDB_PORT`                        | PostgreSQL server port                        |               5432               |
| `PGDB_NAME`                        | PostgreSQL database name                      |             develop              |
| `PGDB_USER`                        | PostgreSQL user name                          |              admin               |
| `PGDB_PASSWORD`                    | PostgreSQL user password                      |             password             |
| `APP_DOMAIN`                       | App domain for cookies and CORS policy        |                -                 |
| `AUTH_HMAC_SECRET`                 | HMAC secret for signing JWTs                  |                -                 |
| `AUTH_REFRESH_TOKEN_EXPIRY`        | Duration for the expiry of refresh token      |                7d                |
| `AUTH_ACCESS_TOKEN_EXPIRY`         | Duration for the expiry of access token       |               30m                |
| `AUTH_SIGN_IN_TOKEN_EXPIRY`        | Duration for the expiry of sign-in token      |               15m                |
| `AUTH_SIGN_IN_TOKEN_FROM_EMAIL`    | `from` email for sending sign-in tokens       |                -                 |
| `AUTH_SIGN_IN_TOKEN_SUPPORT_EMAIL` | Support address to include in sign-in emails  |                -                 |
| `ANDROID_PUBLISHER_API_KEY_PATH`   | Service account key for Android Publisher API | `android-publisher-api-key.json` |
