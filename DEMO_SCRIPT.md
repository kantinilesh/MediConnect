# 🎙️ MediConnect - 5 Minute Demo Script

*This script is designed for a 3-5 minute screen recording. It is paced naturally and highlights the technical depth of the architecture (JWT statelessness, pessimistic locking for slots, composite indexing performance, bounded blocking queue for notifications) rather than just clicking buttons.*

---

### [0:00 - 0:30] Introduction & Architecture

**Visual:** Open your repository on GitHub or show the `docker-compose.yml` file in an IDE.
> "Hi, my name is Nilesh, and this is MediConnect. It's a highly concurrent, API-first healthcare appointment booking microservice. I built this deliberately without a frontend to focus purely on backend scalability, data integrity, and system architecture. It runs on Java 17, Spring Boot 3, and MySQL 8, all orchestrated via Docker Compose."

### [0:30 - 1:15] Auto-Seeding & Swagger UI

**Visual:** Switch to the browser, open `http://localhost:8080/swagger-ui.html` (or your EC2 URL). Scroll through the endpoints.
> "When the Docker container spins up, a custom `DemoSeeder` uses a `CommandLineRunner` to detect if the database is empty. If it is, it automatically populates the system with realistic doctors, patients, and bookable slots for the upcoming week. This allows anyone pulling the repo to immediately test the API without writing manual SQL.
>
> "As you can see, I've fully documented the API using OpenAPI and Swagger. I've also wired up JWT authentication directly into the Swagger UI so we can test the protected routes seamlessly."

### [1:15 - 2:00] Authentication (JWT)

**Visual:** Expand `POST /api/v1/auth/login`. Enter the demo credentials (`patient1@example.com` / `password123`) in the Request Body and click Execute.
> "Let's authenticate as a patient. Our Auth Controller issues a stateless JWT Access Token and a Refresh Token. The system uses Role-Based Access Control, meaning Patients, Doctors, and Admins have strictly isolated privileges. I'll copy the Access Token and authorize my Swagger session."

**Visual:** Scroll up, click the green `Authorize` button, paste the token, and click Login. Close the modal.

### [2:00 - 2:45] Doctor Discovery & Indexing

**Visual:** Expand `GET /api/v1/doctors/search`. Enter "Cardiology" in the specialization field and click Execute. Show the 200 OK response with doctor data.
> "Now let's search for a cardiologist. Under the hood, this triggers a paginated query against the MySQL database.
> 
> "One of the key engineering challenges I solved here was read performance. When I load-tested this endpoint with 50,000 patients and 200,000 appointments, latency spiked. I ran an `EXPLAIN` plan and designed a composite index covering `(specialization, id, first_name, last_name)`. By making it a covering index, the query avoids a costly table lookup, reducing response times by over 40%."

### [2:45 - 3:45] Concurrency & Pessimistic Locking

**Visual:** Expand `GET /api/v1/doctors/{doctorId}/slots`. Paste the Doctor ID from the previous response and today's date. Execute and copy a `slotId`.
> "Here are the doctor's available slots for today. Now, let's book one."

**Visual:** Expand `POST /api/v1/appointments`. Paste the `slotId`. Click Execute. Show the 201 Created response.
> "The appointment is booked successfully. But what happens if two patients try to book this exact same slot at the exact same millisecond?
>
> "In a healthcare app, double-booking is catastrophic. To solve this, I implemented database-level Pessimistic Write Locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`). The first thread to read the slot locks the row. The second thread is forced to wait, sees that the slot status changed to 'BOOKED', and safely aborts with a 409 Conflict. This guarantees zero data anomalies under high concurrency."

### [3:45 - 4:30] Asynchronous Notifications

**Visual:** If you have logs visible in a terminal, briefly pull them up to show the "Notification sent" log line. (Or just explain it while looking at the Swagger UI).
> "Finally, when that appointment was booked, an email notification was triggered. But we don't want the user's HTTP request waiting around for an external SMTP server to respond.
>
> "I designed an asynchronous Notification Dispatcher using Java's `ExecutorService` and a bounded `ArrayBlockingQueue`. The main thread publishes a lightweight Spring Event, and the dispatcher thread pool consumes it. By bounding the queue, we have built-in backpressure—if the email server goes down, we don't infinitely buffer events and crash the JVM with an OutOfMemoryError. We also implemented exponential backoff retries for transient failures."

### [4:30 - 5:00] Conclusion

**Visual:** Show the README or simply your face.
> "The entire system is containerized with a multi-stage Dockerfile that drops root privileges for security and caps the JVM memory footprint. It is currently deployed via GitHub Actions to an AWS EC2 instance behind an Nginx reverse proxy.
>
> "Thank you for watching the demo. The full source code, load testing scripts, and Postman collections are available on my GitHub."
