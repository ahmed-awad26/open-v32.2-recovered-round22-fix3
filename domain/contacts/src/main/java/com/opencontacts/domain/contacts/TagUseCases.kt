package com.opencontacts.domain.contacts

import com.opencontacts.core.model.TagSummary
import kotlinx.coroutines.flow.Flow

class ObserveTagsUseCase(
    private val repository: ContactRepository,
) {
    operator fun invoke(vaultId: String): Flow<List<TagSummary>> = repository.observeTags(vaultId)
}

class UpsertTagUseCase(
    private val repository: ContactRepository,
) {
    suspend operator fun invoke(vaultId: String, tag: TagSummary) = repository.upsertTag(vaultId, tag)
}
