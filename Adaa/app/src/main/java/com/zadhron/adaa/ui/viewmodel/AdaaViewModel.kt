package com.zadhron.adaa.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zadhron.adaa.data.local.AdaaRepository
import com.zadhron.adaa.data.model.Appointment
import com.zadhron.adaa.data.model.AppointmentStatus
import com.zadhron.adaa.data.model.Client
import com.zadhron.adaa.data.model.FinancialMetrics
import com.zadhron.adaa.data.model.Service
import com.zadhron.adaa.data.model.ServiceCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AdaaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AdaaRepository(application)

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _clientSearchQuery = MutableStateFlow("")
    val clientSearchQuery: StateFlow<String> = _clientSearchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<ServiceCategory?>(null)
    val selectedCategoryFilter: StateFlow<ServiceCategory?> = _selectedCategoryFilter.asStateFlow()

    // Dialog control states
    val isBookingDialogVisible = MutableStateFlow(false)
    val isClientDialogVisible = MutableStateFlow(false)
    val isServiceDialogVisible = MutableStateFlow(false)

    val clients: StateFlow<List<Client>> = combine(
        repository.clients,
        _clientSearchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.phone.contains(query, ignoreCase = true) ||
                it.notes.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val services: StateFlow<List<Service>> = combine(
        repository.services,
        _selectedCategoryFilter
    ) { list, catFilter ->
        if (catFilter == null) list else list.filter { it.category == catFilter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawServices: StateFlow<List<Service>> = repository.services
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawClients: StateFlow<List<Client>> = repository.clients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAppointments: StateFlow<List<Appointment>> = repository.appointments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appointmentsForSelectedDate: StateFlow<List<Appointment>> = combine(
        repository.appointments,
        _selectedDate
    ) { list, date ->
        val zone = ZoneId.systemDefault()
        list.filter {
            val apptDate = Instant.ofEpochMilli(it.startEpochMillis).atZone(zone).toLocalDate()
            apptDate == date
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val metrics: StateFlow<FinancialMetrics> = repository.metrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialMetrics())

    init {
        viewModelScope.launch {
            repository.refreshAll()
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun setClientSearchQuery(query: String) {
        _clientSearchQuery.value = query
    }

    fun setCategoryFilter(category: ServiceCategory?) {
        _selectedCategoryFilter.value = category
    }

    fun bookAppointment(
        client: Client,
        service: Service,
        startEpochMillis: Long,
        notes: String = ""
    ) {
        viewModelScope.launch {
            repository.insertAppointment(
                Appointment(
                    clientId = client.id,
                    serviceId = service.id,
                    clientName = client.name,
                    clientPhone = client.phone,
                    serviceTitle = service.title,
                    serviceCategory = service.category,
                    startEpochMillis = startEpochMillis,
                    durationMinutes = service.durationMinutes,
                    price = service.price,
                    status = AppointmentStatus.SCHEDULED,
                    notes = notes
                )
            )
            isBookingDialogVisible.value = false
        }
    }

    fun updateAppointmentStatus(appointmentId: Long, status: AppointmentStatus) {
        viewModelScope.launch {
            repository.updateAppointmentStatus(appointmentId, status)
        }
    }

    fun deleteAppointment(appointmentId: Long) {
        viewModelScope.launch {
            repository.deleteAppointment(appointmentId)
        }
    }

    fun addClient(name: String, phone: String, email: String, notes: String, colorHex: String) {
        viewModelScope.launch {
            repository.insertClient(
                Client(
                    name = name,
                    phone = phone,
                    email = email,
                    notes = notes,
                    colorHex = colorHex
                )
            )
            isClientDialogVisible.value = false
        }
    }

    fun deleteClient(clientId: Long) {
        viewModelScope.launch {
            repository.deleteClient(clientId)
        }
    }

    fun addService(title: String, category: ServiceCategory, price: Double, durationMinutes: Int) {
        viewModelScope.launch {
            repository.insertService(
                Service(
                    title = title,
                    category = category,
                    price = price,
                    durationMinutes = durationMinutes,
                    colorHex = category.defaultColorHex
                )
            )
            isServiceDialogVisible.value = false
        }
    }

    fun deleteService(serviceId: Long) {
        viewModelScope.launch {
            repository.deleteService(serviceId)
        }
    }
}
