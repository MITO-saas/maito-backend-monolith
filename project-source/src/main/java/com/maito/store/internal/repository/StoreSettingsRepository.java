package com.maito.store.internal.repository;

import com.maito.store.internal.domain.StoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreSettingsRepository extends JpaRepository<StoreSettings, UUID> {
    Optional<StoreSettings> findFirstByOrderByCreatedAtAsc();
}
