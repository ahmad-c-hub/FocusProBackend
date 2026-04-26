package com.example.focuspro.dtos;

import java.time.LocalDate;

/** One app's usage for a given day — returned by GET /screen-events/summary. */
public class DailyUsageSummaryDTO {

    private String packageName;
    private String appName;
    private int totalMinutes;
    private LocalDate usageDate;

    public DailyUsageSummaryDTO() {}

    public DailyUsageSummaryDTO(String packageName, String appName,
                                 int totalMinutes, LocalDate usageDate) {
        this.packageName = packageName;
        this.appName = appName;
        this.totalMinutes = totalMinutes;
        this.usageDate = usageDate;
    }

    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public int getTotalMinutes() { return totalMinutes; }
    public LocalDate getUsageDate() { return usageDate; }
}
