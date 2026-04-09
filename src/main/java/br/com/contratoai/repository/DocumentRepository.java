package br.com.contratoai.repository;

import br.com.contratoai.domain.entity.Document;
import br.com.contratoai.domain.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("SELECT COUNT(d) FROM Document d WHERE d.user.id = :userId AND d.createdAt >= :since")
    long countDocumentsSince(UUID userId, LocalDateTime since);

    Optional<Document> findByIdAndUserId(UUID id, UUID userId);

    Page<Document> findByUserIdAndStatus(UUID userId, DocumentStatus status, Pageable pageable);
}
