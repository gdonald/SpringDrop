# SpringDrop

A clone of Drupal core built on Spring: Drupal's content model, extensibility model,
and admin experience in Java, with a server-rendered Bootstrap frontend.

## Requirements

- JDK 25
- PostgreSQL 17 running locally, or the containers in `compose.yaml`
- Node 22 for the frontend tests
- Docker for the Testcontainers integration tests and for building the image

## Local development

The `dev` profile targets a native local Postgres. Create the database once:

```
createuser -s springdrop
createdb -O springdrop springdrop
```

Then run the app on http://localhost:8080:

```
./gradlew bootRun
```

Override the connection with `SPRINGDROP_DB_URL`, `SPRINGDROP_DB_USERNAME`, and
`SPRINGDROP_DB_PASSWORD`. To run the dependencies in containers instead, start
`docker compose up -d` and point `SPRINGDROP_DB_URL` at port 55432.

## Tests

```
./test.sh              # backend and frontend, with both 100% coverage gates
./test.sh --backend
./test.sh --frontend
```

The build compiles with `-Xlint:all -Werror`, so a compiler warning fails the build
rather than piling up. There are no warning suppressions in the codebase: a warning is
answered by changing the code, which for an untyped value read back from JSON, YAML, or
a jsonb column means a checked conversion rather than an unchecked cast.

## Error pages

403, 404, and 500 render themed Bootstrap pages. A site can replace the 403 and 404
pages with its own content by storing internal paths in the `system.error_pages`
config object, in the `forbiddenPath` and `notFoundPath` keys. A blank path keeps the
themed default.

## Production image

`bootBuildImage` builds a layered OCI image with the Paketo builder. The Java
buildpack runs a CDS training run and bakes the resulting archive into the image, so
startup loads shared class data instead of reparsing the classes.

```
./gradlew bootBuildImage
```

The image carries no database configuration. Run it against an external Postgres by
passing the connection in the environment:

```
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRINGDROP_DB_URL=jdbc:postgresql://db.internal:5432/springdrop \
  -e SPRINGDROP_DB_USERNAME=springdrop \
  -e SPRINGDROP_DB_PASSWORD=secret \
  springdrop:0.1-SNAPSHOT
```

In `prod` the `/actuator/health` and `/actuator/info` endpoints require a user
holding the `administer site configuration` permission. Outside `prod` they are open.

The `prod` profile also reads `SPRINGDROP_REDIS_URL`, `SPRINGDROP_MAIL_HOST`, and
`SPRINGDROP_MAIL_PORT`, and logs ECS-format JSON to the console.
