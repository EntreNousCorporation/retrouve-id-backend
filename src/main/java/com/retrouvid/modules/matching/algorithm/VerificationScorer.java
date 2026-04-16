package com.retrouvid.modules.matching.algorithm;

import com.retrouvid.modules.declaration.entity.Declaration;
import org.springframework.stereotype.Component;

import java.text.Normalizer;

@Component
public class VerificationScorer {

    public static final double MATCH_THRESHOLD = 70.0;

    public double score(Declaration perte, Declaration decouverte) {
        double total = 0.0;

        if (hashEquals(perte.getDocumentNumberHash(), decouverte.getDocumentNumberHash())) {
            total += 60.0;
        }
        if (hashEquals(perte.getDobHash(), decouverte.getDobHash())) {
            total += 20.0;
        }
        total += 10.0 * fuzzyNameSimilarity(perte.getOwnerName(), decouverte.getOwnerName());
        total += 10.0 * fuzzyNameSimilarity(perte.getDiscriminantHint(), decouverte.getDiscriminantHint());

        return total;
    }

    private boolean hashEquals(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return false;
        return a.equals(b);
    }

    double fuzzyNameSimilarity(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return 0.0;
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return 0.0;
        int dist = levenshtein(na, nb);
        int max = Math.max(na.length(), nb.length());
        double sim = 1.0 - ((double) dist / max);
        return sim >= 0.9 || sim >= 0.7 ? sim : 0.0;
    }

    private String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }
}
