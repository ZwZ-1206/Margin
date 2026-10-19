package com.liubai.feature.focus

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.liubai.core.common.DurationFormat
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 专注结果分享卡片：纯 Canvas 绘制（无第三方库），保存到系统相册。
 * 内容为本次专注时长、标签、日期与品牌字标。
 */
object ShareCard {

    private const val W = 1080
    private const val H = 1440

    fun render(durationMs: Long, tagLabel: String?, cycles: Int, completed: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // 奶油纸底
        c.drawColor(Color.parseColor("#F1EEE7"))

        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B1915") }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#98907F") }
        val teal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A7A61") }

        // 顶部字标
        ink.textSize = 40f
        c.drawText("MARGIN", 96f, 150f, muted)
        ink.textSize = 76f
        c.drawText("「留白」", 96f, 250f, ink)

        // 留白之环
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6E1D6"); style = Paint.Style.STROKE; strokeWidth = 28f; strokeCap = Paint.Cap.ROUND
        }
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A7A61"); style = Paint.Style.STROKE; strokeWidth = 28f; strokeCap = Paint.Cap.ROUND
        }
        val oval = RectF(W / 2f - 260f, 420f, W / 2f + 260f, 940f)
        c.drawArc(oval, 300f, 300f, false, ringPaint)
        c.drawArc(oval, 300f, 300f, false, arcPaint)

        // 中央时长
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B1915"); textSize = 132f
        }
        val text = DurationFormat.chinese(durationMs)
        val tw = big.measureText(text)
        c.drawText(text, W / 2f - tw / 2, 700f, big)

        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6E675C"); textSize = 40f }
        val subText = if (completed) "完成" else "中断"
        val sw = sub.measureText(subText)
        c.drawText(subText, W / 2f - sw / 2, 770f, sub)

        // 标签与日期
        var y = 1080f
        if (!tagLabel.isNullOrBlank()) {
            teal.textSize = 46f
            c.drawText(tagLabel, 96f, y, teal)
            y += 70f
        }
        muted.textSize = 38f
        val dateText = Instant.now().atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
        c.drawText(dateText, 96f, y, muted)
        if (cycles > 0) {
            c.drawText("· 完成 $cycles 轮", 96f + muted.measureText(dateText) + 20f, y, muted)
        }

        // 底部标语
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#98907F"); textSize = 36f }
        c.drawText("放下手机，为生活中的其他精彩留白", 96f, H - 90f, foot)
        return bmp
    }

    /** 保存到相册（Pictures/Margin），返回 content Uri */
    fun save(context: Context, bitmap: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Margin")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            val out: OutputStream? = resolver.openOutputStream(uri)
            out?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }.getOrNull()
    }
}
