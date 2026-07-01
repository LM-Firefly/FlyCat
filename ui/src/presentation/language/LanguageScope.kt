package com.github.yumelira.yumebox.presentation.language

enum class LanguageScope(val scopeName: String, val displayName: String) {
    Yaml("source.yaml", "YAML"),
    JavaScript("source.js", "JavaScript"),
    Json("source.json", "JSON"),
    Text("text.plain", "Plain Text");

    companion object {

        fun fromExtension(extension: String): LanguageScope {
            return when (extension.lowercase()) {
                "yaml",
                "yml" -> Yaml
                "js" -> JavaScript
                "json" -> Json
                else -> Text
            }
        }

        fun fromScopeName(scopeName: String): LanguageScope {
            return entries.find { it.scopeName == scopeName } ?: Text
        }
    }
}
