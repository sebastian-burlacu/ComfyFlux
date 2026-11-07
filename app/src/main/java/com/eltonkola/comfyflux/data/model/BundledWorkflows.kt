package com.eltonkola.comfyflux.data.model

/** The picker's only list filter: built-ins (isAsset) can be hidden wholesale. */
internal fun List<WorkflowFile>.withoutBundled(hidden: Boolean): List<WorkflowFile> =
    if (hidden) filterNot { it.isAsset } else this
