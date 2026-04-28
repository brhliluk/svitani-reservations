# Building and running

To build the application, you need to have JDK 21 or later installed.

The following tasks are available:

- `./gradlew jsViteRun` - run the Vite dev server for JS target on `http://localhost:3000`
- `./gradlew -t jsViteCompileDev` - run the development compilation in continuous mode for JS target
- `./gradlew jsViteBuild` - build production application for JS target to `build/vite/js/dist` directory
- `./gradlew wasmJsViteRun` - run the Vite dev server for Wasm target on `http://localhost:3000`
- `./gradlew -t wasmJsViteCompileDev` - run the development compilation in continuous mode for Wasm target
- `./gradlew wasmJsViteBuild` - build production application for Wasm target to `build/vite/wasmJs/dist` directory
- `./gradlew -t jsBrowserDevelopmentRun` - run the webpack dev server in continuous build mode for JS target on `http://localhost:3000`
- `./gradlew -t wasmJsBrowserDevelopmentRun` - run the webpack dev server in continuous build mode for Wasm target on `http://localhost:3000`
- `./gradlew -t jvmRun` - run the JVM dev server on `http://localhost:8080`
- `./gradlew jsBrowserDistribution` - build production application for JS target to `build/dist/js/productionExecutable` directory
- `./gradlew wasmJsBrowserDistribution` - build production application for Wasm target to `build/dist/wasmJs/productionExecutable` directory
- `./gradlew jarWithJs` - build full application with JS frontend to `build/libs` directory
- `./gradlew jarWithWasmJs` - build full application with Wasm frontend to `build/libs` directory
- `./gradlew exportWithJs` - export pre-rendered static website with JS frontend to `build/site` directory
- `./gradlew exportWithWasmJs` - export pre-rendered static website with Wasm frontend to `build/site` directory

Note: For auto reload with Ktor JVM backend you need to run  `./gradlew -t compileKotlinJvm` in a separate Gradle process.

Note: use `gradlew.bat` instead of `./gradlew` on Windows operating system.

# Environment variables

The JVM backend requires these environment variables at startup. Missing any of them will cause the app to fail fast with a clear error message.

| Name                  | Purpose                                                                                                                                                                                                                    |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GMAIL_USERNAME`      | Gmail/Workspace address used as the SMTP login and `From` for outgoing emails (reservation confirmations, password resets, etc.).                                                                                          |
| `GMAIL_APP_PASSWORD`  | 16-character [Google App Password](https://myaccount.google.com/apppasswords) for the above account. Requires 2-Step Verification to be enabled on the Google account. Plain account passwords are rejected by Gmail SMTP. |
| `APP_BASE_URL`        | Public URL of the frontend, no trailing slash (e.g. `https://rezervace.example.cz`). Used to build the password-reset link in emails.                                                                                      |
| `JWT_CONFIG_SECRET`   | HMAC secret for signing JWT access tokens.                                                                                                                                                                                 |
| `JWT_CONFIG_ISSUER`   | `iss` claim written into issued JWTs.                                                                                                                                                                                      |
| `JWT_CONFIG_AUDIENCE` | `aud` claim written into issued JWTs.                                                                                                                                                                                      |
| `BANK_ACCOUNT_NUMBER` | Account number shown in payment instructions and encoded in the QR code. Falls back to a default if unset.                                                                                                                 |
| `FIO_TOKEN`           | API token for the FIO banking integration that pairs incoming payments to reservations.                                                                                                                                    |

For local development you can swap the `EmailService` binding in `DI.kt` back to `ConsoleEmailService` to avoid needing real Gmail credentials — emails will print to stdout instead.
