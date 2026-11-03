package com.github.lmfirefly.flycat.core.util.crypto

import com.github.lmfirefly.flycat.core.Clash
import com.github.lmfirefly.flycat.core.model.AgeKeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 由原生 mihomo/JNI 提供的 Age 密钥加密操作的工具包装器。
 * suspend 函数统一分派到 [Dispatchers.Default]，不会在 UI 线程执行 JNI 加密；`*Blocking` 变体在调用线程直接执行，须从后台线程调用。
 */
object AgeKeyCrypto {
    /**从给定的 [secretKeys] 派生公钥（阻塞，须从后台线程调用）。 */
    fun toPublicKeysBlocking(secretKeys: List<String>): List<String>? = Clash.toPublicKeys(secretKeys)
    /**生成 x25519 密钥对（阻塞，须从后台线程调用）。 */
    fun genX25519KeyPairBlocking(): Pair<String, String>? = Clash.genX25519KeyPair()
    /**生成混合 (mlkem768x25519) 密钥对（阻塞，须从后台线程调用）。 */
    fun genHybridKeyPairBlocking(): AgeKeyPair? = Clash.genHybridKeyPair()
    /**通过 Rust liboverride 生成 Age x25519 密钥对（阻塞，须从后台线程调用）。 */
    fun genAgeKeyBlocking(): AgeKeyPair? = Clash.genAgeKey()
    /**通过 Rust liboverride 从私钥派生 Age 公钥（阻塞，须从后台线程调用）。 */
    fun agePublicKeyBlocking(secretKey: String): String? = Clash.agePublicKey(secretKey)
    /**从 [Dispatchers.Default] 上给定的 [secretKeys] 派生公钥。 */
    suspend fun toPublicKeys(secretKeys: List<String>): List<String>? = withContext(Dispatchers.Default) { toPublicKeysBlocking(secretKeys) }
    /**在 [Dispatchers.Default] 上生成 x25519 密钥对。 */
    suspend fun genX25519KeyPair(): Pair<String, String>? = withContext(Dispatchers.Default) { genX25519KeyPairBlocking() }
    /**在 [Dispatchers.Default] 上生成混合 (mlkem768x25519) 密钥对。 */
    suspend fun genHybridKeyPair(): AgeKeyPair? = withContext(Dispatchers.Default) { genHybridKeyPairBlocking() }
    /**通过 [Dispatchers.Default] 上的 Rust liboverride 生成 Age x25519 密钥对。 */
    suspend fun genAgeKey(): AgeKeyPair? = withContext(Dispatchers.Default) { genAgeKeyBlocking() }
    /**通过 [Dispatchers.Default] 上的 Rust liboverride 从私钥派生 Age 公钥。 */
    suspend fun agePublicKey(secretKey: String): String? = withContext(Dispatchers.Default) { agePublicKeyBlocking(secretKey) }
}
