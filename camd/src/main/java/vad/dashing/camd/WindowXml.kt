package vad.dashing.camd

import java.util.Locale

/**
 * One qcarcam window. Fractions are of the head-unit display, same as the stock XML.
 * The running qcarcam_test reads this file once, so a move is a new launch.
 */
object WindowXml {

    fun build(input: CameraInput, x: Float, y: Float, w: Float, h: Float): String {
        val posX = fmt(x)
        val posY = fmt(y)
        val width = fmt(w)
        val height = fmt(h)
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <qcarcam_inputs>
                <input_device>
                    <properties input_id="${input.id}"/>
                    <display_setting display_id="0" nbufs="3"
                        window_width="$width" window_height="$height" window_pos_x="$posX" window_pos_y="$posY" />
                    <output_setting width="${input.width}" height="${input.height}" nbufs="5" />
                </input_device>
            </qcarcam_inputs>
        """.trimIndent() + "\n"
    }

    private fun fmt(value: Float): String = String.format(Locale.US, "%.4f", value)
}
