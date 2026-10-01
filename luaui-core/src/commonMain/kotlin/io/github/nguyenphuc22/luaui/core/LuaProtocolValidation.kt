package io.github.nguyenphuc22.luaui.core

private val identifierPattern = Regex("[A-Za-z][A-Za-z0-9._:-]{0,127}")

data class LuaProtocolLimits(
    val maxNodes: Int = 1_000,
    val maxDepth: Int = 32,
    val maxChildrenPerColumn: Int = 500,
    val maxTextLength: Int = 4_096,
    val maxButtonLabelLength: Int = 256,
)

data class LuaValidationIssue(
    val code: String,
    val path: String,
    val message: String,
)

sealed interface LuaScreenValidationResult {
    data class Valid(val screen: LuaScreen) : LuaScreenValidationResult

    data class Invalid(val issues: List<LuaValidationIssue>) : LuaScreenValidationResult
}

object LuaProtocolValidator {
    fun validateScreen(
        screen: LuaScreen,
        clientCapabilities: Set<LuaCapability>? = null,
        limits: LuaProtocolLimits = LuaProtocolLimits(),
    ): LuaScreenValidationResult {
        val issues = mutableListOf<LuaValidationIssue>()

        validateIdentifier(screen.id.value, "screen.id", issues)
        if (screen.protocolVersion != LUA_PROTOCOL_VERSION) {
            issues += issue(
                code = "unsupported_protocol_version",
                path = "screen.protocolVersion",
                message = "Expected protocol version $LUA_PROTOCOL_VERSION.",
            )
        }
        if (screen.schemaVersion != LUA_SCHEMA_VERSION) {
            issues += issue(
                code = "unsupported_schema_version",
                path = "screen.schemaVersion",
                message = "Expected schema version $LUA_SCHEMA_VERSION.",
            )
        }

        appendCapabilityIssues(
            capabilities = screen.requiredCapabilities,
            path = "screen.requiredCapabilities",
            issues = issues,
        )

        val visitedIds = mutableSetOf<LuaNodeId>()
        val requiredByTree = mutableSetOf<LuaCapability>()
        var nodeCount = 0

        fun visit(node: LuaNode, path: String, depth: Int) {
            nodeCount += 1
            if (nodeCount > limits.maxNodes) {
                issues += issue(
                    code = "node_limit_exceeded",
                    path = path,
                    message = "Screen exceeds the ${limits.maxNodes}-node limit.",
                )
                return
            }
            if (depth > limits.maxDepth) {
                issues += issue(
                    code = "tree_depth_exceeded",
                    path = path,
                    message = "Screen exceeds the ${limits.maxDepth}-level depth limit.",
                )
                return
            }

            validateIdentifier(node.id.value, "$path.id", issues)
            if (!visitedIds.add(node.id)) {
                issues += issue(
                    code = "duplicate_node_id",
                    path = "$path.id",
                    message = "Node ID '${node.id.value}' is used more than once in this screen.",
                )
            }

            requiredByTree += LuaCapabilities.requiredBy(node)
            when (node) {
                is LuaColumnNode -> {
                    if (node.children.size > limits.maxChildrenPerColumn) {
                        issues += issue(
                            code = "column_child_limit_exceeded",
                            path = "$path.children",
                            message = "Column exceeds the ${limits.maxChildrenPerColumn}-child limit.",
                        )
                    }
                    node.children.forEachIndexed { index, child ->
                        visit(child, "$path.children[$index]", depth + 1)
                    }
                }

                is LuaTextNode -> {
                    if (node.text.length > limits.maxTextLength) {
                        issues += issue(
                            code = "text_length_exceeded",
                            path = "$path.text",
                            message = "Text exceeds the ${limits.maxTextLength}-character limit.",
                        )
                    }
                }

                is LuaButtonNode -> {
                    if (node.label.length > limits.maxButtonLabelLength) {
                        issues += issue(
                            code = "button_label_length_exceeded",
                            path = "$path.label",
                            message = "Button label exceeds the ${limits.maxButtonLabelLength}-character limit.",
                        )
                    }
                    validateIdentifier(node.onClick.actionId.value, "$path.onClick.actionId", issues)
                }
            }
        }

        visit(node = screen.root, path = "screen.root", depth = 1)

        val undeclared = requiredByTree - screen.requiredCapabilities
        if (undeclared.isNotEmpty()) {
            issues += issue(
                code = "undeclared_required_capability",
                path = "screen.requiredCapabilities",
                message = "Screen must declare all capabilities used by its tree: ${undeclared.describe()}.",
            )
        }

        clientCapabilities?.let { capabilities ->
            val missing = screen.requiredCapabilities - capabilities
            if (missing.isNotEmpty()) {
                issues += issue(
                    code = "client_capability_missing",
                    path = "screen.requiredCapabilities",
                    message = "Client does not support: ${missing.describe()}.",
                )
            }
        }

        return if (issues.isEmpty()) {
            LuaScreenValidationResult.Valid(screen)
        } else {
            LuaScreenValidationResult.Invalid(issues)
        }
    }

    fun validateScreenRequest(request: LuaScreenRequest): List<LuaValidationIssue> {
        val issues = mutableListOf<LuaValidationIssue>()
        validateIdentifier(request.screenId.value, "screenRequest.screenId", issues)
        appendCapabilityIssues(
            capabilities = request.clientCapabilities,
            path = "screenRequest.clientCapabilities",
            issues = issues,
        )
        return issues
    }

    fun validateActionRequest(request: LuaActionRequest): List<LuaValidationIssue> {
        val issues = mutableListOf<LuaValidationIssue>()
        validateIdentifier(request.screenId.value, "action.screenId", issues)
        validateIdentifier(request.sourceNodeId.value, "action.sourceNodeId", issues)
        validateIdentifier(request.actionId.value, "action.actionId", issues)
        appendCapabilityIssues(
            capabilities = request.clientCapabilities,
            path = "action.clientCapabilities",
            issues = issues,
        )
        return issues
    }

    fun missingCapabilities(
        screen: LuaScreen,
        clientCapabilities: Set<LuaCapability>,
    ): Set<LuaCapability> = screen.requiredCapabilities - clientCapabilities

    fun validateCapabilities(
        capabilities: Set<LuaCapability>,
        path: String = "capabilities",
    ): List<LuaValidationIssue> = buildList {
        appendCapabilityIssues(
            capabilities = capabilities,
            path = path,
            issues = this,
        )
    }

    private fun validateIdentifier(
        value: String,
        path: String,
        issues: MutableList<LuaValidationIssue>,
    ) {
        if (!identifierPattern.matches(value)) {
            issues += issue(
                code = "invalid_identifier",
                path = path,
                message = "Identifier must match ${identifierPattern.pattern}.",
            )
        }
    }

    private fun appendCapabilityIssues(
        capabilities: Set<LuaCapability>,
        path: String,
        issues: MutableList<LuaValidationIssue>,
    ) {
        capabilities
            .groupBy { capability -> capability.name }
            .filterValues { grouped -> grouped.size > 1 }
            .forEach { (name, _) ->
                issues += issue(
                    code = "duplicate_capability_name",
                    path = path,
                    message = "Capability '$name' is declared more than once.",
                )
            }
        capabilities.forEachIndexed { index, capability ->
            validateIdentifier(capability.name, "$path[$index].name", issues)
            if (capability.version <= 0) {
                issues += issue(
                    code = "invalid_capability_version",
                    path = "$path[$index].version",
                    message = "Capability version must be positive.",
                )
            }
        }
    }

    private fun issue(code: String, path: String, message: String): LuaValidationIssue =
        LuaValidationIssue(code = code, path = path, message = message)
}

private fun Set<LuaCapability>.describe(): String =
    sortedWith(compareBy<LuaCapability> { capability -> capability.name }.thenBy { capability -> capability.version })
        .joinToString(separator = ", ") { capability -> "${capability.name}@${capability.version}" }
