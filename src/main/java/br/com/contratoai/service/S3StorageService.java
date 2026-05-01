package br.com.contratoai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.presigned-url-expiry-hours:24}")
    private int presignedUrlExpiryHours;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * Faz upload de um documento para S3.
     * Caminho: documents/{userId}/{documentId}/{timestamp}.{extension}
     *
     * @return a S3 key do objeto armazenado
     */
    public String uploadDocument(UUID userId, UUID documentId, byte[] content,
                                  String contentType, String extension) {
        String s3Key = buildS3Key(userId, documentId, extension);

        Map<String, String> metadata = Map.of(
                "userId", userId.toString(),
                "documentId", documentId.toString(),
                "generatedAt", Instant.now().toString(),
                "contentType", contentType
        );

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(contentType)
                    .metadata(metadata)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));

            log.info("Upload S3 concluído. bucket={}, key={}, tamanho={} bytes",
                    bucket, s3Key, content.length);

            return s3Key;

        } catch (S3Exception e) {
            log.error("Falha no upload S3. bucket={}, key={}, erro={}",
                    bucket, s3Key, e.getMessage(), e);
            throw new RuntimeException("Erro ao fazer upload para S3: " + e.getMessage(), e);
        }
    }

    /**
     * Gera uma presigned URL para download seguro sem expor o bucket.
     */
    public URL generatePresignedUrl(String s3Key) {
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(presignedUrlExpiryHours))
                    .getObjectRequest(builder -> builder
                            .bucket(bucket)
                            .key(s3Key)
                            .build())
                    .build();

            URL url = s3Presigner.presignGetObject(presignRequest).url();

            log.debug("Presigned URL gerada para key={}, expira em {}h", s3Key, presignedUrlExpiryHours);
            return url;

        } catch (S3Exception e) {
            log.error("Falha ao gerar presigned URL. key={}, erro={}", s3Key, e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar URL de download: " + e.getMessage(), e);
        }
    }

    /**
     * Soft delete: marca o objeto com tag deleted=true.
     * Não remove fisicamente — preserva para auditoria jurídica.
     */
    public void softDelete(String s3Key) {
        try {
            Tagging tagging = Tagging.builder()
                    .tagSet(Tag.builder()
                            .key("deleted")
                            .value("true")
                            .build())
                    .build();

            s3Client.putObjectTagging(PutObjectTaggingRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .tagging(tagging)
                    .build());

            log.info("Soft delete aplicado. bucket={}, key={}", bucket, s3Key);

        } catch (S3Exception e) {
            log.error("Falha no soft delete. key={}, erro={}", s3Key, e.getMessage(), e);
            throw new RuntimeException("Erro ao marcar documento como deletado: " + e.getMessage(), e);
        }
    }

    /**
     * Verifica se o objeto existe no S3.
     */
    public boolean exists(String s3Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private String buildS3Key(UUID userId, UUID documentId, String extension) {
        long timestamp = Instant.now().toEpochMilli();
        return String.format("documents/%s/%s/%d.%s",
                userId, documentId, timestamp, extension);
    }
}
