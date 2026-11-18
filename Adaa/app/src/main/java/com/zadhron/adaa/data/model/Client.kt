package com.zadhron.adaa.data.model

data class Client(
    val id: Long = 0,
    val name: String,
    val phone: String,
    val email: String = "",
    val notes: String = "",
    val colorHex: String = "#B85D6E",
    val createdAt: Long = System.currentTimeMillis()
) {
    val initials: String
        get() {
            val parts = name.trim().split(" ")
            return if (parts.size >= 2) {
                "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
            } else {
                name.take(2).uppercase()
            }
        }
}
