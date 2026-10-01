package io.github.nguyenphuc22.luaui.annotations

import io.github.nguyenphuc22.luaui.core.LuaNode
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class LuaRenderer(
    val node: KClass<out LuaNode>,
)
