@file:Suppress("ktlint:standard:property-naming", "PropertyName")

package eu.kanade.tachiyomi.animesource.model

/** The host-side instance behind [SEpisode.create]. */
class SEpisodeImpl : SEpisode {

    override lateinit var url: String

    override lateinit var name: String

    override var date_upload: Long = 0L

    override var episode_number: Float = -1f

    override var fillermark: Boolean = false

    override var scanlator: String? = null

    override var summary: String? = null

    override var preview_url: String? = null
}
