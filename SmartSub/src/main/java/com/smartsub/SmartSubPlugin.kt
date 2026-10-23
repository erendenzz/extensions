package com.smartsub

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SmartSubPlugin : Plugin() {
    override fun load(context: Context) {
        registerSubtitleProvider(SmartSubProvider())
    }
}
