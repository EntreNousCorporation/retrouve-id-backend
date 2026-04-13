package com.retrouvid.modules.legal;

import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.exception.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Sert les textes légaux (CGU, politique de confidentialité) depuis
 * resources/legal/*.md. Accessible sans authentification pour que l'Appli
 * mobile puisse afficher les CGU avant inscription.
 */
@RestController
@RequestMapping("/api/v1/legal")
public class LegalController {

    private static final Set<String> ALLOWED = Set.of("cgu", "privacy");

    public record LegalDocument(String slug, String content) {}

    @GetMapping("/{slug}")
    public ApiResponse<LegalDocument> get(@PathVariable String slug) {
        if (!ALLOWED.contains(slug)) {
            throw ApiException.notFound("Document inconnu");
        }
        try (var in = new ClassPathResource("legal/" + slug + ".md").getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return ApiResponse.ok(new LegalDocument(slug, content));
        } catch (IOException e) {
            throw ApiException.badRequest("Impossible de lire le document");
        }
    }
}
