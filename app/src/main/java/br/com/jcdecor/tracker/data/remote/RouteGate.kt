package br.com.jcdecor.tracker.data.remote

object RouteGate {
    fun canPost(routeId: String?): Boolean = !routeId.isNullOrBlank()
}
