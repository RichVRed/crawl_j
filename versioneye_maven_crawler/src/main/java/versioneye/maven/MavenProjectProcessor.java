package versioneye.maven;

import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Organization;
import org.apache.maven.project.MavenProject;
import versioneye.domain.Dependency;
import versioneye.domain.Product;
import versioneye.domain.Repository;
import versioneye.domain.Version;
import versioneye.persistence.IDependencyDao;
import versioneye.persistence.IDeveloperDao;
import versioneye.persistence.ILicenseDao;
import versioneye.persistence.IProductDao;
import versioneye.service.ArchiveService;
import versioneye.service.DependencyService;
import versioneye.service.ProductService;
import versioneye.service.VersionLinkService;
import versioneye.utils.MavenUrlUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: robertreiz
 * Date: 7/28/13
 * Time: 5:52 PM
 */
public class MavenProjectProcessor {

    private ProductService productService;
    private ArchiveService archiveService;
    private VersionLinkService versionLinkService;
    private DependencyService dependencyService;
    private IProductDao productDao;
    private IDeveloperDao developerDao;
    private ILicenseDao licenseDao;
    private MavenUrlUtils mavenUrlUtils = new MavenUrlUtils();
    private Repository repository;

    public void updateLicense(MavenProject project){
        try{
            Product product = fetchOrCreateProduct(project);
            updateLicenseInfo(product, project);
        } catch (Exception ex) {
            ex.printStackTrace();
            return ;
        }
    }

    public void updateProject(MavenProject project, Date lastModified) {
        try{
            Product product = fetchOrCreateProduct(project);
            addVersionIfNotExist(product, project, lastModified);

            String urlToPom = "";
            String urlToProduct = "";
            if (repository.getName().equals("central")){
                urlToPom = mavenUrlUtils.getPomUrl( project.getGroupId(), project.getArtifactId(), project.getVersion() );
                urlToProduct = mavenUrlUtils.getProductUrl( project.getGroupId(), project.getArtifactId() );
            } else {
                urlToPom = mavenUrlUtils.getPomUrl( repository.getSrc(), project.getGroupId(), project.getArtifactId(), project.getVersion() );
                urlToProduct = mavenUrlUtils.getProductUrl( repository.getSrc(), project.getGroupId(), project.getArtifactId() );
            }
            archiveService.createArchivesIfNotExist(product, urlToPom);
            createLinks(product, project, urlToProduct);

            createDependenciesIfNotExist(product, project);
            createDeveloperIfNotExist(product, project);
            updateLicenseInfo(product, project);
        } catch (Exception ex) {
            ex.printStackTrace();
            return ;
        }
    }

    private void addVersionIfNotExist(Product product, MavenProject project, Date lastModified){
        Version version = new Version();
        version.setVersion( project.getVersion() );
        version.setProduct_key( product.getProd_key() );
        if (lastModified != null){
            version.setReleased_at(lastModified);
            setReleasedDateString(version, lastModified);
        }
        productService.createVersionIfNotExist(product, version, repository);
    }

    private void setReleasedDateString(Version version, Date lastModified){
        try{
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            version.setReleased_string(sdf.format(lastModified));
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void createLinks(Product product, MavenProject project, String urlToProduct){
        urlToProduct = checkEndingSlash( urlToProduct );
        String projectUrl = checkEndingSlash( project.getUrl() );
        versionLinkService.createLinkIfNotExist( product.getLanguage(), product.getProd_key(), null, "Link to Repository", urlToProduct);
        versionLinkService.createLinkIfNotExist( product.getLanguage(), product.getProd_key(), product.getVersion(), "URL", projectUrl );
        if (project.getOrganization() != null){
            Organization organization = project.getOrganization();
            String organisationName = organization.getName();
            String organisationUrl  = checkEndingSlash( organization.getUrl() );
            versionLinkService.createLinkIfNotExist( product.getLanguage(), product.getProd_key(), null, organisationName, organisationUrl);
        }
        if (project.getScm() != null && project.getScm().getUrl() != null){
            String scmUrl = checkEndingSlash( project.getScm().getUrl() );
            versionLinkService.createLinkIfNotExist( product.getLanguage(), product.getProd_key(),
                    product.getVersion(), "SCM", scmUrl );
        }
    }


    private void createDependenciesIfNotExist(Product product, MavenProject project){
        if (project.getDependencies() == null || project.getDependencies().isEmpty()){
            return ;
        }

//      dependencyDao.deleteDependencies(product.getLanguage(), product.getProd_key(), project.getVersion() );  // this is temp.
        for (org.apache.maven.model.Dependency dep : project.getDependencies() ){
            String key = dep.getGroupId() + "/" + dep.getArtifactId();
            Dependency dependency = new Dependency(product.getLanguage(), product.getProd_key(), project.getVersion(),
                    dep.getArtifactId(), dep.getVersion(), key);
            dependency.setScope(dep.getScope());
            dependency.setGroupId(dep.getGroupId());
            dependency.setArtifactId(dep.getArtifactId());
            dependency.setProdType("Maven2");
            dependencyService.createDependencyIfNotExist(dependency);
        }
    }

    private void createDeveloperIfNotExist(Product product, MavenProject project){
        for ( Developer developer : project.getDevelopers() ){
            String role = "";
            if (developer.getRoles() != null && !developer.getRoles().isEmpty()){
                role = developer.getRoles().get(0);
            }
            versioneye.domain.Developer dev = new versioneye.domain.Developer(product.getLanguage(), product.getProd_key(),
                    product.getVersion(), developer.getId(), developer.getName(), developer.getEmail(),
                    developer.getUrl(), role, developer.getOrganization(), developer.getOrganizationUrl(),
                    developer.getTimezone());
            if (!developerDao.doesExistAlready(product.getLanguage(), product.getProd_key(), product.getVersion(), developer.getName()))
                developerDao.create(dev);
        }
    }

    private void updateLicenseInfo(Product product, MavenProject project){
        for ( License license : project.getLicenses() ) {
            versioneye.domain.License license1 = new versioneye.domain.License();
            if (licenseDao.existAlready(product.getLanguage(), product.getProd_key(), product.getVersion(), license.getName(), license.getUrl()))
                return ;
            license1.setName( license.getName() );
            license1.setUrl(license.getUrl());
            license1.setComments(license.getName());
            license1.setDistributions(license.getDistribution());
            license1.setLanguage(product.getLanguage());
            license1.setProd_key(product.getProd_key());
            license1.setVersion( product.getVersion() );
            licenseDao.create(license1);
        }
    }

    private Product fetchOrCreateProduct(MavenProject project) {
        Product product = null;
        try {
            product = productDao.getByGA(project.getGroupId().toLowerCase(), project.getArtifactId().toLowerCase());
        } catch (Exception ex){
            ex.printStackTrace();
        }
        if (product == null){
            product = newProduct( project );
        }
        product.setVersion( project.getVersion() );
        return product;
    }

    private Product newProduct( MavenProject project ){
        Product product = new Product();
        if (repository != null){
            product.addRepository(repository);
            product.setProd_type(repository.getRepoType());
            product.setLanguage(repository.getLanguage());
        } else {
            product.setProd_type("Maven2");
            product.setLanguage("Java");
        }
        product.setGroupId(project.getGroupId().toLowerCase());
        product.setArtifactId(project.getArtifactId().toLowerCase());
        product.setGroupId_orig(project.getGroupId());
        product.setArtifactId_orig(project.getArtifactId());
        product.setProd_key(project.getGroupId().toLowerCase() + "/" + project.getArtifactId().toLowerCase());
        product.setName(project.getArtifactId());
        product.setVersion(project.getVersion());
        product.setDescription( project.getDescription() );

        productService.createProductIfNotExist(product, repository);
        return product;
    }

    private String checkEndingSlash(String url){
        if (url == null){
            return null;
        }
        if (url.endsWith("/")){
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public void setProductService(ProductService productService) {
        this.productService = productService;
    }

    public void setProductDao(IProductDao productDao) {
        this.productDao = productDao;
    }

    public void setArchiveService(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    public void setDependencyService(DependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    public void setVersionLinkService(VersionLinkService versionLinkService) {
        this.versionLinkService = versionLinkService;
    }

    public void setDeveloperDao(IDeveloperDao developerDao) {
        this.developerDao = developerDao;
    }

    public void setLicenseDao(ILicenseDao licenseDao) {
        this.licenseDao = licenseDao;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

}
