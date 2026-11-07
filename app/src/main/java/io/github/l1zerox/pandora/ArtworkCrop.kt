package io.github.l1zerox.pandora

import kotlin.math.min
import kotlin.math.roundToInt

internal data class ArtworkCrop(val zoom: Float = 1f, val centerX: Float = .5f, val centerY: Float = .5f)
internal data class CropBounds(val left: Int, val top: Int, val size: Int)

internal fun boundedCrop(crop: ArtworkCrop, width: Int, height: Int): ArtworkCrop {
    require(width > 0 && height > 0)
    val zoom=crop.zoom.takeIf { it.isFinite() }?.coerceIn(1f,5f) ?: 1f
    val side=min(width,height)/zoom
    val halfX=side/(2*width); val halfY=side/(2*height)
    return ArtworkCrop(zoom,
        (crop.centerX.takeIf { it.isFinite() } ?: .5f).coerceIn(halfX,1-halfX),
        (crop.centerY.takeIf { it.isFinite() } ?: .5f).coerceIn(halfY,1-halfY))
}

/** The preview and exported bitmap use this exact same pixel rectangle. */
internal fun artworkCropBounds(crop: ArtworkCrop, width: Int, height: Int): CropBounds {
    val valid=boundedCrop(crop,width,height)
    val side=(min(width,height)/valid.zoom).roundToInt().coerceIn(1,min(width,height))
    return CropBounds((valid.centerX*width-side/2f).roundToInt().coerceIn(0,width-side),
        (valid.centerY*height-side/2f).roundToInt().coerceIn(0,height-side),side)
}

/** Pan and focal point are fractions of the square preview, independent of screen density. */
internal fun transformArtworkCrop(crop: ArtworkCrop, width: Int, height: Int,
    panX: Float, panY: Float, zoomFactor: Float, focusX: Float = .5f, focusY: Float = .5f): ArtworkCrop {
    val old=boundedCrop(crop,width,height)
    val zoom=(old.zoom*zoomFactor.takeIf { it.isFinite() && it > 0 }.let { it ?: 1f }).coerceIn(1f,5f)
    val oldSide=min(width,height)/old.zoom; val newSide=min(width,height)/zoom
    return boundedCrop(ArtworkCrop(zoom,
        old.centerX+((focusX-.5f)*(oldSide-newSide)-panX*newSide)/width,
        old.centerY+((focusY-.5f)*(oldSide-newSide)-panY*newSide)/height),width,height)
}
