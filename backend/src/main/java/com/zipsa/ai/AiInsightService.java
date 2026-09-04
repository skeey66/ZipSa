package com.zipsa.ai;

import com.zipsa.ai.dto.AiInsightResponse;
import com.zipsa.news.News;
import com.zipsa.policy.Policy;
import com.zipsa.user.User;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 「AI 요약」과 「나에게 어떻게 적용되나」의 진입점.
 *
 *   OPENAI_API_KEY 있음 → LLM 호출 (aiGenerated=true, 화면 배지 「AI」)
 *   키 없음 / 호출 실패 → 규칙 기반  (aiGenerated=false, 화면 배지 「샘플」)
 *
 * 키만 넣으면 바로 동작한다. 코드를 고칠 필요가 없다.
 *
 * ⚠️ AI 는 보조 기능이다. OpenAI 가 죽어도 정책 본문은 읽을 수 있어야 하므로
 *    예외를 밖으로 던지지 않고 규칙 기반으로 떨어진다.
 */
@Service
public class AiInsightService {

    private static final Logger log = LoggerFactory.getLogger(AiInsightService.class);

    /**
     * 같은 글을 다시 열 때마다 LLM 을 부르면 비용이 그대로 곱해진다.
     * 회원별로 답이 달라 전역 캐시는 못 쓰고, (종류·글·회원) 조합으로 캐시한다.
     * 서버를 다시 띄우면 사라지는 수준으로 충분하다(정책·기사 내용은 자주 안 바뀐다).
     */
    private static final int CACHE_MAX = 500;
    private final Map<String, AiInsightResponse> cache =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, AiInsightResponse> eldest) {
                    return size() > CACHE_MAX;
                }
            });

    private final AiAvailability availability;
    private final RuleBasedInsight ruleBased;
    /** 키가 없으면 ChatModel 빈이 없을 수도 있다. 그때도 앱은 떠야 한다. */
    private final ObjectProvider<LlmInsight> llm;

    public AiInsightService(AiAvailability availability, RuleBasedInsight ruleBased,
                            ObjectProvider<LlmInsight> llm) {
        this.availability = availability;
        this.ruleBased = ruleBased;
        this.llm = llm;
    }

    public AiInsightResponse forPolicy(Policy policy, User user) {
        AiInsightResponse fallback = ruleBased.forPolicy(policy, user);
        return withLlm("policy", policy.getId(), user.getId(), fallback,
                engine -> engine.forPolicy(policy, user, verdictOf(fallback)));
    }

    public AiInsightResponse forNews(News news, User user) {
        AiInsightResponse fallback = ruleBased.forNews(news, user);
        return withLlm("news", news.getId(), user.getId(), fallback,
                engine -> engine.forNews(news, user, verdictOf(fallback)));
    }

    /* ── 공통 ────────────────────────────────── */

    @FunctionalInterface
    private interface LlmCall {
        AiInsightResponse apply(LlmInsight engine);
    }

    private AiInsightResponse withLlm(String kind, Long targetId, Long userId,
                                      AiInsightResponse fallback, LlmCall call) {
        if (!availability.isConfigured()) {
            return fallback;
        }
        LlmInsight engine = llm.getIfAvailable();
        if (engine == null) {
            return fallback;
        }

        String key = kind + ":" + targetId + ":" + userId;
        AiInsightResponse cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            AiInsightResponse generated = call.apply(engine);
            cache.put(key, generated);
            return generated;
        } catch (Exception e) {
            // 화면을 막지 않는다. 무엇이 실패했는지는 로그에만 남긴다.
            log.warn("AI 생성 실패 — 규칙 기반으로 대체합니다 ({} {}): {}",
                    kind, targetId, e.getMessage());
            return fallback;
        }
    }

    /** 규칙 기반이 내린 판정을 LLM 프롬프트에 넘길 형태로 바꾼다. */
    private LlmInsight.Verdict verdictOf(AiInsightResponse fallback) {
        var app = fallback.application();
        return new LlmInsight.Verdict(app.verdict(), app.reasons(), app.tone());
    }
}
