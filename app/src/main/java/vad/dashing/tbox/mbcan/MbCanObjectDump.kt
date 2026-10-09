package vad.dashing.tbox.mbcan

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Flattens any OEM mbCAN payload (`com.mengbo.mbCan.entity.*`, nested structs,
 * arrays, primitive callback args) into `path → value` pairs via field reflection.
 *
 * Reads fields only, never getters: runs on OEM callback threads and must not
 * re-enter mbCAN JNI.
 */
object MbCanObjectDump {
    private const val MAX_DEPTH = 4
    private const val MAX_ARRAY_ITEMS = 64

    private val fieldCache = ConcurrentHashMap<Class<*>, List<Field>>()

    fun flatten(obj: Any?): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        append("", obj, 0, out)
        return out
    }

    /** Callback args: a single struct is flattened in place, otherwise `argN` prefixes. */
    fun flattenArgs(args: Array<out Any?>?): List<Pair<String, String>> {
        if (args.isNullOrEmpty()) return emptyList()
        if (args.size == 1 && !isLeaf(args[0])) return flatten(args[0])
        val out = ArrayList<Pair<String, String>>()
        args.forEachIndexed { index, arg -> append("arg$index", arg, 0, out) }
        return out
    }

    private fun append(path: String, value: Any?, depth: Int, out: MutableList<Pair<String, String>>) {
        val key = path.ifEmpty { "value" }
        when {
            value == null -> out.add(key to "null")
            isLeaf(value) -> out.add(key to leafText(value))
            value.javaClass.isArray -> appendArray(path, value, depth, out)
            value is Iterable<*> -> appendArray(path, value.toList().toTypedArray(), depth, out)
            depth >= MAX_DEPTH -> out.add(key to value.toString())
            else -> {
                val fields = fieldsOf(value.javaClass)
                if (fields.isEmpty()) {
                    out.add(key to value.toString())
                    return
                }
                fields.forEach { field ->
                    val child = runCatching { field.get(value) }.getOrElse { "<${it.javaClass.simpleName}>" }
                    append(if (path.isEmpty()) field.name else "$path.${field.name}", child, depth + 1, out)
                }
            }
        }
    }

    private fun appendArray(path: String, array: Any, depth: Int, out: MutableList<Pair<String, String>>) {
        val size = java.lang.reflect.Array.getLength(array)
        val items = (0 until minOf(size, MAX_ARRAY_ITEMS)).map { java.lang.reflect.Array.get(array, it) }
        if (items.all { it == null || isLeaf(it) }) {
            val suffix = if (size > MAX_ARRAY_ITEMS) ",…($size)" else ""
            out.add(path.ifEmpty { "value" } to items.joinToString(",", "[", "$suffix]") { it?.let(::leafText) ?: "null" })
            return
        }
        items.forEachIndexed { index, item -> append("$path[$index]", item, depth + 1, out) }
    }

    private fun isLeaf(value: Any?): Boolean =
        value == null || value is Number || value is Boolean || value is Char ||
            value is CharSequence || value is Enum<*>

    private fun leafText(value: Any): String = value.toString()

    private fun fieldsOf(cls: Class<*>): List<Field> = fieldCache.getOrPut(cls) {
        val result = ArrayList<Field>()
        var current: Class<*>? = cls
        while (current != null && current != Any::class.java) {
            current.declaredFields
                .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
                .forEach { field ->
                    if (runCatching { field.isAccessible = true }.isSuccess) result.add(field)
                }
            current = current.superclass
        }
        result
    }
}
