package com.zadhron.adaa.ui.preview

import com.zadhron.adaa.data.model.Appointment
import com.zadhron.adaa.data.model.AppointmentStatus
import com.zadhron.adaa.data.model.Client
import com.zadhron.adaa.data.model.FinancialMetrics
import com.zadhron.adaa.data.model.Service
import com.zadhron.adaa.data.model.ServiceCategory
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object PreviewData {
    val sampleClients = listOf(
        Client(
            id = 1,
            name = "Sophia Miller",
            phone = "+1 (555) 234-5678",
            email = "sophia.m@example.com",
            notes = "Prefers almond nail shape & nude tones",
            colorHex = "#B85D6E"
        ),
        Client(
            id = 2,
            name = "Elena Rostova",
            phone = "+1 (555) 345-6789",
            email = "elena.r@example.com",
            notes = "Organic cuticle oils only, sensitive skin",
            colorHex = "#DDA15E"
        ),
        Client(
            id = 3,
            name = "Amara Chen",
            phone = "+1 (555) 456-7890",
            email = "amara.c@example.com",
            notes = "Regular for Brow Lamination every 6 weeks",
            colorHex = "#9B89B3"
        ),
        Client(
            id = 4,
            name = "Zara Patel",
            phone = "+1 (555) 567-8901",
            email = "zara.p@example.com",
            notes = "Bridal glam trial booked for next month",
            colorHex = "#83A697"
        )
    )

    val sampleServices = listOf(
        Service(id = 1, title = "Gel Manicure & Nail Art", category = ServiceCategory.NAILS, price = 55.0, durationMinutes = 75),
        Service(id = 2, title = "Russian Manicure", category = ServiceCategory.NAILS, price = 70.0, durationMinutes = 90),
        Service(id = 3, title = "Spa Pedicure & Scrub", category = ServiceCategory.NAILS, price = 60.0, durationMinutes = 60),
        Service(id = 4, title = "Balayage & Blowdry", category = ServiceCategory.HAIR, price = 150.0, durationMinutes = 150),
        Service(id = 5, title = "Precision Haircut & Style", category = ServiceCategory.HAIR, price = 65.0, durationMinutes = 45),
        Service(id = 6, title = "Lash Lift & Keratin Tint", category = ServiceCategory.BROWS_LASHES, price = 80.0, durationMinutes = 60),
        Service(id = 7, title = "Brow Lamination & Shaping", category = ServiceCategory.BROWS_LASHES, price = 50.0, durationMinutes = 45),
        Service(id = 8, title = "Bridal Glam Makeup", category = ServiceCategory.MAKEUP, price = 120.0, durationMinutes = 90),
        Service(id = 9, title = "Hydra Glow Facial", category = ServiceCategory.SKINCARE, price = 95.0, durationMinutes = 60)
    )

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    val sampleAppointments = listOf(
        Appointment(
            id = 1,
            clientId = 1,
            serviceId = 1,
            clientName = "Sophia Miller",
            clientPhone = "+1 (555) 234-5678",
            serviceTitle = "Gel Manicure & Nail Art",
            serviceCategory = ServiceCategory.NAILS,
            startEpochMillis = today.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli(),
            durationMinutes = 75,
            price = 55.0,
            status = AppointmentStatus.COMPLETED,
            notes = "Rose quartz finish"
        ),
        Appointment(
            id = 2,
            clientId = 2,
            serviceId = 2,
            clientName = "Elena Rostova",
            clientPhone = "+1 (555) 345-6789",
            serviceTitle = "Russian Manicure",
            serviceCategory = ServiceCategory.NAILS,
            startEpochMillis = today.atTime(LocalTime.of(12, 30)).atZone(zone).toInstant().toEpochMilli(),
            durationMinutes = 90,
            price = 70.0,
            status = AppointmentStatus.SCHEDULED,
            notes = "Soft square shape"
        ),
        Appointment(
            id = 3,
            clientId = 3,
            serviceId = 6,
            clientName = "Amara Chen",
            clientPhone = "+1 (555) 456-7890",
            serviceTitle = "Lash Lift & Keratin Tint",
            serviceCategory = ServiceCategory.BROWS_LASHES,
            startEpochMillis = today.atTime(LocalTime.of(15, 0)).atZone(zone).toInstant().toEpochMilli(),
            durationMinutes = 60,
            price = 80.0,
            status = AppointmentStatus.SCHEDULED,
            notes = "Deep black tint"
        )
    )

    val sampleMetrics = FinancialMetrics(
        todayRevenue = 125.0,
        monthRevenue = 1450.0,
        totalRevenue = 4850.0,
        todayAppointmentsCount = 3,
        upcomingCount = 5,
        completedCount = 28,
        cancelledCount = 2,
        totalClients = 4,
        categoryBreakdown = mapOf(
            ServiceCategory.NAILS to 2200.0,
            ServiceCategory.HAIR to 1350.0,
            ServiceCategory.BROWS_LASHES to 850.0,
            ServiceCategory.MAKEUP to 450.0
        )
    )
}
