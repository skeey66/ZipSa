-- ============================================================
--  V20 — 실거래가 2NF 정규화: 아파트를 별도 테이블로 분리
--
--  기존 real_estate_transactions 의 자연키는
--      (region_code, apt_name, deal_date, exclusive_area, floor)
--  인데 latitude·longitude·build_year 는 이 중 (region_code, apt_name)
--  에만 종속됐습니다. 키의 일부에만 종속 = 부분 함수 종속 = 2NF 위반.
--
--  결과로 41,600건이 5,587개 단지의 좌표를 평균 7.4번씩 중복 저장했고,
--  실제로 같은 단지인데 행마다 건축년도가 다른 경우가 95건 생겼습니다.
--  (좌표가 안 깨진 건 크롤러가 단지 단위로 캐시해서지 스키마 덕이 아닙니다.)
-- ============================================================

CREATE TABLE apartments (
    id          BIGSERIAL     PRIMARY KEY,
    region_code VARCHAR(10)   NOT NULL REFERENCES regions (region_code),
    name        VARCHAR(255)  NOT NULL,
    build_year  INT,
    latitude    DECIMAL(10, 7),
    longitude   DECIMAL(10, 7),
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uq_apartments UNIQUE (region_code, name)
);
CREATE INDEX idx_apartments_region ON apartments (region_code);
CREATE INDEX idx_apartments_name   ON apartments (name);

COMMENT ON TABLE  apartments IS '단지. 좌표·건축년도처럼 거래가 아니라 단지에 딸린 사실을 보관한다';
COMMENT ON COLUMN apartments.latitude IS '카카오 로컬 API 지오코딩 결과. 단지당 한 번만 조회하면 된다';

-- ─────────────── 기존 데이터에서 단지 추출 ───────────────
--
-- build_year 는 mode()(최빈값)로 뽑습니다. 행마다 값이 엇갈리는 95개 단지를
-- 다수결로 하나로 모읍니다. mode() 는 NULL 을 무시하므로 값이 있는 것 중에서 고릅니다.
INSERT INTO apartments (region_code, name, build_year, latitude, longitude)
SELECT region_code,
       apt_name,
       mode() WITHIN GROUP (ORDER BY build_year),
       max(latitude),
       max(longitude)
FROM real_estate_transactions
GROUP BY region_code, apt_name;

-- ─────────────── 거래를 단지에 연결 ───────────────

ALTER TABLE real_estate_transactions ADD COLUMN apartment_id BIGINT;

UPDATE real_estate_transactions t
   SET apartment_id = a.id
  FROM apartments a
 WHERE a.region_code = t.region_code
   AND a.name        = t.apt_name;

ALTER TABLE real_estate_transactions
    ALTER COLUMN apartment_id SET NOT NULL,
    ADD CONSTRAINT fk_transactions_apartment
        FOREIGN KEY (apartment_id) REFERENCES apartments (id);

-- ─────────────── 단지에 딸린 컬럼 제거 ───────────────
--
-- region_code 도 뺍니다. 단지가 이미 갖고 있어 거래에 두면 이행 종속입니다.
ALTER TABLE real_estate_transactions
    DROP CONSTRAINT uq_transaction,
    DROP COLUMN region_code,
    DROP COLUMN apt_name,
    DROP COLUMN build_year,
    DROP COLUMN latitude,
    DROP COLUMN longitude;

-- ─────────────── 제약·인덱스 재구축 ───────────────
--
-- ⚠️ 예전 uq_transaction 에는 deal_type 이 빠져 있었습니다. 같은 단지·날짜·면적·층에
--    전세와 월세가 함께 나오면 크롤러의 ON CONFLICT DO UPDATE 가 한쪽을 덮어씁니다.
--    지금 데이터에선 우연히 충돌이 0건이었을 뿐이라, 이번에 키에 포함시킵니다.
ALTER TABLE real_estate_transactions
    ADD CONSTRAINT uq_transaction
        UNIQUE (apartment_id, deal_date, exclusive_area, floor, deal_type);

CREATE INDEX idx_transactions_apartment_date ON real_estate_transactions (apartment_id, deal_date);
CREATE INDEX idx_transactions_type_date      ON real_estate_transactions (deal_type, deal_date);
