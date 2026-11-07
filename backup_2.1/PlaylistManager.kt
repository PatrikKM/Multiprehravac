package com.example.multiprehrava

object PlaylistManager {
    var activePlaylist: List<SearchResultItem> = emptyList()
    var currentPlaylistIndex: Int = -1

    fun setPlaylist(items: List<SearchResultItem>, startIndex: Int) {
        activePlaylist = items
        currentPlaylistIndex = startIndex
    }

    fun hasNext(): Boolean {
        return currentPlaylistIndex in 0 until (activePlaylist.size - 1)
    }

    fun hasPrevious(): Boolean {
        return currentPlaylistIndex > 0 && currentPlaylistIndex < activePlaylist.size
    }

    fun getNextTrack(): SearchResultItem? {
        if (hasNext()) {
            currentPlaylistIndex++
            return activePlaylist[currentPlaylistIndex]
        }
        return null
    }

    fun getPreviousTrack(): SearchResultItem? {
        if (hasPrevious()) {
            currentPlaylistIndex--
            return activePlaylist[currentPlaylistIndex]
        }
        return null
    }

    fun getCurrentTrack(): SearchResultItem? {
        if (currentPlaylistIndex in activePlaylist.indices) {
            return activePlaylist[currentPlaylistIndex]
        }
        return null
    }

    fun clear() {
        activePlaylist = emptyList()
        currentPlaylistIndex = -1
    }
}
