package nz.eloque.quits.application.transfer

import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.data.transfer.GroupJson
import nz.eloque.quits.data.transfer.exportFileName
import nz.eloque.quits.domain.GroupId

/** Exports a whole group as a lossless JSON file through the platform's save/share affordance. */
class ExportGroup(
    private val repo: GroupRepository,
    private val exporter: FileExporter,
) {
    suspend operator fun invoke(id: GroupId) {
        val group = repo.load(id) ?: return
        exporter.export(exportFileName(group.name), "application/json", GroupJson.encode(group))
    }
}
