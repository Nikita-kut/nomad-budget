package ru.nomadbudget.presentation.update

actual val updatesByReload: Boolean = true

actual fun reloadApp() {
    js("window.location.reload()")
}
