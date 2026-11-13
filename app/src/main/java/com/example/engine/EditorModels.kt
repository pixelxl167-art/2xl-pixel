package com.example.engine

import androidx.annotation.DrawableRes
import com.example.R

enum class EditorTool(val label: String) {
    ERASE_OBJECT("Hapus Objek"),
    ADJUST("Sesuaikan"),
    FILTER("Filter"),
    TRANSFORM("Transform")
}

enum class FilterPreset(val displayName: String) {
    ORIGINAL("Asli"),
    WARM("Hangat"),
    COOL("Sejuk"),
    MONOCHROME("Monokrom"),
    VINTAGE("Vintage"),
    VIVID("Tajam"),
    CINEMA("Sinema")
}

enum class OfflineInpaintQuality(val displayName: String, val description: String) {
    DETAILED_TEXTURE(
        "Detail Tekstur (Lokal)",
        "Analisis tambalan piksel lokal + difusi arah untuk hasil alami tanpa internet."
    ),
    FAST_DIFFUSION(
        "Cepat (Difusi Harmonik)",
        "Pengisian gradien batas cepat secara lokal untuk objek kecil dan goresan tipis."
    )
}

enum class SaveFormat(
    val extension: String,
    val mimeType: String,
    val label: String,
    val shortBadge: String
) {
    PNG_LOSSLESS(
        extension = "png",
        mimeType = "image/png",
        label = "PNG (Lossless Kualitas Tertinggi)",
        shortBadge = "PNG"
    ),
    JPEG_HIGH_98(
        extension = "jpg",
        mimeType = "image/jpeg",
        label = "JPEG (Kualitas Tinggi 98%)",
        shortBadge = "JPG 98%"
    )
}

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Bebas", null),
    SQUARE_1_1("1:1", 1f),
    LANDSCAPE_4_3("4:3", 4f / 3f),
    PORTRAIT_3_4("3:4", 3f / 4f),
    WIDE_16_9("16:9", 16f / 9f)
}

data class ImageAdjustments(
    val brightness: Float = 0f,     // -0.5f .. +0.5f
    val contrast: Float = 1f,       // 0.5f .. 1.6f
    val saturation: Float = 1f,     // 0f .. 2f
    val warmth: Float = 0f,         // -0.4f .. +0.4f
    val vignette: Float = 0f,       // 0f .. 0.8f
    val filterPreset: FilterPreset = FilterPreset.ORIGINAL
) {
    val isDefault: Boolean
        get() = brightness == 0f &&
            contrast == 1f &&
            saturation == 1f &&
            warmth == 0f &&
            vignette == 0f &&
            filterPreset == FilterPreset.ORIGINAL
}

data class NormalizedPoint(
    val x: Float,
    val y: Float
)

data class MaskStroke(
    val points: List<NormalizedPoint>,
    val brushRadiusNormalized: Float
)

data class SamplePhoto(
    val id: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val drawableRes: Int
)

object SamplePhotos {
    val items = listOf(
        SamplePhoto(
            id = "sample_beach",
            title = "Pantai Senja",
            subtitle = "Uji hapus pelampung merah di pasir",
            drawableRes = R.drawable.img_sample_beach
        ),
        SamplePhoto(
            id = "sample_street",
            title = "Kafe Sudut Kota",
            subtitle = "Uji hapus kerucut oranye di meja",
            drawableRes = R.drawable.img_sample_street
        )
    )
}
