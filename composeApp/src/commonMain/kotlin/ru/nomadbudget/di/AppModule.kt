package ru.nomadbudget.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.nomadbudget.presentation.home.HomeViewModel
import ru.nomadbudget.data.repository.AccountRepositoryImpl
import ru.nomadbudget.data.repository.AuthRepositoryImpl
import ru.nomadbudget.data.repository.BudgetRepositoryImpl
import ru.nomadbudget.data.repository.CategoryRepositoryImpl
import ru.nomadbudget.data.repository.ExchangeRateRepositoryImpl
import ru.nomadbudget.data.repository.PeriodRepositoryImpl
import ru.nomadbudget.data.repository.TransactionRepositoryImpl
import ru.nomadbudget.data.supabase.SupabaseClientFactory
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.TransactionRepository

val appModule = module {
    single { SupabaseClientFactory.create() }

    single<AuthRepository> { AuthRepositoryImpl(get()) }
    single<AccountRepository> { AccountRepositoryImpl(get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get()) }
    single<PeriodRepository> { PeriodRepositoryImpl(get()) }
    single<TransactionRepository> { TransactionRepositoryImpl(get()) }
    single<BudgetRepository> { BudgetRepositoryImpl(get()) }
    single<ExchangeRateRepository> { ExchangeRateRepositoryImpl(get()) }

    viewModelOf(::HomeViewModel)
}
