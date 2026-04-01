package com.example.focuspro.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "activity_type", nullable = false, length = 100)
    private String activityType;

    @Column(name = "activity_description", columnDefinition = "TEXT")
    private String activityDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "activity_data", columnDefinition = "jsonb")
    private String activityData;

    @Column(name = "activity_date")
    private LocalDateTime activityDate;
}
