package com.mediconnect.integration;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("local")
public class BenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runBenchmark() {
        // Query 1: Admin Dashboard - All upcoming appointments by status (No index on date+status)
        String q1 = "SELECT count(*) FROM appointments WHERE status = 'CONFIRMED' AND appointment_date BETWEEN '2026-08-01' AND '2026-09-30'";

        // Query 2: Doctor Schedule - Needs filesort
        String q2 = "SELECT * FROM appointments WHERE doctor_id = (SELECT user_id FROM doctors LIMIT 1) AND status = 'CONFIRMED' ORDER BY appointment_date DESC, start_time DESC";
        
        // Query 3: Doctor Search with partial match (No index)
        String q3 = "SELECT * FROM doctors WHERE specialization = 'CARDIOLOGY' AND clinic_name LIKE '%Clinic 1%' ORDER BY rating DESC";

        runExplain("Admin Dashboard", q1);
        runBenchmarkQuery("Admin Dashboard", q1, 50);

        runExplain("Doctor Schedule", q2);
        runBenchmarkQuery("Doctor Schedule", q2, 100);
        
        runExplain("Doctor Search", q3);
        runBenchmarkQuery("Doctor Search", q3, 100);
    }

    private void runExplain(String name, String query) {
        log.info("EXPLAIN for: {}", name);
        List<Map<String, Object>> plan = jdbcTemplate.queryForList("EXPLAIN " + query);
        for (Map<String, Object> row : plan) {
            log.info("table: {}, type: {}, possible_keys: {}, key: {}, rows: {}, Extra: {}", row.get("table"), row.get("type"), row.get("possible_keys"), row.get("key"), row.get("rows"), row.get("Extra"));
        }
    }

    private void runBenchmarkQuery(String name, String query, int iterations) {
        // Warmup
        for (int i = 0; i < 5; i++) jdbcTemplate.queryForList(query);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) jdbcTemplate.queryForList(query);
        long end = System.nanoTime();
        double avgMs = (end - start) / 1_000_000.0 / iterations;
        System.out.printf("BENCHMARK_RESULT|%s|%.2f\n", name, avgMs);
    }
}
