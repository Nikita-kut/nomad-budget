package ru.nomadbudget

private fun currentHash(): String = js("window.location.hash || ''")

private fun clearHash() {
    js("history.replaceState(null, '', window.location.pathname + window.location.search)")
}

actual fun consumeLaunchLink(): LaunchLink? {
    val link = parseLaunchFragment(currentHash()) ?: return null
    clearHash()
    return link
}
