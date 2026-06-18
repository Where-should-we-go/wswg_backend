package com.ssafy.wswg.external.openai;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;

@Component
public class OpenAiEmbeddingClient {
    private final RestClient restClient;
    private final String apiKey;
    private final String embeddingModel;

    public OpenAiEmbeddingClient(
            RestClient.Builder restClientBuilder,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.embedding-model:text-embedding-3-small}") String embeddingModel,
            @Value("${openai.embedding-url:https://api.openai.com/v1/embeddings}") String embeddingUrl) {
        this.restClient = restClientBuilder
                .baseUrl(embeddingUrl)
                .build();
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
    }

    public List<Double> createEmbedding(String input) {
        if (!StringUtils.hasText(apiKey)) {
            throw new CommonException(ErrorCode.EMBEDDING_REQUEST_FAILED);
        }

        try {
            EmbeddingResponse response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(new EmbeddingRequest(embeddingModel, input))
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new CommonException(ErrorCode.EMBEDDING_REQUEST_FAILED);
            }

            return response.data().get(0).embedding();
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            throw new CommonException(ErrorCode.EMBEDDING_REQUEST_FAILED);
        }
    }

    private record EmbeddingRequest(String model, String input) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(List<Double> embedding) {
    }
}
