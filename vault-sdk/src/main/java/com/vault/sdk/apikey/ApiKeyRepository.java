package com.vault.sdk.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByKeyPrefix(String keyPrefix);

    List<ApiKey> findByTenantIdAndCreatedByAndRevokedFalseOrderByCreatedAtDesc(String tenantId, String createdBy);

    List<ApiKey> findByTenantIdAndRevokedFalseOrderByCreatedAtDesc(String tenantId);
}
