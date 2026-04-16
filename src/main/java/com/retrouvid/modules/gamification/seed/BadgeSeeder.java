package com.retrouvid.modules.gamification.seed;

import com.retrouvid.modules.gamification.entity.Badge;
import com.retrouvid.modules.gamification.repository.BadgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Idempotent : crée les badges du catalogue au démarrage s'ils n'existent
 *  pas. Les badges existants ne sont pas écrasés (permet l'évolution des
 *  libellés par migration plus tard si besoin). */
@Slf4j
@Component
@RequiredArgsConstructor
public class BadgeSeeder implements ApplicationRunner {

    public static final String FIRST_RESTITUTION = "FIRST_RESTITUTION";
    public static final String CITIZEN_3 = "CITIZEN_3";
    public static final String CITIZEN_10 = "CITIZEN_10";
    public static final String CITIZEN_25 = "CITIZEN_25";
    public static final String CITIZEN_50 = "CITIZEN_50";
    public static final String CITIZEN_100 = "CITIZEN_100";
    public static final String FAST_DROP = "FAST_DROP";
    public static final String ECLAIREUR = "ECLAIREUR";
    public static final String SECOURISTE = "SECOURISTE";

    private final BadgeRepository badgeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Badge> catalog = List.of(
                badge(FIRST_RESTITUTION, "Premier geste",
                        "Première restitution complétée. Bienvenue parmi les citoyens RetrouvID !",
                        "🌱", 1),
                badge(CITIZEN_3, "Citoyen engagé — 3",
                        "3 pièces restituées.", "🤝", 3),
                badge(CITIZEN_10, "Citoyen engagé — 10",
                        "10 pièces restituées.", "🤝", 10),
                badge(CITIZEN_25, "Citoyen engagé — 25",
                        "25 pièces restituées.", "🤝", 25),
                badge(CITIZEN_50, "Citoyen engagé — 50",
                        "50 pièces restituées.", "🤝", 50),
                badge(CITIZEN_100, "Citoyen engagé — 100",
                        "100 pièces restituées. Vous êtes une légende.", "🤝", 100),
                badge(FAST_DROP, "Éclair",
                        "Pièce déposée en moins de 24 h après découverte.", "⚡", null),
                badge(ECLAIREUR, "Éclaireur",
                        "Première restitution dans une commune.", "🏆", null),
                badge(SECOURISTE, "Secouriste",
                        "Restitution d'un passeport.", "🎖️", null)
        );
        for (Badge b : catalog) {
            badgeRepository.findByCode(b.getCode()).orElseGet(() -> {
                log.info("Seed badge {}", b.getCode());
                return badgeRepository.save(b);
            });
        }
    }

    private static Badge badge(String code, String name, String desc, String icon, Integer threshold) {
        return Badge.builder()
                .code(code)
                .name(name)
                .description(desc)
                .icon(icon)
                .threshold(threshold)
                .build();
    }
}
