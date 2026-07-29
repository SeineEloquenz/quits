package nz.eloque.quits.util

/** Triggers a browser download via an object URL and a synthetic anchor click. */
class WebFileExporter : FileExporter {
    override fun export(
        fileName: String,
        mimeType: String,
        content: String,
    ) {
        downloadFile(fileName, mimeType, content)
    }
}

private fun downloadFile(
    fileName: String,
    mimeType: String,
    content: String,
) {
    js(
        """{
        var blob = new Blob([content], { type: mimeType });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }""",
    )
}
