package com.saarthi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.saarthi.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * "9:12" today, "Yesterday", "Mon" within the last week, else a short date —
 * matches the design's History/Home row timestamps exactly. `Locale.getDefault()`
 * tracks the app's chosen language (see `withLocale`/`LocalizedContent`), so "Mon"
 * and the numeral shapes already localize themselves; only "Yesterday" is a literal.
 */
@Composable
fun formatRelativeTime(timestampMillis: Long, now: Long = System.currentTimeMillis()): String {
    val nowCal = Calendar.getInstance().apply { timeInMillis = now }
    val thenCal = Calendar.getInstance().apply { timeInMillis = timestampMillis }

    val dayDiff = daysBetween(nowCal, thenCal)
    return when {
        dayDiff == 0 -> SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(timestampMillis))
        dayDiff == 1 -> stringResource(R.string.yesterday)
        dayDiff in 2..6 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestampMillis))
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestampMillis))
    }
}

private fun daysBetween(a: Calendar, b: Calendar): Int {
    val start = a.clone() as Calendar
    start.set(Calendar.HOUR_OF_DAY, 0)
    start.set(Calendar.MINUTE, 0)
    start.set(Calendar.SECOND, 0)
    start.set(Calendar.MILLISECOND, 0)
    val end = b.clone() as Calendar
    end.set(Calendar.HOUR_OF_DAY, 0)
    end.set(Calendar.MINUTE, 0)
    end.set(Calendar.SECOND, 0)
    end.set(Calendar.MILLISECOND, 0)
    val diffMillis = start.timeInMillis - end.timeInMillis
    return (diffMillis / (24L * 60 * 60 * 1000)).toInt()
}
