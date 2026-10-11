package io.github.nguyenphuc22.luaui.core

private val identifierPattern = Regex("[A-Za-z][A-Za-z0-9._:-]{0,127}")

data class LuaProtocolLimits(
    val maxNodes: Int = 1_000,
    val maxDepth: Int = 32,
    val maxChildrenPerColumn: Int = 500,
    val maxTextLength: Int = 4_096,
    val maxButtonLabelLength: Int = 256,
    val maxTextFieldLabelLength: Int = 256,
    val maxTextFieldInitialValueLength: Int = 4_096,
    val maxSubmittedTextFields: Int = 32,
    val maxSubmittedTextFieldValueLength: Int = 4_096,
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
        val nodesById = mutableMapOf<LuaNodeId, LuaNode>()
        val requiredByTree = mutableSetOf<LuaCapability>()
        val submitTextFieldActions = mutableListOf<Pair<LuaAction.SubmitTextFields, String>>()
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
                return
            }
            nodesById[node.id] = node

            when (node) {
                is LuaColumnNode -> {
                    requiredByTree += LuaCapabilities.column
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
                    requiredByTree += LuaCapabilities.text
                    if (node.text.length > limits.maxTextLength) {
                        issues += issue(
                            code = "text_length_exceeded",
                            path = "$path.text",
                            message = "Text exceeds the ${limits.maxTextLength}-character limit.",
                        )
                    }
                }

                is LuaTextFieldNode -> {
                    requiredByTree += LuaCapabilities.textField
                    if (node.label.length > limits.maxTextFieldLabelLength) {
                        issues += issue(
                            code = "text_field_label_length_exceeded",
                            path = "$path.label",
                            message = "Text field label exceeds the ${limits.maxTextFieldLabelLength}-character limit.",
                        )
                    }
                    if (node.initialValue.length > limits.maxTextFieldInitialValueLength) {
                        issues += issue(
                            code = "text_field_initial_value_length_exceeded",
                            path = "$path.initialValue",
                            message = "Text field initial value exceeds the ${limits.maxTextFieldInitialValueLength}-character limit.",
                        )
                    }
                }

                is LuaButtonNode -> {
                    requiredByTree += LuaCapabilities.button
                    requiredByTree += LuaCapabilities.requiredBy(node.onClick)
                    if (node.label.length > limits.maxButtonLabelLength) {
                        issues += issue(
                            code = "button_label_length_exceeded",
                            path = "$path.label",
                            message = "Button label exceeds the ${limits.maxButtonLabelLength}-character limit.",
                        )
                    }
                    validateActionDefinition(
                        action = node.onClick,
                        path = "$path.onClick",
                        limits = limits,
                        issues = issues,
                        submitTextFieldActions = submitTextFieldActions,
                    )
                }
            }
        }

        visit(node = screen.root, path = "screen.root", depth = 1)

        submitTextFieldActions.forEach { (action, actionPath) ->
            action.textFieldNodeIds.forEachIndexed { index, fieldId ->
                val path = "$actionPath.textFieldNodeIds[$index]"
                when (nodesById[fieldId]) {
                    null -> issues += issue(
                        code = "submit_text_fields_missing_node",
                        path = path,
                        message = "SubmitTextFields must reference a node in the same screen.",
                    )

                    !is LuaTextFieldNode -> issues += issue(
                        code = "submit_text_fields_requires_text_field",
                        path = path,
                        message = "SubmitTextFields may reference only TextField nodes.",
                    )

                    is LuaTextFieldNode -> Unit
                }
            }
        }

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

    fun validateActionRequest(
        request: LuaActionRequest,
        limits: LuaProtocolLimits = LuaProtocolLimits(),
    ): List<LuaValidationIssue> {
        val issues = mutableListOf<LuaValidationIssue>()
        validateIdentifier(request.screenId.value, "action.screenId", issues)
        validateIdentifier(request.sourceNodeId.value, "action.sourceNodeId", issues)
        validateIdentifier(request.actionId.value, "action.actionId", issues)
        appendCapabilityIssues(
            capabilities = request.clientCapabilities,
            path = "action.clientCapabilities",
            issues = issues,
        )
        request.textFieldSubmission?.let { submission ->
            validateTextFieldSubmission(
                submission = submission,
                path = "action.textFieldSubmission",
                limits = limits,
                issues = issues,
            )
        }
        return issues
    }

    /** Validates the syntactic bounds of an explicit action acknowledgement. */
    fun validateTextFieldAcknowledgement(
        acknowledgement: LuaTextFieldAcknowledgement,
        limits: LuaProtocolLimits = LuaProtocolLimits(),
    ): List<LuaValidationIssue> {
        val issues = mutableListOf<LuaValidationIssue>()
        validateIdentifier(
            acknowledgement.submissionId.value,
            "acknowledgement.submissionId",
            issues,
        )
        if (acknowledgement.fields.isEmpty()) {
            issues += issue(
                code = "empty_text_field_acknowledgement",
                path = "acknowledgement.fields",
                message = "A TextField acknowledgement must contain at least one field.",
            )
        }
        if (acknowledgement.fields.size > limits.maxSubmittedTextFields) {
            issues += issue(
                code = "acknowledgement_field_count_exceeded",
                path = "acknowledgement.fields",
                message = "Acknowledgement exceeds the ${limits.maxSubmittedTextFields}-field limit.",
            )
        }

        val seenFieldIds = mutableSetOf<LuaNodeId>()
        acknowledgement.fields.forEachIndexed { index, field ->
            val path = "acknowledgement.fields[$index]"
            validateIdentifier(field.nodeId.value, "$path.nodeId", issues)
            if (!seenFieldIds.add(field.nodeId)) {
                issues += issue(
                    code = "duplicate_acknowledged_field_node_id",
                    path = "$path.nodeId",
                    message = "A TextField acknowledgement may name each node only once.",
                )
            }
            if (field.draftRevision < 0) {
                issues += issue(
                    code = "negative_acknowledged_draft_revision",
                    path = "$path.draftRevision",
                    message = "An acknowledged TextField draft revision must be non-negative.",
                )
            }
        }
        return issues
    }

    /** Validates every network-visible part of an action response before runtime consumption. */
    fun validateActionResponse(
        response: LuaActionResponse,
        clientCapabilities: Set<LuaCapability>? = null,
        limits: LuaProtocolLimits = LuaProtocolLimits(),
    ): List<LuaValidationIssue> = when (response) {
        is LuaActionResponse.Screen -> screenValidationIssues(
            screen = response.screen,
            clientCapabilities = clientCapabilities,
            limits = limits,
        )

        is LuaActionResponse.AcknowledgedScreen -> buildList {
            clientCapabilities?.let { capabilities ->
                val acknowledgementCapabilities = setOf(
                    LuaCapabilities.textField,
                    LuaCapabilities.submitTextFields,
                )
                val missing = acknowledgementCapabilities - capabilities
                if (missing.isNotEmpty()) {
                    add(
                        issue(
                            code = "acknowledged_screen_client_capability_missing",
                            path = "actionResponse.acknowledgedScreen",
                            message = "Client does not support TextField acknowledgement: ${missing.describe()}.",
                        ),
                    )
                }
            }
            addAll(
                screenValidationIssues(
                    screen = response.screen,
                    clientCapabilities = clientCapabilities,
                    limits = limits,
                ),
            )
            addAll(validateTextFieldAcknowledgement(response.acknowledgement, limits))
        }

        is LuaActionResponse.Failure -> emptyList()
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

    private fun validateActionDefinition(
        action: LuaAction,
        path: String,
        limits: LuaProtocolLimits,
        issues: MutableList<LuaValidationIssue>,
        submitTextFieldActions: MutableList<Pair<LuaAction.SubmitTextFields, String>>,
    ) {
        validateIdentifier(action.actionId.value, "$path.actionId", issues)
        when (action) {
            is LuaAction.Submit -> Unit

            is LuaAction.SubmitTextFields -> {
                if (action.textFieldNodeIds.isEmpty()) {
                    issues += issue(
                        code = "empty_submit_text_fields",
                        path = "$path.textFieldNodeIds",
                        message = "SubmitTextFields must declare at least one TextField node ID.",
                    )
                }
                if (action.textFieldNodeIds.size > limits.maxSubmittedTextFields) {
                    issues += issue(
                        code = "submit_text_fields_count_exceeded",
                        path = "$path.textFieldNodeIds",
                        message = "SubmitTextFields exceeds the ${limits.maxSubmittedTextFields}-field limit.",
                    )
                }

                val seenFieldIds = mutableSetOf<LuaNodeId>()
                action.textFieldNodeIds.forEachIndexed { index, fieldId ->
                    val fieldPath = "$path.textFieldNodeIds[$index]"
                    validateIdentifier(fieldId.value, fieldPath, issues)
                    if (!seenFieldIds.add(fieldId)) {
                        issues += issue(
                            code = "duplicate_submit_text_field_node_id",
                            path = fieldPath,
                            message = "SubmitTextFields may name each TextField node only once.",
                        )
                    }
                }
                submitTextFieldActions += action to path
            }
        }
    }

    private fun validateTextFieldSubmission(
        submission: LuaTextFieldSubmission,
        path: String,
        limits: LuaProtocolLimits,
        issues: MutableList<LuaValidationIssue>,
    ) {
        validateIdentifier(submission.submissionId.value, "$path.submissionId", issues)
        if (submission.fields.isEmpty()) {
            issues += issue(
                code = "empty_text_field_submission",
                path = "$path.fields",
                message = "A TextField submission must contain at least one field.",
            )
        }
        if (submission.fields.size > limits.maxSubmittedTextFields) {
            issues += issue(
                code = "submission_field_count_exceeded",
                path = "$path.fields",
                message = "Submission exceeds the ${limits.maxSubmittedTextFields}-field limit.",
            )
        }

        val seenFieldIds = mutableSetOf<LuaNodeId>()
        submission.fields.forEachIndexed { index, field ->
            val fieldPath = "$path.fields[$index]"
            validateIdentifier(field.nodeId.value, "$fieldPath.nodeId", issues)
            if (!seenFieldIds.add(field.nodeId)) {
                issues += issue(
                    code = "duplicate_submitted_field_node_id",
                    path = "$fieldPath.nodeId",
                    message = "A TextField submission may name each node only once.",
                )
            }
            if (field.value.length > limits.maxSubmittedTextFieldValueLength) {
                issues += issue(
                    code = "submitted_text_field_value_length_exceeded",
                    path = "$fieldPath.value",
                    message = "Submitted TextField value exceeds the ${limits.maxSubmittedTextFieldValueLength}-character limit.",
                )
            }
            if (field.draftRevision < 0) {
                issues += issue(
                    code = "negative_submitted_draft_revision",
                    path = "$fieldPath.draftRevision",
                    message = "A submitted TextField draft revision must be non-negative.",
                )
            }
        }
    }

    private fun screenValidationIssues(
        screen: LuaScreen,
        clientCapabilities: Set<LuaCapability>?,
        limits: LuaProtocolLimits,
    ): List<LuaValidationIssue> = when (
        val result = validateScreen(
            screen = screen,
            clientCapabilities = clientCapabilities,
            limits = limits,
        )
    ) {
        is LuaScreenValidationResult.Valid -> emptyList()
        is LuaScreenValidationResult.Invalid -> result.issues
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
