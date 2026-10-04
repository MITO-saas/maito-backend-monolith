package com.maito.cms.api.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record PageLayoutResponse(
    String pageSlug,
    String title,
    Map<String, Object> seo,
    ThemeTokensDto theme,
    List<SectionWidgetDto> sections
) implements Serializable {}
