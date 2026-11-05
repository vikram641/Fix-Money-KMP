package com.fixmoney.shared.export

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToURL
import platform.Foundation.create

/**
 * iOS has no shared Downloads folder to write into, so the file goes to the
 * app's own **Documents** directory.
 *
 * That directory is deliberate rather than a temp one: with
 * `UIFileSharingEnabled` / `LSSupportsOpeningDocumentsInPlace` in `Info.plist`
 * it is exactly what the Files app shows under "On My iPhone > FixMoney", which
 * is the closest iOS equivalent of the Android behaviour. **Those two keys are
 * not set yet**, so for now the file is written and readable by the app but the
 * user cannot browse to it - see CLAUDE.md. A share sheet is the other half and
 * needs a `UIViewController`, which lives in `composeApp`, not here.
 */
actual class ExportFileWriter {

    // writeToURL's error-out parameter is a CPointer, which is ExperimentalForeignApi
    // in Kotlin/Native 2.2. Passing null is the documented "I don't want the
    // NSError" form, so nothing here actually depends on the unstable surface.
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun write(fileName: String, csv: String): ExportLocation {
        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: error("No documents directory")

        val url = NSURL.fileURLWithPath("$documents/$fileName")
        val ok = (csv as NSString).writeToURL(url, true, NSUTF8StringEncoding, null)
        if (!ok) error("Could not write the export file")
        return ExportLocation(
            display = "Files > On My iPhone > FixMoney/$fileName",
            handle = "$documents/$fileName"
        )
    }

    /**
     * Not wired on iOS. Handing a file to another app means presenting a
     * `UIActivityViewController`, which needs a `UIViewController` - and this
     * class lives in `shared`, which has no UIKit view hierarchy to present
     * from. Returning false makes the UI omit the action rather than offer a
     * button that does nothing. Move this into `composeApp` when iOS sharing is
     * built, alongside the `Info.plist` keys that make the Documents folder
     * browsable.
     */
    actual fun open(location: ExportLocation): Boolean = false
}
