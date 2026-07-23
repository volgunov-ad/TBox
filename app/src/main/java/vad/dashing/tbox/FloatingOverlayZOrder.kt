package vad.dashing.tbox

/**
 * Pure helpers for floating-overlay z-order: only remount panels that actually overlap.
 */
internal object FloatingOverlayZOrder {

    fun rectsIntersect(a: PanelPxBounds, b: PanelPxBounds): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return false
        val aRight = a.x + a.width
        val aBottom = a.y + a.height
        val bRight = b.x + b.width
        val bBottom = b.y + b.height
        return a.x < bRight && aRight > b.x && a.y < bBottom && aBottom > b.y
    }

    /**
     * Connected components of the intersection graph among [mountedInConfigOrder],
     * each component sorted in config order (bottom → top for WindowManager remount).
     * Singleton components are omitted (no z-order work needed).
     */
    fun overlappingComponentsInConfigOrder(
        mountedInConfigOrder: List<String>,
        boundsById: Map<String, PanelPxBounds>,
    ): List<List<String>> {
        if (mountedInConfigOrder.size <= 1) return emptyList()
        val ids = mountedInConfigOrder.filter { boundsById.containsKey(it) }
        if (ids.size <= 1) return emptyList()

        val indexOf = ids.withIndex().associate { (i, id) -> id to i }
        val parent = IntArray(ids.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        for (i in ids.indices) {
            val bi = boundsById[ids[i]] ?: continue
            for (j in i + 1 until ids.size) {
                val bj = boundsById[ids[j]] ?: continue
                if (rectsIntersect(bi, bj)) {
                    union(i, j)
                }
            }
        }

        val groups = linkedMapOf<Int, MutableList<String>>()
        for (id in ids) {
            val root = find(indexOf.getValue(id))
            groups.getOrPut(root) { mutableListOf() }.add(id)
        }
        return groups.values
            .filter { it.size > 1 }
            .map { component -> component.sortedBy { indexOf.getValue(it) } }
    }

    /**
     * Within [desiredOrder] (config order for one overlapping component), whether
     * [currentMountedOrder] (subset of WM keys in that component) already matches.
     */
    fun componentNeedsRemount(
        desiredOrder: List<String>,
        currentMountedOrder: List<String>,
    ): Boolean {
        if (desiredOrder.size <= 1) return false
        return currentMountedOrder != desiredOrder
    }
}
