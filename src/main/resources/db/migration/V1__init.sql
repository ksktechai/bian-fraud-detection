-- BIAN Fraud Detection schema
-- Flyway owns the schema; quarkus.hibernate-orm.database.generation=none.

-- Control record for the Fraud Detection service domain.
CREATE TABLE fraud_evaluation (
    reference_id     uuid PRIMARY KEY,
    transaction_id   varchar(128),
    amount           numeric(18, 2),
    type             varchar(32),
    name_orig        varchar(64),
    name_dest        varchar(64),
    oldbalance_org   numeric(18, 2),
    newbalance_orig  numeric(18, 2),
    risk_score       integer,
    decision         varchar(16),
    ai_reasoning     text,
    case_narrative   text,
    status           varchar(16) NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_evaluation_status ON fraud_evaluation (status);

-- Staging table for raw PaySim rows (loaded by DataLoader from ./data/*.csv).
CREATE TABLE fraud_txn_sample (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    step              integer,
    type              varchar(32),
    amount            numeric(18, 2),
    name_orig         varchar(64),
    oldbalance_org    numeric(18, 2),
    newbalance_orig   numeric(18, 2),
    name_dest         varchar(64),
    oldbalance_dest   numeric(18, 2),
    newbalance_dest   numeric(18, 2),
    is_fraud          integer,
    is_flagged_fraud  integer
);

CREATE INDEX idx_fraud_txn_sample_type ON fraud_txn_sample (type);
