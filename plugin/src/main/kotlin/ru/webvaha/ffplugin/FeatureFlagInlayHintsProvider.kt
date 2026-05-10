package ru.webvaha.ffplugin

import com.goide.psi.GoConstDefinition
import com.goide.psi.GoConstSpec
import com.goide.psi.GoReferenceExpression
import com.goide.psi.GoStringLiteral
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Добавляет inlay-подсказки рядом с любым строковым значением, совпадающим
 * с ключом из feature-flags.json - независимо от имени метода или SDK:
 *
 *   ff.IsEnabled(ctx, "ff_my_flag")        // [true]
 *   client.Evaluate("ff_my_flag", entity)  // [true]
 *   const myFlag = "ff_my_flag"            // [true]
 *
 * Поддерживает два варианта:
 *   - строковый литерал:      "flag_name"
 *   - именованная константа:  myFlagConst  (резолвится к строке)
 *
 * Данные берёт из feature-flags.json через FeatureFlagService.
 */
@Suppress("UnstableApiUsage")
class FeatureFlagInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey("io.sbmt.feature-flag-hints")
    override val name: String = "Feature Flag (prod state)"
    override val description: String =
        "Shows production state of Flipt feature flags from feature-flags.json"
    override val previewText: String? = null
    override val isVisibleInSettings: Boolean = true

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = JPanel()
        }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector {
        // Загружаем флаги один раз на весь проход по файлу, а не на каждый элемент.
        val flags = file.project.getService(FeatureFlagService::class.java).getFlags()
        // Минимальная длина ключа - для пре-фильтрации ссылок до вызова resolve().
        val minKeyLength = flags?.keys?.minOfOrNull { it.length } ?: Int.MAX_VALUE

        return object : FactoryInlayHintsCollector(editor) {

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (flags == null) return true

                val flagKey: String = when {
                    element is GoStringLiteral ->
                        element.stringValue()
                    // Любые ссылки - пробуем резолвить как константу.
                    // pkg.Const (пакетные константы) и простые ссылки обрабатываются одинаково.
                    // obj.Field и методы resolveConstantValue вернёт null (as? GoConstDefinition).
                    // Пре-фильтр по длине отсекает короткие идентификаторы (ctx, err, i…)
                    // до дорогого вызова resolve().
                    element is GoReferenceExpression &&
                        (element.identifier?.textLength ?: 0) >= minKeyLength ->
                        resolveConstantValue(element)
                    else -> return true
                } ?: return true

                val enabled = flags[flagKey] ?: return true

                val label = if (enabled) "true" else "false"
                val presentation = factory.roundWithBackground(factory.smallText(label))

                sink.addInlineElement(
                    offset = element.textRange.endOffset,
                    relatesToPrecedingText = true,
                    presentation = presentation,
                )

                return true
            }
        }
    }

    /**
     * Резолвит константу к её строковому значению.
     *
     * const tmpFFDisconnectedConsumer = "tmp_call_disconnected_consumer"
     *                                    ↑ это и возвращаем
     */
    private fun resolveConstantValue(ref: GoReferenceExpression): String? {
        val constDef = ref.resolve() as? GoConstDefinition ?: return null
        val spec = constDef.parent as? GoConstSpec ?: return null
        val idx = spec.constDefinitionList.indexOf(constDef)
        val valueExpr = spec.expressionList.getOrNull(idx) ?: return null
        return (valueExpr as? GoStringLiteral)?.stringValue()
    }

    /**
     * Возвращает строковое значение литерала без кавычек.
     * getText() возвращает `"flag_name"` (с кавычками).
     */
    private fun GoStringLiteral.stringValue(): String? {
        val raw = text ?: return null
        return raw.removeSurrounding("\"").takeIf { it.isNotEmpty() }
    }
}
