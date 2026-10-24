package com.liubai.data.repository

import com.liubai.data.db.AppLimitEntity
import com.liubai.data.db.LiubaiDatabase
import com.liubai.domain.model.AppLimit
import com.liubai.domain.repository.LimitsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LimitsRepositoryImpl(private val db: LiubaiDatabase) : LimitsRepository {

    private val dao = db.limitDao()

    override fun observeLimits(): Flow<List<AppLimit>> =
        dao.observeAll().map { list ->
            list.map { AppLimit(it.packageName, it.limitMs, it.enabled, it.hardLock) }
        }

    override suspend fun setLimit(limit: AppLimit) =
        dao.upsert(
            AppLimitEntity(limit.packageName, limit.limitMs, limit.enabled, limit.hardLock)
        )

    override suspend fun removeLimit(packageName: String) = dao.delete(packageName)

    override suspend fun listLimits(): List<AppLimit> =
        dao.listAll().map { AppLimit(it.packageName, it.limitMs, it.enabled, it.hardLock) }

    override suspend fun limitOf(packageName: String): AppLimit? =
        dao.get(packageName)?.let { AppLimit(it.packageName, it.limitMs, it.enabled, it.hardLock) }
}
