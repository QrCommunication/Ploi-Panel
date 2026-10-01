package com.qrcommunication.ploipanel

/** Keep manually entered associations intact across picker pages; invalid IDs block submission. */
internal fun parseProjectIds(raw: String): List<Long>? {
    val values = raw.split(',').map(String::trim).filter(String::isNotEmpty)
    val ids = values.map { it.toLongOrNull()?.takeIf { id -> id > 0 } ?: return null }
    return ids.distinct()
}

internal fun toggleProjectId(raw: String, id: Long, checked: Boolean): String {
    require(id > 0)
    val existing = parseProjectIds(raw) ?: return raw
    return (if (checked) existing + id else existing - id).distinct().joinToString(",")
}
