package com.ssafy.wswg.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.ssafy.wswg.config.OracleObjectStorageProperties;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;

@Component
public class OciObjectStorageUploader implements ObjectStorageUploader {
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
        }

        return new ObjectStorageUploadResult(objectKey, buildPublicUrl(objectKey));
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

    private String buildPublicUrl(String objectKey) {
        String baseUrl = properties.getPublicBaseUrl().replaceAll("/+$", "");
        String encodedKey = Arrays.stream(objectKey.split("/"))
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((left, right) -> left + "/" + right)
                .orElse(objectKey);

        return baseUrl + "/" + encodedKey;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
