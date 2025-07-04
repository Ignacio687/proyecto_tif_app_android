package ar.edu.um.tif.aiAssistant.core.di

import android.content.Context
import ar.edu.um.tif.aiAssistant.core.client.AssistantApiClient
import ar.edu.um.tif.aiAssistant.core.skills.CallContactSkill
import com.justai.aimybox.core.CustomSkill
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

    @Provides
    @Singleton
    fun provideCustomSkills(
        @ApplicationContext context: Context,
        assistantApiClientProvider: Provider<AssistantApiClient>
    ): LinkedHashSet<CustomSkill<*, *>> {
        return linkedSetOf(
            CallContactSkill(context, assistantApiClientProvider)
        )
    }
}
