-- Phase A — Workflow de restitution : hashs de vérification, code de retrait,
-- lien relais, RESTITUTED, candidatures d'agents relais.

-- declarations : hashs pour cross-check sécurisé (SHA-256 + sel) + détail discriminant
ALTER TABLE declarations ADD COLUMN document_number_hash VARCHAR(128);
ALTER TABLE declarations ADD COLUMN dob_hash VARCHAR(128);
ALTER TABLE declarations ADD COLUMN discriminant_hint VARCHAR(160);

CREATE INDEX idx_declarations_doc_hash ON declarations(document_number_hash);

-- matches : extensions pour le workflow de remise via relais
ALTER TABLE matches ADD COLUMN verification_score DOUBLE PRECISION;
ALTER TABLE matches ADD COLUMN relay_point_id UUID REFERENCES relay_points(id);
ALTER TABLE matches ADD COLUMN handover_deadline TIMESTAMP;
ALTER TABLE matches ADD COLUMN code_hash VARCHAR(128);
ALTER TABLE matches ADD COLUMN code_expires_at TIMESTAMP;
ALTER TABLE matches ADD COLUMN dropped_at TIMESTAMP;
ALTER TABLE matches ADD COLUMN picked_up_at TIMESTAMP;
ALTER TABLE matches ADD COLUMN failed_pickup_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE matches ADD COLUMN pickup_locked_until TIMESTAMP;
ALTER TABLE matches ADD COLUMN agent_user_id UUID REFERENCES users(id);

CREATE INDEX idx_matches_relay ON matches(relay_point_id);
CREATE INDEX idx_matches_handover_deadline ON matches(handover_deadline);
CREATE INDEX idx_matches_code_expires ON matches(code_expires_at);

-- relay_points : lier à l'utilisateur agent propriétaire (nullable : relais créés
-- par admin avant Phase A n'ont pas d'agent, on garde la rétrocompat)
ALTER TABLE relay_points ADD COLUMN user_id UUID UNIQUE REFERENCES users(id);

-- relay_applications : candidatures d'utilisateurs pour devenir point relais
CREATE TABLE relay_applications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_name VARCHAR(200) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    address TEXT NOT NULL,
    city VARCHAR(100) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    phone VARCHAR(20),
    opening_hours VARCHAR(200),
    storefront_photo_url TEXT,
    justification_document_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    relay_point_id UUID REFERENCES relay_points(id) ON DELETE SET NULL,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_relay_applications_user ON relay_applications(user_id);
CREATE INDEX idx_relay_applications_status ON relay_applications(status);
-- Unicité "un seul dossier PENDING par user" : enforced côté service (partial
-- unique indexes non portables sur H2 utilisé en tests)
