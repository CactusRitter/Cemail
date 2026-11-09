package com.cemail.app.data.mail

import android.content.Context
import com.cemail.app.R

/** Example messages (texts come from resources, so they follow the system language). */
object DemoData {
    fun seed(context: Context, now: Long = System.currentTimeMillis()): List<Mail> {
        val min = 60_000L
        val h = 60 * min
        val d = 24 * h
        fun s(id: Int) = context.getString(id)
        return listOf(
            Mail(1, s(R.string.demo_1_name), "jonathan@example.com", s(R.string.demo_1_subject), s(R.string.demo_1_body), now - 12 * min, pinned = true),
            Mail(2, s(R.string.demo_2_name), "news@thunderbird.example", s(R.string.demo_2_subject), s(R.string.demo_2_body), now - 3 * h),
            Mail(3, s(R.string.demo_3_name), "digest@spark.example", s(R.string.demo_3_subject), s(R.string.demo_3_body), now - 5 * h, read = true),
            Mail(4, s(R.string.demo_4_name), "lucia@example.com", s(R.string.demo_4_subject), s(R.string.demo_4_body), now - 26 * h, starred = true, hasAttachment = true),
            Mail(5, s(R.string.demo_5_name), "hello@cenotes.example", s(R.string.demo_5_subject), s(R.string.demo_5_body), now - 2 * d, read = true),
            Mail(6, s(R.string.demo_6_name), "statements@banco.example", s(R.string.demo_6_subject), s(R.string.demo_6_body), now - 4 * d, pinned = true, read = true, hasAttachment = true),
            Mail(7, s(R.string.demo_7_name), "mateo@example.com", s(R.string.demo_7_subject), s(R.string.demo_7_body), now - 6 * d),
            Mail(8, s(R.string.demo_8_name), "hello@cemail.example", s(R.string.demo_8_subject), s(R.string.demo_8_body), now - 9 * d, read = true),
        )
    }
}
