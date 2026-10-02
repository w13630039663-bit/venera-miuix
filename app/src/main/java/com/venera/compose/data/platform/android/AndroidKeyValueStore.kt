package com.venera.compose.data.platform.android

import android.content.Context
import android.content.SharedPreferences
import com.venera.compose.data.platform.KeyValueStore

/**
 * Android 侧实现：贴在系统 SharedPreferences 上（一个名字一个 XML），不改系统语义。
 *
 * 类型不匹配的行为跟系统一致：`getX` 撞上别的类型抛 ClassCastException，这里不替它兜成默认值。
 * 落盘走 `apply()`（异步但顺序有保证），与收口前那 13 处旁路的写法同口径。
 */
class AndroidKeyValueStore(context: Context, name: String) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(k: String, d: String?): String? = prefs.getString(k, d)

    override fun getBoolean(k: String, d: Boolean): Boolean = prefs.getBoolean(k, d)

    override fun getInt(k: String, d: Int): Int = prefs.getInt(k, d)

    override fun getLong(k: String, d: Long): Long = prefs.getLong(k, d)

    override fun getFloat(k: String, d: Float): Float = prefs.getFloat(k, d)

    /**
     * 系统返回的那个 Set 不许调用方改（改了会崩在 SharedPreferences 内部），
     * 所以这里就还一个只读副本；没这个键给空集。
     */
    override fun getStringSet(k: String): Set<String> = prefs.getStringSet(k, null)?.toSet() ?: emptySet()

    override fun put(k: String, v: Any?) {
        val editor = prefs.edit()
        bind(editor, k, v)
        editor.apply()
    }

    override fun putAll(values: Map<String, Any?>) {
        // 一次 edit 多条 put：保住收口前"这几条是一个事务"的语义
        val editor = prefs.edit()
        values.forEach { (key, v) -> bind(editor, key, v) }
        editor.apply()
    }

    override fun remove(k: String) {
        prefs.edit().remove(k).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun keys(): Set<String> = prefs.all.keys.toSet()

    /**
     * 变更监听原样透出。
     *
     * [KeyValueStore] 接口里没有回调成员（桌面侧的 JSON 实现每次从磁盘重读，本来就不需要通知），
     * 而 Android 这几个调用点还挂在 SharedPreferences 原生监听上。不改它们的签名，就不动 UI 层。
     */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun bind(editor: SharedPreferences.Editor, k: String, v: Any?) {
        when (v) {
            null -> editor.remove(k)
            is String -> editor.putString(k, v)
            is Boolean -> editor.putBoolean(k, v)
            is Int -> editor.putInt(k, v)
            is Long -> editor.putLong(k, v)
            is Float -> editor.putFloat(k, v)
            is Set<*> -> editor.putStringSet(
                k,
                v.map { item ->
                    require(item is String) { "键 $k 的集合元素只支持字符串，实际是 ${item?.javaClass?.name}" }
                    item
                }.toSet(),
            )
            else -> throw IllegalArgumentException("键 $k 的值类型不支持落盘：${v.javaClass.name}")
        }
    }
}
