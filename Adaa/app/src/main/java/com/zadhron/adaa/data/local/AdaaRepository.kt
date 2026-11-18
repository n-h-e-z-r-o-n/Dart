package com.zadhron.adaa.data.local

import android.content.ContentValues
import android.content.Context
import com.zadhron.adaa.data.model.Appointment
import com.zadhron.adaa.data.model.AppointmentStatus
import com.zadhron.adaa.data.model.Client
import com.zadhron.adaa.data.model.FinancialMetrics
import com.zadhron.adaa.data.model.Service
import com.zadhron.adaa.data.model.ServiceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class AdaaRepository(context: Context) {

    private val dbHelper = AdaaDbHelper(context.applicationContext)

    private val _clients = MutableStateFlow<List<Client>>(emptyList())
    val clients: Flow<List<Client>> = _clients.asStateFlow()

    private val _services = MutableStateFlow<List<Service>>(emptyList())
    val services: Flow<List<Service>> = _services.asStateFlow()

    private val _appointments = MutableStateFlow<List<Appointment>>(emptyList())
    val appointments: Flow<List<Appointment>> = _appointments.asStateFlow()

    private val _metrics = MutableStateFlow(FinancialMetrics())
    val metrics: Flow<FinancialMetrics> = _metrics.asStateFlow()

    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        loadClients()
        loadServices()
        loadAppointments()
        calculateMetrics()
    }

    private fun loadClients() {
        val list = mutableListOf<Client>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM ${AdaaDbHelper.TABLE_CLIENTS} ORDER BY ${AdaaDbHelper.COL_CLIENT_NAME} ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Client(
                        id = it.getLong(it.getColumnIndexOrThrow(AdaaDbHelper.COL_CLIENT_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_CLIENT_NAME)),
                        phone = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_CLIENT_PHONE)),
                        email = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_CLIENT_EMAIL)) ?: "",
                        notes = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_CLIENT_NOTES)) ?: "",
                        colorHex = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_CLIENT_COLOR)) ?: "#B85D6E",
                        createdAt = it.getLong(it.getColumnIndexOrThrow(AdaaDbHelper.COL_CLIENT_CREATED_AT))
                    )
                )
            }
        }
        _clients.value = list
    }

    private fun loadServices() {
        val list = mutableListOf<Service>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM ${AdaaDbHelper.TABLE_SERVICES} ORDER BY ${AdaaDbHelper.COL_SERVICE_CATEGORY} ASC, ${AdaaDbHelper.COL_SERVICE_TITLE} ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                val catStr = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_SERVICE_CATEGORY))
                list.add(
                    Service(
                        id = it.getLong(it.getColumnIndexOrThrow(AdaaDbHelper.COL_SERVICE_ID)),
                        title = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_SERVICE_TITLE)),
                        category = ServiceCategory.fromString(catStr),
                        price = it.getDouble(it.getColumnIndexOrThrow(AdaaDbHelper.COL_SERVICE_PRICE)),
                        durationMinutes = it.getInt(it.getColumnIndexOrThrow(AdaaDbHelper.COL_SERVICE_DURATION)),
                        colorHex = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_SERVICE_COLOR)) ?: "#E0838A"
                    )
                )
            }
        }
        _services.value = list
    }

    private fun loadAppointments() {
        val list = mutableListOf<Appointment>()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT a.*, c.${AdaaDbHelper.COL_CLIENT_NAME}, c.${AdaaDbHelper.COL_CLIENT_PHONE},
                   s.${AdaaDbHelper.COL_SERVICE_TITLE}, s.${AdaaDbHelper.COL_SERVICE_CATEGORY}
            FROM ${AdaaDbHelper.TABLE_APPOINTMENTS} a
            LEFT JOIN ${AdaaDbHelper.TABLE_CLIENTS} c ON a.${AdaaDbHelper.COL_APPT_CLIENT_ID} = c.${AdaaDbHelper.COL_CLIENT_ID}
            LEFT JOIN ${AdaaDbHelper.TABLE_SERVICES} s ON a.${AdaaDbHelper.COL_APPT_SERVICE_ID} = s.${AdaaDbHelper.COL_SERVICE_ID}
            ORDER BY a.${AdaaDbHelper.COL_APPT_START_TIME} ASC
        """.trimIndent()

        val cursor = db.rawQuery(query, null)
        cursor.use {
            while (it.moveToNext()) {
                val statusStr = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_APPT_STATUS))
                val catStr = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_SERVICE_CATEGORY)) ?: ServiceCategory.NAILS.name
                list.add(
                    Appointment(
                        id = it.getLong(it.getColumnIndexOrThrow(AdaaDbHelper.COL_APPT_ID)),
                        clientId = it.getLong(it.getColumnIndexOrThrow(AdaaDbHelper.COL_APPT_CLIENT_ID)),
                        serviceId = it.getLong(it.getColumnIndexOrThrow(AdaaDbHelper.COL_APPT_SERVICE_ID)),
                        clientName = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_CLIENT_NAME)) ?: "Client",
                        clientPhone = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_CLIENT_PHONE)) ?: "",
                        serviceTitle = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_SERVICE_TITLE)) ?: "Service",
                        serviceCategory = ServiceCategory.fromString(catStr),
                        startEpochMillis = it.getLong(it.getColumnIndexOrThrow(AdaaDbHelper.COL_APPT_START_TIME)),
                        durationMinutes = it.getInt(it.getColumnIndexOrThrow(AdaaDbHelper.COL_APPT_DURATION)),
                        price = it.getDouble(it.getColumnIndexOrThrow(AdaaDbHelper.COL_APPT_PRICE)),
                        status = AppointmentStatus.fromString(statusStr),
                        notes = it.getString(it.getColumnIndexOrThrow(AdaaDbHelper.COL_APPT_NOTES)) ?: ""
                    )
                )
            }
        }
        _appointments.value = list
    }

    private fun calculateMetrics() {
        val appts = _appointments.value
        val today = LocalDate.now()
        val currentMonth = YearMonth.now()
        val zone = ZoneId.systemDefault()

        var todayRev = 0.0
        var monthRev = 0.0
        var totalRev = 0.0
        var todayCount = 0
        var upcoming = 0
        var completed = 0
        var cancelled = 0
        val categoryBreakdown = mutableMapOf<ServiceCategory, Double>()

        for (a in appts) {
            val date = Instant.ofEpochMilli(a.startEpochMillis).atZone(zone).toLocalDate()
            val month = YearMonth.from(date)

            if (a.status == AppointmentStatus.COMPLETED) {
                totalRev += a.price
                categoryBreakdown[a.serviceCategory] = (categoryBreakdown[a.serviceCategory] ?: 0.0) + a.price
                if (date == today) {
                    todayRev += a.price
                }
                if (month == currentMonth) {
                    monthRev += a.price
                }
                completed++
            } else if (a.status == AppointmentStatus.SCHEDULED) {
                upcoming++
            } else if (a.status == AppointmentStatus.CANCELLED) {
                cancelled++
            }

            if (date == today) {
                todayCount++
            }
        }

        _metrics.value = FinancialMetrics(
            todayRevenue = todayRev,
            monthRevenue = monthRev,
            totalRevenue = totalRev,
            todayAppointmentsCount = todayCount,
            upcomingCount = upcoming,
            completedCount = completed,
            cancelledCount = cancelled,
            totalClients = _clients.value.size,
            categoryBreakdown = categoryBreakdown
        )
    }

    suspend fun insertClient(client: Client): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(AdaaDbHelper.COL_CLIENT_NAME, client.name)
            put(AdaaDbHelper.COL_CLIENT_PHONE, client.phone)
            put(AdaaDbHelper.COL_CLIENT_EMAIL, client.email)
            put(AdaaDbHelper.COL_CLIENT_NOTES, client.notes)
            put(AdaaDbHelper.COL_CLIENT_COLOR, client.colorHex)
            put(AdaaDbHelper.COL_CLIENT_CREATED_AT, client.createdAt)
        }
        val id = db.insert(AdaaDbHelper.TABLE_CLIENTS, null, cv)
        loadClients()
        calculateMetrics()
        id
    }

    suspend fun insertService(service: Service): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(AdaaDbHelper.COL_SERVICE_TITLE, service.title)
            put(AdaaDbHelper.COL_SERVICE_CATEGORY, service.category.name)
            put(AdaaDbHelper.COL_SERVICE_PRICE, service.price)
            put(AdaaDbHelper.COL_SERVICE_DURATION, service.durationMinutes)
            put(AdaaDbHelper.COL_SERVICE_COLOR, service.colorHex)
        }
        val id = db.insert(AdaaDbHelper.TABLE_SERVICES, null, cv)
        loadServices()
        id
    }

    suspend fun insertAppointment(appointment: Appointment): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(AdaaDbHelper.COL_APPT_CLIENT_ID, appointment.clientId)
            put(AdaaDbHelper.COL_APPT_SERVICE_ID, appointment.serviceId)
            put(AdaaDbHelper.COL_APPT_START_TIME, appointment.startEpochMillis)
            put(AdaaDbHelper.COL_APPT_DURATION, appointment.durationMinutes)
            put(AdaaDbHelper.COL_APPT_PRICE, appointment.price)
            put(AdaaDbHelper.COL_APPT_STATUS, appointment.status.name)
            put(AdaaDbHelper.COL_APPT_NOTES, appointment.notes)
        }
        val id = db.insert(AdaaDbHelper.TABLE_APPOINTMENTS, null, cv)
        loadAppointments()
        calculateMetrics()
        id
    }

    suspend fun updateAppointmentStatus(id: Long, status: AppointmentStatus) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(AdaaDbHelper.COL_APPT_STATUS, status.name)
        }
        db.update(AdaaDbHelper.TABLE_APPOINTMENTS, cv, "${AdaaDbHelper.COL_APPT_ID} = ?", arrayOf(id.toString()))
        loadAppointments()
        calculateMetrics()
    }

    suspend fun deleteAppointment(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(AdaaDbHelper.TABLE_APPOINTMENTS, "${AdaaDbHelper.COL_APPT_ID} = ?", arrayOf(id.toString()))
        loadAppointments()
        calculateMetrics()
    }

    suspend fun deleteClient(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(AdaaDbHelper.TABLE_APPOINTMENTS, "${AdaaDbHelper.COL_APPT_CLIENT_ID} = ?", arrayOf(id.toString()))
        db.delete(AdaaDbHelper.TABLE_CLIENTS, "${AdaaDbHelper.COL_CLIENT_ID} = ?", arrayOf(id.toString()))
        loadClients()
        loadAppointments()
        calculateMetrics()
    }

    suspend fun deleteService(id: Long) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(AdaaDbHelper.TABLE_SERVICES, "${AdaaDbHelper.COL_SERVICE_ID} = ?", arrayOf(id.toString()))
        loadServices()
    }
}
