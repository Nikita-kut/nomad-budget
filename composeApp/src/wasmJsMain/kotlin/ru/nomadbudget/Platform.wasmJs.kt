package ru.nomadbudget

actual fun platformName(): String = "Веб-версия"

private fun downloadText(name: String, content: String) {
    js("var b = new Blob(['\\ufeff' + content], {type: 'text/csv;charset=utf-8'}); var u = URL.createObjectURL(b); var a = document.createElement('a'); a.href = u; a.download = name; document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(u);")
}

actual fun shareTextFile(fileName: String, content: String) = downloadText(fileName, content)
