package com.ssafy.wswg.model.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dao.GroupDao;
import com.ssafy.wswg.model.dao.TripDao;
import com.ssafy.wswg.model.dto.TripDto;
import com.ssafy.wswg.model.dto.TripMediaUploadResponse;
import com.ssafy.wswg.realtime.PlanStateService;
import com.ssafy.wswg.storage.ObjectStorageUploadResult;
import com.ssafy.wswg.storage.ObjectStorageUploader;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TripMediaService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> MEDIA_TYPES = List.of("PHOTO", "AUDIO", "VIDEO");

    private final TripDao tripDao;
    private final GroupDao groupDao;
    private final ObjectStorageUploader objectStorageUploader;
    private final PlanStateService planStateService;

    @Transactional
    public TripMediaUploadResponse uploadMedia(
            Long tripId,
            Long userId,
            String itemId,
            String mediaType,
            MultipartFile file) {
        TripDto trip = findTrip(tripId);
        validateWritable(trip, userId);

        String normalizedItemId = normalizeItemId(itemId);
        String normalizedMediaType = normalizeMediaType(mediaType);
        validateFile(file, normalizedMediaType);

        ObjectNode data = copyData(trip.getData());
        ObjectNode item = findItem(data, normalizedItemId);

        String mediaId = UUID.randomUUID().toString();
        String objectKey = buildObjectKey(tripId, normalizedItemId, mediaId, file.getOriginalFilename());
        ObjectStorageUploadResult uploadResult = upload(objectKey, file);

        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("objectKey", uploadResult.objectKey());
        metadata.put("originalFilename", safeOriginalFilename(file.getOriginalFilename()));
        metadata.put("contentType", file.getContentType());
        metadata.put("size", file.getSize());
        metadata.put("uploadedAt", OffsetDateTime.now().toString());

        ObjectNode media = OBJECT_MAPPER.createObjectNode();
        media.put("id", mediaId);
        media.put("type", normalizedMediaType);
        media.put("url", uploadResult.url());
        media.set("metadata", metadata);

        JsonNode updatedData;
        if (planStateService.hasState(tripId)) {
            updatedData = planStateService.appendBlockMedia(tripId, userId, normalizedItemId, media);
        } else {
            mediaArray(item).add(media);
            updatedData = data;
        }

        if (tripDao.updateTripData(tripId, updatedData) == 0) {
            throw new CommonException(ErrorCode.NOT_FOUND_TRIP);
        }

        TripMediaUploadResponse response = new TripMediaUploadResponse();
        response.setTripId(tripId);
        response.setItemId(normalizedItemId);
        response.setMediaId(mediaId);
        response.setMediaType(normalizedMediaType);
        response.setMediaUrl(uploadResult.url());
        response.setMetadata(metadata);

        return response;
    }

    private TripDto findTrip(Long tripId) {
        if (tripId == null) {
            throw new CommonException(ErrorCode.NOT_FOUND_TRIP);
        }

        TripDto trip = tripDao.readTripById(tripId);
        if (trip == null) {
            throw new CommonException(ErrorCode.NOT_FOUND_TRIP);
        }

        return trip;
    }

    private void validateWritable(TripDto trip, Long userId) {
        if (trip.getUserId() != null && trip.getUserId().equals(userId)) {
            return;
        }

        if (trip.getGroupId() != null && groupDao.countGroupOwner(trip.getGroupId(), userId) > 0) {
            return;
        }

        throw new CommonException(ErrorCode.TRIP_ACCESS_DENIED);
    }

    private String normalizeItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new CommonException(ErrorCode.INVALID_MEDIA_UPLOAD_REQUEST);
        }

        return itemId.trim();
    }

    private String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            throw new CommonException(ErrorCode.INVALID_MEDIA_UPLOAD_REQUEST);
        }

        String normalizedMediaType = mediaType.trim().toUpperCase(Locale.ROOT);
        if (!MEDIA_TYPES.contains(normalizedMediaType)) {
            throw new CommonException(ErrorCode.INVALID_MEDIA_UPLOAD_REQUEST);
        }

        return normalizedMediaType;
    }

    private void validateFile(MultipartFile file, String mediaType) {
        if (file == null || file.isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_MEDIA_UPLOAD_REQUEST);
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new CommonException(ErrorCode.INVALID_MEDIA_UPLOAD_REQUEST);
        }

        if ("PHOTO".equals(mediaType) && !contentType.startsWith("image/")) {
            throw new CommonException(ErrorCode.INVALID_MEDIA_UPLOAD_REQUEST);
        }

        if ("AUDIO".equals(mediaType) && !contentType.startsWith("audio/")) {
            throw new CommonException(ErrorCode.INVALID_MEDIA_UPLOAD_REQUEST);
        }

        if ("VIDEO".equals(mediaType) && !contentType.startsWith("video/")) {
            throw new CommonException(ErrorCode.INVALID_MEDIA_UPLOAD_REQUEST);
        }
    }

    private ObjectNode copyData(JsonNode data) {
        if (data == null || data.isNull() || !data.isObject()) {
            ObjectNode defaultData = OBJECT_MAPPER.createObjectNode();
            defaultData.putArray("items");
            return defaultData;
        }

        return data.deepCopy();
    }

    private ObjectNode findItem(ObjectNode data, String itemId) {
        JsonNode items = data.get("items");
        if (items == null || !items.isArray()) {
            throw new CommonException(ErrorCode.NOT_FOUND_TRIP_ITEM);
        }

        for (JsonNode item : items) {
            if (item.isObject() && itemId.equals(item.path("id").asText(null))) {
                return (ObjectNode) item;
            }
        }

        throw new CommonException(ErrorCode.NOT_FOUND_TRIP_ITEM);
    }

    private ArrayNode mediaArray(ObjectNode item) {
        JsonNode media = item.get("media");
        if (media != null && media.isArray()) {
            return (ArrayNode) media;
        }

        ArrayNode mediaArray = OBJECT_MAPPER.createArrayNode();
        item.set("media", mediaArray);

        return mediaArray;
    }

    private ObjectStorageUploadResult upload(String objectKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return objectStorageUploader.upload(objectKey, inputStream, file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new CommonException(ErrorCode.OBJECT_STORAGE_UPLOAD_FAILED);
        }
    }

    private String buildObjectKey(Long tripId, String itemId, String mediaId, String originalFilename) {
        return "trips/%d/items/%s/%s%s".formatted(
                tripId,
                sanitizePathSegment(itemId),
                mediaId,
                extension(originalFilename));
    }

    private String extension(String originalFilename) {
        String filename = safeOriginalFilename(originalFilename);
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }

        return filename.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private String safeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "media";
        }

        String filename = originalFilename.replace("\\", "/");
        int slashIndex = filename.lastIndexOf('/');
        return slashIndex < 0 ? filename : filename.substring(slashIndex + 1);
    }

    private String sanitizePathSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
