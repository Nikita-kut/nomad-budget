package ru.nomadbudget

import android.os.Build

actual fun platformName(): String = "Android API ${Build.VERSION.SDK_INT}"
