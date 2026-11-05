package com.fixmoney.shared.coins

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

actual val platformName: String = "ios"

private const val SERVICE = "com.fixmoney.coins"
private const val ACCOUNT = "device-key"
private const val DEFAULTS_KEY = "coin_device_fallback"

/**
 * A random UUID kept in the Keychain. Keychain items outlive deleting the app,
 * so a reinstall finds the same wallet and trial - the iOS counterpart of
 * Android's ANDROID_ID.
 *
 * `AfterFirstUnlock` rather than `WhenUnlocked`: a Siri request can run the app
 * in the background while the phone is locked, and it must still be able to
 * read this. `ThisDeviceOnly` keeps it out of iCloud: the key identifies the
 * *device*; following the user to a new phone is the token's job.
 *
 * If the Keychain is unavailable (an unsigned simulator build can return
 * errSecMissingEntitlement) the id falls back to NSUserDefaults, which only
 * lasts as long as the install - the app keeps working, it just loses the
 * reinstall guarantee.
 */
class IosDeviceKeySource : DeviceKeySource {

    override fun deviceKey(): String {
        val id = readKeychain() ?: NSUUID().UUIDString.also { if (!writeKeychain(it)) return fallback() }
        return "i1-$id"
    }

    private fun fallback(): String {
        val defaults = NSUserDefaults.standardUserDefaults
        val id = defaults.stringForKey(DEFAULTS_KEY)
            ?: NSUUID().UUIDString.also { defaults.setObject(it, DEFAULTS_KEY) }
        return "i0-$id"
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readKeychain(): String? = memScoped {
        val query = CFDictionaryCreateMutable(null, 5, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, CFBridgingRetain(SERVICE))
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(ACCOUNT))
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writeKeychain(value: String): Boolean {
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        val item = CFDictionaryCreateMutable(null, 5, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(item, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(item, kSecAttrService, CFBridgingRetain(SERVICE))
        CFDictionaryAddValue(item, kSecAttrAccount, CFBridgingRetain(ACCOUNT))
        CFDictionaryAddValue(item, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        CFDictionaryAddValue(item, kSecValueData, CFBridgingRetain(data))
        return SecItemAdd(item, null) == errSecSuccess
    }
}
