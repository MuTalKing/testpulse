package io.testpulse.server.storage

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.StatObjectArgs
import java.io.ByteArrayInputStream

/** Blob store backed by a MinIO / S3-compatible bucket. */
class MinioBlobStore(
    endpoint: String,
    accessKey: String,
    secretKey: String,
    private val bucket: String,
) : BlobStore {

    private val client: MinioClient = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build()

    init {
        val exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
        }
    }

    override fun put(key: String, bytes: ByteArray, contentType: String?) {
        ByteArrayInputStream(bytes).use { stream ->
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(key)
                    .stream(stream, bytes.size.toLong(), -1)
                    .contentType(contentType ?: "application/octet-stream")
                    .build(),
            )
        }
    }

    override fun get(key: String): Blob? {
        val contentType = runCatching {
            client.statObject(StatObjectArgs.builder().bucket(bucket).`object`(key).build()).contentType()
        }.getOrNull() ?: return null

        val bytes = client.getObject(GetObjectArgs.builder().bucket(bucket).`object`(key).build())
            .use { it.readBytes() }
        return Blob(bytes, contentType)
    }

    companion object {
        /** Build from env, or return null when object storage is not configured. */
        fun fromEnv(): MinioBlobStore? {
            val endpoint = System.getenv("TESTPULSE_S3_ENDPOINT") ?: return null
            return MinioBlobStore(
                endpoint = endpoint,
                accessKey = System.getenv("TESTPULSE_S3_ACCESS_KEY") ?: "testpulse",
                secretKey = System.getenv("TESTPULSE_S3_SECRET_KEY") ?: "testpulse",
                bucket = System.getenv("TESTPULSE_S3_BUCKET") ?: "testpulse",
            )
        }
    }
}
