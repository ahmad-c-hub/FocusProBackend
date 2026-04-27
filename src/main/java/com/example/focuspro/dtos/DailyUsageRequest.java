package com.example.focuspro.dtos;

import java.util.List;

/** Payload sent by Flutter every ~10 minutes with today's totals from UsageStatsManager. */
public class DailyUsageRequest {

    private List<AppUsageItem> usageStats;

    /**
     * The device's local date (ISO format "yyyy-MM-dd"), sent by Flutter so the
     * backend uses the correct calendar day regardless of server timezone (UTC).
     * If absent, falls back to LocalDate.now() on the server.
     */
    private String usageDate;

    public List<AppUsageItem> getUsageStats() { return usageStats; }
    public void setUsageStats(List<AppUsageItem> usageStats) { this.usageStats = usageStats; }

    public String getUsageDate() { return usageDate; }
    public void setUsageDate(String usageDate) { this.usageDate = usageDate; }

    public static class AppUsageItem {
        private String packageName;
        private String appName;
        private int totalMinutesToday;

        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }

        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }

        public int getTotalMinutesToday() { return totalMinutesToday; }
        public void setTotalMinutesToday(int totalMinutesToday) { this.totalMinutesToday = totalMinutesToday; }
    }
}
