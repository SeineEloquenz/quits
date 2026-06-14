package nz.eloque.quits.data.sync

import nz.eloque.quits.data.db.ExpenseEntity
import nz.eloque.quits.data.db.ExpensePayerEntity
import nz.eloque.quits.data.db.ExpenseSplitEntity
import nz.eloque.quits.data.db.ExpenseWithLines
import nz.eloque.quits.data.db.GroupEntity
import nz.eloque.quits.data.db.MemberEntity
import nz.eloque.quits.data.db.SettlementEntity
import nz.eloque.quits.data.db.SyncMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordMapperTest {
    private val meta = SyncMeta(updatedAt = 5, deviceId = "dev", deleted = false, dirty = false)

    private fun reencode(record: SyncRecord): SyncRecord = record.copy(payload = SyncJson.decode(SyncJson.encode(record.payload)))

    @Test
    fun group_record_uses_constant_id_and_round_trips() {
        val group = GroupEntity("g1", "Trip", "USD", meta)
        val record = reencode(RecordMapper.record(group))
        assertEquals(RecordMapper.GROUP_RECORD_ID, record.id)
        // Group id is contextual: it comes from the container, not the payload.
        val back = RecordMapper.groupEntity(record.payload as SyncPayload.Group, "g1", RecordMapper.meta(record, dirty = false))
        assertEquals(group, back)
    }

    @Test
    fun member_round_trips() {
        val member = MemberEntity("m1", "g1", "Alice", color = 0xFF00FF, sync = meta)
        val record = reencode(RecordMapper.record(member))
        assertEquals("m1", record.id)
        val back = RecordMapper.memberEntity(record.payload as SyncPayload.Member, "g1", RecordMapper.meta(record, dirty = false))
        assertEquals(member, back)
    }

    @Test
    fun expense_with_lines_round_trips() {
        val expense =
            ExpenseWithLines(
                ExpenseEntity("e1", "g1", "Hotel", 10000, "EUR", 1.1, "lodging", 7, "two nights", "SHARES", meta),
                listOf(
                    ExpensePayerEntity("e1:payer:0", "e1", "m1", 6000),
                    ExpensePayerEntity("e1:payer:1", "e1", "m2", 4000),
                ),
                listOf(
                    ExpenseSplitEntity("e1:m1", "e1", "m1", 5000, weight = 2.0),
                    ExpenseSplitEntity("e1:m2", "e1", "m2", 5000, weight = 1.0),
                ),
            )
        val record = reencode(RecordMapper.record(expense))
        assertEquals("e1", record.id)
        val back = RecordMapper.expenseEntities(record.payload as SyncPayload.Expense, "g1", RecordMapper.meta(record, dirty = false))
        assertEquals(expense, back)
    }

    @Test
    fun settlement_round_trips() {
        val settlement = SettlementEntity("s1", "g1", "m2", "m1", 2500, "USD", 1.0, 9, note = null, sync = meta)
        val record = reencode(RecordMapper.record(settlement))
        assertEquals("s1", record.id)
        val back = RecordMapper.settlementEntity(record.payload as SyncPayload.Settlement, "g1", RecordMapper.meta(record, dirty = false))
        assertEquals(settlement, back)
    }

    @Test
    fun tombstone_and_meta_survive() {
        val member = MemberEntity("m1", "g1", "Bob", null, SyncMeta(updatedAt = 99, deviceId = "d2", deleted = true, dirty = false))
        val record = reencode(RecordMapper.record(member))
        assertTrue(record.deleted)
        assertEquals(99, record.updatedAt)
        assertEquals("d2", record.deviceId)
    }

    @Test
    fun payload_json_carries_a_type_discriminator() {
        val json = SyncJson.encode(SyncPayload.Member("m1", "Alice", null))
        assertTrue(json.contains("\"type\":\"member\""), "expected a type discriminator, got: $json")
    }
}
