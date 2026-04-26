package com.example.focuspro.dtos;

import java.util.List;

/**
 * Payload the Flutter app sends when syncing a batch of screen events.
 * Each item in the list is one app-switch captured by FocusProAccessibilityService.
 */
public class AppScreenEventRequest {

    private List<EventItem> events;

    public List<EventItem> getEvents() { return events; }
    public void setEvents(List<EventItem> events) { this.events = events; }

    public static class EventItem {

        private String packageName;
        private String appName;
        private String activityName;

        /**
         * ISO-8601 timestamp string sent by the device, e.g. "2024-05-01T08:30:00".
         * We parse this on the server side.
         */
        private String startedAt;

        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }

        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }

        public String getActivityName() { return activityName; }
        public void setActivityName(String activityName) { this.activityName = activityName; }

        public String getStartedAt() { return startedAt; }
        public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    }
}
