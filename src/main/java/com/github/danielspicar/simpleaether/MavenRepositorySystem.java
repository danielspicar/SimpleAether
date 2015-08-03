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
 * Created by danielspicar on 30/07/15.
 */
public class MavenRepositorySystem {
    private final static Logger logger = LoggerFactory.getLogger(MavenRepositorySystem.class);

    private final RepositorySystem repositorySystem;
    private final LocalRepository localRepository;
    private final List<RemoteRepository> remoteRepositories;
    private final Settings settings;
    private final Set<String> globalExclusions;

    public static class Builder {
        private RepositorySystem repositorySystem = null;
        private List<RemoteRepository> remoteRepositories = new LinkedList<RemoteRepository>();
        private LocalRepository localRepository = null;
        private Settings settings = null;
        private Set<String> globalExclusions = new HashSet<String>();

        /**
         * Creates a repository specification.
         *
         * @param id    some user defined ID for the repository
         * @param type  the repository type. typically "default".
         * @param url   the repository URL.
         * @return the repository specification.
         */
        public static RemoteRepository createRemoteRepository(String id, String type, String url) {
            return new RemoteRepository.Builder(id, type, url).build();
        }

        public Builder withRepositorySystem(RepositorySystem repositorySystem) {
            this.repositorySystem = repositorySystem;
            return this;
        }

        public Builder withDefaultRepositorySystem() {
            this.repositorySystem = defaultRepositorySystem();
            return this;
        }

        public Builder withSettings(Settings settings) {
            this.settings = settings;
            return this;
        }

        public Builder withDefaultSettings() {
            this.settings = defaultMavenSettings();
            return this;
        }

        public Builder withLocalRepository(File baseDir) {
            this.localRepository = new LocalRepository(baseDir);
            return this;
        }

        public Builder withLocalRepository(String baseDir) {
            this.localRepository = new LocalRepository(baseDir);
            return this;
        }

        public Builder withLocalRepository(File baseDir, String type) {
            this.localRepository = new LocalRepository(baseDir, type);
            return this;
        }

        public Builder withLocalRepository(String baseDir, String type) {
            this.localRepository = new LocalRepository(new File(baseDir), type);
            return this;
        }

        public Builder withDefaultLocalRepository() {
            this.localRepository = defaultLocalRepository(null);
            return this;
        }

        public Builder withRemoteRepositories(RemoteRepository... repositories) {
            this.remoteRepositories.addAll(Arrays.asList(repositories));
            return this;
        }

        public Builder withMavenCentralRepository() {
            this.remoteRepositories.add(centralRepository());
            return this;
        }

        public Builder withGlobalExclusions(String... exclusions) {
            for(String exclusion : exclusions) {
                this.globalExclusions.add(exclusion);
            }
            return this;
        }

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

            return new MavenRepositorySystem(this.repositorySystem, this.settings, this.localRepository, this.remoteRepositories, this.globalExclusions);
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

        /**
         * Creates the Maven central repository specification.
         *
         * @return the Maven central repository specification.
         */
        private RemoteRepository centralRepository() {
            return createRemoteRepository("central", "default", "http://repo1.maven.org/maven2/");
        }
    }

    /**
     * Creates a new MavenRepositorySystem.
     */
    protected MavenRepositorySystem(RepositorySystem repositorySystem, Settings settings, LocalRepository localRepository, List<RemoteRepository> remoteRepositories, Set<String> globalExclusions) {
        this.repositorySystem = repositorySystem;
        this.settings = settings;
        this.localRepository = localRepository;
        this.remoteRepositories = remoteRepositories;
        this.globalExclusions = globalExclusions;
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
        HashSet<File> files = new HashSet<File>();
        for(org.apache.maven.model.Dependency dependency : model.getDependencies()) {
            Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getType(), dependency.getVersion());
            files.addAll(resolveDependencies(artifact, model.getRepositories(), getAetherExclusions(dependency)));
        }

        return new ArrayList<File>(files);
    }

    /**
     *
     */
    public List<Artifact> resolveDependencyArtifactsFromPom(File pom) {
        Model model = getEffectiveModel(pom);
        List<Artifact> artifacts = new LinkedList<Artifact>();
        for(org.apache.maven.model.Dependency dependency : model.getDependencies()) {
            Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getType(), dependency.getVersion());
            artifacts.add(artifact);
        }

        return artifacts;
    }

    /**
     * Resolve an artifact and all its runtime dependencies.
     */
    public List<File> resolveDependencies(String groupId, String artifactId, String classifier, String extension, String version) throws DependencyResolutionException {
        Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);
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
    protected List<File> resolveDependencies(Artifact artifact, List<Repository> repositories, List<Exclusion> exclusions) throws DependencyResolutionException {

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
     * Resolve the effective Maven model (pom) for a POM file.
     *
     * This resolves the POM hierarchy (parents and modules) and creates an
     * overall model.
     *
     * @param pom the POM file to resolve.
     * @return the effective model.
     */
    protected Model getEffectiveModel(File pom) {
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

    /**
     * Get all configured repositories.
     *
     * @return an unmodifyable list of repositories.
     */
    public List<RemoteRepository> getRemoteRepositories() {
        return Collections.unmodifiableList(this.remoteRepositories);
    }

    /**
     * Creates a repository session.
     *
     * @return the repository session.
     */
    protected RepositorySystemSession createSession() {
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
    protected List<Exclusion> getAetherExclusions(org.apache.maven.model.Dependency dependency) {
        List<org.apache.maven.model.Exclusion> mavenExclusions = dependency.getExclusions();
        List<Exclusion> exclusions = new ArrayList<Exclusion>(mavenExclusions.size());
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
        List<RemoteRepository> remoteRepositories = new ArrayList<RemoteRepository>(repositories.size());
        for(Repository repository : repositories) {
            remoteRepositories.add(ArtifactDescriptorUtils.toRemoteRepository(repository));
        }

        return remoteRepositories;
    }
}
