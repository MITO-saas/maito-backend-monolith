package com.maito.analytics.internal.controller;

import com.maito.analytics.api.dto.DailySalesPointDto;
import com.maito.analytics.api.dto.DashboardKpiResponse;
import com.maito.analytics.api.service.AnalyticsService;
import com.maito.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Admin Commercial Analytics", description = "Executive merchant KPI dashboards, revenue aggregation, and sales velocity trends")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping({"/kpis", "/dashboard"})
    @Operation(summary = "Get high-level commercial dashboard KPIs (GMV, AOV, top-selling SKUs, stock risks)")
    public ResponseEntity<ApiResponse<DashboardKpiResponse>> getExecutiveKpis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        DashboardKpiResponse kpis = analyticsService.getExecutiveKpis(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(kpis));
    }

    @GetMapping("/sales-trend")
    @Operation(summary = "Get daily order count and sales revenue trend")
    public ResponseEntity<ApiResponse<List<DailySalesPointDto>>> getDailySalesTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<DailySalesPointDto> trend = analyticsService.getDailySalesTrend(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(trend));
    }
}
