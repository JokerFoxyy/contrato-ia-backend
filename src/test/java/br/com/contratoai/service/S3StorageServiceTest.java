package br.com.contratoai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        s3StorageService = new S3StorageService(s3Client, s3Presigner);
        ReflectionTestUtils.setField(s3StorageService, "bucket", "test-bucket");
        ReflectionTestUtils.setField(s3StorageService, "presignedUrlExpiryHours", 24);
    }

    @Test
    @DisplayName("uploadDocument - should upload to S3 with correct key pattern and metadata")
    void uploadDocument_success() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        byte[] content = "PDF content".getBytes();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String s3Key = s3StorageService.uploadDocument(
                userId, documentId, content, "application/pdf", "pdf");

        assertThat(s3Key).startsWith("documents/" + userId + "/" + documentId + "/");
        assertThat(s3Key).endsWith(".pdf");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.contentType()).isEqualTo("application/pdf");
        assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(request.metadata()).containsEntry("userId", userId.toString());
        assertThat(request.metadata()).containsEntry("documentId", documentId.toString());
    }

    @Test
    @DisplayName("uploadDocument - should throw on S3 error")
    void uploadDocument_s3Error() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").build());

        assertThatThrownBy(() -> s3StorageService.uploadDocument(
                UUID.randomUUID(), UUID.randomUUID(), "data".getBytes(), "application/pdf", "pdf"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro ao fazer upload para S3");
    }

    @Test
    @DisplayName("generatePresignedUrl - should return presigned URL")
    void generatePresignedUrl_success() throws MalformedURLException {
        String s3Key = "documents/user/doc/123.pdf";
        URL expectedUrl = new URL("https://test-bucket.s3.amazonaws.com/documents/user/doc/123.pdf?signed=true");

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(expectedUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        URL result = s3StorageService.generatePresignedUrl(s3Key);

        assertThat(result).isEqualTo(expectedUrl);
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    @DisplayName("softDelete - should tag object as deleted")
    void softDelete_success() {
        String s3Key = "documents/user/doc/123.pdf";

        when(s3Client.putObjectTagging(any(PutObjectTaggingRequest.class)))
                .thenReturn(PutObjectTaggingResponse.builder().build());

        s3StorageService.softDelete(s3Key);

        ArgumentCaptor<PutObjectTaggingRequest> captor = ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        verify(s3Client).putObjectTagging(captor.capture());

        PutObjectTaggingRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(s3Key);
        assertThat(request.tagging().tagSet()).hasSize(1);
        assertThat(request.tagging().tagSet().get(0).key()).isEqualTo("deleted");
        assertThat(request.tagging().tagSet().get(0).value()).isEqualTo("true");
    }

    @Test
    @DisplayName("exists - should return true when object exists")
    void exists_objectExists() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertThat(s3StorageService.exists("documents/user/doc/123.pdf")).isTrue();
    }

    @Test
    @DisplayName("exists - should return false when object does not exist")
    void exists_objectNotFound() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        assertThat(s3StorageService.exists("documents/user/doc/999.pdf")).isFalse();
    }
}
