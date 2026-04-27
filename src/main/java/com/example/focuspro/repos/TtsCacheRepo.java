package com.example.focuspro.repos;

import com.example.focuspro.entities.TtsCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TtsCacheRepo extends JpaRepository<TtsCacheEntry, Integer> {
}
