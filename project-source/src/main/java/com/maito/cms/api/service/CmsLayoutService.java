package com.maito.cms.api.service;

import com.maito.cms.api.dto.CreatePageCommand;
import com.maito.cms.api.dto.PageLayoutResponse;
import com.maito.cms.api.dto.PublishPageCommand;
import com.maito.cms.api.dto.UpdateSectionCommand;
import com.maito.cms.internal.domain.CmsPage;
import com.maito.cms.internal.domain.CmsSection;

import java.util.UUID;

public interface CmsLayoutService {
    PageLayoutResponse getPublishedLayout(String pageSlug, String locale);
    CmsPage createOrUpdatePage(CreatePageCommand command);
    CmsSection updateSection(UUID sectionId, UpdateSectionCommand command);
    void publishPage(String pageSlug, PublishPageCommand command);
    void evictLayoutCache(String tenantId, String pageSlug);
}
