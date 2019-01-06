package org.example.maven

import java.io.File
import java.util
import java.util.Collections

import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Provides}
import javax.inject.{Named, Singleton}
import org.apache.maven.model.io.{DefaultModelReader, ModelReader}
import org.apache.maven.model.locator.{DefaultModelLocator, ModelLocator}
import org.apache.maven.model.validation.{DefaultModelValidator, ModelValidator}
import org.apache.maven.repository.internal._
import org.apache.maven.settings.building.{DefaultSettingsBuilderFactory, DefaultSettingsBuildingRequest}
import org.codehaus.plexus.classworlds.ClassWorld
import org.codehaus.plexus.{DefaultContainerConfiguration, DefaultPlexusContainer, PlexusConstants}
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.impl.guice.AetherModule
import org.eclipse.aether.impl.{ArtifactDescriptorReader, MetadataGeneratorFactory, VersionRangeResolver, VersionResolver}
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory
import org.eclipse.aether.repository.{LocalRepository, NoLocalRepositoryManagerException}
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.sisu.inject.DefaultBeanLocator
import org.eclipse.sisu.plexus.ClassRealmManager

import scala.collection.JavaConverters._


object App {

  def main(args: Array[String]): Unit = {
    val userHome = System.getProperty("user.home")
    val realmId = "plexus.core"
    val classWorld = new ClassWorld(realmId, Thread.currentThread().getContextClassLoader)
    val config = new DefaultContainerConfiguration
    config.setClassWorld(classWorld)
    config.setRealm(classWorld.getRealm(realmId))
    config.setClassPathScanning(PlexusConstants.SCANNING_INDEX)
    config.setAutoWiring(true)
    config.setName("maven")
    val container = new DefaultPlexusContainer(config, new AetherModule, new DependencyResolutionModule)
    container.addComponent(new ClassRealmManager(container, new DefaultBeanLocator), classOf[ClassRealmManager].getName)
    val localRepositoryManagerFactory = container.lookup(classOf[LocalRepositoryManagerFactory])
    //val projectBuilder = container.lookup(classOf[ProjectBuilder])
    val repositorySystem = container.lookup(classOf[RepositorySystem])
    val request = new DefaultSettingsBuildingRequest
    request.setUserSettingsFile(new File(userHome, ".m2/settings.xml"))
    request.setSystemProperties(System.getProperties)
    val settings = new DefaultSettingsBuilderFactory().newInstance.build(request).getEffectiveSettings
    val session = MavenRepositorySystemUtils.newSession
    session.setLocalRepositoryManager(localRepositoryManagerFactory.newInstance(session, new LocalRepository(new File(userHome, ".m2/repository"))))
    if (settings.getLocalRepository != null) try
      session.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory().newInstance(session, new LocalRepository(settings.getLocalRepository)))
    catch {
      case e: NoLocalRepositoryManagerException =>
        throw new IllegalStateException("Cannot set local repository to " + settings.getLocalRepository, e)
    }
    val artifact = new DefaultArtifact("org.springframework:spring-expression:4.3.9.RELEASE")
    val dependency = new Dependency(artifact, "runtime")
    val ar = new ArtifactRequest(dependency.getArtifact, null, null)
    val list = repositorySystem.resolveArtifacts(session, List(ar).asJava).asScala.toList
    println(list)
  }

}


class DependencyResolutionModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[ModelLocator]).to(classOf[DefaultModelLocator]).in(classOf[Singleton])
    bind(classOf[ModelReader]).to(classOf[DefaultModelReader]).in(classOf[Singleton])
    bind(classOf[ModelValidator]).to(classOf[DefaultModelValidator]).in(classOf[Singleton])
    bind(classOf[RepositoryConnectorFactory]).to(classOf[BasicRepositoryConnectorFactory]).in(classOf[Singleton])
    bind(classOf[ArtifactDescriptorReader]).to(classOf[DefaultArtifactDescriptorReader]).in(classOf[Singleton])
    bind(classOf[VersionResolver]).to(classOf[DefaultVersionResolver]).in(classOf[Singleton])
    bind(classOf[VersionRangeResolver]).to(classOf[DefaultVersionRangeResolver]).in(classOf[Singleton])
    bind(classOf[MetadataGeneratorFactory]).annotatedWith(Names.named("snapshot")).to(classOf[SnapshotMetadataGeneratorFactory]).in(classOf[Singleton])
    bind(classOf[MetadataGeneratorFactory]).annotatedWith(Names.named("versions")).to(classOf[VersionsMetadataGeneratorFactory]).in(classOf[Singleton])
    bind(classOf[TransporterFactory]).annotatedWith(Names.named("http")).to(classOf[HttpTransporterFactory]).in(classOf[Singleton])
    bind(classOf[TransporterFactory]).annotatedWith(Names.named("file")).to(classOf[FileTransporterFactory]).in(classOf[Singleton])
  }

  @Provides
  @Singleton
  def provideMetadataGeneratorFactories(@Named("snapshot") snapshot: MetadataGeneratorFactory, @Named("versions") versions: MetadataGeneratorFactory): util.Set[MetadataGeneratorFactory] = {
    val factories: util.Set[MetadataGeneratorFactory] = new util.HashSet[MetadataGeneratorFactory]
    factories.add(snapshot)
    factories.add(versions)
    Collections.unmodifiableSet(factories)
  }

  @Provides
  @Singleton
  def provideRepositoryConnectorFactories(factory: RepositoryConnectorFactory): util.Set[RepositoryConnectorFactory] = Collections.singleton(factory)

  @Provides
  @Singleton
  def provideTransporterFactories(@Named("file") file: TransporterFactory, @Named("http") http: TransporterFactory): util.Set[TransporterFactory] = { // Order is decided elsewhere (by priority)
    val factories: util.Set[TransporterFactory] = new util.HashSet[TransporterFactory]
    factories.add(file)
    factories.add(http)
    Collections.unmodifiableSet(factories)
  }

}