package com.liubai.data.repository

import com.liubai.data.db.LiubaiDatabase
import com.liubai.domain.model.FocusDaily
import com.liubai.domain.model.FocusSessionRecord
import com.liubai.domain.model.Tag
import com.liubai.domain.model.TimerState
import com.liubai.domain.repository.FocusRepository
import com.liubai.core.common.DateKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FocusRepositoryImpl(private val db: LiubaiDatabase) : FocusRepository {

    private val focusDao = db.focusDao()
    private val checkpointDao = db.checkpointDao()

    override fun observeRecentSessions(limit: Int): Flow<List<FocusSessionRecord>> =
        focusDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeSessionsBetween(fromDate: String, toDate: String): Flow<List<FocusSessionRecord>> =
        focusDao.observeBetween(DateKeys.startOfDay(fromDate), DateKeys.endOfDay(toDate))
            .map { list -> list.map { it.toDomain() } }

    override fun observeTags(): Flow<List<Tag>> =
        focusDao.observeTags().map { list -> list.map { it.toDomain() } }

    override fun observeDaily(rangeDays: Int): Flow<List<FocusDaily>> {
        val to = DateKeys.today()
        val from = java.time.LocalDate.parse(to).minusDays(rangeDays.toLong()).toString()
        return focusDao.observeBetween(DateKeys.startOfDay(from), DateKeys.endOfDay(to))
            .map { sessions ->
                sessions.map { it.toDomain() }
                    .groupBy { DateKeys.of(it.startedAt) }
                    .map { (date, list) ->
                        FocusDaily(
                            date = date,
                            focusMs = list.sumOf { it.durationMs },
                            completedCount = list.count { it.status.name == "COMPLETED" },
                            interruptCount = list.count { it.status.name == "INTERRUPTED" },
                        )
                    }
                    .sortedBy { it.date }
            }
    }

    override suspend fun saveSession(record: FocusSessionRecord): Long =
        focusDao.insert(record.toEntity())

    override suspend fun saveTag(tag: Tag): Long =
        focusDao.insertTag(tag.toEntity())

    override suspend fun deleteTag(id: Long) = focusDao.deleteTag(id)

    override suspend fun loadCheckpoint(): TimerState? =
        checkpointDao.load()?.toTimerState()?.takeIf { it.isActive || it.phase.name == "FINISHED" }

    override fun observeCheckpoint(): Flow<TimerState?> =
        checkpointDao.observe().map { it?.toTimerState() }

    override suspend fun saveCheckpoint(state: TimerState) {
        checkpointDao.save(state.toCheckpoint(System.currentTimeMillis()))
    }

    override suspend fun clearCheckpoint() = checkpointDao.clear()
}
