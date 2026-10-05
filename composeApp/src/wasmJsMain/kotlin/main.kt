import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import ru.nomadbudget.App
import ru.nomadbudget.demo.DemoMode

private fun hasDemoParam(): Boolean = js("new URLSearchParams(window.location.search).has('demo')")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    DemoMode.enabled = hasDemoParam()
    ComposeViewport(viewportContainerId = "root") {
        App()
    }
}
