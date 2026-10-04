package com.maito.cms.api.dto;

import java.io.Serializable;
import java.util.Map;

public record ThemeTokensDto(
    String themeName,
    Map<String, Object> tokens
) implements Serializable {}
