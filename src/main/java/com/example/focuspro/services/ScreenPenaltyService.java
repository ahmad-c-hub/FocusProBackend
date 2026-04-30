package com.example.focuspro.services;

import com.example.focuspro.entities.DailyAppUsage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Computes the daily screen-time penalty deducted from a user's daily score.
 *
 * Tier 1 — high distraction (TikTok, Instagram, Snapchat, Facebook, YouTube)
 *   • Free threshold : 15 min/day
 *   • Block size     : every 25 min of excess = 1 block
 *   • Cost per block : 2 points
 *
 * Tier 2 — medium distraction (WhatsApp, Telegram, Discord, Netflix, Spotify, Reddit, Twitter/X)
 *   • Free threshold : 30 min/day
 *   • Block size     : every 30 min of excess = 1 block
 *   • Cost per block : 1 point
 *
 * Total penalty is capped at 10 points per day.
 */
@Service
public class ScreenPenaltyService {

    // ── App → tier mapping ────────────────────────────────────────────────────

    private static final Map<String, Integer> TIER_MAP = Map.ofEntries(
            // Tier 1 — high distraction
            Map.entry("com.zhiliaoapp.musically",    1),  // TikTok (global)
            Map.entry("com.ss.android.ugc.trill",    1),  // TikTok (some regions)
            Map.entry("com.instagram.android",        1),  // Instagram
            Map.entry("com.snapchat.android",         1),  // Snapchat
            Map.entry("com.facebook.katana",          1),  // Facebook
            Map.entry("com.google.android.youtube",   1),  // YouTube

            // Tier 2 — medium distraction
            Map.entry("com.whatsapp",                 2),  // WhatsApp
            Map.entry("org.telegram.messenger",       2),  // Telegram
            Map.entry("com.discord",                  2),  // Discord
            Map.entry("com.netflix.mediaclient",      2),  // Netflix
            Map.entry("com.spotify.music",            2),  // Spotify
            Map.entry("com.reddit.frontpage",         2),  // Reddit
            Map.entry("com.twitter.android",          2)   // Twitter / X
    );

    // ── Tier constants ────────────────────────────────────────────────────────

    private static final int T1_THRESHOLD = 15;   // free minutes
    private static final int T1_BLOCK     = 25;   // minutes per penalty block
    private static final int T1_WEIGHT    = 2;    // points per block

    private static final int T2_THRESHOLD = 30;
    private static final int T2_BLOCK     = 30;
    private static final int T2_WEIGHT    = 1;

    private static final int MAX_PENALTY  = 10;   // daily cap

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Computes the total screen-time penalty (0–10) for a given day's usage list.
     *
     * @param usageList  today's {@link DailyAppUsage} rows for one user
     * @return           integer penalty to subtract from the daily score
     */
    /**
     * Sums the total minutes spent on ALL tracked distracting apps for the given day.
     * Used by UserStatsService to surface the "distracting minutes" stat.
     *
     * @param usageList  today's {@link DailyAppUsage} rows for one user
     * @return           total minutes on distracting apps (tier 1 + tier 2), no cap
     */
    public int totalDistractingMinutes(List<DailyAppUsage> usageList) {
        int total = 0;
        for (DailyAppUsage usage : usageList) {
            if (TIER_MAP.containsKey(usage.getPackageName())) {
                total += usage.getTotalMinutes();
            }
        }
        return total;
    }

    public int computePenalty(List<DailyAppUsage> usageList) {
        int total = 0;
        for (DailyAppUsage usage : usageList) {
            Integer tier = TIER_MAP.get(usage.getPackageName());
            if (tier == null) continue;

            int minutes = usage.getTotalMinutes();
            int penalty;

            if (tier == 1) {
                int excess = Math.max(0, minutes - T1_THRESHOLD);
                penalty = (excess / T1_BLOCK) * T1_WEIGHT;
            } else {
                int excess = Math.max(0, minutes - T2_THRESHOLD);
                penalty = (excess / T2_BLOCK) * T2_WEIGHT;
            }
            total += penalty;
        }
        return Math.min(MAX_PENALTY, total);
    }
}
