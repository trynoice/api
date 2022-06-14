<p align="center">
  <a href="https://trynoice.com">
    <img alt="Noice Logo" src="https://raw.githubusercontent.com/trynoice/.github/main/graphics/icon-round.png" width="92" />
  </a>
</p>
<h1 align="center">Noice API</h1>

[![Latest release][release-badge]][github-releases]
[![GitHub license][license-badge]](LICENSE)
[![API][gw-api-badge]][gw-api]
[![codecov][codecov-badge]][codecov]

## Configuration

The [`application.properties`][app-props] file declares all the configuration
options that the API server accepts in addition to configuration options of the
Spring Framework.

When started using the [`bootRun`][build.gradle] Gradle task, the API server
loads the [`application-dev-default.properties`][app-dev-default-props] and
[`application-dev.properties`][app-dev-props-stub] files. See "[Development
Configuration](#development-configuration)" for more details.

## Development

### Database

Create a PostgreSQL instance before running the API server. The `./scripts`
directory contains a Docker Compose helper configuration to create PostgreSQL
and PGAdmin containers. It also creates a fresh `develop` database as soon as
the PostgreSQL container starts. It is also the default data-source
configuration in [`application-dev-default.properties`][app-dev-default-props].

```sh
docker-compose -f scripts/postgres-with-docker-compose.yaml up -d
```

To delete and recreate the develop database, restart the `postgres-init`
container.

```sh
docker-compose -f scripts/postgres-with-docker-compose.yaml restart postgres-init
```

### Build and Run

Use [**API** run configuration][idea-run-api] to start the API server from
IntelliJ IDEA. Hot-reloading is pre-configured in
[`application-dev-default.properties`][app-dev-default-props]. Recompile the
project source using `Ctrl` (`Cmd`) + `F9` to hot-reload a running API server
instance.

To run from a terminal, use the Gradle wrapper.

```sh
./gradlew bootRun
```

To enable hot-reload, build the source continuously while running the `bootRun`
Gradle task in parallel.

```sh
./gradlew build --continuous
```

### Development Configuration

Both the `bootRun` Gradle task and the [**API** IntelliJ run
configuration][idea-run-api], activate the `dev-default` and `dev` Spring
profiles. [`application-dev-default.properties`][app-dev-default-props] contains
some sensible defaults required for development.
[`application-dev.properties`][app-dev-props-stub] is gitignored and meant to
store personal configuration, such as API secrets, etc.

### Integration Tests

[`application-test.properties`][app-test-props] contains configuration critical
for running integration tests. Both, the ["Integration Tests" IntelliJ run
configuration][idea-run-itests] and the `integrationTest` Gradle task, enable
the `test` profile to load this configuration.

The `test` profile configures the application to use the [PostgreSQL
module][testcontainers-pg] of [Testcontainers][testcontainers]. It requires
access to an active Docker daemon. It creates one PostgreSQL container per test
class and one database per test.

## License

[GNU GPL v3](LICENSE)

<a href="https://thenounproject.com/icon/white-noise-1287855/">
  <small>White Noise icon by Juraj Sedlák</small>
</a>

[release-badge]: https://img.shields.io/github/tag-date/trynoice/api.svg?color=orange&label=release
[github-releases]: https://github.com/trynoice/api/releases/
[license-badge]: https://img.shields.io/github/license/trynoice/api.svg
[license]: LICENSE
[gw-api-badge]: https://github.com/trynoice/api/actions/workflows/api.yaml/badge.svg?event=push
[gw-api]: https://github.com/trynoice/api/actions/workflows/api.yaml
[codecov-badge]: https://codecov.io/gh/trynoice/api/branch/main/graph/badge.svg
[codecov]: https://app.codecov.io/gh/trynoice/api/branch/main
[app-props]: src/main/resources/application.properties
[app-dev-default-props]: src/main/resources/application-dev-default.properties
[app-dev-props-stub]: src/main/resources/application-dev.properties
[app-test-props]: src/integrationTest/resources/application-test.properties
[build.gradle]: build.gradle
[idea-run-api]: .idea/runConfigurations/API.xml
[idea-run-itests]: .idea/runConfigurations/Integration_Tests.xml
[testcontainers]: https://www.testcontainers.org
[testcontainers-pg]: https://www.testcontainers.org/modules/databases/postgres/
