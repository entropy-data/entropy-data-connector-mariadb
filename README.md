Entropy Data Connector for MariaDB
===

The connector for MariaDB is a Spring Boot application that uses the [entropy-data-sdk](https://github.com/entropy-data/entropy-data-sdk) internally, and is available as a ready-to-use Docker image [entropydata/entropy-data-connector-mariadb](https://hub.docker.com/repository/docker/entropydata/entropy-data-connector-mariadb) to be deployed in your environment.

## Features

- **Asset Synchronization**: Extract database schemas, tables, and views from your MariaDB database and synchronize them with your Entropy Data instance.
- Filter out system schemas like `information_schema`
- Schedule periodic synchronization with configurable intervals
- State tracking to only synchronize changed assets

## Usage

Start the connector using Docker. You must pass the API keys as environment variables.

```
docker run \
  -e ENTROPYDATA_CLIENT_APIKEY='insert-api-key-here' \
  -e ENTROPYDATA_CLIENT_MARIADB_CONNECTION_HOST='your-mariadb-server' \
  -e ENTROPYDATA_CLIENT_MARIADB_CONNECTION_PORT='3306' \
  -e ENTROPYDATA_CLIENT_MARIADB_CONNECTION_DATABASE='your-database' \
  -e ENTROPYDATA_CLIENT_MARIADB_CONNECTION_USERNAME='your-username' \
  -e ENTROPYDATA_CLIENT_MARIADB_CONNECTION_PASSWORD='your-password' \
  entropydata/entropy-data-connector-mariadb:latest
```

## Versions

Every release is published as an immutable image tag. Pin a version rather than following `latest`:

```
entropydata/entropy-data-connector-mariadb:0.3.0
```

| Tag | Meaning |
|---|---|
| `X.Y.Z` | A released version. Immutable, and the recommended way to run the connector. |
| `latest` | The most recent release. Moves with every release. |
| `sha-<commit>` | A single commit on `main`, published so that a change can be tried out before it is released. |

Release images are signed with [cosign](https://docs.sigstore.dev/), and carry an SBOM and build provenance:

```
cosign verify entropydata/entropy-data-connector-mariadb:0.3.0 \
  --certificate-identity-regexp 'https://github.com/entropy-data/entropy-data-connector-mariadb/.*' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

## Configuration

| Environment Variable                                    | Default Value                      | Description                                                                       |
|--------------------------------------------------------|------------------------------------|-----------------------------------------------------------------------------------|
| `ENTROPYDATA_CLIENT_HOST`                           | `https://api.entropy-data.com` | Base URL of the Entropy Data API.                                            |
| `ENTROPYDATA_CLIENT_APIKEY`                         |                                    | API key for authenticating requests to the Entropy Data.                     |
| `ENTROPYDATA_CLIENT_MARIADB_CONNECTION_HOST`        | `localhost`                        | MariaDB server hostname                                                           |
| `ENTROPYDATA_CLIENT_MARIADB_CONNECTION_PORT`        | `3306`                             | MariaDB server port                                                               |
| `ENTROPYDATA_CLIENT_MARIADB_CONNECTION_DATABASE`    |                                    | Database name to connect to                                                       |
| `ENTROPYDATA_CLIENT_MARIADB_CONNECTION_USERNAME`    |                                    | Username for MariaDB connection                                                   |
| `ENTROPYDATA_CLIENT_MARIADB_CONNECTION_PASSWORD`    |                                    | Password for MariaDB connection                                                   |
| `ENTROPYDATA_CLIENT_MARIADB_ASSETS_ENABLED`         | `true`                             | Enable assets synchronization                                                     |
| `ENTROPYDATA_CLIENT_MARIADB_ASSETS_CONNECTORID`     | `mariadb-assets`                   | Unique ID for this connector instance                                             |
| `ENTROPYDATA_CLIENT_MARIADB_ASSETS_POLLINTERVAL`    | `PT10M`                            | Synchronization interval in ISO-8601 duration format (PT10M means 10 minutes)     |

## Resources

The connector needs **at least 1 GB of container memory**. The image sets a heap limit accordingly:

```
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError
```

Without `MaxRAMPercentage`, the JVM caps the heap at 25% of the container memory. `ExitOnOutOfMemoryError` terminates the
container instead of leaving it running with a dead synchronization thread, so that your orchestrator can restart it.

Setting `JAVA_TOOL_OPTIONS` at runtime **replaces** these flags rather than adding to them. Repeat the flags you want to keep:

```
-e JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError -javaagent:/agent.jar'
```

Expect the container to use around 60% of its memory limit under load. Adjust memory alarms accordingly.

### Synchronization Health

The health endpoint reports whether the asset synchronization is still up to date:

```
curl http://localhost:8080/actuator/health
```

The `assetsSynchronizationHealth` component reports `DEGRADED` when the last run failed, or when no run has succeeded for three
poll intervals, and names the failure in `lastFailure`. It is deliberately not reported as `DOWN`, and the endpoint still responds
with 200, because the usual cause is an unavailable data platform, which restarting the container does not fix. Point liveness
probes at `/actuator/health/liveness`, which is unaffected by the synchronization state.

## Building

```bash
./mvnw clean package
```

## Running

### Using Java

```bash
./mvnw spring-boot:run
```

### Using Docker

```bash
docker build -t entropydata/entropy-data-connector-mariadb .

docker run -d \
  -e ENTROPYDATA_CLIENT_HOST=https://api.entropy-data.com \
  -e ENTROPYDATA_CLIENT_APIKEY=your-api-key \
  -e ENTROPYDATA_CLIENT_MARIADB_CONNECTION_HOST=your-mariadb-server \
  -e ENTROPYDATA_CLIENT_MARIADB_CONNECTION_PORT=3306 \
  -e ENTROPYDATA_CLIENT_MARIADB_CONNECTION_DATABASE=your-database \
  -e ENTROPYDATA_CLIENT_MARIADB_CONNECTION_USERNAME=your-username \
  -e ENTROPYDATA_CLIENT_MARIADB_CONNECTION_PASSWORD=your-password \
  -e ENTROPYDATA_CLIENT_MARIADB_ASSETS_CONNECTORID=mariadb-assets \
  -p 8080:8080 \
  --name entropy-data-connector-mariadb \
  entropydata/entropy-data-connector-mariadb
```

## Development

### Prerequisites

- Java 17
- Maven or the included Maven Wrapper
- Docker (for running tests with TestContainers)

### Running Tests

```bash
./mvnw test
```

## Implementation Details

The connector implements the Entropy Data SDK interfaces:

1. `EntropyDataAssetsProvider` - Extracts metadata from MariaDB using JDBC DatabaseMetaData
2. Uses the Entropy Data SDK's synchronization functionality to maintain state
3. Transforms MariaDB metadata into the Entropy Data Asset model
4. Schedules regular synchronization based on configured intervals

## Health Check

The connector exposes health and info endpoints:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/actuator/info`

## License

[Apache 2.0](LICENSE)