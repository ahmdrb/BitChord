package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The two badge locations used by YouTube Music's playlist and artist rows. */
class ExplicitBadgeTest {

    private fun response(): kotlinx.serialization.json.JsonObject = Json.parseToJsonElement(
        """
        {
          "contents": {
            "twoColumnBrowseResultsRenderer": {
              "secondaryContents": {
                "musicPlaylistShelfRenderer": {
                  "contents": [
                    {
                      "musicResponsiveListItemRenderer": {
                        "playlistItemData": { "videoId": "explicit-badges-row", "playlistSetVideoId": "set-1" },
                        "flexColumns": [
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Badged from badges" } ] } } },
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Artist" } ] } } }
                        ],
                        "badges": [
                          { "musicInlineBadgeRenderer": { "icon": { "iconType": "MUSIC_EXPLICIT_BADGE" } } }
                        ]
                      }
                    },
                    {
                      "musicResponsiveListItemRenderer": {
                        "playlistItemData": { "videoId": "explicit-subtitle-row", "playlistSetVideoId": "set-2" },
                        "flexColumns": [
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Badged from subtitleBadges" } ] } } },
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Artist" } ] } } }
                        ],
                        "subtitleBadges": [
                          { "musicInlineBadgeRenderer": { "icon": { "iconType": "MUSIC_EXPLICIT_BADGE" } } }
                        ]
                      }
                    },
                    {
                      "musicResponsiveListItemRenderer": {
                        "playlistItemData": { "videoId": "explicit-accessibility-row", "playlistSetVideoId": "set-3" },
                        "flexColumns": [
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Badged from accessibility label" } ] } } },
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Artist" } ] } } }
                        ],
                        "badges": [
                          { "musicInlineBadgeRenderer": { "accessibilityData": { "accessibilityData": { "label": "Explicit" } } } }
                        ]
                      }
                    },
                    {
                      "musicResponsiveListItemRenderer": {
                        "playlistItemData": { "videoId": "explicit-boolean-row", "playlistSetVideoId": "set-4" },
                        "isExplicit": true,
                        "flexColumns": [
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Badged from direct flag" } ] } } },
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Artist" } ] } } }
                        ]
                      }
                    },
                    {
                      "musicResponsiveListItemRenderer": {
                        "playlistItemData": { "videoId": "not-explicit-row", "playlistSetVideoId": "set-5" },
                        "flexColumns": [
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Clean track" } ] } } },
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Artist" } ] } } }
                        ]
                      }
                    }
                  ]
                }
              }
            }
          }
        }
        """,
    ).jsonObject

    @Test
    fun playlistRowsPreserveExplicitBadgeFromEitherBadgeCollection() {
        val songs = InnertubeParser.parsePlaylistShelf(response())!!.songs

        assertEquals(
            listOf(
                "explicit-badges-row",
                "explicit-subtitle-row",
                "explicit-accessibility-row",
                "explicit-boolean-row",
                "not-explicit-row",
            ),
            songs.map { it.videoId },
        )
        assertEquals(true, songs[0].isExplicit)
        assertEquals(true, songs[1].isExplicit)
        assertEquals(true, songs[2].isExplicit)
        assertEquals(true, songs[3].isExplicit)
        assertNull(songs[4].isExplicit)
    }

    @Test
    fun searchRowsCarryExplicitFlagThroughTheSearchParser() {
        val search = Json.parseToJsonElement(
            """
            {
              "contents": {
                "musicShelfRenderer": {
                  "contents": [
                    {
                      "musicResponsiveListItemRenderer": {
                        "overlay": {
                          "musicItemThumbnailOverlayRenderer": {
                            "content": {
                              "musicPlayButtonRenderer": {
                                "playNavigationEndpoint": {
                                  "watchEndpoint": { "videoId": "search-explicit-track" }
                                }
                              }
                            }
                          }
                        },
                        "flexColumns": [
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Explicit search track" } ] } } },
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Artist" } ] } } }
                        ],
                        "badges": [
                          { "musicInlineBadgeRenderer": { "icon": { "iconType": "MUSIC_EXPLICIT_BADGE" } } }
                        ]
                      }
                    }
                  ]
                }
              }
            }
            """,
        ).jsonObject

        val song = InnertubeParser.parseSearchSongs(search).single()
        assertEquals("search-explicit-track", song.videoId)
        assertEquals(true, song.isExplicit)
    }
}
