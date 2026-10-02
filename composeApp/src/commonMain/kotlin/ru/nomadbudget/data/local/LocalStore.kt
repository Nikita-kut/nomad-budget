package ru.nomadbudget.data.local

expect class LocalStore() {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}
