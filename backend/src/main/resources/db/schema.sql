CREATE TABLE IF NOT EXISTS app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(80) NOT NULL,
    email VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_user_email UNIQUE (email)
);
CREATE TABLE IF NOT EXISTS equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_tag VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    location VARCHAR(100) NOT NULL,
    maintenance_key VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_equipment_asset UNIQUE (asset_tag)
);
CREATE TABLE IF NOT EXISTS reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    review_note VARCHAR(500),
    reviewed_by_id BIGINT,
    reviewed_at TIMESTAMP(6),
    checked_out_at TIMESTAMP(6),
    returned_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reservation_equipment FOREIGN KEY (equipment_id) REFERENCES equipment(id),
    CONSTRAINT fk_reservation_requester FOREIGN KEY (requester_id) REFERENCES app_users(id),
    CONSTRAINT fk_reservation_reviewer FOREIGN KEY (reviewed_by_id) REFERENCES app_users(id),
    CONSTRAINT uq_reservation_request UNIQUE (requester_id, request_key),
    CONSTRAINT ck_reservation_dates CHECK (end_date >= start_date)
);
CREATE INDEX IF NOT EXISTS ix_reservation_equipment_dates ON reservations(equipment_id, status, start_date, end_date);
CREATE INDEX IF NOT EXISTS ix_reservation_requester ON reservations(requester_id, created_at);
CREATE TABLE IF NOT EXISTS audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    details VARCHAR(600) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES app_users(id)
);
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    dedupe_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    read_at TIMESTAMP(6),
    CONSTRAINT fk_notification_user FOREIGN KEY (recipient_id) REFERENCES app_users(id),
    CONSTRAINT fk_notification_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT uq_notification_dedupe UNIQUE (dedupe_key)
);
