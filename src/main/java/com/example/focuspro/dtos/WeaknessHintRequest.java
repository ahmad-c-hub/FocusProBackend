package com.example.focuspro.dtos;

public class WeaknessHintRequest {

    private String hint;

    public WeaknessHintRequest() {}

    public WeaknessHintRequest(String hint) {
        this.hint = hint;
    }

    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
}
