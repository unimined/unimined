package xyz.wagyourtail.unimined.api.runs.auth

import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Configuration for authentication.
 * it is recommended to use the gradle properties (possibly globally) instead of setting these values in the build.gradle
 *
 * @since 1.2.0
 */
interface AuthConfig {

    /**
     * If enabled, unimined will use https://github.com/RaphiMC/MinecraftAuth to authenticate client runs with Mojang.
     * value can also be set by the `unimined.auth.enabled` gradle property. but this overrides the value set there.
     */
    var enabled: Boolean

    /**
     * If enabled, unimined will store the credentials to global gradle cache/unimined/auth.json
     * this value can also be set by the `unimined.auth.storeCredentials` gradle property.
     * if enabled, it will default to the first account in the store, or the `unimined.auth.username` gradle property.
     * if the username specified by the property is not found, it will prompt for login.
     *
     * to make not enabling this less annoying, unimined will always store the credentials in-memory for the gradle daemon.
     */
    var storeCredentials: Boolean

    /**
     * when enabled, unimined will store a key for decrypting the credential file to the os's keychain using java-keyring.
     * if disabled, the tokens will be stored in plain text!!!
     * this value can also be set by the `unimined.auth.encryptStoredCredentials` gradle property.
     *
     * do note, that due to OS limitations, the keyring may be fully accessible to other java applications,
     * or even other applications entirely. see: https://github.com/javakeyring/java-keyring?tab=readme-ov-file#security-concerns
     */
    var encryptStoredCredentials: Boolean

    @get:ApiStatus.Internal
    @set:ApiStatus.Experimental
    var authInfo: AuthInfo?

    data class AuthInfo(
        val username: String,
        val uuid: UUID,
        val accessToken: String,
    )

}