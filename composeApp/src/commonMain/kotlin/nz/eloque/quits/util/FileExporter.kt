package nz.eloque.quits.util

/**
 * Hands a generated text file to the platform's save/share affordance
 */
interface FileExporter {
    fun export(
        fileName: String,
        mimeType: String,
        content: String,
    )
}
