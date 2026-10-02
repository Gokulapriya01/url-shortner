-- URL Shortener Database Schema
-- Version: 1.0.0

-- URLs table
CREATE TABLE urls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    short_code VARCHAR(10) UNIQUE NOT NULL,
    original_url TEXT NOT NULL,
    custom_alias VARCHAR(50) UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    click_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Click events table (raw analytics)
CREATE TABLE click_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url_id UUID NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    referrer TEXT,
    user_agent TEXT,
    ip_hash VARCHAR(64) NOT NULL,
    country VARCHAR(2),
    city VARCHAR(100)
);

-- Click aggregates table (pre-aggregated analytics)
CREATE TABLE click_aggregates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url_id UUID NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    period_type VARCHAR(10) NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    click_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_click_aggregates UNIQUE (url_id, period_type, period_start)
);

-- Orchestration tasks table
CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID,
    definition_id VARCHAR(100),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    phase INTEGER NOT NULL,
    order_index INTEGER NOT NULL,
    dependencies TEXT[],
    ac_ids TEXT[],
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    error TEXT,
    output JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Task transitions (audit log)
CREATE TABLE task_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    actor VARCHAR(20) NOT NULL DEFAULT 'system',
    reason TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Gates table
CREATE TABLE gates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID,
    definition_id VARCHAR(100),
    name VARCHAR(200) NOT NULL,
    phase INTEGER NOT NULL,
    requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP WITH TIME ZONE,
    rejected_reason TEXT,
    blocked_tasks TEXT[],
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Orchestration sessions table
CREATE TABLE orchestration_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    current_phase INTEGER NOT NULL DEFAULT 1,
    current_gate_id UUID,
    context JSONB NOT NULL DEFAULT '{}',
    metrics JSONB NOT NULL DEFAULT '{}',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for URLs
CREATE INDEX idx_urls_short_code ON urls(short_code);
CREATE INDEX idx_urls_custom_alias ON urls(custom_alias);
CREATE INDEX idx_urls_created_at ON urls(created_at);
CREATE INDEX idx_urls_expires_at ON urls(expires_at);

-- Indexes for click events
CREATE INDEX idx_click_events_url_id ON click_events(url_id);
CREATE INDEX idx_click_events_timestamp ON click_events(timestamp);
CREATE INDEX idx_click_events_url_timestamp ON click_events(url_id, timestamp);

-- Indexes for click aggregates
CREATE INDEX idx_click_aggregates_url_period ON click_aggregates(url_id, period_type);
CREATE INDEX idx_click_aggregates_period_start ON click_aggregates(period_start);

-- Indexes for tasks
CREATE INDEX idx_tasks_session_id ON tasks(session_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_phase ON tasks(phase);

-- Indexes for gates
CREATE INDEX idx_gates_session_id ON gates(session_id);
CREATE INDEX idx_gates_phase ON gates(phase);

-- Indexes for sessions
CREATE INDEX idx_sessions_status ON orchestration_sessions(status);

-- Function to auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
CREATE TRIGGER update_urls_updated_at
    BEFORE UPDATE ON urls
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
