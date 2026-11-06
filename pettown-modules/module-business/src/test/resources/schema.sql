CREATE TABLE IF NOT EXISTS user_account (
    id VARCHAR(31) NOT NULL PRIMARY KEY,
    phone CHAR(11) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS pet (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    species VARCHAR(30) NOT NULL,
    owner_id VARCHAR(31) NOT NULL,
    breed VARCHAR(100),
    gender VARCHAR(16),
    birth_date DATE,
    weight_kg DECIMAL(5,2),
    sterilized BOOLEAN,
    avatar_url VARCHAR(2048),
    deleted INT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL,
    updated_time TIMESTAMP NOT NULL,
    INDEX idx_pet_owner_id (owner_id)
);
