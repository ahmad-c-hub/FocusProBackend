package com.example.focuspro.repos;

import com.example.focuspro.entities.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameRepo extends JpaRepository<Game, Integer> {

    Optional<Game> findByType(String type);
}
