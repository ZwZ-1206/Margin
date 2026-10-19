package com.liubai.data.repository

import com.liubai.data.db.AppUsageDailyEntity
import com.liubai.data.db.FocusSessionEntity
import com.liubai.data.db.TagEntity
import com.liubai.data.db.TimerCheckpointEntity
import com.liubai.domain.model.AppUsageDaily
import com.liubai.domain.model.FocusMode
import com.liubai.domain.model.FocusSessionRecord
import com.liubai.domain.model.SessionStatus
import com.liubai.domain.model.Tag
import com.liubai.domain.model.TimerPhase
import com.liubai.domain.model.TimerState

fun FocusSessionEntity.toDomain() = FocusSessionRecord(
    id = id,
    mode = runCatching { FocusMode.valueOf(mode) }.getOrDefault(FocusMode.COUNTDOWN),
    tagId = tagId,
    startedAt = startedAt,
    endedAt = endedAt,
    durationMs = durationMs,
    status = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.INTERRUPTED),
    cycles = cycles,
)

fun FocusSessionRecord.toEntity() = FocusSessionEntity(
    id = id,
    mode = mode.name,
    tagId = tagId,
    startedAt = startedAt,
    endedAt = endedAt,
    durationMs = durationMs,
    status = status.name,
    cycles = cycles,
)

fun TagEntity.toDomain() = Tag(id = id, name = name, emoji = emoji, createdAt = createdAt, archived = archived)

fun Tag.toEntity() = TagEntity(id = id, name = name, emoji = emoji, createdAt = createdAt, archived = archived)

fun AppUsageDailyEntity.toDomain() =
    AppUsageDaily(date = date, packageName = packageName, totalMs = totalMs, openCount = openCount, estimatedMs = estimatedMs)

fun AppUsageDaily.toEntity() =
    AppUsageDailyEntity(date = date, packageName = packageName, totalMs = totalMs, openCount = openCount, estimatedMs = estimatedMs)

fun TimerCheckpointEntity.toTimerState() = TimerState(
    mode = runCatching { FocusMode.valueOf(mode) }.getOrDefault(FocusMode.COUNTDOWN),
    phase = runCatching { TimerPhase.valueOf(phase) }.getOrDefault(TimerPhase.IDLE),
    running = running,
    tagId = tagId,
    focusTargetMs = focusTargetMs,
    focusElapsedMs = focusElapsedMs,
    segmentStartMs = segmentStartMs,
    breakTargetMs = breakTargetMs,
    breakElapsedMs = breakElapsedMs,
    breakSegmentStartMs = breakSegmentStartMs,
    cycleIndex = cycleIndex,
    sessionFocusMs = sessionFocusMs,
    sessionStartedAt = sessionStartedAt,
)

fun TimerState.toCheckpoint(savedAt: Long) = TimerCheckpointEntity(
    mode = mode.name,
    phase = phase.name,
    running = running,
    tagId = tagId,
    focusTargetMs = focusTargetMs,
    focusElapsedMs = focusElapsedMs,
    segmentStartMs = segmentStartMs,
    breakTargetMs = breakTargetMs,
    breakElapsedMs = breakElapsedMs,
    breakSegmentStartMs = breakSegmentStartMs,
    cycleIndex = cycleIndex,
    sessionFocusMs = sessionFocusMs,
    sessionStartedAt = sessionStartedAt,
    savedAt = savedAt,
)
