package cn.yiiguxing.plugin.translate.documentation

import cn.yiiguxing.plugin.translate.trans.Translator
import cn.yiiguxing.plugin.translate.util.TranslateService
import cn.yiiguxing.plugin.translate.util.invokeLater
import com.intellij.lang.Language
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.concurrency.isPending
import org.jetbrains.concurrency.runAsync
import java.util.concurrent.TimeoutException

class TranslateDocumentationTask(
    val text: String,
    val language: Language? = null,
    val translator: Translator = TranslateService.translator
) {

    //execute on a different thread outside read action
    private val promise = runAsync {
        translator.getTranslatedDocumentation(text, language)
    }.onError { e ->
        invokeLater(ModalityState.NON_MODAL) { DocNotifications.showWarning(e, null) }
    }

    val isProcessed: Boolean get() = !promise.isPending

    val isSucceeded: Boolean get() = promise.isSucceeded

    fun onSuccess(callback: (String) -> Unit) {
        promise.onSuccess(callback)
    }

    fun nonBlockingGet(): String? {
        var tries = DEFAULT_TIMEOUT_IN_MILLIS / TIME_TO_BLOCK_IN_MILLIS

        //blocking for the whole time can lead to ui freezes, so we need to periodically do `checkCanceled`
        while (tries > 0) {
            tries -= 1
            ProgressManager.checkCanceled()
            try {
                return promise.blockingGet(TIME_TO_BLOCK_IN_MILLIS)
            } catch (t: TimeoutException) {
                //ignore
            } catch (e: Throwable) {
                return null
            }
        }

        //translation is not ready yet, show original documentation
        return null
    }

    companion object {
        private const val DEFAULT_TIMEOUT_IN_MILLIS = 3_000
        private const val TIME_TO_BLOCK_IN_MILLIS = 100
    }
}