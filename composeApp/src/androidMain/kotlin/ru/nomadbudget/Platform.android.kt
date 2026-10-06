package ru.nomadbudget

import android.os.Build

actual fun platformName(): String = "Android API ${Build.VERSION.SDK_INT}"

actual fun shareTextFile(fileName: String, content: String) {
    val context = ru.nomadbudget.data.local.AppContextHolder.context
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, fileName)
        putExtra(android.content.Intent.EXTRA_TEXT, content)
    }
    context.startActivity(android.content.Intent.createChooser(send, fileName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}
