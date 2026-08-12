# MediConnect

Healthcare appointment booking microservice built with Spring Boot 3, Java 17, Spring Security (JWT), and MySQL 8.

## Getting Started

Follow these steps to run the application locally from a clean clone using Docker.

### 1. Configure Environment Variables

The application relies on a `.env` file for secrets and database configuration so that they are never committed to version control.

Copy the example file to create your local `.env`:
```bash
cp .env.example .env
```

*Note: The `.env.example` file contains safe default values for local development. You do not need to change them unless you are deploying to production.*

### 2. Build and Run via Docker Compose

We use a multi-stage `Dockerfile` that builds the application using Maven and then packages it into a slim JRE runtime image.

To build the images and start the services (Application, MySQL, and Adminer):
```bash
docker-compose up --build -d
```

### 3. Verify the Services

- **Application Health:** The app waits for MySQL to be healthy before starting. You can check the logs to see it boot up:
  ```bash
  docker-compose logs -f app
  ```
  The API will be available at: http://localhost:8080
- **Database GUI (Adminer):** Available at http://localhost:8081. Log in with:
  - **System:** MySQL
  - **Server:** `mysql`
  - **Username:** `mediconnect`
  - **Password:** `mediconnect123`
  - **Database:** `mediconnect_db`

### 4. Stopping the Application

To stop the containers while preserving your database data (stored in a named volume):
```bash
docker-compose down
```

To stop the containers and **wipe all database data**:
```bash
docker-compose down -v
```
