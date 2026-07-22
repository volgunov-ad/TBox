package vad.dashing.tbox

/**
 * Interest / media source ids for main-screen panels.
 * Window-mode overlay must not share ids with fullscreen [MainActivity] panels so Activity
 * dispose does not clear overlay subscriptions.
 */
internal object MainScreenPanelInterestIds {

    fun mbCanInterestSourceId(panelId: String, windowMode: Boolean): String =
        if (windowMode) {
            "main-screen-window-$panelId"
        } else {
            "main-screen-$panelId"
        }

    fun mediaSourceId(panelId: String, windowMode: Boolean): String =
        if (windowMode) {
            "main-screen-window-dashboard-$panelId"
        } else {
            "main-screen-dashboard-$panelId"
        }
}
