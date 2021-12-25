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
configuration](.idea/runConfigurations/API.xml), activate the `dev-default` and `dev` Spring
profiles.
[`application-dev-default.properties`](src/main/resources/application-dev-default.properties)
contains some sensible defaults required for development, whereas,
[`application-dev.properties`](src/main/resources/application-dev.properties) is gitignored and
meant to store developer's personalised configuration (such as API secrets, etc.).

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

All production configuration must be accepted through a separate properties file. The following
properties must be configured before deploying the application to the production environment.

| Name                                               | Description                                                      |
| -------------------------------------------------- | ---------------------------------------------------------------- |
| `spring.datasource.url`                            | Spring datasource URL (PostgreSQL DSN)                           |
| `spring.datasource.username`                       | PostgreSQL user name                                             |
| `spring.datasource.password`                       | PostgreSQL user password                                         |
| `app.cors.allowed-origins`                         | Comma-separated list of allowed origins patterns for CORS        |
|                                                    | `https://*.domain1.com` - all endings with domain1.com           |
|                                                    | `https://*.domain1.com:[8080,8081]` - all endings with           |
|                                                    | domain1.com on port 8080 or 8081                                 |
|                                                    | `https://*.domain1.com:[*]` - all endings with domain1.com       |
|                                                    | with any port (including default port)                           |
|                                                    |                                                                  |
| `app.auth.hmac-secret`                             | HMAC secret for signing JWTs                                     |
| `app.auth.refresh-token-expiry`                    | Duration for the expiry of refresh token (default: `7d`)         |
| `app.auth.access-token-expiry`                     | Duration for the expiry of access token (default: `30m`)         |
| `app.auth.sign-in-token-expiry`                    | Duration for the expiry of sign-in token (default: `15m`)        |
| `app.auth.cookie-domain`                           | Domain value to use when sending cookies to clients              |
| `app.subscriptions.android-publisher-api-key-path` | Path of the service account key to access Android Publisher API  |
| `app.subscriptions.stripe-api-key`                 | Secret API key to access Stripe API                              |
| `app.subscriptions.stripe-webhook-secret`          | Secret to verify webhook event payload using HMAC-256 signatures |
| `app.sounds.library-manifest-s3-bucket`            | Name of the S3 bucket that hosts the library manifest            |
| `app.sounds.library-manifest-s3-key`               | Path of the library manifest in the given S3 bucket              |
| `app.sounds.library-manifest-cache-ttl`            | TTL for library manifest cache from the S3 bucket (default: 5m)  |
