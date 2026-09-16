package com.venera.compose.feature.sourcemanage

data class RepoSourceItem(
    val name: String,
    val fileName: String,
    val key: String,
    val version: String,
    val description: String? = null
)

sealed class SourceSettingItem(
    open val key: String,
    open val title: String
) {
    data class Input(
        override val key: String,
        override val title: String,
        val value: String,
        val defaultValue: String = "",
        val validator: String? = null
    ) : SourceSettingItem(key, title)

    data class Select(
        override val key: String,
        override val title: String,
        val value: String,
        val defaultValue: String = "",
        val options: List<SelectOption>
    ) : SourceSettingItem(key, title)

    data class Switch(
        override val key: String,
        override val title: String,
        val value: Boolean,
        val defaultValue: Boolean = false
    ) : SourceSettingItem(key, title)

    data class Callback(
        override val key: String,
        override val title: String,
        val buttonText: String = "点击执行"
    ) : SourceSettingItem(key, title)
}

data class SelectOption(
    val value: String,
    val text: String
)

data class SourceAccountInfo(
    val hasAccount: Boolean = false,
    val isLogged: Boolean = false,
    val username: String? = null,
    val infoItems: List<Pair<String, String>> = emptyList(),
    val supportsLogin: Boolean = false,
    val loginWebsite: String? = null,
    val registerWebsite: String? = null
)

data class SourceConfigBundle(
    val sourceKey: String,
    val sourceName: String,
    val version: String,
    val isJsSource: Boolean,
    val settings: List<SourceSettingItem> = emptyList(),
    val accountInfo: SourceAccountInfo = SourceAccountInfo()
)
