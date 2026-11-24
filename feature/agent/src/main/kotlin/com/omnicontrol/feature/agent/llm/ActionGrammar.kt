package com.omnicontrol.feature.agent.llm

import com.omnicontrol.core.model.AgentAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AgentPlan(
    val thought: String = "",
    val action: AgentAction,
    val say: String? = null
)

object ActionGrammar {

    const val GBNF_GRAMMAR = """root ::= "{" ws "\"thought\":" ws string "," ws "\"action\":" ws action ("," ws "\"say\":" ws string)? ws "}"
action ::= "{" ws "\"type\":" ws type ("," ws action_fields)? ws "}"
type ::= "\"click\"" | "\"long_press\"" | "\"type_text\"" | "\"clear_text\"" | "\"scroll\"" | "\"open_app\"" | "\"open_url\"" | "\"back\"" | "\"home\"" | "\"recents\"" | "\"notifications\"" | "\"quick_settings\"" | "\"select\"" | "\"wait\"" | "\"read_screen\"" | "\"ask_user\"" | "\"done\""
action_fields ::= field ("," ws field)*
field ::= "\"target\":" ws number | "\"app\":" ws string | "\"url\":" ws string | "\"text\":" ws string | "\"submit\":" ws boolean | "\"direction\":" ws ("\"down\"" | "\"up\"" | "\"left\"" | "\"right\"") | "\"ms\":" ws number | "\"question\":" ws string | "\"summary\":" ws string
string ::= "\"" [^"\\\n]* "\""
number ::= [0-9]+
boolean ::= "true" | "false"
ws ::= [ \t\n]*
"""

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parsePlan(jsonString: String): Result<AgentPlan> {
        return try {
            val plan = json.decodeFromString<AgentPlan>(jsonString.trim())
            Result.success(plan)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
