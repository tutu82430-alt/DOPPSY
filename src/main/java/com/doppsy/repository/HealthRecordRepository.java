package com.doppsy.repository;

import com.doppsy.entity.HealthRecord;
import com.doppsy.entity.HealthRecord.RecordType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link HealthRecord} entities.
 *
 * <p>Extends {@link JpaRepository} to inherit full CRUD operations.
 * Custom derived-query methods expose the most common health-record
 * access patterns for patients, providers, and admins.</p>
 */
@Repository
public interface HealthRecordRepository extends JpaRepository<HealthRecord, UUID> {

    /**
     * Returns all health records owned by a patient, newest first.
     * The primary query for a patient's health timeline.
     *
     * @param userId the patient's UUID
     * @return list of the patient's health records
     */
    List<HealthRecord> findAllByUserUserIdOrderByUploadedAtDesc(UUID userId);

    /**
     * Returns all health records of a specific type for a patient
     * (e.g., all PRESCRIPTION records for a given user).
     *
     * @param userId     the patient's UUID
     * @param recordType the document category to filter by
     * @return filtered list of health records
     */
    List<HealthRecord> findAllByUserUserIdAndRecordType(UUID userId, RecordType recordType);

    /**
     * Returns all records issued by a specific provider.
     * Used on the provider dashboard to review issued documents.
     *
     * @param providerId the provider's UUID
     * @return list of records issued by this provider
     */
    List<HealthRecord> findAllByProviderProviderIdOrderByUploadedAtDesc(UUID providerId);

    /**
     * Returns all records of a specific type issued by a specific provider
     * (e.g., all LAB_REPORTs issued by Dr. X).
     *
     * @param providerId the provider's UUID
     * @param recordType the document category to filter by
     * @return filtered list of health records
     */
    List<HealthRecord> findAllByProviderProviderIdAndRecordType(UUID providerId, RecordType recordType);

    /**
     * Counts total records per type for a user — useful for the patient profile summary.
     *
     * @param userId     the patient's UUID
     * @param recordType the document category to count
     * @return total count
     */
    long countByUserUserIdAndRecordType(UUID userId, RecordType recordType);
}
