package io.testpulse.server.storage

import java.util.concurrent.ConcurrentHashMap

/** Attachment blob bytes plus their content type. */
class Blob(val bytes: ByteArray, val contentType: String?)

/** Object storage for attachment blobs. Minio in production, in-memory in tests. */
interface BlobStore {
    fun put(key: String, bytes: ByteArray, contentType: String?)
    fun get(key: String): Blob?
}

/** In-memory blob store for tests (no object storage required). */
class InMemoryBlobStore : BlobStore {
    private val blobs = ConcurrentHashMap<String, Blob>()
    override fun put(key: String, bytes: ByteArray, contentType: String?) {
        blobs[key] = Blob(bytes, contentType)
    }
    override fun get(key: String): Blob? = blobs[key]
}
