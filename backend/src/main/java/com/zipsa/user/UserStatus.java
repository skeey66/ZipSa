package com.zipsa.user;

/**
 * 명세 v4 §1.3.
 * SUSPENDED 는 관리자가 정지시킨 상태로, 로그인은 막되 작성한 글은 남긴다.
 */
public enum UserStatus { ACTIVE, SUSPENDED, DELETED }
