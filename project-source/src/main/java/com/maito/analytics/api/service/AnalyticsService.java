package com.maito.analytics.api.service;

import com.maito.analytics.api.dto.DailySalesPointDto;
import com.maito.analytics.api.dto.DashboardKpiResponse;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsService {
    DashboardKpiResponse getExecutiveKpis(LocalDate startDate, LocalDate endDate);
    List<DailySalesPointDto> getDailySalesTrend(LocalDate startDate, LocalDate endDate);
}
