package keappexpressionbiclusters;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kbaserelationengine.Bicluster;
import kbaserelationengine.CleanKEAppResultsParams;
import kbaserelationengine.CompendiumDescriptor;
import kbaserelationengine.GetCompendiumDescriptorsParams;
import kbaserelationengine.GetKEAppDescriptorParams;
import kbaserelationengine.GraphUpdateStat;
import kbaserelationengine.KBaseRelationEngineServiceClient;
import kbaserelationengine.KEAppDescriptor;
import kbaserelationengine.StoreBiclustersParams;
import kbaserelationengine.StoreKEAppDescriptorParams;
import kbkeutil.BuildBiclustersOutput;
import kbkeutil.BuildBiclustersParams;
import kbkeutil.KbKeUtilClient;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.UnauthorizedException;

public class KEAppExpressionBiclustersServerImpl {
    private URL srvWizUrl = null;
    private URL callbackUrl = null;    
	
	
    public KEAppExpressionBiclustersServerImpl(Map<String, String> config) throws MalformedURLException {
        srvWizUrl = new URL(config.get("srv-wiz-url"));
        callbackUrl = new URL(System.getenv("SDK_CALLBACK_URL"));

	}

	private KbKeUtilClient getKEMathClient(AuthToken authPart) throws UnauthorizedException, IOException{
        KbKeUtilClient client = new KbKeUtilClient(callbackUrl, authPart);
        client.setIsInsecureHttpConnectionAllowed(true);
        client.setServiceVersion("dev");
        return client;
    }    
    
    private KBaseRelationEngineServiceClient getRECleint(AuthToken authPart) throws UnauthorizedException, IOException{
        KBaseRelationEngineServiceClient client = new KBaseRelationEngineServiceClient(srvWizUrl, authPart);
        client.setIsInsecureHttpConnectionAllowed(true);
        client.setServiceVersion("dev");
        return client;    	
    }

	public ConstructExprBiclustersOutput constructExprBiclusters(ConstructExprBiclustersInput params,
			AuthToken authPart) throws IOException, JsonClientException {
        KBaseRelationEngineServiceClient reClient = getRECleint(authPart);
        KbKeUtilClient kmClient = getKEMathClient(authPart);
        final String DATA_TYPE = "gene expression";
        final String KEAPP_GUID = "KEApp1";
        
        // Get KEApp
        KEAppDescriptor app = reClient.getKEAppDescriptor(
        		new GetKEAppDescriptorParams()
        		.withAppGuid(KEAPP_GUID));
        
        // Clean all results generated by this app before
        reClient.cleanKEAppResults(
        		new CleanKEAppResultsParams()
        		.withAppGuid(app.getGuid()));
        
        // Init KEApp
        app
        	.withNodesCreated(0L)
        	.withRelationsCreated(0L)
        	.withPropertiesSet(0L);
        
        reClient.storeKEAppDescriptor(new StoreKEAppDescriptorParams()
        		.withApp(app));
        
        // Get all compendia
        List<CompendiumDescriptor> compendia = reClient.getCompendiumDescriptors(
        		new GetCompendiumDescriptorsParams()
        		.withDataType(DATA_TYPE));
        
        // Process each compendium
        for(CompendiumDescriptor cmp: compendia){
        	
        	// Build biclusters
        	BuildBiclustersOutput res = kmClient.buildBiclusters(
        		new BuildBiclustersParams()
        		.withNdarrayRef(cmp.getWsNdarrayId())
        		.withDistMetric("euclidean")
        		.withDistThreshold(50.0)
        		.withFclusterCriterion("distance")
        		.withLinkageMethod("ward"));        		
        	
        	// Build list of biclusters
        	List<Bicluster> biclusters = new ArrayList<Bicluster>();
        	for(List<String> biItemGuids: res.getBiclusters()) {
        		Bicluster bic = new Bicluster()
        				.withCompendiumGuid(cmp.getGuid())
        				.withConditionGuids(null)
        				.withFeatureGuids(biItemGuids)
        				.withGuid("BIC:" + System.currentTimeMillis() + "_" + ((int)(Math.random()*1000)))
        				.withKeappGuid(KEAPP_GUID);
        		biclusters.add(bic);
        	}
        	
        	// Store biclusters
        	GraphUpdateStat ret = reClient.storeBiclusters(
        			new StoreBiclustersParams()
        			.withBiclusters(biclusters));
        	
        	// Update app state
            app
        	.withNodesCreated(app.getNodesCreated() + ret.getNodesCreated())
        	.withRelationsCreated(app.getNodesCreated() + ret.getRelationshipsCreated())
        	.withPropertiesSet(app.getPropertiesSet() + ret.getPropertiesSet());
            
            reClient.storeKEAppDescriptor(new StoreKEAppDescriptorParams()
            		.withApp(app));            
        }
        
        return new ConstructExprBiclustersOutput()
        		.withNewReNodes(app.getNodesCreated())
        		.withNewReLinks(app.getRelationsCreated())
        		.withUpdatedReNodes(0L)
        		.withPropertiesSet(app.getPropertiesSet())
        		.withMessage("");        
	}

}
