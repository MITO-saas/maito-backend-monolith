package com.maito.wallet.internal.repository;

import com.maito.wallet.internal.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByCustomerProfileId(UUID customerProfileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.customerProfileId = :customerProfileId")
    Optional<Wallet> findByCustomerProfileIdWithLock(@Param("customerProfileId") UUID customerProfileId);
}
