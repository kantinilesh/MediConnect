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

## Demo This Project

This API is designed to be fully demoable without a frontend. When you run `docker-compose up`, a `DemoSeeder` will automatically populate the database with doctors, patients, and bookable slots if the database is empty.

### Option A: Swagger UI
The easiest way to explore the API is via the auto-generated OpenAPI documentation.
- **Local URL:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **EC2 URL:** `http://YOUR_EC2_IP:8080/swagger-ui.html` (or `https://yourdomain.com/swagger-ui.html` if configured)

**How to test protected endpoints:**
1. Call `POST /api/v1/auth/login` using the demo credentials:
   - **Email:** `patient1@example.com`
   - **Password:** `password123`
2. Copy the `accessToken` from the response.
3. Click the green **Authorize** button at the top of the Swagger page, paste the token, and click Login.
4. You can now execute any protected endpoint (like `GET /api/v1/appointments`).

### Option B: Postman Collection
A full Postman collection and environment are included in the repository.
1. Import the `/postman/MediConnect-API-V1.postman_collection.json` file into Postman.
2. Import the `/postman/MediConnect-Local.postman_environment.json` file.
3. Select the "MediConnect Local" environment in the top right corner.
4. Run the requests in order. The collection uses test scripts to automatically extract IDs and Tokens and pass them to the next request!
