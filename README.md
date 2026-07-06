# SSC Event Booking System Backend

Spring Boot backend foundation for the SSC Event Booking System file storage flow. The full local backend stack runs with Docker Compose: Spring Boot, MinIO, and MySQL.

## What This Includes

- Spring Boot 3 backend container
- MinIO and MySQL local services with Docker Compose
- One-command full stack startup
- MinIO bucket initialization on application startup
- PDF document upload support
- DOCX template upload support
- Presigned download URL generation
- File validation and storage exception handling
- CORS setup for the Next.js frontend
- Actuator health checks for Docker

This project does not yet include authentication implementation, submission workflows, frontend changes, or `DocumentVersion` persistence. The file API is ready for those layers to plug in later.

## Running The Full Stack

### Prerequisites

- Docker Desktop installed and running

No local Java or Maven install is required for the Docker path. The Spring Boot app is built inside the Docker image.

### Start Everything

```bash
docker compose up -d
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

### View Logs

```bash
docker compose logs -f
```

Spring Boot only:

```bash
docker compose logs -f springboot
```

MinIO only:

```bash
docker compose logs -f minio
```

MySQL only:

```bash
docker compose logs -f mysql
```

### Stop Everything

```bash
docker compose down
```

### Fresh Start

This stops all containers and deletes local MinIO/MySQL data volumes:

```bash
docker compose down -v
```

### Rebuild Spring Boot After Code Changes

```bash
docker compose up -d --build springboot
```

### Test The API Health Check

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Local Spring Boot Development Option

If you want faster app restarts while coding, run only MinIO and MySQL in Docker:

```bash
docker compose up -d minio mysql
```

Then run Spring Boot locally:

```bash
mvn spring-boot:run
```

If a Maven wrapper is added later, use:

```bash
./mvnw spring-boot:run
```

This local path requires Java 21 and Maven 3.8+ installed on your machine.

## Docker Networking Notes

Inside Docker, services talk to each other by service name:

```text
Spring Boot -> MySQL: mysql:3306
Spring Boot -> MinIO: http://minio:9000
```

From your Mac browser or terminal, use localhost because ports are mapped to the host:

```text
Spring Boot API: http://localhost:8080
MinIO S3 API: http://localhost:9000
MinIO Console: http://localhost:9001
MySQL: localhost:3306
```

## Buckets

On startup, the application checks MinIO and creates these buckets if needed:

```text
ssc-documents
ssc-templates
```

## Configuration

Local configuration lives in:

```text
src/main/resources/application.yml
```

Docker configuration lives in:

```text
src/main/resources/application-docker.yml
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

## Project Structure

```text
Dockerfile
.dockerignore
docker-compose.yml
docker-compose.dev.yml
src/main/java/com/ssc/booking/
  config/
    CorsConfig.java
    MinioBucketInitializer.java
    MinioConfig.java
    MinioProperties.java
    SecurityConfig.java
  controller/
    FileController.java
  dto/
    ErrorResponse.java
    FileUploadResponse.java
    PresignedUrlResponse.java
  exception/
    FileStorageException.java
    FileValidationException.java
    GlobalExceptionHandler.java
  service/
    FileStorageService.java
  SscBookingApplication.java
src/main/resources/
  application.yml
  application-docker.yml
  application-prod.yml
```

## Notes For Next Integration Steps

- Add the actual authentication/JWT layer before using the role-protected endpoints in a real environment.
- Connect upload success to the future `DocumentVersion` persistence flow.
- Keep stored file paths as MinIO object keys.
- Generate temporary presigned URLs only when users need to view or download files.
