package com.ssafy.wswg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("oci.object-storage")
public class OracleObjectStorageProperties {
    private String region;
    private String namespace;
    private String bucket;
    private String publicBaseUrl;
    private String tenancyOcid;
    private String userOcid;
    private String fingerprint;
    private String privateKeyPath;
}
