package com.example.focuspro.dtos;

public class AppUsageStatDTO {

    private String packageName;
    private String appName;
    private long totalMinutesToday;

    public AppUsageStatDTO() {}

    public AppUsageStatDTO(String packageName, String appName, long totalMinutesToday) {
        this.packageName = packageName;
        this.appName = appName;
        this.totalMinutesToday = totalMinutesToday;
    }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public long getTotalMinutesToday() { return totalMinutesToday; }
    public void setTotalMinutesToday(long totalMinutesToday) { this.totalMinutesToday = totalMinutesToday; }
}
