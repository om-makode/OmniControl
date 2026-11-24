package com.omnicontrol.feature.agent.loop

import com.omnicontrol.core.model.AssistantState
import com.omnicontrol.core.model.ChatItem

interface AgentEventListener {
    fun onStateChanged(state: AssistantState)
    fun onMessage(item: ChatItem)
    fun onCurrentApp(appLabel: String)
}
