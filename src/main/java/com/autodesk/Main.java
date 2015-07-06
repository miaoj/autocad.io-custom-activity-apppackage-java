package com.autodesk;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.olingo.client.api.*;
import org.apache.olingo.client.api.communication.request.cud.ODataEntityCreateRequest;
import org.apache.olingo.client.api.communication.request.retrieve.EdmMetadataRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntityRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataServiceDocumentRequest;
import org.apache.olingo.client.api.communication.response.ODataEntityCreateResponse;
import org.apache.olingo.client.api.communication.response.ODataResponse;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.*;
import org.apache.olingo.client.core.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import static java.lang.System.*;
/**
 * Created by Jonathan Miao on 7/6/2015.
 */

public class Main {

    public static void main(String[] args) throws IOException, ParseException, InterruptedException {

        final String token = getToken("you consumer key", "you consumer secret");

        ODataClient client = ODataClientFactory.getClient();
        String serviceRoot = "https://developer.api.autodesk.com/autocad.io/us-east/v2/";

        ClientEntity act = getActivity(client, serviceRoot, token);

        createNewWorkItem(client, serviceRoot, token);
    }

    //obtain authorization token
    static String getToken(final String consumerKey, final String consumerSecret) throws IOException, ParseException {
        final String url = "https://developer.api.autodesk.com/authentication/v1/authenticate";
        final HttpPost post = new HttpPost(url);
        List<NameValuePair> form = new ArrayList<NameValuePair>();
        form.add(new BasicNameValuePair("client_id", consumerKey));
        form.add(new BasicNameValuePair("client_secret", consumerSecret));
        form.add(new BasicNameValuePair("grant_type", "client_credentials"));
        post.setEntity(new UrlEncodedFormEntity(form, "UTF-8"));


        final HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response = client.execute(post);

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatusLine().getStatusCode());
        }

        final BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
        final JSONParser jsonParser = new JSONParser();
        final JSONObject jsonObj = (JSONObject) jsonParser.parse(br);
        return (String)jsonObj.get("token_type") + " " + (String)jsonObj.get("access_token");
    }

    static ClientEntity getActivity(final ODataClient client, final String serviceRoot, final String token)
    {
        // try get Activity
        URI uri = client.newURIBuilder(serviceRoot)
                .appendEntitySetSegment("Activities")
                .appendKeySegment("PlotToPDF").build();

        ODataEntityRequest<ClientEntity> entityRequest = client.getRetrieveRequestFactory().getEntityRequest(uri);
        entityRequest.addCustomHeader("Authorization", token);
        ODataRetrieveResponse<ClientEntity> response = entityRequest.execute();
        ClientEntity activity = response.getBody();

        return activity;
    }

    static void createNewWorkItem(final ODataClient client, final String serviceRoot, final String token) throws InterruptedException, IOException, ParseException {
        // create a new WorkItem
        ClientEntity ent = client.getObjectFactory().newEntity(new FullQualifiedName("ACES.Models", "WorkItem"));
        // set ID to ""
        ent.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
                "Id", client.getObjectFactory().newPrimitiveValueBuilder().buildString("")));

        // set ActivityId to "PlotToPDF"
        ent.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
                "ActivityId", client.getObjectFactory().newPrimitiveValueBuilder().buildString("PlotToPDF")));

        // Set Arguments
        ClientComplexValue inputArgument = client.getObjectFactory().newComplexValue("ACES.Models.Argument");
        inputArgument.add(client.getObjectFactory().newPrimitiveProperty("Name",
                client.getObjectFactory().newPrimitiveValueBuilder().buildString("HostDwg")));
        inputArgument.add(client.getObjectFactory().newPrimitiveProperty("Resource",
                client.getObjectFactory().newPrimitiveValueBuilder().buildString("http://download.autodesk.com/us/samplefiles/acad/blocks_and_tables_-_imperial.dwg")));
        inputArgument.add(client.getObjectFactory().newEnumProperty("StorageProvider", client.getObjectFactory().newEnumValue("ACES.Models.StorageProvider", "Generic")));

        ClientComplexValue outputArgument = client.getObjectFactory().newComplexValue("ACES.Models.Argument");
        outputArgument.add(client.getObjectFactory().newPrimitiveProperty("Name",
                client.getObjectFactory().newPrimitiveValueBuilder().buildString("Result")));
        outputArgument.add(client.getObjectFactory().newPrimitiveProperty("Resource",
                client.getObjectFactory().newPrimitiveValueBuilder().buildString("")));
        outputArgument.add(client.getObjectFactory().newEnumProperty("StorageProvider", client.getObjectFactory().newEnumValue("ACES.Models.StorageProvider", "Generic")));
        outputArgument.add(client.getObjectFactory().newEnumProperty("HttpVerb", client.getObjectFactory().newEnumValue("ACES.Models.HttpVerbType","POST")));

        ClientCollectionValue<ClientValue> inputArgCollection = client.getObjectFactory().newCollectionValue("ACES.Models.Argument");
        inputArgCollection.add(inputArgument);
        ClientCollectionValue<ClientValue> outputArgCollection = client.getObjectFactory().newCollectionValue("ACES.Models.Argument");
        outputArgCollection.add(outputArgument);
        ClientComplexValue arguments = client.getObjectFactory().newComplexValue("ACES.Models.Arguments");
        arguments.add(client.getObjectFactory().newCollectionProperty("InputArguments", inputArgCollection));
        arguments.add(client.getObjectFactory().newCollectionProperty("OutputArguments", outputArgCollection));

        ent.getProperties().add(client.getObjectFactory().newComplexProperty("Arguments", arguments));

        // submit the request
        URI uri = client.newURIBuilder(serviceRoot).appendEntitySetSegment("WorkItems").build();
        ODataEntityCreateRequest<ClientEntity> req = client.getCUDRequestFactory().getEntityCreateRequest(uri, ent);
        req.addCustomHeader("Authorization", token);
        ODataEntityCreateResponse<ClientEntity> res = req.execute();

        if(res.getStatusCode() == 201)
        {
            ClientEntity workItem = res.getBody();
            String id = workItem.getProperty("Id").getValue().asPrimitive().toString();
            String status;
            do {
                out.println("Sleeping for 2s...");
                Thread.sleep(2000);
                out.print("Checking work item status=");
                workItem = pollWorkItem(client, serviceRoot, token, id);
                status = workItem.getProperty("Status").getValue().asEnum().getValue();
                out.println(status);
            } while (status.compareTo("Pending")==0 || status.compareTo("InProgress")==0);
            if (status.compareTo("Succeeded")==0)
                downloadResults(workItem);
        }
    }

    //polls the workitem for its status. Returns the status.
    static ClientEntity pollWorkItem(final ODataClient client, final String serviceRoot, final String token, final String id) throws IOException, ParseException {
        // try get Activity
        URI uri = client.newURIBuilder(serviceRoot)
                .appendEntitySetSegment("WorkItems")
                .appendKeySegment(id).build();

        ODataEntityRequest<ClientEntity> entityRequest = client.getRetrieveRequestFactory().getEntityRequest(uri);
        entityRequest.addCustomHeader("Authorization", token);
        ODataRetrieveResponse<ClientEntity> response = entityRequest.execute();
        ClientEntity wi = response.getBody();
        return wi;
    }

    //downloads the workitem results and status report.
    static void downloadResults(final ClientEntity wi) throws IOException, ParseException {
        ClientComplexValue arguments = wi.getProperty("Arguments").getComplexValue();
        ClientProperty outputArguments = arguments.get("OutputArguments");
        for(ClientValue collectionValue : outputArguments.getCollectionValue())
        {
            ClientComplexValue argument = collectionValue.asComplex();
            String nameValue = argument.get("Name").getValue().asPrimitive().toString();
            if(nameValue.compareTo("Result")==0) {
                final String resultUrl = argument.get("Resource").getValue().asPrimitive().toString();
                // download result pdf
                FileUtils.copyURLToFile(new URL(resultUrl), new File("d:/result.pdf"));
                break;
            }
        }
        final String reportUrl = wi.getProperty("StatusDetails").getComplexValue().get("Report").getValue().asPrimitive().toString();
        // download execution report
        FileUtils.copyURLToFile(new URL(reportUrl), new File("d:/report.txt"));
    }
}
