-- 1. Admin Dashboard Index
-- Query filters on `status` (equality) and `appointment_date` (range).
-- Placing `status` first ensures an efficient range scan.
CREATE INDEX idx_appt_status_date ON appointments (status, appointment_date);

-- 2. Doctor Schedule Index
-- Query filters on `doctor_id` (equality) and `status` (equality), then orders by `appointment_date DESC, start_time DESC`.
-- Adding this composite index eliminates the filesort entirely because the index provides the sort order.
CREATE INDEX idx_appt_doc_status_date_time ON appointments (doctor_id, status, appointment_date DESC, start_time DESC);

-- 3. Doctor Search Index
-- Query filters on `specialization` (equality).
-- Adding this allows the engine to jump directly to the right specialization before filtering by LIKE.
CREATE INDEX idx_doc_specialization ON doctors (specialization);
