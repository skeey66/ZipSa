package com.zipsa.policy;

import java.util.Map;

/**
 * 시·도 코드 ↔ 회원의 region 값.
 *
 * 정책의 대상 지역은 온통청년 API 의 zipCd(법정동코드)에서 앞 2자리를 뽑아 저장한다.
 * 기관명 문자열("부산광역시 주택건축국 주택정책과", "주택과")에서 긁어내던 방식은
 * 지역이 안 들어간 값에서 그냥 샜다.
 *
 * ⚠️ 2026년 개편으로 광주광역시(29)와 전라남도(46)가
 *    전남광주통합특별시(12)로 합쳐졌다. 구 코드로 조회하면 0건이 나온다.
 *    실거래가·공공임대 API 도 12 를 쓴다.
 */
public final class RegionCodes {

    /** 전국 대상 정책의 sido_codes 값. */
    public static final String NATIONWIDE = "ALL";

    private static final Map<String, String> CODE_TO_NAME = Map.ofEntries(
            Map.entry("11", "서울"), Map.entry("26", "부산"), Map.entry("27", "대구"),
            Map.entry("28", "인천"), Map.entry("12", "전남광주"),
            Map.entry("29", "전남광주"), Map.entry("46", "전남광주"),
            Map.entry("30", "대전"), Map.entry("31", "울산"), Map.entry("36", "세종"),
            Map.entry("41", "경기"), Map.entry("42", "강원"), Map.entry("51", "강원"),
            Map.entry("43", "충북"), Map.entry("44", "충남"),
            Map.entry("45", "전북"), Map.entry("52", "전북"),
            Map.entry("47", "경북"), Map.entry("48", "경남"), Map.entry("50", "제주"));

    private RegionCodes() {
    }

    /** 회원 region 이름 → 그 지역에 해당하는 시·도 코드들. 강원처럼 코드가 둘인 곳이 있다. */
    public static java.util.List<String> codesOf(String regionName) {
        if (regionName == null || regionName.isBlank()) return java.util.List.of();
        return CODE_TO_NAME.entrySet().stream()
                .filter(e -> e.getValue().equals(regionName.trim()))
                .map(Map.Entry::getKey)
                .toList();
    }

    public static String nameOf(String code) {
        return CODE_TO_NAME.get(code);
    }

    /**
     * 이 정책을 그 지역 주민이 신청할 수 있는가.
     * sidoCodes 가 비어 있으면(코드 미제공) 판단하지 않고 통과시킨다 — 막으면 놓친다.
     */
    public static boolean matches(String policySidoCodes, String userRegion) {
        if (policySidoCodes == null || policySidoCodes.isBlank()) return true;
        if (NATIONWIDE.equals(policySidoCodes)) return true;
        if (userRegion == null || userRegion.isBlank()) return true;

        var wanted = codesOf(userRegion);
        for (String code : policySidoCodes.split(",")) {
            if (wanted.contains(code.trim())) return true;
        }
        return false;
    }

    /** 정책의 대상 지역을 사람이 읽는 이름으로. 전국이면 "전국". */
    public static String displayOf(String policySidoCodes) {
        if (policySidoCodes == null || policySidoCodes.isBlank()) return null;
        if (NATIONWIDE.equals(policySidoCodes)) return "전국";
        return java.util.Arrays.stream(policySidoCodes.split(","))
                .map(String::trim).map(RegionCodes::nameOf)
                .filter(java.util.Objects::nonNull).distinct()
                .reduce((a, b) -> a + "·" + b).orElse(null);
    }
}
