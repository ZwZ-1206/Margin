package com.liubai.data.di

import android.content.Context
import com.liubai.data.db.LiubaiDatabase
import com.liubai.data.datastore.SettingsRepositoryImpl
import com.liubai.data.repository.FocusRepositoryImpl
import com.liubai.data.repository.LimitsRepositoryImpl
import com.liubai.data.repository.UsageRepositoryImpl
import com.liubai.data.usage.UsageStatsReader
import com.liubai.domain.repository.AppContainer
import com.liubai.domain.repository.FocusRepository
import com.liubai.domain.repository.LimitsRepository
import com.liubai.domain.repository.SettingsRepository
import com.liubai.domain.repository.UsageRepository

/**
 * 手动依赖容器。由 Application 初始化一次，Service / Receiver 直接读取，
 * 避免 Hilt 的注解处理成本，同时保持可测试性（接口边界在 domain）。
 */
object ServiceLocator : AppContainer {

    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val db: LiubaiDatabase by lazy { LiubaiDatabase.get(appContext) }
    private val usageReader: UsageStatsReader by lazy { UsageStatsReader(appContext) }

    override val focusRepository: FocusRepository by lazy { FocusRepositoryImpl(db) }
    override val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(appContext) }
    override val usageRepository: UsageRepository by lazy { UsageRepositoryImpl(db, usageReader) }
    override val limitsRepository: LimitsRepository by lazy { LimitsRepositoryImpl(db) }

    val database: LiubaiDatabase get() = db
}
