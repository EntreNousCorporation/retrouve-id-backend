-- Phase C — Gamification : points, badges, leaderboard, remerciements

-- Stats cumulées du trouveur. 1:1 avec users. Créé paresseusement à la 1re
-- restitution. `public_profile`/`public_alias` sont opt-in pour le leaderboard.
CREATE TABLE user_stats (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    total_points INT NOT NULL DEFAULT 0,
    current_month_points INT NOT NULL DEFAULT 0,
    month_reset_at TIMESTAMP,
    restitutions_completed INT NOT NULL DEFAULT 0,
    public_profile BOOLEAN NOT NULL DEFAULT FALSE,
    public_alias VARCHAR(80),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_stats_total ON user_stats(total_points);
CREATE INDEX idx_user_stats_monthly ON user_stats(current_month_points);

-- Catalogue de badges. Seedé au démarrage via BadgeSeeder (idempotent).
CREATE TABLE badges (
    id UUID PRIMARY KEY,
    code VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    icon VARCHAR(16),
    threshold INT
);

-- Badges obtenus par un utilisateur. Unicité par (user, badge) sauf pour
-- les collections (ECLAIREUR par ville) où on stocke la métadonnée dans
-- metadata_key pour permettre plusieurs obtentions du même badge.
CREATE TABLE user_badges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    badge_id UUID NOT NULL REFERENCES badges(id) ON DELETE CASCADE,
    metadata_key VARCHAR(120),
    earned_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_user_badges ON user_badges(user_id, badge_id, metadata_key);
CREATE INDEX idx_user_badges_user ON user_badges(user_id);

-- Messages de remerciement post-pickup (1 par match max, du perdeur au trouveur).
CREATE TABLE thanks_messages (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL UNIQUE REFERENCES matches(id) ON DELETE CASCADE,
    from_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content VARCHAR(140) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_thanks_to_user ON thanks_messages(to_user_id);
