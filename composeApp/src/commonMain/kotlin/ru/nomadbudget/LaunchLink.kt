package ru.nomadbudget

sealed interface LaunchLink {
    data class Invite(val code: String) : LaunchLink
    data class Reset(val tokenHash: String) : LaunchLink
}

private val CODE = Regex("^[A-Za-z0-9_-]{16,128}$")

fun parseLaunchFragment(fragment: String): LaunchLink? {
    val params = fragment.removePrefix("#").split('&').mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
    }.toMap()
    params["invite"]?.takeIf(CODE::matches)?.let { return LaunchLink.Invite(it) }
    params["reset"]?.takeIf(CODE::matches)?.let { return LaunchLink.Reset(it) }
    return null
}

expect fun consumeLaunchLink(): LaunchLink?
