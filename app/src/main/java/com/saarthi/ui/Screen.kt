package com.saarthi.ui

/**
 * The screens [MainActivity] hosts. The assist overlay and hand-back screens
 * are NOT here — they live in [AssistActivity]'s own `OverlayScreen`, a
 * separate hierarchy, because the two Activities can never navigate into
 * each other.
 *
 * `ThreadDetail` is where a tapped History row opens.
 */
sealed interface Screen {
    data object Welcome : Screen
    data object Language : Screen
    data object Voice : Screen
    data object MicPermission : Screen
    data object AccessibilityPermission : Screen
    data object AssistantPermission : Screen
    data object Promise : Screen
    data object Home : Screen
    data object History : Screen
    data class ThreadDetail(val chatId: String) : Screen
    data object Settings : Screen
}
