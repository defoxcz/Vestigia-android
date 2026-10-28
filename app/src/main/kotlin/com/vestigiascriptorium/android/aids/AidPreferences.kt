package com.vestigiascriptorium.android.aids

import android.content.Context

/** Library display preferences belong to the app, not to a transient Compose screen. */
internal class AidPreferences(context: Context,name: String="research-aids") {
    private val preferences=context.getSharedPreferences(name,Context.MODE_PRIVATE)
    var installedOnly: Boolean
        get()=preferences.getBoolean("installedOnly",false)
        set(value) { preferences.edit().putBoolean("installedOnly",value).apply() }
}
