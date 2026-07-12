package com.jack.meuholerite.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.homeLayoutDataStore by preferencesDataStore(name = "home_layout_prefs")

data class HomeLayoutPrefs(
    val sectionOrder: List<String>,
    val hiddenSections: Set<String>,
    val showAiFab: Boolean
)

object HomeLayoutStore {
    private val KEY_SECTION_ORDER = stringPreferencesKey("section_order")
    private val KEY_HIDDEN_SECTIONS = stringPreferencesKey("hidden_sections")
    private val KEY_SHOW_AI_FAB = booleanPreferencesKey("show_ai_fab")

    val defaultSectionOrder = listOf(
        "status",
        "sorteios",
        "net_pay",
        "promos",
        "next_payment",
        "smart_alerts",
        "salary_ranking",
        "finance",
        "monthly_summary",
        "quick_links",
        "ai_shortcut",
        "profile",
        "point_overview"
    )
    val defaultHiddenSections = setOf(
        "next_payment",
        "smart_alerts",
        "finance",
        "quick_links",
        "ai_shortcut",
        "profile",
        "point_overview"
    )

    fun defaultLayout(): HomeLayoutPrefs = HomeLayoutPrefs(
        sectionOrder = defaultSectionOrder,
        hiddenSections = defaultHiddenSections,
        showAiFab = true
    )

    fun layoutFlow(context: Context): Flow<HomeLayoutPrefs> {
        return context.homeLayoutDataStore.data.map { prefs ->
            val savedOrder = prefs[KEY_SECTION_ORDER]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val normalizedOrder = (savedOrder + defaultSectionOrder)
                .filter { it in defaultSectionOrder }
                .distinct()

            val hiddenSections = prefs[KEY_HIDDEN_SECTIONS]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
            HomeLayoutPrefs(
                sectionOrder = if (normalizedOrder.isEmpty()) defaultSectionOrder else normalizedOrder,
                hiddenSections = if (prefs[KEY_HIDDEN_SECTIONS] == null) defaultHiddenSections else hiddenSections,
                showAiFab = prefs[KEY_SHOW_AI_FAB] ?: true
            )
        }
    }

    suspend fun moveSection(context: Context, sectionKey: String, direction: Int) {
        context.homeLayoutDataStore.edit { prefs ->
            val currentOrder = currentOrderFromPrefs(prefs)
            val currentIndex = currentOrder.indexOf(sectionKey)
            if (currentIndex == -1) return@edit

            val targetIndex = (currentIndex + direction).coerceIn(0, currentOrder.lastIndex)
            if (targetIndex == currentIndex) return@edit

            val reordered = currentOrder.toMutableList().apply {
                removeAt(currentIndex)
                add(targetIndex, sectionKey)
            }
            prefs[KEY_SECTION_ORDER] = reordered.joinToString(",")
        }
    }

    suspend fun setSectionOrder(context: Context, orderedKeys: List<String>) {
        context.homeLayoutDataStore.edit { prefs ->
            val normalizedOrder = (orderedKeys + defaultSectionOrder)
                .filter { it in defaultSectionOrder }
                .distinct()
            prefs[KEY_SECTION_ORDER] = normalizedOrder.joinToString(",")
        }
    }

    suspend fun setSectionVisible(context: Context, sectionKey: String, visible: Boolean) {
        context.homeLayoutDataStore.edit { prefs ->
            val hidden = hiddenFromPrefs(prefs).toMutableSet()
            if (visible) hidden.remove(sectionKey) else hidden.add(sectionKey)
            prefs[KEY_HIDDEN_SECTIONS] = hidden.joinToString(",")
        }
    }

    suspend fun setShowAiFab(context: Context, show: Boolean) {
        context.homeLayoutDataStore.edit { prefs ->
            prefs[KEY_SHOW_AI_FAB] = show
        }
    }

    suspend fun reset(context: Context) {
        context.homeLayoutDataStore.edit { prefs ->
            prefs[KEY_SECTION_ORDER] = defaultSectionOrder.joinToString(",")
            prefs[KEY_HIDDEN_SECTIONS] = defaultHiddenSections.joinToString(",")
            prefs[KEY_SHOW_AI_FAB] = true
        }
    }

    private fun currentOrderFromPrefs(
        prefs: androidx.datastore.preferences.core.Preferences
    ): List<String> {
        val savedOrder = prefs[KEY_SECTION_ORDER]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return (savedOrder + defaultSectionOrder)
            .filter { it in defaultSectionOrder }
            .distinct()
    }

    private fun hiddenFromPrefs(
        prefs: androidx.datastore.preferences.core.Preferences
    ): Set<String> {
        return prefs[KEY_HIDDEN_SECTIONS]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
    }
}
