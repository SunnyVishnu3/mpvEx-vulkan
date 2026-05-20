package com.astrixforge.devicemasker.service.diagnostics

import android.content.Context
import android.os.Build
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

object SecurityStateDiagnostics {

    private const val ADVANCED_PROTECTION_MANAGER =
        "android.security.advancedprotection.AdvancedProtectionManager"
    private const val ADVANCED_PROTECTION_METHOD = "isAdvancedProtectionEnabled"
    private const val BIOMETRIC_AUTHENTICATORS =
        "android.hardware.biometrics.BiometricManager\$Authenticators"
    private const val IDENTITY_CHECK_FIELD = "IDENTITY_CHECK"

    fun snapshotFile(context: Context): Pair<String, String> =
        "security_state_snapshot.json" to DiagnosticJson.encodeToString(capture(context))

    fun capture(context: Context): SecurityStateSnapshot {
        val advancedProtection = readAdvancedProtectionMode(context)
        val identityCheck = readIdentityCheckAuthenticator()
        return SecurityStateSnapshot(
            androidSdk = Build.VERSION.SDK_INT,
            advancedProtectionApiAvailable = advancedProtection.apiAvailable,
            advancedProtectionEnabled = advancedProtection.enabled,
            advancedProtectionStatus = advancedProtection.status,
            identityCheckAuthenticatorAvailable = identityCheck.available,
            identityCheckAuthenticatorValue = identityCheck.value,
            identityCheckStatus = identityCheck.status,
        )
    }

    private fun readAdvancedProtectionMode(context: Context): AdvancedProtectionSnapshot =
        try {
            val managerClass = Class.forName(ADVANCED_PROTECTION_MANAGER)
            val manager =
                context.getSystemService(managerClass)
                    ?: return AdvancedProtectionSnapshot(apiAvailable = true, status = "NO_SERVICE")
            val enabled =
                managerClass.getMethod(ADVANCED_PROTECTION_METHOD).invoke(manager) as? Boolean
            AdvancedProtectionSnapshot(
                apiAvailable = true,
                enabled = enabled,
                status = if (enabled == null) "UNEXPECTED_RESULT" else "OK",
            )
        } catch (e: ClassNotFoundException) {
            AdvancedProtectionSnapshot(apiAvailable = false, status = e.unavailableStatus())
        } catch (_: NoSuchMethodException) {
            AdvancedProtectionSnapshot(apiAvailable = true, status = "METHOD_UNAVAILABLE")
        } catch (e: SecurityException) {
            AdvancedProtectionSnapshot(apiAvailable = true, status = e.deniedStatus())
        } catch (e: ReflectiveOperationException) {
            AdvancedProtectionSnapshot(apiAvailable = true, status = e.javaClass.simpleName)
        } catch (e: IllegalArgumentException) {
            AdvancedProtectionSnapshot(apiAvailable = true, status = e.javaClass.simpleName)
        } catch (e: IllegalStateException) {
            AdvancedProtectionSnapshot(apiAvailable = true, status = e.javaClass.simpleName)
        }

    private fun readIdentityCheckAuthenticator(): IdentityCheckSnapshot =
        try {
            val authenticatorsClass = Class.forName(BIOMETRIC_AUTHENTICATORS)
            val value = authenticatorsClass.getField(IDENTITY_CHECK_FIELD).getInt(null)
            IdentityCheckSnapshot(available = true, value = value, status = "OK")
        } catch (e: ClassNotFoundException) {
            IdentityCheckSnapshot(available = false, status = e.unavailableStatus())
        } catch (_: NoSuchFieldException) {
            IdentityCheckSnapshot(available = false, status = "FIELD_UNAVAILABLE")
        } catch (e: IllegalAccessException) {
            IdentityCheckSnapshot(available = true, status = e.javaClass.simpleName)
        } catch (e: IllegalArgumentException) {
            IdentityCheckSnapshot(available = true, status = e.javaClass.simpleName)
        } catch (e: IllegalStateException) {
            IdentityCheckSnapshot(available = true, status = e.javaClass.simpleName)
        }
}

private fun ClassNotFoundException.unavailableStatus(): String =
    "API_UNAVAILABLE:${javaClass.simpleName}"

private fun SecurityException.deniedStatus(): String = "PERMISSION_DENIED:${javaClass.simpleName}"

@Serializable
data class SecurityStateSnapshot(
    val androidSdk: Int,
    val advancedProtectionApiAvailable: Boolean,
    val advancedProtectionEnabled: Boolean?,
    val advancedProtectionStatus: String,
    val identityCheckAuthenticatorAvailable: Boolean,
    val identityCheckAuthenticatorValue: Int?,
    val identityCheckStatus: String,
)

private data class AdvancedProtectionSnapshot(
    val apiAvailable: Boolean,
    val enabled: Boolean? = null,
    val status: String,
)

private data class IdentityCheckSnapshot(
    val available: Boolean,
    val value: Int? = null,
    val status: String,
)
