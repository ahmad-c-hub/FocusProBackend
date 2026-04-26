package com.example.focuspro.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_app_usage")
public class DailyAppUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "package_name", nullable = false, length = 255)
    private String packageName;

    @Column(name = "app_name", length = 255)
    private String appName;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "total_minutes", nullable = false)
    private int totalMinutes;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public DailyAppUsage() {}

    public Long getId() { return id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public LocalDate getUsageDate() { return usageDate; }
    public void setUsageDate(LocalDate usageDate) { this.usageDate = usageDate; }

    public int getTotalMinutes() { return totalMinutes; }
    public void setTotalMinutes(int totalMinutes) { this.totalMinutes = totalMinutes; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
