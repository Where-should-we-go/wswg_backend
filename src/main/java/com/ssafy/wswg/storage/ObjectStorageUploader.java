package com.ssafy.wswg.storage;

import java.io.IOException;
import java.io.InputStream;

public interface ObjectStorageUploader {
    ObjectStorageUploadResult upload(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType) throws IOException;
}
