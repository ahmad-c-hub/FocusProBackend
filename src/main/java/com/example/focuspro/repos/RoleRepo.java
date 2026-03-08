package com.example.focuspro.repos;

import com.example.focuspro.entities.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepo extends org.springframework.data.jpa.repository.JpaRepository<com.example.focuspro.entities.Role, Integer> {

    @Query("SELECT r FROM Role r WHERE r.name = ?1")
    Optional<Role> findByName(String name);

}
