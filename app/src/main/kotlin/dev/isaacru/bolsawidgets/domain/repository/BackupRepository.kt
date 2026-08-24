package dev.isaacru.bolsawidgets.domain.repository

import dev.isaacru.bolsawidgets.domain.csv.CsvBackup

/**
 * Reads and writes the whole hand-typed dataset as CSV text.
 *
 * Deliberately deals in text rather than files: where the file lives is a question for
 * the storage picker at the edge of the app, not for the domain.
 */
interface BackupRepository {

    /** The current portfolio and watchlist as CSV. */
    suspend fun exportCsv(): String

    /**
     * Replaces everything with [backup]. A restore is a restore: merging would either
     * duplicate every lot on a second import or need an identity that hand-entered
     * positions simply do not have.
     */
    suspend fun restore(backup: CsvBackup)
}
