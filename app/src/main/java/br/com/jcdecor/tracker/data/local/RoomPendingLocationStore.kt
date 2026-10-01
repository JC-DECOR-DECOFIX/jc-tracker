package br.com.jcdecor.tracker.data.local

import br.com.jcdecor.tracker.tracking.PendingLocationStore
import br.com.jcdecor.tracker.tracking.PendingPoint
import kotlinx.coroutines.flow.Flow

class RoomPendingLocationStore(
    private val dao: PendingLocationDao,
) : PendingLocationStore {
    override suspend fun insert(point: PendingPoint): PendingPoint {
        val id = dao.insert(point.toEntity().copy(id = 0))
        return point.copy(id = id)
    }

    override suspend fun listInSendOrder(): List<PendingPoint> = dao.listInSendOrder().map { it.toPoint() }

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun incrementRetry(id: Long) {
        dao.incrementRetry(id)
    }

    override suspend fun count(): Int = dao.count()

    fun observeCount(): Flow<Int> = dao.observeCount()
}
