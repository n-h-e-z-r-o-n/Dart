package com.zadhron.adaa.data.session

import android.content.Context
import com.zadhron.adaa.data.model.AppMode
import com.zadhron.adaa.data.model.JourneyPreferences

class AndroidJourneyPreferences(context: Context) : JourneyPreferences {
    private val prefs = context.getSharedPreferences("adaa_journey", Context.MODE_PRIVATE)
    override var welcomed: Boolean
        get() = prefs.getBoolean("welcomed", false)
        set(value) { prefs.edit().putBoolean("welcomed", value).apply() }
    override var mode: AppMode
        get() = runCatching { AppMode.valueOf(prefs.getString("mode", null).orEmpty()) }.getOrDefault(AppMode.CUSTOMER)
        set(value) { prefs.edit().putString("mode", value.name).apply() }
    override fun selectedBusiness(userId: String): String? = prefs.getString("business_$userId", null)
    override fun selectBusiness(userId: String, businessId: String) {
        prefs.edit().putString("business_$userId", businessId).apply()
    }
}
