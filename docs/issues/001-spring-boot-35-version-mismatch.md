# SDK Fails In Spring Boot 3.5.x App Due To Version Mismatch

## Summary

When testing `vault-sdk` in a separate Spring Boot application, the app fails during startup before the SDK auth flow can run.

## Environment

- Consuming app: separate Spring Boot app
- Consuming app Spring Boot version: `3.5.7`
- SDK dependency observed on classpath: `io.github.hesandaliyanage:vault-sdk:0.1.0`
- Java runtime shown by IntelliJ: Temurin `24.0.2`

## Error

```text
java.lang.IllegalStateException: Could not evaluate condition on org.springframework.boot.devtools.autoconfigure.DevToolsDataSourceAutoConfiguration due to org/springframework/boot/jdbc/autoconfigure/DataSourceProperties not found.
```

Root cause:

```text
Caused by: java.lang.NoClassDefFoundError: org/springframework/boot/jdbc/autoconfigure/DataSourceProperties
Caused by: java.lang.ClassNotFoundException: org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
```

## Notable Classpath Clue

The consuming app is mostly using Spring Boot `3.5.7`, but `spring-boot-devtools` appears as `4.0.6`:

```text
spring-boot-3.5.7.jar
spring-boot-autoconfigure-3.5.7.jar
spring-boot-devtools-4.0.6.jar
```

This suggests a Spring Boot 3 / Spring Boot 4 dependency mismatch.

## Expected Behavior

A Spring Boot 3.5.x consuming app should either run with a compatible SDK artifact or fail with a clear documented compatibility requirement.

## Investigation Direction

- Confirm supported Spring Boot version range for `vault-sdk`.
- Check whether the SDK should target Spring Boot 3.x instead of Spring Boot 4.x for broader compatibility.
- Check whether SDK dependencies should avoid forcing Boot-managed versions into the consuming app.
- Check whether the consuming app explicitly added `spring-boot-devtools:4.0.6`.
- Verify whether Maven Central version `0.1.0` differs from current local `0.1.1` changes.

## Acceptance Criteria

- Confirm supported Spring Boot version range.
- Update SDK POM/dependency strategy if needed.
- Add compatibility note to README.
- Verify startup in a clean Spring Boot 3.5.x consumer app.
