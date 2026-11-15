package ru.nomadbudget.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.features.AppModule
import ru.nomadbudget.features.Feature
import ru.nomadbudget.features.FeatureState
import ru.nomadbudget.features.enabledModules
import ru.nomadbudget.features.withModule
import ru.nomadbudget.presentation.components.PrimaryButton
import ru.nomadbudget.presentation.components.SecondaryButton
import ru.nomadbudget.presentation.format.MoneyFormat
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme

enum class IntroArt { WELCOME, ENVELOPES, SAVINGS, CUSHION, DEBTS, FUNDS, CURRENCIES, MORE, HELP }

data class IntroPage(val art: IntroArt, val title: String, val text: String, val modules: List<AppModule> = emptyList())

val INTRO_PAGES = listOf(
    IntroPage(IntroArt.WELCOME, "Знаешь, сколько можно потратить сегодня", "Nomad Budget — личный бюджет для тех, кто живёт в одной валюте, а получает в другой, или просто хочет спокойно дожить до следующего дохода."),
    IntroPage(IntroArt.ENVELOPES, "Доход — по конвертам", "Решаешь заранее, сколько уйдёт на еду, жильё и всё остальное. Главная цифра каждого дня — сколько ещё можно потратить, не залезая в чужой конверт."),
    IntroPage(IntroArt.SAVINGS, "Сначала заплати себе", "Часть дохода откладываешь сразу, в начале месяца, — это «Себе». Остальное делишь по конвертам. Так подушка растёт сама.", listOf(AppModule.CUSHION)),
    IntroPage(IntroArt.CUSHION, "Подушка и капитал", "Подушка — запас на чёрный день в месяцах твоих расходов. Капитал — все счета минус долги, с прогнозом и целями.", listOf(AppModule.CAPITAL)),
    IntroPage(IntroArt.DEBTS, "Кредиты без сюрпризов", "Обязательный платёж сам попадает в план месяца. Досрочку можно запланировать — с карты или из подушки.", listOf(AppModule.DEBTS)),
    IntroPage(IntroArt.FUNDS, "Копилки на крупное", "Аренда раз в квартал, отпуск, техника. Каждый месяц откладываешь понемногу — и платишь из копилки, не трогая конверты.", listOf(AppModule.FUNDS)),
    IntroPage(IntroArt.CURRENCIES, "Несколько валют", "Счета в разных валютах, обмены по реальному курсу. Бот подскажет, когда менять выгодно.", listOf(AppModule.CURRENCIES, AppModule.BANK_WATCH)),
    IntroPage(IntroArt.MORE, "Графики и «Входящие»", "Как менялись доходы, траты и подушка по месяцам. А трату на бегу можно записать одной строкой и разобрать потом.", listOf(AppModule.CHARTS, AppModule.INBOX)),
    IntroPage(IntroArt.HELP, "Если что-то не так", "Кнопка «?» есть на каждом экране: нажми на место, где что-то не так, — отправится обращение со скриншотом, суммы на нём скрыты. Твои счета и операции в приложении видишь только ты. Выгрузить всё в CSV или удалить учётку можно в любой момент в настройках."),
)

object IntroTags {
    const val NEXT = "intro.next"
    const val BACK = "intro.back"
    const val SKIP = "intro.skip"
    fun module(module: AppModule) = "intro.module.${module.key}"
}

fun offeredModules(page: IntroPage, features: FeatureState): List<AppModule> =
    page.modules.filter { module -> Feature.entries.any { it.module == module && features.stageAllows(it) } }

@Composable
fun IntroScreen(
    state: HomeState,
    features: FeatureState,
    firstTime: Boolean,
    onSetModules: (Set<String>, Set<String>) -> Unit,
    onDone: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    val page = INTRO_PAGES[index]
    val last = index == INTRO_PAGES.lastIndex
    val enabled = enabledModules(state.settings.modulesOff)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 520.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        INTRO_PAGES.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (i == index) 18.dp else 6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    }
                    if (!last) TextButton(onClick = onDone, modifier = Modifier.testTag(IntroTags.SKIP)) { Text(if (firstTime) "Пропустить" else "Закрыть") }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    IntroIllustration(page.art, state)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(page.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    offeredModules(page, features).forEach { module ->
                        val on = module in enabled
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(module.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        (if (on) "включён" else "выключен") + " · " + module.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = on,
                                    onCheckedChange = { onSetModules(withModule(state.settings.modulesOff, module, it), state.settings.blocksOff) },
                                    modifier = Modifier.testTag(IntroTags.module(module)),
                                )
                            }
                        }
                    }
                    if (last && !firstTime) {
                        Text("Модули можно поменять в любой момент: «Ещё» → «Модули».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (index > 0) SecondaryButton(onClick = { index-- }, modifier = Modifier.testTag(IntroTags.BACK)) { Text("Назад") }
                    PrimaryButton(
                        onClick = { if (last) onDone() else index++ },
                        modifier = Modifier.weight(1f).testTag(IntroTags.NEXT),
                    ) {
                        Text(
                            when {
                                index == 0 -> "Как это работает"
                                last && firstTime -> "Перейти к настройке"
                                last -> "Готово"
                                else -> "Дальше"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroIllustration(art: IntroArt, state: HomeState) {
    val colors = AppTheme.colors
    val scheme = MaterialTheme.colorScheme
    fun money(units: Long): String = MoneyFormat.format(Money(units * 100, state.base), false)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (art) {
                IntroArt.WELCOME -> {
                    listOf(
                        Triple("1", "Конверты на месяц", "Видно, сколько можно потратить сегодня") to scheme.primary,
                        Triple("2", "Сначала себе", "Подушка растёт, даже когда кажется, что не из чего") to colors.save,
                        Triple("3", "Только нужное", "Кредиты, копилки, валюты — включай модули, которые нужны тебе") to colors.good,
                    ).forEach { (texts, color) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                Text(texts.first, color = color, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text(texts.second, fontWeight = FontWeight.SemiBold)
                                Text(texts.third, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                IntroArt.ENVELOPES -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(scheme.primary, colors.save))).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Можно ещё потратить", color = scheme.onPrimary.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                        Text(money(24_492), color = scheme.onPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        Bar(0.62f, scheme.onPrimary, scheme.onPrimary.copy(alpha = 0.3f))
                        Text("≈ ${money(1_884)} в день · 13 дней до конца месяца", color = scheme.onPrimary.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple("Еда", 0.62f, scheme.primary), Triple("Транспорт", 0.48f, scheme.primary), Triple("Кафе", 1f, colors.warning)).forEach { (name, share, color) ->
                            Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(name, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                                Bar(share, color, scheme.outlineVariant)
                            }
                        }
                    }
                }
                IntroArt.SAVINGS -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Доход пришёл", modifier = Modifier.weight(1f), color = scheme.onSurfaceVariant)
                        Text(money(230_000), fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.weight(17f).fillMaxSize().background(colors.save))
                        Box(Modifier.weight(13f).fillMaxSize().background(scheme.outline))
                        Box(Modifier.weight(52f).fillMaxSize().background(scheme.primary))
                        Box(Modifier.weight(18f).fillMaxSize().background(colors.good))
                    }
                    Text("«Себе» — первым, потом кредиты, конверты и свободное", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                IntroArt.CUSHION -> {
                    Text("Подушка · цель — 6 месяцев расходов", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Text("4,2 мес.", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Bar(0.7f, colors.save, scheme.surfaceVariant)
                    Text("Через 12 месяцев по плану — 6 месяцев", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                IntroArt.DEBTS -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Обязательно в месяц", modifier = Modifier.weight(1f), color = scheme.onSurfaceVariant)
                        Text(money(27_400), fontWeight = FontWeight.SemiBold)
                    }
                    MiniRow("Ипотека · 15-го", "оплачено", colors.good)
                    MiniRow("Рассрочка · 25-го", "через 10 дней", scheme.onSurfaceVariant)
                }
                IntroArt.FUNDS -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Аренда" to 60_000L, "Отпуск" to 35_000L).forEach { (name, amount) ->
                        Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(scheme.surfaceVariant).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(name, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                            Text(money(amount), fontWeight = FontWeight.SemiBold)
                            Text("+${money(amount / 3)} в месяц", style = MaterialTheme.typography.labelSmall, color = colors.warning)
                        }
                    }
                }
                IntroArt.CURRENCIES -> {
                    MiniRow("Карта", "1 250 $", scheme.onSurface)
                    MiniRow("Наличные", money(48_000), scheme.onSurface)
                    Text("Итоги — в одной основной валюте, по курсу на дату операции", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.good))
                        Text("Бот: курс обмена сейчас выгодный", style = MaterialTheme.typography.bodySmall)
                    }
                }
                IntroArt.MORE -> {
                    Row(modifier = Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        listOf(0.42f, 0.58f, 0.49f, 0.73f, 0.64f, 0.82f).forEachIndexed { i, h ->
                            Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(if (i == 5) scheme.primary else scheme.primaryContainer))
                        }
                    }
                    MiniRow("«кофе 300»", "во «Входящих»", scheme.onSurfaceVariant)
                }
                IntroArt.HELP -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Text("?", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    }
                    Text("Отметь место — и напиши, что не так. Ответ увидишь в «Ещё» → «Мои обращения».", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Bar(share: Float, color: Color, track: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
        Box(modifier = Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
private fun MiniRow(left: String, right: String, rightColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(left, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(right, style = MaterialTheme.typography.bodySmall, color = rightColor)
    }
}

