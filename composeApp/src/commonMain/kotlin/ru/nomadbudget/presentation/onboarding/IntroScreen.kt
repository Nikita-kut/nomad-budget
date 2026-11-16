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
import androidx.compose.runtime.mutableStateOf
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
    IntroPage(IntroArt.WELCOME, "Сколько можно потратить сегодня", "Nomad Budget — личный бюджет на месяц. Ты заранее решаешь, куда пойдёт доход, а приложение каждый день показывает, сколько ещё можно потратить, чтобы спокойно дотянуть до следующего дохода. Подходит и для одной валюты, и для нескольких."),
    IntroPage(IntroArt.ENVELOPES, "Доход — по конвертам", "Конверт — сумма на месяц для одной категории: еда, жильё, транспорт. Каждая трата уменьшает свой конверт. Главная цифра — сколько осталось во всех конвертах и сколько это в день."),
    IntroPage(IntroArt.SAVINGS, "Сначала откладываешь, потом тратишь", "В начале месяца сразу переводишь часть дохода в резерв — это строка «Отложить» в плане. Остальное раскладываешь по конвертам. Так сбережения растут каждый месяц, а не только когда что-то осталось.", listOf(AppModule.CUSHION)),
    IntroPage(IntroArt.CUSHION, "Резерв и капитал", "Резерв — деньги на случай, если доход пропадёт. Его удобно мерить в месяцах расходов: сколько проживёшь без дохода. Капитал — всё, что у тебя есть, минус долги: видно, растёт ли он и когда дойдёшь до цели.", listOf(AppModule.CAPITAL)),
    IntroPage(IntroArt.DEBTS, "Кредиты в плане", "Обязательный платёж сам попадает в план месяца, и эти деньги не уйдут на другое. Досрочное погашение тоже можно запланировать — из дохода или из резерва.", listOf(AppModule.DEBTS)),
    IntroPage(IntroArt.FUNDS, "Копилки на крупные траты", "Для трат раз в квартал или раз в год: страховка, отпуск, техника. Каждый месяц откладываешь понемногу, а когда приходит срок, платишь из копилки — и месяц не проваливается.", listOf(AppModule.FUNDS)),
    IntroPage(IntroArt.CURRENCIES, "Если деньги в разных валютах", "Счета в любой валюте, итоги — в одной основной, по курсу на дату операции. Обмен записывается по реальному курсу. Если все деньги в одной валюте, этот модуль не нужен.", listOf(AppModule.CURRENCIES, AppModule.BANK_WATCH)),
    IntroPage(IntroArt.MORE, "Графики и «Входящие»", "Графики показывают, как менялись доходы, траты и резерв. «Входящие» — трата одной строкой, когда некогда заполнять запись: разберёшь потом.", listOf(AppModule.CHARTS, AppModule.INBOX)),
    IntroPage(IntroArt.HELP, "Если что-то не так", "Кнопка «?» есть на каждом экране: нажми на место, где ошибка, и напиши пару слов — уйдёт обращение со скриншотом, суммы на нём скрыты. Твои счета и операции видишь только ты. Выгрузить всё в CSV или удалить учётку можно в любой момент в настройках."),
)

private val DECIDED_IN_SETUP = setOf(AppModule.CURRENCIES)

object IntroTags {
    const val NEXT = "intro.next"
    const val BACK = "intro.back"
    const val SKIP = "intro.skip"
    const val OPEN_MODULES = "intro.open_modules"
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
    onOpenModules: () -> Unit = {},
) {
    var index by remember { mutableIntStateOf(0) }
    val page = INTRO_PAGES[index]
    val last = index == INTRO_PAGES.lastIndex
    var draftOff by remember { mutableStateOf(state.settings.modulesOff) }
    val enabled = enabledModules(if (firstTime) draftOff else state.settings.modulesOff)
    val finish = {
        if (firstTime && draftOff != state.settings.modulesOff) onSetModules(draftOff, state.settings.blocksOff)
        onDone()
    }

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
                    if (!last) TextButton(onClick = finish, modifier = Modifier.testTag(IntroTags.SKIP)) { Text(if (firstTime) "Пропустить" else "Закрыть") }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                ) {
                    IntroIllustration(page.art, state)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(page.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    offeredModules(page, features).filterNot { firstTime && it in DECIDED_IN_SETUP }.forEach { module ->
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
                                if (firstTime) {
                                    Switch(
                                        checked = on,
                                        onCheckedChange = { draftOff = withModule(draftOff, module, it) },
                                        modifier = Modifier.testTag(IntroTags.module(module)),
                                    )
                                }
                            }
                        }
                    }
                    if (!firstTime && offeredModules(page, features).isNotEmpty()) {
                        TextButton(onClick = onOpenModules, modifier = Modifier.testTag(IntroTags.OPEN_MODULES)) { Text("Включить или выключить — в «Модулях»") }
                    }
                    if (last && firstTime) {
                        Text("Модули можно поменять в любой момент: «Ещё» → «Модули».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (index > 0) SecondaryButton(onClick = { index-- }, modifier = Modifier.testTag(IntroTags.BACK)) { Text("Назад") }
                    PrimaryButton(
                        onClick = { if (last) finish() else index++ },
                        modifier = Modifier.weight(1f).testTag(IntroTags.NEXT),
                    ) {
                        Text(
                            when {
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
                        Triple("1", "План на месяц", "доход раскладываешь заранее, а не считаешь в конце") to scheme.primary,
                        Triple("2", "Сначала откладываешь", "резерв растёт каждый месяц, а не «что останется»") to colors.save,
                        Triple("3", "Только нужное", "кредиты, копилки, валюты — включаешь, если пригодятся") to colors.good,
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
                    Text("«Отложить» — первым, потом кредиты, конверты и свободное", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                IntroArt.CUSHION -> {
                    Text("Резерв · цель — 6 месяцев расходов", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
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

