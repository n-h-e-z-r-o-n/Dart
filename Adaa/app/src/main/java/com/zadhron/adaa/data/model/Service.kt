package com.zadhron.adaa.data.model

enum class ServiceCategory(val displayName: String, val defaultColorHex: String) {
    NAILS("Nails", "#E0838A"),
    HAIR("Hair Styling", "#DDA15E"),
    BROWS_LASHES("Brows & Lashes", "#9B89B3"),
    MAKEUP("Makeup", "#E76F51"),
    SKINCARE("Skincare & Facial", "#83A697"),
    OTHER("Other", "#6C757D");

    companion object {
        fun fromString(value: String): ServiceCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
        }
    }
}

data class Service(
    val id: Long = 0,
    val title: String,
    val category: ServiceCategory,
    val price: Double,
    val durationMinutes: Int,
    val colorHex: String = category.defaultColorHex
)
