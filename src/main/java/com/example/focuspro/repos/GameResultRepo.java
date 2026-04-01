package com.example.focuspro.repos;

import com.example.focuspro.entities.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameResultRepo extends JpaRepository<GameResult, Long> {

    List<GameResult> findByUserIdOrderByPlayedAtDesc(int userId);
}
