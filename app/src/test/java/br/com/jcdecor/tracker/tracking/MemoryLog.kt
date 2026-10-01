package br.com.jcdecor.tracker.tracking

class MemoryLog : TrackerLog {
    val lines = mutableListOf<String>()

    override fun info(message: String) {
        lines += message
    }
}
