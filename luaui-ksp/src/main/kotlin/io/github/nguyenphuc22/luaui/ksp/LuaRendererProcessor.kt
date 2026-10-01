package io.github.nguyenphuc22.luaui.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate

private const val rendererAnnotation = "io.github.nguyenphuc22.luaui.annotations.LuaRenderer"
private const val composableAnnotation = "androidx.compose.runtime.Composable"
private const val renderContextType = "io.github.nguyenphuc22.luaui.compose.LuaRenderContext"

class LuaRendererProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        LuaRendererProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options,
        )
}

private class LuaRendererProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {
    private var generated = false

    override fun process(resolver: com.google.devtools.ksp.processing.Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val symbols = resolver.getSymbolsWithAnnotation(rendererAnnotation).toList()
        val deferred = symbols.filterNot { symbol -> symbol.validate(enableNewFeatures = true) }
        if (deferred.isNotEmpty()) return deferred

        val renderers = symbols.mapNotNull { symbol ->
            val function = symbol as? KSFunctionDeclaration
            if (function == null) {
                logger.error("@LuaRenderer can only annotate a function.", symbol)
                null
            } else {
                function.toRenderer()
            }
        }

        if (renderers.isEmpty()) return emptyList()

        val generatedPackage = options["luaui.generated.package"]
            ?: "io.github.nguyenphuc22.luaui.generated"
        val generatedObject = options["luaui.generated.object"]
            ?: "LuaGeneratedRendererDispatcher"

        codeGenerator
            .createNewFile(
                dependencies = Dependencies(
                    aggregating = false,
                    *renderers.mapNotNull { renderer -> renderer.containingFile }.toTypedArray(),
                ),
                packageName = generatedPackage,
                fileName = generatedObject,
            )
            .bufferedWriter()
            .use { writer ->
                writer.appendLine("package $generatedPackage")
                writer.appendLine()
                writer.appendLine("import androidx.compose.runtime.Composable")
                writer.appendLine("import io.github.nguyenphuc22.luaui.compose.LuaNodeDispatcher")
                writer.appendLine("import io.github.nguyenphuc22.luaui.compose.LuaRenderContext")
                writer.appendLine("import io.github.nguyenphuc22.luaui.core.LuaAction")
                writer.appendLine("import io.github.nguyenphuc22.luaui.core.LuaNode")
                writer.appendLine("import io.github.nguyenphuc22.luaui.core.LuaNodeId")
                writer.appendLine()
                writer.appendLine("public object $generatedObject : LuaNodeDispatcher {")
                writer.appendLine("    @Composable")
                writer.appendLine("    override fun Render(node: LuaNode, onAction: (LuaNodeId, LuaAction) -> Unit) {")
                writer.appendLine("        val context = LuaRenderContext(onAction) { child -> Render(child, onAction) }")
                writer.appendLine("        when (node) {")
                renderers.sortedBy { renderer -> renderer.nodeType }.forEach { renderer ->
                    writer.appendLine(
                        "            is ${renderer.nodeType} -> ${renderer.functionName}(node, context)",
                    )
                }
                writer.appendLine("        }")
                writer.appendLine("    }")
                writer.appendLine("}")
            }

        generated = true
        return emptyList()
    }

    private fun KSFunctionDeclaration.toRenderer(): Renderer? {
        if (parentDeclaration != null) {
            logger.error("@LuaRenderer functions must be top-level.", this)
            return null
        }
        if (parameters.size != 2) {
            logger.error("@LuaRenderer function must accept a node and LuaRenderContext.", this)
            return null
        }
        if (!annotations.any { annotation -> annotation.qualifiedName() == composableAnnotation }) {
            logger.error("@LuaRenderer function must be annotated with @Composable.", this)
            return null
        }

        val annotation = annotations.first { candidate -> candidate.qualifiedName() == rendererAnnotation }
        val annotatedNode = annotation.arguments
            .firstOrNull { argument -> argument.name?.asString() == "node" }
            ?.value as? KSType
        val nodeType = annotatedNode?.declaration?.qualifiedName?.asString()
        if (nodeType == null) {
            logger.error("@LuaRenderer node must be a concrete LuaNode type.", this)
            return null
        }

        val parameterNodeType = parameters[0].type.resolve().declaration.qualifiedName?.asString()
        if (parameterNodeType != nodeType) {
            logger.error("First parameter must be the annotated node type $nodeType.", this)
            return null
        }
        val contextType = parameters[1].type.resolve().declaration.qualifiedName?.asString()
        if (contextType != renderContextType) {
            logger.error("Second parameter must be $renderContextType.", this)
            return null
        }

        val functionName = qualifiedName?.asString()
        if (functionName == null) {
            logger.error("Unable to resolve renderer function name.", this)
            return null
        }

        return Renderer(
            nodeType = nodeType,
            functionName = functionName,
            containingFile = containingFile,
        )
    }
}

private data class Renderer(
    val nodeType: String,
    val functionName: String,
    val containingFile: KSFile?,
)

private fun com.google.devtools.ksp.symbol.KSAnnotation.qualifiedName(): String? =
    annotationType.resolve().declaration.qualifiedName?.asString()
