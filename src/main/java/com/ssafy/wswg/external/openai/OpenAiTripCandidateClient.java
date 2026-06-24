package com.ssafy.wswg.external.openai;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.wswg.exception.CommonException;
import com.ssafy.wswg.exception.ErrorCode;
import com.ssafy.wswg.model.dto.AiTripCandidateResponse;

@Component
public class OpenAiTripCandidateClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String apiKey;
    private final String chatModel;

    public OpenAiTripCandidateClient(
            RestClient.Builder restClientBuilder,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.chat-model:gpt-4o-mini}") String chatModel,
            @Value("${openai.chat-url:https://api.openai.com/v1/chat/completions}") String chatUrl) {
        this.restClient = restClientBuilder
                .baseUrl(chatUrl)
                .build();
        this.apiKey = apiKey;
        this.chatModel = chatModel;
    }

    public AiTripCandidateResponse createCandidates(String message, int count) {
        if (!StringUtils.hasText(apiKey)) {
            throw new CommonException(ErrorCode.AI_TRIP_CANDIDATE_FAILED);
        }

        try {
            ChatCompletionResponse response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request(message, count))
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            String content = response == null || response.choices() == null || response.choices().isEmpty()
                    ? null
                    : response.choices().get(0).message().content();
            if (!StringUtils.hasText(content)) {
                throw new CommonException(ErrorCode.AI_TRIP_CANDIDATE_FAILED);
            }

            return OBJECT_MAPPER.readValue(content, AiTripCandidateResponse.class);
        } catch (CommonException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new CommonException(ErrorCode.AI_TRIP_CANDIDATE_FAILED);
        } catch (Exception e) {
            throw new CommonException(ErrorCode.AI_TRIP_CANDIDATE_FAILED);
        }
    }

    private ChatCompletionRequest request(String message, int count) {
        String systemPrompt = """
                당신은 한국 여행 추천 후보를 만드는 도우미입니다.
                반드시 JSON만 응답하세요. 마크다운 코드블록을 쓰지 마세요.
                응답 스키마:
                {
                  "reply": "사용자에게 보여줄 짧은 안내",
                  "candidates": [
                    {
                      "name": "여행지 또는 지역 이름",
                      "regionHint": "시도/시군구 힌트",
                      "description": "여행지 분위기와 특징",
                      "reason": "사용자 요청과 어울리는 이유"
                    }
                  ],
                  "nextQuestion": "후보 선택을 유도하는 짧은 질문"
                }
                candidates는 정확히 %d개를 반환하세요.
                """.formatted(count);

        return new ChatCompletionRequest(
                chatModel,
                List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", message)),
                new ResponseFormat("json_object"),
                0.7);
    }

    private record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            ResponseFormat response_format,
            double temperature) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ResponseFormat(String type) {
    }

    private record ChatCompletionResponse(List<ChatChoice> choices) {
    }

    private record ChatChoice(ChatMessage message) {
    }
}
