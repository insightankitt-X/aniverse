package app.anilab.local.di

import android.content.Context
import app.anilab.source.runtime.ExtensionClient
import app.anilab.source.runtime.SourceRuntime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {
    @Provides
    @Singleton
    fun extensionClient(@ApplicationContext context: Context, json: Json): ExtensionClient =
        ExtensionClient(context, json)

    @Provides
    @Singleton
    fun sourceRuntime(client: ExtensionClient, json: Json): SourceRuntime = SourceRuntime(client, json)
}
