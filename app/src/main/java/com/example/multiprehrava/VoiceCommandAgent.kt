package com.example.multiprehrava

object VoiceCommandAgent {

    sealed interface CommandResult {
        data class SearchAndPlay(val query: String) : CommandResult
        object PlayNext : CommandResult
        object PlayPrevious : CommandResult
        object Pause : CommandResult
        object Resume : CommandResult
        object Download : CommandResult
        data class ChangeQuality(val quality: String) : CommandResult
        object ExitApp : CommandResult
        data class Unknown(val rawText: String) : CommandResult
    }

    fun parseCommand(spokenText: String, langCode: String = "cs"): CommandResult {
        val text = spokenText.lowercase().trim()

        if (langCode == "en") {
            return when {
                text.contains("next") || text.contains("skip") -> CommandResult.PlayNext
                text.contains("previous") || text.contains("back") -> CommandResult.PlayPrevious
                text.contains("pause") || text.contains("stop") -> CommandResult.Pause
                text == "play" || text == "resume" || text == "continue" -> CommandResult.Resume
                text.contains("download") || text.contains("save") -> CommandResult.Download
                text.contains("quit") || text.contains("exit") || text.contains("close") -> CommandResult.ExitApp
                text.contains("1080") -> CommandResult.ChangeQuality("1080")
                text.contains("720") -> CommandResult.ChangeQuality("720")
                text.contains("480") -> CommandResult.ChangeQuality("480")
                text.contains("360") -> CommandResult.ChangeQuality("360")
                text.startsWith("play ") || text.startsWith("search ") || text.startsWith("find ") -> {
                    val query = text.replace(Regex("^(play|search|find)\\s+"), "").trim()
                    if (query.isNotBlank()) CommandResult.SearchAndPlay(query) else CommandResult.Unknown(spokenText)
                }
                text.isNotBlank() -> CommandResult.SearchAndPlay(text)
                else -> CommandResult.Unknown(spokenText)
            }
        }

        // Czech fallback
        return when {
            text.contains("další") || text.contains("dalsi") || text.contains("přeskočit") || text.contains("next") -> CommandResult.PlayNext
            text.contains("předchozí") || text.contains("predchozi") || text.contains("zpět") || text.contains("previous") -> CommandResult.PlayPrevious
            text.contains("pauza") || text.contains("zastav") || text.contains("pause") || text.contains("stop") -> CommandResult.Pause
            text.contains("spustit") || text.contains("pokračuj") || (text.contains("play") && !text.contains("hledej") && !text.contains("přehraj")) -> CommandResult.Resume
            text.contains("stáhnout") || text.contains("stahuj") || text.contains("download") -> CommandResult.Download
            text.contains("ukončit") || text.contains("zavřít") || text.contains("vypnout") || text.contains("exit") -> CommandResult.ExitApp
            text.contains("1080") -> CommandResult.ChangeQuality("1080")
            text.contains("720") -> CommandResult.ChangeQuality("720")
            text.contains("480") -> CommandResult.ChangeQuality("480")
            text.contains("360") -> CommandResult.ChangeQuality("360")
            text.startsWith("přehrát") || text.startsWith("prahraj") || text.startsWith("hledej") || text.startsWith("najdi") || text.startsWith("přehrát") || text.startsWith("spusť") -> {
                val query = text.replace(Regex("^(přehrát|prahraj|hledej|najdi|spusť|přehraj)\\s+"), "").trim()
                if (query.isNotBlank()) CommandResult.SearchAndPlay(query) else CommandResult.Unknown(spokenText)
            }
            text.isNotBlank() -> CommandResult.SearchAndPlay(text)
            else -> CommandResult.Unknown(spokenText)
        }
    }
}
