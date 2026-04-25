package ru.webvaha.ffplugin

import com.goide.psi.GoCallExpr
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
 * Добавляет inlay-подсказки рядом с аргументом-флагом в вызовах IsEnabled:
 *
 *   if !i.ff.IsEnabled(ctx, tmpFFDisconnectedConsumer) { // [false]
 *   if p.feature.IsEnabled(ctx, "ff_tmp_outbox_event_task_created_topic") { // [true]
 *
 * Поддерживает два варианта передачи имени флага:
 *   - строковый литерал:   IsEnabled(ctx, "flag_name")
 *   - именованная константа: IsEnabled(ctx, tmpFFDisconnectedConsumer)
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
        return object : FactoryInlayHintsCollector(editor) {

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element !is GoCallExpr) return true

                // Определяем имя вызываемого метода.
                // Для i.ff.IsEnabled(...)  expression — это GoReferenceExpression,
                // у которого identifier содержит "IsEnabled".
                val callee = element.expression as? GoReferenceExpression ?: return true
                if (callee.identifier?.text != "IsEnabled") return true

                val args = element.argumentList?.expressionList ?: return true
                // Ожидаем минимум 2 аргумента: (ctx, flagName)
                if (args.size < 2) return true

                val flagArg = args[1]

                val flagKey: String = when (flagArg) {
                    is GoStringLiteral -> flagArg.stringValue()
                    is GoReferenceExpression -> resolveConstantValue(flagArg)
                    else -> null
                } ?: return true

                val service = element.project.getService(FeatureFlagService::class.java)
                val flags = service.getFlags() ?: return true
                val enabled = flags[flagKey] ?: return true // флаг не найден в файле — молчим

                // пока посмотрим с true|false потом можно переделать на "prod:✓"|"prod:✗"
                val label = if (enabled) "true" else "false"
                val presentation = factory.roundWithBackground(factory.smallText(label))

                // Hint ставим сразу после аргумента с именем флага
                sink.addInlineElement(
                    offset = flagArg.textRange.endOffset,
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
