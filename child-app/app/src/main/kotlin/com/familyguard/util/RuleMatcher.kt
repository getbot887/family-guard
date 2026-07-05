package com.familyguard.util

import java.util.*

class RuleMatcher(private val storage: RuleStorage) {
    private var rules: List<BlockRule> = emptyList()

    fun update(r: List<BlockRule>) { rules = r.sortedByDescending { it.priority } }

    fun shouldBlock(pkg: String): Boolean {
        // 按优先级从高到低判断
        for (rule in rules.sortedByDescending { it.priority }) {
            if (!rule.isActive || !inTimeSlot(rule)) continue
            if (rule.mode == "whitelist") {
                // 白名单模式：在列表中的应用不拦截，其余全部拦截
                if (!rule.blockedApps.contains(pkg)) return true
            } else {
                // 黑名单模式（默认）：在列表中的应用拦截
                if (rule.blockedApps.contains(pkg)) return true
            }
        }
        return false
    }

    private fun inTimeSlot(rule: BlockRule): Boolean {
        if (rule.schedules.isEmpty()) return true
        val now = Calendar.getInstance()
        val day = toIsoDayOfWeek(now.get(Calendar.DAY_OF_WEEK))
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return rule.schedules.any { s ->
            if (!s.daysOfWeek.contains(day)) return@any false
            val start = parse(s.startTime)
            val end = parse(s.endTime)
            if (start <= end) current in start..end
            else current >= start || current <= end
        }
    }

    /** Calendar.SUNDAY=1 → 7, Calendar.MONDAY=2 → 1, ... */
    private fun toIsoDayOfWeek(calendarDay: Int): Int = when (calendarDay) {
        Calendar.SUNDAY -> 7
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> calendarDay
    }

    private fun parse(t: String): Int {
        val p = t.split(":"); return (p.getOrNull(0)?.toIntOrNull()?:0) * 60 + (p.getOrNull(1)?.toIntOrNull()?:0)
    }
}

data class BlockRule(val id: Int, val name: String, val isActive: Boolean, val blockedApps: List<String>, val schedules: List<TimeSlot>, val mode: String = "blacklist", val priority: Int = 0)
data class TimeSlot(val daysOfWeek: List<Int>, val startTime: String, val endTime: String)
