package to.kuudere.anisuge.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StickerMessage(
    val id: String = "",
    val name: String = "",
    @SerialName("mediaType") val mediaType: String = "image",
    @SerialName("assetUrl") val assetUrl: String = "",
    @SerialName("thumbnailUrl") val thumbnailUrl: String? = null,
)

@Serializable
data class Sticker(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    @SerialName("mediaType") val mediaType: String = "image",
    @SerialName("assetUrl") val assetUrl: String = "",
    @SerialName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerialName("accessMode") val accessMode: String = "free",
    @SerialName("priceCoins") val priceCoins: Int = 0,
    val owned: Boolean = false,
) {
    fun toMessage(): StickerMessage = StickerMessage(
        id = id,
        name = name,
        mediaType = mediaType,
        assetUrl = assetUrl,
        thumbnailUrl = thumbnailUrl,
    )
}

@Serializable
data class StickerMeResponse(
    @SerialName("isPremium") val isPremium: Boolean = false,
    val stickers: List<Sticker> = emptyList(),
)
