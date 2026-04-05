package com.example.focuspro.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteProfileRequest {
    private String dob;   // expected format: "YYYY-MM-DD"
    private String name;  // optional display name override
}
