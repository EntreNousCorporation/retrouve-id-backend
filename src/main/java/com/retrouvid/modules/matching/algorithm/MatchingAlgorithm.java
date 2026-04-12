package com.retrouvid.modules.matching.algorithm;

import com.retrouvid.modules.declaration.entity.Declaration;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class MatchingAlgorithm {

    public static final double NOTIFY_THRESHOLD = 60.0;
    public static final double STRONG_THRESHOLD = 85.0;

    public double score(Declaration perte, Declaration decouverte) {
        if (perte.getDocumentType() != decouverte.getDocumentType()) return 0.0;

        double docTypeScore = 100.0;
        double nameScore = nameSimilarity(perte.getOwnerName(), decouverte.getOwnerName()) * 100.0;
        double geoScore = geoProximityScore(perte, decouverte);
        double timeScore = temporalProximityScore(perte.getDateEvent(), decouverte.getDateEvent());
        double numberScore = partialNumberMatch(perte.getDocumentNumberPartial(), decouverte.getDocumentNumberPartial());

        return 0.30 * docTypeScore
             + 0.30 * nameScore
             + 0.20 * geoScore
             + 0.10 * timeScore
             + 0.10 * numberScore;
    }

    double nameSimilarity(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return 0.0;
        String na = normalize(a);
        String nb = normalize(b);
        int dist = levenshtein(na, nb);
        int max = Math.max(na.length(), nb.length());
        if (max == 0) return 0.0;
        return 1.0 - ((double) dist / max);
    }

    double geoProximityScore(Declaration a, Declaration b) {
        if (a.getLatitude() == null || a.getLongitude() == null
                || b.getLatitude() == null || b.getLongitude() == null) {
            if (a.getCity() != null && b.getCity() != null
                    && a.getCity().equalsIgnoreCase(b.getCity())) return 70.0;
            return 0.0;
        }
        double km = haversineKm(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        if (km <= 1) return 100.0;
        if (km <= 5) return 85.0;
        if (km <= 10) return 70.0;
        if (km <= 25) return 40.0;
        if (km <= 50) return 20.0;
        return 0.0;
    }

    double temporalProximityScore(LocalDate perte, LocalDate decouverte) {
        if (perte == null || decouverte == null) return 50.0;
        long days = Math.abs(ChronoUnit.DAYS.between(perte, decouverte));
        if (days <= 1) return 100.0;
        if (days <= 3) return 85.0;
        if (days <= 7) return 70.0;
        if (days <= 30) return 40.0;
        return 10.0;
    }

    double partialNumberMatch(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return 0.0;
        String na = a.replaceAll("\\s+", "").toUpperCase();
        String nb = b.replaceAll("\\s+", "").toUpperCase();
        if (na.equals(nb)) return 100.0;
        if (na.contains(nb) || nb.contains(na)) return 80.0;
        int common = longestCommonSubstring(na, nb);
        int max = Math.max(na.length(), nb.length());
        return max == 0 ? 0 : (100.0 * common / max);
    }

    private String normalize(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
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

    private int longestCommonSubstring(String a, String b) {
        int max = 0;
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    max = Math.max(max, dp[i][j]);
                }
            }
        }
        return max;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }
}
