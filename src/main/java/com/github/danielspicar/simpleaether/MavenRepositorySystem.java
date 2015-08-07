package com.github.danielspicar.simpleaether;

import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.*;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.*;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.transport.wagon.WagonProvider;
import org.eclipse.aether.transport.wagon.WagonTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.filter.PatternExclusionsDependencyFilter;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Simple interface to resolve maven style dependencies and download artifacts from the maven repository system.
 *
 * This class has no public constructor. Use {@link MavenRepositorySystem.Builder} to create new instances.
 *
 * @author daniel
 */
public class MavenRepositorySystem {
    private final static Logger logger = LoggerFactory.getLogger(MavenRepositorySystem.class);

    private final RepositorySystem repositorySystem;
    private final LocalRepository localRepository;
    private final List<RemoteRepository> remoteRepositories;
    private final Set<String> globalExclusions;

    /**
     * This class configures and builds new instances of {@link MavenRepositorySystem}.
     */
    public static class Builder {
        private RepositorySystem repositorySystem = null;
        private final List<RemoteRepository> remoteRepositories = new LinkedList<>();
        private LocalRepository localRepository = null;
        private Settings settings = null;
        private final Set<String> globalExclusions = new HashSet<>();

        /**
         * Creates a {@link RemoteRepository} instance from the elements of a maven artifact specification.
         *
         * @param id    some user defined ID for the repository
         * @param type  the repository type. typically "default".
         * @param url   the repository URL.
         *
         * @return the {@link RemoteRepository} specification.
         */
        public static RemoteRepository createRemoteRepository(String id, String type, String url) {
            return new RemoteRepository.Builder(id, type, url).build();
        }

        /**
         * Set the {@link RepositorySystem} instance to use with this {@link MavenRepositorySystem}.
         *
         * @param repositorySystem the instance
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         *
         */
        public Builder withRepositorySystem(RepositorySystem repositorySystem) {
            this.repositorySystem = repositorySystem;
            return this;
        }

        /**
         * The default {@link RepositorySystem} as provided by the Aether service locator.
         *
         * @see MavenRepositorySystemUtils#newServiceLocator()
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withDefaultRepositorySystem() {
            this.repositorySystem = defaultRepositorySystem();
            return this;
        }

        /**
         * Use a specific instance of maven settings (e.g. for locating the local repository path).
         *
         * @param settings the settings instance
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withSettings(Settings settings) {
            this.settings = settings;
            return this;
        }

        /**
         * Use settings from USER_HOME/.m2/settings.xml or an empty settings instance.
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withDefaultSettings() {
            this.settings = defaultMavenSettings();
            return this;
        }

        /**
         * Use a specific local repository location (overriding the one specified in supplied settings or in settings.xml)
         *
         * @param baseDir The repository base directory.
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withLocalRepository(File baseDir) {
            this.localRepository = new LocalRepository(baseDir);
            return this;
        }

        /**
         * Use a specific local repository location (overriding the one specified in supplied settings or in settings.xml)
         *
         * @param baseDir The repository base directory.
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withLocalRepository(String baseDir) {
            this.localRepository = new LocalRepository(baseDir);
            return this;
        }

        /**
         * Use a specific local repository location (overriding the one specified in supplied settings or in settings.xml)
         *
         * @param baseDir The repository base directory.
         * @param type the repository type (usually 'default'. Can be empty or null.
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withLocalRepository(File baseDir, String type) {
            this.localRepository = new LocalRepository(baseDir, type);
            return this;
        }

        /**
         * Use a specific local repository location (overriding the one specified in supplied settings or in settings.xml)
         *
         * @param baseDir The repository base directory.
         * @param type the repository type (usually 'default'. Can be empty or null.
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withLocalRepository(String baseDir, String type) {
            this.localRepository = new LocalRepository(new File(baseDir), type);
            return this;
        }

        /**
         * Use USER_HOME/.m2/repository as the local repository.
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withDefaultLocalRepository() {
            this.localRepository = defaultLocalRepository(null);
            return this;
        }

        /**
         * Add {@link RemoteRepository} specifications to be used for resolving artifacts.
         *
         * @param repositories the {@link RemoteRepository} specifications.
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withRemoteRepositories(RemoteRepository... repositories) {
            this.remoteRepositories.addAll(Arrays.asList(repositories));
            return this;
        }

        /**
         * Add the maven central repository to the list of remote repositories.
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withMavenCentralRepository() {
            this.remoteRepositories.add(centralRepository());
            return this;
        }

        /**
         * Specify a patterns that exclude all matching artifacts from any
         * dependency resolution.
         *
         * Each pattern segment is optional and supports full and partial * wildcards.
         * An empty pattern segment is treated as an implicit wildcard.
         *
         * For example, org.apache.* would match all artifacts whose group id started
         * with org.apache. , and :::*-SNAPSHOT would match all snapshot artifacts.
         *
         * @param exclusions [groupId]:[artifactId]:[extension]:[version]
         *
         * @return this {@link com.github.danielspicar.simpleaether.MavenRepositorySystem.Builder} instance for method chaining.
         */
        public Builder withGlobalExclusions(String... exclusions) {
            this.globalExclusions.addAll(Arrays.asList(exclusions));
            return this;
        }

        /**
         * Build an instance of {@link MavenRepositorySystem} with the currently configured settings.
         *
         * By default the default repository system of Eclipse Aether is used. Settings are read from USER_HOME/.m2/settings.xml.
         * The default local repository is USER_HOME/.m2/repository or the configuration in the settings.
         *
         * @return a new instance of {@link MavenRepositorySystem}
         */
        public MavenRepositorySystem build() {
            if(this.repositorySystem == null) {
                this.repositorySystem = defaultRepositorySystem();
            }

            if(this.settings == null) {
                this.settings = defaultMavenSettings();
            }

            if(this.localRepository == null) {
                this.localRepository = defaultLocalRepository(this.settings);
            }

            return new MavenRepositorySystem(this.repositorySystem, this.localRepository, this.remoteRepositories, this.globalExclusions);
        }

        private LocalRepository defaultLocalRepository(Settings settings) {
            String repoPath = null;
            if(settings != null) {
                repoPath = settings.getLocalRepository();
            }
            if(repoPath == null || repoPath.isEmpty()) {
                return new LocalRepository(new File(new File(System.getProperty("user.home"), ".m2"), "repository"));
            } else {
                return new LocalRepository(new File(repoPath));
            }
        }

        private RepositorySystem defaultRepositorySystem() {
            DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
            locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
            locator.addService(TransporterFactory.class, FileTransporterFactory.class);
            locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
            locator.addService(TransporterFactory.class, WagonTransporterFactory.class);
            locator.setService(WagonProvider.class, SimpleWagonProvider.class);

            locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
                @Override
                public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                    logger.error("ServiceLocatorError: ", exception);
                }
            });

            return locator.getService(RepositorySystem.class);
        }

        private Settings defaultMavenSettings() {
            try {
                File settingsXml = new File(new File(System.getProperty("user.home"), ".m2"), "settings.xml");
                if(settingsXml.canRead()) {
                    SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
                    SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
                    request.setSystemProperties(System.getProperties());
                    request.setUserSettingsFile(settingsXml);

                    return settingsBuilder.build(request).getEffectiveSettings();
                }
            } catch (SettingsBuildingException ex) {
                logger.warn("Could not build settings from user settings.xml.", ex);
            }

            return new Settings();
        }

        private RemoteRepository centralRepository() {
            return createRemoteRepository("central", "default", "http://repo1.maven.org/maven2/");
        }
    }

    private MavenRepositorySystem(RepositorySystem repositorySystem, LocalRepository localRepository, List<RemoteRepository> remoteRepositories, Set<String> globalExclusions) {
        this.repositorySystem = repositorySystem;
        this.localRepository = localRepository;
        this.remoteRepositories = remoteRepositories;
        this.globalExclusions = globalExclusions;
    }

    /**
     * Add a remote repository.
     *
     * @param repository The repository to use.
     */
    public void addRepository(RemoteRepository repository) {
        this.remoteRepositories.add(repository);
    }

    /**
     * Remove a repository.
     *
     * @param repository the repository to remove.
     */
    public void removeRepository(RemoteRepository repository) {
        this.remoteRepositories.remove(repository);
    }

    /**
     * Get all configured repositories.
     *
     * @return an unmodifiable list of repositories.
     */
    public List<RemoteRepository> getRemoteRepositories() {
        return Collections.unmodifiableList(this.remoteRepositories);
    }

    /**
     * Specify a pattern that excludes all matching artifacts from any
     * dependency resolution.
     *
     * Each pattern segment is optional and supports full and partial * wildcards.
     * An empty pattern segment is treated as an implicit wildcard.
     *
     * For example, org.apache.* would match all artifacts whose group id started
     * with org.apache. , and :::*-SNAPSHOT would match all snapshot artifacts.
     *
     * @param pattern [groupId]:[artifactId]:[extension]:[version]
     */
    public void addGlobalExclusion(String pattern) {
        globalExclusions.add(pattern);
    }

    /**
     * Get all configured global exclusions.
     *
     * @return an unmodifiable list of global exclusions.
     */
    public Set<String> getGlobalExclusions() {
        return Collections.unmodifiableSet(globalExclusions);
    }

    /**
     * Resolve all dependencies and transitive runtime dependencies specified in
     * a POM file.
     *
     * <p>
     *  Additional information extracted from the POM:
     *  <ul>
     *      <li>Dependency exclusions.</li>
     *      <li>Repository specifications.</li>
     *      <li>Parent POMs are parsed as well (if they can be found).</li>
     *  </ul>
     * </p>
     *
     * @param pom   the POM (pom.xml) file.
     * @return
     *      All dependencies specified in the POM and their transitive runtime
     *      dependencies as files.
     */
    public List<File> resolveDependenciesFromPom(File pom) throws DependencyResolutionException {
        Model model = getEffectiveModel(pom);
        HashSet<File> files = new HashSet<>();
        for(org.apache.maven.model.Dependency dependency : model.getDependencies()) {
            Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getType(), dependency.getVersion());
            files.addAll(resolveDependencies(artifact, model.getRepositories(), getAetherExclusions(dependency)));
        }

        return new ArrayList<>(files);
    }

    /**
     *
     */
    public List<Artifact> resolveDependencyArtifactsFromPom(File pom) {
        Model model = getEffectiveModel(pom);
        List<Artifact> artifacts = new LinkedList<>();
        for(org.apache.maven.model.Dependency dependency : model.getDependencies()) {
            Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), dependency.getType(), dependency.getVersion());
            artifacts.add(artifact);
        }

        return artifacts;
    }

    /**
     * Resolve an artifact and all its runtime dependencies.
     *
     * @return the artifact and its transitive runtime dependencies as files.
     */
    @SuppressWarnings("unchecked")
    public List<File> resolveDependencies(String groupId, String artifactId, String classifier, String extension, String version) throws DependencyResolutionException {
        Artifact artifact = new DefaultArtifact(
                groupId, artifactId, classifier, extension, version);
        return resolveDependencies(artifact, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    }

    /**
     * Resolve an artifact and all its runtime dependencies.
     *
     * @return the artifact and its transitive runtime dependencies as files.
     */
    @SuppressWarnings("unchecked")
    public List<File> resolveDependencies(Artifact artifact) throws DependencyResolutionException {
        return resolveDependencies(artifact, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    }

    /**
     * Resolve an artifact and its transitive runtime dependencies given a list
     * of repositories and artifact exclusions.
     *
     * @param artifact
     *      The artifact to resolve.
     * @param repositories
     *      Additional repositories to use for dependency resolution.
     * @param exclusions
     *      Artifacts not to include in the final list of files.
     * @return
     *      The artifact and its transitive runtime dependencies as files.
     */
    private List<File> resolveDependencies(Artifact artifact, List<Repository> repositories, List<Exclusion> exclusions) throws DependencyResolutionException {

        RepositorySystemSession session = createSession();
        Dependency dependency = new Dependency(artifact, JavaScopes.RUNTIME,
                false, exclusions);
        DependencyFilter classpathFilter =
                DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME);

        PatternExclusionsDependencyFilter patternExclusionFilter = new PatternExclusionsDependencyFilter(this.globalExclusions);
        DependencyFilter filter = DependencyFilterUtils.andFilter(classpathFilter, patternExclusionFilter);

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(dependency);
        collectRequest.setRepositories(this.remoteRepositories);
        for(RemoteRepository repository : toRemoteRepositories(repositories)) {
            collectRequest.addRepository(repository);
        }
        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest(collectRequest);
        dependencyRequest.setFilter(filter);
        DependencyResult result = this.repositorySystem.resolveDependencies(session, dependencyRequest);

        PreorderNodeListGenerator listGen = new PreorderNodeListGenerator();
        result.getRoot().accept(listGen);

        return listGen.getFiles();
    }

    /**
     * Resolve the effective Maven model (pom) for a POM file.
     *
     * This resolves the POM hierarchy (parents and modules) and creates an
     * overall model.
     *
     * @param pom the POM file to resolve.
     * @return the effective model.
     */
    private Model getEffectiveModel(File pom) {
        ModelBuildingRequest req = new DefaultModelBuildingRequest();
        req.setProcessPlugins(false);
        req.setPomFile(pom);
        req.setModelResolver(new SimpleModelResolver(this.repositorySystem, createSession(), getRemoteRepositories()));
        req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

        ModelBuilder builder = new DefaultModelBuilderFactory().newInstance();
        try {
            return builder.build(req).getEffectiveModel();
        } catch(ModelBuildingException ex) {
            logger.warn("Could not build maven model.", ex);
        }

        return new Model();
    }

    private RepositorySystemSession createSession() {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, localRepository));
        session.setTransferListener(new LoggingTransferListener());
        session.setRepositoryListener(new LoggingRepositoryListener());
        return session;
    }

    /**
     * Creates Aether exclusions from Maven dependency exclusions.
     *
     * Translates Maven Exclusions to Aether Exclusions.
     *
     * @param dependency the Maven dependency.
     * @return the Sonatype exclusion.
     */
    private List<Exclusion> getAetherExclusions(org.apache.maven.model.Dependency dependency) {
        List<org.apache.maven.model.Exclusion> mavenExclusions = dependency.getExclusions();
        List<Exclusion> exclusions = new ArrayList<>(mavenExclusions.size());
        for(org.apache.maven.model.Exclusion mavenExclusion : mavenExclusions) {
            exclusions.add(new Exclusion(stringOrWildcard(mavenExclusion.getGroupId()),
                    stringOrWildcard(mavenExclusion.getArtifactId()), "*", "*"));
        }
        return exclusions;
    }

    private String stringOrWildcard(String string) {
        if(string == null || string.isEmpty()) {
            return "*";
        }
        return string;
    }

    private List<RemoteRepository> toRemoteRepositories(List<Repository> repositories) {
        List<RemoteRepository> remoteRepositories = new ArrayList<>(repositories.size());
        for(Repository repository : repositories) {
            remoteRepositories.add(ArtifactDescriptorUtils.toRemoteRepository(repository));
        }

        return remoteRepositories;
    }
}
