package com.neuralcast.radioplayer.data

import com.neuralcast.radioplayer.R
import com.neuralcast.radioplayer.model.RadioStation

object StationProvider {
    val stations = listOf(
        RadioStation(
            id = "neuralcast",
            name = "NeuralCast",
            streamUrl = "https://neuralcast.duckdns.org/listen/neuralcast/radio.mp3",
            backgroundResId = R.drawable.neuralcast_bg,
            artworkResId = R.drawable.neuralcast_art,
            description = "The future of broadcasting."
        ),
        RadioStation(
            id = "neuralforge",
            name = "NeuralForge",
            streamUrl = "https://neuralcast.duckdns.org/listen/neuralforge/radio.mp3",
            backgroundResId = R.drawable.neuralforge_bg,
            artworkResId = R.drawable.neuralforge_art,
            description = "Forging new sounds."
        )
    )

    fun getStation(id: String): RadioStation? = stations.find { it.id == id }
}
