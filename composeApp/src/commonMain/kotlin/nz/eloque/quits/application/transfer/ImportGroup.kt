package nz.eloque.quits.application.transfer

import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.data.transfer.GroupImportException
import nz.eloque.quits.data.transfer.GroupJson
import nz.eloque.quits.domain.GroupId

sealed interface ImportResult {
    data class Imported(
        val id: GroupId,
    ) : ImportResult

    data class Invalid(
        val reason: String,
    ) : ImportResult
}

/** Creates a new, unshared local group from an exported group file. */
class ImportGroup(
    private val repo: GroupRepository,
) {
    suspend operator fun invoke(text: String): ImportResult {
        val group =
            try {
                GroupJson.decode(text)
            } catch (e: GroupImportException) {
                return ImportResult.Invalid(e.message.orEmpty())
            }
        repo.importGroup(group)
        return ImportResult.Imported(group.id)
    }
}
