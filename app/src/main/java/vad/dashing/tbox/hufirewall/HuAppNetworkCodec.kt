package vad.dashing.tbox.hufirewall

/**
 * A10 stock `CarService` (`CarNetworkService`) fills iptables chains `adayo_output_wifi` /
 * `adayo_output_mobile` with a whitelist from `/system/etc/adayo/firewall/AdayoFirewallAppDef.json`
 * (empty on Jetour): only uid 0..9999 may leave via wlan/eth/usb/tbox, every installed app is DROPped.
 * The command inserts an idempotent RETURN for app uids at the head of both chains (root, not persistent).
 */
internal sealed interface HuAppNetworkOutcome {
    /** [added] = chains where the rule was missing and has just been inserted. */
    data class Open(val chains: Int, val added: Int) : HuAppNetworkOutcome
    data object NoFirewall : HuAppNetworkOutcome
    data object NoSu : HuAppNetworkOutcome
    data class Failed(val detail: String) : HuAppNetworkOutcome
}

internal object HuAppNetworkCodec {
    val CHAINS = listOf("adayo_output_wifi", "adayo_output_mobile")
    private const val RULE = "-m owner --uid-owner 10000-99999 -j RETURN"

    val SHELL_COMMAND: String = buildString {
        append("if command -v su >/dev/null 2>&1; then su 0 sh -c '")
        append("for c in ${CHAINS.joinToString(" ")}; do ")
        append("iptables -w -L \$c -n >/dev/null 2>&1 || continue; ")
        append("if iptables -w -C \$c $RULE 2>/dev/null; then echo TBOX_FW_PRESENT \$c; ")
        append("elif iptables -w -I \$c 1 $RULE; then echo TBOX_FW_ADDED \$c; ")
        append("else echo TBOX_FW_FAIL \$c; fi; done; echo TBOX_FW_END'; ")
        append("else echo TBOX_FW_ERR nosu; fi")
    }

    fun parseOutput(output: String): HuAppNetworkOutcome {
        val words = output.lineSequence().map { it.trim().split(Regex("\\s+")) }.toList()
        fun count(marker: String) = words.count { it.firstOrNull() == marker }
        if (words.any { it.firstOrNull() == "TBOX_FW_ERR" && it.getOrNull(1) == "nosu" }) {
            return HuAppNetworkOutcome.NoSu
        }
        val failed = words.filter { it.firstOrNull() == "TBOX_FW_FAIL" }.mapNotNull { it.getOrNull(1) }
        if (failed.isNotEmpty()) return HuAppNetworkOutcome.Failed(failed.joinToString(","))
        if (count("TBOX_FW_END") == 0) return HuAppNetworkOutcome.Failed("noreply")
        val added = count("TBOX_FW_ADDED")
        val chains = added + count("TBOX_FW_PRESENT")
        return if (chains == 0) HuAppNetworkOutcome.NoFirewall else HuAppNetworkOutcome.Open(chains, added)
    }
}
