package com.github.yumelira.yumebox.core.util

import com.github.yumelira.yumebox.core.Clash
import com.github.yumelira.yumebox.core.model.AgeKeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility wrappers for age key cryptographic operations backed by the native mihomo/JNI surface.
 * All functions dispatch to [Dispatchers.Default] so callers never execute JNI crypto on the UI thread.
 */
object AgeKeyCrypto {
    /** Derive public keys from the given [secretKeys] (blocking, call from background). */
    fun toPublicKeysBlocking(secretKeys: List<String>): List<String>? = Clash.toPublicKeys(secretKeys)
    /** Generate an x25519 key pair (blocking, call from background). */
    fun genX25519KeyPairBlocking(): Pair<String, String>? = Clash.genX25519KeyPair()
    /** Generate a hybrid (mlkem768x25519) key pair (blocking, call from background). */
    fun genHybridKeyPairBlocking(): AgeKeyPair? = Clash.genHybridKeyPair()
    /** Generate an age x25519 key pair via Rust liboverride (blocking, call from background). */
    fun genAgeKeyBlocking(): AgeKeyPair? = Clash.genAgeKey()
    /** Derive an age public key from a secret key via Rust liboverride (blocking, call from background). */
    fun agePublicKeyBlocking(secretKey: String): String? = Clash.agePublicKey(secretKey)
    /** Derive public keys from the given [secretKeys] on [Dispatchers.Default]. */
    suspend fun toPublicKeys(secretKeys: List<String>): List<String>? =
        withContext(Dispatchers.Default) { toPublicKeysBlocking(secretKeys) }
    /** Generate an x25519 key pair on [Dispatchers.Default]. */
    suspend fun genX25519KeyPair(): Pair<String, String>? =
        withContext(Dispatchers.Default) { genX25519KeyPairBlocking() }
    /** Generate a hybrid (mlkem768x25519) key pair on [Dispatchers.Default]. */
    suspend fun genHybridKeyPair(): AgeKeyPair? =
        withContext(Dispatchers.Default) { genHybridKeyPairBlocking() }
    /** Generate an age x25519 key pair via Rust liboverride on [Dispatchers.Default]. */
    suspend fun genAgeKey(): AgeKeyPair? =
        withContext(Dispatchers.Default) { genAgeKeyBlocking() }
    /** Derive an age public key from a secret key via Rust liboverride on [Dispatchers.Default]. */
    suspend fun agePublicKey(secretKey: String): String? =
        withContext(Dispatchers.Default) { agePublicKeyBlocking(secretKey) }
}
