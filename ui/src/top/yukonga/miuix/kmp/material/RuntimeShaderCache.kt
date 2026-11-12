// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized

/**
 * 已编译 [RuntimeShader] 实例的缓存，避免每帧重新编译。
 */
interface RuntimeShaderCache {

    /**
     * 返回给定 [key] 对应的缓存 [RuntimeShader]，若尚未缓存则从 [string] 编译。
     *
     * @param key 标识已编译 [RuntimeShader] 的缓存键。
     * @param string 缓存未命中时编译的 AGSL/SkSL shader 源码。
     */
    fun obtainRuntimeShader(key: String, string: String): RuntimeShader
}

/**
 * 在组合树内提供共享 [RuntimeShaderCache] 的 CompositionLocal。
 *
 * 同一组合内的所有背景效果作用域共享此缓存，既避免重复编译 shader，
 * 又允许缓存在组合销毁时被垃圾回收。
 */
val LocalRuntimeShaderCache = staticCompositionLocalOf<RuntimeShaderCache> {
    DefaultRuntimeShaderCache()
}

@OptIn(InternalCoroutinesApi::class)
class DefaultRuntimeShaderCache : RuntimeShaderCache {

    private val lock = SynchronizedObject()
    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader = synchronized(lock) {
        runtimeShaders.getOrPut(key) { RuntimeShader(string) }
    }
}
