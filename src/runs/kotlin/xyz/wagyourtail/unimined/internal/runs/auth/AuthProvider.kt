package xyz.wagyourtail.unimined.internal.runs.auth

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.lenni0451.commons.httpclient.HttpClient
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode
import xyz.wagyourtail.unimined.api.runs.auth.AuthConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.runs.RunsProvider
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.OSUtils
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

class AuthProvider(val runProvider: RunsProvider) : AuthConfig {
    override var enabled: Boolean by FinalizeOnRead((runProvider.project.findProperty("unimined.auth.enabled") as String?)?.toBoolean() ?: false)
    override var storeCredentials: Boolean by FinalizeOnRead((runProvider.project.findProperty("unimined.auth.storeCredentials") as String?)?.toBoolean() ?: true)
    override var encryptStoredCredentials: Boolean by FinalizeOnRead((runProvider.project.findProperty("unimined.auth.encryptStoredCredentials") as String?)?.toBoolean() ?: true)

    companion object {
        private var daemonCache2: JsonObject? = null
    }

    val client = MinecraftAuth.createHttpClient().apply {
        setHeader("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtail.xyz>)")
    }

    fun passwordGenerator(): String {
        val secureRandom = SecureRandom()
        // use base64 encoding, 256 bits
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        return Base64.getEncoder().encodeToString(randomBytes)
    }

    fun passwordToBytes(password: String): ByteArray {
        return Base64.getDecoder().decode(password)
    }

    fun encryptAES256(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(ByteArray(16)))
        return cipher.doFinal(data)
    }

    fun decryptAES256(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ByteArray(16)))
        return cipher.doFinal(data)
    }

    override var authInfo: AuthConfig.AuthInfo? by FinalizeOnRead(LazyMutable {
        if (!enabled) return@LazyMutable null

        runProvider.project.logger.lifecycle("[Unimined/Auth] Getting Auth Info")
        val authFile = runProvider.project.unimined.getGlobalCache().resolve("auth.json")
        val encAuthFile = runProvider.project.unimined.getGlobalCache().resolve("auth.json.enc")

        val existingCredList = if (daemonCache2 != null) {
            runProvider.project.logger.lifecycle("[Unimined/Auth] Using in-memory cached auth from previous run")
            daemonCache2
        } else if (storeCredentials) {
            val fileBytes = if (authFile.exists() && encryptStoredCredentials) {
                // re-store encrypted
                try {
                    val password = getOrSetPassword()
                    // encrypt auth.json
                    val authData = authFile.readBytes()
                    val encData = encryptAES256(authData, passwordToBytes(password))
                    encAuthFile.writeBytes(encData)

                    // delete old auth.json
                    authFile.deleteIfExists()

                    authData
                } catch (e: Exception) {
                    runProvider.project.logger.error("[Unimined/Auth] Failed writing encrypted session data, no credentials will be stored!", e)
                    null
                }
            } else if (encryptStoredCredentials && encAuthFile.exists()) {
                // get key from keychain
                val encKey = getOrSetPassword()
                val encData = encAuthFile.readBytes()
                try {
                    decryptAES256(encData, passwordToBytes(encKey))
                } catch (e: Exception) {
                    runProvider.project.logger.error("[Unimined/Auth] Failed decrypting session data!", e)
                    null
                }
            } else if (!encryptStoredCredentials && authFile.exists()) {
                authFile.readBytes()
            } else {
                null
            }
            // read to json
            if (fileBytes != null) {
                try {
                    JsonParser.parseReader(ByteArrayInputStream(fileBytes).reader())
                } catch (e: Exception) {
                    runProvider.project.logger.error("[Unimined/Auth] Failed reading session data!", e)
                    null
                }
            } else {
                null
            }
        } else {
            null
        }

        val existingCreds = if (existingCredList != null) {
            val username = runProvider.project.findProperty("unimined.auth.username") as String?
            if (username != null) {
                existingCredList.asJsonObject.get(username)
            } else {
                existingCredList.asJsonObject.entrySet().firstOrNull()?.value
            }
        } else {
            null
        }

        val session = if (existingCreds != null) {
            try {
                val session = MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.fromJson(existingCreds.asJsonObject)
                runProvider.project.logger.lifecycle("Refreshing auth")
                MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.refresh(client, session)
            } catch (e: Exception) {
                runProvider.project.logger.lifecycle("[Unimined/Auth] $existingCreds")
                throw e
            }
        } else {
            runProvider.project.logger.lifecycle("[Unimined/Auth] Logging in to Minecraft!")
            MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.getFromInput(client, StepMsaDeviceCode.MsaDeviceCodeCallback {
                runProvider.project.logger.lifecycle("[Unimined/Auth] If your web browser does not open, please go to ${it.directVerificationUri}")
                openUrl(it.directVerificationUri)
            })
        }

        // write back to json
        val creds = if (existingCredList != null) {
            existingCredList.asJsonObject
        } else {
            JsonObject()
        }
        creds.add(session.mcProfile.name, MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.toJson(session))
        daemonCache2 = creds
        if (storeCredentials) {
            if (encryptStoredCredentials) {
                try {
                    val password = getOrSetPassword()
                    // encrypt auth.json
                    val encData = encryptAES256(creds.toString().toByteArray(), passwordToBytes(password))
                    encAuthFile.writeBytes(encData)
                } catch (e: Exception) {
                    runProvider.project.logger.error("[Unimined/Auth] Failed writing encrypted session data, no credentials will be stored!", e)
                }
            } else {
                authFile.writeBytes(creds.toString().toByteArray())
            }
        }

        AuthConfig.AuthInfo(
            session.mcProfile.name,
            session.mcProfile.id,
            session.mcProfile.mcToken.accessToken
        )
    })

    private fun getOrSetPassword(): String {
        Keyring.create().use {
            runProvider.project.logger.info("[Unimined/Auth] Keyring Info: ${it.keyringStorageType}")
            val existing = try {
                it.getPassword("unimined", "authEncKey")
            } catch (e: PasswordAccessException) {
                runProvider.project.logger.error("[Unimined/Auth] error retrieving encryption password", e)
                null
            }
            return if (existing != null) {
                existing
            } else {
                val password = passwordGenerator()
                it.setPassword("unimined", "authEncKey", password)
                password
            }
        }
    }

    fun deletePassword() {
        Keyring.create().use {
            it.deletePassword("unimined", "authEncKey")
        }
    }

    fun openUrl(url: String) {
        // depending on platform, open url
        when (OSUtils.oSId) {
            OSUtils.WINDOWS -> {
                Runtime.getRuntime().exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", url))
            }
            OSUtils.OSX -> {
                Runtime.getRuntime().exec(arrayOf("open", url))
            }
            OSUtils.LINUX -> {
                Runtime.getRuntime().exec(arrayOf("xdg-open", url))
            }
        }
    }
}