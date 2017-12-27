package org.example

import org.apache.maven.repository.internal.*
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.artifact.DefaultArtifactType
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.impl.*
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry
import org.eclipse.aether.util.graph.manager.ClassicDependencyManager
import org.eclipse.aether.util.graph.selector.AndDependencySelector
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector
import org.eclipse.aether.util.graph.transformer.*
import org.eclipse.aether.util.graph.traverser.FatArtifactTraverser
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy
import java.util.*


fun main(args: Array<String>) {



    //val locator = MavenRepositorySystemUtils.newServiceLocator()
    val locator = DefaultServiceLocator()
    locator.addService(ArtifactDescriptorReader::class.java, DefaultArtifactDescriptorReader::class.java)
    locator.addService(VersionResolver::class.java, DefaultVersionResolver::class.java)
    locator.addService(VersionRangeResolver::class.java, DefaultVersionRangeResolver::class.java)
    locator.addService(MetadataGeneratorFactory::class.java, SnapshotMetadataGeneratorFactory::class.java)
    locator.addService(MetadataGeneratorFactory::class.java, VersionsMetadataGeneratorFactory::class.java)
    locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)

    locator.addService(TransporterFactory::class.java, FileTransporterFactory::class.java)
    locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)

    val repositorySystem = locator.getService(RepositorySystem::class.java)
    //val session = MavenRepositorySystemUtils.newSession()
    val session = DefaultRepositorySystemSession()
    session.dependencyTraverser = FatArtifactTraverser()
    session.dependencyManager = ClassicDependencyManager()
    session.dependencySelector = AndDependencySelector(ScopeDependencySelector("test", "provided"), OptionalDependencySelector(), ExclusionDependencySelector())
    val transformer = ConflictResolver(NearestVersionSelector(), JavaScopeSelector(), SimpleOptionalitySelector(), JavaScopeDeriver())
    ChainedDependencyGraphTransformer(transformer, JavaDependencyContextRefiner())
    session.dependencyGraphTransformer = transformer
    val stereotypes = DefaultArtifactTypeRegistry()
    stereotypes.add(DefaultArtifactType("pom"))
    stereotypes.add(DefaultArtifactType("maven-plugin", "jar", "", "java"))
    stereotypes.add(DefaultArtifactType("jar", "jar", "", "java"))
    stereotypes.add(DefaultArtifactType("ejb", "jar", "", "java"))
    stereotypes.add(DefaultArtifactType("ejb-client", "jar", "client", "java"))
    stereotypes.add(DefaultArtifactType("test-jar", "jar", "tests", "java"))
    stereotypes.add(DefaultArtifactType("javadoc", "jar", "javadoc", "java"))
    stereotypes.add(DefaultArtifactType("java-source", "jar", "sources", "java", false, false))
    stereotypes.add(DefaultArtifactType("war", "war", "", "java", false, true))
    stereotypes.add(DefaultArtifactType("ear", "ear", "", "java", false, true))
    stereotypes.add(DefaultArtifactType("rar", "rar", "", "java", false, true))
    stereotypes.add(DefaultArtifactType("par", "par", "", "java", false, true))
    session.artifactTypeRegistry = stereotypes
    session.artifactDescriptorPolicy = SimpleArtifactDescriptorPolicy(true, true)
    val sysProps = Properties()
    for (key in System.getProperties().stringPropertyNames()) {
        sysProps.put(key, System.getProperty(key))
    }
    session.setSystemProperties(sysProps)
    session.setConfigProperties(sysProps)
    session.localRepositoryManager = repositorySystem.newLocalRepositoryManager(session, LocalRepository("target/local-repo"))

    val request = VersionRangeRequest()
    request.addRepository(RemoteRepository.Builder("aliyun","default","http://maven.aliyun.com/nexus/content/groups/public/").build())
    request.artifact = DefaultArtifact("org.springframework:spring-context:jar:[3,)")
    val range = repositorySystem.resolveVersionRange(session, request)
    println(range.highestVersion)
}


