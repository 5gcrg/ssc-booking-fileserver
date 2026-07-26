# SSC Event Booking System — File Server

Spring Boot service handling the SSC Event Booking System's file storage flow (MinIO-backed document and template storage). Runs Windows-native, no Docker — see `ssc-system/INSTRUCTIONS.md` for the full multi-service setup and start order (MySQL → MinIO → File Server → Main API → Frontend).

## What This Includes

- MinIO bucket initialization on application startup
- PDF document upload support
- DOCX template upload support
- Presigned download URL generation
- File validation and storage exception handling
- CORS setup for the Next.js frontend
- Actuator health checks

## Running

### Prerequisites

- Java 21 (Temurin) and Maven 3.9+
- MySQL 8.0 running (`ssc_booking` database) and MinIO running — see `ssc-system/INSTRUCTIONS.md` §1–3

### Build and start

```bash
mvn clean package -DskipTests
java -jar target/ssc-booking-0.0.1-SNAPSHOT.jar
```

Or, for hot-restart while coding:

```bash
mvn spring-boot:run
```

### Services

Spring Boot API:

```text
http://localhost:8080
```

MinIO S3 API:

```text
http://localhost:9000
```

MinIO Console:

```text
http://localhost:9001
Username: sscadmin
Password: sscpassword123
```

MySQL:

```text
Host: localhost
Port: 3306
Database: ssc_booking
Username: sscuser
Password: sscpassword
```

### Test The API Health Check

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Buckets

On startup, the application checks MinIO and creates these buckets if needed:

```text
ssc-documents
ssc-templates
ssc-projects
```

`ssc-projects` is reserved for external integration clients (see **Project Storage API**
below) and is deliberately separate from the document/template buckets.

## Configuration

Local configuration lives in:

```text
src/main/resources/application.yml
```

Production overrides live in:

```text
src/main/resources/application-prod.yml
```

Production values should come from environment variables:

```text
MINIO_URL=
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=
DB_URL=
DB_USERNAME=
DB_PASSWORD=
FRONTEND_URL=
```

## Object Key Format

Uploaded student PDF documents are stored in the `ssc-documents` bucket:

```text
submissions/{submission_id}/{document_id}/v{version_number}/{filename}
```

Example:

```text
submissions/test-123/doc-456/v1/budget_proposal.pdf
```

Uploaded DOCX templates are stored in the `ssc-templates` bucket:

```text
templates/{template_type}/v{version_number}/{filename}
```

Example:

```text
templates/activity_proposal/v1/ActivityProposal.docx
```

Store these object keys in the database, not full MinIO URLs. Download URLs are generated on demand and expire after one hour by default.

## API Endpoints

Base path:

```text
/api/v1/files
```

Upload a PDF document:

```text
POST /api/v1/files/documents/upload
Content-Type: multipart/form-data

file=<pdf>
submissionId=test-123
documentId=doc-456
versionNumber=1
```

Upload a DOCX template:

```text
POST /api/v1/files/templates/upload
Content-Type: multipart/form-data

file=<docx>
templateType=activity_proposal
versionNumber=1
```

Generate a document download URL:

```text
GET /api/v1/files/documents/url?objectKey=submissions/test-123/doc-456/v1/budget_proposal.pdf
```

Generate a template download URL:

```text
GET /api/v1/files/templates/url?objectKey=templates/activity_proposal/v1/ActivityProposal.docx
```

The query-param download endpoints are preferred because object keys include `/` characters.

## Upload Responses

Successful uploads return:

```json
{
  "objectKey": "submissions/test-123/doc-456/v1/budget_proposal.pdf",
  "fileName": "budget_proposal.pdf",
  "fileSizeBytes": 245678,
  "uploadedAt": "2026-07-06T03:30:00Z",
  "message": "File uploaded successfully."
}
```

Presigned URL responses return:

```json
{
  "presignedUrl": "http://localhost:9000/ssc-documents/...",
  "expiresAt": "2026-07-06T04:30:00Z",
  "expiresInSeconds": 3600
}
```

## File Validation

Document uploads must be:

- PDF files
- `application/pdf`
- No larger than 10 MB

Template uploads must be:

- `.docx` files
- No larger than 10 MB

Filenames are sanitized before storage. Spaces are converted to underscores, path traversal characters are removed, and names are capped at 200 characters.

## Project Storage API (external integrations, X-API-Key)

Machine-to-machine file storage for external services (e.g. other teams' apps). Each client
gets an API key and a dedicated folder; **every operation is confined to
`projects/<project-folder>/` in the `ssc-projects` bucket** — any path that tries to escape it
(traversal, absolute paths, backslashes, `%`, control characters) is rejected with
`403 PATH_VIOLATION`. Keys in requests and responses are always relative to the client's
folder; callers never see or supply the folder itself.

Clients are configured via `app.integration.clients` (env vars `PROJECT_CLIENT_n_NAME` /
`_KEY` / `_FOLDER`). The registry is validated at startup (fail-fast on missing/unsafe
folders or duplicates); only client names are logged, never keys. Requests are rate-limited
per client (`app.integration.rate-limit`, default 60/min, `429` + `Retry-After`).

Auth is the `X-API-Key` header. **No CORS entry exists for this path by design** — it is
server-to-server only; browsers cannot call it cross-origin.

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/integration/files` | Upload/overwrite (multipart: `file`, `path=builds/v1/app.zip`) → `201` with `overwritten` flag |
| GET | `/api/v1/integration/files/url?path=…` | Presigned download URL (`404 FILE_NOT_FOUND` if missing) |
| GET | `/api/v1/integration/files?prefix=…&maxKeys=…&startAfter=…` | List (relative keys, `truncated` + `nextStartAfter` pagination) |
| DELETE | `/api/v1/integration/files?path=…` | Delete → `204` always (idempotent, including for missing keys) |

Paths always travel as query/form params (never path variables) so slashes survive routing.

Upload rules: max size `minio.max-project-file-size-mb` (default 25 MB, must stay below the
50 MB servlet multipart ceiling); extension denylist `minio.blocked-extensions` (executables —
files are only ever served via presigned MinIO URLs, never from the app origin); the declared
content type is stored but never trusted for validation.

Errors: `401 API_KEY_MISSING`/`NOT_CONFIGURED`, `403 API_KEY_INVALID`/`PATH_VIOLATION`,
`400 FILE_VALIDATION_ERROR`, `404 FILE_NOT_FOUND`, `429 RATE_LIMITED`.

> **Presigned URL reachability:** download URLs embed `minio.public-url` and the signature
> binds to that exact host. External callers must be able to reach that host from *their*
> network — set `MINIO_PUBLIC_URL` to an address reachable by the integration client, not
> `localhost`.

## Project Structure

```text
src/main/java/com/ssc/booking/
  config/
    AppProperties.java
    CorsConfig.java
    MinioBucketInitializer.java
    MinioConfig.java
    MinioProperties.java
    SecurityConfig.java
  controller/
    FileController.java
    IntegrationFileController.java
  dto/
    ErrorResponse.java
    FileUploadResponse.java
    PresignedUrlResponse.java
    ProjectFileInfo.java
    ProjectFileListResponse.java
    ProjectFileUploadResponse.java
  exception/
    FilePathViolationException.java
    FileStorageException.java
    FileValidationException.java
    GlobalExceptionHandler.java
    ProjectFileNotFoundException.java
  security/
    IntegrationApiKeyFilter.java
    IntegrationClientRegistry.java
    JwtAuthenticationFilter.java
    JwtService.java
  service/
    FileStorageService.java
    ObjectKeySanitizer.java
    ProjectFileStorageService.java
  SscBookingApplication.java
src/main/resources/
  application.yml
  application-prod.yml
```

## Notes For Next Integration Steps

- Add the actual authentication/JWT layer before using the role-protected endpoints in a real environment.
- Connect upload success to the future `DocumentVersion` persistence flow.
- Keep stored file paths as MinIO object keys.
- Generate temporary presigned URLs only when users need to view or download files.
