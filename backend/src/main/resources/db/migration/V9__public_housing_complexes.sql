-- ============================================================
--  공공임대 단지 (마이홈포털 공공임대주택 단지정보)
--
--  기존 public_housings 와 왜 나누는가
--    · public_housings 는 「모집 공고」다. LH 분양임대공고문 API 에서 오고,
--      공고명·모집 시작/마감일·신청 링크를 가진다(화면 10 캘린더의 점).
--    · 이 표는 「단지」다. 마이홈포털 API 에서 오고,
--      주소·세대수·전용면적·보증금·월세를 가진다(지도 마커와 임대조건).
--
--  둘은 공통 키가 없어 안전하게 조인할 수 없다. 억지로 한 표에 합치면
--  공고에는 주소가, 단지에는 마감일이 영원히 비어 있는 행이 생긴다.
--
--  한 단지가 평형(styleNm)마다 임대조건이 달라 행이 여러 개 나온다.
--  지도 마커는 API 에서 단지 단위로 묶는다.
-- ============================================================

CREATE TABLE public_housing_complexes (
    id             BIGSERIAL     PRIMARY KEY,
    external_id    VARCHAR(120)  NOT NULL UNIQUE,
    complex_no     BIGINT        NOT NULL,
    name           VARCHAR(255)  NOT NULL,
    institution    VARCHAR(100),
    sido_code      VARCHAR(2)    NOT NULL,
    sido_name      VARCHAR(50),
    sigungu_code   VARCHAR(3)    NOT NULL,
    sigungu_name   VARCHAR(50),
    road_address   VARCHAR(255),
    housing_type   VARCHAR(30)   NOT NULL,
    house_type     VARCHAR(30),
    style_name     VARCHAR(50),
    household_count INT,
    exclusive_area  DECIMAL(8, 3),
    supply_area     DECIMAL(8, 3),
    deposit         BIGINT,
    monthly_rent    BIGINT,
    parking_count   INT,
    completed_date  VARCHAR(8),
    latitude        DECIMAL(10, 7),
    longitude       DECIMAL(10, 7),
    crawl_job_id    BIGINT       REFERENCES crawl_jobs (id),
    crawled_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_phc_region ON public_housing_complexes (sido_code, sigungu_code);
CREATE INDEX idx_phc_type   ON public_housing_complexes (housing_type);
CREATE INDEX idx_phc_complex ON public_housing_complexes (complex_no);

COMMENT ON TABLE  public_housing_complexes IS '마이홈포털 단지정보. 한 단지가 평형마다 여러 행';
COMMENT ON COLUMN public_housing_complexes.external_id  IS 'MYHOME-{hsmpSn}-{styleNm}. 크롤러가 이 값으로 upsert';
COMMENT ON COLUMN public_housing_complexes.sigungu_code IS '⚠️ 법정동코드 5자리가 아니라 뒤 3자리(강남구=680). API 가 그렇게 요구한다';
COMMENT ON COLUMN public_housing_complexes.deposit      IS '원 단위(bassRentGtn). 실거래가 테이블의 만원 단위와 다르니 주의';
COMMENT ON COLUMN public_housing_complexes.monthly_rent IS '원 단위(bassMtRntchrg)';
