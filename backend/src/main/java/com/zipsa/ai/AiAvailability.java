package com.zipsa.ai;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 키가 실제로 설정됐는지 판단한다.
 *
 * application.yml 에 자리표시자(not-configured)를 넣어 애플리케이션이 뜨게 해뒀다.
 * 그래서 "ChatModel 빈이 있다 = 쓸 수 있다" 가 아니다. 호출 전에 여기로 확인한다.
 *
 * 확인하지 않고 부르면 사용자는 500 을 보고, 우리는 로그에서 401 을 본다.
 * 둘 다 원인이 "키를 안 넣었다" 인데 그 사실이 어디에도 안 적힌다.
 */
@Component
public class AiAvailability {

    private static final Logger log = LoggerFactory.getLogger(AiAvailability.class);
    private static final String PLACEHOLDER = "not-configured";

    private final String apiKey;

    public AiAvailability(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !PLACEHOLDER.equals(apiKey);
    }

    @PostConstruct
    void announce() {
        if (isConfigured()) {
            log.info("AI 사용 가능 — 정책·뉴스 요약이 동작합니다.");
        } else {
            log.warn("OPENAI_API_KEY 가 없습니다. AI 요약은 비활성 상태로 뜹니다. "
                    + "(나머지 기능은 정상 — .env 의 OPENAI_API_KEY 를 채우면 켜집니다)");
        }
    }
}
