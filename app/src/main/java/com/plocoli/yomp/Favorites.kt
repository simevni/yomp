package com.plocoli.yomp

import kotlinx.serialization.Serializable

/** One home rail. title = what's shown on screen, query = the actual Spotify search term (defaults to title). */
@Serializable
data class Fav(val title: String, val query: String = title)

/**
 * Starter set of parent-curated "rails" shown on the Home screen.
 *
 * These are placeholder examples — replace them with whatever suits your child.
 * You don't have to edit this file to change them: use the in-app editor
 * (pencil icon on Home). This list is only the first-run default; once edited,
 * the list is stored on the device (see FavoritesStore).
 *
 * Notes:
 * - Spotify blocks its genre/recommendation APIs for new (development-mode) apps,
 *   so a "genre" is expressed as concrete artist/movie search terms.
 * - If a name collides with a common word, set an explicit `query`
 *   (e.g. title "Frozen" but query "Disney Frozen" so unrelated songs don't show up).
 * - For non-English songs, put the original-language text in `query` for accurate results.
 */
object Favorites {
    val items: List<Fav> = listOf(
        Fav("Nursery Rhymes"),
        Fav("Lullabies"),
        Fav("Kids Pop", query = "Kidz Bop"),
        Fav("Disney"),
        Fav("Frozen", query = "Disney Frozen"),   // explicit query so unrelated "Frozen" songs are filtered out
        Fav("Moana"),
    )
}
