package app.aniverse.local.di

import app.aniverse.local.data.network.RepoApi
import app.aniverse.local.data.network.RepoIndexParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** One shared client for repository downloads, extension APKs and extension media. */
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            // Only idempotent GETs are retried; the client never retries unsafe methods.
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "AniVerseLocal/1.0")
                    .build(),
            )
        }
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://localhost/")
        .client(client)
        .build()

    @Provides
    @Singleton
    fun repoApi(retrofit: Retrofit): RepoApi = retrofit.create(RepoApi::class.java)

    @Provides
    @Singleton
    fun repoIndexParser(json: Json): RepoIndexParser = RepoIndexParser(json)
}
