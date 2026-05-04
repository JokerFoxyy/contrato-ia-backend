package br.com.contratoai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class SqsConfig {

    @Value("${aws.sqs.region:${aws.s3.region:us-east-1}}")
    private String region;

    @Value("${aws.sqs.endpoint:${aws.s3.endpoint:#{null}}}")
    private String endpoint;

    @Value("${aws.s3.access-key:#{null}}")
    private String accessKey;

    @Value("${aws.s3.secret-key:#{null}}")
    private String secretKey;

    @Bean
    public SqsClient sqsClient() {
        var builder = SqsClient.builder()
                .region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    accessKey != null ? accessKey : "test",
                                    secretKey != null ? secretKey : "test"
                            )
                    ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
