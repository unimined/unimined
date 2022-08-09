package xyz.wagyourtail.unimined.idea


fun isIdeaSync(): Boolean {
    return java.lang.Boolean.getBoolean("idea.sync.active")
}