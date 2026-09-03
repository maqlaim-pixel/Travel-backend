package com.travelvista.repository;

import com.travelvista.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findTopByEmailAndPurposeAndRecordTypeAndRecordIdOrderByCreatedAtDesc(
            String email, String purpose, String recordType, Long recordId);

    List<OtpVerification> findByEmailAndPurposeAndRecordTypeAndRecordIdAndVerifiedFalse(
            String email, String purpose, String recordType, Long recordId);

    @Modifying
    @Transactional
    @Query("DELETE FROM OtpVerification o WHERE o.expiresAt < :now")
    void deleteExpired(LocalDateTime now);

    @Query("SELECT COUNT(o) FROM OtpVerification o WHERE o.email = :email AND o.createdAt > :since")
    long countRecentByEmail(String email, LocalDateTime since);
}
