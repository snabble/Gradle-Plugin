package io.snabble.setup

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Action
import org.gradle.api.Project
import java.lang.IllegalStateException

open class SnabbleExtension(private val project: Project) {
    val environments: Map<Environment, BuildEnvironment> = mutableMapOf()

    private fun getEnvironment(environment: Environment) =
        (environments as MutableMap<Environment, BuildEnvironment>).getOrPut(environment) {
            BuildEnvironment(environment)
        }

    fun production(environment: Action<BuildEnvironment>) {
        environment.execute(getEnvironment(Environment.Production))
        createTasks(Environment.Production)
    }

    fun staging(environment: Action<BuildEnvironment>) {
        environment.execute(getEnvironment(Environment.Staging))
        createTasks(Environment.Staging)
    }

    fun testing(environment: Action<BuildEnvironment>) {
        environment.execute(getEnvironment(Environment.Testing))
        createTasks(Environment.Testing)
    }

    private fun createTasks(environment: Environment) {
        val extension = environments[environment]!!
        val downloadTask = project.tasks.register(
            "downloadSnabble${environment}Metadata",
            DownloadTask::class.java
        ) {
            val appId = extension.appId ?: throw IllegalStateException("You must define the app id in order to download the manifest")
            val app = project.extensions.getByType(ApplicationExtension::class.java)
            val appVersion = app.defaultConfig.versionName ?: throw IllegalStateException("No app version number detected")
            it.url.set("${environment.baseUrl}/metadata/app/$appId/android/$appVersion")
            it.environmentName.set(environment.name.lowercase())
            it.outputDir.set( project.layout.buildDirectory.dir("generated/snabble/res"))
        }

        val generateConfigTask = project.tasks.register(
            "generateSnabble${environment}Config",
            GenerateSnabbleConfigTask::class.java
        ) {
            it.appId.set(extension.appId ?: throw IllegalStateException("You must define the app id"))
            it.secret.set(extension.secret ?: throw IllegalStateException("You must define the secret"))
            it.endpointBaseUrl.set(extension.endpointBaseUrl ?: environment.baseUrl)
            it.bundledMetadataAssetPath.set(extension.bundledMetadataAssetPath)
            it.generateSearchIndex.set(extension.generateSearchIndex)
            it.maxProductDatabaseAge.set(extension.maxProductDatabaseAge)
            it.maxShoppingCartAge.set(extension.maxShoppingCartAge)
            it.disableCertificatePinning.set(extension.disableCertificatePinning)
            it.vibrateToConfirmCartFilled.set(extension.vibrateToConfirmCartFilled)
            it.loadActiveShops.set(extension.loadActiveShops)
            it.checkInRadius.set(extension.checkInRadius)
            it.checkOutRadius.set(extension.checkOutRadius)
            it.lastSeenThreshold.set(extension.lastSeenThreshold)
            it.networkInterceptor.set(extension.networkInterceptor)
            it.manualProductDatabaseUpdates.set(extension.manualProductDatabaseUpdates)
            it.environmentName.set(environment.name.lowercase())
            it.outputDir.set( project.layout.buildDirectory.dir("generated/snabble/res"))
        }


        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        val app = project.extensions.getByType(ApplicationExtension::class.java)

        androidComponents.onVariants { variant ->
            val offline = project.gradle.startParameter.isOffline

            val isDebuggable = app.buildTypes
                .getByName(variant.buildType ?: "release")
                .isDebuggable

            val shouldPrefetch = !(offline && isDebuggable) && extension.prefetchMetaData

            variant.sources.res?.addGeneratedSourceDirectory(
                generateConfigTask,
                GenerateSnabbleConfigTask::outputDir
            )

            if (shouldPrefetch) {
                variant.sources.res?.addGeneratedSourceDirectory(
                    downloadTask,
                    DownloadTask::outputDir
                )
            }
        }
    }
}
