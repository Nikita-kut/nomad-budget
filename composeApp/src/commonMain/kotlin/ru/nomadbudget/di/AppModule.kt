package ru.nomadbudget.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.nomadbudget.data.local.LocalStore
import ru.nomadbudget.data.local.OfflineCache
import ru.nomadbudget.data.local.PendingQueue
import ru.nomadbudget.data.repository.AccountRepositoryImpl
import ru.nomadbudget.data.repository.AuthRepositoryImpl
import ru.nomadbudget.data.repository.BalanceCheckRepositoryImpl
import ru.nomadbudget.data.repository.BudgetRepositoryImpl
import ru.nomadbudget.data.repository.CategoryRepositoryImpl
import ru.nomadbudget.data.repository.CurrencyRepositoryImpl
import ru.nomadbudget.data.repository.DebtRepositoryImpl
import ru.nomadbudget.data.repository.ExchangeRateRepositoryImpl
import ru.nomadbudget.data.repository.PeriodRepositoryImpl
import ru.nomadbudget.data.repository.TransactionRepositoryImpl
import ru.nomadbudget.data.supabase.SupabaseClientFactory
import ru.nomadbudget.domain.repository.AccountRepository
import ru.nomadbudget.domain.repository.AuthRepository
import ru.nomadbudget.domain.repository.BalanceCheckRepository
import ru.nomadbudget.domain.repository.BudgetRepository
import ru.nomadbudget.domain.repository.CategoryRepository
import ru.nomadbudget.domain.repository.CurrencyRepository
import ru.nomadbudget.domain.repository.DebtRepository
import ru.nomadbudget.domain.repository.ExchangeRateRepository
import ru.nomadbudget.domain.repository.PeriodRepository
import ru.nomadbudget.domain.repository.TransactionRepository
import ru.nomadbudget.presentation.home.HomeViewModel

val appModule = module {
    single { SupabaseClientFactory.create() }
    single { LocalStore() }
    single { OfflineCache(get()) }
    single { PendingQueue(get()) }

    single<AuthRepository> { AuthRepositoryImpl(get()) }
    single<CurrencyRepository> { CurrencyRepositoryImpl(get(), get()) }
    single<AccountRepository> { AccountRepositoryImpl(get(), get(), get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get(), get()) }
    single<PeriodRepository> { PeriodRepositoryImpl(get(), get()) }
    single<TransactionRepository> { TransactionRepositoryImpl(get(), get(), get()) }
    single<BudgetRepository> { BudgetRepositoryImpl(get(), get()) }
    single<ExchangeRateRepository> { ExchangeRateRepositoryImpl(get(), get(), get()) }
    single<BalanceCheckRepository> { BalanceCheckRepositoryImpl(get(), get()) }
    single<DebtRepository> { DebtRepositoryImpl(get(), get(), get()) }

    viewModelOf(::HomeViewModel)
}
