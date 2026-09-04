-- ============================================================
--  전월세 실거래가를 담기 위한 컬럼 추가
--
--  국토부 전월세 API 는 보증금(deposit)과 월세(monthlyRent) 를 함께 줍니다.
--  기존 deal_amount 한 칸으로는 담기지 않아 월세를 따로 둡니다.
--
--  금액 단위는 API 원본 그대로 "만원" 입니다.
--    · 매매      deal_amount = 거래금액,  monthly_rent = NULL
--    · 전세      deal_amount = 보증금,    monthly_rent = 0
--    · 월세      deal_amount = 보증금,    monthly_rent = 월세액
-- ============================================================

ALTER TABLE real_estate_transactions
    ADD COLUMN monthly_rent BIGINT;

COMMENT ON COLUMN real_estate_transactions.deal_amount  IS '만원 단위. 매매는 거래금액, 전월세는 보증금';
COMMENT ON COLUMN real_estate_transactions.monthly_rent IS '만원 단위. 매매는 NULL, 전세는 0, 월세는 월세액';
