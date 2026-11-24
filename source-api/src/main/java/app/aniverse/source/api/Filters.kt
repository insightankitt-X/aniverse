package app.aniverse.source.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Search filters returned by [Source.getFilterList]. The app renders this list, lets the user
 * change the state on a copy and sends the copy back with [Source.search].
 */
@Serializable
data class FilterList(val filters: List<Filter> = emptyList()) {

    /** True when there is nothing for the user to change, so the filter button is hidden. */
    val isEmpty: Boolean
        get() = filters.none {
            it is Filter.Text || it is Filter.CheckBox || it is Filter.TriState ||
                it is Filter.Select || it is Filter.Sort
        }

    /** Short labels for the active-filter chips, e.g. "Genre: Action". */
    fun activeSummary(): List<String> = filters.mapNotNull { filter ->
        when (filter) {
            is Filter.Text -> filter.value.trim().takeIf { it.isNotEmpty() }?.let { "${filter.title}: $it" }
            is Filter.CheckBox -> if (filter.state) filter.title else null
            is Filter.TriState -> if (filter.state != TriState.IGNORE) "${filter.title}: ${filter.state.name.lowercase()}" else null
            is Filter.Select -> filter.options.getOrNull(filter.selectedIndex)?.name
                ?.takeIf { it != filter.title }
                ?.let { "${filter.title}: $it" }
            is Filter.Sort -> null
            is Filter.Header, is Filter.Separator, is Filter.Group -> null
        }
    }

    /** Resets every control back to its default while keeping the same structure. */
    fun cleared(): FilterList = copy(filters = filters.map { filter ->
        when (filter) {
            is Filter.Text -> filter.copy(value = "")
            is Filter.CheckBox -> filter.copy(state = false)
            is Filter.TriState -> filter.copy(state = TriState.IGNORE)
            is Filter.Select -> filter.copy(selectedIndex = 0)
            is Filter.Sort -> filter.copy(selectedIndex = 0, ascending = true)
            is Filter.Group -> filter.copy(filters = FilterList(filter.filters).cleared().filters)
            is Filter.Header, is Filter.Separator -> filter
        }
    })

    companion object {
        val Empty = FilterList()
    }
}

@Serializable
enum class TriState {
    @SerialName("ignore") IGNORE,
    @SerialName("include") INCLUDE,
    @SerialName("exclude") EXCLUDE,
}

@Serializable
data class SelectOption(val name: String, val value: String = name)

@Serializable
sealed interface Filter {
    val key: String
    val title: String

    @Serializable
    @SerialName("header")
    data class Header(override val key: String, override val title: String) : Filter

    @Serializable
    @SerialName("separator")
    data class Separator(override val key: String = "separator", override val title: String = "") : Filter

    @Serializable
    @SerialName("text")
    data class Text(
        override val key: String,
        override val title: String,
        val value: String = "",
    ) : Filter

    @Serializable
    @SerialName("checkbox")
    data class CheckBox(
        override val key: String,
        override val title: String,
        val state: Boolean = false,
    ) : Filter

    @Serializable
    @SerialName("tristate")
    data class TriState(
        override val key: String,
        override val title: String,
        val state: app.aniverse.source.api.TriState = app.aniverse.source.api.TriState.IGNORE,
    ) : Filter

    @Serializable
    @SerialName("select")
    data class Select(
        override val key: String,
        override val title: String,
        val options: List<SelectOption> = emptyList(),
        val selectedIndex: Int = 0,
    ) : Filter

    @Serializable
    @SerialName("sort")
    data class Sort(
        override val key: String,
        override val title: String,
        val options: List<SelectOption> = emptyList(),
        val selectedIndex: Int = 0,
        val ascending: Boolean = true,
    ) : Filter

    @Serializable
    @SerialName("group")
    data class Group(
        override val key: String,
        override val title: String,
        val filters: List<Filter> = emptyList(),
    ) : Filter
}
