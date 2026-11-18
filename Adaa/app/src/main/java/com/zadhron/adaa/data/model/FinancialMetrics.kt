package com.zadhron.adaa.data.model

data class FinancialMetrics(
    val todayRevenue: Double = 0.0,
    val monthRevenue: Double = 0.0,
    val totalRevenue: Double = 0.0,
    val todayAppointmentsCount: Int = 0,
    val upcomingCount: Int = 0,
    val completedCount: Int = 0,
    val cancelledCount: Int = 0,
    val totalClients: Int = 0,
    val categoryBreakdown: Map<ServiceCategory, Double> = emptyMap()
)
