package com.zipsa.news;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsRepository extends JpaRepository<News, Long> {

    Page<News> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);

    /** 제목과 요약을 함께 검색한다. 제목만 보면 놓치는 기사가 많다. */
    @Query("""
            select n from News n
            where lower(n.title) like lower(concat('%', :keyword, '%'))
               or lower(n.summary) like lower(concat('%', :keyword, '%'))
            order by n.publishedAt desc, n.id desc
            """)
    Page<News> search(@Param("keyword") String keyword, Pageable pageable);
}
