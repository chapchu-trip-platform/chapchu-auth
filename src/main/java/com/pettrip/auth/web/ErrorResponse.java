package com.pettrip.auth.web;

/** chapchu-api의 오류 응답과 같은 형태로 맞춘다. 프론트가 두 서버를 구분해서 다룰 이유가 없다. */
public record ErrorResponse(String code, String message) {}
