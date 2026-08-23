package dev.isaacru.bolsawidgets.data.repository

import dev.isaacru.bolsawidgets.data.local.dao.PositionDao
import dev.isaacru.bolsawidgets.data.local.toDomain
import dev.isaacru.bolsawidgets.data.local.toEntity
import dev.isaacru.bolsawidgets.di.IoDispatcher
import dev.isaacru.bolsawidgets.domain.model.Position
import dev.isaacru.bolsawidgets.domain.repository.PortfolioRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioRepositoryImpl @Inject constructor(
    private val positionDao: PositionDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : PortfolioRepository {

    override fun observePositions(): Flow<List<Position>> =
        positionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeHeldSymbols(): Flow<List<String>> = positionDao.observeSymbols()

    override suspend fun getPositions(): List<Position> = withContext(io) {
        positionDao.getAll().map { it.toDomain() }
    }

    override suspend fun getPosition(id: Long): Position? = withContext(io) {
        positionDao.getById(id)?.toDomain()
    }

    override suspend fun upsert(position: Position): Long = withContext(io) {
        val rowId = positionDao.upsert(position.toEntity())
        // Room returns -1 from @Upsert when the row was updated rather than inserted.
        if (rowId == -1L) position.id else rowId
    }

    override suspend fun delete(id: Long) = withContext(io) {
        positionDao.deleteById(id)
    }
}
