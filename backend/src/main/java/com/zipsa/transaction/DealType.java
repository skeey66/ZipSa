package com.zipsa.transaction;

/** 거래 유형. 국토부 API 는 매매/전월세 엔드포인트가 나뉘고, 전월세는 월세액으로 구분한다. */
public enum DealType {
    SALE,     // 매매
    JEONSE,   // 전세 (월세 0)
    MONTHLY   // 월세
}
