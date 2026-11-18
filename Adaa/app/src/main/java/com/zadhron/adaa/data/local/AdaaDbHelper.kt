package com.zadhron.adaa.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.zadhron.adaa.data.model.Appointment
import com.zadhron.adaa.data.model.AppointmentStatus
import com.zadhron.adaa.data.model.Client
import com.zadhron.adaa.data.model.Service
import com.zadhron.adaa.data.model.ServiceCategory
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AdaaDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "adaa_salon.db"
        const val DATABASE_VERSION = 1

        // Table Clients
        const val TABLE_CLIENTS = "clients"
        const val COL_CLIENT_ID = "id"
        const val COL_CLIENT_NAME = "name"
        const val COL_CLIENT_PHONE = "phone"
        const val COL_CLIENT_EMAIL = "email"
        const val COL_CLIENT_NOTES = "notes"
        const val COL_CLIENT_COLOR = "color_hex"
        const val COL_CLIENT_CREATED_AT = "created_at"

        // Table Services
        const val TABLE_SERVICES = "services"
        const val COL_SERVICE_ID = "id"
        const val COL_SERVICE_TITLE = "title"
        const val COL_SERVICE_CATEGORY = "category"
        const val COL_SERVICE_PRICE = "price"
        const val COL_SERVICE_DURATION = "duration_minutes"
        const val COL_SERVICE_COLOR = "color_hex"

        // Table Appointments
        const val TABLE_APPOINTMENTS = "appointments"
        const val COL_APPT_ID = "id"
        const val COL_APPT_CLIENT_ID = "client_id"
        const val COL_APPT_SERVICE_ID = "service_id"
        const val COL_APPT_START_TIME = "start_epoch_millis"
        const val COL_APPT_DURATION = "duration_minutes"
        const val COL_APPT_PRICE = "price"
        const val COL_APPT_STATUS = "status"
        const val COL_APPT_NOTES = "notes"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createClients = """
            CREATE TABLE $TABLE_CLIENTS (
                $COL_CLIENT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CLIENT_NAME TEXT NOT NULL,
                $COL_CLIENT_PHONE TEXT NOT NULL,
                $COL_CLIENT_EMAIL TEXT,
                $COL_CLIENT_NOTES TEXT,
                $COL_CLIENT_COLOR TEXT,
                $COL_CLIENT_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        val createServices = """
            CREATE TABLE $TABLE_SERVICES (
                $COL_SERVICE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SERVICE_TITLE TEXT NOT NULL,
                $COL_SERVICE_CATEGORY TEXT NOT NULL,
                $COL_SERVICE_PRICE REAL NOT NULL,
                $COL_SERVICE_DURATION INTEGER NOT NULL,
                $COL_SERVICE_COLOR TEXT
            )
        """.trimIndent()

        val createAppointments = """
            CREATE TABLE $TABLE_APPOINTMENTS (
                $COL_APPT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_APPT_CLIENT_ID INTEGER NOT NULL,
                $COL_APPT_SERVICE_ID INTEGER NOT NULL,
                $COL_APPT_START_TIME INTEGER NOT NULL,
                $COL_APPT_DURATION INTEGER NOT NULL,
                $COL_APPT_PRICE REAL NOT NULL,
                $COL_APPT_STATUS TEXT NOT NULL,
                $COL_APPT_NOTES TEXT,
                FOREIGN KEY ($COL_APPT_CLIENT_ID) REFERENCES $TABLE_CLIENTS($COL_CLIENT_ID),
                FOREIGN KEY ($COL_APPT_SERVICE_ID) REFERENCES $TABLE_SERVICES($COL_SERVICE_ID)
            )
        """.trimIndent()

        db.execSQL(createClients)
        db.execSQL(createServices)
        db.execSQL(createAppointments)

        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_APPOINTMENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SERVICES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CLIENTS")
        onCreate(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        // Seed default Services
        val services = listOf(
            Service(title = "Gel Manicure & Nail Art", category = ServiceCategory.NAILS, price = 55.0, durationMinutes = 75),
            Service(title = "Russian Manicure", category = ServiceCategory.NAILS, price = 70.0, durationMinutes = 90),
            Service(title = "Spa Pedicure & Scrub", category = ServiceCategory.NAILS, price = 60.0, durationMinutes = 60),
            Service(title = "Balayage & Blowdry", category = ServiceCategory.HAIR, price = 150.0, durationMinutes = 150),
            Service(title = "Precision Haircut & Style", category = ServiceCategory.HAIR, price = 65.0, durationMinutes = 45),
            Service(title = "Lash Lift & Keratin Tint", category = ServiceCategory.BROWS_LASHES, price = 80.0, durationMinutes = 60),
            Service(title = "Brow Lamination & Shaping", category = ServiceCategory.BROWS_LASHES, price = 50.0, durationMinutes = 45),
            Service(title = "Bridal Glam Makeup", category = ServiceCategory.MAKEUP, price = 120.0, durationMinutes = 90),
            Service(title = "Hydra Glow Facial", category = ServiceCategory.SKINCARE, price = 95.0, durationMinutes = 60)
        )

        for (s in services) {
            val cv = ContentValues().apply {
                put(COL_SERVICE_TITLE, s.title)
                put(COL_SERVICE_CATEGORY, s.category.name)
                put(COL_SERVICE_PRICE, s.price)
                put(COL_SERVICE_DURATION, s.durationMinutes)
                put(COL_SERVICE_COLOR, s.colorHex)
            }
            db.insert(TABLE_SERVICES, null, cv)
        }

        // Seed initial Clients
        val clients = listOf(
            Client(name = "Sophia Miller", phone = "+1 (555) 234-5678", email = "sophia.m@example.com", notes = "Prefers nude tones and almond nail shape", colorHex = "#B85D6E"),
            Client(name = "Elena Rostova", phone = "+1 (555) 345-6789", email = "elena.r@example.com", notes = "Sensitive skin, organic cuticle oils only", colorHex = "#DDA15E"),
            Client(name = "Amara Chen", phone = "+1 (555) 456-7890", email = "amara.c@example.com", notes = "Regular for Brow Lamination every 6 weeks", colorHex = "#9B89B3"),
            Client(name = "Zara Patel", phone = "+1 (555) 567-8901", email = "zara.p@example.com", notes = "Bridal trial coming up next month", colorHex = "#83A697")
        )

        val clientIds = mutableListOf<Long>()
        for (c in clients) {
            val cv = ContentValues().apply {
                put(COL_CLIENT_NAME, c.name)
                put(COL_CLIENT_PHONE, c.phone)
                put(COL_CLIENT_EMAIL, c.email)
                put(COL_CLIENT_NOTES, c.notes)
                put(COL_CLIENT_COLOR, c.colorHex)
                put(COL_CLIENT_CREATED_AT, c.createdAt)
            }
            val id = db.insert(TABLE_CLIENTS, null, cv)
            clientIds.add(id)
        }

        // Seed today's and upcoming appointments
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        val appts = listOf(
            Triple(clientIds[0], 1L, today.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli()),
            Triple(clientIds[1], 2L, today.atTime(LocalTime.of(12, 30)).atZone(zone).toInstant().toEpochMilli()),
            Triple(clientIds[2], 6L, today.atTime(LocalTime.of(15, 0)).atZone(zone).toInstant().toEpochMilli()),
            Triple(clientIds[3], 8L, today.plusDays(1).atTime(LocalTime.of(11, 0)).atZone(zone).toInstant().toEpochMilli())
        )

        val prices = listOf(55.0, 70.0, 80.0, 120.0)
        val durations = listOf(75, 90, 60, 90)

        for (i in appts.indices) {
            val (cId, sId, epoch) = appts[i]
            val cv = ContentValues().apply {
                put(COL_APPT_CLIENT_ID, cId)
                put(COL_APPT_SERVICE_ID, sId)
                put(COL_APPT_START_TIME, epoch)
                put(COL_APPT_DURATION, durations[i])
                put(COL_APPT_PRICE, prices[i])
                put(COL_APPT_STATUS, if (i == 0) AppointmentStatus.COMPLETED.name else AppointmentStatus.SCHEDULED.name)
                put(COL_APPT_NOTES, "Standard appointment booking")
            }
            db.insert(TABLE_APPOINTMENTS, null, cv)
        }
    }
}
