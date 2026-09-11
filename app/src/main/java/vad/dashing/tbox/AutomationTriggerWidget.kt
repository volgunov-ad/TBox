package vad.dashing.tbox

/** Dashboard widget: tap runs automations whose trigger id matches the tile config. */
const val AUTOMATION_TRIGGER_WIDGET_DATA_KEY = "automationTriggerWidget"

/** Max length of an automation trigger id used by [AUTOMATION_TRIGGER_WIDGET_DATA_KEY] tiles. */
const val AUTOMATION_TRIGGER_ID_MAX_CHARS = 32

/**
 * Normalizes a trigger id for [AUTOMATION_TRIGGER_WIDGET_DATA_KEY]:
 * trims surrounding whitespace and caps the length at [AUTOMATION_TRIGGER_ID_MAX_CHARS].
 */
fun normalizeAutomationTriggerId(raw: String): String =
    raw.trim().take(AUTOMATION_TRIGGER_ID_MAX_CHARS)
