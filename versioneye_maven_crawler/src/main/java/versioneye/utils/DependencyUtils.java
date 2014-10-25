package versioneye.utils;

import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: robertreiz
 * Date: 7/11/13
 * Time: 8:12 PM
 */
public class DependencyUtils {

    public static List<Artifact> collectAllDependencies(List<Dependency> dependencies) {
        List<Artifact> result = new ArrayList<Artifact>(dependencies.size());
        for (Dependency dependency : dependencies) {
            result.add(dependency.getArtifact());
        }
        return result;
    }

    public static List<Artifact> collectDirectDependencies(List<DependencyNode> dependencies) {
        List<Artifact> result = new ArrayList<Artifact>(dependencies.size());
        for (DependencyNode dependencyNode : dependencies) {
            result.add(dependencyNode.getDependency().getArtifact());
        }
        return result;
    }

    public CollectRequest getCollectRequest(MavenProject project, List<RemoteRepository> repos){
        Artifact a = new DefaultArtifact( project.getArtifact().toString() );
        DefaultArtifact pom = new DefaultArtifact( a.getGroupId(), a.getArtifactId(), "pom", a.getVersion() );
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(pom, "compile"));
        collectRequest.setRepositories(repos);
        return collectRequest;
    }

    public CollectRequest getCollectRequest(ArtifactInfo artifactInfo, List<RemoteRepository> repos){
        DefaultArtifact pom = new DefaultArtifact( artifactInfo.groupId, artifactInfo.artifactId, "pom", artifactInfo.version );
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(pom, JavaScopes.COMPILE));
        collectRequest.setRepositories(repos);
        return collectRequest;
    }



}
