package com.ssafy.wswg.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.springframework.stereotype.Component;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.ssafy.wswg.config.OracleObjectStorageProperties;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;

@Component
public class OciObjectStorageUploader implements ObjectStorageUploader {
    // 비공개 버킷이라 객체별 read 전용 PAR(Pre-Authenticated Request) URL 을 만들어 반환한다.
    // 버킷을 Public 으로 열지 않고도, 토큰이 담긴 URL 로만 이미지를 조회할 수 있다.
    private static final long PAR_EXPIRES_DAYS = 3650; // 약 10년(만료되면 링크가 끊기므로 길게).

    private final OracleObjectStorageProperties properties;

    public OciObjectStorageUploader(OracleObjectStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public ObjectStorageUploadResult upload(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType) throws IOException {
        validateProperties();

        SimpleAuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
                .tenantId(properties.getTenancyOcid())
                .userId(properties.getUserOcid())
                .fingerprint(properties.getFingerprint())
                .privateKeySupplier(new SimplePrivateKeySupplier(properties.getPrivateKeyPath()))
                .build();

        try (ObjectStorageClient client = ObjectStorageClient.builder()
                .region(Region.fromRegionId(properties.getRegion()))
                .build(provider)) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .namespaceName(properties.getNamespace())
                    .bucketName(properties.getBucket())
                    .objectName(objectKey)
                    .putObjectBody(inputStream)
                    .contentLength(contentLength)
                    .contentType(contentType)
                    .build();

            client.putObject(request);

            return new ObjectStorageUploadResult(objectKey, buildReadParUrl(client, objectKey));
        }
    }

    private void validateProperties() {
        if (isBlank(properties.getRegion())
                || isBlank(properties.getNamespace())
                || isBlank(properties.getBucket())
                || isBlank(properties.getPublicBaseUrl())
                || isBlank(properties.getTenancyOcid())
                || isBlank(properties.getUserOcid())
                || isBlank(properties.getFingerprint())
                || isBlank(properties.getPrivateKeyPath())) {
            throw new CommonException(ErrorCode.OBJECT_STORAGE_CONFIG_INVALID);
        }
    }

    // 객체 read 전용 PAR 생성 → accessUri(상대경로)를 publicBaseUrl 의 호스트에 붙여 완전한 URL 로 만든다.
    private String buildReadParUrl(ObjectStorageClient client, String objectKey) {
        CreatePreauthenticatedRequestDetails details = CreatePreauthenticatedRequestDetails.builder()
                .name("read-" + objectKey)
                .objectName(objectKey)
                .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectRead)
                .timeExpires(Date.from(Instant.now().plus(PAR_EXPIRES_DAYS, ChronoUnit.DAYS)))
                .build();

        String accessUri = client.createPreauthenticatedRequest(
                        CreatePreauthenticatedRequestRequest.builder()
                                .namespaceName(properties.getNamespace())
                                .bucketName(properties.getBucket())
                                .createPreauthenticatedRequestDetails(details)
                                .build())
                .getPreauthenticatedRequest()
                .getAccessUri();

        URI base = URI.create(properties.getPublicBaseUrl());
        return base.getScheme() + "://" + base.getAuthority() + accessUri;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
