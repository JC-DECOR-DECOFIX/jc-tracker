package br.com.jcdecor.tracker.tracking

class InMemoryPendingLocationStore : PendingLocationStore {
    private val items = mutableListOf<PendingPoint>()
    private var nextId = 1L

    override suspend fun insert(point: PendingPoint): PendingPoint {
        val stored = point.copy(id = nextId++)
        items += stored
        return stored
    }

    override suspend fun listInSendOrder(): List<PendingPoint> =
        items.sortedWith(compareBy({ it.createdAt }, { it.sequence }, { it.id }))

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
    }

    override suspend fun incrementRetry(id: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = items[index].copy(retryCount = items[index].retryCount + 1)
        }
    }

    override suspend fun count(): Int = items.size
}
