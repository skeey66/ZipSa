-- ============================================================
--  V21 — 공공임대 단지 3NF 정규화: 단지와 평형을 분리
--
--  public_housing_complexes 는 「단지 × 평형」 한 행이었습니다.
--  6,993개 단지가 이름·주소·좌표를 48,886행에 걸쳐 반복했고,
--  단지에만 딸린 사실이 평형 행마다 복제됐습니다(이행 종속).
--
--  실제로 값이 어긋난 곳:
--    · household_count  109개 단지에서 평형마다 다름
--  정규화하면 구조적으로 불가능해집니다.
--
--  ⚠️ 데이터로 확인한 것 하나 — housing_type 은 단지가 아니라 평형에 딸립니다.
--     한 단지에 임대유형이 둘 이상인 경우가 107건 있습니다(국민임대 + 영구임대 등).
--     단지 쪽으로 올렸으면 이 107건을 잃을 뻔했습니다.
-- ============================================================

CREATE TABLE housing_complexes (
    id             BIGSERIAL    PRIMARY KEY,
    complex_no     BIGINT       NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    institution    VARCHAR(100),
    -- sido_code(2) || sigungu_code(3) = 법정동코드 5자리. regions 와 정확히 맞는 것을
    -- 확인했다(25/25). 이름(sido_name·sigungu_name)은 regions 에서 조인해 얻는다.
    region_code    VARCHAR(10)  NOT NULL REFERENCES regions (region_code),
    road_address   VARCHAR(255),
    house_type     VARCHAR(30),
    household_count INT,
    parking_count  INT,
    completed_date VARCHAR(8),
    latitude       DECIMAL(10, 7),
    longitude      DECIMAL(10, 7),
    crawl_job_id   BIGINT       REFERENCES crawl_jobs (id),
    crawled_at     TIMESTAMP    NOT NULL DEFAULT now(),
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_housing_complexes_region ON housing_complexes (region_code);

CREATE TABLE housing_complex_units (
    id              BIGSERIAL    PRIMARY KEY,
    complex_id      BIGINT       NOT NULL REFERENCES housing_complexes (id) ON DELETE CASCADE,
    external_id     VARCHAR(120) NOT NULL UNIQUE,
    housing_type    VARCHAR(30)  NOT NULL,
    style_name      VARCHAR(50),
    exclusive_area  DECIMAL(8, 3),
    supply_area     DECIMAL(8, 3),
    deposit         BIGINT,
    monthly_rent    BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_complex_units_complex ON housing_complex_units (complex_id);
CREATE INDEX idx_complex_units_type    ON housing_complex_units (housing_type);

COMMENT ON TABLE  housing_complexes      IS '단지. 이름·주소·좌표처럼 평형과 무관한 사실';
COMMENT ON TABLE  housing_complex_units  IS '단지의 평형별 임대조건. 임대유형도 여기 있다 — 한 단지에 유형이 여럿일 수 있다';
COMMENT ON COLUMN housing_complexes.household_count IS '단지 총 세대수. 평형마다 어긋난 109건은 최빈값으로 통일';
COMMENT ON COLUMN housing_complex_units.deposit      IS '원 단위(bassRentGtn). 실거래가의 만원 단위와 다르다';
COMMENT ON COLUMN housing_complex_units.monthly_rent IS '원 단위(bassMtRntchrg)';

-- ─────────────── 데이터 이관 ───────────────

INSERT INTO housing_complexes
    (complex_no, name, institution, region_code, road_address, house_type,
     household_count, parking_count, completed_date, latitude, longitude, crawl_job_id, crawled_at)
SELECT complex_no,
       max(name),
       max(institution),
       max(sido_code || sigungu_code),
       max(road_address),
       max(house_type),
       -- 평형마다 어긋난 109건을 다수결로 모은다. mode() 는 NULL 을 무시한다.
       mode() WITHIN GROUP (ORDER BY household_count),
       max(parking_count),
       max(completed_date),
       max(latitude),
       max(longitude),
       max(crawl_job_id),
       max(crawled_at)
FROM public_housing_complexes
GROUP BY complex_no;

INSERT INTO housing_complex_units
    (complex_id, external_id, housing_type, style_name, exclusive_area, supply_area,
     deposit, monthly_rent)
SELECT hc.id, c.external_id, c.housing_type, c.style_name, c.exclusive_area, c.supply_area,
       c.deposit, c.monthly_rent
FROM public_housing_complexes c
JOIN housing_complexes hc ON hc.complex_no = c.complex_no;

DROP TABLE public_housing_complexes;
